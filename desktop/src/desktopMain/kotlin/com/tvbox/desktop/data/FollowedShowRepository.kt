package com.tvbox.desktop.data

import com.tvbox.app.data.KeyValueStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 追剧条目。记录关注时的集数，之后每次刷新检测到更多集数就标记为「有新集」。
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
) {
    val key: String get() = "$apiLineId:$movieId"
}

class FollowedShowRepository(private val store: KeyValueStore) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun load(): List<FollowedShow> {
        val raw = store.getString(KEY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<FollowedShow>>(raw) }.getOrDefault(emptyList())
    }

    fun save(items: List<FollowedShow>) {
        store.edit { putString(KEY, json.encodeToString(items)) }
    }

    private companion object {
        const val KEY = "followed_shows"
    }
}
