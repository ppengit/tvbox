package com.tvbox.desktop.data

import com.tvbox.app.data.KeyValueStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 追剧条目。`lastEpisodeCount` 是用户已经知道的集数（基线），
 * `latestEpisodeCount` 是最近一次巡检发现的实际集数；两者拉开距离就说明有新集，
 * 在用户点开这部剧之前一直保留「有新集」标记。
 */
@Serializable
data class FollowedShow(
    val apiLineId: String,
    val movieId: Int,
    val name: String,
    val posterUrl: String = "",
    val year: String = "",
    val area: String = "",
    val followedAtMs: Long = 0L,
    val lastEpisodeCount: Int = 0,
    val lastCheckedAtMs: Long = 0L,
    val lastEpisodeIndex: Int = 0,
    val lastPositionMs: Long = 0L,
    val latestEpisodeTitle: String = "",
    val hasNewEpisodes: Boolean = false,
    val latestEpisodeCount: Int = 0,
) {
    val key: String get() = "$apiLineId:$movieId"

    /** 卡片上展示的集数优先用巡检到的最新值。 */
    val displayEpisodeCount: Int get() = maxOf(lastEpisodeCount, latestEpisodeCount)
}

class FollowedShowRepository(private val store: KeyValueStore) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Synchronized
    fun load(): List<FollowedShow> {
        val raw = store.getString(KEY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        val decoded = runCatching { json.decodeFromString<List<FollowedShow>>(raw) }.getOrNull()
        if (decoded == null) store.backupCorruptValue(KEY, raw)
        return decoded ?: emptyList()
    }

    @Synchronized
    fun save(items: List<FollowedShow>) {
        store.edit { putString(KEY, json.encodeToString(items)) }
    }

    private companion object {
        const val KEY = "followed_shows"
    }
}
