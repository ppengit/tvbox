package com.tvbox.desktop.state

import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.PlayEpisode
import com.tvbox.app.domain.PlaySource
import com.tvbox.app.domain.buildEpisodeMatrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 播放页状态切换的纯逻辑：切集/切源后的进度与失败线路清理、失败换源的候选集，
 * 以及「统一集数下标」和线路自身下标混用时的兜底。
 */
class PlaybackSwitchStateTest {

    @Test
    fun `换集从头开始并清掉本轮失败线路`() {
        val before = playerState(sourceIndex = 1, episodeIndex = 2, url = "line1-ep3")
            .copy(
                startPositionMs = 90_000,
                lastPositionMs = 90_000,
                lastDurationMs = 100_000,
                failedSourceIndices = setOf(0, 2),
                switchNotice = "旧的提示",
            )

        val after = before.withEpisodeSwitch(
            targetSource = 1,
            targetEpisode = 3,
            url = "line1-ep4",
            title = "第4集",
            episodeCount = 10,
            startPositionMs = 0L,
            durationMs = 0L,
            switchNotice = null,
        )

        assertEquals(1, after.sourceIndex)
        assertEquals(3, after.episodeIndex)
        assertEquals("line1-ep4", after.currentUrl)
        assertEquals(0L, after.startPositionMs)
        assertEquals(0L, after.lastPositionMs)
        assertEquals(0L, after.lastDurationMs)
        assertTrue(after.failedSourceIndices.isEmpty())
        assertNull(after.switchNotice)
    }

    @Test
    fun `同一集换线路保留观看进度`() {
        val before = playerState(sourceIndex = 0, episodeIndex = 2, url = "line0-ep3")
            .copy(lastPositionMs = 42_000, lastDurationMs = 1_000_000)

        val after = before.withEpisodeSwitch(
            targetSource = 2,
            targetEpisode = 2,
            url = "line2-ep3",
            title = "第3集",
            episodeCount = 10,
            startPositionMs = before.lastPositionMs,
            durationMs = before.lastDurationMs,
            switchNotice = "已切换到「线路三」",
        )

        assertEquals(2, after.sourceIndex)
        assertEquals(2, after.episodeIndex)
        assertEquals(42_000L, after.startPositionMs)
        assertEquals(42_000L, after.lastPositionMs)
        assertEquals(1_000_000L, after.lastDurationMs)
        assertEquals("已切换到「线路三」", after.switchNotice)
    }

    @Test
    fun `失败换源只挑剩余线路里确实有这一集的`() {
        val movie = movie(
            listOf(
                PlaySource(
                    "完整",
                    listOf(
                        PlayEpisode("第1集", "a1"),
                        PlayEpisode("第2集", "a2"),
                        PlayEpisode("第3集", "a3"),
                    ),
                ),
                PlaySource(
                    "缺第2集",
                    listOf(PlayEpisode("第1集", "b1"), PlayEpisode("第3集", "b3")),
                ),
                PlaySource("只有第1集", listOf(PlayEpisode("第1集", "c1"))),
            ),
        )
        val matrix = buildEpisodeMatrix(movie.playSources)

        assertEquals(
            listOf(1, 2),
            failureCandidates(matrix, failedSourceIndices = setOf(0), episodeIndex = 0),
        )
        // 第 2 集在剩下的两条线路里都没有，不能换过去。
        assertEquals(
            emptyList(),
            failureCandidates(matrix, failedSourceIndices = setOf(0), episodeIndex = 1),
        )
        assertEquals(
            listOf(2),
            failureCandidates(matrix, failedSourceIndices = setOf(0, 1), episodeIndex = 0),
        )
        assertEquals(
            emptyList(),
            failureCandidates(matrix, failedSourceIndices = emptySet(), episodeIndex = 99),
        )
    }

    @Test
    fun `统一集数下标在线路缺集时换成能播这一集的线路`() {
        val movie = movie(
            listOf(
                PlaySource("完整", listOf(PlayEpisode("第1集", "a1"), PlayEpisode("第2集", "a2"))),
                PlaySource("空线路", emptyList()),
            ),
        )
        val matrix = buildEpisodeMatrix(movie.playSources)

        assertEquals(0, playableSourceIndex(matrix, requested = 1, episodeIndex = 0))
        assertEquals(0, playableSourceIndex(matrix, requested = 0, episodeIndex = 1))
        // 越界的集数保持请求线路，由调用方提示“所有线路都缺少这一集”。
        assertEquals(1, playableSourceIndex(matrix, requested = 1, episodeIndex = 99))
        assertEquals(0, playableSourceIndex(matrix, requested = 99, episodeIndex = 0))
    }

    private fun playerState(
        sourceIndex: Int,
        episodeIndex: Int,
        url: String,
    ): PlayerState = PlayerState(
        movie = movie(
            listOf(
                PlaySource("线路一", (1..10).map { index -> PlayEpisode("第${index}集", "line0-ep$index") }),
            ),
        ),
        sourceIndex = sourceIndex,
        episodeIndex = episodeIndex,
        currentUrl = url,
    )

    private fun movie(sources: List<PlaySource>): Movie = Movie(
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
        playSources = sources,
    )
}
