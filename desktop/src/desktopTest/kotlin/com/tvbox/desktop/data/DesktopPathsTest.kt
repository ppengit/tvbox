package com.tvbox.desktop.data

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopPathsTest {
    @Test
    fun `安装目录带 portable flag 时使用旁边的 data 目录`() {
        val dir = Files.createTempDirectory("xj-portable").createDirectories()
        dir.resolve("portable.flag").createFile()
        val launcher = dir.resolve("XiaoJi.exe").createFile()
        val other = Files.createTempDirectory("xj-cwd")

        assertEquals(dir.resolve("data"), DesktopPaths.resolvePortableRoot(launcher, other))
    }

    @Test
    fun `没有 launcher 路径时按工作目录识别便携模式`() {
        val dir = Files.createTempDirectory("xj-cwd-portable")
        dir.resolve("portable.flag").createFile()

        assertEquals(dir.resolve("data"), DesktopPaths.resolvePortableRoot(null, dir))
    }

    @Test
    fun `没有 flag 时按普通模式处理`() {
        val dir = Files.createTempDirectory("xj-normal")

        assertNull(DesktopPaths.resolvePortableRoot(dir.resolve("XiaoJi.exe"), dir))
    }
}
