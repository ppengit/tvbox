package com.tvbox.desktop.ui.theme

import com.tvbox.desktop.state.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Desktop design tokens. Keep values here so controls and pages share one rhythm. */
object DesktopSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val page = 28.dp
}

object DesktopShapes {
    val compact = 6.dp
    val control = 8.dp
    val card = 12.dp
    val panel = 16.dp
    val pill = 999.dp
}

object DesktopControls {
    val height = 34.dp
    val iconButtonSize = 34.dp
    val minimumTouchTarget = 34.dp
    val horizontalPadding = 16.dp
    val focusStroke = 2.dp
}

object DesktopFocus {
    val strokeWidth: Dp = DesktopControls.focusStroke
    const val ringAlpha = 0.9f
    const val disabledAlpha = 0.48f
}

object DesktopMotion {
    const val fast = 90
    const val standard = 160
    const val deliberate = 240
}

/**
 * 桌面端配色：延续电视端「影院主题」的深色底与薄荷绿强调色，但对比度和层级
 * 按桌面阅读距离重新调过，避免大屏电视的低对比在大窗体上发灰。
 */
data class DesktopColors(
    val background: Color,
    val sidebar: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceHover: Color,
    val surfaceActive: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentSoft: Color,
    val danger: Color,
    val warning: Color,
    val scrim: Color,
)

val DesktopDarkColors = DesktopColors(
    background = Color(0xFF080B0B),
    sidebar = Color(0xFF0C100F),
    surface = Color(0xFF101615),
    surfaceRaised = Color(0xFF171F1D),
    surfaceHover = Color(0xFF1E2825),
    surfaceActive = Color(0xFF22302B),
    border = Color(0xFF243029),
    borderStrong = Color(0xFF35443D),
    textPrimary = Color(0xFFF5F8F6),
    textSecondary = Color(0xFFA6B0AA),
    textTertiary = Color(0xFF7A857F),
    accent = Color(0xFF29E68C),
    accentSoft = Color(0x2229E68C),
    danger = Color(0xFFFF7171),
    warning = Color(0xFFFFC857),
    scrim = Color(0xCC050707),
)

val LocalDesktopColors = staticCompositionLocalOf { DesktopDarkColors }

object AppColors {
    val colors: DesktopColors
        @Composable get() = LocalDesktopColors.current
}

private val DesktopTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

private val DesktopColorScheme = darkColorScheme(
    primary = DesktopDarkColors.accent,
    onPrimary = Color(0xFF062015),
    primaryContainer = DesktopDarkColors.surfaceActive,
    onPrimaryContainer = DesktopDarkColors.textPrimary,
    secondary = DesktopDarkColors.textSecondary,
    onSecondary = DesktopDarkColors.background,
    background = DesktopDarkColors.background,
    onBackground = DesktopDarkColors.textPrimary,
    surface = DesktopDarkColors.surface,
    onSurface = DesktopDarkColors.textPrimary,
    surfaceVariant = DesktopDarkColors.surfaceRaised,
    onSurfaceVariant = DesktopDarkColors.textSecondary,
    outline = DesktopDarkColors.border,
    outlineVariant = DesktopDarkColors.border,
    error = DesktopDarkColors.danger,
)

object DesktopMetrics {
    val sidebarWidth = 208.dp
    val sidebarCollapsedWidth = 76.dp
    val topBarHeight = 64.dp
    val contentPadding = DesktopSpacing.page
    val cardRadius = DesktopShapes.card
    val posterWidth = 172.dp

    /** 海报统一 2:3，和豆瓣/资源站的真实封面比例一致，不再裁掉上下标题。 */
    val posterHeight = 258.dp
    val titleBarHeight = 38.dp

    /** 所有文字按钮统一 34dp 高，避免一行里出现高低不齐的控件。 */
    val buttonHeight = DesktopControls.height
}

@Composable
fun DesktopTheme(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalDesktopColors provides DesktopDarkColors,
    ) {
        MaterialTheme(
            colorScheme = DesktopColorScheme,
            typography = DesktopTypography,
            content = content,
        )
    }
}
