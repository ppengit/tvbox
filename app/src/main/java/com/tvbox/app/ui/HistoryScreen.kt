package com.tvbox.app.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.tvbox.app.domain.HistoryFilter
import com.tvbox.app.domain.WatchHistoryItem
import com.tvbox.app.domain.filterHistory
import com.tvbox.app.domain.historyDateText
import com.tvbox.app.domain.historyFocusKey
import com.tvbox.app.domain.historyKey
import com.tvbox.app.domain.historyProgressText
import com.tvbox.app.domain.isCompleted
import com.tvbox.app.ui.components.PageSurface
import com.tvbox.app.ui.components.tvFocusScale
import com.tvbox.app.ui.theme.TvLayout
import kotlinx.coroutines.launch

@Composable
internal fun HistoryScreen(state: TvBoxUiState, actions: TvBoxViewModel) {
    var filterName by rememberSaveable { mutableStateOf(HistoryFilter.All.name) }
    val filter = HistoryFilter.entries.firstOrNull { it.name == filterName } ?: HistoryFilter.All
    val records = remember(state.historyItems) { state.historyItems.filterHistory(HistoryFilter.All) }
    val visible = remember(records, filter) { records.filterHistory(filter) }
    val visibleKeys = visible.map { it.historyKey }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val cardFocus = remember { mutableMapOf<String, FocusRequester>() }
    visibleKeys.forEach { cardFocus.getOrPut(it) { FocusRequester() } }
    val filterFocus = remember { HistoryFilter.entries.associateWith { FocusRequester() } }
    val returnFocus = remember { FocusRequester() }
    val clearFocus = remember { FocusRequester() }
    var lastFocusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastFocusedIndex by rememberSaveable { mutableIntStateOf(0) }
    var restoreNonce by remember { mutableIntStateOf(0) }
    var restoredNonce by remember { mutableIntStateOf(-1) }
    var restoreHeader by remember { mutableStateOf<String?>(null) }
    var menuKey by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingClear by remember { mutableStateOf(false) }
    val menuItem = records.firstOrNull { it.historyKey == menuKey }

    fun restoreCard() {
        menuKey = null
        confirmRemove = false
        restoreHeader = null
        restoreNonce++
    }

    // Re-focus only after a navigation/dialog/mutation, never on each progress/list update.
    LaunchedEffect(restoreNonce, visibleKeys, menuKey, confirmClear) {
        if (restoredNonce == restoreNonce || menuKey != null || confirmClear) return@LaunchedEffect
        val targetKey = if (restoreHeader == null) visible.historyFocusKey(lastFocusedKey, lastFocusedIndex) else null
        val targetIndex = visible.indexOfFirst { it.historyKey == targetKey }
        if (targetIndex >= 0 && grid.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }) {
            grid.scrollToItem(targetIndex)
        }
        for (attempt in 0..45) {
            withFrameNanos { }
            if (restoreHeader == null && targetIndex >= 0 &&
                grid.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }
            ) {
                continue
            }
            val requester = when {
                targetKey != null -> cardFocus[targetKey]
                restoreHeader == "clear" && (records.isNotEmpty() || state.historyError != null) -> clearFocus
                else -> returnFocus
            }
            if (runCatching { requester?.requestFocus() ?: false }.getOrDefault(false)) {
                restoredNonce = restoreNonce
                break
            }
        }
    }
    LaunchedEffect(visibleKeys) { cardFocus.keys.retainAll(visibleKeys.toSet()) }

    // Read the latest VM state as well: a very fast write may be conflated by UI collection.
    LaunchedEffect(state.historyUpdating, state.historyItems, state.historyError, pendingRemoval, pendingClear) {
        val latest = actions.state.value
        if (latest.historyUpdating) return@LaunchedEffect
        val removedKey = pendingRemoval
        if (removedKey != null) {
            if (latest.historyItems.none { it.historyKey == removedKey }) {
                pendingRemoval = null
                lastFocusedKey = visible.historyFocusKey(removedKey, lastFocusedIndex)
                if (visible.isEmpty()) restoreHeader = "return" else restoreHeader = null
                menuKey = null
                confirmRemove = false
                restoreNonce++
            } else if (latest.historyError != null) {
                pendingRemoval = null
            }
        }
        if (pendingClear) {
            if (latest.historyItems.isEmpty() && latest.historyError == null) {
                pendingClear = false
                confirmClear = false
                lastFocusedKey = null
                lastFocusedIndex = 0
                restoreHeader = "return"
                restoreNonce++
            } else if (latest.historyError != null) {
                pendingClear = false
            }
        }
    }
    LaunchedEffect(menuKey, records, pendingRemoval) {
        if (menuKey != null && menuItem == null && pendingRemoval == null) restoreCard()
    }

    PageSurface { padding ->
        val firstCardFocus = visibleKeys.firstOrNull()?.let(cardFocus::get)
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("观看历史", style = MaterialTheme.typography.headlineLarge)
                    Text("${records.size} 条记录 · 确认直接续播，菜单键 / 长按查看更多操作",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HistoryButton("找片", actions::openSearch, Modifier.focusProperties {
                    if (firstCardFocus != null) down = firstCardFocus
                })
                HistoryButton("清空历史", { confirmClear = true }, Modifier.focusRequester(clearFocus).focusProperties {
                    if (firstCardFocus != null) down = firstCardFocus
                }, enabled = !state.historyUpdating && (records.isNotEmpty() || state.historyError != null))
                HistoryButton("返回", { actions.goBack() }, Modifier.focusRequester(returnFocus).focusProperties {
                    if (firstCardFocus != null) down = firstCardFocus
                })
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HistoryFilter.entries.forEach { option ->
                    val count = when (option) {
                        HistoryFilter.All -> records.size
                        HistoryFilter.Unfinished -> records.count { !it.isCompleted }
                        HistoryFilter.Completed -> records.count { it.isCompleted }
                    }
                    FilterChip(
                        selected = filter == option,
                        onClick = {
                            filterName = option.name
                            if (records.isNotEmpty()) scope.launch { grid.scrollToItem(0) }
                        },
                        label = { Text("${option.displayName} ($count)") },
                        modifier = Modifier.heightIn(min = 48.dp).focusRequester(filterFocus.getValue(option))
                            .tvFocusScale(RoundedCornerShape(12.dp)).focusProperties {
                                if (firstCardFocus != null) down = firstCardFocus
                            }.testTag("history_filter_${option.name}"),
                    )
                }
                Text("已看完指本集进度达到 95%", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.historyError?.let { error ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = actions::retryCurrent, enabled = !state.historyUpdating) { Text("重试") }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (visible.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text(if (records.isEmpty()) "暂无观看历史" else "暂无${filter.displayName}记录",
                        style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(if (records.isEmpty()) "播放影片后会保留最近一集的进度" else "可以切换筛选，或找一部影片继续观看",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HistoryButton("找片", actions::openSearch)
                        HistoryButton("返回", { actions.goBack() })
                    }
                }
            } else {
                val minWidth = TvLayout.HistoryGridMinWidth
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val columns = ((maxWidth.value + 18f) / (minWidth.value + 18f)).toInt().coerceAtLeast(1)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minWidth), state = grid,
                        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize().testTag("history_grid"),
                    ) {
                        itemsIndexed(visible, key = { _, item -> item.historyKey }) { index, item ->
                            HistoryPosterCard(
                                item = item,
                                onContinue = {
                                    lastFocusedKey = item.historyKey
                                    lastFocusedIndex = index
                                    actions.resumeHistory(item, TvScreen.History)
                                },
                                onActions = {
                                    lastFocusedKey = item.historyKey
                                    lastFocusedIndex = index
                                    confirmRemove = false
                                    menuKey = item.historyKey
                                },
                                modifier = Modifier.focusRequester(cardFocus.getValue(item.historyKey))
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            lastFocusedKey = item.historyKey
                                            lastFocusedIndex = index
                                        }
                                    }.focusProperties {
                                        if (index < columns) up = filterFocus.getValue(filter)
                                    },
                            )
                        }
                    }
                }
            }
        }
    }

    menuItem?.let { item ->
        HistoryActionsDialog(
            item = item, confirmingRemove = confirmRemove,
            busy = state.historyUpdating || pendingRemoval != null,
            error = state.historyError,
            onContinue = { menuKey = null; actions.resumeHistory(item, TvScreen.History) },
            onRestart = { menuKey = null; actions.restartHistory(item, TvScreen.History) },
            onDetail = { menuKey = null; actions.openHistoryDetail(item) },
            onRemovePrompt = { confirmRemove = true },
            onRemoveConfirm = { actions.removeHistory(item); pendingRemoval = item.historyKey },
            onCancelRemove = { confirmRemove = false },
            onBack = ::restoreCard,
        )
    }
    if (confirmClear) {
        HistoryClearDialog(
            busy = state.historyUpdating || pendingClear, error = state.historyError,
            onClear = { actions.clearHistory(); pendingClear = true },
            onCancel = {
                confirmClear = false
                restoreHeader = "clear"
                restoreNonce++
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryPosterCard(item: WatchHistoryItem, onContinue: () -> Unit, onActions: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.tvFocusScale(shape).clip(shape).background(MaterialTheme.colorScheme.surface)
        .onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.keyCode != AndroidKeyEvent.KEYCODE_MENU) false else {
                if (event.type == KeyEventType.KeyUp) onActions()
                true
            }
        }.combinedClickable(role = Role.Button, onClickLabel = "继续观看", onClick = onContinue,
            onLongClickLabel = "打开历史操作", onLongClick = onActions).testTag("history_card_${item.historyKey}")) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (item.posterUrl.isNotBlank()) {
                AsyncImage(model = item.posterUrl, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("暂无海报", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item.episodeTitle.ifBlank { "第 ${item.episodeIndex + 1} 集" },
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0xC0000000)).padding(10.dp),
                color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.durationMs > 0L) {
            LinearProgressIndicator(progress = { item.progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(item.movieName.ifBlank { "未命名影片" }, style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(historyLineText(item), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.historyProgressText(), style = MaterialTheme.typography.bodyMedium)
            Text(if (item.isCompleted) "本集已看完" else "本集未看完", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text(item.historyDateText(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryActionsDialog(
    item: WatchHistoryItem,
    confirmingRemove: Boolean,
    busy: Boolean,
    error: String?,
    onContinue: () -> Unit,
    onRestart: () -> Unit,
    onDetail: () -> Unit,
    onRemovePrompt: () -> Unit,
    onRemoveConfirm: () -> Unit,
    onCancelRemove: () -> Unit,
    onBack: () -> Unit,
) {
    val safeFocus = remember(confirmingRemove) { FocusRequester() }
    LaunchedEffect(confirmingRemove, busy) {
        if (!busy) for (attempt in 0..12) {
            withFrameNanos { }
            if (runCatching { safeFocus.requestFocus() }.getOrDefault(false)) break
        }
    }
    Dialog(
        onDismissRequest = { if (!busy) { if (confirmingRemove) onCancelRemove() else onBack() } },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
    ) {
        Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth(0.9f), shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (confirmingRemove) "移除这条记录？" else "历史操作", style = MaterialTheme.typography.headlineSmall)
                Text(item.movieName, style = MaterialTheme.typography.titleMedium)
                Text("${item.episodeTitle} · ${historyLineText(item)}", style = MaterialTheme.typography.bodyMedium)
                Text(item.historyProgressText(), style = MaterialTheme.typography.bodyMedium)
                Text(item.historyDateText(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (confirmingRemove) {
                    Text("只移除此影视接口的影片记录，不影响其它影片或其它接口的同名影片。",
                        style = MaterialTheme.typography.bodyMedium)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    HistoryButton("保留记录", onCancelRemove, Modifier.fillMaxWidth().focusRequester(safeFocus), enabled = !busy)
                    HistoryButton(if (busy) "正在移除…" else "移除记录", onRemoveConfirm,
                        Modifier.fillMaxWidth().testTag("history_remove_confirm"), enabled = !busy)
                } else {
                    HistoryButton("继续观看", onContinue, Modifier.fillMaxWidth(), enabled = !busy)
                    HistoryButton("从本集开头播放", onRestart, Modifier.fillMaxWidth(), enabled = !busy)
                    HistoryButton("查看详情", onDetail, Modifier.fillMaxWidth(), enabled = !busy)
                    HistoryButton("移除记录", onRemovePrompt, Modifier.fillMaxWidth(), enabled = !busy)
                    HistoryButton("返回", onBack, Modifier.fillMaxWidth().focusRequester(safeFocus), enabled = !busy)
                }
            }
        }
    }
}

@Composable
private fun HistoryClearDialog(busy: Boolean, error: String?, onClear: () -> Unit, onCancel: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(busy) {
        if (!busy) for (attempt in 0..12) {
            withFrameNanos { }
            if (runCatching { cancelFocus.requestFocus() }.getOrDefault(false)) break
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text("清空全部历史？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("所有接口的观看记录都会移除，之后无法从历史续播。此操作不会自动执行。")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onClear, enabled = !busy) { Text(if (busy) "正在清空…" else "清空") } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.focusRequester(cancelFocus)) { Text("取消") } },
    )
}

@Composable
private fun HistoryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).tvFocusScale(RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)) {
        Text(text, maxLines = 1)
    }
}

private fun historyLineText(item: WatchHistoryItem): String =
    "${item.apiLineName.ifBlank { item.apiLineId }} · ${item.sourceName.ifBlank { "播放线路未知" }}"
