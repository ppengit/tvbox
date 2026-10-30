package com.tvbox.desktop.data

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileKeyValueStoreTest {

    @Test
    fun `中文与空值往返保持正确`() {
        val file = Files.createTempDirectory("kv-roundtrip").resolve("store.properties")
        val store = FileKeyValueStore(file)
        assertTrue(store.edit {
            putString("name", "庆余年")
            putString("empty", "")
            putString("removed", "x")
            remove("removed")
        })

        val reloaded = FileKeyValueStore(file)
        assertEquals("庆余年", reloaded.getString("name", null))
        assertEquals("", reloaded.getString("empty", null))
        assertNull(reloaded.getString("removed", null))
    }

    @Test
    fun `两个实例写同一个文件时不会互相覆盖`() {
        val file = Files.createTempDirectory("kv-two-instances").resolve("desktop_settings.properties")
        val windowStore = FileKeyValueStore(file)
        assertTrue(windowStore.edit { putString("window_x", "10") })

        // 模拟 AppContainer 在 Main 之后另开一个实例写平台直播地址。
        val settingsStore = FileKeyValueStore(file)
        assertTrue(settingsStore.edit { putString("platform_live_service_url", "https://live.test") })

        // 旧实例再次写入时必须先合并磁盘内容，而不是拿启动时的旧缓存整片覆盖。
        assertTrue(windowStore.edit { putString("window_y", "20") })

        val reloaded = FileKeyValueStore(file)
        assertEquals("10", reloaded.getString("window_x", null))
        assertEquals("20", reloaded.getString("window_y", null))
        assertEquals("https://live.test", reloaded.getString("platform_live_service_url", null))
        assertEquals("https://live.test", windowStore.getString("platform_live_service_url", null))
    }

    @Test
    fun `并发写入不会丢键`() {
        val file = Files.createTempDirectory("kv-concurrent").resolve("store.properties")
        val store = FileKeyValueStore(file)
        val threads = 6
        val perThread = 20
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        try {
            val futures = (0 until threads).map { thread ->
                pool.submit {
                    start.await()
                    repeat(perThread) { index ->
                        assertTrue(store.edit { putString("k-$thread-$index", "v-$index") })
                    }
                }
            }
            start.countDown()
            futures.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        val reloaded = FileKeyValueStore(file)
        repeat(threads) { thread ->
            repeat(perThread) { index ->
                assertEquals("v-$index", reloaded.getString("k-$thread-$index", null))
            }
        }
    }

    @Test
    fun `损坏文件会先备份再重建`() {
        val dir = Files.createTempDirectory("kv-corrupt")
        val file = dir.resolve("store.properties")
        // 非法 \u 转义会让 Properties.load 抛异常，模拟损坏文件。
        Files.writeString(file, "key=\\uZZZZ\n", StandardCharsets.UTF_8)

        val store = FileKeyValueStore(file)
        assertNull(store.getString("key", null))
        assertTrue(Files.exists(dir.resolve("store.properties.corrupt")))

        assertTrue(store.edit { putString("ok", "1") })
        assertEquals("1", FileKeyValueStore(file).getString("ok", null))
    }

    @Test
    fun `旧版 GBK 文件仍能读出中文`() {
        val file = Files.createTempDirectory("kv-gbk").resolve("store.properties")
        Files.write(file, "name=中文\n".toByteArray(Charset.forName("GBK")))

        val store = FileKeyValueStore(file)
        assertEquals("中文", store.getString("name", null))
    }

    @Test
    fun `目录不存在时自动创建且失败写入返回 false`() {
        val dir = Files.createTempDirectory("kv-missing-parent")
        val nested = dir.resolve("nested").resolve("deeper").resolve("store.properties")
        assertTrue(FileKeyValueStore(nested).edit { putString("a", "b") })
        assertEquals("b", FileKeyValueStore(nested).getString("a", null))

        val blocker = dir.resolve("blocked")
        Files.writeString(blocker, "not a directory")
        val blockedStore = FileKeyValueStore(blocker.resolve("store.properties"))
        assertFalse(blockedStore.edit { putString("a", "b") })
    }

    @Test
    fun `clear 清空所有键并持久化`() {
        val file = Files.createTempDirectory("kv-clear").resolve("store.properties")
        val store = FileKeyValueStore(file)
        store.edit {
            putString("a", "1")
            putBoolean("b", true)
        }
        assertTrue(store.edit { clear() })

        val reloaded = FileKeyValueStore(file)
        assertNull(reloaded.getString("a", null))
        assertFalse(reloaded.getBoolean("b", false))
    }
}
