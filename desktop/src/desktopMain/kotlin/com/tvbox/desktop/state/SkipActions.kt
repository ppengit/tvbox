package com.tvbox.desktop.state

import com.tvbox.app.domain.Movie
import com.tvbox.desktop.data.SkipMarks

/**
 * 片头片尾标记。同一部剧的所有集共用一组，按剧（而非按集）存储；
 * 标记只在播放页记录，因为只有那里能拿到真实播放进度。
 */
fun DesktopAppState.loadSkipMarks() {
    val items = runCatching { skipMarksRepository.load() }.getOrDefault(emptyList())
    updateSkipMarks { items.associateBy { it.key } }
}

fun DesktopAppState.skipMarksFor(movie: Movie?): SkipMarks? {
    if (movie == null) return null
    return skipMarks.value["${movie.apiLineId}:${movie.id}"]
}

private fun DesktopAppState.persistSkipMarks(marks: SkipMarks, message: String) {
    runCatching { skipMarksRepository.save(marks) }
    updateSkipMarks { it + (marks.key to marks) }
    notify(message)
}

fun DesktopAppState.setIntroEnd(movie: Movie, positionMs: Long) {
    val current = skipMarksFor(movie) ?: SkipMarks(movie.apiLineId, movie.id)
    val introEnd = positionMs.coerceAtLeast(0L)
    if (current.hasOutro && introEnd >= current.outroStartMs) {
        notify("片头不能晚于片尾标记")
        return
    }
    persistSkipMarks(current.copy(introEndMs = introEnd), "片头结束已记为 ${formatSkipTime(introEnd)}")
}

fun DesktopAppState.setOutroStart(movie: Movie, positionMs: Long) {
    val current = skipMarksFor(movie) ?: SkipMarks(movie.apiLineId, movie.id)
    val outroStart = positionMs.coerceAtLeast(0L)
    if (current.hasIntro && outroStart <= current.introEndMs) {
        notify("片尾必须晚于片头标记")
        return
    }
    persistSkipMarks(current.copy(outroStartMs = outroStart), "片尾开始已记为 ${formatSkipTime(outroStart)}")
}

fun DesktopAppState.clearSkipMarks(movie: Movie) {
    runCatching { skipMarksRepository.remove(movie.apiLineId, movie.id) }
    updateSkipMarks { it - "${movie.apiLineId}:${movie.id}" }
    notify("已清除本剧的片头片尾标记")
}

fun formatSkipTime(ms: Long): String {
    if (ms <= 0L) return "未设置"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}
