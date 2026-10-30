package com.tvbox.desktop.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tvbox.app.domain.DoubanHotItem
import com.tvbox.app.domain.HomeFeedMode
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.WatchHistoryItem
import com.tvbox.app.domain.continueWatching
import com.tvbox.app.domain.historyKey
import com.tvbox.desktop.data.FollowedShow
import com.tvbox.desktop.state.DesktopAppState
import com.tvbox.desktop.state.HistoryState
import com.tvbox.desktop.state.HomeState
import com.tvbox.desktop.state.*
import com.tvbox.desktop.ui.components.Chip
import com.tvbox.desktop.ui.components.ErrorBox
import com.tvbox.desktop.ui.components.GhostButton
import com.tvbox.desktop.ui.components.LoadingBox
import com.tvbox.desktop.ui.components.MovieCard
import com.tvbox.desktop.ui.components.PageHeader
import com.tvbox.desktop.ui.components.PlayIcon
import com.tvbox.desktop.ui.components.PosterCard
import com.tvbox.desktop.ui.components.PosterImage
import com.tvbox.desktop.ui.components.PrimaryButton
import com.tvbox.desktop.ui.components.SectionHeader
import com.tvbox.desktop.ui.components.StarIcon
import com.tvbox.desktop.ui.theme.AppColors
import com.tvbox.desktop.ui.theme.DesktopMetrics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun HomeScreen(
    state: DesktopAppState,
    home: HomeState,
    history: HistoryState,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val follow by state.follow.collectAsState()
    // 同一部剧可能在多条线路上留下记录，继续观看只保留最近的一条。
    val recent = remember(history.items) {
        history.items.continueWatching(limit = 16)
            .distinctBy { it.movieName.trim().lowercase() }
            .take(8)
    }
    val recentNames = remember(recent) { recent.mapTo(mutableSetOf()) { it.movieName.trim() } }
    val followMap = remember(follow.items) { follow.items.associateBy { it.key } }
    val slides = remember(home.mode, home.items, home.movies, home.resolvingTitle, recentNames) {
        heroSlides(state, home).filterNot { it.title.trim() in recentNames }
    }
    val visibleDouban = remember(home.items, slides) {
        val featured = slides.mapTo(mutableSetOf()) { it.key }
        home.items.filterNot { "douban-${it.doubanId}" in featured }
    }
    val visibleMovies = remember(home.movies, slides) {
        val featured = slides.mapTo(mutableSetOf()) { it.key }
        home.movies.filterNot { "${it.apiLineId}-${it.id}" in featured }
    }

    // 滚到接近底部时自动加载下一页，不再需要点「加载更多」。
    LaunchedEffect(gridState, home.page, home.pageCount, home.loadingMore, home.mode) {
        snapshotFlowOfVisible(gridState)
            .distinctUntilChanged()
            .filter { (lastVisible, total) -> total > 0 && lastVisible >= total - 4 }
            .collect { state.loadHome() }
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
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 900.dp) {
                        PageHeader(title = "首页", subtitle = homeSubtitle(home)) {
                            HomeHeaderActions(state, home)
                        }
                    } else {
                        Column {
                            PageHeader(title = "首页", subtitle = homeSubtitle(home))
                            Spacer(Modifier.height(12.dp))
                            HomeHeaderActions(state, home)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (slides.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(title = "今日推荐") {
                        Text(
                            text = if (home.mode == HomeFeedMode.Douban) "豆瓣热播前 ${minOf(slides.size, 12)} 部" else "本站最新更新",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.colors.textTertiary,
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FeaturedRow(slides)
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (recent.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(title = "继续观看") {
                        GhostButton(text = "全部历史", onClick = { state.navigate(DesktopScreen.History) })
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        lazyItems(recent, key = { it.historyKey }) { item ->
                            ContinueCard(
                                item = item,
                                followed = followMap["${item.apiLineId}:${item.movieId}"],
                                resuming = history.resumingKey == item.historyKey,
                                enabled = history.resumingKey == null,
                                onClick = { state.resumeHistory(item) },
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(title = if (home.mode == HomeFeedMode.Douban) "豆瓣热播" else "本站最新") {
                    if (home.loadingMore) {
                        Text(
                            "加载中…",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.colors.textTertiary,
                        )
                    }
                }
            }

            if (home.loading && home.isEmpty) {
                item(span = { GridItemSpan(maxLineSpan) }) { LoadingBox("正在获取内容…") }
            } else if (home.error != null && home.isEmpty) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorBox(home.error) { state.refreshHome() }
                }
            } else {
                when (home.mode) {
                    HomeFeedMode.Douban -> items(visibleDouban, key = { it.doubanId }) { item ->
                        PosterCard(
                            item = item,
                            resolving = home.resolvingTitle == item.title,
                            enabled = home.resolvingTitle == null,
                            onClick = { state.openDoubanItem(item) },
                        )
                    }

                    HomeFeedMode.Latest -> items(visibleMovies, key = { "${it.apiLineId}-${it.id}" }) { movie ->
                        MovieCard(movie = movie, onClick = { state.openCategoryMovie(movie) })
                    }
                }
            }
        }
    }
}

private fun snapshotFlowOfVisible(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
): kotlinx.coroutines.flow.Flow<Pair<Int, Int>> = androidx.compose.runtime.snapshotFlow {
    val layout = gridState.layoutInfo
    val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
    lastVisible to layout.totalItemsCount
}

private val HomeState.isEmpty: Boolean
    get() = items.isEmpty() && movies.isEmpty()

private fun homeSubtitle(home: HomeState): String = when {
    home.mode == HomeFeedMode.Douban && home.items.isEmpty() -> "豆瓣热播"
    home.mode == HomeFeedMode.Douban -> "豆瓣热播 · 已加载 ${home.items.size} 部"
    home.movies.isEmpty() -> "本站最新更新"
    else -> "本站最新 · 已加载 ${home.movies.size} 部"
}

@Composable
private fun HomeHeaderActions(state: DesktopAppState, home: HomeState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HomeFeedMode.entries.forEach { mode ->
            Chip(
                text = mode.displayName,
                selected = home.mode == mode,
                onClick = { state.setHomeFeed(mode) },
            )
            Spacer(Modifier.width(6.dp))
        }
        Spacer(Modifier.width(8.dp))
        GhostButton(
            text = "换一批",
            enabled = !home.loading,
            onClick = { state.refreshHome() },
        )
    }
}

// region 推荐轮播

private data class HeroSlide(
    val key: String,
    val title: String,
    val posterUrl: String,
    val subtitle: String,
    val tag: String,
    val score: Double,
    val ratingCount: Int,
    val actionText: String,
    val actionHint: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/** 首屏推荐位取当前内容源的前 5 部，标题去重后轮播。 */
private fun heroSlides(state: DesktopAppState, home: HomeState): List<HeroSlide> {
    val slides = when (home.mode) {
        HomeFeedMode.Douban -> home.items.map { item ->
            HeroSlide(
                key = "douban-${item.doubanId}",
                title = item.title,
                posterUrl = item.posterUrl,
                subtitle = item.subtitle,
                tag = "豆瓣热播",
                score = item.score,
                ratingCount = item.ratingCount,
                actionText = when {
                    home.resolvingTitle == item.title -> "正在查找资源…"
                    home.resolvingTitle != null -> "请稍候…"
                    else -> "开始观看"
                },
                actionHint = "点击后从当前线路查找可播放资源",
                enabled = home.resolvingTitle == null,
                onClick = { state.openDoubanItem(item) },
            )
        }

        HomeFeedMode.Latest -> home.movies.map { movie ->
            HeroSlide(
                key = "${movie.apiLineId}-${movie.id}",
                title = movie.name,
                posterUrl = movie.posterUrl,
                subtitle = movie.subtitle,
                tag = "最新更新",
                score = 0.0,
                ratingCount = 0,
                actionText = "查看详情",
                actionHint = "来自 ${movie.apiLineName.ifBlank { movie.apiLineId }}",
                enabled = true,
                onClick = { state.openCategoryMovie(movie) },
            )
        }
    }
    return slides.distinctBy { it.title }.take(6)
}

/**
 * 今日推荐：一排等宽竖版海报，按 2:3 完整展示，不裁切也不做虚化背景。
 * 标题与评分压在封面底部，悬停才出现播放键，比自动轮播更容易扫读。
 */
@Composable
private fun FeaturedRow(slides: List<HeroSlide>) {
    if (slides.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 14.dp
        val minCard = 172.dp
        val count = slides.size
            .coerceAtMost(((maxWidth + gap) / (minCard + gap)).toInt())
            .coerceAtLeast(1)
        val cardWidth = ((maxWidth - gap * (count - 1)) / count).coerceAtMost(226.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            slides.take(count).forEachIndexed { index, slide ->
                FeaturedCard(
                    slide = slide,
                    rank = index + 1,
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}

@Composable
private fun FeaturedCard(slide: HeroSlide, rank: Int, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.colors.surfaceRaised)
            .border(
                1.dp,
                when {
                    focused -> AppColors.colors.accent
                    hovered && slide.enabled -> AppColors.colors.accent.copy(alpha = 0.7f)
                    else -> AppColors.colors.border
                },
                RoundedCornerShape(12.dp),
            )
            .hoverable(interaction, enabled = slide.enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = slide.enabled,
                onClick = slide.onClick,
            )
            .aspectRatio(2f / 3f),
    ) {
        PosterImage(slide.posterUrl, slide.title, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x26000000),
                    0.55f to Color(0x00000000),
                    1f to Color(0xE6060A09),
                ),
            ),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (rank == 1) AppColors.colors.accent else Color(0x99060A09))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                text = "TOP $rank",
                style = MaterialTheme.typography.labelMedium,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (rank == 1) Color(0xFF06150E) else Color(0xE6FFFFFF),
            )
        }
        if (hovered && slide.enabled) {
            Box(Modifier.fillMaxSize().background(Color(0x4D060A09)), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color(0xD929E68C)),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayIcon(color = Color(0xFF062015), size = 20.dp)
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            Text(
                text = slide.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (slide.score > 0) {
                    StarIcon(color = AppColors.colors.warning, size = 11.dp)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = String.format("%.1f", slide.score),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.colors.warning,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = if (hovered || !slide.enabled) slide.actionText else slide.tag,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hovered || !slide.enabled) AppColors.colors.accent else Color(0xB3FFFFFF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 继续观看卡片：海报流。进度条压在封面底部，集数与「新集」标记直接叠在封面上，
 * 悬停出现蒙版播放键，比一整条横向信息条更接近主流流媒体的扫读方式。
 */
@Composable
private fun ContinueCard(
    item: WatchHistoryItem,
    followed: FollowedShow?,
    resuming: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val total = maxOf(item.episodeCount, followed?.lastEpisodeCount ?: 0)

    Column(
        modifier = Modifier
            .width(152.dp)
            .hoverable(interaction, enabled = enabled)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(228.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.colors.surfaceRaised)
                .border(
                    1.dp,
                    when {
                        focused || resuming -> AppColors.colors.accent
                        hovered && enabled -> AppColors.colors.accent.copy(alpha = 0.65f)
                        else -> AppColors.colors.border
                    },
                    RoundedCornerShape(10.dp),
                ),
        ) {
            PosterImage(item.posterUrl, item.movieName, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0x00000000), Color(0x00000000), Color(0xCC060A09)),
                    ),
                ),
            )
            if (resuming) {
                Box(Modifier.fillMaxSize().background(Color(0x99060A09)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = AppColors.colors.accent,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "正在续播…",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xF2FFFFFF),
                        )
                    }
                }
            } else if (hovered && enabled) {
                Box(Modifier.fillMaxSize().background(Color(0x66060A09)), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xD929E68C)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayIcon(color = Color(0xFF062015), size = 20.dp)
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (total > 1) {
                            "第 ${item.episodeIndex + 1}/$total 集"
                        } else if (item.episodeIndex > 0) {
                            "第 ${item.episodeIndex + 1} 集"
                        } else if (item.progressPercent > 0) {
                            "已看 ${item.progressPercent}%"
                        } else {
                            "继续观看"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xF2FFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (followed?.hasNewEpisodes == true) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AppColors.colors.accent)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                "新集",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF062015),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(Color(0x4DFFFFFF)),
                ) {
                    val fraction = item.progressPercent.coerceIn(0, 100) / 100f
                    if (fraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(3.dp)
                                .background(AppColors.colors.accent),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.movieName,
            style = MaterialTheme.typography.titleMedium,
            color = if (hovered && enabled) AppColors.colors.accent else AppColors.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = relativeTime(item.updatedAtEpochMs),
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textTertiary,
                maxLines = 1,
            )
            val source = item.sourceName.ifBlank { item.remarks }
            if (source.isNotBlank()) {
                Text(
                    text = " · $source",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (followed?.hasNewEpisodes == true) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "更新至 ${followed.latestEpisodeTitle}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 继续观看卡片上的相对时间，扫一眼就知道上次看到是多久以前。 */
private fun relativeTime(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    val diff = System.currentTimeMillis() - epochMs
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "刚刚"
        diff < hour -> "${diff / minute} 分钟前"
        diff < day -> "${diff / hour} 小时前"
        diff < 2 * day -> "昨天"
        diff < 7 * day -> "${diff / day} 天前"
        else -> java.time.Instant.ofEpochMilli(epochMs)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("M月d日"))
    }
}
