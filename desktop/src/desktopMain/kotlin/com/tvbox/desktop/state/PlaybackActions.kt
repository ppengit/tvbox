package com.tvbox.desktop.state

import com.tvbox.app.data.AppLog
import com.tvbox.app.data.HistoryWriteGuard
import com.tvbox.app.domain.EpisodeMatrix
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.PlayEpisode
import com.tvbox.app.domain.PlaybackIssueType
import com.tvbox.app.domain.WatchHistoryItem
import com.tvbox.app.domain.buildEpisodeMatrix
import com.tvbox.app.domain.historyKey
import com.tvbox.app.domain.playbackHealthKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MAX_RETRY_BACKOFF_MS = 8_000L

/** 播放器换 URL 后销毁旧媒体时会补报一次进度；只在这个窗口内把补报算到旧媒体头上。 */
private const val REPLACED_MEDIA_FLUSH_WINDOW_MS = 3_000L

/**
 * 观看进度落盘队列。
 *
 * 同一部剧的进度必须串行写入，而且只写最新一次，否则慢 IO 会让旧位置覆盖新位置。
 * 版本按「剧」区分：A 剧排队中的保存不会被 B 剧的保存顶掉（旧实现用一个全局版本号，
 * 换片时会把上一部剧的收尾进度直接丢掉）。
 */
internal class ProgressSaveQueue {
    val mutex: Mutex = Mutex()
    private val versions = mutableMapOf<String, Long>()

    fun next(key: String): Long {
        val version = (versions[key] ?: 0L) + 1L
        versions[key] = version
        return version
    }

    fun isLatest(key: String, version: Long): Boolean = versions[key] == version
}

/**
 * 记录被换下来 / 刚关闭的媒体。
 *
 * 播放器的进度回调来自 UI：切集时旧媒体的 `onDispose` 会再报一次位置，而那一刻
 * `player.value` 已经是新一集，直接按当前状态写会把上一集的进度写进新一集；
 * 关闭播放页时 `player.value` 已经为空，那次补报反而是最准的收尾进度。
 */
internal class MediaProgressTracker(private val windowMs: Long = REPLACED_MEDIA_FLUSH_WINDOW_MS) {
    private var replaced: PlayerState? = null
    private var replacedAtMs: Long = 0L
    private var closed: PlayerState? = null
    private var closedAtMs: Long = 0L

    fun rememberReplaced(previous: PlayerState?, next: PlayerState, nowMs: Long = System.currentTimeMillis()) {
        if (previous == null) return
        if (previous.currentUrl == next.currentUrl) return
        replaced = previous
        replacedAtMs = nowMs
    }

    fun takeReplaced(currentUrl: String, nowMs: Long = System.currentTimeMillis()): PlayerState? {
        val candidate = replaced ?: return null
        val ageMs = nowMs - replacedAtMs
        if (ageMs < 0L || ageMs > windowMs) {
            replaced = null
            return null
        }
        // 还是当前媒体的进度，不是旧媒体的补报，保留记录给随后到达的补报用。
        if (candidate.currentUrl == currentUrl) return null
        replaced = null
        return candidate
    }

    /** 直播或没有播放器时传 null：清掉残留的收尾记录，避免直播关闭时的补报写到点播进度上。 */
    fun rememberClosed(state: PlayerState?, nowMs: Long = System.currentTimeMillis()) {
        if (state == null || state.isLive || state.currentUrl.isBlank()) {
            closed = null
            closedAtMs = 0L
            return
        }
        closed = state
        closedAtMs = nowMs
    }

    fun takeClosed(nowMs: Long = System.currentTimeMillis()): PlayerState? {
        val candidate = closed ?: return null
        closed = null
        val ageMs = nowMs - closedAtMs
        if (ageMs < 0L || ageMs > windowMs) return null
        return candidate
    }

    fun clear() {
        replaced = null
        replacedAtMs = 0L
        closed = null
        closedAtMs = 0L
    }
}

/** 播放器里的线路菜单项，带上该线路在统一集数列表下的可播集数。 */
data class PlayerSourceOption(
    val index: Int,
    val name: String,
    val playable: Int,
    val total: Int,
)

internal fun EpisodeMatrix.episodeOf(sourceIndex: Int, unifiedIndex: Int): PlayEpisode? =
    sources.getOrNull(sourceIndex)?.byIndex?.get(unifiedIndex)

private fun DesktopAppState.matrixOf(movie: Movie): EpisodeMatrix =
    buildEpisodeMatrix(movie.playSources)

/**
 * 播放失败后可以换过去的线路：有这一集，且本轮还没试过。
 */
