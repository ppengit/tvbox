package com.tvbox.desktop.state

import com.tvbox.desktop.data.FollowedShow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FollowRefreshTest {

    private fun show(
        baseline: Int,
        latest: Int = baseline,
        hasNew: Boolean = false,
    ) = FollowedShow(
        apiLineId = "line",
        movieId = 1,
        name = "测试剧",
        lastEpisodeCount = baseline,
        latestEpisodeCount = latest,
        hasNewEpisodes = hasNew,
    )

    private fun FollowedShow.refresh(count: Int) = refreshedWith(
        detailName = name,
        detailPosterUrl = posterUrl,
        detailYear = year,
        detailArea = area,
        latestCount = count,
        latestTitle = "第 $count 集",
        nowMs = 1_000L,
    )

    private fun FollowedShow.markSeen() = copy(
        lastEpisodeCount = displayEpisodeCount,
        hasNewEpisodes = false,
    )

    @Test
    fun `新集标记会保持到用户点开`() {
        val detected = show(baseline = 10).refresh(count = 12)
        assertTrue(detected.hasNewEpisodes)
        assertEquals(10, detected.lastEpisodeCount)
        assertEquals(12, detected.latestEpisodeCount)

        // 下一轮巡检拿到同样的集数：标记不能被清掉。
        val again = detected.refresh(count = 12)
        assertTrue(again.hasNewEpisodes)
        assertEquals(10, again.lastEpisodeCount)

        val seen = again.markSeen()
        assertFalse(seen.hasNewEpisodes)
        assertEquals(12, seen.lastEpisodeCount)

        // 点开之后再巡检同一集数，不应再次提示。
        val afterSeen = seen.refresh(count = 12)
        assertFalse(afterSeen.hasNewEpisodes)
    }

    @Test
    fun `首次未知集数只补基线不报新集`() {
        val refreshed = show(baseline = 0).refresh(count = 24)
        assertFalse(refreshed.hasNewEpisodes)
        assertEquals(24, refreshed.lastEpisodeCount)
        assertEquals(24, refreshed.latestEpisodeCount)
    }

    @Test
    fun `集数减少时基线与最新集数都不回退`() {
        val refreshed = show(baseline = 20, latest = 24, hasNew = true).refresh(count = 18)
        assertTrue(refreshed.hasNewEpisodes)
        assertEquals(20, refreshed.lastEpisodeCount)
        assertEquals(24, refreshed.latestEpisodeCount)
        assertEquals(24, refreshed.displayEpisodeCount)
    }

    @Test
    fun `集数增加后再次增加仍只算一次更新提示`() {
        val first = show(baseline = 10).refresh(count = 12)
        assertTrue(first.hasNewEpisodes)
        val second = first.refresh(count = 14)
        assertTrue(second.hasNewEpisodes)
        // 更新计数由调用方按「标记从无到有」判断，这里确认标记与最新集数持续推进。
        assertEquals(14, second.latestEpisodeCount)
        assertEquals(10, second.lastEpisodeCount)
    }
}
