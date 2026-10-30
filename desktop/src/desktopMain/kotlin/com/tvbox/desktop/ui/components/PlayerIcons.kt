package com.tvbox.desktop.ui.components

import com.tvbox.desktop.state.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 播放控制图标用 Canvas 现画，避免为了几个图标引入整包 material-icons-extended
 * （它会显著增大桌面端体积）。
 */
@Composable
fun PauseIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val barWidth = this.size.width * 0.2f
        val gap = this.size.width * 0.18f
        val top = this.size.height * 0.18f
        val height = this.size.height * 0.64f
        drawRoundRect(
            color = color,
            topLeft = Offset(this.size.width / 2 - gap / 2 - barWidth, top),
            size = Size(barWidth, height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.2f),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(this.size.width / 2 + gap / 2, top),
            size = Size(barWidth, height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.2f),
        )
    }
}

@Composable
fun PlayIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val path = Path().apply {
            moveTo(this@Canvas.size.width * 0.28f, this@Canvas.size.height * 0.14f)
            lineTo(this@Canvas.size.width * 0.28f, this@Canvas.size.height * 0.86f)
            lineTo(this@Canvas.size.width * 0.86f, this@Canvas.size.height * 0.5f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun SkipNextIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val path = Path().apply {
            moveTo(this@Canvas.size.width * 0.16f, this@Canvas.size.height * 0.16f)
            lineTo(this@Canvas.size.width * 0.16f, this@Canvas.size.height * 0.84f)
            lineTo(this@Canvas.size.width * 0.68f, this@Canvas.size.height * 0.5f)
            close()
        }
        drawPath(path, color)
        drawRoundRect(
            color = color,
            topLeft = Offset(this@Canvas.size.width * 0.72f, this@Canvas.size.height * 0.16f),
            size = Size(this@Canvas.size.width * 0.12f, this@Canvas.size.height * 0.68f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
        )
    }
}

@Composable
fun SkipPreviousIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val path = Path().apply {
            moveTo(this@Canvas.size.width * 0.84f, this@Canvas.size.height * 0.16f)
            lineTo(this@Canvas.size.width * 0.84f, this@Canvas.size.height * 0.84f)
            lineTo(this@Canvas.size.width * 0.32f, this@Canvas.size.height * 0.5f)
            close()
        }
        drawPath(path, color)
        drawRoundRect(
            color = color,
            topLeft = Offset(this@Canvas.size.width * 0.16f, this@Canvas.size.height * 0.16f),
            size = Size(this@Canvas.size.width * 0.12f, this@Canvas.size.height * 0.68f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
        )
    }
}

@Composable
fun FullscreenIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val stroke = Stroke(width = this.size.width * 0.09f, cap = StrokeCap.Round)
        val inset = this.size.width * 0.2f
        val arm = this.size.width * 0.22f
        val w = this.size.width
        val h = this.size.height
        listOf(
            Offset(inset, inset) to Offset(inset + arm, inset),
            Offset(inset, inset) to Offset(inset, inset + arm),
            Offset(w - inset, inset) to Offset(w - inset - arm, inset),
            Offset(w - inset, inset) to Offset(w - inset, inset + arm),
            Offset(inset, h - inset) to Offset(inset + arm, h - inset),
            Offset(inset, h - inset) to Offset(inset, h - inset - arm),
            Offset(w - inset, h - inset) to Offset(w - inset - arm, h - inset),
            Offset(w - inset, h - inset) to Offset(w - inset, h - inset - arm),
        ).forEach { (start, end) -> drawLine(color, start, end, strokeWidth = stroke.width, cap = StrokeCap.Round) }
    }
}

@Composable
fun ExitFullscreenIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val inset = this.size.width * 0.2f
        val arm = this.size.width * 0.22f
        val w = this.size.width
        val h = this.size.height
        listOf(
            Offset(inset + arm, inset) to Offset(inset, inset + arm),
            Offset(inset, inset + arm) to Offset(inset + arm, inset),
            Offset(w - inset - arm, inset) to Offset(w - inset, inset + arm),
            Offset(w - inset, inset + arm) to Offset(w - inset - arm, inset),
            Offset(inset + arm, h - inset) to Offset(inset, h - inset - arm),
            Offset(inset, h - inset - arm) to Offset(inset + arm, h - inset),
            Offset(w - inset - arm, h - inset) to Offset(w - inset, h - inset - arm),
            Offset(w - inset, h - inset - arm) to Offset(w - inset - arm, h - inset),
        ).forEach { (start, end) ->
            drawLine(
                color,
                start,
                end,
                strokeWidth = this.size.width * 0.09f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun VolumeIcon(color: Color, muted: Boolean, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val bodyW = this.size.width * 0.34f
        val bodyH = this.size.height * 0.4f
        val left = this.size.width * 0.1f
        val top = this.size.height / 2 - bodyH / 2
        drawRect(color, Offset(left, top), Size(bodyW, bodyH))
        val cone = Path().apply {
            moveTo(left + bodyW, top)
            lineTo(this@Canvas.size.width * 0.55f, this@Canvas.size.height * 0.18f)
            lineTo(this@Canvas.size.width * 0.55f, this@Canvas.size.height * 0.82f)
            lineTo(left + bodyW, top + bodyH)
            close()
        }
        drawPath(cone, color)
        if (muted) {
            val stroke = Stroke(width = this.size.width * 0.09f, cap = StrokeCap.Round)
            drawLine(
                color,
                Offset(this.size.width * 0.66f, this.size.height * 0.34f),
                Offset(this.size.width * 0.9f, this.size.height * 0.66f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
            drawLine(
                color,
                Offset(this.size.width * 0.9f, this.size.height * 0.34f),
                Offset(this.size.width * 0.66f, this.size.height * 0.66f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        } else {
            drawArc(
                color = color,
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(this.size.width * 0.32f, this.size.height * 0.3f),
                size = Size(this.size.width * 0.52f, this.size.height * 0.42f),
                style = Stroke(width = this.size.width * 0.08f, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(this.size.width * 0.46f, this.size.height * 0.18f),
                size = Size(this.size.width * 0.54f, this.size.height * 0.64f),
                style = Stroke(width = this.size.width * 0.06f, cap = StrokeCap.Round),
            )
        }
    }
}
