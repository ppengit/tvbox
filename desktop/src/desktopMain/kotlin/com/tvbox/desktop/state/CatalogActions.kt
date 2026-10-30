package com.tvbox.desktop.state

import com.tvbox.app.domain.AppSettings
import com.tvbox.app.domain.DoubanHotItem
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.WatchHistoryItem
import com.tvbox.app.domain.findBestTitleMatchIndex
import com.tvbox.app.domain.historyKey
import com.tvbox.app.domain.resolveHistoryEpisodePosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val HOME_API_FALLBACK = "liangzi"

internal val DesktopAppState.homeApiLineId: String
    get() = settings.value.settings.homeApiLineId.ifBlank { HOME_API_FALLBACK }

// region 首页

fun DesktopAppState.loadHome(reset: Boolean = false, forceRefresh: Boolean = false) {
    val mode = com.tvbox.app.domain.HomeFeedMode.fromStorageKey(settings.value.settings.homeFeed)
    if (reset) {
        homeJob?.cancel()
        updateHome { it.copy(mode = mode, loading = it.items.isEmpty() && it.movies.isEmpty(), loadingMore = false) }
    } else {
        val current = home.value
        // 设置里的数据源刚切换过时，旧列表不能继续翻页，直接重载第一页。
        if (current.mode != mode) {
            loadHome(reset = true)
            return
        }
        if (current.loading || current.loadingMore || current.page >= current.pageCount) return
        updateHome { it.copy(loadingMore = true) }
    }

    homeJob = scopeRef.launch {
        val requestedPage = if (reset) 1 else home.value.page + 1
        if (mode == com.tvbox.app.domain.HomeFeedMode.Latest) {
            loadHomeLatest(requestedPage, reset)
        } else {
            loadHomeDouban(requestedPage, reset, forceRefresh)
        }
    }
}

private suspend fun DesktopAppState.loadHomeLatest(page: Int, reset: Boolean) {
    runCatching {
        movieRepository.getMovies(apiLineId = homeApiLineId, page = page)
    }.onSuccess { paged ->
        updateHome { current ->
            val merged = if (reset) paged.movies else (current.movies + paged.movies).distinctBy { "${it.apiLineId}-${it.id}" }
            current.copy(
                movies = merged,
                items = emptyList(),
                page = paged.page,
                pageCount = paged.pageCount,
                loading = false,
                loadingMore = false,
                error = null,
                ready = true,
            )
        }
    }.onFailure { error ->
        if (error is CancellationException) throw error
        updateHome {
            it.copy(
                loading = false,
                loadingMore = false,
                ready = true,
                error = if (it.movies.isEmpty()) error.userMessage() else null,
            )
        }
        com.tvbox.app.data.AppLog.warn("Home", "加载本站最新失败：${error.userMessage()}")
        if (home.value.movies.isNotEmpty()) notify("加载更多失败：${error.userMessage()}")
    }
}

private suspend fun DesktopAppState.loadHomeDouban(page: Int, reset: Boolean, forceRefresh: Boolean) {
    runCatching {
        doubanRepository.getRecentHot(
            page = page,
            forceRefresh = forceRefresh,
            category = com.tvbox.app.data.DoubanHotCategory.Tv,
        )
    }.onSuccess { paged ->
        updateHome { current ->
            val merged = if (reset) {
                paged.items
            } else {
                (current.items + paged.items).distinctBy { it.doubanId }
            }
            current.copy(
                items = merged,
                movies = emptyList(),
                page = paged.page,
                pageCount = paged.pageCount,
                loading = false,
                loadingMore = false,
                error = null,
                ready = true,
            )
        }
    }.onFailure { error ->
        if (error is CancellationException) throw error
        updateHome {
            it.copy(
                loading = false,
                loadingMore = false,
                ready = true,
                error = if (it.items.isEmpty()) error.userMessage() else null,
            )
        }
        if (home.value.items.isNotEmpty()) notify("加载更多失败：${error.userMessage()}")
    }
}

