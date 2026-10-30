package com.tvbox.desktop.ui.components

import com.tvbox.desktop.state.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeIcon(color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.1f
        val roof = Path().apply {
            moveTo(w * 0.1f, h * 0.48f)
            lineTo(w * 0.5f, h * 0.14f)
            lineTo(w * 0.9f, h * 0.48f)
        }
        drawPath(roof, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.2f, h * 0.44f),
            size = Size(w * 0.6f, h * 0.42f),
            cornerRadius = CornerRadius(w * 0.06f),
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.43f, h * 0.63f),
            size = Size(w * 0.14f, h * 0.23f),
            cornerRadius = CornerRadius(w * 0.03f),
        )
    }
}

@Composable
fun SearchIcon(color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.1f
        drawCircle(
            color = color,
            radius = w * 0.28f,
            center = Offset(w * 0.44f, h * 0.44f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color,
            Offset(w * 0.65f, h * 0.65f),
            Offset(w * 0.88f, h * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun HistoryIcon(color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.1f
        drawCircle(
            color = color,
            radius = w * 0.36f,
            center = Offset(w * 0.5f, h * 0.52f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color,
            Offset(w * 0.5f, h * 0.52f),
            Offset(w * 0.5f, h * 0.3f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(w * 0.5f, h * 0.52f),
            Offset(w * 0.68f, h * 0.62f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun LiveTvIcon(color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.1f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.24f),
            size = Size(w * 0.76f, h * 0.5f),
            cornerRadius = CornerRadius(w * 0.08f),
            style = Stroke(width = stroke),
        )
        drawLine(color, Offset(w * 0.38f, h * 0.86f), Offset(w * 0.62f, h * 0.86f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.74f), Offset(w * 0.5f, h * 0.86f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, h * 0.14f), Offset(w * 0.5f, h * 0.24f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.58f, h * 0.14f), Offset(w * 0.5f, h * 0.24f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
    }
}

@Composable
fun BroadcastIcon(color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2, h / 2)
        drawCircle(color, radius = w * 0.09f, center = center)
        listOf(0.24f, 0.38f).forEach { radius ->
            drawCircle(
                color = color.copy(alpha = if (radius > 0.3f) 0.55f else 0.8f),
                radius = w * radius,
                center = center,
                style = Stroke(width = w * 0.075f),
            )
        }
        listOf(0.75f, 1.0f).forEach { scale ->
            val r = w * 0.5f * scale
            drawArc(
                color = color.copy(alpha = 0.45f),
                startAngle = 200f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = w * 0.06f, cap = StrokeCap.Round),
            )
            drawArc(
                color = color.copy(alpha = 0.45f),
                startAngle = 290f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = w * 0.06f, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
fun SettingsIcon(color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2, h / 2)
        drawCircle(
            color = color,
            radius = w * 0.34f,
            center = center,
            style = Stroke(width = w * 0.1f),
        )
        drawCircle(color, radius = w * 0.11f, center = center)
        repeat(8) { index ->
            val angle = index * PI / 4
            val inner = w * 0.42f
            val outer = w * 0.5f
            drawLine(
                color,
                Offset(center.x + (cos(angle) * inner).toFloat(), center.y + (sin(angle) * inner).toFloat()),
                Offset(center.x + (cos(angle) * outer).toFloat(), center.y + (sin(angle) * outer).toFloat()),
                strokeWidth = w * 0.1f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun ArrowLeftIcon(color: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.11f
        drawLine(color, Offset(w * 0.72f, h * 0.5f), Offset(w * 0.28f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.48f, h * 0.3f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.48f, h * 0.7f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun StarIcon(color: Color, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path()
        val cx = w / 2
        val cy = h / 2
        repeat(10) { index ->
            val angle = -PI / 2 + index * PI / 5
            val radius = if (index % 2 == 0) w * 0.48f else w * 0.2f
            val x = (cx + cos(angle) * radius).toFloat()
            val y = (cy + sin(angle) * radius).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
    }
}

/** 片库：四宫格，表示分类浏览。 */
@Composable
fun LibraryIcon(color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.09f
        val gap = w * 0.08f
        val cell = (w - gap * 3) / 2
        listOf(
            Offset(gap, gap),
            Offset(gap * 2 + cell, gap),
            Offset(gap, gap * 2 + cell),
            Offset(gap * 2 + cell, gap * 2 + cell),
        ).forEach { topLeft ->
            drawRoundRect(
                color = color,
                topLeft = topLeft,
                size = Size(cell, (h - gap * 3) / 2),
                cornerRadius = CornerRadius(w * 0.06f),
                style = Stroke(width = stroke),
            )
        }
    }
}

/** 追剧：书签形，表示持续关注。 */
@Composable
fun FollowIcon(color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.1f
        val bookmark = Path().apply {
            moveTo(w * 0.24f, h * 0.12f)
            lineTo(w * 0.76f, h * 0.12f)
            lineTo(w * 0.76f, h * 0.88f)
            lineTo(w * 0.5f, h * 0.68f)
            lineTo(w * 0.24f, h * 0.88f)
            close()
        }
        drawPath(bookmark, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}