internal fun failureCandidates(
    matrix: EpisodeMatrix,
    failedSourceIndices: Set<Int>,
    episodeIndex: Int,
): List<Int> = matrix.sources.indices.filter { index ->
    index !in failedSourceIndices && matrix.availability(index).getOrElse(episodeIndex) { false }
}

/**
 * 切集/切源后的播放器状态：换集从头开始，同一集换线路保留进度。
 * 单独抽出来是为了让「索引、进度、失败线路」的清理规则可以被单测覆盖。
 */
internal fun PlayerState.withEpisodeSwitch(
    targetSource: Int,
    targetEpisode: Int,
    url: String,
    title: String,
    episodeCount: Int,
    startPositionMs: Long,
    durationMs: Long,
    switchNotice: String?,
): PlayerState = copy(
    sourceIndex = targetSource,
    episodeIndex = targetEpisode,
    currentUrl = url,
    startPositionMs = startPositionMs.coerceAtLeast(0L),
    episodeTitle = title,
    episodeCount = episodeCount,
    failedSourceIndices = emptySet(),
    lastPositionMs = startPositionMs.coerceAtLeast(0L),
    lastDurationMs = durationMs.coerceAtLeast(0L),
    switchNotice = switchNotice,
)

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
    val next = PlayerState(
        movie = movie,
        sourceIndex = targetSource,
        episodeIndex = targetEpisodeIndex,
        startPositionMs = startPositionMs.coerceAtLeast(0L),
        currentUrl = episode.url,
        episodeTitle = matrix.episodes.getOrNull(targetEpisodeIndex)?.title ?: episode.title,
        episodeCount = matrix.episodes.size,
        failedSourceIndices = emptySet(),
        lastPositionMs = startPositionMs.coerceAtLeast(0L),
    )
    mediaProgress.clear()
    mediaProgress.rememberReplaced(player.value, next)
    setPlayer(next)
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
        // 先用最近一次上报的位置兜底；播放器销毁旧媒体时还会补报一次更准的位置。
        savePlayerProgress(state.lastPositionMs, state.lastDurationMs, state)
    }
    mediaProgress.rememberClosed(state)
    setPlayer(null)
}

/** 播放中切源：保留统一集数，缺集时自动跳到该线路最近的可播集。 */
fun DesktopAppState.switchPlayerSource(sourceIndex: Int) {
    val state = player.value ?: return
    if (state.isLive) return
    val movie = state.movie ?: return
    val matrix = matrixOf(movie)
    val source = movie.playSources.getOrNull(sourceIndex) ?: run {
        notify("该线路不存在")
        return
    }
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
    // 点的是当前线路，地址也没变：不要为了“切换”把正在播放的这一集重开。
    if (sourceIndex == state.sourceIndex &&
        matrix.episodeOf(sourceIndex, targetEpisode)?.url == state.currentUrl
    ) {
        return
    }
    switchPlayerEpisodeInternal(
        episodeIndex = state.episodeIndex,
        preferredSource = sourceIndex,
        notice = "已切换到「${source.name}」",
        // 同一集换线路要接着看，不能回到 0 秒。
        preservePosition = true,
    )
}

fun DesktopAppState.switchPlayerEpisode(episodeIndex: Int) {
    switchPlayerEpisodeInternal(episodeIndex, player.value?.sourceIndex ?: 0)
}

/** 顶部“已切换到…”提示展示几秒后清掉，避免它一直挂在播放页上。 */
fun DesktopAppState.clearSwitchNotice() {
    updatePlayer { current ->
        if (current?.switchNotice != null) current.copy(switchNotice = null) else current
    }
}

