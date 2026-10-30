package com.tvbox.desktop.state

import com.tvbox.app.domain.EpisodeMatrix
import com.tvbox.app.domain.PlayEpisode
import com.tvbox.app.domain.PlaybackIssueType
import com.tvbox.app.domain.WatchHistoryItem
import com.tvbox.app.domain.buildEpisodeMatrix
import com.tvbox.app.domain.playbackHealthKey
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.WeakHashMap

private const val MAX_RETRY_BACKOFF_MS = 8_000L
private data class ProgressSaveState(val mutex: Mutex = Mutex(), var version: Long = 0L)
private val progressSaveStates = WeakHashMap<DesktopAppState, ProgressSaveState>()
private fun DesktopAppState.nextProgressSave(): Pair<ProgressSaveState, Long> = synchronized(progressSaveStates) {
    val state = progressSaveStates.getOrPut(this) { ProgressSaveState() }
    state.version++
    state to state.version
}
private fun DesktopAppState.isLatestProgressSave(state: ProgressSaveState, version: Long): Boolean =
    synchronized(progressSaveStates) { progressSaveStates[this] === state && state.version == version }

/** 播放器里的线路菜单项，带上该线路在统一集数列表下的可播集数。 */
data class PlayerSourceOption(
    val index: Int,
    val name: String,
    val playable: Int,
    val total: Int,
)

internal fun EpisodeMatrix.episodeOf(sourceIndex: Int, unifiedIndex: Int): PlayEpisode? =
    sources.getOrNull(sourceIndex)?.byIndex?.get(unifiedIndex)

private fun DesktopAppState.matrixOf(movie: com.tvbox.app.domain.Movie): EpisodeMatrix =
    buildEpisodeMatrix(movie.playSources)

/**
 * 进入播放页。集数下标一律使用「统一集数」（多源取并集），
 * 当前线路缺这一集时自动换成有这一集的线路。
 */
fun DesktopAppState.playDetailEpisode(sourceIndex: Int, episodeIndex: Int, startPositionMs: Long = 0L) {
    val movie = detail.value?.movie ?: run {
        notify("影片信息未就绪")
        return
    }
    if (movie.playSources.isEmpty()) {
        notify("该影片没有可播放的线路")
        return
    }
    val matrix = matrixOf(movie)
    val resolved = matrix.resolvePlayable(sourceIndex, episodeIndex)
        ?: run {
            notify("所有线路都缺少这一集")
            return
        }
    val (targetSource, targetEpisodeIndex) = resolved
    val episode = matrix.episodeOf(targetSource, targetEpisodeIndex) ?: run {
        notify("该线路没有可播放的剧集")
        return
    }
    val sourceName = movie.playSources.getOrNull(targetSource)?.name.orEmpty()
    if (targetSource != sourceIndex) {
        notify("「${movie.playSources.getOrNull(sourceIndex)?.name}」缺少这一集，已切到「$sourceName」")
    }
    setPlayer(
        PlayerState(
            movie = movie,
            sourceIndex = targetSource,
            episodeIndex = targetEpisodeIndex,
            startPositionMs = startPositionMs.coerceAtLeast(0L),
            currentUrl = episode.url,
            episodeTitle = matrix.episodes.getOrNull(targetEpisodeIndex)?.title ?: episode.title,
            episodeCount = matrix.episodes.size,
            failedSourceIndices = emptySet(),
            lastPositionMs = startPositionMs.coerceAtLeast(0L),
        ),
    )
    updateDetail { it.copy(sourceIndex = targetSource, episodeIndex = targetEpisodeIndex) }
    prefetchUpcoming(movie, targetSource, targetEpisodeIndex)
}

/** Compatibility shim for existing UI call sites; all detail playback is centralized above. */
@Deprecated("Use playDetailEpisode")
fun DesktopAppState.openPlayer(sourceIndex: Int, episodeIndex: Int, startPositionMs: Long = 0L) =
    playDetailEpisode(sourceIndex, episodeIndex, startPositionMs)

fun DesktopAppState.closePlayer() {
    val state = player.value
    if (state != null && !state.isLive) {
        updateDetail { it.copy(sourceIndex = state.sourceIndex, episodeIndex = state.episodeIndex) }
        savePlayerProgress(state.lastPositionMs, state.lastDurationMs, state)
    }
    setPlayer(null)
}

/** 播放中切源：保留统一集数，缺集时自动跳到该线路最近的可播集。 */
fun DesktopAppState.switchPlayerSource(sourceIndex: Int) {
    val state = player.value ?: return
    val movie = state.movie ?: return
    val matrix = matrixOf(movie)
    val source = movie.playSources.getOrNull(sourceIndex) ?: return
    val resolved = matrix.resolveSourceSwitch(sourceIndex, state.episodeIndex)
    if (resolved == null) {
        notify("「${source.name}」没有可播的集")
        return
    }
    val targetEpisode = resolved.second
    if (targetEpisode != state.episodeIndex) {
        notify("「${source.name}」缺少第 ${state.episodeIndex + 1} 集，已切到最近可播的第 ${targetEpisode + 1} 集")
        switchPlayerEpisodeInternal(targetEpisode, sourceIndex)
        return
    }
    switchPlayerEpisodeInternal(state.episodeIndex, sourceIndex, notice = "已切换到「${source.name}」")
}

