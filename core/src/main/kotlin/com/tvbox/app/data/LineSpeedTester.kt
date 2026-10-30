package com.tvbox.app.data

import com.tvbox.app.domain.ApiLine
import com.tvbox.app.domain.PlaySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 测速结果。速率按真实下载字节数 ÷ 耗时计算，不用 ping 或首字节延迟冒充带宽。
 */
data class SpeedResult(
    val targetId: String,
    val targetName: String,
    val ok: Boolean,
    val bytes: Long = 0L,
    val elapsedMs: Long = 0L,
    /** 首个响应包的往返耗时（毫秒），用于展示「N ms」并按延迟排序。 */
    val latencyMs: Long = 0L,
    val error: String? = null,
) {
    /** 千字节每秒。 */
    val kbps: Double
        get() = if (elapsedMs <= 0L) 0.0 else bytes / 1024.0 / (elapsedMs / 1000.0)

    val mbps: Double get() = kbps / 1024.0

    fun formatSpeed(): String = when {
        !ok -> "失败"
        kbps >= 1024 -> String.format("%.1f MB/s", mbps)
        else -> String.format("%.0f KB/s", kbps)
    }

    fun formatLatency(): String = when {
        !ok -> "失败"
        latencyMs <= 0L -> "—"
        else -> "$latencyMs ms"
    }
}

/**
 * 线路与播放源测速。
 *
 * - 线路测速：请求该线路的接口列表页，统计真实响应大小与耗时。
 * - 播放源测速：用 Range 头只抓前 [PROBE_BYTES] 字节，m3u8 则额外抓第一个分片，
 *   避免为了测速把整集视频下载下来。
 */
