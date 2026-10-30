package com.tvbox.app.ui

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tvbox.app.ui.components.requestFocusWhenReady
import com.tvbox.app.ui.components.tvFocusScale
import com.tvbox.app.domain.PlaySource
import com.tvbox.app.domain.PlayerRemotePolicy
import com.tvbox.app.domain.VideoScaleMode
import com.tvbox.app.domain.correspondingEpisodeIndex

internal enum class PlayerPanel { None, Episodes, Sources, Speeds, Picture, Help }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerControls(
    title: String,
    sources: List<PlaySource>,
    sourceIndex: Int,
    episodeIndex: Int,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    speed: Float,
    panel: PlayerPanel,
    videoScaleMode: VideoScaleMode,
    onPanel: (PlayerPanel) -> Unit,
    onToggle: () -> Unit,
    onEpisode: (Int) -> Unit,
    onSource: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    onVideoScale: (VideoScaleMode) -> Unit,
    onSeek: (Long) -> Unit,
    onInteraction: () -> Unit,
    onScrubbing: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = sources[sourceIndex]
    val compact = LocalConfiguration.current.screenWidthDp < 800
    val buttonScroll = rememberScrollState()
    var progressFocused by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }
    val sourceFocus = remember { FocusRequester() }
    val speedFocus = remember { FocusRequester() }
    val pictureFocus = remember { FocusRequester() }
    val helpFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    var returnFocus by remember { mutableStateOf<FocusRequester?>(null) }
    var preview by remember { mutableStateOf<Float?>(null) }
    var lastSeekTime by remember { mutableStateOf(Long.MIN_VALUE) }
    LaunchedEffect(panel) {
        if (panel == PlayerPanel.None) {
            (returnFocus ?: playFocus).requestFocusWhenReady()
        }
    }
    Box(modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                .windowInsetsPadding(WindowInsets.safeDrawing).testTag("player_controls"),
            color = Color.Transparent, contentColor = Color.White,
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("$title · ${source.episodes.getOrNull(episodeIndex)?.title.orEmpty()}",
                    style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatPlayerTime(preview?.toLong() ?: positionMs), modifier = Modifier.width(66.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = Color.White.copy(alpha = 0.8f))
                    Slider(
                        value = preview ?: positionMs.toFloat().coerceIn(0f, durationMs.coerceAtLeast(1).toFloat()),
                        onValueChange = { preview = it; onScrubbing(true); onInteraction() },
                        onValueChangeFinished = {
                            preview?.let { onSeek(it.toLong()) }; preview = null; onScrubbing(false)
                        },
                        valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(), enabled = durationMs > 0,
                        thumb = {
                            Box(Modifier.size(if (progressFocused) 18.dp else 12.dp)
                                .background(Color.White, CircleShape))
                        },
                        track = { slider ->
                            val fraction = ((slider.value - slider.valueRange.start) /
                                (slider.valueRange.endInclusive - slider.valueRange.start).coerceAtLeast(1f)).coerceIn(0f, 1f)
                            Canvas(Modifier.fillMaxWidth().height(if (progressFocused) 5.dp else 3.dp)) {
                                val center = size.height / 2
                                drawLine(Color.White.copy(alpha = 0.28f), Offset(0f, center), Offset(size.width, center), size.height, StrokeCap.Round)
                                drawLine(Color(0xFF7AE2BA), Offset(0f, center), Offset(size.width * fraction, center), size.height, StrokeCap.Round)
                            }
                        },
                        modifier = Modifier.weight(1f).height(36.dp).padding(horizontal = 8.dp)
                            .onFocusChanged { progressFocused = it.isFocused; if (it.isFocused) onInteraction() }
                            .testTag("player_progress").onPreviewKeyEvent { event ->
                            val delta = when (event.nativeKeyEvent.keyCode) {
                                NativeKeyEvent.KEYCODE_DPAD_LEFT -> -10_000L
                                NativeKeyEvent.KEYCODE_DPAD_RIGHT -> 10_000L
                                else -> return@onPreviewKeyEvent false
                            }
                            onInteraction()
                            if (event.type == KeyEventType.KeyDown) {
                                if (event.nativeKeyEvent.repeatCount == 0 || lastSeekTime == Long.MIN_VALUE ||
                                    event.nativeKeyEvent.eventTime - lastSeekTime >= 150) {
                                    preview = ((preview?.toLong() ?: positionMs) + delta).coerceIn(0, durationMs.coerceAtLeast(0)).toFloat()
                                    lastSeekTime = event.nativeKeyEvent.eventTime
                                }
                                onScrubbing(true)
                            } else if (event.type == KeyEventType.KeyUp) {
                                preview?.let { onSeek(it.toLong()) }; preview = null; onScrubbing(false)
                                lastSeekTime = Long.MIN_VALUE
                            }
                            true
                        },
                    )
                    Text(if (durationMs > 0) formatPlayerTime(durationMs) else "--:--", modifier = Modifier.width(66.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = Color.White.copy(alpha = 0.8f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().then(if (compact) Modifier.horizontalScroll(buttonScroll) else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlButton(if (playing) "暂停" else "播放", onToggle, Modifier.focusRequester(playFocus), primary = true, onFocused = onInteraction)
                    ControlButton("上一集", {
                        PlayerRemotePolicy.episodeTarget(episodeIndex, -1, source.episodes.size)?.let(onEpisode)
                    }, enabled = episodeIndex > 0, onFocused = onInteraction)
                    ControlButton("下一集", {
                        PlayerRemotePolicy.episodeTarget(episodeIndex, 1, source.episodes.size)?.let(onEpisode)
                    }, enabled = episodeIndex < source.episodes.lastIndex, onFocused = onInteraction)
                    if (!compact) Spacer(Modifier.weight(1f))
                    ControlButton("选集", { returnFocus = episodeFocus; onPanel(PlayerPanel.Episodes) }, Modifier.focusRequester(episodeFocus), onFocused = onInteraction)
                    ControlButton("线路", { returnFocus = sourceFocus; onPanel(PlayerPanel.Sources) }, Modifier.focusRequester(sourceFocus), onFocused = onInteraction)
                    ControlButton(formatPlayerSpeed(speed), { returnFocus = speedFocus; onPanel(PlayerPanel.Speeds) }, Modifier.focusRequester(speedFocus), onFocused = onInteraction)
                    ControlButton("画面", { returnFocus = pictureFocus; onPanel(PlayerPanel.Picture) }, Modifier.focusRequester(pictureFocus), onFocused = onInteraction)
                    ControlButton("帮助", { returnFocus = helpFocus; onPanel(PlayerPanel.Help) }, Modifier.focusRequester(helpFocus), onFocused = onInteraction)
                }
            }
        }
        if (panel != PlayerPanel.None) {
            val labels = when (panel) {
                PlayerPanel.Episodes -> source.episodes.map { it.title }
                PlayerPanel.Sources -> sources.map { it.name }
                PlayerPanel.Speeds -> playerSpeeds.map(::formatPlayerSpeed)
                PlayerPanel.Picture -> VideoScaleMode.entries.map { it.displayName }
                PlayerPanel.Help -> listOf("方向键：浏览", "确认键：选择", "返回键：关闭", "数字 1/3：上一集/下一集", "菜单键：循环倍速")
                PlayerPanel.None -> emptyList()
            }
            val selected = when (panel) {
                PlayerPanel.Episodes -> episodeIndex
                PlayerPanel.Sources -> sourceIndex
                PlayerPanel.Speeds -> playerSpeeds.indexOf(speed).coerceAtLeast(0)
                PlayerPanel.Picture -> VideoScaleMode.entries.indexOf(videoScaleMode)
                PlayerPanel.Help, PlayerPanel.None -> 0
            }
            val pickerFocus = remember(panel, labels.size) { List(labels.size) { FocusRequester() } }
            val grid = rememberLazyGridState()
            LaunchedEffect(panel, selected, labels.size) {
                if (panel != PlayerPanel.Help && labels.isNotEmpty()) {
                    val target = selected.coerceIn(0, labels.lastIndex)
                    grid.scrollToItem(target)
                    pickerFocus[target].requestFocusWhenReady()
                } else {
                    closeFocus.requestFocusWhenReady()
                }
            }
            Dialog(
                onDismissRequest = { onPanel(PlayerPanel.None) },
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                ),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.88f).fillMaxHeight(0.72f),
                    color = Color(0xFA202020), contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(16.dp).focusGroup()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(when (panel) {
                                    PlayerPanel.Episodes -> "选择集数 · ${source.episodes.size} 集"
                                    PlayerPanel.Sources -> "选择线路 · ${sources.size} 条"
                                    PlayerPanel.Picture -> "画面比例 · ${videoScaleMode.displayName}"
                                    PlayerPanel.Help -> "遥控操作指南"
                                    else -> "播放倍速 · 当前 ${formatPlayerSpeed(speed)}"
                                }, style = MaterialTheme.typography.titleMedium)
                                Text(if (panel == PlayerPanel.Help) "控制栏与全屏播放均可使用遥控器操作"
                                    else "当前：${labels.getOrNull(selected).orEmpty()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.65f))
                            }
                            if (panel != PlayerPanel.Help) {
                                ControlButton("帮助", { onPanel(PlayerPanel.Help) }, onFocused = onInteraction)
                            }
                            ControlButton("关闭", { onPanel(PlayerPanel.None) }, Modifier.focusRequester(closeFocus), onFocused = onInteraction)
                        }
                        if (panel == PlayerPanel.Picture) {
                            Text(videoScaleMode.description, style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 12.dp))
                        }
                        if (panel == PlayerPanel.Help) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                labels.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                                Text("全屏播放：左右键快退/快进 10 秒，上下或确认键显示控制栏。",
                                    color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                                Text("在进度条上左右键调整进度，松开按键后跳转；返回键逐层关闭面板与控制栏。",
                                    color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                            }
                        } else LazyVerticalGrid(GridCells.Adaptive(130.dp), state = grid,
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(8.dp),
                            modifier = Modifier.weight(1f)) {
                            itemsIndexed(labels) { index, label ->
                                val enabled = if (panel != PlayerPanel.Sources) true else {
                                    val targetSource = sources.getOrNull(index)
                                    val currentEpisode = source.episodes.getOrNull(episodeIndex)
                                    targetSource != null && currentEpisode != null && correspondingEpisodeIndex(
                                        targetSource, currentEpisode.title, episodeIndex) != null
                                }
                                ControlButton(if (index == selected) "$label ✓" else label, {
                                    onInteraction()
                                    when (panel) {
                                        PlayerPanel.Episodes -> onEpisode(index)
                                        PlayerPanel.Sources -> onSource(index)
                                        PlayerPanel.Speeds -> onSpeed(playerSpeeds[index])
                                        PlayerPanel.Picture -> onVideoScale(VideoScaleMode.entries[index])
                                        else -> Unit
                                    }
                                    onPanel(PlayerPanel.None)
                                }, Modifier.focusRequester(pickerFocus[index]), enabled, onFocused = onInteraction)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ControlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    onFocused: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (primary) 24.dp else 10.dp)
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp)
            .then(if (enabled) Modifier.tvFocusScale(shape) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            },
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = when { primary -> Color.White; focused -> Color.White.copy(alpha = 0.18f); else -> Color.Transparent },
            contentColor = if (primary) Color(0xFF101614) else Color.White.copy(alpha = 0.9f),
            disabledContainerColor = Color.Transparent, disabledContentColor = Color.White.copy(alpha = 0.3f),
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(text, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

internal fun formatPlayerTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
private fun formatPlayerSpeed(speed: Float): String {
    val normalized = if (speed == 0f) 0f else speed
    return if (normalized % 1f == 0f) "${normalized.toInt()}x" else "${normalized}x"
}

private val playerSpeeds = PlayerRemotePolicy.speedOptions
