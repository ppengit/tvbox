package com.tvbox.app.data

import com.tvbox.app.domain.PlaybackHealthEntry
import com.tvbox.app.domain.PlaybackHealthSnapshot
import com.tvbox.app.domain.PlaybackIssueType
import com.tvbox.app.domain.prunePlaybackHealthEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

interface PlaybackHealthRepository {
    suspend fun getSnapshot(): PlaybackHealthSnapshot
    suspend fun recordIssue(key: String, issueType: PlaybackIssueType, nowMs: Long): PlaybackHealthSnapshot
    suspend fun recordSuccess(key: String, nowMs: Long): PlaybackHealthSnapshot
    suspend fun clear(): PlaybackHealthSnapshot
}

class PersistentPlaybackHealthRepository(private val store: KeyValueStore) : PlaybackHealthRepository {
    // 播放器会从多个协程同时上报成功/失败；没有锁时 read-modify-write 会互相覆盖计数。
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun getSnapshot(): PlaybackHealthSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val nowMs = System.currentTimeMillis()
            val entries = readPrunedEntries(nowMs)
            writeEntries(entries)
            entries.toSnapshot()
        }
    }

    override suspend fun recordIssue(
        key: String,
        issueType: PlaybackIssueType,
        nowMs: Long,
    ): PlaybackHealthSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readPrunedEntries(nowMs)
                .associateBy { it.key }
                .toMutableMap()
            val entry = current[key] ?: PlaybackHealthEntry(key = key)
            current[key] = when (issueType) {
                PlaybackIssueType.Error -> entry.copy(
                    lastFailureAtMs = nowMs,
                    failureCount = entry.failureCount + 1,
                )
                PlaybackIssueType.SlowBuffer -> entry.copy(
                    lastSlowBufferAtMs = nowMs,
                    slowBufferCount = entry.slowBufferCount + 1,
                )
            }
            val entries = prunePlaybackHealthEntries(current.values, nowMs)
            writeEntries(entries)
            entries.toSnapshot()
        }
    }

    override suspend fun recordSuccess(key: String, nowMs: Long): PlaybackHealthSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readPrunedEntries(nowMs)
                .associateBy { it.key }
                .toMutableMap()
            val entry = current[key] ?: PlaybackHealthEntry(key = key)
            current[key] = entry.copy(
                lastSuccessAtMs = nowMs,
                successCount = entry.successCount + 1,
            )
            val entries = prunePlaybackHealthEntries(current.values, nowMs)
            writeEntries(entries)
            entries.toSnapshot()
        }
    }

    override suspend fun clear(): PlaybackHealthSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!store.edit { remove(KEY_ENTRIES) }) throw IOException("播放健康记录清除失败")
            PlaybackHealthSnapshot()
        }
    }

    private fun readEntries(): Map<String, StoredPlaybackHealthEntry> {
        val raw = store.getString(KEY_ENTRIES) ?: return emptyMap()
        return runCatching {
            json.decodeFromString<List<StoredPlaybackHealthEntry>>(raw)
                .filter { it.key.isNotBlank() }
                .associateBy { it.key }
        }.getOrDefault(emptyMap())
    }

    private fun readPrunedEntries(nowMs: Long): List<PlaybackHealthEntry> {
        return prunePlaybackHealthEntries(
            entries = readEntries().values.map { it.toDomain() },
            nowMs = nowMs,
        )
    }

    private fun writeEntries(entries: Collection<PlaybackHealthEntry>) {
        val stored = entries.map { it.toStored() }
        if (!store.edit { putString(KEY_ENTRIES, json.encodeToString(stored)) }) {
            throw IOException("播放健康记录保存失败")
        }
    }

    private fun Collection<PlaybackHealthEntry>.toSnapshot(): PlaybackHealthSnapshot {
        return PlaybackHealthSnapshot(associateBy { it.key })
    }

    private fun PlaybackHealthEntry.toStored(): StoredPlaybackHealthEntry {
        return StoredPlaybackHealthEntry(
            key = key,
            lastFailureAtMs = lastFailureAtMs,
            lastSlowBufferAtMs = lastSlowBufferAtMs,
            lastSuccessAtMs = lastSuccessAtMs,
            failureCount = failureCount,
            slowBufferCount = slowBufferCount,
            successCount = successCount,
        )
    }

    private fun StoredPlaybackHealthEntry.toDomain(): PlaybackHealthEntry {
        return PlaybackHealthEntry(
            key = key,
            lastFailureAtMs = lastFailureAtMs,
            lastSlowBufferAtMs = lastSlowBufferAtMs,
            lastSuccessAtMs = lastSuccessAtMs,
            failureCount = failureCount,
            slowBufferCount = slowBufferCount,
            successCount = successCount,
        )
    }

    private companion object {
        const val KEY_ENTRIES = "entries"
    }
}

@Serializable
private data class StoredPlaybackHealthEntry(
    val key: String,
    val lastFailureAtMs: Long = 0L,
    val lastSlowBufferAtMs: Long = 0L,
    val lastSuccessAtMs: Long = 0L,
    val failureCount: Int = 0,
    val slowBufferCount: Int = 0,
    val successCount: Int = 0,
) {
    val latestActivityAtMs: Long
        get() = maxOf(lastFailureAtMs, lastSlowBufferAtMs, lastSuccessAtMs)
}
