package com.tvbox.desktop.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 桌面端的本地数据都靠 kotlinx.serialization 落盘。构建脚本一旦漏掉
 * serialization 插件，代码仍能编译，但运行时会抛「serializer not found」，
 * 追剧/片头片尾/播放策略都会静默保存失败，这里用一个最小往返把问题挡在测试里。
 */
class DesktopSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `追剧条目可序列化往返`() {
        val original = FollowedShow(
            apiLineId = "line-a",
            movieId = 7,
            name = "庆余年",
            lastEpisodeCount = 30,
            hasNewEpisodes = true,
        )
        val restored = json.decodeFromString<FollowedShow>(json.encodeToString(original))
        assertEquals(original, restored)
    }

    @Test
    fun `跳过标记可序列化往返`() {
        val original = SkipMarks(apiLineId = "line-a", movieId = 7, introEndMs = 90_000L, outroStartMs = 1_800_000L)
        val restored = json.decodeFromString<SkipMarks>(json.encodeToString(original))
        assertEquals(original, restored)
    }

    @Test
    fun `播放策略可序列化往返`() {
        val original = MoviePlaybackPolicy(apiLineId = "line-a", movieId = 7, autoSwitch = false, preferFastest = true)
        val restored = json.decodeFromString<MoviePlaybackPolicy>(json.encodeToString(original))
        assertEquals(original, restored)
    }
}
