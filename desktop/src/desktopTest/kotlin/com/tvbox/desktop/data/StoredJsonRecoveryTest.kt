package com.tvbox.desktop.data

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoredJsonRecoveryTest {

    @Test
    fun `损坏的 JSON 会备份原文且不影响后续使用`() {
        val file = Files.createTempDirectory("stored-json").resolve("store.properties")
        val store = FileKeyValueStore(file)
        store.edit {
            putString("skip_marks", "{not-json")
            putString("followed_shows", "[")
            putString("movie_playback_policies", "oops")
            putString("recent_searches", "{}")
        }

        assertTrue(SkipMarksRepository(store).load().isEmpty())
        assertTrue(FollowedShowRepository(store).load().isEmpty())
        assertTrue(PlaybackPolicyRepository(store).load().isEmpty())
        assertTrue(SearchHistoryRepository(store).load().isEmpty())

        assertEquals("{not-json", store.getString("skip_marks.corrupt"))
        assertEquals("[", store.getString("followed_shows.corrupt"))
        assertEquals("oops", store.getString("movie_playback_policies.corrupt"))
        assertEquals("{}", store.getString("recent_searches.corrupt"))

        // 备份之后仍可正常写入新数据。
        assertTrue(SearchHistoryRepository(store).record("新关键词").contains("新关键词"))
    }
}
