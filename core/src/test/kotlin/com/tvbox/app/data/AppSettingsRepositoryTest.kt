package com.tvbox.app.data

import com.tvbox.app.domain.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AppSettingsRepositoryTest {

    @Test
    fun saveFailureIsReportedInsteadOfSilentlyReturningSettings() = runBlocking {
        val store = InMemoryKeyValueStore(editSucceeds = false)
        val repository = PersistentAppSettingsRepository(store)

        try {
            repository.saveSettings(AppSettings(aiApiKey = "secret"))
            fail("保存失败时必须抛错，避免界面提示“已保存”")
        } catch (expected: IllegalStateException) {
            assertEquals("设置保存失败", expected.message)
        }
    }

    @Test
    fun savedSettingsRoundTrip() = runBlocking {
        val store = InMemoryKeyValueStore()
        val repository = PersistentAppSettingsRepository(store)
        val settings = AppSettings(
            homeApiLineId = "custom",
            aiApiKey = "key-123",
            proxyHost = "127.0.0.1",
            proxyPort = 7891,
            imageCacheMaxMb = 512,
        )

        repository.saveSettings(settings)

        val loaded = repository.getSettings()
        assertEquals("custom", loaded.homeApiLineId)
        assertEquals("key-123", loaded.aiApiKey)
        assertEquals("127.0.0.1", loaded.proxyHost)
        assertEquals(7891, loaded.proxyPort)
        assertEquals(512, loaded.imageCacheMaxMb)
    }
}