/** 切换首页内容来源：豆瓣热播 / 当前站最新更新。 */
fun DesktopAppState.setHomeFeed(mode: com.tvbox.app.domain.HomeFeedMode) {
    val current = settings.value.settings
    if (com.tvbox.app.domain.HomeFeedMode.fromStorageKey(current.homeFeed) == mode) return
    saveSettings { it.copy(homeFeed = mode.storageKey) }
    updateHome {
        HomeState(mode = mode, loading = true, ready = false)
    }
    loadHome(reset = true)
}

fun DesktopAppState.refreshHome() {
    loadHome(reset = true, forceRefresh = true)
}

/**
 * 豆瓣条目只有标题和海报，真正可播放的资源要在当前影视线路里按片名反查。
 * 命中后进入详情页，由详情页继续补齐播放源。
 */
fun DesktopAppState.openDoubanItem(item: DoubanHotItem) {
    if (home.value.resolvingTitle == item.title) return
    updateHome { it.copy(resolvingTitle = item.title) }
    scopeRef.launch {
        val apiLineId = homeApiLineId
        runCatching {
            val keyword = item.title.trim()
            require(keyword.isNotBlank()) { "片名为空" }
            val movies = movieRepository.getMovies(
                apiLineId = apiLineId,
                page = 1,
                keyword = keyword,
            ).movies
            val matchedIndex = findBestTitleMatchIndex(keyword, movies.map { it.name })
            matchedIndex?.let(movies::getOrNull)
        }.onSuccess { movie ->
            updateHome { it.copy(resolvingTitle = null) }
            if (movie == null) {
                // 首页只绑一个源站，但点片不该被这个绑定卡住：按片名去其他线路里找。
                scopeRef.launch {
                    val found = findMovieOnAnyLine(item.title, preferredLineId = apiLineId)
                    if (found == null) {
                        notify("各线路都没有找到《${item.title}》")
                    } else {
                        val (match, lineId) = found
                        notify("已自动切到「${match.apiLineName.ifBlank { lineId }}」线路")
                        openDetail(movie = match, apiLineId = lineId)
                    }
                }
            } else {
                openDetail(movie = movie, apiLineId = apiLineId)
            }
        }.onFailure { error ->
            updateHome { it.copy(resolvingTitle = null) }
            notify("查找《${item.title}》失败：${error.userMessage()}")
        }
    }
}

/**
 * 按片名在其余线路里找一条有结果的。
 *
 * 桌面端首页只挑一个源站展示，但播放不该被这个绑定限制住——豆瓣点片、历史续播
 * 都走这里兜底，用户不需要自己回设置里换线路。
 */
internal suspend fun DesktopAppState.findMovieOnAnyLine(
    title: String,
    preferredLineId: String? = null,
    excludeApiLineId: String? = null,
): Pair<Movie, String>? {
    val keyword = title.trim()
    if (keyword.isBlank()) return null
    val lines = runCatching { movieRepository.apiLines }.getOrDefault(emptyList())
        .filter { it.id != excludeApiLineId }
        .sortedBy { if (it.id == preferredLineId) 0 else 1 }
        .take(16)
    for (line in lines) {
        val match = runCatching {
            val movies = movieRepository.getMovies(apiLineId = line.id, page = 1, keyword = keyword).movies
            findBestTitleMatchIndex(keyword, movies.map { it.name })?.let(movies::getOrNull)
        }.getOrNull() ?: continue
        return match to line.id
    }
    return null
}

// endregion

// region 搜索