fun DesktopAppState.switchPlayerEpisode(episodeIndex: Int) {
    switchPlayerEpisodeInternal(episodeIndex, player.value?.sourceIndex ?: 0)
}

private fun DesktopAppState.switchPlayerEpisodeInternal(
    episodeIndex: Int,
    preferredSource: Int,
    notice: String? = null,
) {
    val state = player.value ?: return
    val movie = state.movie ?: return
    val matrix = matrixOf(movie)
    val resolved = matrix.resolvePlayable(preferredSource, episodeIndex) ?: run {
        notify("这一集在所有线路都不可用")
        return
    }
    val (targetSource, targetEpisode) = resolved
    val episode = matrix.episodeOf(targetSource, targetEpisode) ?: return
    val sourceChanged = targetSource != state.sourceIndex
    setPlayer(
        state.copy(
            sourceIndex = targetSource,
            episodeIndex = targetEpisode,
            currentUrl = episode.url,
            startPositionMs = 0L,
            episodeTitle = matrix.episodes.getOrNull(targetEpisode)?.title ?: episode.title,
            episodeCount = matrix.episodes.size,
            failedSourceIndices = emptySet(),
            lastPositionMs = 0L,
            lastDurationMs = 0L,
            switchNotice = notice ?: if (sourceChanged) {
                "当前线路缺少这一集，已切到「${movie.playSources.getOrNull(targetSource)?.name}」"
            } else {
                null
            },
        ),
    )
    updateDetail { it.copy(sourceIndex = targetSource, episodeIndex = targetEpisode) }
    prefetchUpcoming(movie, targetSource, targetEpisode)
}

/** 播完自动下一集：跨线路也可用，找不到就提示已播完。 */
fun DesktopAppState.playNextEpisode() {
    val state = player.value ?: return
    val movie = state.movie ?: return
    val matrix = matrixOf(movie)
    val next = state.episodeIndex + 1
    if (next >= matrix.episodes.size) {
        notify("已经是最后一集")
        return
    }
    switchPlayerEpisodeInternal(next, state.sourceIndex)
}

fun DesktopAppState.playPreviousEpisode() {
    val state = player.value ?: return
    if (state.isLive) return
    val movie = state.movie ?: return
    val matrix = matrixOf(movie)
    val previous = (state.episodeIndex - 1).coerceAtLeast(0)
    if (previous == state.episodeIndex) return
    switchPlayerEpisodeInternal(previous, state.sourceIndex)
}

/** 播放页里的线路菜单：列出所有线路和各自的可播集数。 */
fun DesktopAppState.playerSourceOptions(): List<PlayerSourceOption> {
    val movie = player.value?.movie ?: return emptyList()
    val matrix = matrixOf(movie)
    return movie.playSources.mapIndexed { index, source ->
        PlayerSourceOption(
            index = index,
            name = source.name,
            playable = matrix.availability(index).count { it },
            total = matrix.episodes.size,
        )
    }
}

/** 详情页线路可用集数，用于在选集网格上标注。 */
fun DesktopAppState.episodeMatrix(): EpisodeMatrix? {
    val movie = detail.value?.movie ?: return null
    return matrixOf(movie)
}

/** 预热后续几集：提前建立到 CDN 的连接，切集更快。 */
internal fun DesktopAppState.prefetchUpcoming(
    movie: com.tvbox.app.domain.Movie,
    sourceIndex: Int,
    episodeIndex: Int,
) {
    val count = settings.value.settings.prefetchEpisodeCount
    if (count <= 0) return
    val matrix = matrixOf(movie)
    val urls = (1..count)
        .mapNotNull { offset -> matrix.episodeOf(sourceIndex, episodeIndex + offset)?.url }
    if (urls.isEmpty()) return
    scopeRef.launch { runCatching { container.streamPrefetcher.warmUp(urls) } }
}

fun DesktopAppState.retryDelayForAttempt(attempt: Int): Long =
    (500L shl (attempt - 1).coerceIn(0, 4)).coerceAtMost(MAX_RETRY_BACKOFF_MS)

// region 进度与健康度

/**
 * 写入观看进度。播放页每 10 秒以及退出时调用，桌面端同样支持跨启动续播。
 */
fun DesktopAppState.savePlayerProgress(positionMs: Long, durationMs: Long) {
    val state = player.value ?: return
    if (state.isLive) return
    val safePosition = positionMs.coerceAtLeast(0L)
    val safeDuration = durationMs.coerceAtLeast(0L)
    updatePlayer { current ->
        if (current?.currentUrl == state.currentUrl) {
            current.copy(lastPositionMs = safePosition, lastDurationMs = safeDuration)
        } else {
            current
        }
    }
    savePlayerProgress(safePosition, safeDuration, state)
}

