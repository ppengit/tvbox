package com.tvbox.desktop.state

import com.tvbox.app.data.AppLog
import com.tvbox.app.data.SpeedResult
import com.tvbox.app.domain.Movie
import com.tvbox.app.data.CachedSpeed
import com.tvbox.app.data.movieSpeedCacheKey
import com.tvbox.app.data.speedCacheKey
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
            val measuredAt = System.currentTimeMillis()
            updateSpeedTest {
                it.copy(
                    running = false,
                    lastRunAtMs = measuredAt,
                    lineResults = results.associateBy { result -> result.targetId },
                )
            }
            // 落盘后重启也能按上次的延迟排序，不必每次开设置页重测。
            speedCacheRepository.saveLineSpeeds(
                speeds = results.associate { it.targetId to CachedSpeed.from(it) },
                measuredAtMs = measuredAt,
            )
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
            val measuredAt = System.currentTimeMillis()
            // 结果按「线路 + 播放源名」缓存，同一部剧下次打开可直接回填排序。
            runCatching {
                speedCacheRepository.saveSourceSpeeds(
                    movieKey = movieSpeedCacheKey(movie.apiLineId, movie.id),
                    speeds = movie.playSources.mapIndexedNotNull { index, source ->
                        results.getOrNull(index)?.let { source.speedCacheKey() to CachedSpeed.from(it) }
                    }.toMap(),
                    measuredAtMs = measuredAt,
                )
            }
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
                        sourceSpeedsMeasuredAtMs = measuredAt,
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

/** 详情页「播放线路」标题右侧的测速状态：说明排序依据以及结果是新测的还是回填的。 */
internal fun speedFreshnessHint(speedTesting: Boolean, measuredAtMs: Long, nowMs: Long): String {
    if (speedTesting) return "测速中…"
    if (measuredAtMs <= 0L) return "测速后按延迟排序"
    val minutes = ((nowMs - measuredAtMs).coerceAtLeast(0L)) / 60_000L
    val freshness = when {
        minutes < 1L -> "刚刚测速"
        minutes < 60L -> "${minutes} 分钟前测速"
        minutes < 24L * 60L -> "${minutes / 60L} 小时前测速"
        else -> "${minutes / (24L * 60L)} 天前测速"
    }
    return "$freshness · 按延迟排序"
}

/** 详情请求是否还是发起测速的那一次，且剧集没有被换掉。 */
internal fun DesktopAppState.isCurrentDetailMovie(movie: Movie, requestId: Long): Boolean =
    detailRequestId == requestId &&
        detail.value?.movie?.let { it.apiLineId == movie.apiLineId && it.id == movie.id } == true

/**
 * 启动时回填上次的线路测速结果。只保留当前仍然存在的线路，
 * 避免删掉的线路继续出现在「最快线路」里。
 */
fun DesktopAppState.loadCachedLineSpeeds() {
    val cached = runCatching { speedCacheRepository.loadLineSpeeds() }.getOrNull() ?: return
    val knownIds = runCatching { movieRepository.apiLines.map { it.id }.toSet() }.getOrDefault(emptySet())
    val results = cached.toResults().filterKeys { knownIds.isEmpty() || it in knownIds }
    if (results.isEmpty()) return
    updateSpeedTest { it.copy(lineResults = results, lastRunAtMs = cached.measuredAtMs) }
}

fun DesktopAppState.clearLineSpeedResults() {
    updateSpeedTest { it.copy(lineResults = emptyMap(), lastRunAtMs = 0) }
    speedCacheRepository.clearLineSpeeds()
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
