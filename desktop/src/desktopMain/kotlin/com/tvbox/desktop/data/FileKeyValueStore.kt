package com.tvbox.desktop.data

import com.tvbox.app.data.KeyValueStore
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * 桌面端持久化实现：每个 [KeyValueStore] 对应一个 properties 文件。
 *
 * 写入是同步且原子的，和 [KeyValueStore.edit] 的契约一致。读取会把文件缓存到内存，
 * 写入后立即更新缓存，避免频繁磁盘 IO 影响 UI 线程。
 *
 * 同一个文件可能被多个实例打开（例如启动阶段的窗口几何与设置页共用 desktop_settings）。
 * 每次写入前都会重新读取磁盘并合并，再用文件级的锁串行化，避免某个旧实例把另一个实例
 * 刚写入的键整片覆盖掉。
 */
class FileKeyValueStore(file: Path) : KeyValueStore {

    private val file: Path = file.toAbsolutePath().normalize()
    // 整体替换而不是逐键修改：读线程只会看到替换前或替换后的完整快照，不会读到半空缓存。
    @Volatile
    private var cache: Map<String, String> = emptyMap()
    private val lock: Any = fileLocks.computeIfAbsent(
        file.toAbsolutePath().normalize().toString(),
    ) { Any() }

    init {
        synchronized(lock) { loadFromDisk() }
    }

    private fun loadFromDisk() {
        cache = readDiskProperties() ?: emptyMap()
    }

    override fun getString(key: String, defaultValue: String?): String? =
        cache[key] ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        cache[key]?.toBooleanStrictOrNull() ?: defaultValue

    override fun getFloat(key: String, defaultValue: Float): Float =
        cache[key]?.toFloatOrNull() ?: defaultValue

    override fun edit(block: KeyValueStore.Editor.() -> Unit): Boolean = synchronized(lock) {
        // 以磁盘内容为准重新合并：其他实例（或上一进程）写入的键不能因为本实例的
        // 旧缓存而丢失。Editor 没有读接口，调用方只能写入，因此不会依赖旧缓存值。
        val pending = LinkedHashMap<String, String?>(readDiskProperties() ?: return@synchronized false)
        val editor = Editor(pending)
        editor.block()
        val persisted: Map<String, String> = pending.entries
            .mapNotNull { (key, value) -> value?.let { key to it } }
            .toMap()
        runCatching {
            writeAtomically(persisted)
            cache = persisted
            true
        }.getOrDefault(false)
    }

    private fun writeAtomically(values: Map<String, String>) {
        val parent = file.parent ?: Path.of(".").toAbsolutePath()
        Files.createDirectories(parent)
        val props = Properties()
        values.forEach { (key, value) -> props[key] = value }
        val temp = Files.createTempFile(parent, "store", ".tmp")
        try {
            Files.newBufferedWriter(temp, StandardCharsets.UTF_8).use { writer ->
                props.store(writer, null)
            }
            try {
                Files.move(
                    temp,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                // 网络盘 / FAT 等文件系统不支持原子替换时退化为普通替换，
                // 仍然比直接截断写原文件安全。
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            runCatching { Files.deleteIfExists(temp) }
        }
    }

    /**
     * 读取并解析磁盘文件。返回 null 表示文件存在但暂时读不出来（例如被独占），
     * 此时调用方不应拿空数据覆盖它。
     */
    private fun readDiskProperties(): Map<String, String>? {
        if (!Files.exists(file)) return emptyMap()
        val bytes = runCatching { Files.readAllBytes(file) }.getOrNull() ?: return null
        if (bytes.isEmpty()) return emptyMap()
        val text = decodeStrict(bytes) ?: run {
            backupCorruptFile()
            return emptyMap()
        }
        val props = Properties()
        val loaded = runCatching { props.load(StringReader(text)) }.isSuccess
        if (!loaded) {
            backupCorruptFile()
            return emptyMap()
        }
        return props.entries.associate { (key, value) ->
            key.toString() to repairMojibake(value.toString())
        }
    }

    /**
     * 先按 UTF-8 严格解码；历史版本在中文 Windows 上可能写过 GBK，再退回 GBK，
     * 最后用 ISO-8859-1 兜底。全部失败才认为是损坏文件。
     */
    private fun decodeStrict(bytes: ByteArray): String? {
        for (charset in listOf(StandardCharsets.UTF_8, Charset.forName("GBK"), StandardCharsets.ISO_8859_1)) {
            val decoded = runCatching {
                charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            }.getOrNull() ?: continue
            return decoded.removePrefix("\uFEFF")
        }
        return null
    }

    /** 损坏文件先留一份备份再继续，避免下一次写入把用户数据彻底覆盖。 */
    private fun backupCorruptFile() {
        runCatching {
            val backup = file.resolveSibling(file.fileName.toString() + ".corrupt")
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING)
        }
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

    private companion object {
        /** 同一文件的所有实例共享一把锁；路径在构造时已归一化为绝对路径。 */
        val fileLocks = ConcurrentHashMap<String, Any>()
    }
}

/**
 * JSON 值损坏时先留一份原文，下一次保存不会把用户的旧数据彻底覆盖。
 * 反复读取同一份损坏数据时只备份一次，避免每次启动都写盘。
 */
internal fun KeyValueStore.backupCorruptValue(key: String, raw: String) {
    if (raw.isBlank()) return
    val backupKey = "$key.corrupt"
    if (getString(backupKey) == raw) return
    edit { putString(backupKey, raw) }
}