private fun DesktopAppState.switchPlayerEpisodeInternal(
    episodeIndex: Int,
    preferredSource: Int,
    notice: String? = null,
    preservePosition: Boolean = false,
) {
    val state = player.value ?: return
    if (state.isLive) return
    val movie = state.movie ?: return
    val matrix = matrixOf(movie)
    val resolved = matrix.resolvePlayable(preferredSource, episodeIndex) ?: run {
        notify("这一集在所有线路都不可用")
        return
    }
    val (targetSource, targetEpisode) = resolved
    val episode = matrix.episodeOf(targetSource, targetEpisode) ?: run {
        notify("这一集在所有线路都不可用")
        return
    }
    if (notice == null &&
        targetSource == state.sourceIndex &&
        targetEpisode == state.episodeIndex &&
        episode.url == state.currentUrl
    ) {
        return
    }
    val keepPosition = preservePosition && targetEpisode == state.episodeIndex
    val position = if (keepPosition) state.lastPositionMs else 0L
    val duration = if (keepPosition) state.lastDurationMs else 0L
    val sourceChanged = targetSource != state.sourceIndex
    val next = state.withEpisodeSwitch(
        targetSource = targetSource,
        targetEpisode = targetEpisode,
        url = episode.url,
        title = matrix.episodes.getOrNull(targetEpisode)?.title ?: episode.title,
        episodeCount = matrix.episodes.size,
        startPositionMs = position,
        durationMs = duration,
        switchNotice = notice ?: if (sourceChanged) {
            "当前线路缺少这一集，已切到「${movie.playSources.getOrNull(targetSource)?.name}」"
        } else {
            null
        },
    )
    // 真正换集时把旧集看到的位置落盘；换线路是同一集，由 closePlayer/定时保存兜底。
    if (targetEpisode != state.episodeIndex && state.lastPositionMs > 0L) {
        savePlayerProgress(state.lastPositionMs, state.lastDurationMs, state)
    }
    mediaProgress.rememberReplaced(state, next)
    setPlayer(next)
    updateDetail { it.copy(sourceIndex = targetSource, episodeIndex = targetEpisode) }
    prefetchUpcoming(movie, targetSource, targetEpisode)
}

/** 播完自动下一集：跨线路也可用，找不到就提示已播完。 */
fun DesktopAppState.playNextEpisode() {
    val state = player.value ?: return
    if (state.isLive) return
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
    if (state.episodeIndex <= 0) {
        notify("已经是第一集")
        return
    }
    switchPlayerEpisodeInternal(state.episodeIndex - 1, state.sourceIndex)
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
    movie: Movie,
    sourceIndex: Int,
    episodeIndex: Int,
) {
    val count = settings.value.settings.prefetchEpisodeCount
    if (count <= 0) return
    val matrix = matrixOf(movie)
    val urls = (1..count)
        .mapNotNull { offset -> matrix.episodeOf(sourceIndex, episodeIndex + offset)?.url }
    if (urls.isEmpty()) return
    scopeRef.launch {
        runCatching { container.streamPrefetcher.warmUp(urls) }
            .onFailure { AppLog.warn("Playback", "预取失败：${it.message}") }
    }
}

fun DesktopAppState.retryDelayForAttempt(attempt: Int): Long =
    (500L shl (attempt - 1).coerceIn(0, 4)).coerceAtMost(MAX_RETRY_BACKOFF_MS)

// region 进度与健康度

/**
 * 写入观看进度。播放页每 10 秒、切集以及退出时调用，桌面端同样支持跨启动续播。
 *
 * 播放器销毁旧媒体时会再补报一次位置：换集时这份进度属于被替换的上一集，
 * 关闭播放页时它反而是最准的收尾进度，两种都要记到对应的媒体上。
 */
fun DesktopAppState.savePlayerProgress(positionMs: Long, durationMs: Long) {
    val safePosition = positionMs.coerceAtLeast(0L)
    val safeDuration = durationMs.coerceAtLeast(0L)
    val state = player.value
    if (state == null) {
        // 关闭播放页后播放器还会补报一次位置，用它把收尾进度写准。
        val closed = mediaProgress.takeClosed() ?: return
        savePlayerProgress(safePosition, safeDuration, closed)
        return
    }
    if (state.isLive) return
    val replaced = mediaProgress.takeReplaced(state.currentUrl)
    if (replaced != null) {
        savePlayerProgress(safePosition, safeDuration, replaced)
        return
    }
    updatePlayer { current ->
        if (current != null && current.currentUrl == state.currentUrl) {
            current.copy(lastPositionMs = safePosition, lastDurationMs = safeDuration)
        } else {
            current
        }
    }
    savePlayerProgress(safePosition, safeDuration, state)
}

