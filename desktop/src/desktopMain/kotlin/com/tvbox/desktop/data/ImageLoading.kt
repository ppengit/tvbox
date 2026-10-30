package com.tvbox.desktop.data

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.tvbox.app.data.DOUBAN_REFERER
import com.tvbox.app.data.NetworkProxy
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.Path.Companion.toOkioPath

/**
 * 豆瓣图床对缺少 Referer 的请求直接返回 418，桌面端没有 WebView 兜底，必须在图片请求上补齐。
 * 图片磁盘缓存放在应用自己的 cache 目录，避免每次启动重新下载，也不污染系统临时目录。
 */
object DesktopImages {
    @Volatile
    private var loader: ImageLoader? = null

    fun remember(created: ImageLoader): ImageLoader {
        loader = created
        return created
    }

    fun cacheSizeBytes(): Long = loader?.diskCache?.size ?: 0L

    fun clearCaches() {
        runCatching {
            loader?.memoryCache?.clear()
            loader?.diskCache?.clear()
        }
    }
}

fun createDesktopImageLoader(
    context: PlatformContext,
    maxDiskBytes: Long = IMAGE_DISK_CACHE_BYTES,
): ImageLoader =
    DesktopImages.remember(ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { DesktopImageHttpClient }))
        }
        .diskCache {
            DiskCache.Builder()
                .directory(DesktopPaths.cache.resolve("images").toOkioPath())
                // 设置文件可能被手工改成 0/负数；Coil 要求缓存上限为正数，否则启动即崩。
                .maxSizeBytes(maxDiskBytes.coerceAtLeast(1L))
                .build()
        }
        .build())

private val DesktopImageHttpClient: OkHttpClient by lazy {
    // 图片和接口走同一套代理策略，否则用户设置代理后会出现「能搜到片、海报全裂」。
    NetworkProxy.applyTo(OkHttpClient.Builder())
        .addInterceptor(DoubanImageInterceptor)
        .build()
}

private object DoubanImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.host.endsWith("doubanio.com", ignoreCase = true)) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder()
                .header("Referer", DOUBAN_REFERER)
                .header("User-Agent", DESKTOP_BROWSER_UA)
                .build(),
        )
    }
}

private const val DESKTOP_BROWSER_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private const val IMAGE_DISK_CACHE_BYTES = 256L * 1024 * 1024
