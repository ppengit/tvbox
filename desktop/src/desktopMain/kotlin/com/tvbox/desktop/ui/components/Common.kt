package com.tvbox.desktop.ui.components

import com.tvbox.desktop.state.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tvbox.app.domain.DoubanHotItem
import com.tvbox.app.domain.Movie
import com.tvbox.desktop.ui.theme.AppColors
import com.tvbox.desktop.ui.theme.DesktopControls
import com.tvbox.desktop.ui.theme.DesktopFocus
import com.tvbox.desktop.ui.theme.DesktopMetrics
import com.tvbox.desktop.ui.theme.DesktopMotion
import com.tvbox.desktop.ui.theme.DesktopShapes
import com.tvbox.desktop.ui.theme.DesktopSpacing

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                color = AppColors.colors.textPrimary,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colors.textTertiary,
                )
            }
        }
        actions()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = AppColors.colors.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(DesktopShapes.control)
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> AppColors.colors.surfaceRaised
            hovered -> Color(0xFF55F0A1)
            else -> AppColors.colors.accent
        },
        animationSpec = tween(DesktopMotion.fast),
        label = "primaryButtonBackground",
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
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color(0xFF06150E) else AppColors.colors.textTertiary,
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(DesktopShapes.control)
    val background by animateColorAsState(
        if (hovered && enabled) AppColors.colors.surfaceHover else AppColors.colors.surfaceRaised,
        tween(DesktopMotion.fast),
        label = "ghostButtonBackground",
    )
    val borderColor = when {
        focused -> AppColors.colors.accent
        hovered && enabled -> AppColors.colors.borderStrong
        else -> AppColors.colors.border
    }
    Row(
        modifier = modifier
            .height(DesktopControls.height)
            .clip(shape)
            .background(background)
            .border(if (focused) DesktopFocus.strokeWidth else 1.dp, borderColor, shape)
            .hoverable(interaction, enabled = enabled)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { if (!enabled) disabled() }
            .padding(horizontal = DesktopControls.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) AppColors.colors.textPrimary else AppColors.colors.textTertiary,
        )
    }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val background by animateColorAsState(
        when {
            !enabled -> AppColors.colors.surfaceRaised.copy(alpha = DesktopFocus.disabledAlpha)
            selected -> AppColors.colors.accentSoft
            hovered -> AppColors.colors.surfaceHover
            else -> Color.Transparent
        },
        animationSpec = tween(DesktopMotion.fast),
        label = "chipBackground",
    )
    val textColor by animateColorAsState(
        when {
            !enabled -> AppColors.colors.textTertiary
            selected -> AppColors.colors.accent
            hovered -> AppColors.colors.textPrimary
            else -> AppColors.colors.textSecondary
        },
        animationSpec = tween(DesktopMotion.fast),
        label = "chipText",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DesktopShapes.pill))
            .background(background)
            .border(
                if (focused) DesktopFocus.strokeWidth else 1.dp,
                when {
                    focused -> AppColors.colors.accent
                    selected -> AppColors.colors.accent.copy(alpha = 0.4f)
                    else -> AppColors.colors.border
                },
                RoundedCornerShape(DesktopShapes.pill),
            )
            .hoverable(interaction, enabled = enabled)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                if (!enabled) disabled()
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

/**
 * 纯文字选项：筛选行用它代替胶囊按钮。选中态靠颜色和字重区分，
 * 不用给每个词都套一个圆角框，整页会安静很多。
 */