private fun DesktopAppState.savePlayerProgress(positionMs: Long, durationMs: Long, state: PlayerState) {
    val movie = state.movie ?: return
    val matrix = matrixOf(movie)
    val episode = matrix.episodeOf(state.sourceIndex, state.episodeIndex) ?: return
    val source = movie.playSources.getOrNull(state.sourceIndex) ?: return
    if (state.currentUrl.isBlank()) return
    val item = WatchHistoryItem(
        movieId = movie.id,
        apiLineId = movie.apiLineId,
        apiLineName = movie.apiLineName,
        movieName = movie.name,
        posterUrl = movie.posterUrl,
        typeName = movie.typeName,
        remarks = movie.remarks,
        sourceIndex = state.sourceIndex,
        sourceName = source.name,
        episodeIndex = state.episodeIndex,
        episodeTitle = episode.title,
        episodeUrl = episode.url,
        positionMs = positionMs.coerceAtLeast(0L),
        durationMs = durationMs.coerceAtLeast(0L),
        updatedAtEpochMs = System.currentTimeMillis(),
        episodeCount = matrix.episodes.size,
    )
    val (saveState, saveVersion) = nextProgressSave()
    scopeRef.launch {
        saveState.mutex.withLock {
            if (!isLatestProgressSave(saveState, saveVersion)) return@withLock
            runCatching { historyRepository.saveProgress(item) }
                .onSuccess { items ->
                    updateHistory { HistoryState(items = items) }
                    markFollowProgress(movie, state.episodeIndex, positionMs)
                }
        }
    }
}

fun DesktopAppState.recordPlaybackSuccess() {
    val state = player.value ?: return
    val movie = state.movie ?: return
    val source = movie.playSources.getOrNull(state.sourceIndex) ?: return
    val matrix = matrixOf(movie)
    if (matrix.episodeOf(state.sourceIndex, state.episodeIndex)?.url.isNullOrBlank()) return
    val key = playbackHealthKey(movie.id, state.episodeIndex, source)
    scopeRef.launch {
        runCatching { playbackHealthRepository.recordSuccess(key, System.currentTimeMillis()) }
            .onSuccess { snapshot -> updateSettings { it.copy(playbackHealth = snapshot) } }
    }
}

fun DesktopAppState.recordPlaybackIssue(issue: PlaybackIssueType) {
    val state = player.value ?: return
    val movie = state.movie ?: return
    val source = movie.playSources.getOrNull(state.sourceIndex) ?: return
    val matrix = matrixOf(movie)
    if (matrix.episodeOf(state.sourceIndex, state.episodeIndex)?.url.isNullOrBlank()) return
    val key = playbackHealthKey(movie.id, state.episodeIndex, source)
    com.tvbox.app.data.AppLog.warn("Playback", "记录播放问题 $issue：${movie.name} / ${source.name} / 第${state.episodeIndex + 1}集")
    scopeRef.launch {
        runCatching { playbackHealthRepository.recordIssue(key, issue, System.currentTimeMillis()) }
            .onSuccess { snapshot -> updateSettings { it.copy(playbackHealth = snapshot) } }
    }
}

/**
 * 自然播放失败时优先换一条有同一集、且不在冷却中的线路；全部不可用则提示。
 * 返回 true 表示已经切到别的线路。
 */
fun DesktopAppState.handlePlaybackFailure(reason: String): Boolean {
    val state = player.value ?: return false
    val movie = state.movie ?: return false
    recordPlaybackIssue(PlaybackIssueType.Error)
    val matrix = matrixOf(movie)
    val failed = state.failedSourceIndices + state.sourceIndex
    val candidates = movie.playSources.indices
        .filterNot { it in failed }
        .filter { matrix.availability(it).getOrElse(state.episodeIndex) { false } }
    if (candidates.isEmpty()) {
        updatePlayer { current ->
            if (current?.currentUrl == state.currentUrl) current.copy(failedSourceIndices = failed) else current
        }
        com.tvbox.app.data.AppLog.error("Playback", "播放失败且无未尝试的备用线路：$reason")
        notify("播放失败：$reason")
        return false
    }
    val preferred = resolvePreferredSource(movie, candidates.first(), state.episodeIndex)
    val target = preferred.takeIf { it in candidates } ?: candidates.first()
    com.tvbox.app.data.AppLog.warn(
        "Playback",
        "播放失败自动切源：${movie.playSources.getOrNull(state.sourceIndex)?.name} → ${movie.playSources.getOrNull(target)?.name}（$reason）",
    )
    val episode = matrix.episodeOf(target, state.episodeIndex) ?: return false
    setPlayer(
        state.copy(
            sourceIndex = target,
            currentUrl = episode.url,
            startPositionMs = state.lastPositionMs,
            failedSourceIndices = failed,
            switchNotice = "播放失败，已切换到「${movie.playSources.getOrNull(target)?.name}」",
        ),
    )
    updateDetail { it.copy(sourceIndex = target, episodeIndex = state.episodeIndex) }
    prefetchUpcoming(movie, target, state.episodeIndex)
    notify("播放失败，已切换到「${movie.playSources.getOrNull(target)?.name}」")
    return true
}

// endregion
