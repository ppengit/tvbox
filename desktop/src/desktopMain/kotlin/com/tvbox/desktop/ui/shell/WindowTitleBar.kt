package com.tvbox.desktop.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tvbox.desktop.ui.theme.AppColors
import com.tvbox.desktop.ui.theme.DesktopMetrics
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import kotlin.math.roundToInt

/**
 * 自绘标题栏。窗口使用 undecorated 模式，最小化/最大化/关闭与拖动都由这里接管，
 * 深色主题才能铺满整个窗口，而不是顶着一条系统白条。
 */
@Composable
fun WindowTitleBar(
    window: Window,
    isMaximized: Boolean,
    isWindowMaximized: () -> Boolean,
    title: String = "小鸡影视",
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onRestoreForDrag: (Point) -> Point,
    workAreaFor: (Point) -> Rectangle,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DesktopMetrics.titleBarHeight)
            .background(AppColors.colors.sidebar)
            .pointerInput(Unit) {
                var startLocation: Point? = null
                var startPointer: Point? = null
                var restoredDuringDrag = false
                detectDragGestures(
                    onDragStart = {
                        startLocation = window.location
                        startPointer = java.awt.MouseInfo.getPointerInfo()?.location
                        restoredDuringDrag = false
                    },
                    onDragEnd = {
                        startLocation = null
                        startPointer = null
                        restoredDuringDrag = false
                    },
                    onDragCancel = {
                        startLocation = null
                        startPointer = null
                        restoredDuringDrag = false
                    },
                ) { change, _ ->
                    change.consume()
                    val pointer = java.awt.MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
                    if (isWindowMaximized() && !restoredDuringDrag) {
                        startLocation = onRestoreForDrag(pointer)
                        startPointer = pointer
                        restoredDuringDrag = true
                    }
                    val start = startLocation ?: return@detectDragGestures
                    val from = startPointer ?: return@detectDragGestures
                    val area = workAreaFor(pointer)
                    val targetX = (start.x + pointer.x - from.x)
                        .coerceIn(area.x - window.width + 160, area.x + area.width - 160)
                    val targetY = (start.y + pointer.y - from.y)
                        .coerceIn(area.y, area.y + area.height - 48)
                    window.setLocation(targetX, targetY)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onToggleMaximize() })
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(14.dp))
        Box(Modifier.size(6.dp).background(AppColors.colors.accent, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = AppColors.colors.textTertiary,
        )
        Spacer(Modifier.weight(1f))
        WindowButton(label = "最小化", onClick = onMinimize) { tint ->
            Canvas(Modifier.size(10.dp)) {
                drawLine(tint, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.2.dp.toPx())
            }
        }
        WindowButton(label = if (isMaximized) "还原" else "最大化", onClick = onToggleMaximize) { tint ->
            Canvas(Modifier.size(10.dp)) {
                val stroke = 1.2.dp.toPx()
                if (isMaximized) {
                    val inset = stroke * 1.5f
                    drawRect(
                        color = tint,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(stroke),
                    )
                } else {
                    val inset = stroke / 2
                    drawRect(
                        color = tint,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke),
                    )
                }
            }
        }
        WindowButton(label = "关闭", onClick = onClose, danger = true) { tint ->
            Canvas(Modifier.size(10.dp)) {
                val stroke = 1.2.dp.toPx()
                drawLine(tint, Offset(0f, 0f), Offset(size.width, size.height), stroke)
                drawLine(tint, Offset(size.width, 0f), Offset(0f, size.height), stroke)
            }
        }
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun WindowButton(
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    icon: @Composable (Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val active = hovered || focused
    val background = when {
        danger && active -> Color(0xFFD93F3F)
        active -> AppColors.colors.surfaceHover
        else -> Color.Transparent
    }
    val tint = if (danger && active) Color.White else AppColors.colors.textSecondary
    Box(
        modifier = Modifier
            .width(40.dp)
            .fillMaxHeight()
            .background(background)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.Center,
    ) {
        icon(tint)
    }
}
