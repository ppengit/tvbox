package com.tvbox.desktop.ui.follow

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvbox.desktop.data.FollowedShow
import com.tvbox.desktop.state.DesktopAppState
import com.tvbox.desktop.state.FollowState
import com.tvbox.desktop.state.*
import com.tvbox.desktop.ui.components.EmptyBox
import com.tvbox.desktop.ui.components.FollowIcon
import com.tvbox.desktop.ui.components.GhostButton
import com.tvbox.desktop.ui.components.InlineError
import com.tvbox.desktop.ui.components.PageHeader
import com.tvbox.desktop.ui.components.PosterImage
import com.tvbox.desktop.ui.components.PrimaryButton
import com.tvbox.desktop.ui.components.RemoveMediaButton
import com.tvbox.desktop.ui.components.StatusBadge
import com.tvbox.desktop.ui.components.StatusBadgeTone
import com.tvbox.desktop.ui.theme.AppColors

/**
 * 追剧列表：关注过的剧集集中在这里，检测到新集的时候整行高亮并打「新集」标。
 */
@Composable
fun FollowScreen(
    state: DesktopAppState,
    follow: FollowState,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PageHeader(
            title = "追剧",
            subtitle = buildString {
                append("共 ${follow.items.size} 部")
                if (follow.items.any { it.hasNewEpisodes }) {
                    append(" · ${follow.items.count { it.hasNewEpisodes }} 部有新集")
                }
                append(" · ").append(followCheckedLabel(follow.lastCheckedAtMs))
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (follow.checking) {
                    Text(
                        "检查中…",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.colors.textTertiary,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                GhostButton(
                    text = "检查更新",
                    enabled = !follow.checking && follow.items.isNotEmpty(),
                    onClick = { state.refreshFollows(force = true) },
                )
            }
        }

        follow.error?.let { error ->
            InlineError(
                message = error,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                onRetry = if (follow.items.isNotEmpty()) ({ state.refreshFollows(force = true) }) else null,
            )
        }

        if (follow.items.isEmpty()) {
            EmptyBox(
                title = "还没有追的剧",
                description = "在详情页点「追剧」，有新集时会在这里提醒你",
                modifier = Modifier.fillMaxSize(),
                icon = { FollowIcon(color = AppColors.colors.textTertiary, size = 20.dp) },
            )
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(follow.items, key = { it.key }) { item ->
                FollowRow(
                    item = item,
                    onClick = { state.openFollow(item) },
                    onRemove = { state.removeFollow(item) },
                )
            }
        }
    }
}

@Composable
private fun FollowRow(item: FollowedShow, onClick: () -> Unit, onRemove: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        focused -> AppColors.colors.accent
        item.hasNewEpisodes -> AppColors.colors.accent.copy(alpha = 0.55f)
        hovered -> AppColors.colors.borderStrong
        else -> AppColors.colors.border
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) AppColors.colors.surfaceHover else AppColors.colors.surface)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(56.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(AppColors.colors.surfaceRaised),
        ) {
            PosterImage(item.posterUrl, item.name, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.hasNewEpisodes) {
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(text = "新集", tone = StatusBadgeTone.Accent)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOf(item.year, item.area).filter { it.isNotBlank() }.joinToString(" / ")
                    .ifBlank { item.apiLineId },
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textTertiary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildString {
                    if (item.displayEpisodeCount > 0) {
                        append("更新至 ${item.displayEpisodeCount} 集")
                    } else {
                        append("集数未知")
                    }
                    if (item.latestEpisodeTitle.isNotBlank()) append(" · ${item.latestEpisodeTitle}")
                    if (item.lastEpisodeIndex > 0) append(" · 看到第 ${item.lastEpisodeIndex + 1} 集")
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (item.hasNewEpisodes) AppColors.colors.accent else AppColors.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.lastPositionMs > 0L) {
                Spacer(Modifier.height(8.dp))
                StatusBadge(text = "第 ${item.lastEpisodeIndex + 1} 集已有观看进度")
            }
        }
        Spacer(Modifier.width(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(
                text = if (item.lastPositionMs > 0L || item.lastEpisodeIndex > 0) "继续看" else "播放",
                onClick = onClick,
            )
            Spacer(Modifier.width(8.dp))
            RemoveMediaButton(onClick = onRemove, contentDescription = "取消追剧")
        }
    }
}
