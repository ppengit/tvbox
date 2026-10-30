package com.tvbox.app.data

import com.tvbox.app.domain.PlaybackIssueType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class PlaybackHealthRepositoryTest {

    @Test
    fun concurrentReportsDoNotLoseCounts() = runBlocking {
        val store = InMemoryKeyValueStore()
        val repository = PersistentPlaybackHealthRepository(store)
        val nowMs = System.currentTimeMillis()

        coroutineScope {
            repeat(60) {
                launch(Dispatchers.Default) {
                    repository.recordIssue("movie:1:source", PlaybackIssueType.Error, nowMs)
                }
            }
            repeat(40) {
                launch(Dispatchers.Default) {
                    repository.recordSuccess("movie:1:source", nowMs)
                }
            }
        }

        val entry = repository.getSnapshot().entryFor("movie:1:source")!!
        assertEquals(60, entry.failureCount)
        assertEquals(40, entry.successCount)
    }

    @Test
    fun writeFailureIsReported() = runBlocking {
        val repository = PersistentPlaybackHealthRepository(InMemoryKeyValueStore(editSucceeds = false))

        try {
            repository.recordIssue("movie:1:source", PlaybackIssueType.Error, System.currentTimeMillis())
            fail("写入失败时不能假装记录成功")
        } catch (expected: IOException) {
            assertEquals("播放健康记录保存失败", expected.message)
        }
    }
}
