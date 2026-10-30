package com.tvbox.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tvbox.app.ui.theme.TvColors
import com.tvbox.app.ui.theme.TvDimens

@Composable
fun Modifier.tvFocusScale(
    shape: Shape,
    focusedBorder: Color? = null,
    idleBorder: Color? = null,
): Modifier = composed {
    val resolvedFocusedBorder = focusedBorder ?: TvColors.FocusRing
    val resolvedIdleBorder = idleBorder ?: TvColors.Border
    val accent = TvColors.Accent
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) TvDimens.FocusScale else 1f,
        animationSpec = tween(140),
        label = "tv-focus-scale",
    )
    this
        .onFocusChanged { focused = it.isFocused }
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.shape = shape
            shadowElevation = if (focused) 12.dp.toPx() else 0f
            ambientShadowColor = accent.copy(alpha = 0.5f)
            spotShadowColor = accent.copy(alpha = 0.6f)
            clip = false
        }
        .border(
            width = if (focused) 3.dp else 1.dp,
            color = if (focused) resolvedFocusedBorder else resolvedIdleBorder,
            shape = shape,
        )
}

internal suspend fun FocusRequester.requestFocusWhenReady(maxFrames: Int = 45): Boolean {
    repeat(maxFrames) {
        withFrameNanos { }
        if (runCatching { requestFocus() }.getOrDefault(false)) return true
    }
    return false
}

@Composable
internal fun RestoreFocusOnClose(open: Boolean, requester: FocusRequester) {
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        val restore = wasOpen && !open
        wasOpen = open
        if (restore) requester.requestFocusWhenReady()
    }
}
