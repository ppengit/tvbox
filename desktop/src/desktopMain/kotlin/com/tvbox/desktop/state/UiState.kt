package com.tvbox.desktop.state

import com.tvbox.app.data.LogEntry
import com.tvbox.app.data.LogLevel
import com.tvbox.app.data.SpeedResult
import com.tvbox.app.domain.AppSettings
import com.tvbox.app.domain.Category
import com.tvbox.app.domain.DoubanHotItem
import com.tvbox.app.domain.LiveChannel
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.PlatformLiveCategory
import com.tvbox.app.domain.PlatformLiveParentCategory
import com.tvbox.app.domain.PlatformLiveRoom
import com.tvbox.app.domain.PlatformLiveSite
import com.tvbox.app.domain.PlaybackHealthSnapshot
import com.tvbox.app.domain.ResolvedPlatformLiveStream
import com.tvbox.app.domain.WatchHistoryItem
import com.tvbox.desktop.data.FollowedShow

/** 侧边栏一级页面。详情与播放是叠加在这些页面之上的层级，不占导航项。 */
enum class DesktopScreen(val title: String) {
    Home("首页"),
    Category("片库"),
    Search("搜索"),
    Follow("追剧"),
    History("历史"),
    Live("电视直播"),
    PlatformLive("平台直播"),
    Settings("设置"),
}

data class HomeState(
    val mode: com.tvbox.app.domain.HomeFeedMode = com.tvbox.app.domain.HomeFeedMode.Douban,
    val items: List<DoubanHotItem> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val page: Int = 1,
    val pageCount: Int = 1,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val resolvingTitle: String? = null,
    val ready: Boolean = false,
)

/**
 * 片库浏览状态。分类树来自当前首页线路，筛选条件尽量下发给站点，
 * 返回结果再做一次本地校验，避免不支持筛选的站点把无关结果塞进来。
 */
data class CategoryState(
    val apiLineId: String = "",
    val categories: List<Category> = emptyList(),
    val selectedParentId: Int = 0,
    val selectedTypeId: Int? = null,
    val year: String = "",
    val area: String = "",
    val lang: String = "",
    val movies: List<Movie> = emptyList(),
    val page: Int = 1,
    val pageCount: Int = 1,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val ready: Boolean = false,
    /** 本轮聚合实际返回内容的数据源数量。 */
    val sourceCount: Int = 0,
) {
    /** 顶级分类。部分站点的分类树会重复返回同名项，这里按名称去重。 */
    val parentCategories: List<Category>
        get() = categories.filter { it.parentId == 0 }.distinctBy { it.name.trim() }

    /**
     * 子分类。选中「全部」时不再展开子分类，否则会把顶级分类原样重复一遍。
     */
    val childCategories: List<Category>
        get() = if (selectedParentId == 0) {
            emptyList()
        } else {
            categories.filter { it.parentId == selectedParentId }.distinctBy { it.name.trim() }
        }

    val activeFilters: Int
        get() = listOf(year, area, lang).count { it.isNotBlank() }
}

data class FollowState(
    val items: List<FollowedShow> = emptyList(),
    val checking: Boolean = false,
    val lastCheckedAtMs: Long = 0L,
    val error: String? = null,
)

data class SpeedTestState(
    val running: Boolean = false,
    val lastRunAtMs: Long = 0L,
    val lineResults: Map<String, SpeedResult> = emptyMap(),
) {
    /** 延迟低的排前面；延迟相同再看带宽。 */
    fun fastestLineId(): String? = lineResults.values
        .filter { it.ok }
        .minWithOrNull(compareBy({ it.latencyMs.coerceAtLeast(1L) }, { -it.kbps }))
        ?.targetId
}

data class SourceSpeedState(
    val running: Boolean = false,
    val results: Map<Int, SpeedResult> = emptyMap(),
) {
    fun fastestSourceIndex(): Int? = results.entries
        .filter { it.value.ok }
        .minWithOrNull(compareBy({ it.value.latencyMs.coerceAtLeast(1L) }, { -it.value.kbps }))
        ?.key
}

data class LogState(
    val minLevel: LogLevel = LogLevel.Info,
    /** 只保留最近多少分钟内的日志；0 表示不限时间。 */
    val sinceMinutes: Int = 0,
    val entries: List<LogEntry> = emptyList(),
    val exportPath: String? = null,
)

data class SearchState(
    val query: String = "",
    val submittedQuery: String = "",
    val results: List<Movie> = emptyList(),
    val completedSources: Int = 0,
    val totalSources: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val searched: Boolean = false,
)

