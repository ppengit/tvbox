package com.tvbox.desktop.ui.player

import com.tvbox.desktop.state.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tvbox.desktop.ui.theme.AppColors

/**
 * 自绘进度条：桌面端需要"点哪跳哪"和拖动预览，Material3 的 Slider 在深浅色和
 * 尺寸上都不合适，这里按播放器习惯单独实现。
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(0L) }
    val total = durationMs.coerceAtLeast(1L)
    val shown = if (dragging) dragPosition else positionMs.coerceIn(0L, total)
    val accentColor = AppColors.colors.accent

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = shown.toFloat(),
                    range = 0f..total.toFloat(),
                )
            }
            .pointerInput(enabled, durationMs) {
                if (!enabled || durationMs <= 0L) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun timeAt(x: Float): Long =
                        ((x / size.width).coerceIn(0f, 1f) * durationMs).toLong()

                    dragging = true
                    dragPosition = timeAt(down.position.x)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        dragPosition = timeAt(change.position.x)
                        change.consume()
                        if (!change.pressed) break
                    }
                    dragging = false
                    onSeek(dragPosition)
                }
            },
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val centerY = size.height / 2
            val trackHeight = 4.dp.toPx()
            val radius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2)

            drawLine(
                color = Color(0x33FFFFFF),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = trackHeight,
            )
            val bufferedFraction = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            if (bufferedFraction > 0f) {
                drawLine(
                    color = Color(0x55FFFFFF),
                    start = Offset(0f, centerY),
                    end = Offset(size.width * bufferedFraction, centerY),
                    strokeWidth = trackHeight,
                )
            }
            val progress = (shown.toFloat() / total).coerceIn(0f, 1f)
            drawLine(
                color = accentColor,
                start = Offset(0f, centerY),
                end = Offset(size.width * progress, centerY),
                strokeWidth = trackHeight,
            )
            if (dragging || enabled) {
                drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = Offset(size.width * progress, centerY),
                )
            }
        }
    }
}

@Composable
fun BufferedSpinner(size: androidx.compose.ui.unit.Dp = 34.dp) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = Modifier.height(size),
        color = AppColors.colors.accent,
        strokeWidth = 2.5.dp,
    )
}
