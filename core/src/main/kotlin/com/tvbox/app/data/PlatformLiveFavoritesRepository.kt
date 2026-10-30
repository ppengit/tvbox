package com.tvbox.app.data

import com.tvbox.app.domain.PlatformLiveRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

interface PlatformLiveFavoritesRepository {
    suspend fun getFavorites(): List<PlatformLiveRoom>
    suspend fun toggleFavorite(room: PlatformLiveRoom): List<PlatformLiveRoom>
}

class PersistentPlatformLiveFavoritesRepository(private val store: KeyValueStore) : PlatformLiveFavoritesRepository {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getFavorites(): List<PlatformLiveRoom> = withContext(Dispatchers.IO) {
        mutex.withLock { readFavorites().map(FavoriteRoomRecord::toRoom) }
    }

    override suspend fun toggleFavorite(room: PlatformLiveRoom): List<PlatformLiveRoom> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readFavorites()
            val updated = toggleFavoriteRecords(current, room)
            if (!store.edit { putString(KEY_ROOMS, json.encodeToString(updated)) }) {
                throw IOException("收藏保存失败")
            }
            updated.map(FavoriteRoomRecord::toRoom)
        }
    }

    private fun readFavorites(): List<FavoriteRoomRecord> {
        val raw = store.getString(KEY_ROOMS).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<FavoriteRoomRecord>>(raw) }
            .getOrElse { throw IOException("收藏数据读取失败", it) }
            .filter { it.site.isNotBlank() && it.roomId.isNotBlank() }
            .distinctBy { favoriteKey(it.site, it.roomId) }
    }

    private companion object {
        const val KEY_ROOMS = "rooms"
    }
}

private fun favoriteKey(site: String, roomId: String): String = "${site.lowercase()}:$roomId"

internal fun toggleFavoriteRecords(current: List<FavoriteRoomRecord>, room: PlatformLiveRoom): List<FavoriteRoomRecord> {
    val key = favoriteKey(room.site, room.roomId)
    return if (current.any { favoriteKey(it.site, it.roomId) == key }) {
        current.filterNot { favoriteKey(it.site, it.roomId) == key }
    } else {
        listOf(FavoriteRoomRecord.fromRoom(room)) + current
    }
}

@Serializable
internal data class FavoriteRoomRecord(
    val site: String,
    val roomId: String,
    val title: String,
    val anchor: String,
    val cover: String,
    val categoryId: String,
    val categoryName: String,
) {
    fun toRoom(): PlatformLiveRoom = PlatformLiveRoom(
        id = favoriteKey(site, roomId),
        site = site,
        roomId = roomId,
        title = title,
        anchor = anchor,
        cover = cover,
        online = 0,
        categoryId = categoryId,
        categoryName = categoryName,
    )

    companion object {
        fun fromRoom(room: PlatformLiveRoom): FavoriteRoomRecord = FavoriteRoomRecord(
            site = room.site.lowercase(),
            roomId = room.roomId,
            title = room.title,
            anchor = room.anchor,
            cover = room.cover,
            categoryId = room.categoryId,
            categoryName = room.categoryName,
        )
    }
}
