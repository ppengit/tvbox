package com.tvbox.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 一部剧的片头片尾标记。同一部剧各集共用一组标记，不同剧各自独立。
 * 时间点为 0 表示未设置。
 */
@Serializable
data class SkipMarks(
    val apiLineId: String,
    val movieId: Int,
    val introEndMs: Long = 0L,
    val outroStartMs: Long = 0L,
) {
    val key: String get() = "$apiLineId:$movieId"
    val hasIntro: Boolean get() = introEndMs > 0L
    val hasOutro: Boolean get() = outroStartMs > 0L
}

class SkipMarksRepository(private val store: KeyValueStore) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Synchronized
    fun load(): List<SkipMarks> {
        val raw = store.getString(KEY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        val decoded = runCatching { json.decodeFromString<List<SkipMarks>>(raw) }.getOrNull()
        if (decoded == null) store.backupCorruptValue(KEY, raw)
        return decoded ?: emptyList()
    }

    // load + persist 是读改写，必须整体串行，否则播放器自动记录和详情页手动修改会互相覆盖。
    @Synchronized
    fun save(marks: SkipMarks) {
        persist(load().filterNot { it.key == marks.key } + marks)
    }

    @Synchronized
    fun remove(apiLineId: String, movieId: Int) {
        persist(load().filterNot { it.apiLineId == apiLineId && it.movieId == movieId })
    }

    private fun persist(items: List<SkipMarks>) {
        store.edit { putString(KEY, json.encodeToString(items)) }
    }

    private companion object {
        const val KEY = "skip_marks"
    }
}
