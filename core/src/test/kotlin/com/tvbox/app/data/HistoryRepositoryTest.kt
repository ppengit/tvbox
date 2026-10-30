package com.tvbox.app.data

import com.tvbox.app.domain.WatchHistoryItem
import com.tvbox.app.domain.historyKey
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class HistoryRepositoryTest {
    @Test fun removeUsesBothApiLineAndMovieIdAndPreservesOtherRecords() {
        val first = history()
        val otherApi = first.copy(apiLineId = "other")
        val otherMovie = first.copy(movieId = 2)
        assertEquals(listOf(otherApi, otherMovie), removeHistoryRecords(listOf(first, otherApi, otherMovie), "test", 1))
        assertEquals(listOf(first), removeHistoryRecords(listOf(first), "missing", 1))
    }

    @Test fun saveReplacesOnlyItsCompositeKeyCapsAndUpdatesDate() {
        val first = history()
        val otherApi = first.copy(apiLineId = "other")
        val updated = mergeHistoryProgress(listOf(first, otherApi, first.copy(movieId = 2)), first.copy(positionMs = 50_000), 456, 2)
        assertEquals(listOf("test:1", "other:1"), updated.map { it.historyKey })
        assertEquals(456L, updated.first().updatedAtEpochMs)
        assertEquals(50_000L, updated.first().positionMs)
    }

    @Test fun knownDurationSurvivesPreparingSameUrlAfterSourceAndEpisodeIndicesMove() {
        val previous = history()
        val preparing = previous.copy(durationMs = 0, sourceIndex = 5, episodeIndex = 3, episodeTitle = "01")
        val merged = mergeHistoryProgress(listOf(previous), preparing, 2, 100).single()
        assertEquals(previous.durationMs, merged.durationMs)
        assertEquals(5, merged.sourceIndex)
        assertEquals(3, merged.episodeIndex)
    }

    @Test fun durationIsNeverInheritedFromDifferentEpisodeOrApiLineOrBlankUrl() {
        val previous = history()
        val differentEpisode = previous.copy(episodeUrl = "new", durationMs = 0)
        assertEquals(0L, mergeHistoryProgress(listOf(previous), differentEpisode, 2, 100).first().durationMs)
        val otherApi = previous.copy(apiLineId = "other", durationMs = 0)
        assertEquals(0L, mergeHistoryProgress(listOf(previous), otherApi, 2, 100).first().durationMs)
        val blank = previous.copy(episodeUrl = "")
        assertEquals(0L, mergeHistoryProgress(listOf(blank), blank.copy(durationMs = 0), 2, 100).first().durationMs)
    }

    @Test fun oldJsonWithoutApiFieldsDecodesAndUnknownFieldsAreIgnored() {
        val raw = """[{"movieId":1,"movieName":"movie","posterUrl":"","typeName":"drama","remarks":"","sourceIndex":0,"sourceName":"line","episodeIndex":0,"episodeTitle":"one","episodeUrl":"https://test/one","positionMs":1000,"durationMs":2000,"updatedAtEpochMs":3,"futureField":true}]"""
        val decoded = decodeHistoryRecords(raw).single()
        assertEquals("ruyi", decoded.apiLineId)
        assertEquals("如意", decoded.apiLineName)
        val encoded = HISTORY_JSON.encodeToString(listOf(decoded))
        assertFalse(encoded.contains("isCompleted"))
        assertFalse(encoded.contains("historyKey"))
        assertEquals(listOf(decoded), Json.decodeFromString<List<WatchHistoryItem>>(encoded))
    }

    @Test fun malformedJsonAndMissingRequiredFieldsFailInsteadOfReplacingWithEmpty() {
        for (raw in listOf("not-json", "[{\"movieId\":1}]", "[{\"movieId\":\"invalid\"}]")) {
            try {
                decodeHistoryRecords(raw)
                fail("Corrupt history must not become an empty list")
            } catch (expected: IOException) {
                assertNotNull(expected.cause)
            }
        }
        assertTrue(decodeHistoryRecords("[]").isEmpty())
    }

    @Test fun restoreKeepsUndoneSnapshotEvenWhenHistoryIsFull() {
        val restored = history().copy(movieId = 99, updatedAtEpochMs = 1)
        val full = (1..3).map { history().copy(movieId = it, updatedAtEpochMs = 100L + it) }

        val result = restoreHistoryRecord(full, restored, limit = 3)

        assertEquals(3, result.size)
        assertTrue(result.any { it.movieId == 99 })
        assertTrue(result.none { it.movieId == 1 })
        assertTrue(restoreHistoryRecord(full, restored, limit = 0).isEmpty())
    }

    @Test fun restoreReplacesStaleDuplicateAndKeepsOrder() {
        val stale = history().copy(positionMs = 1, updatedAtEpochMs = 5)
        val restored = stale.copy(positionMs = 50_000)
        val other = history().copy(movieId = 2, updatedAtEpochMs = 10)

        val result = restoreHistoryRecord(listOf(stale, other), restored, limit = 10)

        assertEquals(listOf(2, 1), result.map { it.movieId })
        assertEquals(50_000L, result.single { it.movieId == 1 }.positionMs)
    }

    @Test fun historyReadIsCappedAtOneHundredNewestRecords() = runBlocking {
        val items = (1..120).map { id ->
            history().copy(movieId = id, updatedAtEpochMs = id.toLong())
        }
        val store = InMemoryKeyValueStore(mapOf("items" to HISTORY_JSON.encodeToString(items)))
        val loaded = PersistentHistoryRepository(store).getHistory()

        assertEquals(100, loaded.size)
        assertEquals(120, loaded.first().movieId)
        assertEquals(21, loaded.last().movieId)
    }

    @Test fun removeInvalidatesQueuedOldSnapshotButAllowsNewPlaybackAndOtherApi() {
        val guard = HistoryWriteGuard()
        val item = history()
        val other = item.copy(apiLineId = "other")
        val old = guard.version(item)
        val otherOld = guard.version(other)
        guard.remove(item)
        assertFalse(guard.isCurrent(item, old))
        assertTrue(guard.isCurrent(other, otherOld))
        assertTrue(guard.isCurrent(item, guard.version(item)))
    }

    @Test fun clearInvalidatesEveryQueuedSnapshotEvenAfterEarlierRemove() {
        val guard = HistoryWriteGuard()
        val item = history()
        guard.remove(item)
        val beforeClear = guard.version(item)
        val other = item.copy(apiLineId = "other")
        val otherBeforeClear = guard.version(other)
        guard.clear()
        assertFalse(guard.isCurrent(item, beforeClear))
        assertFalse(guard.isCurrent(other, otherBeforeClear))
        assertTrue(guard.isCurrent(item, guard.version(item)))
    }

    private fun history() = WatchHistoryItem(1, "test", "接口", "影片", "", "电视剧", "",
        0, "线路", 0, "第1集", "https://test/one", 25_000, 100_000, 1)
}
