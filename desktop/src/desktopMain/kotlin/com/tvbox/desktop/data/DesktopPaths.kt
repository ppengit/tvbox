package com.tvbox.desktop.data

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * 桌面端数据目录。默认放在用户目录下，不依赖安装路径（Program Files 通常只读）。
 * 可执行文件同目录存在 `portable.flag` 时改用旁边的 `data` 目录，方便整包拷走的便携版。
 */
object DesktopPaths {

    /**
     * jpackage 启动器会写入 `jpackage.app-path`，优先用它定位安装目录；直接走开发运行时
     * 没有该属性时退回工作目录。两处都找不到 `portable.flag` 就按普通模式使用用户目录。
     */
    internal fun resolvePortableRoot(launcherPath: Path?, workingDir: Path): Path? {
        val flag = sequenceOf(
            launcherPath?.parent?.resolve("portable.flag"),
            workingDir.toAbsolutePath().resolve("portable.flag"),
        ).filterNotNull().firstOrNull { Files.isRegularFile(it) } ?: return null
        return flag.parent.resolve("data")
    }

    private val portableRoot: Path? by lazy {
        runCatching {
            resolvePortableRoot(
                launcherPath = System.getProperty("jpackage.app-path")?.let { Path.of(it) },
                workingDir = Path.of(""),
            )
        }.getOrNull()
    }

    val root: Path by lazy {
        (portableRoot ?: run {
            val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
                ?: Path.of(System.getProperty("user.home"), "AppData", "Roaming")
            base.resolve("XiaoJiYingShi")
        }).also { Files.createDirectories(it) }
    }

    val isPortable: Boolean get() = portableRoot != null

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
