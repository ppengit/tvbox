package com.tvbox.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 把 Android 的 SharedPreferences 适配成跨平台 [KeyValueStore]。
 *
 * Android 端必须继续使用 SharedPreferences：已有用户数据存在这里，换成文件存储会丢历史。
 * 写操作使用 commit 而不是 apply，因为 [KeyValueStore.edit] 的返回值代表落盘结果。
 */
class PreferenceKeyValueStore(
    context: Context,
    name: String,
) : KeyValueStore {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String, defaultValue: String?): String? =
        preferences.getString(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        preferences.getBoolean(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Float =
        preferences.getFloat(key, defaultValue)

    override fun edit(block: KeyValueStore.Editor.() -> Unit): Boolean {
        val editor = Editor(preferences.edit())
        editor.block()
        return editor.commit()
    }

    private class Editor(private val delegate: SharedPreferences.Editor) : KeyValueStore.Editor {
        override fun putString(key: String, value: String?): KeyValueStore.Editor = apply {
            delegate.putString(key, value)
        }

        override fun putBoolean(key: String, value: Boolean): KeyValueStore.Editor = apply {
            delegate.putBoolean(key, value)
        }

        override fun putFloat(key: String, value: Float): KeyValueStore.Editor = apply {
            delegate.putFloat(key, value)
        }

        override fun remove(key: String): KeyValueStore.Editor = apply {
            delegate.remove(key)
        }

        override fun clear(): KeyValueStore.Editor = apply {
            delegate.clear()
        }

        fun commit(): Boolean = delegate.commit()
    }
}

internal object AppStorageNames {
    const val SETTINGS = "app_settings"
    const val HISTORY = "watch_history"
    const val PLAYBACK_HEALTH = "playback_health"
    const val PLATFORM_LIVE_FAVORITES = "platform_live_favorites"
    const val DOUBAN_HOT_CACHE = "douban_hot_cache"
    const val SPEED_CACHE = "speed_cache"
    const val SKIP_MARKS = "skip_marks"
}
