package com.tvbox.app.domain

import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class WatchHistoryTest {
    @Test fun completionUsesExactRatioAndMeansOnlyTheSavedEpisode() {
        assertFalse(history().copy(positionMs = 94_999, durationMs = 100_000).isCompleted)
        assertTrue(history().copy(positionMs = 95_000, durationMs = 100_000).isCompleted)
        assertTrue(history().copy(positionMs = 120_000, durationMs = 100_000).isCompleted)
        assertFalse(history().copy(positionMs = 0).isCompleted)
        assertFalse(history().copy(durationMs = 0).isCompleted)
        assertFalse(history().copy(durationMs = -1).isCompleted)
    }

    @Test fun filteringKeepsLatestBeforeCompletionAndSeparatesApiLines() {
        val old = history().copy(updatedAtEpochMs = 1)
        val finished = old.copy(positionMs = 100_000, updatedAtEpochMs = 3)
        val otherApi = old.copy(apiLineId = "other", updatedAtEpochMs = 2)
        val records = listOf(old, finished, otherApi)
        assertEquals(listOf("test:1", "other:1"), records.filterHistory(HistoryFilter.All).map { it.historyKey })
        assertEquals(listOf("test:1"), records.filterHistory(HistoryFilter.Completed).map { it.historyKey })
        assertEquals(listOf("other:1"), records.filterHistory(HistoryFilter.Unfinished).map { it.historyKey })
        assertEquals(listOf("other:1"), records.continueWatching().map { it.historyKey })
    }

    @Test fun zeroAndUnknownDurationStayUnfinishedButZeroDoesNotContinue() {
        val zero = history().copy(positionMs = 0)
        val unknown = history().copy(movieId = 2, durationMs = 0)
        assertEquals(2, listOf(zero, unknown).filterHistory(HistoryFilter.Unfinished).size)
        assertEquals(listOf(2), listOf(zero, unknown).continueWatching().map { it.movieId })
        assertTrue(listOf(unknown).continueWatching(-1).isEmpty())
    }

    @Test fun progressDoesNotOverflowAndFormatsExactSecondsAndKnownTotal() {
        assertEquals(100, history().copy(positionMs = Long.MAX_VALUE, durationMs = 1).progressPercent)
        assertEquals(100, history().copy(positionMs = Long.MAX_VALUE, durationMs = Long.MAX_VALUE).progressPercent)
        assertEquals("1:01:01 / 2:00:00 · 50%", history().copy(positionMs = 3_661_000, durationMs = 7_200_000).historyProgressText())
        assertEquals("已看 0:05 · 总时长未知", history().copy(positionMs = 5_999, durationMs = 0).historyProgressText())
        assertEquals("0:00 / 1:40 · 0%", history().copy(positionMs = -1).historyProgressText())
    }

    @Test fun dateUsesRequestedTimezoneAndUnknownDateIsExplicit() {
        assertEquals("最近观看 2026-10-22 08:09", history().copy(updatedAtEpochMs = java.time.Instant.parse("2026-10-22T08:09:00Z").toEpochMilli()).historyDateText(ZoneId.of("UTC")))
        assertEquals("最近观看 2026-10-22 16:09", history().copy(updatedAtEpochMs = java.time.Instant.parse("2026-10-22T08:09:00Z").toEpochMilli()).historyDateText(ZoneId.of("Asia/Shanghai")))
        assertEquals("观看日期未知", history().copy(updatedAtEpochMs = 0).historyDateText())
    }

    @Test fun focusKeepsCardThenChoosesNextPreviousOrNoCard() {
        val records = (1..3).map { history().copy(movieId = it) }
        assertEquals("test:2", records.historyFocusKey("test:2", 0))
        assertEquals("test:3", listOf(records[0], records[2]).historyFocusKey("test:2", 1))
        assertEquals("test:2", records.take(2).historyFocusKey("test:3", 2))
        assertNull(emptyList<WatchHistoryItem>().historyFocusKey("test:1", 0))
    }

    @Test fun resumeUsesUrlThenStableSourceNameAndEpisodeNumber() {
        val original = history().copy(sourceIndex = 0, sourceName = "saved", episodeIndex = 0,
            episodeTitle = "第02集", episodeUrl = "old-url")
        val sources = listOf(PlaySource("other", listOf(PlayEpisode("第1集", "other"))),
            PlaySource("saved", listOf(PlayEpisode("第1集", "one"), PlayEpisode("02", "new-url"))))
        val movie = movie(sources)
        assertEquals(1 to 1, resolveHistoryEpisodePosition(movie, original))
        assertEquals(1 to 0, resolveHistoryEpisodePosition(movie, original.copy(episodeUrl = "one")))
        assertEquals(0 to 0, resolveHistoryEpisodePosition(movie(emptyList()), original))
    }

    @Test fun matrixSelectionNeverChangesEpisodeButExplicitSourceSwitchUsesNearest() {
        val matrix = buildEpisodeMatrix(
            listOf(
                PlaySource("sparse", listOf(PlayEpisode("第1集", "s1"), PlayEpisode("第3集", "s3"))),
                PlaySource("full", listOf(PlayEpisode("第1集", "f1"), PlayEpisode("第2集", "f2"), PlayEpisode("第3集", "f3"))),
            ),
        )

        assertEquals(1 to 1, matrix.resolvePlayable(sourceIndex = 0, episodeIndex = 1))
        assertEquals(0 to 0, matrix.resolveSourceSwitch(sourceIndex = 0, episodeIndex = 1))
        assertNull(matrix.resolvePlayable(sourceIndex = 0, episodeIndex = 99))
    }

    @Test fun resumeReturnsMatrixIndexForSparseSource() {
        val sources = listOf(
            PlaySource("saved", listOf(PlayEpisode("第1集", "one"), PlayEpisode("第3集", "three"))),
            PlaySource("full", listOf(PlayEpisode("第1集", "f1"), PlayEpisode("第2集", "f2"), PlayEpisode("第3集", "f3"))),
        )
        val saved = history().copy(
            sourceName = "saved",
            sourceIndex = 0,
            episodeIndex = 1,
            episodeCount = 3,
            episodeTitle = "第3集",
            episodeUrl = "three",
        )

        assertEquals(0 to 2, resolveHistoryEpisodePosition(movie(sources), saved))
    }

    private fun history() = WatchHistoryItem(1, "test", "接口", "影片", "", "电视剧", "",
        0, "线路", 0, "第1集", "https://example.test/1", 25_000, 100_000, 1)

    private fun movie(sources: List<PlaySource>) = Movie(1, "test", "接口", "影片", 1, "电视剧", "", "",
        "", "", "", "", "", "", "", sources)
}
