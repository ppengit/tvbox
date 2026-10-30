package com.tvbox.desktop.state

import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.buildEpisodeMatrix
import com.tvbox.desktop.data.MoviePlaybackPolicy

/**
 * 播放策略的生效值：单剧覆盖优先，没有覆盖时回落到全局设置。
 * 线路相关策略在进入播放页时读取一次，避免播放中途被设置页改动打断。
 */
fun DesktopAppState.moviePolicy(movie: Movie?): MoviePlaybackPolicy? {
    if (movie == null) return null
    val openedMovie = detail.value?.movie
    return detail.value
        ?.takeIf { openedMovie != null && openedMovie.apiLineId == movie.apiLineId && openedMovie.id == movie.id }
        ?.policy
        ?: playbackPolicyRepository.find(movie.apiLineId, movie.id)
}

fun DesktopAppState.effectiveAutoSwitch(movie: Movie?): Boolean =
    moviePolicy(movie)?.autoSwitch ?: settings.value.settings.playbackAgentAutoSwitchEnabled

fun DesktopAppState.effectivePreferFastest(movie: Movie?): Boolean =
    moviePolicy(movie)?.preferFastest ?: settings.value.settings.preferFastestLine

/** 三个状态：null 跟随全局，true 强制开启，false 强制关闭。 */
fun DesktopAppState.setMoviePolicy(autoSwitch: Boolean?, preferFastest: Boolean?) {
    val current = detail.value ?: return
    val movie = current.movie ?: return
    val policy = MoviePlaybackPolicy(
        apiLineId = movie.apiLineId,
        movieId = movie.id,
        autoSwitch = autoSwitch,
        preferFastest = preferFastest,
    )
    runCatching { playbackPolicyRepository.save(policy) }
    updateDetail { it.copy(policy = policy) }
    notify("已保存本剧播放策略")
}

/** 测速完成后按策略切到最快线路；当前集在该线路缺集时保持不动。 */
internal fun DesktopAppState.applyFastestSource(
    movie: Movie,
    results: List<com.tvbox.app.data.SpeedResult>,
) {
    // 测速是异步的：完成时用户可能已经换了一部剧，这时不能再用旧剧的线路下标去切播放。
    val playback = player.value
    if (playback != null) {
        val playingMovie = playback.movie
        if (playingMovie == null || playingMovie.apiLineId != movie.apiLineId || playingMovie.id != movie.id) return
    } else {
        val currentMovie = detail.value?.movie
        if (currentMovie == null || currentMovie.apiLineId != movie.apiLineId || currentMovie.id != movie.id) return
    }
    val matrix = buildEpisodeMatrix(movie.playSources)
    val episodeIndex = playback?.episodeIndex ?: detail.value?.episodeIndex ?: 0
    val candidate = movie.playSources.indices
        .filter { results.getOrNull(it)?.ok == true }
        .filter { matrix.availability(it).getOrElse(episodeIndex) { false } }
        .minWithOrNull(compareBy({ results[it].latencyMs.coerceAtLeast(1L) }, { -results[it].kbps }))
        ?: return
    val currentIndex = playback?.sourceIndex ?: detail.value?.sourceIndex
    if (candidate == currentIndex) return
    if (playback != null) {
        switchPlayerSource(candidate)
        // 切源失败（线路没有可播集等）时不能再报“已切到最快线路”。
        if (player.value?.sourceIndex != candidate) return
    } else {
        updateDetail { it.copy(sourceIndex = candidate) }
    }
    notify("已切到最快线路：${movie.playSources.getOrNull(candidate)?.name}")
}
