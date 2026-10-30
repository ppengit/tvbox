package com.tvbox.desktop.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvbox.app.domain.HistoryFilter
import com.tvbox.app.domain.WatchHistoryItem
import com.tvbox.app.domain.filterHistory
import com.tvbox.app.domain.historyDateText
import com.tvbox.app.domain.historyKey
import com.tvbox.desktop.data.FollowedShow
import com.tvbox.desktop.state.DesktopAppState
import com.tvbox.desktop.state.HistoryState
import com.tvbox.desktop.state.*
import com.tvbox.desktop.ui.components.Chip
import com.tvbox.desktop.ui.components.DangerButton
import com.tvbox.desktop.ui.components.EmptyBox
import com.tvbox.desktop.ui.components.ErrorBox
import com.tvbox.desktop.ui.components.GhostButton
import com.tvbox.desktop.ui.components.LoadingBox
import com.tvbox.desktop.ui.components.MediaProgressBar
import com.tvbox.desktop.ui.components.PageHeader
import com.tvbox.desktop.ui.components.PosterImage
import com.tvbox.desktop.ui.components.PrimaryButton
import com.tvbox.desktop.ui.components.RemoveMediaButton
import com.tvbox.desktop.ui.components.StatusBadge
import com.tvbox.desktop.ui.components.StatusBadgeTone
import com.tvbox.desktop.ui.theme.AppColors

@Composable
fun HistoryScreen(
    state: DesktopAppState,
    history: HistoryState,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val follow by state.follow.collectAsState()
    var filter by remember { mutableStateOf(HistoryFilter.All) }
    var confirmClear by remember { mutableStateOf(false) }

    val followMap = remember(follow.items) { follow.items.associateBy { it.key } }
    val visible = remember(history.items, filter) { history.items.filterHistory(filter) }
    val newEpisodeCount = visible.count { item ->
        followMap["${item.apiLineId}:${item.movieId}"]?.hasNewEpisodes == true
    }

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 330.dp),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(bottom = 28.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    PageHeader(
                        title = "观看历史",
                        subtitle = when {
                            history.items.isEmpty() -> "播放过的影片会自动记录进度"
                            newEpisodeCount > 0 -> "共 ${history.items.size} 条记录，其中 $newEpisodeCount 部有新集"
                            else -> "共 ${history.items.size} 条记录，点击卡片继续播放"
                        },
                    ) {
                        if (history.items.isNotEmpty()) {
                            GhostButton(text = "清空历史", onClick = { confirmClear = true })
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HistoryFilter.entries.forEach { option ->
                            Chip(
                                text = "${option.displayName} ${
                                    if (option == HistoryFilter.All) history.items.size
                                    else history.items.filterHistory(option).size
                                }",
                                selected = filter == option,
                                onClick = { filter = option },
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            when {
                history.loading && history.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    LoadingBox("正在读取历史…")
                }

                history.error != null && history.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorBox(history.error) { state.loadHistory() }
                }

                history.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyBox(
                        title = "还没有观看记录",
                        description = "从首页或搜索进入详情开始播放，进度会自动保存在这里",
                    ) {
                        PrimaryButton("去首页看看", onClick = { state.navigate(com.tvbox.desktop.state.DesktopScreen.Home) })
                    }
                }

                visible.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyBox(
                        title = if (filter == HistoryFilter.Completed) "还没有看完的影片" else "没有观看记录符合筛选条件",
                        description = "换一个筛选条件看看",
                    )
                }

                else -> items(visible, key = { it.historyKey }) { item ->
                    HistoryCard(
                        item = item,
                        followed = followMap["${item.apiLineId}:${item.movieId}"],
                        resuming = history.resumingKey == item.historyKey,
                        enabled = history.resumingKey == null,
                        onResume = { state.resumeHistory(item) },
                        onRemove = { state.removeHistory(item) },
                    )
                }
            }
        }

        if (confirmClear) {
            ClearHistoryConfirmOverlay(
                count = history.items.size,
                onCancel = { confirmClear = false },
                onConfirm = {
                    confirmClear = false
                    state.clearHistory()
                },
            )
        }
    }
}

/**
 * 清空历史的页面内确认浮层。清空不可恢复，所以必须二次确认；
 * 外圈遮罩和「取消」等价，点面板本身不关闭。
 */
