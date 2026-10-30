package com.tvbox.desktop.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tvbox.desktop.state.DesktopAppState
import com.tvbox.desktop.state.SearchState
import com.tvbox.desktop.state.*
import com.tvbox.desktop.ui.components.Chip
import com.tvbox.desktop.ui.components.EmptyBox
import com.tvbox.desktop.ui.components.ErrorBox
import com.tvbox.desktop.ui.components.LoadingBox
import com.tvbox.desktop.ui.components.MovieCard
import com.tvbox.desktop.ui.components.PageHeader
import com.tvbox.desktop.ui.components.SearchIcon
import com.tvbox.desktop.ui.theme.AppColors
import com.tvbox.desktop.ui.theme.DesktopMetrics

private val SUGGESTIONS = listOf("庆余年", "漫长的季节", "三体", "狂飙", "繁花", "琅琊榜")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    state: DesktopAppState,
    search: SearchState,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val searchHistory by state.searchHistory.collectAsState()

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = DesktopMetrics.posterWidth),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = modifier.fillMaxSize().padding(bottom = 28.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                PageHeader(
                    title = "搜索",
                    subtitle = "关键词会在当前影视线路的多条数据源上并行搜索",
                )
                Spacer(Modifier.height(16.dp))
                SearchField(
                    value = search.query,
                    loading = search.loading,
                    focusRequester = focusRequester,
                    onValueChange = state::onSearchQueryChanged,
                    onClear = { state.onSearchQueryChanged("") },
                    onSearch = { state.search() },
                )
                Spacer(Modifier.height(12.dp))
                if (searchHistory.isNotEmpty() && !search.searched) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "最近搜索",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.colors.textTertiary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            searchHistory.take(8).forEach { keyword ->
                                Chip(text = keyword, selected = false, onClick = {
                                    state.onSearchQueryChanged(keyword)
                                    state.search(keyword)
                                })
                            }
                        }
                        Text(
                            text = "清空",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.colors.textTertiary,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { state.clearSearchHistory() }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "试试：",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.colors.textTertiary,
                        modifier = Modifier.padding(vertical = 7.dp),
                    )
                    SUGGESTIONS.forEach { keyword ->
                        Chip(text = keyword, selected = false, onClick = {
                            state.onSearchQueryChanged(keyword)
                            state.search(keyword)
                        })
                    }
                }
                if (search.searched) {
                    Spacer(Modifier.height(16.dp))
                    SearchResultToolbar(search = search, onRetry = { state.search(search.submittedQuery) })
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        when {
            search.loading && search.results.isEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }) { LoadingBox("正在搜索…") }
            }

            search.error != null && search.results.isEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorBox(search.error) { state.search() }
                }
            }

            !search.searched -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyBox(
                        title = "输入片名开始搜索",
                        description = "支持剧名、电影名，也可以直接点上面的示例关键词",
                        icon = { SearchIcon(color = AppColors.colors.textTertiary, size = 20.dp) },
                    )
                }
            }

            search.results.isEmpty() && !search.loading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyBox(
                        title = "没有找到「${search.submittedQuery}」",
                        description = "换个关键词，或在设置里切换其他影视线路",
                        icon = { SearchIcon(color = AppColors.colors.textTertiary, size = 20.dp) },
                    )
                }
            }

            else -> {
                items(search.results, key = { "${it.apiLineId}-${it.id}" }) { movie ->
                    MovieCard(movie = movie, onClick = { state.openDetail(movie) })
                }
            }
        }
    }
}

@Composable
private fun SearchResultToolbar(search: SearchState, onRetry: () -> Unit) {
    val total = search.totalSources.coerceAtLeast(search.completedSources)
    val finished = total > 0 && search.completedSources >= total
    val partialFailure = search.error != null && search.results.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.colors.surface.copy(alpha = 0.55f))
            .border(1.dp, AppColors.colors.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "“${search.submittedQuery}” · ${search.results.size} 个结果",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = when {
                    partialFailure -> "部分数据源搜索失败，已保留可用结果"
                    search.loading && total > 0 -> "正在搜索数据源 ${search.completedSources} / $total"
                    search.loading -> "正在连接数据源…"
                    finished -> "已完成 $total 条数据源"
                    search.error != null -> search.error
                    else -> "搜索已完成"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (partialFailure || search.error != null) AppColors.colors.warning else AppColors.colors.textTertiary,
            )
        }
        if (search.loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = AppColors.colors.accent,
                strokeWidth = 2.dp,
            )
        } else if (partialFailure) {
            Text(
                text = "重试",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.accent,
                modifier = Modifier.clickable(onClick = onRetry).padding(8.dp),
            )
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    loading: Boolean,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.colors.surface)
            .border(1.dp, AppColors.colors.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchIcon(color = AppColors.colors.textTertiary, size = 18.dp)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = AppColors.colors.textPrimary),
                cursorBrush = SolidColor(AppColors.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
            if (value.isEmpty()) {
                Text(
                    "搜索影片、剧集…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.colors.textTertiary,
                )
            }
        }
        if (value.isNotEmpty()) {
            Text(
                text = "清除",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = AppColors.colors.accent,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "搜索中…",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.accent,
            )
        } else {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (value.isBlank()) AppColors.colors.surfaceRaised else AppColors.colors.accentSoft)
                    .clickable(enabled = value.isNotBlank(), onClick = onSearch)
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchIcon(
                    color = if (value.isBlank()) AppColors.colors.textTertiary else AppColors.colors.accent,
                    size = 14.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (value.isBlank()) AppColors.colors.textTertiary else AppColors.colors.accent,
                )
            }
        }
    }
}
