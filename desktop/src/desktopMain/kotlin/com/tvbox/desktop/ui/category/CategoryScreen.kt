package com.tvbox.desktop.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tvbox.desktop.state.CategoryFilters
import com.tvbox.desktop.state.CategoryState
import com.tvbox.desktop.state.DesktopAppState
import com.tvbox.desktop.state.*
import com.tvbox.desktop.ui.components.EmptyBox
import com.tvbox.desktop.ui.components.ErrorBox
import com.tvbox.desktop.ui.components.GhostButton
import com.tvbox.desktop.ui.components.LoadingBox
import com.tvbox.desktop.ui.components.MovieCard
import com.tvbox.desktop.ui.components.PageHeader
import com.tvbox.desktop.ui.components.TextChoice
import com.tvbox.desktop.ui.theme.AppColors
import com.tvbox.desktop.ui.theme.DesktopMetrics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * 片库：聚合所有已配置线路的同一分类，按剧名去重后展示，支持年份/地区/语言筛选与滚动加载。
 * 分类名在不同站点对应不同 typeId，加载时会先按名字解析，解析不到的站点直接跳过。
 */
@Composable
fun CategoryScreen(
    state: DesktopAppState,
    category: CategoryState,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, category.loadingMore, category.page, category.pageCount) {
        snapshotFlow {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible to layout.totalItemsCount
        }
            .distinctUntilChanged()
            .filter { (lastVisible, total) -> total > 0 && lastVisible >= total - 4 }
            .collect { state.loadCategory() }
    }

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = DesktopMetrics.posterWidth),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize().padding(bottom = 28.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PageHeader(
                    title = "片库",
                    subtitle = when {
                        category.loading && category.movies.isEmpty() -> "正在聚合各源站…"
                        category.movies.isEmpty() -> "聚合 ${category.sourceCount} 个源站"
                        else -> "聚合 ${category.sourceCount} 个源站 · 已加载 ${category.movies.size} 部"
                    },
                ) {
                    if (category.loading) {
                        Text("加载中…", style = MaterialTheme.typography.labelMedium, color = AppColors.colors.textTertiary)
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                CategorySelector(state, category)
            }

            if (category.loading && category.movies.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { LoadingBox("正在获取分类内容…") }
            } else if (category.error != null && category.movies.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorBox(category.error) { state.loadCategory(reset = true) }
                }
            } else if (category.movies.isEmpty() && category.ready) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyBox(
                        title = "这个分类下没有内容",
                        description = if (category.activeFilters > 0) "试试清除筛选条件，或换一条数据源" else "换一条数据源试试",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                items(category.movies, key = { "${it.apiLineId}-${it.id}" }) { movie ->
                    MovieCard(movie = movie, onClick = { state.openCategoryMovie(movie) })
                }
                if (category.loadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                            Text("加载中…", style = MaterialTheme.typography.labelMedium, color = AppColors.colors.textTertiary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySelector(state: DesktopAppState, category: CategoryState) {
    var expanded by remember { mutableStateOf(true) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.colors.surface.copy(alpha = 0.55f))
            .border(1.dp, AppColors.colors.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "筛选",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            if (category.activeFilters > 0) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "已选 ${category.activeFilters} 项",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.colors.accent,
                )
            }
            Spacer(Modifier.weight(1f))
            if (category.activeFilters > 0) {
                Text(
                    text = "清除",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.colors.textSecondary,
                    modifier = Modifier.clickable { state.clearCategoryFilters() }.padding(8.dp),
                )
            }
            Text(
                text = if (expanded) "收起 ︿" else "展开 ﹀",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textSecondary,
                modifier = Modifier.clickable { expanded = !expanded }.padding(8.dp),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            ChoiceRow(label = "类型") {
                ChoiceFlow {
                    TextChoice(
                        text = "全部",
                        selected = category.selectedParentId == 0 && category.selectedTypeId == null,
                        onClick = { state.selectCategoryParent(0) },
                    )
                    category.parentCategories.forEach { parent ->
                        TextChoice(
                            text = parent.name,
                            selected = category.selectedParentId == parent.id,
                            onClick = { state.selectCategoryParent(parent.id) },
                        )
                    }
                }
            }

            if (category.childCategories.isNotEmpty()) {
                ChoiceRow(label = "子类") {
                    ChoiceFlow {
                        TextChoice(
                            text = "全部",
                            selected = category.selectedTypeId == null,
                            onClick = { state.selectCategoryType(null) },
                        )
                        category.childCategories.forEach { child ->
                            TextChoice(
                                text = child.name,
                                selected = category.selectedTypeId == child.id,
                                onClick = { state.selectCategoryType(child.id) },
                            )
                        }
                    }
                }
            }

            FilterTextRow(
                label = "年份",
                options = CategoryFilters.years,
                selected = category.year,
                onSelect = { state.setCategoryYear(it) },
            )
            FilterTextRow(
                label = "地区",
                options = CategoryFilters.areas,
                selected = category.area,
                onSelect = { state.setCategoryArea(it) },
            )
            FilterTextRow(
                label = "语言",
                options = CategoryFilters.languages,
                selected = category.lang,
                onSelect = { state.setCategoryLang(it) },
            )
        }
    }
}

/** 一行筛选：左侧固定标签，右侧流式排列的文字选项。 */
@Composable
private fun ChoiceRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colors.textTertiary,
            modifier = Modifier.width(44.dp).padding(top = 6.dp),
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceFlow(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
private fun FilterTextRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String?) -> Unit,
) {
    ChoiceRow(label = label) {
        ChoiceFlow {
            TextChoice(text = "全部", selected = selected.isBlank(), onClick = { onSelect(null) })
            options.forEach { option ->
                TextChoice(text = option, selected = selected == option, onClick = { onSelect(option) })
            }
        }
    }
}
