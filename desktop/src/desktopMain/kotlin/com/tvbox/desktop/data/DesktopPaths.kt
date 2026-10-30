package com.tvbox.desktop.data

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * 桌面端数据目录。所有可写数据都放在用户目录下，不依赖安装路径（Program Files 通常只读）。
 */
object DesktopPaths {

    val root: Path by lazy {
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            ?: Path.of(System.getProperty("user.home"), "AppData", "Roaming")
        base.resolve("XiaoJiYingShi").also { Files.createDirectories(it) }
    }

    val stores: Path by lazy {
        root.resolve("stores").also { Files.createDirectories(it) }
    }

    val cache: Path by lazy {
        root.resolve("cache").also { Files.createDirectories(it) }
    }

    val logs: Path by lazy {
        root.resolve("logs").also { Files.createDirectories(it) }
    }

    fun storeFile(name: String): Path = stores.resolve("$name.properties")

    /**
     * 先写临时文件再原子替换，避免进程中途退出留下半截 JSON。
     */
    fun writeAtomically(target: Path, content: String) {
        val temp = Files.createTempFile(target.parent, "store", ".tmp")
        try {
            Files.writeString(temp, content)
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (error: Exception) {
            runCatching { Files.deleteIfExists(temp) }
            throw error
        }
    }
}
