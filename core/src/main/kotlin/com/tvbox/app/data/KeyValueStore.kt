package com.tvbox.app.data

/**
 * 平台无关的键值持久化。
 *
 * 写入语义是同步落盘：`edit` 返回时数据已经持久化，返回值表示是否成功。调用方依赖这个
 * 结果判断是否需要报错，因此实现不能退化成"先内存再异步刷盘"。
 */
interface KeyValueStore {

    fun getString(key: String, defaultValue: String? = null): String?

    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    fun getFloat(key: String, defaultValue: Float): Float

    fun edit(block: Editor.() -> Unit): Boolean

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putFloat(key: String, value: Float): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
    }
}

/**
 * JSON 值损坏时先留一份原文，下一次保存不会把用户的旧数据彻底覆盖。
 * 反复读取同一份损坏数据时只备份一次，避免每次启动都写盘。
 */
fun KeyValueStore.backupCorruptValue(key: String, raw: String) {
    if (raw.isBlank()) return
    val backupKey = "$key.corrupt"
    if (getString(backupKey) == raw) return
    edit { putString(backupKey, raw) }
}
