package com.tvbox.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.setSingletonImageLoaderFactory
import com.tvbox.app.data.KeyValueStore
import com.tvbox.desktop.data.AppContainer
import com.tvbox.desktop.data.DesktopPaths
import com.tvbox.desktop.data.FileKeyValueStore
import com.tvbox.desktop.data.createDesktopImageLoader
import com.tvbox.desktop.state.DesktopAppState
import com.tvbox.desktop.ui.shell.WindowTitleBar
import java.awt.Dimension
import java.awt.GraphicsConfiguration
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window as AwtWindow

fun main() {
    val container = AppContainer()
    val appState = DesktopAppState(container)
    // 图片缓存上限跟随设置，必须在 ImageLoader 创建前读出来。
    val storedSettings = FileKeyValueStore(DesktopPaths.storeFile("app_settings"))
    val imageCacheBytes = (storedSettings.getString("image_cache_max_mb")?.toIntOrNull() ?: 256) * 1024L * 1024L
    val windowStore = FileKeyValueStore(DesktopPaths.storeFile("desktop_settings"))

    application {
        setSingletonImageLoaderFactory { context -> createDesktopImageLoader(context, imageCacheBytes) }

        val initialWidth = windowStore.getString(KEY_WINDOW_WIDTH)?.toIntOrNull()
            ?.coerceIn(MIN_WIDTH, MAX_WIDTH) ?: DEFAULT_WIDTH
        val initialHeight = windowStore.getString(KEY_WINDOW_HEIGHT)?.toIntOrNull()
            ?.coerceIn(MIN_HEIGHT, MAX_HEIGHT) ?: DEFAULT_HEIGHT
        val startMaximized = windowStore.getBoolean(KEY_WINDOW_MAXIMIZED, false)

        val savedX = windowStore.getString(KEY_WINDOW_X)?.toIntOrNull()
        val savedY = windowStore.getString(KEY_WINDOW_Y)?.toIntOrNull()
        val windowState = rememberWindowState(
            width = initialWidth.dp,
            height = initialHeight.dp,
            position = if (savedX != null && savedY != null) {
                WindowPosition(savedX.dp, savedY.dp)
            } else {
                WindowPosition.Aligned(Alignment.Center)
            },
            placement = WindowPlacement.Floating,
        )
        var isFullscreen by remember { mutableStateOf(false) }
        var hostWindow by remember { mutableStateOf<AwtWindow?>(null) }
        // 无框窗口交给系统处理最大化会盖住任务栏，所以最大化/全屏改成自己管理工作区矩形，
        // 同时把「普通状态下的位置和尺寸」单独记住，还原时才能回到用户原来的窗口。
        var maximized by remember { mutableStateOf(false) }
        var normalBounds by remember { mutableStateOf<java.awt.Rectangle?>(null) }
        var maximizedBeforeFullscreen by remember { mutableStateOf(false) }

        fun configurationAt(point: Point?, window: AwtWindow): GraphicsConfiguration {
            val configurations = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .screenDevices
                .flatMap { it.configurations.asIterable() }
            return point?.let { target -> configurations.firstOrNull { it.bounds.contains(target) } }
                ?: window.graphicsConfiguration
        }

        fun workArea(configuration: GraphicsConfiguration): Rectangle = workArea(
            configuration.bounds,
            Toolkit.getDefaultToolkit().getScreenInsets(configuration),
        )

        fun currentWorkArea(window: AwtWindow): Rectangle = workArea(window.graphicsConfiguration)

        fun defaultNormalBounds(window: AwtWindow): Rectangle {
            val savedX = windowStore.getString(KEY_WINDOW_X)?.toIntOrNull()
            val savedY = windowStore.getString(KEY_WINDOW_Y)?.toIntOrNull()
            val configuration = if (savedX != null && savedY != null) {
                configurationAt(Point(savedX + initialWidth / 2, savedY + initialHeight / 2), window)
            } else {
                window.graphicsConfiguration
            }
            val area = workArea(configuration)
            val width = initialWidth.coerceAtMost(area.width)
            val height = initialHeight.coerceAtMost(area.height)
            val x = savedX ?: (area.x + (area.width - width) / 2)
            val y = savedY ?: (area.y + (area.height - height) / 2)
            return clampWindowBounds(Rectangle(x, y, width, height), area, MIN_WIDTH, MIN_HEIGHT)
        }

        fun saveNormalBounds(bounds: java.awt.Rectangle) {
            windowStore.edit {
                putString(KEY_WINDOW_X, bounds.x.toString())
                putString(KEY_WINDOW_Y, bounds.y.toString())
                putString(KEY_WINDOW_WIDTH, bounds.width.toString())
                putString(KEY_WINDOW_HEIGHT, bounds.height.toString())
            }
        }

        fun setWindowBounds(window: AwtWindow, bounds: java.awt.Rectangle) {
            window.setBounds(bounds)
            // Compose 的 WindowState 会在重组时把自身 size 重新写回窗口；只改 AWT bounds
            // 会让最大化在下一帧被旧的 1440x900 覆盖，所以两边必须同步。
            windowState.size = DpSize(bounds.width.dp, bounds.height.dp)
            windowState.position = WindowPosition(bounds.x.dp, bounds.y.dp)
        }

        fun applyNormal(window: AwtWindow, bounds: Rectangle) {
            val area = workArea(configurationAt(Point(bounds.x + bounds.width / 2, bounds.y + 1), window))
            val clamped = clampWindowBounds(bounds, area, MIN_WIDTH, MIN_HEIGHT)
            setWindowBounds(window, clamped)
            normalBounds = clamped
            saveNormalBounds(clamped)
        }

        fun toggleMaximize(window: AwtWindow) {
            val area = currentWorkArea(window)
            val current = window.bounds
            // 用户也可能用 Win+↑ 把窗口吸到顶部，这种系统级最大化不会经过我们，
            // 判定逻辑统一放进 toggleMaximizeDecision，避免「还原成最大化」的状态错乱。
            val decision = toggleMaximizeDecision(
                maximized = maximized,
                currentBounds = current,
                rememberedNormalBounds = normalBounds,
                workArea = area,
                fallbackNormalBounds = defaultNormalBounds(window),
                minimumWidth = MIN_WIDTH,
                minimumHeight = MIN_HEIGHT,
            )
            if (decision.maximized) saveNormalBounds(decision.normalBounds)
            setWindowBounds(window, decision.bounds)
            normalBounds = decision.normalBounds
            maximized = decision.maximized
            windowStore.edit { putBoolean(KEY_WINDOW_MAXIMIZED, maximized) }
        }

        fun toggleFullscreen(window: AwtWindow) {
            if (isFullscreen) {
                isFullscreen = false
                maximized = maximizedBeforeFullscreen
                if (maximized) {
                    setWindowBounds(window, currentWorkArea(window))
                } else {
                    applyNormal(window, normalBounds ?: defaultNormalBounds(window))
                }
            } else {
                if (!maximized) {
                    normalBounds = window.bounds
                    saveNormalBounds(window.bounds)
                }
                maximizedBeforeFullscreen = maximized
                isFullscreen = true
                setWindowBounds(window, window.graphicsConfiguration.bounds)
            }
        }

        fun restorePosition(window: AwtWindow) {
            applyNormal(window, defaultNormalBounds(window))
        }

        fun restoreForDrag(window: AwtWindow, pointer: Point): Point {
            val area = workArea(configurationAt(pointer, window))
            val restored = restoredBoundsForDrag(
                maximizedBounds = window.bounds,
                normalBounds = normalBounds ?: defaultNormalBounds(window),
                pointer = pointer,
                area = area,
                titleBarHeight = TITLE_BAR_HEIGHT,
                minimumWidth = MIN_WIDTH,
                minimumHeight = MIN_HEIGHT,
            )
            maximized = false
            setWindowBounds(window, restored)
            normalBounds = restored
            windowStore.edit { putBoolean(KEY_WINDOW_MAXIMIZED, false) }
            return Point(restored.x, restored.y)
        }

        fun exitAndPersist(window: AwtWindow) {
            if (maximized) normalBounds?.let(::saveNormalBounds) else saveNormalBounds(window.bounds)
            windowStore.edit { putBoolean(KEY_WINDOW_MAXIMIZED, maximized) }
            exitApplication()
        }

        Window(
            onCloseRequest = { hostWindow?.let { exitAndPersist(it) } ?: exitApplication() },
            state = windowState,
            title = "小鸡影视",
            undecorated = true,
            onPreviewKeyEvent = { event ->
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown ||
                    appState.player.value != null
                ) {
                    false
                } else {
                    val key = event.key
                    when {
                        event.isCtrlPressed && key == androidx.compose.ui.input.key.Key.F -> {
                            appState.navigate(com.tvbox.desktop.state.DesktopScreen.Search)
                            true
                        }
                        event.isCtrlPressed && key == androidx.compose.ui.input.key.Key.H -> {
                            appState.navigate(com.tvbox.desktop.state.DesktopScreen.History)
                            true
                        }
                        event.isCtrlPressed && key == androidx.compose.ui.input.key.Key.J -> {
                            appState.navigate(com.tvbox.desktop.state.DesktopScreen.Follow)
                            true
                        }
                        event.isCtrlPressed && key == androidx.compose.ui.input.key.Key.Comma -> {
                            appState.navigate(com.tvbox.desktop.state.DesktopScreen.Settings)
                            true
                        }
                        event.isAltPressed && key == androidx.compose.ui.input.key.Key.DirectionLeft -> {
                            appState.goBack()
                        }
                        else -> false
                    }
                }
            },
        ) {
            hostWindow = window
            LaunchedEffect(Unit) {
                // 窗口/任务栏图标用品牌图的多档位版本，Windows 会按 DPI 自己挑最合适的一档。
                runCatching {
                    window.iconImages = listOf(16, 24, 32, 48, 64, 128, 256).mapNotNull { size ->
                        object {}.javaClass.getResourceAsStream("/brand/icon_$size.png")?.use {
                            javax.imageio.ImageIO.read(it)
                        }
                    }
                }
                window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
                // 等 Compose 先把 windowState.size 应用到窗口，再覆盖成自己的工作区尺寸，
                // 否则启动即最大化的场景会被随后的首帧布局改回去。
                withFrameNanos { }
                if (startMaximized) {
                    normalBounds = defaultNormalBounds(window)
                    setWindowBounds(window, currentWorkArea(window))
                    maximized = true
                } else {
                    restorePosition(window)
                }
            }

            Column(Modifier.fillMaxSize()) {
                if (!isFullscreen) {
                    WindowTitleBar(
                        window = window,
                        isMaximized = maximized,
                        isWindowMaximized = { maximized },
                        onMinimize = { window.setState(java.awt.Frame.ICONIFIED) },
                        onToggleMaximize = { toggleMaximize(window) },
                        onRestoreForDrag = { pointer -> restoreForDrag(window, pointer) },
                        workAreaFor = { pointer -> workArea(configurationAt(pointer, window)) },
                        onClose = { exitAndPersist(window) },
                    )
                }
                Box(Modifier.fillMaxSize()) {
                    DesktopApp(
                        state = appState,
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = { toggleFullscreen(window) },
                    )
                }
            }
        }
    }
}

private const val KEY_WINDOW_WIDTH = "window_width"
private const val KEY_WINDOW_HEIGHT = "window_height"
private const val KEY_WINDOW_MAXIMIZED = "window_maximized"
private const val KEY_WINDOW_X = "window_x"
private const val KEY_WINDOW_Y = "window_y"

private const val DEFAULT_WIDTH = 1440
private const val DEFAULT_HEIGHT = 900
private const val MIN_WIDTH = 1120
private const val MIN_HEIGHT = 720
private const val MAX_WIDTH = 5120
private const val MAX_HEIGHT = 2880
private const val TITLE_BAR_HEIGHT = 38