fun DesktopAppState.search(keyword: String = search.value.query) {
    val query = keyword.trim()
    if (query.isBlank()) {
        updateSearch { it.copy(results = emptyList(), error = "请输入搜索关键词", searched = true) }
        return
    }
    searchJob?.cancel()
    recordSearch(query)
    updateSearch {
        it.copy(
            query = query,
            submittedQuery = query,
            results = emptyList(),
            loading = true,
            error = null,
            searched = true,
            completedSources = 0,
            totalSources = 0,
        )
    }
    val apiLineId = homeApiLineId
    searchJob = scopeRef.launch {
        try {
            movieRepository.searchProgressively(
                apiLineId = apiLineId,
                page = 1,
                keyword = query,
            ).collect { update ->
                updateSearch { current ->
                    val finished = update.completedSources >= update.totalSources
                    current.copy(
                        results = update.movies,
                        completedSources = update.completedSources,
                        totalSources = update.totalSources,
                        loading = !finished,
                        error = if (finished && update.movies.isEmpty()) "没有找到相关影片" else null,
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            updateSearch { it.copy(loading = false, error = error.userMessage()) }
        }
    }
}

// endregion

// region 详情

fun DesktopAppState.openDetail(movie: Movie, apiLineId: String = movie.apiLineId) {
    detailJob?.cancel()
    setDetail(
        DetailState(
            movie = movie,
            loading = true,
            sourceIndex = movie.preferredSourceIndex(),
            policy = runCatching { playbackPolicyRepository.find(apiLineId, movie.id) }.getOrNull(),
        ),
    )
    startDetailLoad(movie.id, apiLineId, initial = movie)
}

fun DesktopAppState.openDetail(movieId: Int, apiLineId: String = homeApiLineId) {
    detailJob?.cancel()
    setDetail(DetailState(movie = null, loading = true))
    startDetailLoad(movieId, apiLineId, initial = null)
}

private fun DesktopAppState.startDetailLoad(movieId: Int, apiLineId: String, initial: Movie?) {
    detailJob = scopeRef.launch {
        var received = false
        var policyLoaded = false
        var lineFallbackTried = false
        try {
            movieRepository.getDetailProgressively(apiLineId = apiLineId, id = movieId).collect { update ->
                received = true
                updateDetail { current ->
                    val previousMovie = current.movie
                    // 已经选过线路就保持用户的选择；渐进加载每来一批源都把索引重置，
                    // 会让详情页的选中态自己乱跳。
                    val sourceIndex = if (previousMovie != null) {
                        current.sourceIndex.coerceIn(0, update.movie.playSources.lastIndex.coerceAtLeast(0))
                    } else {
                        resolvePreferredSource(update.movie, update.movie.preferredSourceIndex(), 0)
                    }
                    val matrix = com.tvbox.app.domain.buildEpisodeMatrix(update.movie.playSources)
                    val episodeIndex = current.episodeIndex.coerceIn(
                        0,
                        (matrix.episodes.size - 1).coerceAtLeast(0),
                    )
                    val policy = if (policyLoaded) {
                        current.policy
                    } else {
                        policyLoaded = true
                        runCatching { playbackPolicyRepository.find(apiLineId, update.movie.id) }.getOrNull()
                    }
                    current.copy(
                        movie = update.movie,
                        movieId = update.movie.id,
                        loading = false,
                        error = null,
                        completedSources = update.completedSources,
                        totalSources = update.totalSources,
                        sourceIndex = sourceIndex,
                        episodeIndex = episodeIndex,
                        policy = policy,
                    )
                }
                // 这条线路最后一条播放源都没有时，别再让用户自己换线路，按片名自动找一条。
                if (!lineFallbackTried && update.movie.playSources.isEmpty() &&
                    update.totalSources > 0 && update.completedSources >= update.totalSources
                ) {
                    lineFallbackTried = true
                    scopeRef.launch {
                        val found = findMovieOnAnyLine(
                            title = update.movie.name,
                            preferredLineId = homeApiLineId,
                            excludeApiLineId = apiLineId,
                        ) ?: return@launch
                        if (detail.value?.movie?.name != update.movie.name) return@launch
                        notify("已自动切到「${found.first.apiLineName.ifBlank { found.second }}」线路")
                        openDetail(movie = found.first, apiLineId = found.second)
                    }
                }
            }
            if (!received) {
                updateDetail { it.copy(loading = false, error = "影片详情不存在") }
            } else if (settings.value.settings.speedTestOnOpen) {
                runSourceSpeedTest()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (initial != null) {
                updateDetail { it.copy(loading = false, error = error.userMessage()) }
            } else {
                updateDetail { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }
    if (initial != null) {
        loadResumeHint(movieId, apiLineId)
    }
}

private fun DesktopAppState.loadResumeHint(movieId: Int, apiLineId: String) {
    scopeRef.launch {
        val item = runCatching { historyRepository.getHistory() }.getOrNull()
            // 不同源站的 id 可能撞车，必须连线路一起匹配，否则会把别的剧的进度挂上来。
            ?.firstOrNull { it.movieId == movieId && it.apiLineId == apiLineId }
            ?: return@launch
        updateDetail { current ->
            if (current.movie?.id == movieId) current.copy(resumeHint = item) else current
        }
    }
}

fun DesktopAppState.selectDetailSource(index: Int) {
    updateDetail { current ->
        val movie = current.movie ?: return@updateDetail current
        val matrix = com.tvbox.app.domain.buildEpisodeMatrix(movie.playSources)
        val target = index.coerceIn(0, movie.playSources.lastIndex.coerceAtLeast(0))
        val targetEpisode = matrix.resolveSourceSwitch(target, current.episodeIndex)?.second
            ?: current.episodeIndex.coerceIn(0, matrix.episodes.lastIndex.coerceAtLeast(0))
        current.copy(sourceIndex = target, episodeIndex = targetEpisode)
    }
}

fun DesktopAppState.selectDetailEpisode(index: Int) {
    updateDetail { current ->
        val matrix = current.movie?.let { com.tvbox.app.domain.buildEpisodeMatrix(it.playSources) }
            ?: return@updateDetail current
        current.copy(episodeIndex = index.coerceIn(0, matrix.episodes.lastIndex.coerceAtLeast(0)))
    }
}

/**
 * 是否自动换源取决于设置，命中冷却中的坏源时优先选健康线路。
 * 这段判断和电视端共用同一个 [com.tvbox.app.domain.PlaybackAgent]。
 */
internal fun DesktopAppState.resolvePreferredSource(movie: Movie, requested: Int, episodeIndex: Int): Int {
    val safeRequested = requested.coerceIn(0, movie.playSources.lastIndex.coerceAtLeast(0))
    if (movie.playSources.isEmpty()) return 0
    if (!settings.value.settings.playbackAgentAutoSwitchEnabled) return safeRequested
    return playbackAgent.selectBestSource(
        movie = movie,
        requestedSourceIndex = safeRequested,
        episodeIndex = episodeIndex,
        healthSnapshot = settings.value.playbackHealth,
    )?.sourceIndex ?: safeRequested
}

// endregion

// region 历史

fun DesktopAppState.loadHistory() {
    scopeRef.launch {
        updateHistory { it.copy(loading = it.items.isEmpty(), error = null) }
        runCatching { historyRepository.getHistory() }
            .onSuccess { items -> updateHistory { HistoryState(items = items, loading = false) } }
            .onFailure { error -> updateHistory { it.copy(loading = false, error = error.userMessage()) } }
    }
}

fun DesktopAppState.resumeHistory(item: WatchHistoryItem) {
    scopeRef.launch {
        updateHistory { it.copy(resumingKey = item.historyKey) }
        try {
            val movie = runCatching { movieRepository.getDetail(item.apiLineId, item.movieId) }.getOrNull()
            // 原来的线路下架了就按片名自动找一条，不再把用户赶回搜索页。
            ?: findMovieOnAnyLine(item.movieName, preferredLineId = homeApiLineId)?.also { (match, lineId) ->
                notify("原线路不可用，已自动切到「${match.apiLineName.ifBlank { lineId }}」")
            }?.first
        if (movie == null) {
            notify("各线路都没有找到《${item.movieName}》")
            return@launch
        }
        val (sourceIndex, episodeIndex) = resolveHistoryEpisodePosition(movie, item)
        setDetail(
            DetailState(
                movie = movie,
                loading = false,
                sourceIndex = sourceIndex,
                episodeIndex = episodeIndex,
                policy = runCatching { playbackPolicyRepository.find(item.apiLineId, item.movieId) }.getOrNull(),
            ),
        )
        playDetailEpisode(sourceIndex = sourceIndex, episodeIndex = episodeIndex, startPositionMs = item.positionMs)
        } finally {
            updateHistory { if (it.resumingKey == item.historyKey) it.copy(resumingKey = null) else it }
        }
    }
}

/** 撤销提示需要留够点击时间，比普通提示多停 3 秒。 */
private const val UNDO_NOTICE_DURATION_MS = 6_000L

fun DesktopAppState.removeHistory(item: WatchHistoryItem) {
    scopeRef.launch {
        runCatching { historyRepository.removeHistory(item.apiLineId, item.movieId) }
            .onSuccess { items ->
                updateHistory { HistoryState(items = items.filterNot { it.historyKey == item.historyKey }) }
                // 删除前先保留整条快照，撤销时原样写回（含观看进度与时间）。
                notify(
                    text = "已删除《${item.movieName}》的观看记录",
                    type = NoticeType.Success,
                    action = NoticeAction(label = "撤销") { restoreHistory(item) },
                    durationMs = UNDO_NOTICE_DURATION_MS,
                )
            }
            .onFailure { notify("删除失败：${it.userMessage()}", type = NoticeType.Error) }
    }
}

/** 把删除前的快照写回历史；用于单条删除后的「撤销」。 */
fun DesktopAppState.restoreHistory(item: WatchHistoryItem) {
    scopeRef.launch {
        runCatching { historyRepository.restoreHistory(item) }
            .onSuccess { items ->
                updateHistory { it.copy(items = items, error = null) }
                notify("已恢复《${item.movieName}》的观看记录", type = NoticeType.Success)
            }
            .onFailure { notify("撤销失败：${it.userMessage()}", type = NoticeType.Error) }
    }
}

fun DesktopAppState.clearHistory() {
    scopeRef.launch {
        runCatching { historyRepository.clearHistory() }
            .onSuccess {
                updateHistory { HistoryState(items = emptyList()) }
                notify("已清空全部观看记录", type = NoticeType.Success)
            }
            .onFailure { notify("清空失败：${it.userMessage()}", type = NoticeType.Error) }
    }
}

// endregion

// region 设置与播放健康度

fun DesktopAppState.saveSettings(transform: (AppSettings) -> AppSettings) {
    val next = transform(settings.value.settings)
    updateSettings { it.copy(settings = next, saving = true) }
    scopeRef.launch {
        runCatching { settingsRepository.saveSettings(next) }
            .onSuccess { saved ->
                updateSettings { it.copy(settings = saved, saving = false) }
                com.tvbox.app.data.NetworkProxy.configureFromSettings(saved)
                restartSpeedTimer()
                restartFollowTimer()
                notify("设置已保存")
            }
            .onFailure { error ->
                updateSettings { it.copy(saving = false) }
                notify("保存设置失败：${error.userMessage()}")
            }
    }
}

internal fun DesktopAppState.loadPlaybackHealth() {
    scopeRef.launch {
        val snapshot = runCatching { playbackHealthRepository.getSnapshot() }.getOrNull() ?: return@launch
        updateSettings { it.copy(playbackHealth = snapshot) }
    }
}

fun DesktopAppState.clearPlaybackHealth() {
    scopeRef.launch {
        runCatching { playbackHealthRepository.clear() }
            .onSuccess { snapshot ->
                updateSettings { it.copy(playbackHealth = snapshot) }
                notify("已清空播放健康记录")
            }
    }
}

// endregion
