package com.tvbox.desktop.state

import com.tvbox.app.domain.AppSettings
import com.tvbox.app.domain.DoubanHotItem
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.WatchHistoryItem
import com.tvbox.app.domain.findBestTitleMatchIndex
import com.tvbox.app.domain.historyKey
import com.tvbox.app.domain.resolveHistoryEpisodePosition
import com.tvbox.desktop.data.movieSpeedCacheKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    // 一次只解析一个条目：并发解析会让两个详情请求互相覆盖，也会重复弹提示。
    if (home.value.resolvingTitle != null) return
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
    sourceSpeedJob?.cancel()
    sourceSpeedJob = null
    detailRequestId++
    // 上次测过这部就先把结果回填，用户不用为了排序再等一轮测速。
    val cachedSpeeds = runCatching {
        speedCacheRepository.loadSourceSpeeds(movieSpeedCacheKey(apiLineId, movie.id))
    }.getOrNull()
    setDetail(
        DetailState(
            movie = movie,
            loading = true,
            sourceIndex = movie.preferredSourceIndex(),
            sourceSpeeds = cachedSpeeds?.toResultsByIndex(movie.playSources).orEmpty(),
            sourceSpeedsMeasuredAtMs = cachedSpeeds?.measuredAtMs ?: 0L,
            policy = runCatching { playbackPolicyRepository.find(apiLineId, movie.id) }.getOrNull(),
        ),
    )
    startDetailLoad(movie.id, apiLineId, initial = movie)
}

fun DesktopAppState.openDetail(movieId: Int, apiLineId: String = homeApiLineId) {
    detailJob?.cancel()
    sourceSpeedJob?.cancel()
    sourceSpeedJob = null
    detailRequestId++
    setDetail(DetailState(movie = null, loading = true))
    startDetailLoad(movieId, apiLineId, initial = null)
}

private fun DesktopAppState.startDetailLoad(movieId: Int, apiLineId: String, initial: Movie?) {
    val requestId = detailRequestId
    val cachedSpeeds = runCatching {
        speedCacheRepository.loadSourceSpeeds(movieSpeedCacheKey(apiLineId, movieId))
    }.getOrNull()
    detailJob = scopeRef.launch {
        var received = false
        var policyLoaded = false
        var lineFallbackTried = false
        try {
            movieRepository.getDetailProgressively(apiLineId = apiLineId, id = movieId).collect { update ->
                // 用户已经打开了别的详情页：这次请求的后续更新全部作废。
                if (detailRequestId != requestId) return@collect
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
                    // 慢慢加载出来的播放源也要能对上缓存：索引按当前源列表重新映射。
                    val cachedResults = if (current.sourceSpeeds.isEmpty()) {
                        cachedSpeeds?.toResultsByIndex(update.movie.playSources).orEmpty()
                    } else {
                        emptyMap()
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
                        sourceSpeeds = if (cachedResults.isNotEmpty()) cachedResults else current.sourceSpeeds,
                        sourceSpeedsMeasuredAtMs = if (cachedResults.isNotEmpty()) {
                            cachedSpeeds?.measuredAtMs ?: 0L
                        } else {
                            current.sourceSpeedsMeasuredAtMs
                        },
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
                        if (detailRequestId != requestId) return@launch
                        if (detail.value?.movie?.name != update.movie.name) return@launch
                        notify("已自动切到「${found.first.apiLineName.ifBlank { found.second }}」线路")
                        openDetail(movie = found.first, apiLineId = found.second)
                    }
                }
            }
            if (detailRequestId != requestId) return@launch
            if (!received) {
                updateDetail { it.copy(loading = false, error = "影片详情不存在") }
            } else if (settings.value.settings.speedTestOnOpen) {
                runSourceSpeedTest()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (detailRequestId != requestId) return@launch
            updateDetail { it.copy(loading = false, error = error.userMessage()) }
        }
    }
    if (initial != null) {
        loadResumeHint(movieId, apiLineId, requestId)
    }
}

