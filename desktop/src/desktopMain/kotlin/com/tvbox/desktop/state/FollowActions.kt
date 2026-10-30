package com.tvbox.desktop.state

import com.tvbox.app.data.AppLog
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.buildEpisodeMatrix
import com.tvbox.desktop.data.FollowedShow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 未配置定时巡检时的兜底过期时间：打开追剧页才检查，避免长期不刷新。 */
private const val FOLLOW_FALLBACK_STALE_MS = 60 * 60 * 1000L

private fun DesktopAppState.followStaleMs(): Long {
    val minutes = settings.value.settings.followRefreshMinutes
    return if (minutes <= 0) FOLLOW_FALLBACK_STALE_MS else minutes * 60_000L
}

/**
 * 追剧定时巡检：应用运行期间按设置间隔检查，发现新集就提示。
 * 间隔改为 0 表示关闭定时任务，只在打开追剧页时检查。
 */
fun DesktopAppState.restartFollowTimer() {
    followTimerJob?.cancel()
    followTimerJob = null
    val minutes = settings.value.settings.followRefreshMinutes
    if (minutes <= 0) return
    followTimerJob = scopeRef.launch {
        while (true) {
            delay(minutes * 60_000L)
            if (follow.value.items.isNotEmpty() && !follow.value.checking) {
                refreshFollows(force = true)
            }
        }
    }
}

/** 追剧面板的数据来自本地存储，打开页面时同步一次即可。 */
fun DesktopAppState.loadFollows() {
    val items = runCatching { followedShowRepository.load() }
        .getOrDefault(emptyList())
        .sortedWith(compareByDescending<FollowedShow> { it.hasNewEpisodes }.thenByDescending { it.followedAtMs })
    updateFollow { it.copy(items = items) }
    detailFollowFlag()
}

fun DesktopAppState.refreshFollowsIfStale() {
    val state = follow.value
    if (state.items.isEmpty() || state.checking) return
    val stale = state.lastCheckedAtMs == 0L || System.currentTimeMillis() - state.lastCheckedAtMs > followStaleMs()
    if (stale) refreshFollows(force = false)
}

/**
 * 逐个检查追剧条目：重新拉详情，以最多集数的源为准对比集数。
 * 集数变多就标记「有新集」，并记录最新集标题。
 */
fun DesktopAppState.refreshFollows(force: Boolean = true) {
    val state = follow.value
    if (state.checking) return
    if (state.items.isEmpty()) return
    if (!force && state.lastCheckedAtMs > 0 && System.currentTimeMillis() - state.lastCheckedAtMs < followStaleMs()) return

    updateFollow { it.copy(checking = true, error = null) }
    followJob?.cancel()
    followJob = scopeRef.launch {
        var updatedCount = 0
        var failedCount = 0
        val items = follow.value.items.map { item ->
            val detail = runCatching { movieRepository.getDetail(item.apiLineId, item.movieId) }
                .onFailure { failedCount++ }
                .getOrNull()
            if (detail == null) return@map item
            val matrix = buildEpisodeMatrix(detail.playSources)
            val latestCount = matrix.episodes.size
            val latestTitle = matrix.episodes.lastOrNull()?.title.orEmpty()
            val refreshed = item.refreshedWith(
                detailName = detail.name,
                detailPosterUrl = detail.posterUrl,
                detailYear = detail.year,
                detailArea = detail.area,
                latestCount = latestCount,
                latestTitle = latestTitle,
                nowMs = System.currentTimeMillis(),
            )
            if (refreshed.hasNewEpisodes && !item.hasNewEpisodes) updatedCount++
            refreshed
        }
        followedShowRepository.save(items)
        updateFollow {
            it.copy(
                items = items.sortedWith(
                    compareByDescending<FollowedShow> { show -> show.hasNewEpisodes }
                        .thenByDescending { show -> show.followedAtMs },
                ),
                checking = false,
                lastCheckedAtMs = System.currentTimeMillis(),
                error = if (failedCount > 0) "$failedCount 部追剧检查失败，请稍后重试" else null,
            )
        }
        detailFollowFlag()
        if (updatedCount > 0) {
            notify("$updatedCount 部追剧有更新")
            AppLog.info("Follow", "追剧检查完成，$updatedCount 部有新集")
        } else {
            AppLog.info("Follow", "追剧检查完成，暂无新集")
        }
    }
}

/**
 * 一次巡检后的追剧条目。
 *
 * 基线 `lastEpisodeCount` 只在用户点开这部剧时前进（见 [openFollow]）；巡检只更新
 * `latestEpisodeCount` 并把「有新集」标记置上。旧实现每次巡检都把基线抬到最新集数，
 * 新集标记会在用户看到之前就被下一轮巡检清掉。
 */
