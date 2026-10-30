package com.tvbox.desktop.data

import com.tvbox.app.data.KeyValueStore
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * 桌面端持久化实现：每个 [KeyValueStore] 对应一个 properties 文件。
 *
 * 写入是同步且原子的，和 [KeyValueStore.edit] 的契约一致。读取会把文件缓存到内存，
 * 写入后立即更新缓存，避免频繁磁盘 IO 影响 UI 线程。
 */
class FileKeyValueStore(file: Path) : KeyValueStore {

    private val file: Path = file
    private val cache = ConcurrentHashMap<String, String>()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        if (!Files.exists(file)) return
        runCatching {
            val props = Properties()
            // 写入端固定 UTF-8，读取也必须是 UTF-8；用 InputStream 会按 ISO-8859-1 解码，
            // 中文会变成乱码。下面同时对历史遗留的乱码做一次尽力修复。
            Files.newBufferedReader(file, StandardCharsets.UTF_8).use(props::load)
            props.forEach { key, value -> cache[key.toString()] = repairMojibake(value.toString()) }
        }
    }

    override fun getString(key: String, defaultValue: String?): String? =
        cache[key] ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        cache[key]?.toBooleanStrictOrNull() ?: defaultValue

    override fun getFloat(key: String, defaultValue: Float): Float =
        cache[key]?.toFloatOrNull() ?: defaultValue

    override fun edit(block: KeyValueStore.Editor.() -> Unit): Boolean {
        val pending = LinkedHashMap<String, String?>(cache)
        val editor = Editor(pending)
        editor.block()
        return runCatching {
            val props = Properties()
            pending.forEach { (key, value) -> if (value != null) props[key] = value }
            val temp = Files.createTempFile(file.parent, file.fileName.toString(), ".tmp")
            Files.newBufferedWriter(temp, StandardCharsets.UTF_8).use { writer -> props.store(writer, null) }
            Files.move(
                temp,
                file,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
            cache.clear()
            pending.forEach { (key, value) -> if (value != null) cache[key] = value }
            true
        }.getOrDefault(false)
    }

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

    /**
     * 早期版本用默认字符集读写，导致中文被 UTF-8 写入、GBK/ISO-8859-1 读出的双重乱码。
     * 这里尝试把乱码串还原回原始字节再按 UTF-8 解码；只有解出的中文更多时才采用，
     * 正常中文串无法通过这一步（其 GBK 字节不是合法 UTF-8），因此不会被误改。
     */
    private fun repairMojibake(value: String): String {
        if (value.all { it.code < 0x80 }) return value
        val originalCjk = value.count { it in '\u4e00'..'\u9fff' }
        for (charsetName in listOf("GBK", "ISO-8859-1")) {
            val bytes = runCatching { value.toByteArray(charset(charsetName)) }.getOrNull() ?: continue
            val candidate = runCatching { String(bytes, StandardCharsets.UTF_8) }.getOrNull() ?: continue
            if (candidate.contains('\uFFFD') || candidate == value) continue
            val candidateCjk = candidate.count { it in '\u4e00'..'\u9fff' }
            if (candidateCjk > originalCjk) return candidate
        }
        return value
    }
}
