package com.tvbox.app.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class WatchHistoryItem(
    val movieId: Int,
    val apiLineId: String = "ruyi",
    val apiLineName: String = "如意",
    val movieName: String,
    val posterUrl: String,
    val typeName: String,
    val remarks: String,
    val sourceIndex: Int,
    val sourceName: String,
    val episodeIndex: Int,
    val episodeTitle: String,
    val episodeUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochMs: Long,
    /** 保存进度时该剧的统一集数总数；0 表示旧记录或电影这类单集内容。 */
    val episodeCount: Int = 0,
) {
    val progressPercent: Int
        get() {
            if (durationMs <= 0L || positionMs <= 0L) return 0
            return (positionMs.toDouble() / durationMs * 100).coerceIn(0.0, 100.0).toInt()
        }
}

// Presentation values stay outside the serialized record to preserve the existing JSON schema.
val WatchHistoryItem.historyKey: String
    get() = "$apiLineId:$movieId"

/** Completion refers to the saved episode, not to every episode of a series. */
val WatchHistoryItem.isCompleted: Boolean
    get() = durationMs > 0L && positionMs > 0L && positionMs.toDouble() / durationMs >= 0.95

enum class HistoryFilter(val displayName: String) {
    All("全部"),
    Unfinished("未看完"),
    Completed("已看完"),
}

fun List<WatchHistoryItem>.filterHistory(filter: HistoryFilter): List<WatchHistoryItem> =
    sortedByDescending { it.updatedAtEpochMs }
        .distinctBy { it.historyKey }
        .filter {
            when (filter) {
                HistoryFilter.All -> true
                HistoryFilter.Unfinished -> !it.isCompleted
                HistoryFilter.Completed -> it.isCompleted
            }
        }

fun List<WatchHistoryItem>.continueWatching(limit: Int = 6): List<WatchHistoryItem> =
    filterHistory(HistoryFilter.Unfinished)
        .filter { it.positionMs > 0L }
        .take(limit.coerceAtLeast(0))

/** Keeps the same card, or the next card at the old index (then the previous card at the end). */
fun List<WatchHistoryItem>.historyFocusKey(preferredKey: String?, fallbackIndex: Int): String? =
    firstOrNull { it.historyKey == preferredKey }?.historyKey
        ?: getOrNull(fallbackIndex.coerceIn(0, lastIndex.coerceAtLeast(0)))?.historyKey

fun WatchHistoryItem.historyProgressText(): String {
    val position = formatHistoryTime(positionMs)
    return if (durationMs > 0L) {
        "$position / ${formatHistoryTime(durationMs)} · $progressPercent%"
    } else {
        "已看 $position · 总时长未知"
    }
}

fun WatchHistoryItem.historyDateText(zoneId: ZoneId = ZoneId.systemDefault()): String =
    if (updatedAtEpochMs <= 0L) "观看日期未知"
    else "最近观看 ${Instant.ofEpochMilli(updatedAtEpochMs).atZone(zoneId).format(HISTORY_DATE_FORMAT)}"

/** Resolve history to a source index and a shared EpisodeMatrix index. */
fun resolveHistoryEpisodePosition(movie: Movie, item: WatchHistoryItem): Pair<Int, Int> {
    val sources = movie.playSources
    val matrix = buildEpisodeMatrix(sources)
    if (sources.isEmpty() || matrix.episodes.isEmpty()) return 0 to 0

    val sourceByUrl = sources.indexOfFirst { source ->
        item.episodeUrl.isNotBlank() && source.episodes.any { it.url == item.episodeUrl }
    }
    val sourceByName = sources.indexOfFirst { it.name == item.sourceName && it.episodes.isNotEmpty() }
    val sourceIndex = when {
        sourceByUrl >= 0 -> sourceByUrl
        sourceByName >= 0 -> sourceByName
        item.sourceIndex in sources.indices && sources[item.sourceIndex].episodes.isNotEmpty() -> item.sourceIndex
        else -> movie.preferredSourceIndex()
    }.coerceIn(0, sources.lastIndex)

    val aligned = matrix.sources.getOrNull(sourceIndex)
    val episodeByUrl = aligned?.byIndex?.entries?.firstOrNull {
        item.episodeUrl.isNotBlank() && it.value.url == item.episodeUrl
    }?.key
    val number = episodeNumber(item.episodeTitle)
    val episodeByNumber = number?.let { target -> matrix.episodes.indexOfFirst { it.number == target } }
        ?.takeIf { it >= 0 }
    val savedMatrixIndex = item.episodeIndex.takeIf { item.episodeCount > 0 && it in matrix.episodes.indices }
    val legacySourceIndex = if (item.episodeCount <= 0) {
        correspondingEpisodeIndex(sources[sourceIndex], item.episodeTitle, item.episodeIndex)
            ?.let { localIndex -> aligned?.byIndex?.entries?.firstOrNull { it.value == sources[sourceIndex].episodes.getOrNull(localIndex) }?.key }
    } else {
        null
    }
    val episodeIndex = episodeByUrl ?: episodeByNumber ?: savedMatrixIndex ?: legacySourceIndex
        ?: matrix.firstAvailableIndex(sourceIndex)
        ?: 0
    return matrix.resolvePlayable(sourceIndex, episodeIndex) ?: (sourceIndex to episodeIndex)
}

private val HISTORY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

private fun formatHistoryTime(positionMs: Long): String {
    val seconds = positionMs.coerceAtLeast(0L) / 1_000L
    return if (seconds >= 3_600L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
    } else {
        String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }
}
