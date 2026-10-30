package com.tvbox.desktop.ui.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.tvbox.desktop.DesktopVersion
import com.tvbox.desktop.state.DesktopScreen
import com.tvbox.desktop.state.*
import com.tvbox.desktop.ui.components.BroadcastIcon
import com.tvbox.desktop.ui.components.FollowIcon
import com.tvbox.desktop.ui.components.LibraryIcon
import com.tvbox.desktop.ui.components.HistoryIcon
import com.tvbox.desktop.ui.components.HomeIcon
import com.tvbox.desktop.ui.components.LiveTvIcon
import com.tvbox.desktop.ui.components.SearchIcon
import com.tvbox.desktop.ui.components.SettingsIcon
import com.tvbox.desktop.ui.components.StatusBadge
import com.tvbox.desktop.ui.components.StatusBadgeTone
import com.tvbox.desktop.ui.theme.AppColors
import com.tvbox.desktop.ui.theme.DesktopFocus
import com.tvbox.desktop.ui.theme.DesktopMetrics
import com.tvbox.desktop.ui.theme.DesktopShapes
import javax.imageio.ImageIO

/** 宽屏侧栏。窄侧栏复用 [DesktopMetrics.sidebarCollapsedWidth] 的图标栏宽度。 */
private val SidebarWideWidth = 232.dp

/** 三档窗口宽度：宽屏常驻文字侧栏，中/紧凑都收成图标栏，紧凑档再收紧内容留白。 */
private enum class ShellLayout { Wide, Medium, Compact }

/**
 * 桌面端主框架：左侧导航 + 右侧内容区。
 * 导航常驻可见，不做电视端的焦点驱动折叠；窗口变窄时只保留图标栏，
 * 而不是覆盖式抽屉——窗口最小宽度就是 1120dp，抽屉会和窗口约束互相打架。
 */
