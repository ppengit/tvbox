package com.tvbox.desktop.data

import com.tvbox.app.data.KeyValueStore
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 搜索历史：最近的关键词去重保存，最多 12 条。 */
class SearchHistoryRepository(private val store: KeyValueStore) {

    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<String> {
        val raw = store.getString(KEY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    fun record(keyword: String): List<String> {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return load()
        val next = (listOf(trimmed) + load().filterNot { it.equals(trimmed, ignoreCase = true) })
            .take(MAX_ENTRIES)
        persist(next)
        return next
    }

    fun clear() {
        store.edit { putString(KEY, "[]") }
    }

    private fun persist(items: List<String>) {
        store.edit { putString(KEY, json.encodeToString(items)) }
    }

    private companion object {
        const val KEY = "recent_searches"
        const val MAX_ENTRIES = 12
    }
}
