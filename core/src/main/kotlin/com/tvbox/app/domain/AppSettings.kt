package com.tvbox.app.domain

import kotlinx.serialization.Serializable

data class AppSettings(
    val homeApiLineId: String = "liangzi",
    val customVideoApiLines: List<CustomVideoApiLine> = emptyList(),
    val theme: TvTheme = TvTheme.Default,
    val fontScale: TvFontScale = TvFontScale.Normal,
    val aiProviderId: String = AiProviders.default.id,
    val aiModelName: String = AiProviders.default.defaultModel,
    val aiApiKey: String = "",
    val checkUpdatesOnStartup: Boolean = true,
    val playbackAgentAutoSwitchEnabled: Boolean = true,
    val videoScaleMode: VideoScaleMode = VideoScaleMode.Fit,
    val deviceMode: DeviceMode = DeviceMode.Television,
    val preferredPlaybackSpeed: Float = 1f,
    val proxyMode: String = ProxyMode.System.storageKey,
    val proxyHost: String = "",
    val proxyPort: Int = 7890,
    val homeFeed: String = HomeFeedMode.Douban.storageKey,
    val preferFastestLine: Boolean = false,
    val speedTestOnOpen: Boolean = false,
    val speedTestIntervalMinutes: Int = 0,
    val autoSwitchErrorThreshold: Int = 2,
    val bufferingTimeoutSeconds: Int = 12,
    val prefetchEpisodeCount: Int = 1,
    val imageCacheMaxMb: Int = 256,
    val playerBufferMb: Int = 32,
    /** 追剧自动巡检间隔，0 表示只在打开追剧页时检查。 */
    val followRefreshMinutes: Int = 30,
    val autoSkipIntro: Boolean = true,
    val autoSkipOutro: Boolean = true,
)

/** 出网策略。桌面端设置页可切换，Android 保持默认的跟随系统。 */
enum class ProxyMode(val storageKey: String, val displayName: String, val description: String) {
    Direct("direct", "直连", "不经过任何 HTTP 代理。开启 Clash TUN 时流量仍会被 TUN 接管，但不会再套一层系统代理。"),
    System("system", "跟随系统", "使用 Windows 系统代理设置，Clash 的「系统代理」模式会写在这里。"),
    Custom("custom", "自定义代理", "手动填写 HTTP 代理地址，例如 127.0.0.1:7890。"),
    ;

    companion object {
        fun fromStorageKey(value: String?): ProxyMode =
            entries.firstOrNull { it.storageKey == value } ?: System
    }
}

/** 首页内容来源。 */
enum class HomeFeedMode(val storageKey: String, val displayName: String) {
    Douban("douban", "豆瓣热播"),
    Latest("latest", "本站最新"),
    ;

    companion object {
        fun fromStorageKey(value: String?): HomeFeedMode =
            entries.firstOrNull { it.storageKey == value } ?: Douban
    }
}

enum class DeviceMode(val storageKey: String, val displayName: String, val description: String) {
    Television("tv", "电视模式", "电视和盒子保持横屏，竖屏短剧在横屏窗口中显示。"),
    Mobile("mobile", "手机模式", "点播按影片比例切换横竖屏，电视直播使用手机布局。"),
    ;

    companion object {
        fun fromStorageKey(value: String?): DeviceMode =
            entries.firstOrNull { it.storageKey == value } ?: Television
    }
}

enum class VideoScaleMode(val storageKey: String, val displayName: String, val description: String) {
    Fit("fit", "自适应", "保持原始比例并完整显示；影片与屏幕比例不同时可能留黑边。"),
    Zoom("zoom", "裁剪铺满", "保持原始比例铺满屏幕；画面边缘可能被裁剪。"),
    Fill("fill", "拉伸铺满", "铺满屏幕并完整显示；人物和画面比例可能变形。"),
    ;

    companion object {
        fun fromStorageKey(value: String?): VideoScaleMode =
            entries.firstOrNull { it.storageKey == value } ?: Fit
    }
}

enum class TvTheme(
    val storageKey: String,
    val displayName: String,
) {
    Default("default", "默认主题"),
    Cinema("cinema", "影院主题"),
    ;

    companion object {
        fun fromStorageKey(value: String?): TvTheme =
            entries.firstOrNull { it.storageKey == value } ?: Default
    }
}

enum class TvFontScale(
    val storageKey: String,
    val displayName: String,
    val typographyScale: Float,
) {
    Normal("normal", "正常", 1f),
    Large("large", "大", 1.18f),
    ExtraLarge("extra_large", "超大", 1.36f),
    ;

    companion object {
        fun fromStorageKey(value: String?): TvFontScale =
            entries.firstOrNull { it.storageKey == value } ?: Normal
    }
}

@Serializable
data class CustomVideoApiLine(
    val id: String,
    val name: String,
    val baseUrl: String,
) {
    fun toApiLine(): ApiLine {
        return ApiLine(
            id = id,
            name = name,
            baseUrls = listOf(baseUrl),
        )
    }
}

fun List<CustomVideoApiLine>.toApiLines(): List<ApiLine> {
    return map { it.toApiLine() }
}

data class AiProvider(
    val id: String,
    val name: String,
    val apiBaseUrl: String,
    val defaultModel: String,
) {
    val chatCompletionsUrl: String
        get() {
            val normalized = apiBaseUrl.trim().trimEnd('/')
            return if (normalized.endsWith("/chat/completions")) {
                normalized
            } else {
                "$normalized/chat/completions"
            }
        }
}

object AiProviders {
    val all = listOf(
        AiProvider(
            id = "agnes",
            name = "Agnes",
            apiBaseUrl = "https://apihub.agnes-ai.com/v1/chat/completions",
            defaultModel = "agnes-2.5-flash",
        ),
        AiProvider(
            id = "deepseek",
            name = "DeepSeek",
            apiBaseUrl = "https://api.deepseek.com",
            defaultModel = "deepseek-v4-flash",
        ),
        AiProvider(
            id = "siliconflow",
            name = "SiliconFlow",
            apiBaseUrl = "https://api.siliconflow.cn/v1",
            defaultModel = "Qwen/Qwen2.5-7B-Instruct",
        ),
        AiProvider(
            id = "qwen",
            name = "Qwen",
            apiBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            defaultModel = "qwen-plus",
        ),
    )

    val default: AiProvider = all.first()

    fun find(id: String): AiProvider = all.firstOrNull { it.id == id } ?: default
}
