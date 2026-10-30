package com.tvbox.app.data

import java.util.concurrent.ConcurrentHashMap

/** 线程安全的内存 KeyValueStore，供数据层单测使用。 */
internal class InMemoryKeyValueStore(
    initial: Map<String, String> = emptyMap(),
    @Volatile var editSucceeds: Boolean = true,
) : KeyValueStore {

    private val values = ConcurrentHashMap<String, String>(initial)

    override fun getString(key: String, defaultValue: String?): String? =
        values[key] ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        values[key]?.toBooleanStrictOrNull() ?: defaultValue

    override fun getFloat(key: String, defaultValue: Float): Float =
        values[key]?.toFloatOrNull() ?: defaultValue

    override fun edit(block: KeyValueStore.Editor.() -> Unit): Boolean = synchronized(values) {
        if (!editSucceeds) return false
        val pending = LinkedHashMap<String, String?>(values)
        Editor(pending).block()
        values.clear()
        pending.forEach { (key, value) -> if (value != null) values[key] = value }
        true
    }

    fun snapshot(): Map<String, String> = values.toMap()

    private class Editor(private val pending: MutableMap<String, String?>) : KeyValueStore.Editor {
        override fun putString(key: String, value: String?): KeyValueStore.Editor = apply {
            if (value == null) pending.remove(key) else pending[key] = value
        }

        override fun putBoolean(key: String, value: Boolean): KeyValueStore.Editor = apply {
            pending[key] = value.toString()
        }

        override fun putFloat(key: String, value: Float): KeyValueStore.Editor = apply {
            pending[key] = value.toString()
        }

        override fun remove(key: String): KeyValueStore.Editor = apply {
            pending.remove(key)
        }

        override fun clear(): KeyValueStore.Editor = apply {
            pending.clear()
        }
    }
}
