package com.tvbox.desktop.state

import com.tvbox.app.domain.LiveChannel
import com.tvbox.app.domain.PlatformLiveRoom
import com.tvbox.app.domain.PlatformLiveSite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// region 电视直播

fun DesktopAppState.refreshLiveGroupsIfNeeded(force: Boolean = false) {
    val current = live.value
    if (!force && current.channels.isNotEmpty()) return
    if (current.loading) return
    updateLive { it.copy(loading = true, error = null) }
    liveJob = scopeRef.launch {
        runCatching { liveRepository.getChannels() }
            .onSuccess { channels ->
                val groups = channels.map { it.groupName }.filter { it.isNotBlank() }.distinct()
                updateLive {
                    it.copy(
                        channels = channels,
                        groups = groups,
                        selectedGroup = it.selectedGroup?.takeIf { group -> group in groups } ?: groups.firstOrNull(),
                        loading = false,
                        error = null,
                    )
                }
            }
            .onFailure { error ->
                if (error is CancellationException) throw error
                updateLive { it.copy(loading = false, error = error.userMessage()) }
            }
    }
}

fun DesktopAppState.selectLiveGroup(group: String) {
    updateLive { it.copy(selectedGroup = group) }
}

fun DesktopAppState.openLiveChannel(channel: LiveChannel) {
    // 直播页只展示当前分组的频道，播放页的“上一个/下一个频道”也必须走同一份列表，
    // 否则会跳到用户在分组里根本看不到的频道。
    val playlist = live.value.channels
        .filter { channel.groupName.isBlank() || it.groupName == channel.groupName }
        .ifEmpty { live.value.channels }
    val index = playlist.indexOfFirst { it.number == channel.number && it.name == channel.name }
    val firstLine = channel.lines.firstOrNull() ?: run {
        notify("该频道没有可用线路")
        return
    }
    setPlayer(
        PlayerState(
            liveTitle = channel.name,
            liveChannels = playlist,
            liveChannelIndex = index,
            currentUrl = firstLine.url,
        ),
    )
}

/** 直播切台：在关联的频道列表里前后移动，避开没有线路的频道。 */
fun DesktopAppState.stepLiveChannel(delta: Int) {
    val state = player.value ?: return
    if (!state.isLive || state.liveChannels.isEmpty()) return
    val size = state.liveChannels.size
    var cursor = state.liveChannelIndex
    repeat(size) {
        cursor = ((cursor + delta) % size + size) % size
        val candidate = state.liveChannels[cursor]
        val line = candidate.lines.firstOrNull() ?: return@repeat
        setPlayer(
            state.copy(
                liveTitle = candidate.name,
                liveChannelIndex = cursor,
                currentUrl = line.url,
                switchNotice = null,
            ),
        )
        return
    }
    notify("没有其他可用频道")
}

/** 同频道换线路，用于当前线路卡住时手动切备用源。 */
fun DesktopAppState.stepLiveLine(delta: Int) {
    val state = player.value ?: return
    if (!state.isLive) return
    val channel = state.liveChannels.getOrNull(state.liveChannelIndex) ?: return
    if (channel.lines.size <= 1) return
    // 找不到当前地址（例如线路列表刷新过）时按 -1 处理，从第一条开始轮换。
    val currentIndex = channel.lines.indexOfFirst { it.url == state.currentUrl }
    val size = channel.lines.size
    val nextIndex = ((currentIndex + delta) % size + size) % size
    setPlayer(
        state.copy(
            currentUrl = channel.lines[nextIndex].url,
            switchNotice = "已切换到 ${channel.lines[nextIndex].name}",
        ),
    )
}

// endregion

// region 平台直播

fun DesktopAppState.ensurePlatformLiveLoaded() {
    val current = platformLive.value
    if (current.sites.isNotEmpty() || current.loading) return
    loadPlatformLiveSites(favoritesOnly = current.showFavorites)
}

fun DesktopAppState.loadPlatformLiveSites(favoritesOnly: Boolean = false) {
    val serviceUrl = container.platformLiveServiceUrl
    if (serviceUrl.isBlank()) {
        updatePlatformLive {
            it.copy(
                loading = false,
                error = "未配置平台直播服务地址，可在设置中填写",
                showFavorites = favoritesOnly,
            )
        }
        if (favoritesOnly) loadPlatformLiveFavorites()
        return
    }
    platformLiveJob?.cancel()
    updatePlatformLive { it.copy(loading = true, error = null, showFavorites = favoritesOnly) }
    platformLiveJob = scopeRef.launch {
        val sites = runCatching { platformLiveRepository.getSites(serviceUrl) }
            .getOrElse { error ->
                updatePlatformLive { it.copy(loading = false, error = error.userMessage()) }
                return@launch
            }
        val favorites = runCatching { favoritesRepository.getFavorites() }.getOrDefault(emptyList())
        updatePlatformLive { current ->
            current.copy(
                sites = sites,
                favorites = favorites,
                loading = false,
                error = null,
                selectedSite = current.selectedSite?.takeIf { id -> sites.any { it.id == id } } ?: sites.firstOrNull()?.id,
            )
        }
        if (favoritesOnly) {
            loadPlatformLiveFavorites()
        } else {
            platformLive.value.selectedSite?.let(::selectPlatformLiveSite)
        }
    }
}

fun DesktopAppState.loadPlatformLiveFavorites() {
    scopeRef.launch {
        val favorites = runCatching { favoritesRepository.getFavorites() }.getOrDefault(emptyList())
        updatePlatformLive { it.copy(favorites = favorites, showFavorites = true, loading = false, error = null) }
    }
}

