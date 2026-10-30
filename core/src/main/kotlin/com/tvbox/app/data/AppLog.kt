package com.tvbox.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class LogLevel(val label: String, val weight: Int) {
    Info("信息", 0),
    Warn("警告", 1),
    Error("错误", 2),
}

data class LogEntry(
    val timeMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
)

/**
 * 结构化日志：按级别和分类记录测速、切源、播放错误等事件。
 *
 * 桌面端把它接到设置页的「日志」分组：可按级别/时间段过滤、导出为文本、清空。
 * 内存里只保留最近 [MAX_ENTRIES] 条，避免长时间运行后无限增长。
 */
object AppLog {

    private const val MAX_ENTRIES = 1500

    private val lock = Any()
    private val entries = ArrayDeque<LogEntry>(MAX_ENTRIES)
    private val _snapshot = MutableStateFlow<List<LogEntry>>(emptyList())

    val snapshot: StateFlow<List<LogEntry>> = _snapshot.asStateFlow()

    fun info(tag: String, message: String) = append(LogLevel.Info, tag, message)

    fun warn(tag: String, message: String) = append(LogLevel.Warn, tag, message)

    fun error(tag: String, message: String) = append(LogLevel.Error, tag, message)

    fun clear() {
        synchronized(lock) {
            entries.clear()
            _snapshot.value = emptyList()
        }
    }

    fun entriesSince(sinceMs: Long, minLevel: LogLevel = LogLevel.Info): List<LogEntry> {
        val all = synchronized(lock) { entries.toList() }
        return all.filter { it.timeMs >= sinceMs && it.level.weight >= minLevel.weight }
    }

    /** 导出为纯文本日志，返回写入的行数。 */
    fun export(file: File, sinceMs: Long = 0L, minLevel: LogLevel = LogLevel.Info): Int {
        val rows = entriesSince(sinceMs, minLevel)
        file.parentFile?.mkdirs()
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            rows.forEach { entry ->
                writer.appendLine(
                    "${formatTime(entry.timeMs)} [${entry.level.label}] ${entry.tag} - ${entry.message}",
                )
            }
        }
        return rows.size
    }

    private fun append(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
            // 复制一份给 UI；日志量不大，避免额外依赖。
            _snapshot.value = entries.toList()
        }
    }

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    fun formatTime(timeMs: Long): String = timeFormatter.format(Instant.ofEpochMilli(timeMs))
}
