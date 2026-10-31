package com.tvbox.desktop.data

import com.tvbox.app.data.AppSettingsRepository
import com.tvbox.app.data.DefaultDoubanHotRepository
import com.tvbox.app.data.DefaultLiveRepository
import com.tvbox.app.data.DefaultMovieRepository
import com.tvbox.app.data.DefaultPlatformLiveRepository
import com.tvbox.app.data.DoubanHotRepository
import com.tvbox.app.data.HistoryRepository
import com.tvbox.app.data.LineSpeedTester
import com.tvbox.app.data.StreamPrefetcher
import com.tvbox.app.data.LiveRepository
import com.tvbox.app.data.MovieRepository
import com.tvbox.app.data.SkipMarksRepository
import com.tvbox.app.data.SpeedCacheRepository
import com.tvbox.app.data.PersistentAppSettingsRepository
import com.tvbox.app.data.PersistentDoubanHotCache
import com.tvbox.app.data.PersistentHistoryRepository
import com.tvbox.app.data.PersistentPlatformLiveFavoritesRepository
import com.tvbox.app.data.PersistentPlaybackHealthRepository
import com.tvbox.app.data.PlatformLiveFavoritesRepository
import com.tvbox.app.data.PlatformLiveRepository
import com.tvbox.app.data.NetworkProxy
import com.tvbox.app.data.PlaybackHealthRepository
import com.tvbox.app.domain.ProxyMode

/**
 * 桌面端依赖装配。桌面端与 Android 端共享同一套仓库与领域策略，只在存储实现和
 * 播放器上分叉。
 */
class AppContainer {

    private val settingsStore = FileKeyValueStore(DesktopPaths.storeFile("app_settings"))
    private val historyStore = FileKeyValueStore(DesktopPaths.storeFile("watch_history"))
    private val playbackHealthStore = FileKeyValueStore(DesktopPaths.storeFile("playback_health"))
    private val favoritesStore = FileKeyValueStore(DesktopPaths.storeFile("platform_live_favorites"))
    private val doubanCacheStore = FileKeyValueStore(DesktopPaths.storeFile("douban_hot_cache"))
    private val desktopStore = FileKeyValueStore(DesktopPaths.storeFile("desktop_settings"))
    private val followStore = FileKeyValueStore(DesktopPaths.storeFile("followed_shows"))
    private val policyStore = FileKeyValueStore(DesktopPaths.storeFile("movie_playback_policies"))
    private val skipStore = FileKeyValueStore(DesktopPaths.storeFile("skip_marks"))
    private val searchHistoryStore = FileKeyValueStore(DesktopPaths.storeFile("search_history"))
    private val speedCacheStore = FileKeyValueStore(DesktopPaths.storeFile("speed_cache"))

    init {
        // 代理必须在任何网络客户端发出第一个请求之前配置好。
        NetworkProxy.enableSystemProxyLookup()
        runCatching {
            NetworkProxy.configure(
                mode = ProxyMode.fromStorageKey(settingsStore.getString("proxy_mode")),
                host = settingsStore.getString("proxy_host").orEmpty(),
                port = settingsStore.getString("proxy_port")?.toIntOrNull() ?: 7890,
            )
        }
    }

    val appSettingsRepository: AppSettingsRepository = PersistentAppSettingsRepository(settingsStore)

    val historyRepository: HistoryRepository = PersistentHistoryRepository(historyStore)

    val playbackHealthRepository: PlaybackHealthRepository =
        PersistentPlaybackHealthRepository(playbackHealthStore)

    val platformLiveFavoritesRepository: PlatformLiveFavoritesRepository =
        PersistentPlatformLiveFavoritesRepository(favoritesStore)

    val doubanHotRepository: DoubanHotRepository = DefaultDoubanHotRepository(
        cache = PersistentDoubanHotCache(doubanCacheStore),
    )

    val movieRepository: MovieRepository = DefaultMovieRepository()

    val liveRepository: LiveRepository = DefaultLiveRepository()

    val platformLiveRepository: PlatformLiveRepository = DefaultPlatformLiveRepository()

    val speedTester: LineSpeedTester = LineSpeedTester()

    val speedCacheRepository: SpeedCacheRepository = SpeedCacheRepository(speedCacheStore)

    val streamPrefetcher: StreamPrefetcher = StreamPrefetcher()

    val followedShowRepository: FollowedShowRepository = FollowedShowRepository(followStore)

    val playbackPolicyRepository: PlaybackPolicyRepository = PlaybackPolicyRepository(policyStore)

    val skipMarksRepository: SkipMarksRepository = SkipMarksRepository(skipStore)

    val searchHistoryRepository: SearchHistoryRepository = SearchHistoryRepository(searchHistoryStore)

    /**
     * 平台直播依赖一个外部解析服务。电视端把地址编进 APK，桌面端优先读本地设置，
     * 其次读环境变量；两者都没有时使用 APK 同款公共服务，避免开箱就提示“未配置”。
     */
    var platformLiveServiceUrl: String
        get() = desktopStore.getString(PLATFORM_LIVE_KEY)?.takeIf { it.isNotBlank() }
            ?: System.getenv("TVBOX_PLATFORM_LIVE_SERVICE_URL").orEmpty().ifBlank {
                DEFAULT_PLATFORM_LIVE_SERVICE_URL
            }
        set(value) {
            desktopStore.edit { putString(PLATFORM_LIVE_KEY, value.trim()) }
        }

    private companion object {
        const val PLATFORM_LIVE_KEY = "platform_live_service_url"
        const val DEFAULT_PLATFORM_LIVE_SERVICE_URL = "http://20.205.10.127:8868"
    }
}