class LineSpeedTester(
    private val client: OkHttpClient = NetworkProxy.applyTo(
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true),
    ).build(),
) {

    suspend fun measureLines(lines: List<ApiLine>, parallel: Int = DEFAULT_PARALLEL): List<SpeedResult> = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(parallel.coerceAtLeast(1))
        coroutineScope {
            lines.map { line ->
                async { semaphore.withPermit { measureLine(line) } }
            }.awaitAll()
        }
    }

    suspend fun measureLine(line: ApiLine): SpeedResult = withContext(Dispatchers.IO) {
        val baseUrls = line.baseUrls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (baseUrls.isEmpty()) {
            return@withContext SpeedResult(line.id, line.name, ok = false, error = "线路地址为空")
        }
        var lastResult: SpeedResult? = null
        for (baseUrl in baseUrls) {
            val url = if (baseUrl.contains("?")) "$baseUrl&ac=detail&pg=1" else "$baseUrl?ac=detail&pg=1"
            val result = probe(url, line.id, line.name, maxBytes = PROBE_BYTES)
            if (result.ok) {
                logLineResult(line.name, result)
                return@withContext result
            }
            lastResult = result
        }
        val result = lastResult ?: SpeedResult(line.id, line.name, ok = false, error = "线路地址为空")
        logLineResult(line.name, result)
        result
    }

    private fun logLineResult(lineName: String, result: SpeedResult) {
        AppLog.info(
            "Speed",
            "线路 $lineName 测速 ${result.formatSpeed()}（${result.bytes} 字节 / ${result.elapsedMs}ms）" +
                (result.error?.let { " 错误：$it" } ?: ""),
        )
    }

    suspend fun measureSource(source: PlaySource, index: Int): SpeedResult = withContext(Dispatchers.IO) {
        val episodeUrl = source.episodes.firstOrNull()?.url.orEmpty()
        if (episodeUrl.isBlank()) {
            return@withContext SpeedResult("source-$index", source.name, ok = false, error = "没有可用的剧集地址")
        }
        val result = when {
            episodeUrl.contains(".m3u8", ignoreCase = true) -> probePlaylist(episodeUrl, "source-$index", source.name)
            else -> probe(episodeUrl, "source-$index", source.name, maxBytes = PROBE_BYTES)
        }
        AppLog.info(
            "Speed",
            "播放源 ${source.name}（${source.episodes.size} 集）测速 ${result.formatSpeed()}" +
                (result.error?.let { " 错误：$it" } ?: ""),
        )
        result
    }

    suspend fun measureSources(sources: List<PlaySource>, parallel: Int = DEFAULT_PARALLEL): List<SpeedResult> = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(parallel.coerceAtLeast(1))
        coroutineScope {
            sources.mapIndexed { index, source ->
                async { semaphore.withPermit { measureSource(source, index) } }
            }.awaitAll()
        }
    }

    private suspend fun probePlaylist(url: String, id: String, name: String): SpeedResult {
        val playlist = try {
            fetch(url, PROBE_BYTES)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            return SpeedResult(id, name, ok = false, error = error.message ?: "请求失败")
        }
        val firstSegment = playlist.text.lineSequence()
            .map { it.trim().removePrefix("\uFEFF") }
            .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            ?.let { segment -> resolvePlaylistUri(url, segment) }
        if (firstSegment.isNullOrBlank()) {
            return SpeedResult(
                targetId = id,
                targetName = name,
                ok = playlist.bytes > 0,
                bytes = playlist.bytes,
                elapsedMs = playlist.elapsedMs,
                latencyMs = playlist.latencyMs,
                error = if (playlist.bytes > 0) null else "分片列表为空",
            )
        }
        val segment = try {
            fetch(firstSegment, PROBE_BYTES)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            return SpeedResult(id, name, ok = false, error = error.message ?: "分片请求失败")
        }
        return SpeedResult(
            targetId = id,
            targetName = name,
            ok = true,
            bytes = playlist.bytes + segment.bytes,
            elapsedMs = playlist.elapsedMs + segment.elapsedMs,
            // 换源体验取决于「点开这条线路多久出画面」，取播放列表首包延迟而不是分片下载时间。
            latencyMs = playlist.latencyMs,
        )
    }

    /**
     * m3u8 里的分片可能是绝对地址、根路径、相对路径或协议相对地址。
     * 用 HttpUrl.resolve 统一处理，避免手工拼出 `//` 或丢路径。
     */
    private fun resolvePlaylistUri(playlistUrl: String, segment: String): String {
        if (segment.startsWith("http://", ignoreCase = true) ||
            segment.startsWith("https://", ignoreCase = true)
        ) {
            return segment
        }
        val base = playlistUrl.toHttpUrlOrNull() ?: return segment
        return runCatching { base.resolve(segment)?.toString() }.getOrNull() ?: segment
    }

    private suspend fun probe(url: String, id: String, name: String, maxBytes: Long): SpeedResult =
        try {
            val probe = fetch(url, maxBytes)
            SpeedResult(
                id, name, ok = probe.bytes > 0, bytes = probe.bytes,
                elapsedMs = probe.elapsedMs, latencyMs = probe.latencyMs,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SpeedResult(id, name, ok = false, error = error.message ?: "请求失败")
        }

    private data class Probe(val bytes: Long, val elapsedMs: Long, val latencyMs: Long, val text: String)

    private suspend fun fetch(url: String, maxBytes: Long): Probe = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-${maxBytes - 1}")
            .header("User-Agent", SPEED_TEST_UA)
            .build()
        val call = client.newCall(request)
        // execute() 是阻塞调用，协程取消不会自动断开 socket；取消时主动 cancel 请求。
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        try {
            val startedAt = System.nanoTime()
            call.execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    error("HTTP ${response.code}")
                }
                val body = response.body
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                val textBuilder = StringBuilder()
                body.byteStream().use { stream ->
                    while (total < maxBytes) {
                        currentCoroutineContext().ensureActive()
                        val remaining = (maxBytes - total).coerceAtMost(buffer.size.toLong()).toInt()
                        val read = stream.read(buffer, 0, remaining)
                        if (read <= 0) break
                        total += read
                        if (textBuilder.length < 4096) {
                            val append = minOf(read, 4096 - textBuilder.length)
                            textBuilder.append(String(buffer, 0, append, Charsets.UTF_8))
                        }
                    }
                }
                val elapsed = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(1L)
                // OkHttp 的收发时间戳覆盖 DNS + 连接 + 首包，正好是用户感知的「点开要等多久」。
                val latency = (response.receivedResponseAtMillis - response.sentRequestAtMillis)
                    .coerceIn(1L, elapsed)
                Probe(total, elapsed, latency, textBuilder.toString())
            }
        } finally {
            cancellationHandle?.dispose()
        }
    }

    private companion object {
        const val DEFAULT_PARALLEL = 4
        const val PROBE_BYTES = 1_500_000L
        const val SPEED_TEST_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