private fun DesktopAppState.loadResumeHint(movieId: Int, apiLineId: String, requestId: Long) {
    scopeRef.launch {
        val item = runCatching { historyRepository.getHistory() }.getOrNull()
            // 不同源站的 id 可能撞车，必须连线路一起匹配，否则会把别的剧的进度挂上来。
            ?.firstOrNull { it.movieId == movieId && it.apiLineId == apiLineId }
            ?: return@launch
        if (detailRequestId != requestId) return@launch
        updateDetail { current ->
            val currentMovie = current.movie
            if (currentMovie != null && currentMovie.id == movieId && currentMovie.apiLineId == apiLineId) {
                current.copy(resumeHint = item)
            } else {
                current
            }
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
 * 统一集数下这条线路播不了这一集时，换成第一条能播这一集的线路。
 * 索引一律是统一集数下标，避免把线路自己的剧集下标当成统一下标用。
 */
internal fun playableSourceIndex(
    matrix: com.tvbox.app.domain.EpisodeMatrix,
    requested: Int,
    episodeIndex: Int,
): Int {
    if (matrix.sources.isEmpty()) return 0
    val safeRequested = requested.coerceIn(0, matrix.sources.lastIndex)
    if (matrix.availability(safeRequested).getOrElse(episodeIndex) { false }) return safeRequested
    return matrix.resolvePlayable(safeRequested, episodeIndex)?.first ?: safeRequested
}

/**
 * 是否自动换源取决于设置，命中冷却中的坏源时优先选健康线路。
 * 这段判断和电视端共用同一个 [com.tvbox.app.domain.PlaybackAgent]。
 *
 * PlaybackAgent 按「每条线路自己的剧集下标」判断候选，而桌面端传的是统一集数下标，
 * 两者在线路集数不一致时会错位，所以结果必须再用 EpisodeMatrix 校验一次。
 */
internal fun DesktopAppState.resolvePreferredSource(movie: Movie, requested: Int, episodeIndex: Int): Int {
    val sources = movie.playSources
    if (sources.isEmpty()) return 0
    val matrix = com.tvbox.app.domain.buildEpisodeMatrix(sources)
    val safeRequested = requested.coerceIn(0, sources.lastIndex)
    // 请求的线路在统一集数下没有这一集时，先按矩阵换到有这一集的线路。
    val fallback = playableSourceIndex(matrix, safeRequested, episodeIndex)
    if (!settings.value.settings.playbackAgentAutoSwitchEnabled) return fallback
    val selected = playbackAgent.selectBestSource(
        movie = movie,
        requestedSourceIndex = safeRequested,
        episodeIndex = episodeIndex,
        healthSnapshot = settings.value.playbackHealth,
    )?.sourceIndex ?: return fallback
    return if (matrix.availability(selected).getOrElse(episodeIndex) { false }) selected else fallback
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
    // 已经有一条续播在解析中：忽略重复点击，避免两个请求同时打开播放器。
    if (history.value.resumingKey != null) return
    updateHistory { it.copy(resumingKey = item.historyKey) }
    scopeRef.launch {
        try {
            // 详情页上点「继续播放」时，同一部剧的详情已经加载好了：直接复用，
            // 不要重新拉详情——那会把追剧状态、测速结果和续播提示一起重置掉。
            val openedMovie = detail.value
                ?.movie
                ?.takeIf { it.apiLineId == item.apiLineId && it.id == item.movieId }
            val movie = openedMovie ?: run {
                detailJob?.cancel()
                sourceSpeedJob?.cancel()
                sourceSpeedJob = null
                detailRequestId++
                runCatching { movieRepository.getResumeDetail(item.apiLineId, item.movieId) }.getOrNull()
                    // 原来的线路下架了就按片名自动找一条，不再把用户赶回搜索页。
                    ?: findMovieOnAnyLine(item.movieName, preferredLineId = homeApiLineId)?.also { (match, lineId) ->
                        notify("原线路不可用，已自动切到「${match.apiLineName.ifBlank { lineId }}」")
                    }?.first
            }
            if (movie == null) {
                notify("各线路都没有找到《${item.movieName}》")
                return@launch
            }
            val (sourceIndex, episodeIndex) = resolveHistoryEpisodePosition(movie, item)
            if (openedMovie == null) {
                val cachedSpeeds = runCatching {
                    speedCacheRepository.loadSourceSpeeds(movieSpeedCacheKey(movie.apiLineId, movie.id))
                }.getOrNull()
                setDetail(
                    DetailState(
                        movie = movie,
                        movieId = movie.id,
                        loading = false,
                        sourceIndex = sourceIndex,
                        episodeIndex = episodeIndex,
                        sourceSpeeds = cachedSpeeds?.toResultsByIndex(movie.playSources).orEmpty(),
                        sourceSpeedsMeasuredAtMs = cachedSpeeds?.measuredAtMs ?: 0L,
                        policy = runCatching {
                            playbackPolicyRepository.find(movie.apiLineId, movie.id)
                        }.getOrNull(),
                        followed = isFollowed(movie.apiLineId, movie.id),
                    ),
                )
            }
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
        // 和进度保存共用一把锁：删除期间排队中的进度要么先写完（随后被删掉），
        // 要么在删除后因为写守卫失效而直接丢弃，不会把记录复活。
        val result = progressSaves.mutex.withLock {
            historyWriteGuard.remove(item)
            runCatching { historyRepository.removeHistory(item.apiLineId, item.movieId) }
        }
        result
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
        val result = progressSaves.mutex.withLock {
            historyWriteGuard.clear()
            runCatching { historyRepository.clearHistory() }
        }
        result
            .onSuccess {
                updateHistory { HistoryState(items = emptyList()) }
                notify("已清空全部观看记录", type = NoticeType.Success)
            }
            .onFailure { notify("清空失败：${it.userMessage()}", type = NoticeType.Error) }
    }
}

// endregion

// region 设置与播放健康度

/**
 * 设置保存队列。
 *
 * 设置是「整张表」持久化的：连续拨动多个开关时，多个协程并发写同一文件，
 * 先发出的旧快照可能后落盘，把后改的值覆盖掉。这里只让最新快照真正写入，
 * 并记住最后一次成功持久化的值，写失败时把界面回退到它，避免“看起来保存了”。
 */
internal class SettingsSaveQueue {
    val mutex = Mutex()
    private var version = 0L
    private var persisted: AppSettings? = null

    fun next(): Long {
        version++
        return version
    }

    fun isLatest(candidate: Long): Boolean = candidate == version

    fun rememberPersisted(settings: AppSettings) {
        persisted = settings
    }

    fun persistedOr(fallback: AppSettings): AppSettings = persisted ?: fallback
}

fun DesktopAppState.saveSettings(transform: (AppSettings) -> AppSettings) {
    val previous = settings.value.settings
    val next = transform(previous)
    if (next == previous) return
    val version = settingsSaves.next()
    updateSettings { it.copy(settings = next, saving = true) }
    scopeRef.launch {
        settingsSaves.mutex.withLock {
            // 已经有更新的快照在排队：这次不用写盘，等最新那次落盘即可。
            if (!settingsSaves.isLatest(version)) return@withLock
            runCatching { settingsRepository.saveSettings(next) }
                .onSuccess { saved ->
                    settingsSaves.rememberPersisted(saved)
                    if (!settingsSaves.isLatest(version)) return@onSuccess
                    updateSettings { it.copy(settings = saved, saving = false) }
                    com.tvbox.app.data.NetworkProxy.configureFromSettings(saved)
                    restartSpeedTimer()
                    restartFollowTimer()
                    notify("设置已保存")
                }
                .onFailure { error ->
                    if (!settingsSaves.isLatest(version)) return@onFailure
                    // 磁盘没写成功就不能继续显示未持久化的值。
                    updateSettings {
                        it.copy(
                            settings = settingsSaves.persistedOr(previous),
                            saving = false,
                        )
                    }
                    notify("保存设置失败：${error.userMessage()}", type = NoticeType.Error)
                }
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
