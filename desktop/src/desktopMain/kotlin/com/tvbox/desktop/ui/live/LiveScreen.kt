package com.tvbox.desktop.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvbox.app.domain.LiveChannel
import com.tvbox.desktop.state.DesktopAppState
import com.tvbox.desktop.state.LiveState
import com.tvbox.desktop.state.*
import com.tvbox.desktop.ui.components.EmptyBox
import com.tvbox.desktop.ui.components.ErrorBox
import com.tvbox.desktop.ui.components.GhostButton
import com.tvbox.desktop.ui.components.LoadingBox
import com.tvbox.desktop.ui.components.PageHeader
import com.tvbox.desktop.ui.theme.AppColors

@Composable
fun LiveScreen(state: DesktopAppState, modifier: Modifier = Modifier) {
    val live by state.live.collectAsState()
    LiveContent(state, live, modifier)
}

@Composable
private fun LiveContent(state: DesktopAppState, live: LiveState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        PageHeader(
            title = "电视直播",
            subtitle = if (live.channels.isEmpty()) "央视、卫视与地方频道" else "共 ${live.channels.size} 个频道 · ${live.groups.size} 个分组",
        ) {
            GhostButton(text = "刷新频道", onClick = { state.refreshLiveGroupsIfNeeded(force = true) })
        }
        Spacer(Modifier.height(18.dp))

        when {
            live.loading && live.channels.isEmpty() -> LoadingBox("正在加载直播源…", Modifier.fillMaxSize())
            live.error != null && live.channels.isEmpty() -> ErrorBox(
                message = live.error,
                modifier = Modifier.fillMaxSize(),
                onRetry = { state.refreshLiveGroupsIfNeeded(force = true) },
            )
            live.channels.isEmpty() -> EmptyBox(
                title = "直播源没有返回频道",
                description = "稍后重试，或检查网络是否可访问直播源地址",
                modifier = Modifier.fillMaxSize(),
            )
            else -> Row(Modifier.fillMaxSize().padding(bottom = 24.dp)) {
                GroupList(
                    groups = live.groups,
                    selected = live.selectedGroup,
                    onSelect = state::selectLiveGroup,
                )
                Spacer(Modifier.width(22.dp))
                ChannelGrid(
                    channels = live.channels.filter { it.groupName == live.selectedGroup },
                    onPlay = state::openLiveChannel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GroupList(groups: List<String>, selected: String?, onSelect: (String) -> Unit) {
    Column(
        Modifier
            .width(196.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.colors.surface)
            .border(1.dp, AppColors.colors.border, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp),
    ) {
        LazyColumn(Modifier.fillMaxHeight()) {
            items(groups, key = { it }) { group ->
                val isSelected = group == selected
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            when {
                                isSelected -> AppColors.colors.surfaceActive
                                hovered -> AppColors.colors.surfaceHover
                                else -> Color.Transparent
                            },
                        )
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(group) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSelected) {
                        Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(AppColors.colors.accent))
                        Spacer(Modifier.width(9.dp))
                    }
                    Text(
                        text = group,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) AppColors.colors.accent else AppColors.colors.textSecondary,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelGrid(
    channels: List<LiveChannel>,
    onPlay: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 148.dp),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "共 ${channels.size} 个频道，点击立即播放",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textTertiary,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        items(channels, key = { "${it.number}-${it.name}" }) { channel ->
            ChannelCard(channel, onClick = { onPlay(channel) })
        }
    }
}

@Composable
private fun ChannelCard(channel: LiveChannel, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val hasLine = channel.lines.isNotEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (hovered) AppColors.colors.surfaceHover else AppColors.colors.surface)
            .border(
                1.dp,
                if (hovered) AppColors.colors.accent.copy(alpha = 0.5f) else AppColors.colors.border,
                RoundedCornerShape(9.dp),
            )
            .hoverable(interaction)
            .clickable(enabled = hasLine, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = channel.name,
            style = MaterialTheme.typography.titleMedium,
            color = if (hasLine) AppColors.colors.textPrimary else AppColors.colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (hasLine) "${channel.lines.size} 条线路" else "暂无线路",
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colors.textTertiary,
        )
    }
}