internal fun FollowedShow.refreshedWith(
    detailName: String,
    detailPosterUrl: String,
    detailYear: String,
    detailArea: String,
    latestCount: Int,
    latestTitle: String,
    nowMs: Long,
): FollowedShow {
    val hasBaseline = lastEpisodeCount > 0
    val newEpisodes = hasBaseline && latestCount > lastEpisodeCount
    return copy(
        name = detailName.ifBlank { name },
        posterUrl = detailPosterUrl.ifBlank { posterUrl },
        year = detailYear.ifBlank { year },
        area = detailArea.ifBlank { area },
        // 首次拿不到集数时只补基线，不算「有新集」。
        lastEpisodeCount = if (hasBaseline) lastEpisodeCount else latestCount,
        latestEpisodeCount = maxOf(latestCount, latestEpisodeCount),
        latestEpisodeTitle = latestTitle.ifBlank { latestEpisodeTitle },
        hasNewEpisodes = hasNewEpisodes || newEpisodes,
        lastCheckedAtMs = nowMs,
    )
}

fun DesktopAppState.isFollowed(apiLineId: String, movieId: Int): Boolean =
    follow.value.items.any { it.apiLineId == apiLineId && it.movieId == movieId }

fun DesktopAppState.toggleFollow(movie: Movie, episodeIndex: Int = 0, positionMs: Long = 0L) {
    val apiLineId = movie.apiLineId
    val key = "$apiLineId:${movie.id}"
    val existing = follow.value.items
    val items = if (existing.any { it.apiLineId == apiLineId && it.movieId == movie.id }) {
        notify("已取消追剧：${movie.name}")
        AppLog.info("Follow", "取消追剧 $key")
        existing.filterNot { it.apiLineId == apiLineId && it.movieId == movie.id }
    } else {
        val episodeCount = buildEpisodeMatrix(movie.playSources).episodes.size
        existing + FollowedShow(
            apiLineId = apiLineId,
            movieId = movie.id,
            name = movie.name,
            posterUrl = movie.posterUrl,
            year = movie.year,
            area = movie.area,
            followedAtMs = System.currentTimeMillis(),
            lastEpisodeCount = episodeCount,
            latestEpisodeCount = episodeCount,
            latestEpisodeTitle = movie.playSources.firstOrNull()?.episodes?.lastOrNull()?.title.orEmpty(),
            lastEpisodeIndex = episodeIndex,
            lastPositionMs = positionMs,
        ).also {
            notify("已追剧：${movie.name}（当前 $episodeCount 集）")
            AppLog.info("Follow", "追剧 $key，$episodeCount 集")
        }
    }
    followedShowRepository.save(items)
    updateFollow { it.copy(items = items.sortedByDescending { show -> show.followedAtMs }) }
    detailFollowFlag()
}

fun DesktopAppState.removeFollow(item: FollowedShow) {
    val previous = follow.value.items
    if (previous.none { it.key == item.key }) return
    val items = previous.filterNot { it.key == item.key }
    followedShowRepository.save(items)
    updateFollow { it.copy(items = items) }
    detailFollowFlag()
    // 卡片上的移除按钮是单击生效的，给一次撤销机会，避免误删。
    notify(
        text = "已取消追剧：${item.name}",
        type = NoticeType.Success,
        action = NoticeAction(label = "撤销") {
            followedShowRepository.save(previous)
            updateFollow { it.copy(items = previous) }
            detailFollowFlag()
        },
        durationMs = 6_000L,
    )
}

/** 追剧条目的播放进度回写，用于「继续观看」排序。 */
fun DesktopAppState.markFollowProgress(movie: Movie, episodeIndex: Int, positionMs: Long) {
    val items = follow.value.items
    if (items.none { it.apiLineId == movie.apiLineId && it.movieId == movie.id }) return
    val updated = items.map {
        if (it.apiLineId == movie.apiLineId && it.movieId == movie.id) {
            it.copy(lastEpisodeIndex = episodeIndex, lastPositionMs = positionMs)
        } else {
            it
        }
    }
    followedShowRepository.save(updated)
    updateFollow { it.copy(items = updated) }
}

fun DesktopAppState.openFollow(item: FollowedShow) {
    // 用户已经点开这部剧，「有新集」标记到此为止：把基线和已知最新集数对齐。
    val items = follow.value.items.map { show ->
        if (show.key == item.key && show.hasNewEpisodes) {
            show.copy(
                lastEpisodeCount = show.displayEpisodeCount,
                hasNewEpisodes = false,
            )
        } else {
            show
        }
    }
    if (items != follow.value.items) {
        followedShowRepository.save(items)
        updateFollow { it.copy(items = items) }
        detailFollowFlag()
    }
    openDetail(movieId = item.movieId, apiLineId = item.apiLineId)
}

private fun DesktopAppState.detailFollowFlag() {
    val detail = detail.value ?: return
    val movie = detail.movie ?: return
    val followed = follow.value.items.any { it.apiLineId == movie.apiLineId && it.movieId == movie.id }
    if (followed != detail.followed) {
        updateDetail { it.copy(followed = followed) }
    }
}

/** 追剧检查的时间文案。 */
fun followCheckedLabel(timeMs: Long): String {
    if (timeMs <= 0L) return "尚未检查"
    val time = Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()).toLocalTime()
    val date = Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val day = if (date == LocalDate.now()) "今天" else "${date.monthValue}月${date.dayOfMonth}日"
    return "$day %02d:%02d".format(time.hour, time.minute)
}
