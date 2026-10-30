package com.tvbox.desktop.state

import com.tvbox.app.data.AppLog
import com.tvbox.app.data.SpeedResult
import com.tvbox.app.domain.Movie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 线路测速：并发测量所有内置/自定义线路的接口响应速度。
 * 结果同时写入结构化日志，方便事后在日志页回看。
 */
fun DesktopAppState.runLineSpeedTest() {
    if (speedTest.value.running) return
    updateSpeedTest { it.copy(running = true) }
    speedJob?.cancel()
    speedJob = scopeRef.launch {
        try {
            val lines = movieRepository.apiLines
            AppLog.info("Speed", "开始线路测速，共 ${lines.size} 条")
            val results = container.speedTester.measureLines(lines)
            updateSpeedTest {
                it.copy(
                    running = false,
                    lastRunAtMs = System.currentTimeMillis(),
                    lineResults = results.associateBy { result -> result.targetId },
                )
            }
            val fastest = results.filter { it.ok }
                .minWithOrNull(compareBy({ it.latencyMs.coerceAtLeast(1L) }, { -it.kbps }))
            if (fastest != null) {
                AppLog.info(
                    "Speed",
                    "最快线路：${fastest.targetName} ${fastest.formatLatency()}（${fastest.formatSpeed()}）",
                )
                notify("最快线路：${fastest.targetName}（${fastest.formatLatency()}）")
            } else {
                AppLog.warn("Speed", "所有线路测速均失败")
                notify("线路测速全部失败，请检查网络或代理设置")
            }
        } catch (error: CancellationException) {
            // 任务被取消（退出/重新调度）时 running 必须复位，否则之后再也点不动测速。
            updateSpeedTest { it.copy(running = false) }
            throw error
        } catch (error: Throwable) {
            updateSpeedTest { it.copy(running = false) }
            AppLog.error("Speed", "线路测速异常：${error.userMessage()}")
            notify("测速失败：${error.userMessage()}")
        }
    }
}

/** 把测速最快的线路设为首页数据源。 */
fun DesktopAppState.applyFastestLine() {
    val fastest = speedTest.value.fastestLineId() ?: run {
        notify("还没有可用的测速结果")
        return
    }
    val line = movieRepository.apiLines.firstOrNull { it.id == fastest } ?: return
    saveSettings { it.copy(homeApiLineId = line.id) }
    notify("首页数据源已切换到 ${line.name}")
    AppLog.info("Speed", "首页数据源切换为 ${line.name}")
    loadHome(reset = true)
}

/** 详情页的播放源测速。开着「默认使用最快线路」（或本剧覆盖）时测完直接切过去。 */
fun DesktopAppState.runSourceSpeedTest() {
    val detailState = detail.value ?: return
    val movie = detailState.movie ?: return
    if (detailState.speedTesting || movie.playSources.isEmpty()) return
    val autoApply = effectivePreferFastest(movie)
    val requestId = detailRequestId
    // 测速是给「当前详情页」跑的：换剧之后旧结果不能写进新详情页，也不能拿旧剧的
    // 线路下标去切新剧的播放器。
    sourceSpeedJob?.cancel()
    updateDetail { it.copy(speedTesting = true) }
    sourceSpeedJob = scopeRef.launch {
        try {
            val results = container.speedTester.measureSources(movie.playSources)
            if (!isCurrentDetailMovie(movie, requestId)) return@launch
            updateDetail { current ->
                val currentMovie = current.movie
                if (currentMovie == null ||
                    currentMovie.apiLineId != movie.apiLineId ||
                    currentMovie.id != movie.id
                ) {
                    current
                } else {
                    current.copy(
                        speedTesting = false,
                        sourceSpeeds = results.mapIndexed { index, result -> index to result }.toMap(),
                    )
                }
            }
            val fastestIndex = results.indices.filter { results[it].ok }.minWithOrNull(
                compareBy({ results[it].latencyMs.coerceAtLeast(1L) }, { -results[it].kbps }),
            )
            val fastest = fastestIndex?.let { results[it] }
            if (fastest != null) {
                notify("最快线路：${fastest.targetName}（${fastest.formatLatency()}）")
                if (autoApply) applyFastestSource(movie, results)
            } else {
                notify("所有播放源测速失败")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (isCurrentDetailMovie(movie, requestId)) {
                updateDetail { it.copy(speedTesting = false) }
            }
            AppLog.error("Speed", "播放源测速异常：${error.userMessage()}")
        }
    }
}

/** 详情请求是否还是发起测速的那一次，且剧集没有被换掉。 */
internal fun DesktopAppState.isCurrentDetailMovie(movie: Movie, requestId: Long): Boolean =
    detailRequestId == requestId &&
        detail.value?.movie?.let { it.apiLineId == movie.apiLineId && it.id == movie.id } == true

fun DesktopAppState.clearLineSpeedResults() {
    updateSpeedTest { it.copy(lineResults = emptyMap(), lastRunAtMs = 0) }
}

/** 按设置里的间隔启动定时测速；间隔为 0 表示关闭。 */
fun DesktopAppState.restartSpeedTimer() {
    speedTimerJob?.cancel()
    val minutes = settings.value.settings.speedTestIntervalMinutes
    if (minutes <= 0) return
    speedTimerJob = scopeRef.launch {
        AppLog.info("Speed", "定时测速已开启，每 $minutes 分钟一次")
        while (isActive) {
            delay(minutes * 60_000L)
            if (!speedTest.value.running) runLineSpeedTest()
        }
    }
}

internal fun SpeedResult.isBetterThan(other: SpeedResult?): Boolean = when {
    other == null -> true
    !ok -> false
    !other.ok -> true
    latencyMs <= 0L || other.latencyMs <= 0L -> kbps > other.kbps
    else -> latencyMs < other.latencyMs || (latencyMs == other.latencyMs && kbps > other.kbps)
}
