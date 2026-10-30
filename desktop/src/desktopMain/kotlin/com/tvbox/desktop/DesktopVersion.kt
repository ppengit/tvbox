package com.tvbox.desktop

import java.util.Properties

/**
 * 桌面版本号由构建脚本生成到 classpath 中，侧栏与设置页只读这一份，避免多处硬编码漂移。
 */
object DesktopVersion {
    val name: String by lazy {
        runCatching {
            val stream = DesktopVersion::class.java.getResourceAsStream("/desktop-version.properties")
                ?: return@runCatching null
            stream.use { Properties().apply { load(it) }.getProperty("version") }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "开发版"
    }
}