/**
 * 详情页状态。播放源和剧集的选择在桌面端是显式的，不做自动跳转，
 * 用户选源后剧集网格立即跟随。
 */
data class DetailState(
    val movie: Movie? = null,
    val movieId: Int = 0,
    val sourceIndex: Int = 0,
    val episodeIndex: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val completedSources: Int = 0,
    val totalSources: Int = 0,
    val resumeHint: WatchHistoryItem? = null,
    val followed: Boolean = false,
    val sourceSpeeds: Map<Int, SpeedResult> = emptyMap(),
    /** 当前这组播放源测速结果的时间；0 表示还没有任何结果（含缓存）。 */
    val sourceSpeedsMeasuredAtMs: Long = 0L,
    val speedTesting: Boolean = false,
    val policy: com.tvbox.desktop.data.MoviePlaybackPolicy? = null,
)

data class HistoryState(
    val items: List<WatchHistoryItem> = emptyList(),
    val loading: Boolean = false,
    val resumingKey: String? = null,
    val error: String? = null,
)

data class LiveState(
    val channels: List<LiveChannel> = emptyList(),
    val groups: List<String> = emptyList(),
    val selectedGroup: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

data class PlatformLiveState(
    val sites: List<PlatformLiveSite> = emptyList(),
    val selectedSite: String? = null,
    val parents: List<PlatformLiveParentCategory> = emptyList(),
    val selectedParentId: String? = null,
    val categories: List<PlatformLiveCategory> = emptyList(),
    val selectedCategoryId: String? = null,
    val rooms: List<PlatformLiveRoom> = emptyList(),
    val page: Int = 1,
    val pageCount: Int = 1,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val favorites: List<PlatformLiveRoom> = emptyList(),
    val showFavorites: Boolean = false,
)

/**
 * 播放页状态。`movie` 为 null 表示直播播放。
 */
data class PlayerState(
    val movie: Movie? = null,
    val sourceIndex: Int = 0,
    val episodeIndex: Int = 0,
    val startPositionMs: Long = 0L,
    val currentUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    val liveTitle: String = "",
    val liveChannels: List<LiveChannel> = emptyList(),
    val liveChannelIndex: Int = -1,
    val platformStream: ResolvedPlatformLiveStream? = null,
    val switchNotice: String? = null,
    /** 统一集数列表下当前集的标题；直播时为空。 */
    val episodeTitle: String = "",
    /** 统一集数列表的总集数。 */
    val episodeCount: Int = 0,
    /** 本集本轮播放已经失败过的线路，自动切源不得再次选择。 */
    val failedSourceIndices: Set<Int> = emptySet(),
    /** 最近一次播放器上报的位置，关闭页面时用它落盘，不能退回起播位置。 */
    val lastPositionMs: Long = startPositionMs,
    val lastDurationMs: Long = 0L,
) {
    val isLive: Boolean get() = movie == null

    val sourceName: String
        get() = movie?.playSources?.getOrNull(sourceIndex)?.name.orEmpty()

    val title: String
        get() = movie?.name ?: liveTitle.ifBlank { platformStream?.title ?: "电视直播" }
}

/** 轻提示语义类型：决定浮层配色、图标与默认停留时长。 */
enum class NoticeType { Success, Warning, Error, Info }

/** 轻提示上的可选操作，例如「撤销」。执行后浮层立即关闭。 */
data class NoticeAction(val label: String, val onClick: () -> Unit)

/**
 * 全局轻提示。桌面端用右下角浮层，不用系统 Toast。
 *
 * [durationMs] 为空时按类型取默认时长：错误停留 8 秒，其余 3 秒；
 * 带操作（如撤销）的提示可以显式传更长的时间，避免来不及点击。
 */
data class Notice(
    val id: Long,
    val text: String,
    val type: NoticeType = NoticeType.Info,
    val action: NoticeAction? = null,
    val durationMs: Long? = null,
) {
    val visibleDurationMs: Long
        get() = durationMs ?: defaultNoticeDurationMs(type)
}

/** 轻提示默认停留时长：错误需要更长时间读完，普通提示 3 秒。 */
fun defaultNoticeDurationMs(type: NoticeType): Long = when (type) {
    NoticeType.Error -> 8_000L
    NoticeType.Success, NoticeType.Warning, NoticeType.Info -> 3_000L
}

data class AppSettingsState(
    val settings: AppSettings = AppSettings(),
    val categories: List<Category> = emptyList(),
    val ready: Boolean = false,
    val saving: Boolean = false,
    val playbackHealth: PlaybackHealthSnapshot = PlaybackHealthSnapshot(),
)
