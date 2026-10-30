package com.tvbox.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tvbox.desktop.ui.theme.AppColors
import com.tvbox.desktop.ui.theme.DesktopControls
import com.tvbox.desktop.ui.theme.DesktopFocus
import com.tvbox.desktop.ui.theme.DesktopMotion
import com.tvbox.desktop.ui.theme.DesktopShapes
import com.tvbox.desktop.ui.theme.DesktopSpacing

enum class StatusBadgeTone { Neutral, Accent, Warning, Danger }

@Composable
fun DesktopIconButton(
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(DesktopShapes.control)
    val background by animateColorAsState(
        when {
            selected -> AppColors.colors.surfaceActive
            hovered && enabled -> AppColors.colors.surfaceHover
            else -> Color.Transparent
        },
        tween(DesktopMotion.fast),
        label = "iconButtonBackground",
    )
    val contentColor = when {
        !enabled -> AppColors.colors.textTertiary
        selected -> AppColors.colors.accent
        hovered || focused -> AppColors.colors.textPrimary
        else -> AppColors.colors.textSecondary
    }
    Box(
        modifier = modifier
            .size(DesktopControls.iconButtonSize)
            .clip(shape)
            .background(background)
            .border(
                if (focused) DesktopFocus.strokeWidth else 1.dp,
                when {
                    focused -> AppColors.colors.accent
                    selected -> AppColors.colors.accent.copy(alpha = 0.45f)
                    else -> Color.Transparent
                },
                shape,
            )
            .hoverable(interaction, enabled = enabled)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = icon)
    }
}

@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusBadgeTone = StatusBadgeTone.Neutral,
) {
    val foreground = when (tone) {
        StatusBadgeTone.Neutral -> AppColors.colors.textSecondary
        StatusBadgeTone.Accent -> AppColors.colors.accent
        StatusBadgeTone.Warning -> AppColors.colors.warning
        StatusBadgeTone.Danger -> AppColors.colors.danger
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DesktopShapes.pill))
            .background(foreground.copy(alpha = 0.12f))
            .border(1.dp, foreground.copy(alpha = 0.35f), RoundedCornerShape(DesktopShapes.pill))
            .padding(horizontal = DesktopSpacing.sm, vertical = DesktopSpacing.xxs),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = foreground)
    }
}

@Composable
fun DangerButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(DesktopShapes.control)
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> AppColors.colors.surfaceRaised
            hovered -> AppColors.colors.danger.copy(alpha = 0.82f)
            else -> AppColors.colors.danger
        },
        animationSpec = tween(DesktopMotion.fast),
        label = "dangerButtonBackground",
    )
    Row(
        modifier = modifier
            .height(DesktopControls.height)
            .clip(shape)
            .background(background)
            .border(
                DesktopFocus.strokeWidth,
                if (focused) AppColors.colors.textPrimary.copy(alpha = DesktopFocus.ringAlpha) else Color.Transparent,
                shape,
            )
            .hoverable(interaction, enabled = enabled)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { if (!enabled) disabled() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color(0xFF1A0A0A) else AppColors.colors.textTertiary,
        )
    }
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(DesktopShapes.card)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (elevated) AppColors.colors.surfaceRaised else AppColors.colors.surface)
            .border(1.dp, AppColors.colors.border, shape)
            .padding(DesktopSpacing.lg),
    ) {
        content()
    }
}

@Composable
fun InlineError(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(DesktopShapes.control)
    Row(
        modifier = modifier
            .clip(shape)
            .background(AppColors.colors.danger.copy(alpha = 0.10f))
            .border(1.dp, AppColors.colors.danger.copy(alpha = 0.35f), shape)
            .padding(horizontal = DesktopSpacing.md, vertical = DesktopSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesktopSpacing.sm),
    ) {
        Text("!", color = AppColors.colors.danger, fontWeight = FontWeight.Bold)
        Text(
            message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.colors.textPrimary,
        )
        if (onRetry != null) {
            GhostButton(text = "重试", onClick = onRetry)
        }
    }
}

@Composable
fun MediaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(DesktopShapes.pill))
            .background(AppColors.colors.surfaceActive),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(DesktopShapes.pill))
                    .background(AppColors.colors.accent),
            )
        }
    }
}

@Composable
fun RemoveMediaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "移除",
) {
    DesktopIconButton(
        contentDescription = contentDescription,
        modifier = modifier,
        onClick = onClick,
    ) {
        Text("×", style = MaterialTheme.typography.titleMedium)
    }
}
