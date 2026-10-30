package com.tvbox.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateManifestTest {

    @Test
    fun newerVersionWithDownloadUrlIsReturned() {
        val update = parseAppUpdateManifest(
            raw = """
                {
                  "versionCode": 12,
                  "versionName": " 1.4.0 ",
                  "apkUrl": " https://example.test/app.apk ",
                  "apkSize": -1,
                  "changelog": [" 修复问题 ", "  "]
                }
            """.trimIndent(),
            currentVersionCode = 11,
        )!!

        assertEquals(12, update.versionCode)
        assertEquals("1.4.0", update.versionName)
        assertEquals("https://example.test/app.apk", update.apkUrl)
        assertEquals(0L, update.apkSize)
        assertEquals(listOf("修复问题"), update.changelog)
    }

    @Test
    fun sameOrOlderVersionDoesNotPrompt() {
        val raw = """{"versionCode": 11, "versionName": "1.3.0", "apkUrl": "https://example.test/app.apk"}"""
        assertNull(parseAppUpdateManifest(raw, currentVersionCode = 11))
        assertNull(parseAppUpdateManifest(raw, currentVersionCode = 12))
    }

    @Test
    fun manifestWithoutUsableDownloadUrlIsIgnored() {
        val noUrl = """{"versionCode": 12, "versionName": "1.4.0", "apkUrl": ""}"""
        val relativeUrl = """{"versionCode": 12, "versionName": "1.4.0", "apkUrl": "/download/app.apk"}"""

        assertNull(parseAppUpdateManifest(noUrl, currentVersionCode = 11))
        assertNull(parseAppUpdateManifest(relativeUrl, currentVersionCode = 11))
    }
}
