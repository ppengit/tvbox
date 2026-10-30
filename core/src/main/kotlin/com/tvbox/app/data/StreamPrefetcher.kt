package com.tvbox.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 起播预热：对后续剧集的地址做一次极小的 HEAD/GET 请求，提前完成 DNS 解析与 TLS 握手。
 *
 * 资源站的播放地址在详情接口里就已经给全了，真正拖慢切集的是 CDN 首次连接和
 * 重定向，所以这里只做连接预热，不下载视频数据。
 */
class StreamPrefetcher(
    private val client: OkHttpClient = NetworkProxy.applyTo(
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true),
    ).build(),
) {

    suspend fun warmUp(urls: List<String>) {
        if (urls.isEmpty()) return
        withContext(Dispatchers.IO) {
            coroutineScope {
                urls.distinct().take(MAX_URLS).map { url ->
                    async {
                        runCatching {
                            val request = Request.Builder()
                                .url(url)
                                .head()
                                .header("User-Agent", USER_AGENT)
                                .build()
                            client.newCall(request).execute().use { response -> response.code }
                        }.getOrNull()
                    }
                }.awaitAll()
            }
        }
        AppLog.info("Prefetch", "预热 ${urls.distinct().take(MAX_URLS).size} 个播放地址")
    }

    private companion object {
        const val MAX_URLS = 4
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
