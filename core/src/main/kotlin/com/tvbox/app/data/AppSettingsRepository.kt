package com.tvbox.app.data

import com.tvbox.app.domain.AppSettings
import com.tvbox.app.domain.DeviceMode
import com.tvbox.app.domain.CustomVideoApiLine
import com.tvbox.app.domain.TvFontScale
import com.tvbox.app.domain.TvTheme
import com.tvbox.app.domain.VideoScaleMode
import com.tvbox.app.domain.PlayerRemotePolicy
import com.tvbox.app.domain.ProxyMode
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.withContext

interface AppSettingsRepository {
    suspend fun getSettings(): AppSettings
    suspend fun saveSettings(settings: AppSettings): AppSettings
}

class PersistentAppSettingsRepository(private val store: KeyValueStore) : AppSettingsRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    override suspend fun getSettings(): AppSettings = withContext(Dispatchers.IO) {
        AppSettings(
            homeApiLineId = store.getString(KEY_HOME_API_LINE_ID, AppSettings().homeApiLineId)
                ?: AppSettings().homeApiLineId,
            customVideoApiLines = readCustomVideoApiLines(),
            theme = TvTheme.fromStorageKey(store.getString(KEY_THEME, TvTheme.Default.storageKey)),
            fontScale = TvFontScale.fromStorageKey(store.getString(KEY_FONT_SCALE, TvFontScale.Normal.storageKey)),
            aiProviderId = store.getString(KEY_AI_PROVIDER_ID, AppSettings().aiProviderId)
                ?: AppSettings().aiProviderId,
            aiModelName = store.getString(KEY_AI_MODEL_NAME, AppSettings().aiModelName)
                ?: AppSettings().aiModelName,
            aiApiKey = store.getString(KEY_AI_API_KEY, AppSettings().aiApiKey)
                ?: AppSettings().aiApiKey,
            checkUpdatesOnStartup = store.getBoolean(KEY_CHECK_UPDATES_ON_STARTUP, true),
            playbackAgentAutoSwitchEnabled = store.getBoolean(KEY_PLAYBACK_AGENT_AUTO_SWITCH, true),
            videoScaleMode = VideoScaleMode.fromStorageKey(store.getString(KEY_VIDEO_SCALE_MODE)),
            deviceMode = DeviceMode.fromStorageKey(store.getString(KEY_DEVICE_MODE)),
            preferredPlaybackSpeed = runCatching { store.getFloat(KEY_PLAYBACK_SPEED, 1f) }
                .getOrDefault(1f).takeIf { it in PlayerRemotePolicy.speedOptions } ?: 1f,
            proxyMode = ProxyMode.fromStorageKey(store.getString(KEY_PROXY_MODE)).storageKey,
            proxyHost = store.getString(KEY_PROXY_HOST, "") ?: "",
            proxyPort = store.getString(KEY_PROXY_PORT, "7890")?.toIntOrNull() ?: 7890,
            homeFeed = store.getString(KEY_HOME_FEED, AppSettings().homeFeed) ?: AppSettings().homeFeed,
            preferFastestLine = store.getBoolean(KEY_PREFER_FASTEST_LINE, false),
            speedTestOnOpen = store.getBoolean(KEY_SPEED_TEST_ON_OPEN, false),
            speedTestIntervalMinutes = store.getString(KEY_SPEED_TEST_INTERVAL, "0")?.toIntOrNull() ?: 0,
            autoSwitchErrorThreshold = store.getString(KEY_AUTO_SWITCH_THRESHOLD, "2")?.toIntOrNull() ?: 2,
            bufferingTimeoutSeconds = store.getString(KEY_BUFFERING_TIMEOUT, "12")?.toIntOrNull() ?: 12,
            prefetchEpisodeCount = store.getString(KEY_PREFETCH_EPISODES, "1")?.toIntOrNull() ?: 1,
            imageCacheMaxMb = store.getString(KEY_IMAGE_CACHE_MB, "256")?.toIntOrNull() ?: 256,
            playerBufferMb = store.getString(KEY_PLAYER_BUFFER_MB, "32")?.toIntOrNull() ?: 32,
            followRefreshMinutes = store.getString(KEY_FOLLOW_REFRESH, "30")?.toIntOrNull() ?: 30,
            autoSkipIntro = store.getBoolean(KEY_AUTO_SKIP_INTRO, true),
            autoSkipOutro = store.getBoolean(KEY_AUTO_SKIP_OUTRO, true),
        )
    }

    override suspend fun saveSettings(settings: AppSettings): AppSettings = withContext(Dispatchers.IO) {
        store.edit {
            putString(KEY_HOME_API_LINE_ID, settings.homeApiLineId)
            putString(KEY_CUSTOM_VIDEO_API_LINES, json.encodeToString(settings.customVideoApiLines))
            putString(KEY_THEME, settings.theme.storageKey)
            putString(KEY_FONT_SCALE, settings.fontScale.storageKey)
            putString(KEY_AI_PROVIDER_ID, settings.aiProviderId)
            putString(KEY_AI_MODEL_NAME, settings.aiModelName)
            putString(KEY_AI_API_KEY, settings.aiApiKey)
            putBoolean(KEY_CHECK_UPDATES_ON_STARTUP, settings.checkUpdatesOnStartup)
            putBoolean(KEY_PLAYBACK_AGENT_AUTO_SWITCH, settings.playbackAgentAutoSwitchEnabled)
            putString(KEY_VIDEO_SCALE_MODE, settings.videoScaleMode.storageKey)
            putString(KEY_DEVICE_MODE, settings.deviceMode.storageKey)
            putFloat(
                KEY_PLAYBACK_SPEED,
                settings.preferredPlaybackSpeed.takeIf { it in PlayerRemotePolicy.speedOptions } ?: 1f,
            )
            putString(KEY_PROXY_MODE, settings.proxyMode)
            putString(KEY_PROXY_HOST, settings.proxyHost)
            putString(KEY_PROXY_PORT, settings.proxyPort.toString())
            putString(KEY_HOME_FEED, settings.homeFeed)
            putBoolean(KEY_PREFER_FASTEST_LINE, settings.preferFastestLine)
            putBoolean(KEY_SPEED_TEST_ON_OPEN, settings.speedTestOnOpen)
            putString(KEY_SPEED_TEST_INTERVAL, settings.speedTestIntervalMinutes.toString())
            putString(KEY_AUTO_SWITCH_THRESHOLD, settings.autoSwitchErrorThreshold.toString())
            putString(KEY_BUFFERING_TIMEOUT, settings.bufferingTimeoutSeconds.toString())
            putString(KEY_PREFETCH_EPISODES, settings.prefetchEpisodeCount.toString())
            putString(KEY_IMAGE_CACHE_MB, settings.imageCacheMaxMb.toString())
            putString(KEY_PLAYER_BUFFER_MB, settings.playerBufferMb.toString())
            putString(KEY_FOLLOW_REFRESH, settings.followRefreshMinutes.toString())
            putBoolean(KEY_AUTO_SKIP_INTRO, settings.autoSkipIntro)
            putBoolean(KEY_AUTO_SKIP_OUTRO, settings.autoSkipOutro)
        }
        settings
    }

    private fun readCustomVideoApiLines(): List<CustomVideoApiLine> {
        val raw = store.getString(KEY_CUSTOM_VIDEO_API_LINES).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<CustomVideoApiLine>>(raw) }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val KEY_HOME_API_LINE_ID = "home_api_line_id"
        const val KEY_CUSTOM_VIDEO_API_LINES = "custom_video_api_lines"
        const val KEY_THEME = "theme"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_AI_PROVIDER_ID = "ai_provider_id"
        const val KEY_AI_MODEL_NAME = "ai_model_name"
        const val KEY_AI_API_KEY = "ai_api_key"
        const val KEY_CHECK_UPDATES_ON_STARTUP = "check_updates_on_startup"
        const val KEY_PLAYBACK_AGENT_AUTO_SWITCH = "playback_agent_auto_switch"
        const val KEY_VIDEO_SCALE_MODE = "video_scale_mode"
        const val KEY_DEVICE_MODE = "device_mode"
        const val KEY_PLAYBACK_SPEED = "preferred_playback_speed"
        const val KEY_PROXY_MODE = "proxy_mode"
        const val KEY_PROXY_HOST = "proxy_host"
        const val KEY_PROXY_PORT = "proxy_port"
        const val KEY_HOME_FEED = "home_feed"
        const val KEY_PREFER_FASTEST_LINE = "prefer_fastest_line"
        const val KEY_SPEED_TEST_ON_OPEN = "speed_test_on_open"
        const val KEY_SPEED_TEST_INTERVAL = "speed_test_interval"
        const val KEY_AUTO_SWITCH_THRESHOLD = "auto_switch_threshold"
        const val KEY_BUFFERING_TIMEOUT = "buffering_timeout"
        const val KEY_PREFETCH_EPISODES = "prefetch_episodes"
        const val KEY_IMAGE_CACHE_MB = "image_cache_max_mb"
        const val KEY_PLAYER_BUFFER_MB = "player_buffer_mb"
        const val KEY_FOLLOW_REFRESH = "follow_refresh_minutes"
        const val KEY_AUTO_SKIP_INTRO = "auto_skip_intro"
        const val KEY_AUTO_SKIP_OUTRO = "auto_skip_outro"
    }
}
