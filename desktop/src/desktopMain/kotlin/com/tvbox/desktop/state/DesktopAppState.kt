package com.tvbox.desktop.state

import com.tvbox.app.data.DoubanHotRepository
import com.tvbox.app.data.HistoryRepository
import com.tvbox.app.data.LiveRepository
import com.tvbox.app.data.MovieRepository
import com.tvbox.app.data.PlatformLiveFavoritesRepository
import com.tvbox.app.data.PlatformLiveRepository
import com.tvbox.app.data.PlaybackHealthRepository
import com.tvbox.app.data.AppSettingsRepository
import com.tvbox.app.data.AppLog
import com.tvbox.app.data.LogLevel
import com.tvbox.app.data.SpeedResult
import com.tvbox.app.domain.AppSettings
import com.tvbox.app.domain.PlaybackAgent
import com.tvbox.desktop.data.AppContainer
import com.tvbox.desktop.data.FollowedShow
import com.tvbox.desktop.data.FollowedShowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 桌面端唯一的应用状态机。
 *
 * 与 Android 端共享仓库与领域策略，但 UI 状态、导航层级和播放器生命周期都按桌面
 * 交互重新设计：页面是可回退的层级，详情与播放叠加在页面上方，不占用侧边栏导航。
 */
class DesktopAppState(
    val container: AppContainer,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {

    internal val settingsRepository: AppSettingsRepository = container.appSettingsRepository
    internal val historyRepository: HistoryRepository = container.historyRepository
    internal val movieRepository: MovieRepository = container.movieRepository
    internal val doubanRepository: DoubanHotRepository = container.doubanHotRepository
    internal val liveRepository: LiveRepository = container.liveRepository
    internal val platformLiveRepository: PlatformLiveRepository = container.platformLiveRepository
    internal val favoritesRepository: PlatformLiveFavoritesRepository = container.platformLiveFavoritesRepository
    internal val playbackHealthRepository: PlaybackHealthRepository = container.playbackHealthRepository
    internal val followedShowRepository: FollowedShowRepository = container.followedShowRepository
    internal val playbackPolicyRepository: com.tvbox.desktop.data.PlaybackPolicyRepository =
        container.playbackPolicyRepository
    internal val skipMarksRepository: com.tvbox.desktop.data.SkipMarksRepository = container.skipMarksRepository
    internal val playbackAgent = PlaybackAgent()

    internal val scopeRef: CoroutineScope get() = scope

    /** 片库聚合时按分类名解析各站点 typeId 的缓存（站点 id 互不通用）。 */
    internal val categoryTypeIndex = mutableMapOf<String, Map<String, Int>>()

    private val _screen = MutableStateFlow(DesktopScreen.Home)
    val screen: StateFlow<DesktopScreen> = _screen.asStateFlow()

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _category = MutableStateFlow(CategoryState())
    val category: StateFlow<CategoryState> = _category.asStateFlow()

    private val _follow = MutableStateFlow(FollowState())
    val follow: StateFlow<FollowState> = _follow.asStateFlow()

    private val _speedTest = MutableStateFlow(SpeedTestState())
    val speedTest: StateFlow<SpeedTestState> = _speedTest.asStateFlow()

    private val _logs = MutableStateFlow(LogState())
    val logs: StateFlow<LogState> = _logs.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _detail = MutableStateFlow<DetailState?>(null)
    val detail: StateFlow<DetailState?> = _detail.asStateFlow()

    private val _history = MutableStateFlow(HistoryState())
    val history: StateFlow<HistoryState> = _history.asStateFlow()

    private val _live = MutableStateFlow(LiveState())
    val live: StateFlow<LiveState> = _live.asStateFlow()

    private val _platformLive = MutableStateFlow(PlatformLiveState())
    val platformLive: StateFlow<PlatformLiveState> = _platformLive.asStateFlow()

    private val _settings = MutableStateFlow(AppSettingsState())
    val settings: StateFlow<AppSettingsState> = _settings.asStateFlow()

    private val _player = MutableStateFlow<PlayerState?>(null)
    val player: StateFlow<PlayerState?> = _player.asStateFlow()

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    /** 每部剧的片头片尾标记，按 apiLineId:movieId 索引。 */
    private val _skipMarks = MutableStateFlow<Map<String, com.tvbox.desktop.data.SkipMarks>>(emptyMap())
    val skipMarks: StateFlow<Map<String, com.tvbox.desktop.data.SkipMarks>> = _skipMarks.asStateFlow()

    internal val searchHistoryRepository: com.tvbox.desktop.data.SearchHistoryRepository =
        container.searchHistoryRepository

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private var started = false
    private var noticeCounter = 0L

    internal var homeJob: Job? = null
    internal var searchJob: Job? = null
    internal var categoryJob: Job? = null
    internal var followJob: Job? = null
    internal var speedJob: Job? = null
    internal var speedTimerJob: Job? = null
    internal var followTimerJob: Job? = null
    internal var detailJob: Job? = null
    internal var liveJob: Job? = null
    internal var platformLiveJob: Job? = null

    internal fun updateHome(block: (HomeState) -> HomeState) {
        _home.value = block(_home.value)
    }

    internal fun updateCategory(block: (CategoryState) -> CategoryState) {
        _category.value = block(_category.value)
    }

    internal fun updateFollow(block: (FollowState) -> FollowState) {
        _follow.value = block(_follow.value)
    }

    internal fun updateSpeedTest(block: (SpeedTestState) -> SpeedTestState) {
        _speedTest.value = block(_speedTest.value)
    }

    internal fun updateLogs(block: (LogState) -> LogState) {
        _logs.value = block(_logs.value)
    }

    internal fun updateSearch(block: (SearchState) -> SearchState) {
        _search.value = block(_search.value)
    }

    internal fun updateDetail(block: (DetailState) -> DetailState) {
        val current = _detail.value ?: return
        _detail.value = block(current)
    }

    internal fun updateHistory(block: (HistoryState) -> HistoryState) {
        _history.value = block(_history.value)
    }

    internal fun updateLive(block: (LiveState) -> LiveState) {
        _live.value = block(_live.value)
    }

    internal fun updatePlatformLive(block: (PlatformLiveState) -> PlatformLiveState) {
        _platformLive.value = block(_platformLive.value)
    }

    internal fun updateSettings(block: (AppSettingsState) -> AppSettingsState) {
        _settings.value = block(_settings.value)
    }

    internal fun updatePlayer(block: (PlayerState?) -> PlayerState?) {
        _player.value = block(_player.value)
    }

    internal fun updateSkipMarks(
        block: (Map<String, com.tvbox.desktop.data.SkipMarks>) ->
        Map<String, com.tvbox.desktop.data.SkipMarks>,
    ) {
        _skipMarks.value = block(_skipMarks.value)
    }

    internal fun setDetail(state: DetailState?) {
        _detail.value = state
    }

    internal fun setPlayer(state: PlayerState?) {
        _player.value = state
    }

    /** 应用启动后调用一次：先读设置，再按设置里的首页源加载内容。 */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            val loaded = runCatching { settingsRepository.getSettings() }
                .getOrElse {
                    notify("读取设置失败，已使用默认配置", type = NoticeType.Error)
                    AppSettings()
                }
            _settings.value = _settings.value.copy(settings = loaded, ready = true)
            // 首页数据源来自设置，必须等设置就绪后再加载，否则会按默认源请求。
            loadHome(reset = true)
        }
        loadHistory()
        loadPlaybackHealth()
        refreshLiveGroupsIfNeeded()
        loadFollows()
        loadSkipMarks()
        loadSearchHistory()
        refreshFollowsIfStale()
        observeLogs()
        restartSpeedTimer()
        restartFollowTimer()
    }

    /**
     * 弹出一条轻提示。默认是信息型，停留 3 秒；错误型停留 8 秒。
     * [action] 用于「撤销」这类一次性操作，点击后提示自动关闭。
     */
    fun notify(
        text: String,
        type: NoticeType = NoticeType.Info,
        action: NoticeAction? = null,
        durationMs: Long? = null,
    ) {
        if (text.isBlank()) return
        _notice.value = Notice(++noticeCounter, text, type, action, durationMs)
    }

    fun consumeNotice(id: Long) {
        if (_notice.value?.id == id) _notice.value = null
    }

    fun navigate(target: DesktopScreen) {
        if (_player.value != null) {
            setPlayer(null)
        }
        setDetail(null)
        if (_screen.value == target) {
            when (target) {
                DesktopScreen.History -> loadHistory()
                DesktopScreen.Live -> refreshLiveGroupsIfNeeded()
                DesktopScreen.PlatformLive -> ensurePlatformLiveLoaded()
                else -> Unit
            }
            return
        }
        _screen.value = target
        when (target) {
            DesktopScreen.History -> loadHistory()
            DesktopScreen.Live -> refreshLiveGroupsIfNeeded()
            DesktopScreen.PlatformLive -> ensurePlatformLiveLoaded()
            DesktopScreen.Search -> Unit
            DesktopScreen.Home -> Unit
            DesktopScreen.Category -> loadCategory(reset = true)
            DesktopScreen.Follow -> { loadFollows(); refreshFollowsIfStale() }
            DesktopScreen.Settings -> loadPlaybackHealth()
        }
    }

    /** 返回上一级；返回 false 表示已经在最底层，交给调用方决定（例如退出全屏）。 */
    fun goBack(): Boolean {
        if (_player.value != null) {
            closePlayer()
            return true
        }
        if (_detail.value != null) {
            setDetail(null)
            return true
        }
        if (_screen.value != DesktopScreen.Home) {
            _screen.value = DesktopScreen.Home
            return true
        }
        return false
    }

    fun updateSettingsState(settings: AppSettings) {
        _settings.value = _settings.value.copy(settings = settings)
    }

    /** 设置页改动代理后立即生效，不用重启。 */
    fun applyProxySettings() {
        com.tvbox.app.data.NetworkProxy.configureFromSettings(settings.value.settings)
    }

    fun pushLogs(minLevel: LogLevel = logs.value.minLevel, sinceMinutes: Int = logs.value.sinceMinutes) {
        updateLogs {
            it.copy(
                minLevel = minLevel,
                sinceMinutes = sinceMinutes,
                entries = AppLog.entriesSince(cutoffFor(sinceMinutes), minLevel),
            )
        }
    }

    private fun observeLogs() {
        scope.launch {
            AppLog.snapshot.collect { all ->
                val minLevel = logs.value.minLevel
                val cutoff = cutoffFor(logs.value.sinceMinutes)
                updateLogs { state ->
                    state.copy(
                        entries = all.filter { entry ->
                            entry.level.weight >= minLevel.weight && entry.timeMs >= cutoff
                        },
                    )
                }
            }
        }
    }

    private fun cutoffFor(sinceMinutes: Int): Long =
        if (sinceMinutes > 0) System.currentTimeMillis() - sinceMinutes * 60_000L else 0L

    fun onSearchQueryChanged(value: String) {
        updateSearch { it.copy(query = value) }
    }

    internal fun loadSearchHistory() {
        _searchHistory.value = runCatching { searchHistoryRepository.load() }.getOrDefault(emptyList())
    }

    fun recordSearch(keyword: String) {
        _searchHistory.value = runCatching { searchHistoryRepository.record(keyword) }
            .getOrDefault(_searchHistory.value)
    }

    fun clearSearchHistory() {
        runCatching { searchHistoryRepository.clear() }
        _searchHistory.value = emptyList()
        notify("已清空搜索历史")
    }
}

internal fun SpeedResult.summary(): String = formatLatency()

internal fun Throwable.userMessage(): String =
    localizedMessage?.takeIf { it.isNotBlank() } ?: "网络请求失败，请稍后重试"
