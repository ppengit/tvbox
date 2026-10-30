package com.tvbox.desktop.state

import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.PlayEpisode
import com.tvbox.app.domain.PlaySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 进度落盘的版本规则与「旧媒体迟到进度」的归属。
 */
class PlaybackProgressStateTest {

    @Test
    fun `不同剧的进度保存互不顶掉`() {
        val queue = ProgressSaveQueue()
        val movieA = queue.next("line:1")
        val movieB = queue.next("line:2")

        // B 剧排队不应该让 A 剧的保存失效（旧实现用的是全局版本号）。
        assertTrue(queue.isLatest("line:1", movieA))
        assertTrue(queue.isLatest("line:2", movieB))

        val movieAAgain = queue.next("line:1")
        assertFalse(queue.isLatest("line:1", movieA))
        assertTrue(queue.isLatest("line:1", movieAAgain))
        assertTrue(queue.isLatest("line:2", movieB))
    }

    @Test
    fun `还没排队的键不算最新`() {
        val queue = ProgressSaveQueue()

        assertFalse(queue.isLatest("line:1", 1))
        assertTrue(queue.isLatest("line:1", queue.next("line:1")))
    }

    @Test
    fun `换集时旧媒体补报的进度记到旧剧集上`() {
        val tracker = MediaProgressTracker(windowMs = 1_000)
        val previous = state(url = "old-url", episodeIndex = 1)
        val next = state(url = "new-url", episodeIndex = 2)

        tracker.rememberReplaced(previous, next, nowMs = 100)

        assertEquals(previous, tracker.takeReplaced("new-url", nowMs = 200))
        // 只认第一次补报，后续定时保存都按当前媒体算。
        assertNull(tracker.takeReplaced("new-url", nowMs = 300))
    }

    @Test
    fun `超出窗口的迟到进度不再算旧媒体的`() {
        val tracker = MediaProgressTracker(windowMs = 1_000)
        tracker.rememberReplaced(state("old-url", 1), state("new-url", 2), nowMs = 0)

        assertNull(tracker.takeReplaced("new-url", nowMs = 5_000))
        assertNull(tracker.takeReplaced("new-url", nowMs = 5_100))
    }

    @Test
    fun `URL 没变不会记成被替换的媒体`() {
        val tracker = MediaProgressTracker(windowMs = 1_000)
        val same = state("same-url", 1)

        tracker.rememberReplaced(same, same, nowMs = 0)

        assertNull(tracker.takeReplaced("same-url", nowMs = 10))
        assertNull(tracker.takeReplaced("other-url", nowMs = 20))
    }

    @Test
    fun `换回来的旧地址仍按补报处理并保留记录`() {
        val tracker = MediaProgressTracker(windowMs = 1_000)
        tracker.rememberReplaced(state("url-a", 0), state("url-b", 1), nowMs = 0)

        // 当前媒体地址正好等于旧媒体地址：不是补报，记录保留。
        assertNull(tracker.takeReplaced("url-a", nowMs = 10))
        assertEquals("url-a", tracker.takeReplaced("url-b", nowMs = 20)?.currentUrl)
    }

    @Test
    fun `关闭播放页后的收尾进度只认一次且不进直播`() {
        val tracker = MediaProgressTracker(windowMs = 1_000)
        val closed = state("closing-url", 3)

        tracker.rememberClosed(closed, nowMs = 100)
        assertEquals(closed, tracker.takeClosed(nowMs = 200))
        assertNull(tracker.takeClosed(nowMs = 300))

        // 直播关闭（或没有播放器）会清掉残留记录，避免写到点播进度上。
        tracker.rememberClosed(closed, nowMs = 400)
        tracker.rememberClosed(closed.copy(movie = null), nowMs = 500)
        assertNull(tracker.takeClosed(nowMs = 600))

        tracker.rememberClosed(closed, nowMs = 700)
        assertNull(tracker.takeClosed(nowMs = 5_000))
    }

    private fun state(url: String, episodeIndex: Int): PlayerState = PlayerState(
        movie = Movie(
            id = 1,
            apiLineId = "test",
            apiLineName = "接口",
            name = "影片",
            typeId = 1,
            typeName = "电视剧",
            posterUrl = "",
            remarks = "",
            year = "",
            area = "",
            language = "",
            actor = "",
            director = "",
            duration = "",
            description = "",
            playSources = listOf(PlaySource("线路一", listOf(PlayEpisode("第1集", url)))),
        ),
        sourceIndex = 0,
        episodeIndex = episodeIndex,
        currentUrl = url,
    )
}
