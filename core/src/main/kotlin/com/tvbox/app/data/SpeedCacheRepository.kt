package com.tvbox.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 可序列化的测速结果。`kbps` 由字节数和耗时推导，不需要单独保存。 */
@Serializable
data class CachedSpeed(
    val targetId: String,
    val targetName: String,
    val ok: Boolean,
    val bytes: Long = 0L,
    val elapsedMs: Long = 0L,
    val latencyMs: Long = 0L,
    val error: String? = null,
) {
    fun toResult(): SpeedResult = SpeedResult(
        targetId = targetId,
        targetName = targetName,
        ok = ok,
        bytes = bytes,
        elapsedMs = elapsedMs,
        latencyMs = latencyMs,
        error = error,
    )

    companion object {
        fun from(result: SpeedResult): CachedSpeed = CachedSpeed(
            targetId = result.targetId,
            targetName = result.targetName,
            ok = result.ok,
            bytes = result.bytes,
            elapsedMs = result.elapsedMs,
            latencyMs = result.latencyMs,
            error = result.error,
        )
    }
}

@Serializable
data class CachedSourceSpeeds(
    val measuredAtMs: Long = 0L,
    val speeds: Map<String, CachedSpeed> = emptyMap(),
) {
    fun toResultsByIndex(sources: List<com.tvbox.app.domain.PlaySource>): Map<Int, SpeedResult> =
        sources.mapIndexedNotNull { index, source ->
            speeds[source.speedCacheKey()]?.let { index to it.toResult() }
        }.toMap()
}

@Serializable
data class CachedLineSpeeds(
    val measuredAtMs: Long = 0L,
    val speeds: Map<String, CachedSpeed> = emptyMap(),
) {
    fun toResults(): Map<String, SpeedResult> = speeds.mapValues { it.value.toResult() }
}

@Serializable
private data class SourceSpeedStore(
    val movies: Map<String, CachedSourceSpeeds> = emptyMap(),
)

/**
 * 播放源 / 数据源测速缓存。
 *
 * 之前测速结果只存在 `DetailState` 和 `SpeedTestState` 里，关掉详情或重启应用就没了，
 * 每次打开同一部剧都要重新测一遍。这里按「剧 + 稳定源标识」落盘，详情页打开时先回填，
 * 用户仍然可以随时重新测速覆盖。
 */
class SpeedCacheRepository(private val store: KeyValueStore) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Synchronized
    fun loadSourceSpeeds(movieKey: String): CachedSourceSpeeds? {
        if (movieKey.isBlank()) return null
        val raw = store.getString(KEY_SOURCE_SPEEDS, null).orEmpty()
        if (raw.isBlank()) return null
        val decoded = runCatching { json.decodeFromString<SourceSpeedStore>(raw) }.getOrNull() ?: return null
        return decoded.movies[movieKey]?.takeIf { it.speeds.isNotEmpty() }
    }

    @Synchronized
    fun saveSourceSpeeds(movieKey: String, speeds: Map<String, CachedSpeed>, measuredAtMs: Long) {
        if (movieKey.isBlank() || speeds.isEmpty()) return
        val current = readSourceStore().movies.toMutableMap()
        current[movieKey] = CachedSourceSpeeds(measuredAtMs = measuredAtMs, speeds = speeds)
        // 只保留最近测过的若干部剧，避免这个文件无限增长。
        val trimmed = current.entries
            .sortedByDescending { it.value.measuredAtMs }
            .take(MAX_MOVIES)
            .associate { it.key to it.value }
        write(KEY_SOURCE_SPEEDS, json.encodeToString(SourceSpeedStore(trimmed)))
    }

    @Synchronized
    fun loadLineSpeeds(): CachedLineSpeeds? {
        val raw = store.getString(KEY_LINE_SPEEDS, null).orEmpty()
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString<CachedLineSpeeds>(raw) }.getOrNull()
            ?.takeIf { it.speeds.isNotEmpty() }
    }

    @Synchronized
    fun saveLineSpeeds(speeds: Map<String, CachedSpeed>, measuredAtMs: Long) {
        if (speeds.isEmpty()) return
        write(
            KEY_LINE_SPEEDS,
            json.encodeToString(CachedLineSpeeds(measuredAtMs = measuredAtMs, speeds = speeds)),
        )
    }

    @Synchronized
    fun clearLineSpeeds() {
        runCatching { store.edit { putString(KEY_LINE_SPEEDS, null) } }
    }

    @Synchronized
    fun clear() {
        runCatching {
            store.edit {
                putString(KEY_SOURCE_SPEEDS, null)
                putString(KEY_LINE_SPEEDS, null)
            }
        }
    }

    private fun readSourceStore(): SourceSpeedStore {
        val raw = store.getString(KEY_SOURCE_SPEEDS, null).orEmpty()
        if (raw.isBlank()) return SourceSpeedStore()
        return runCatching { json.decodeFromString<SourceSpeedStore>(raw) }.getOrDefault(SourceSpeedStore())
    }

    private fun write(key: String, value: String) {
        if (!runCatching { store.edit { putString(key, value) } }.getOrDefault(false)) {
            AppLog.warn("Speed", "测速缓存写入失败：$key")
        }
    }

    private companion object {
        const val KEY_SOURCE_SPEEDS = "source_speeds"
        const val KEY_LINE_SPEEDS = "line_speeds"
        const val MAX_MOVIES = 80
    }
}

/**
 * 同一条播放源在不同批次的线路列表里顺序可能变，缓存必须按稳定身份匹配。
 * 优先线路 id，其次线路名，再退回播放源名。
 */
fun com.tvbox.app.domain.PlaySource.speedCacheKey(): String =
    listOf(lineId, lineName, name).joinToString("|")

fun movieSpeedCacheKey(apiLineId: String, movieId: Int): String = "$apiLineId:$movieId"
