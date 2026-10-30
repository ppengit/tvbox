package com.tvbox.app.data

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

    /** 恢复一条被删除的历史快照（撤销用）；保留快照里的观看时间与进度。 */
    suspend fun restoreHistory(item: WatchHistoryItem): List<WatchHistoryItem>
}

class PersistentHistoryRepository(private val store: KeyValueStore) : HistoryRepository {
    private val mutex = Mutex()
    private val json = HISTORY_JSON

    override suspend fun getHistory(): List<WatchHistoryItem> = withContext(Dispatchers.IO) {
        mutex.withLock { readHistory() }
    }

    override suspend fun saveProgress(item: WatchHistoryItem): List<WatchHistoryItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = mergeHistoryProgress(readHistory(), item, System.currentTimeMillis(), MAX_HISTORY_ITEMS)
            check(store.edit { putString(KEY_ITEMS, json.encodeToString(updated)) }) {
                "Unable to save watch history"
            }
            updated
        }
    }

    override suspend fun removeHistory(apiLineId: String, movieId: Int): List<WatchHistoryItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = removeHistoryRecords(readHistory(), apiLineId, movieId)
            check(store.edit { putString(KEY_ITEMS, json.encodeToString(updated)) }) {
                "Unable to remove watch history"
            }
            updated
        }
    }

    override suspend fun clearHistory(): List<WatchHistoryItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(store.edit { remove(KEY_ITEMS) }) { "Unable to clear watch history" }
            emptyList()
        }
    }

    override suspend fun restoreHistory(item: WatchHistoryItem): List<WatchHistoryItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = restoreHistoryRecord(readHistory(), item, MAX_HISTORY_ITEMS)
            check(store.edit { putString(KEY_ITEMS, json.encodeToString(updated)) }) {
                "Unable to restore watch history"
            }
            updated
        }
    }

    private fun readHistory(): List<WatchHistoryItem> {
        val raw = store.getString(KEY_ITEMS) ?: return emptyList()
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
            .take(MAX_HISTORY_ITEMS)
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

/** Puts a deleted snapshot back keeping its original timestamp/progress, replacing a stale duplicate. */
internal fun restoreHistoryRecord(
    current: List<WatchHistoryItem>,
    item: WatchHistoryItem,
    limit: Int,
): List<WatchHistoryItem> {
    if (limit <= 0) return emptyList()
    // 恢复的条目必须保留：只按时间排序再截断时，旧快照会被最新记录挤出列表，
    // 用户点「撤销」看起来什么都没发生。这里改为挤掉其他记录里最旧的一条。
    val rest = current
        .filterNot { it.apiLineId == item.apiLineId && it.movieId == item.movieId }
        .sortedByDescending { it.updatedAtEpochMs }
        .take((limit - 1).coerceAtLeast(0))
    return (listOf(item) + rest).sortedByDescending { it.updatedAtEpochMs }
}

/** Invalidates only snapshots captured before an explicit remove/clear action. */
class HistoryWriteGuard {
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
