package com.tvbox.app.data

import android.content.Context
import com.tvbox.app.domain.WatchHistoryItem
import com.tvbox.app.domain.historyKey
import com.tvbox.app.domain.isBlockedContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

interface HistoryRepository {
    suspend fun getHistory(): List<WatchHistoryItem>
    suspend fun saveProgress(item: WatchHistoryItem): List<WatchHistoryItem>
    suspend fun removeHistory(apiLineId: String, movieId: Int): List<WatchHistoryItem>
    suspend fun clearHistory(): List<WatchHistoryItem>
}

class SharedHistoryRepository(context: Context) : HistoryRepository {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("watch_history", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val json = HISTORY_JSON

    override suspend fun getHistory(): List<WatchHistoryItem> = withContext(Dispatchers.IO) {
        mutex.withLock { readHistory() }
    }

    override suspend fun saveProgress(item: WatchHistoryItem): List<WatchHistoryItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = mergeHistoryProgress(readHistory(), item, System.currentTimeMillis(), MAX_HISTORY_ITEMS)
            check(prefs.edit().putString(KEY_ITEMS, json.encodeToString(updated)).commit()) {
                "Unable to save watch history"
            }
            updated
        }
    }

    override suspend fun removeHistory(apiLineId: String, movieId: Int): List<WatchHistoryItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = removeHistoryRecords(readHistory(), apiLineId, movieId)
            check(prefs.edit().putString(KEY_ITEMS, json.encodeToString(updated)).commit()) {
                "Unable to remove watch history"
            }
            updated
        }
    }

    override suspend fun clearHistory(): List<WatchHistoryItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(prefs.edit().remove(KEY_ITEMS).commit()) { "Unable to clear watch history" }
            emptyList()
        }
    }

    private fun readHistory(): List<WatchHistoryItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return decodeHistoryRecords(raw)
            .filterNot {
                isBlockedContent(
                    it.movieName,
                    it.typeName,
                    it.remarks,
                    it.episodeTitle,
                )
            }
            .sortedByDescending { it.updatedAtEpochMs }
    }

    private companion object {
        const val KEY_ITEMS = "items"
        const val MAX_HISTORY_ITEMS = 100
    }
}

internal val HISTORY_JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun decodeHistoryRecords(raw: String): List<WatchHistoryItem> =
    try {
        HISTORY_JSON.decodeFromString<List<WatchHistoryItem>>(raw)
    } catch (error: Exception) {
        throw IOException("观看历史数据读取失败，原记录已保留", error)
    }

internal fun mergeHistoryProgress(
    current: List<WatchHistoryItem>,
    item: WatchHistoryItem,
    updatedAtEpochMs: Long,
    limit: Int,
): List<WatchHistoryItem> {
    val previous = current.firstOrNull { it.apiLineId == item.apiLineId && it.movieId == item.movieId }
    val sameEpisode = previous != null && item.episodeUrl.isNotBlank() && previous.episodeUrl == item.episodeUrl
    // Preparing the same episode temporarily reports an unknown duration; keep the known total.
    val durationMs = if (sameEpisode && item.durationMs <= 0L) previous.durationMs else item.durationMs
    val updated = item.copy(durationMs = durationMs.coerceAtLeast(0L), updatedAtEpochMs = updatedAtEpochMs)
    return buildList {
        add(updated)
        current.filterNot { it.apiLineId == item.apiLineId && it.movieId == item.movieId }.forEach(::add)
    }.take(limit.coerceAtLeast(0))
}

internal fun removeHistoryRecords(
    current: List<WatchHistoryItem>,
    apiLineId: String,
    movieId: Int,
): List<WatchHistoryItem> = current.filterNot { it.apiLineId == apiLineId && it.movieId == movieId }

/** Invalidates only snapshots captured before an explicit remove/clear action. */
internal class HistoryWriteGuard {
    private var clearVersion = 0L
    private val removedVersions = mutableMapOf<String, Long>()

    fun version(item: WatchHistoryItem): Pair<Long, Long> =
        clearVersion to (removedVersions[item.historyKey] ?: 0L)

    fun isCurrent(item: WatchHistoryItem, snapshotVersion: Pair<Long, Long>): Boolean = snapshotVersion == version(item)

    fun remove(item: WatchHistoryItem) {
        removedVersions[item.historyKey] = (removedVersions[item.historyKey] ?: 0L) + 1L
    }

    fun clear() {
        clearVersion++
        removedVersions.clear()
    }
}
