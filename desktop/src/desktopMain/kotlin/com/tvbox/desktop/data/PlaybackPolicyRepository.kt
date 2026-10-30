package com.tvbox.desktop.data

import com.tvbox.app.data.KeyValueStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 单部影片的播放策略覆盖。null 表示跟随全局设置；非 null 时以本剧设置为准。
 * 追剧列表里长期跟的剧，往往希望单独锁定一条稳定线路，而不是每集都重选。
 */
@Serializable
data class MoviePlaybackPolicy(
    val apiLineId: String,
    val movieId: Int,
    val autoSwitch: Boolean? = null,
    val preferFastest: Boolean? = null,
) {
    val key: String get() = "$apiLineId:$movieId"
}

class PlaybackPolicyRepository(private val store: KeyValueStore) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Synchronized
    fun load(): List<MoviePlaybackPolicy> {
        val raw = store.getString(KEY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        val decoded = runCatching { json.decodeFromString<List<MoviePlaybackPolicy>>(raw) }.getOrNull()
        if (decoded == null) store.backupCorruptValue(KEY, raw)
        return decoded ?: emptyList()
    }

    @Synchronized
    fun find(apiLineId: String, movieId: Int): MoviePlaybackPolicy? =
        load().firstOrNull { it.apiLineId == apiLineId && it.movieId == movieId }

    /** 两个覆盖项都为 null 时直接删除，避免存储里堆无效条目。 */
    @Synchronized
    fun save(policy: MoviePlaybackPolicy) {
        val rest = load().filterNot { it.key == policy.key }
        val next = if (policy.autoSwitch == null && policy.preferFastest == null) rest else rest + policy
        persist(next)
    }

    @Synchronized
    fun remove(apiLineId: String, movieId: Int) {
        persist(load().filterNot { it.apiLineId == apiLineId && it.movieId == movieId })
    }

    private fun persist(items: List<MoviePlaybackPolicy>) {
        store.edit { putString(KEY, json.encodeToString(items)) }
    }

    private companion object {
        const val KEY = "movie_playback_policies"
    }
}
