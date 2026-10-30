package com.tvbox.desktop.state

import kotlin.test.Test
import kotlin.test.assertEquals

class SpeedFreshnessHintTest {

    @Test
    fun `没有结果时提示排序依据`() {
        assertEquals("测速后按延迟排序", speedFreshnessHint(false, 0L, 1_000_000L))
    }

    @Test
    fun `测速中优先显示进行中`() {
        assertEquals("测速中…", speedFreshnessHint(true, 1_000L, 2_000L))
    }

    @Test
    fun `按时间跨度显示新鲜度`() {
        val now = 10_000_000_000L
        assertEquals("刚刚测速 · 按延迟排序", speedFreshnessHint(false, now - 30_000L, now))
        assertEquals("5 分钟前测速 · 按延迟排序", speedFreshnessHint(false, now - 5 * 60_000L, now))
        assertEquals("3 小时前测速 · 按延迟排序", speedFreshnessHint(false, now - 3 * 3_600_000L, now))
        assertEquals("2 天前测速 · 按延迟排序", speedFreshnessHint(false, now - 2 * 86_400_000L, now))
    }

    @Test
    fun `系统时间回拨时不显示负数`() {
        assertEquals("刚刚测速 · 按延迟排序", speedFreshnessHint(false, 2_000_000L, 1_000_000L))
    }
}
