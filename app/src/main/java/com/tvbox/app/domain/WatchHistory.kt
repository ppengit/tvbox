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

/** Prefer the saved address, then the source name and episode title/number, before old indices. */
fun resolveHistoryEpisodePosition(movie: Movie, item: WatchHistoryItem): Pair<Int, Int> {
    val sources = movie.playSources
    val sourceByUrl = sources.indexOfFirst { source ->
        item.episodeUrl.isNotBlank() && source.episodes.any { it.url == item.episodeUrl }
    }
    val sourceByName = sources.indexOfFirst { it.name == item.sourceName && it.episodes.isNotEmpty() }
    val sourceIndex = when {
        sourceByUrl >= 0 -> sourceByUrl
        sourceByName >= 0 -> sourceByName
        item.sourceIndex in sources.indices && sources[item.sourceIndex].episodes.isNotEmpty() -> item.sourceIndex
        else -> movie.preferredSourceIndex()
    }
    val source = sources.getOrNull(sourceIndex) ?: return 0 to 0
    val episodeByUrl = source.episodes.indexOfFirst { item.episodeUrl.isNotBlank() && it.url == item.episodeUrl }
    val episodeIndex = if (episodeByUrl >= 0) episodeByUrl else {
        correspondingEpisodeIndex(source, item.episodeTitle, item.episodeIndex) ?: 0
    }
    return sourceIndex.coerceAtLeast(0) to episodeIndex.coerceAtLeast(0)
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