@Composable
private fun ClearHistoryConfirmOverlay(
    count: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.colors.scrim)
            .clickable(interactionSource = scrimInteraction, indication = null, onClick = onCancel)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(AppColors.colors.surfaceRaised)
                .border(1.dp, AppColors.colors.borderStrong, shape)
                .pointerInput(Unit) { detectTapGestures { } }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onCancel()
                        true
                    } else {
                        false
                    }
                }
                .padding(24.dp)
                .semantics { paneTitle = "确认清空观看历史" },
        ) {
            Text(
                text = "清空观看历史？",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.colors.textPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "将删除全部 $count 条观看记录，删除后不可恢复。",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colors.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GhostButton(text = "取消", modifier = Modifier.focusRequester(cancelFocus), onClick = onCancel)
                Spacer(Modifier.width(10.dp))
                DangerButton(text = "清空", onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun HistoryCard(
    item: WatchHistoryItem,
    followed: FollowedShow?,
    resuming: Boolean,
    enabled: Boolean,
    onResume: () -> Unit,
    onRemove: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hasNew = followed?.hasNewEpisodes == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered || resuming) AppColors.colors.surfaceHover else AppColors.colors.surface)
            .border(
                1.dp,
                when {
                    focused -> AppColors.colors.accent
                    hasNew -> AppColors.colors.accent.copy(alpha = 0.55f)
                    hovered || resuming -> AppColors.colors.borderStrong
                    else -> AppColors.colors.border
                },
                RoundedCornerShape(8.dp),
            )
            .hoverable(interaction, enabled = enabled)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onResume)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .width(76.dp)
                .height(114.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.colors.surfaceRaised),
        ) {
            PosterImage(item.posterUrl, item.movieName, Modifier.fillMaxSize())
            if (hovered && enabled) {
                Box(Modifier.fillMaxSize().background(Color(0xB3060A09)), contentAlignment = Alignment.Center) {
                    com.tvbox.desktop.ui.components.PlayIcon(color = AppColors.colors.accent, size = 26.dp)
                }
            }
        }

        Spacer(Modifier.width(13.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.movieName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (hasNew) {
                    Spacer(Modifier.width(6.dp))
                    StatusBadge(text = "新集", tone = StatusBadgeTone.Accent)
                }
            }

            Spacer(Modifier.height(5.dp))
            Text(
                text = episodeLine(item, followed),
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val status = statusLine(item, followed)
            if (status.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hasNew) AppColors.colors.accent else AppColors.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(9.dp))
            MediaProgressBar(progress = item.progressPercent.coerceIn(0, 100) / 100f)
            Spacer(Modifier.height(5.dp))
            Text(
                text = when {
                    item.positionMs <= 0L && item.durationMs <= 0L -> "尚未开始观看"
                    item.durationMs <= 0L -> "已看 ${formatDuration(item.positionMs)} · 总时长未知"
                    item.positionMs <= 0L -> "总时长 ${formatDuration(item.durationMs)}"
                    else -> "${formatDuration(item.positionMs)} / ${formatDuration(item.durationMs)} · 已看 ${item.progressPercent}%"
                },
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textTertiary,
            )

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.historyDateText().removePrefix("最近观看 "),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.colors.textTertiary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (resuming) {
                    Text("正在续播…", style = MaterialTheme.typography.labelMedium, color = AppColors.colors.accent)
                }
                RemoveMediaButton(onClick = onRemove, contentDescription = "移除历史记录")
            }
        }
    }
}

/** 第几集 / 共几集；电影类单集内容不显示集数。 */
private fun episodeLine(item: WatchHistoryItem, followed: FollowedShow?): String {
    val total = maxOf(item.episodeCount, followed?.lastEpisodeCount ?: 0)
    val parts = buildList {
        if (total > 1) add("第 ${item.episodeIndex + 1}/${total} 集")
        item.episodeTitle.takeIf { it.isNotBlank() && total <= 1 }?.let { add(it) }
        item.sourceName.takeIf { it.isNotBlank() }?.let { add("线路 $it") }
    }
    return parts.joinToString(" · ").ifBlank { item.remarks }
}

/** 更新状态：追剧中的剧优先展示已更新到第几集，否则回落到接口给的备注。 */
private fun statusLine(item: WatchHistoryItem, followed: FollowedShow?): String {
    if (followed == null) return item.remarks
    val latest = followed.latestEpisodeTitle.ifBlank { "第 ${followed.lastEpisodeCount} 集" }
    val updating = followed.lastEpisodeCount > 0 && item.remarks.contains("更新")
    return when {
        followed.hasNewEpisodes -> "追剧中 · 已更新至 $latest，可以接着看"
        updating -> "追剧中 · 已更新至 $latest"
        item.remarks.isNotBlank() -> item.remarks
        else -> "追剧中 · 共 ${followed.lastEpisodeCount} 集"
    }
}

internal fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "--:--"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}
