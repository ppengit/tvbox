package com.tvbox.app.data

import com.tvbox.app.domain.ProxyMode
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * 应用级出网策略。
 *
 * - [Direct] 完全不走任何 HTTP 代理：即使 Windows 设置了系统代理，请求也直接发出去。
 * - [System] 跟随 Windows 系统代理（Clash 的「系统代理」模式会写这里）。
 * - [Custom] 走用户填写的 HTTP 代理地址。
 *
 * 注意：Clash 的 TUN 模式在内核层接管流量，应用层无法绕过；这三种模式只决定
 * 「是否再套一层 HTTP 代理」，不改变 TUN 的接管结果。
 */
/**
 * 全局出网配置。所有仓库的 OkHttpClient 都通过 [applyTo] 挂上同一个动态选择器，
 * 因此设置页切换代理后无需重启应用，下一次请求就会按新策略走。
 */
object NetworkProxy {

    @Volatile
    var mode: ProxyMode = ProxyMode.System
        private set

    @Volatile
    private var customProxy: Proxy? = null

    private val systemSelector: ProxySelector? by lazy { ProxySelector.getDefault() }

    private val dynamicSelector = object : ProxySelector() {
        override fun select(uri: URI): List<Proxy> = when (mode) {
            ProxyMode.Direct -> listOf(Proxy.NO_PROXY)
            ProxyMode.Custom -> listOf(customProxy ?: Proxy.NO_PROXY)
            ProxyMode.System -> runCatching {
                systemSelector?.select(uri)?.takeIf { it.isNotEmpty() }
            }.getOrNull() ?: listOf(Proxy.NO_PROXY)
        }

        override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {
            runCatching { systemSelector?.connectFailed(uri, sa, ioe) }
        }
    }

    fun applyTo(builder: OkHttpClient.Builder): OkHttpClient.Builder =
        builder.proxySelector(dynamicSelector)

    /**
     * 更新出网策略。自定义模式下地址不合法时回退为直连，避免请求全部超时。
     */
    fun configure(mode: ProxyMode, host: String = "", port: Int = 0) {
        val normalizedHost = host.trim()
        customProxy = if (mode == ProxyMode.Custom && normalizedHost.isNotBlank() && port in 1..65535) {
            Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(normalizedHost, port))
        } else {
            null
        }
        this.mode = if (mode == ProxyMode.Custom && customProxy == null) ProxyMode.Direct else mode
    }

    fun configureFromSettings(settings: com.tvbox.app.domain.AppSettings) {
        configure(
            mode = ProxyMode.fromStorageKey(settings.proxyMode),
            host = settings.proxyHost,
            port = settings.proxyPort,
        )
    }

    /**
     * 让 JVM 的系统代理选择器读取 Windows 的代理设置。必须在首次发起网络请求前调用。
     */
    fun enableSystemProxyLookup() {
        System.setProperty("java.net.useSystemProxies", "true")
    }
}