fun DesktopAppState.togglePlatformLiveFavoritesView() {
    val target = !platformLive.value.showFavorites
    updatePlatformLive { it.copy(showFavorites = target) }
    if (target) loadPlatformLiveFavorites() else platformLive.value.selectedSite?.let(::selectPlatformLiveSite)
}

fun DesktopAppState.selectPlatformLiveSite(siteId: String) {
    val serviceUrl = container.platformLiveServiceUrl
    if (serviceUrl.isBlank()) {
        updatePlatformLive { it.copy(error = "未配置平台直播服务地址，可在设置中填写") }
        return
    }
    platformLiveJob?.cancel()
    updatePlatformLive {
        it.copy(
            selectedSite = siteId,
            showFavorites = false,
            loading = true,
            error = null,
            parents = emptyList(),
            categories = emptyList(),
            rooms = emptyList(),
            selectedParentId = null,
            selectedCategoryId = null,
            page = 1,
            pageCount = 1,
        )
    }
    platformLiveJob = scopeRef.launch {
        runCatching { platformLiveRepository.getCategoryTree(serviceUrl, siteId) }
            .onSuccess { tree ->
                val parents = tree.parentCategories
                val firstParent = parents.firstOrNull()
                updatePlatformLive {
                    it.copy(
                        parents = parents,
                        categories = tree.categories,
                        loading = false,
                        selectedParentId = firstParent?.id,
                        selectedCategoryId = tree.categories.firstOrNull { category ->
                            category.parentId == firstParent?.id
                        }?.id,
                    )
                }
                loadPlatformLiveRooms(reset = true)
            }
            .onFailure { error ->
                updatePlatformLive { it.copy(loading = false, error = error.userMessage()) }
            }
    }
}

fun DesktopAppState.selectPlatformLiveParent(parentId: String) {
    updatePlatformLive { current ->
        val firstChild = current.categories.firstOrNull { it.parentId == parentId }
        current.copy(selectedParentId = parentId, selectedCategoryId = firstChild?.id)
    }
    loadPlatformLiveRooms(reset = true)
}

fun DesktopAppState.selectPlatformLiveCategory(categoryId: String) {
    updatePlatformLive { it.copy(selectedCategoryId = categoryId) }
    loadPlatformLiveRooms(reset = true)
}

fun DesktopAppState.loadPlatformLiveRooms(reset: Boolean = false) {
    val state = platformLive.value
    val serviceUrl = container.platformLiveServiceUrl
    val site = state.selectedSite ?: return
    val categoryId = state.selectedCategoryId ?: return
    if (serviceUrl.isBlank()) return
    if (!reset && (state.loading || state.loadingMore || state.page >= state.pageCount)) return

    val requestedPage = if (reset) 1 else state.page + 1
    updatePlatformLive { it.copy(loading = reset, loadingMore = !reset, error = null) }
    scopeRef.launch {
        runCatching { platformLiveRepository.getRooms(serviceUrl, site, categoryId, requestedPage) }
            .onSuccess { page ->
                updatePlatformLive { current ->
                    if (current.selectedCategoryId != categoryId) return@updatePlatformLive current
                    current.copy(
                        rooms = if (reset) page.rooms else (current.rooms + page.rooms).distinctBy { it.id },
                        page = page.page,
                        pageCount = page.pageCount,
                        loading = false,
                        loadingMore = false,
                    )
                }
            }
            .onFailure { error ->
                if (error is CancellationException) throw error
                updatePlatformLive { it.copy(loading = false, loadingMore = false, error = error.userMessage()) }
            }
    }
}

fun DesktopAppState.openPlatformLiveRoom(room: PlatformLiveRoom) {
    val serviceUrl = container.platformLiveServiceUrl
    if (serviceUrl.isBlank()) {
        notify("未配置平台直播服务地址")
        return
    }
    // 连点多个直播间时只让最后一次解析生效，避免先点的慢请求后到、把播放器顶成上一个房间。
    val token = ++liveResolveToken
    scopeRef.launch {
        notify("正在解析「${room.title}」的直播流…")
        runCatching { platformLiveRepository.resolve(serviceUrl, room) }
            .onSuccess { stream ->
                if (liveResolveToken != token) return@onSuccess
                if (screen.value != DesktopScreen.PlatformLive) return@onSuccess
                setPlayer(
                    PlayerState(
                        liveTitle = room.title,
                        currentUrl = stream.url,
                        headers = stream.headers,
                        platformStream = stream,
                    ),
                )
            }
            .onFailure {
                if (liveResolveToken != token) return@onFailure
                notify("解析失败：${it.userMessage()}")
            }
    }
}

fun DesktopAppState.togglePlatformLiveFavorite(room: PlatformLiveRoom) {
    scopeRef.launch {
        runCatching { favoritesRepository.toggleFavorite(room) }
            .onSuccess { favorites ->
                updatePlatformLive { it.copy(favorites = favorites) }
                val isFavorite = favorites.any { it.id == room.id }
                notify(if (isFavorite) "已收藏「${room.title}」" else "已取消收藏")
            }
            .onFailure { notify("收藏失败：${it.userMessage()}") }
    }
}

fun DesktopAppState.savePlatformLiveServiceUrl(url: String) {
    container.platformLiveServiceUrl = url
    updatePlatformLive { PlatformLiveState() }
    if (url.isNotBlank()) {
        loadPlatformLiveSites()
        notify("平台直播服务地址已保存")
    }
}

val DesktopAppState.platformLiveServiceUrl: String
    get() = container.platformLiveServiceUrl

internal val PlatformLiveSite.displayName: String
    get() = name.ifBlank { id }

// endregion