private fun DesktopAppState.savePlayerProgress(positionMs: Long, durationMs: Long, state: PlayerState) {
    if (state.isLive) return
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
    val key = item.historyKey
    val version = progressSaves.next(key)
    val guardVersion = historyWriteGuard.version(item)
    scopeRef.launch {
        var saved = false
        progressSaves.mutex.withLock {
            // 同一部剧只落最新一次；删除/清空历史之后，排队中的旧进度直接丢弃。
            if (!progressSaves.isLatest(key, version)) return@withLock
            if (!historyWriteGuard.isCurrent(item, guardVersion)) return@withLock
            runCatching { historyRepository.saveProgress(item) }
                .onSuccess { items ->
                    saved = true
                    updateHistory { it.copy(items = items) }
                    updateDetail { current ->
                        val currentMovie = current.movie
                        if (currentMovie != null &&
                            currentMovie.apiLineId == item.apiLineId &&
                            currentMovie.id == item.movieId
                        ) {
                            current.copy(resumeHint = item)
                        } else {
                            current
                        }
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    AppLog.warn("Playback", "保存观看进度失败：${error.message}")
                }
        }
        if (saved) markFollowProgress(movie, state.episodeIndex, positionMs.coerceAtLeast(0L))
    }
}

fun DesktopAppState.recordPlaybackSuccess() {
    val state = player.value ?: return
    if (state.isLive) return
    val movie = state.movie ?: return
    val source = movie.playSources.getOrNull(state.sourceIndex) ?: return
    val matrix = matrixOf(movie)
    if (matrix.episodeOf(state.sourceIndex, state.episodeIndex)?.url.isNullOrBlank()) return
    val key = playbackHealthKey(movie.id, state.episodeIndex, source)
    scopeRef.launch {
        runCatching { playbackHealthRepository.recordSuccess(key, System.currentTimeMillis()) }
            .onSuccess { snapshot -> updateSettings { it.copy(playbackHealth = snapshot) } }
            .onFailure { AppLog.warn("Playback", "记录播放成功失败：${it.message}") }
    }
}

fun DesktopAppState.recordPlaybackIssue(issue: PlaybackIssueType) {
    val state = player.value ?: return
    if (state.isLive) return
    val movie = state.movie ?: return
    val source = movie.playSources.getOrNull(state.sourceIndex) ?: return
    val matrix = matrixOf(movie)
    if (matrix.episodeOf(state.sourceIndex, state.episodeIndex)?.url.isNullOrBlank()) return
    val key = playbackHealthKey(movie.id, state.episodeIndex, source)
    AppLog.warn("Playback", "记录播放问题 $issue：${movie.name} / ${source.name} / 第${state.episodeIndex + 1}集")
    scopeRef.launch {
        runCatching { playbackHealthRepository.recordIssue(key, issue, System.currentTimeMillis()) }
            .onSuccess { snapshot -> updateSettings { it.copy(playbackHealth = snapshot) } }
            .onFailure { AppLog.warn("Playback", "记录播放问题失败：${it.message}") }
    }
}

/**
 * 自然播放失败时优先换一条有同一集、且不在冷却中的线路；全部不可用则提示。
 * 返回 true 表示已经切到别的线路。
 */
fun DesktopAppState.handlePlaybackFailure(reason: String): Boolean {
    val state = player.value ?: return false
    if (state.isLive) return false
    val movie = state.movie ?: return false
    // 记在真正失败的这条线路上（切换之后 state.sourceIndex 就变了）。
    recordPlaybackIssue(PlaybackIssueType.Error)
    val matrix = matrixOf(movie)
    val failed = state.failedSourceIndices + state.sourceIndex
    val candidates = failureCandidates(matrix, failed, state.episodeIndex)
    if (candidates.isEmpty()) {
        updatePlayer { current ->
            when {
                current == null -> null
                current.currentUrl != state.currentUrl -> current
                else -> current.copy(failedSourceIndices = failed)
            }
        }
        AppLog.error("Playback", "播放失败且无未尝试的备用线路：$reason")
        notify("播放失败：$reason", type = NoticeType.Error)
        return false
    }
    val preferred = resolvePreferredSource(movie, candidates.first(), state.episodeIndex)
    val target = preferred.takeIf { it in candidates } ?: candidates.first()
    val targetSourceName = movie.playSources.getOrNull(target)?.name.orEmpty()
    val episode = matrix.episodeOf(target, state.episodeIndex) ?: run {
        notify("播放失败：$reason", type = NoticeType.Error)
        return false
    }
    AppLog.warn(
        "Playback",
        "播放失败自动切源：${movie.playSources.getOrNull(state.sourceIndex)?.name} → $targetSourceName（$reason）",
    )
    val next = state.copy(
        sourceIndex = target,
        currentUrl = episode.url,
        startPositionMs = state.lastPositionMs.coerceAtLeast(0L),
        episodeTitle = matrix.episodes.getOrNull(state.episodeIndex)?.title ?: episode.title,
        episodeCount = matrix.episodes.size,
        failedSourceIndices = failed,
        switchNotice = "播放失败，已切换到「$targetSourceName」",
    )
    mediaProgress.rememberReplaced(state, next)
    setPlayer(next)
    updateDetail { it.copy(sourceIndex = target, episodeIndex = state.episodeIndex) }
    prefetchUpcoming(movie, target, state.episodeIndex)
    notify("播放失败，已切换到「$targetSourceName」", type = NoticeType.Warning)
    return true
}

// endregion