@Composable
fun TextChoice(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = when {
            !enabled -> AppColors.colors.textTertiary
            selected -> AppColors.colors.accent
            hovered || focused -> AppColors.colors.textPrimary
            else -> AppColors.colors.textSecondary
        },
        modifier = modifier
            .clip(RoundedCornerShape(DesktopShapes.compact))
            .background(if ((hovered || focused) && !selected && enabled) AppColors.colors.surfaceHover else Color.Transparent)
            .border(
                DesktopFocus.strokeWidth,
                if (focused) AppColors.colors.accent else Color.Transparent,
                RoundedCornerShape(DesktopShapes.compact),
            )
            .hoverable(interaction, enabled = enabled)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                if (!enabled) disabled()
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/**
 * 海报图。资源站的海报链接经常失效，失败时用片名占位，避免出现一整块空白灰。
 */
@Composable
fun PosterImage(
    url: String,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var failed by remember(url) { mutableStateOf(url.isBlank()) }
    Box(modifier) {
        if (!failed) {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = contentScale,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(AppColors.colors.surfaceRaised, AppColors.colors.surface),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.colors.textTertiary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}

/**
 * 豆瓣条目卡片：海报 + 评分角标 + 标题。悬停时轻微抬升并显示可点击状态。
 */
@Composable
fun PosterCard(
    item: DoubanHotItem,
    modifier: Modifier = Modifier,
    resolving: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val lift by animateFloatAsState(if (hovered && enabled) 1f else 0f, label = "posterLift")

    Column(
        modifier = modifier
            .width(DesktopMetrics.posterWidth)
            // 悬停抬升只用绘制偏移，不能改变布局高度，否则会把下一行顶下去。
            .graphicsLayer { translationY = -3.dp.toPx() * lift }
            .clip(RoundedCornerShape(DesktopMetrics.cardRadius))
            .hoverable(interaction, enabled = enabled)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DesktopMetrics.posterHeight)
                .clip(RoundedCornerShape(DesktopMetrics.cardRadius))
                .background(AppColors.colors.surfaceRaised)
                .border(
                    width = 1.dp,
                    color = when {
                        focused -> AppColors.colors.accent
                        hovered && enabled -> AppColors.colors.accent.copy(alpha = 0.65f)
                        else -> AppColors.colors.border
                    },
                    shape = RoundedCornerShape(DesktopMetrics.cardRadius),
                ),
        ) {
            PosterImage(
                url = item.posterUrl,
                title = item.title,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.score > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC06100C))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = String.format("%.1f", item.score),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.colors.accent,
                    )
                }
            }
            if (resolving) {
                Box(
                    Modifier.fillMaxSize().background(Color(0xB3060A09)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = AppColors.colors.accent,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("正在查找资源", style = MaterialTheme.typography.labelMedium, color = AppColors.colors.textSecondary)
                    }
                }
            } else if (hovered && enabled) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC050807)),
                            ),
                        ),
                ) {
                    Text(
                        text = "查看详情",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = if (hovered && enabled) AppColors.colors.textPrimary else AppColors.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.subtitle.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun MovieCard(movie: Movie, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier = modifier
            .width(DesktopMetrics.posterWidth)
            .clip(RoundedCornerShape(DesktopMetrics.cardRadius))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DesktopMetrics.posterHeight)
                .clip(RoundedCornerShape(DesktopMetrics.cardRadius))
                .background(AppColors.colors.surfaceRaised)
                .border(
                    1.dp,
                    when {
                        focused -> AppColors.colors.accent
                        hovered -> AppColors.colors.accent.copy(alpha = 0.65f)
                        else -> AppColors.colors.border
                    },
                    RoundedCornerShape(DesktopMetrics.cardRadius),
                ),
        ) {
            PosterImage(
                url = movie.posterUrl,
                title = movie.name,
                modifier = Modifier.fillMaxSize(),
            )
            if (movie.remarks.isNotBlank()) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xCC06100C))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        movie.remarks,
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            movie.name,
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = movie.subtitle.ifBlank { movie.typeName },
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LoadingBox(text: String = "加载中…", modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = AppColors.colors.accent,
                strokeWidth = 2.5.dp,
            )
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = AppColors.colors.textTertiary)
        }
    }
}

@Composable
fun EmptyBox(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(AppColors.colors.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    icon()
                } else {
                    Text("∅", style = MaterialTheme.typography.titleLarge, color = AppColors.colors.textTertiary)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.colors.textSecondary)
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium, color = AppColors.colors.textTertiary)
            }
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                action()
            }
        }
    }
}

@Composable
fun ErrorBox(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    EmptyBox(
        title = "出了点问题",
        description = message,
        modifier = modifier,
        action = { if (onRetry != null) PrimaryButton("重试", onClick = onRetry) },
    )
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(AppColors.colors.border))
}
