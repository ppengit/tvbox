package com.tvbox.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tvbox.desktop.state.DesktopAppState
import com.tvbox.desktop.state.DesktopScreen
import com.tvbox.desktop.state.Notice
import com.tvbox.desktop.state.NoticeType
import com.tvbox.desktop.ui.category.CategoryScreen
import com.tvbox.desktop.ui.components.GhostButton
import com.tvbox.desktop.ui.follow.FollowScreen
import com.tvbox.desktop.ui.detail.DetailScreen
import com.tvbox.desktop.ui.history.HistoryScreen
import com.tvbox.desktop.ui.home.HomeScreen
import com.tvbox.desktop.ui.search.SearchScreen
import com.tvbox.desktop.ui.shell.AppShell
import com.tvbox.desktop.ui.theme.AppColors
import com.tvbox.desktop.ui.theme.DesktopTheme
import kotlinx.coroutines.delay

@Composable
fun DesktopApp(
    state: DesktopAppState,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
) {
    val screen by state.screen.collectAsState()
    val home by state.home.collectAsState()
    val search by state.search.collectAsState()
    val detail by state.detail.collectAsState()
    val history by state.history.collectAsState()
    val player by state.player.collectAsState()
    val notice by state.notice.collectAsState()
    val category by state.category.collectAsState()
    val follow by state.follow.collectAsState()

    LaunchedEffect(Unit) { state.start() }

    // 全屏只属于播放页：任何方式离开播放页（返回按钮、错误页返回、播放结束退出）都要
    // 退出全屏，否则主界面会停在无标题栏的全屏状态，用户找不到退出入口。
    LaunchedEffect(player == null) {
        if (player == null && isFullscreen) onToggleFullscreen()
    }

    DesktopTheme {
        Box(Modifier.fillMaxSize().background(AppColors.colors.background)) {
            if (player != null) {
                com.tvbox.desktop.ui.player.PlayerScreen(
                    state = state,
                    playerState = player!!,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                )
            } else {
                AppShell(
                    current = screen,
                    onNavigate = state::navigate,
                    followUpdates = follow.items.count { it.hasNewEpisodes },
                ) {
                    if (detail != null) {
                        DetailScreen(state = state, detail = detail!!)
                    } else {
                        when (screen) {
                            DesktopScreen.Home -> HomeScreen(state, home, history)
                            DesktopScreen.Category -> CategoryScreen(state, category)
                            DesktopScreen.Search -> SearchScreen(state, search)
                            DesktopScreen.Follow -> FollowScreen(state, follow)
                            DesktopScreen.History -> HistoryScreen(state, history)
                            DesktopScreen.Live -> com.tvbox.desktop.ui.live.LiveScreen(state)
                            DesktopScreen.PlatformLive -> com.tvbox.desktop.ui.live.PlatformLiveScreen(state)
                            DesktopScreen.Settings -> com.tvbox.desktop.ui.settings.SettingsScreen(state)
                        }
                    }
                }
            }

            NoticeHost(notice, onDismiss = state::consumeNotice)
        }
    }
}

/**
 * 全局轻提示浮层：按类型配色，可带一个动作按钮（如「撤销」）。
 * 错误用 assertive liveRegion 立即播报，其余类型用 polite。
 */
@Composable
private fun NoticeHost(notice: Notice?, onDismiss: (Long) -> Unit) {
    if (notice == null) return
    LaunchedEffect(notice.id) {
        delay(notice.visibleDurationMs)
        onDismiss(notice.id)
    }
    val tone = when (notice.type) {
        NoticeType.Success -> AppColors.colors.accent
        NoticeType.Warning -> AppColors.colors.warning
        NoticeType.Error -> AppColors.colors.danger
        NoticeType.Info -> AppColors.colors.textSecondary
    }
    val glyph = when (notice.type) {
        NoticeType.Success -> "✓"
        NoticeType.Warning -> "!"
        NoticeType.Error -> "×"
        NoticeType.Info -> "i"
    }
    val action = notice.action
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.BottomEnd) {
        Row(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 440.dp)
                .clip(shape)
                .background(AppColors.colors.surfaceRaised)
                .border(1.dp, tone.copy(alpha = 0.45f), shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tone.copy(alpha = 0.16f))
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = glyph,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = tone,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    liveRegion = if (notice.type == NoticeType.Error) {
                        LiveRegionMode.Assertive
                    } else {
                        LiveRegionMode.Polite
                    }
                },
            ) {
                Text(
                    text = notice.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colors.textPrimary,
                )
            }
            if (action != null) {
                Spacer(Modifier.width(12.dp))
                GhostButton(
                    text = action.label,
                    onClick = {
                        action.onClick()
                        onDismiss(notice.id)
                    },
                )
            }
        }
    }
}