@Composable
fun AppShell(
    current: DesktopScreen,
    onNavigate: (DesktopScreen) -> Unit,
    followUpdates: Int = 0,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.colors.background)) {
        val layout = when {
            maxWidth >= 1440.dp -> ShellLayout.Wide
            maxWidth >= 1120.dp -> ShellLayout.Medium
            else -> ShellLayout.Compact
        }
        val contentPadding = if (layout == ShellLayout.Compact) 18.dp else DesktopMetrics.contentPadding
        Row(Modifier.fillMaxSize()) {
            AppSidebar(
                current = current,
                onNavigate = onNavigate,
                followUpdates = followUpdates,
                expanded = layout == ShellLayout.Wide,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = contentPadding,
                        end = contentPadding,
                        top = 20.dp,
                        bottom = 0.dp,
                    ),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun AppSidebar(
    current: DesktopScreen,
    onNavigate: (DesktopScreen) -> Unit,
    followUpdates: Int,
    expanded: Boolean,
) {
    Column(
        modifier = Modifier
            .width(if (expanded) SidebarWideWidth else DesktopMetrics.sidebarCollapsedWidth)
            .fillMaxHeight()
            .background(AppColors.colors.sidebar)
            .padding(horizontal = if (expanded) 14.dp else 10.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandBlock(expanded)
        Spacer(Modifier.height(if (expanded) 26.dp else 22.dp))
        DesktopScreen.entries.forEach { screen ->
            NavItem(
                screen = screen,
                selected = screen == current,
                badge = if (screen == DesktopScreen.Follow) followUpdates else 0,
                expanded = expanded,
                onClick = { onNavigate(screen) },
            )
            Spacer(Modifier.height(if (expanded) 4.dp else 6.dp))
        }
        Spacer(Modifier.weight(1f))
        SidebarFooter(expanded)
    }
}

@Composable
private fun BrandBlock(expanded: Boolean) {
    val logo = rememberBrandLogo()
    if (!expanded) {
        Box(
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = "小鸡影视" },
            contentAlignment = Alignment.Center,
        ) {
            BrandLogo(logo)
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandLogo(logo)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "小鸡影视",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppColors.colors.textPrimary,
            )
            Text(
                "Windows",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun BrandLogo(logo: ImageBitmap?) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.colors.surfaceRaised)
            .border(1.dp, AppColors.colors.border, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(logo, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Text("鸡", style = MaterialTheme.typography.titleLarge, color = AppColors.colors.accent)
        }
    }
}

@Composable
private fun NavItem(
    screen: DesktopScreen,
    selected: Boolean,
    badge: Int = 0,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    if (expanded) {
        ExpandedNavItem(screen, selected, badge, onClick)
    } else {
        RailNavItem(screen, selected, badge, onClick)
    }
}

@Composable
private fun ExpandedNavItem(screen: DesktopScreen, selected: Boolean, badge: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(DesktopShapes.control)
    val background = when {
        selected -> AppColors.colors.surfaceActive
        hovered || focused -> AppColors.colors.surfaceHover
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val contentColor = when {
        selected -> AppColors.colors.accent
        hovered || focused -> AppColors.colors.textPrimary
        else -> AppColors.colors.textSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(
                DesktopFocus.strokeWidth,
                if (focused) AppColors.colors.accent else androidx.compose.ui.graphics.Color.Transparent,
                shape,
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScreenIcon(screen, contentColor)
        Spacer(Modifier.width(11.dp))
        Text(
            text = screen.title,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (badge > 0) {
            Spacer(Modifier.weight(1f))
            StatusBadge(
                text = badge.toString(),
                tone = StatusBadgeTone.Accent,
            )
        }
    }
}

/**
 * 图标栏导航项：不渲染文字，用 tooltip 补充视觉提示，用 contentDescription 保留可访问名称，
 * selected 语义与宽侧栏一致，读屏仍能听出当前页。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailNavItem(screen: DesktopScreen, selected: Boolean, badge: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(DesktopShapes.control)
    val background = when {
        selected -> AppColors.colors.surfaceActive
        hovered || focused -> AppColors.colors.surfaceHover
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val contentColor = when {
        selected -> AppColors.colors.accent
        hovered || focused -> AppColors.colors.textPrimary
        else -> AppColors.colors.textSecondary
    }
    val accessibleName = if (badge > 0) "${screen.title}，$badge 项更新" else screen.title

    TooltipArea(
        tooltip = {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.colors.surfaceRaised)
                    .border(1.dp, AppColors.colors.border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = accessibleName,
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.colors.textSecondary,
                )
            }
        },
        delayMillis = 350,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(14.dp, 6.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(shape)
                .background(background)
                .border(
                    DesktopFocus.strokeWidth,
                    if (focused) AppColors.colors.accent else androidx.compose.ui.graphics.Color.Transparent,
                    shape,
                )
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
                .semantics {
                    this.selected = selected
                    contentDescription = accessibleName
                },
            contentAlignment = Alignment.Center,
        ) {
            ScreenIcon(screen, contentColor, size = 22.dp)
            if (badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-9).dp, y = 9.dp)
                        .size(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.colors.accent),
                )
            }
        }
    }
}

@Composable
private fun ScreenIcon(
    screen: DesktopScreen,
    color: androidx.compose.ui.graphics.Color,
    size: Dp = 20.dp,
) {
    when (screen) {
        DesktopScreen.Home -> HomeIcon(color, size)
        DesktopScreen.Category -> LibraryIcon(color, size)
        DesktopScreen.Search -> SearchIcon(color, size)
        DesktopScreen.Follow -> FollowIcon(color, size)
        DesktopScreen.History -> HistoryIcon(color, size)
        DesktopScreen.Live -> LiveTvIcon(color, size)
        DesktopScreen.PlatformLive -> BroadcastIcon(color, size)
        DesktopScreen.Settings -> SettingsIcon(color, size)
    }
}

@Composable
private fun SidebarFooter(expanded: Boolean) {
    if (!expanded) {
        Box(
            Modifier.fillMaxWidth().semantics { contentDescription = "小鸡影视桌面版 ${DesktopVersion.name}" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(AppColors.colors.accent))
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(AppColors.colors.accent))
            Spacer(Modifier.width(8.dp))
            Text(
                "桌面版 ${DesktopVersion.name}",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun rememberBrandLogo(): ImageBitmap? = remember {
    runCatching {
        val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream("brand/icon.png")
            ?: return@runCatching null
        stream.use { ImageIO.read(it)?.toComposeImageBitmap() }
    }.getOrNull()
}

@Composable
fun ScreenScaffold(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp)) { header() }
        content(androidx.compose.foundation.layout.PaddingValues(0.dp))
    }
}

@Composable
fun BackButton(
    text: String = "返回",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(DesktopShapes.control)
    val contentColor = when {
        !enabled -> AppColors.colors.textTertiary
        hovered || focused -> AppColors.colors.textPrimary
        else -> AppColors.colors.textSecondary
    }
    Row(
        modifier = modifier
            .clip(shape)
            .background(if ((hovered || focused) && enabled) AppColors.colors.surfaceHover else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                DesktopFocus.strokeWidth,
                if (focused) AppColors.colors.accent else androidx.compose.ui.graphics.Color.Transparent,
                shape,
            )
            .hoverable(interaction, enabled = enabled)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        com.tvbox.desktop.ui.components.ArrowLeftIcon(
            color = contentColor,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}
