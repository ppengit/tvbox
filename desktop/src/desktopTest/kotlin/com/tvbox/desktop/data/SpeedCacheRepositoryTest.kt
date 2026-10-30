package com.tvbox.desktop.data

import com.tvbox.app.data.KeyValueStore
import com.tvbox.app.data.SpeedResult
import com.tvbox.app.domain.PlaySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeedCacheRepositoryTest {

    private fun result(id: String, ok: Boolean = true, latency: Long = 120L, bytes: Long = 1024L) =
        SpeedResult(targetId = id, targetName = id, ok = ok, bytes = bytes, elapsedMs = 500L, latencyMs = latency)

    @Test
    fun `线路测速结果写入后重启仍能读回`() {
        val store = MemoryStore()
        SpeedCacheRepository(store).saveLineSpeeds(
            speeds = mapOf("line-a" to CachedSpeed.from(result("line-a", latency = 88))),
            measuredAtMs = 1_700_000_000_000L,
        )

        val cached = SpeedCacheRepository(store).loadLineSpeeds()
        assertNotNull(cached)
        assertEquals(1_700_000_000_000L, cached.measuredAtMs)
        val line = cached.toResults()["line-a"]
        assertNotNull(line)
        assertTrue(line.ok)
        assertEquals(88L, line.latencyMs)
    }

    @Test
    fun `播放源结果按稳定标识回填而不是按当前顺序`() {
        val store = MemoryStore()
        val repository = SpeedCacheRepository(store)
        val original = listOf(source("A"), source("B"), source("C"))
        repository.saveSourceSpeeds(
            movieKey = movieSpeedCacheKey("line-a", 7),
            speeds = original.associate { it.speedCacheKey() to CachedSpeed.from(result(it.name, latency = 50)) },
            measuredAtMs = 42L,
        )

        // 第二次请求源顺序变了，缓存必须跟着名称走。
        val reordered = listOf(source("C"), source("A"))
        val cached = repository.loadSourceSpeeds(movieSpeedCacheKey("line-a", 7))
        assertNotNull(cached)
        val mapped = cached.toResultsByIndex(reordered)
        assertEquals(50L, mapped.getValue(0).latencyMs)
        assertEquals(50L, mapped.getValue(1).latencyMs)
        assertEquals("C", mapped.getValue(0).targetName)
        assertEquals("A", mapped.getValue(1).targetName)
        assertNull(mapped[2])
    }

    @Test
    fun `清空线路结果后不再返回旧缓存`() {
        val store = MemoryStore()
        val repository = SpeedCacheRepository(store)
        repository.saveLineSpeeds(mapOf("line-a" to CachedSpeed.from(result("line-a"))), 1L)
        repository.clearLineSpeeds()
        assertNull(repository.loadLineSpeeds())
    }

    private fun source(name: String) = PlaySource(
        name = name,
        episodes = emptyList(),
        lineId = "line-$name",
        lineName = "线路 $name",
    )

    private class MemoryStore : KeyValueStore {
        private val values = mutableMapOf<String, String>()

        override fun getString(key: String, defaultValue: String?): String? = values[key] ?: defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            values[key]?.toBooleanStrictOrNull() ?: defaultValue

        override fun getFloat(key: String, defaultValue: Float): Float =
            values[key]?.toFloatOrNull() ?: defaultValue

        override fun edit(block: KeyValueStore.Editor.() -> Unit): Boolean {
            val editor = object : KeyValueStore.Editor {
                override fun putString(key: String, value: String?): KeyValueStore.Editor {
                    if (value == null) values.remove(key) else values[key] = value
                    return this
                }

                override fun putBoolean(key: String, value: Boolean): KeyValueStore.Editor {
                    values[key] = value.toString()
                    return this
                }

                override fun putFloat(key: String, value: Float): KeyValueStore.Editor {
                    values[key] = value.toString()
                    return this
                }

                override fun remove(key: String): KeyValueStore.Editor {
                    values.remove(key)
                    return this
                }

                override fun clear(): KeyValueStore.Editor {
                    values.clear()
                    return this
                }
            }
            editor.block()
            return true
        }
    }
}
