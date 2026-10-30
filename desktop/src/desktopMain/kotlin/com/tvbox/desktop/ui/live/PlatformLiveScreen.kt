package com.tvbox.desktop.ui.live

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tvbox.app.domain.PlatformLiveRoom
import com.tvbox.desktop.state.DesktopAppState
import com.tvbox.desktop.state.PlatformLiveState
import com.tvbox.desktop.state.*
import com.tvbox.desktop.ui.components.Chip
import com.tvbox.desktop.ui.components.EmptyBox
import com.tvbox.desktop.ui.components.ErrorBox
import com.tvbox.desktop.ui.components.GhostButton
import com.tvbox.desktop.ui.components.InlineError
import com.tvbox.desktop.ui.components.LoadingBox
import com.tvbox.desktop.ui.components.PageHeader
import com.tvbox.desktop.ui.components.PrimaryButton
import com.tvbox.desktop.ui.theme.AppColors

@Composable
fun PlatformLiveScreen(state: DesktopAppState, modifier: Modifier = Modifier) {
    val live by state.platformLive.collectAsState()
    PlatformLiveContent(state, live, modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlatformLiveContent(state: DesktopAppState, live: PlatformLiveState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        PageHeader(
            title = "平台直播",
            subtitle = "斗鱼、虎牙、哔哩哔哩、抖音、快手的直播间",
        ) {
            GhostButton(
                text = if (live.showFavorites) "看全部" else "我的收藏",
                onClick = { state.togglePlatformLiveFavoritesView() },
            )
        }
        Spacer(Modifier.height(10.dp))

        if (live.sites.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                live.sites.forEach { site ->
                    Chip(
                        text = site.name.ifBlank { site.id },
                        selected = !live.showFavorites && site.id == live.selectedSite,
                        onClick = { state.selectPlatformLiveSite(site.id) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        when {
            live.loading && live.rooms.isEmpty() && live.favorites.isEmpty() -> {
                LoadingBox("正在加载直播间…", Modifier.fillMaxSize())
            }

            live.error != null && live.rooms.isEmpty() && live.favorites.isEmpty() -> {
                if (state.platformLiveServiceUrl.isBlank()) {
                    EmptyBox(
                        title = "还没有配置解析服务",
                        description = "平台直播需要本机或远程的解析服务地址，在「设置 → 网络与数据源」里填写后即可浏览直播间",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        PrimaryButton("前往设置") { state.navigate(DesktopScreen.Settings) }
                    }
                } else {
                    ErrorBox(
                        message = live.error,
                        modifier = Modifier.fillMaxSize(),
                        onRetry = { state.loadPlatformLiveSites() },
                    )
                }
            }

            live.showFavorites -> FavoritesView(state, live, Modifier.fillMaxSize())

            else -> CategoryBrowser(state, live, Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryBrowser(state: DesktopAppState, live: PlatformLiveState, modifier: Modifier = Modifier) {
    val parents = live.parents
    val children = live.categories.filter { it.parentId == live.selectedParentId }
    Column(modifier) {
        if (parents.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                parents.forEach { parent ->
                    Chip(
                        text = parent.name,
                        selected = parent.id == live.selectedParentId,
                        onClick = { state.selectPlatformLiveParent(parent.id) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (children.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                children.forEach { category ->
                    val selected = category.id == live.selectedCategoryId
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) AppColors.colors.accent else AppColors.colors.textTertiary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(role = Role.RadioButton) { state.selectPlatformLiveCategory(category.id) }
                            .semantics {
                                this.selected = selected
                                role = Role.RadioButton
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (live.error != null && live.rooms.isNotEmpty()) {
            InlineError(
                message = live.error,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                onRetry = {
                    if (live.page > 1) {
                        state.loadPlatformLiveRooms()
                    } else {
                        live.selectedCategoryId?.let(state::selectPlatformLiveCategory)
                    }
                },
            )
        }

        // 切分类时旧房间列表会保留到新一页返回；这里用加载态遮住旧内容，避免看错分类。
        if (live.loading) {
            LoadingBox("正在加载直播间…", Modifier.fillMaxSize())
            return
        }

        if (live.rooms.isEmpty()) {
            EmptyBox(
                title = "这个分类暂时没有直播间",
                description = "换个分类或平台看看",
                modifier = Modifier.fillMaxSize(),
            )
            return
        }

        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 236.dp),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(bottom = 24.dp),
        ) {
            items(live.rooms, key = { it.id }) { room ->
                RoomCard(
                    room = room,
                    favorite = live.favorites.any { it.id == room.id },
                    onClick = { state.openPlatformLiveRoom(room) },
                    onToggleFavorite = { state.togglePlatformLiveFavorite(room) },
                )
            }
            if (live.page < live.pageCount) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        GhostButton(
                            text = if (live.loadingMore) "加载中…" else "加载更多",
                            enabled = !live.loadingMore,
                            onClick = { state.loadPlatformLiveRooms() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesView(state: DesktopAppState, live: PlatformLiveState, modifier: Modifier = Modifier) {
    if (live.favorites.isEmpty()) {
        EmptyBox(
            title = "还没有收藏的直播间",
            description = "在直播间卡片上点星标即可收藏",
            modifier = modifier,
        ) {
            GhostButton("浏览直播间") { state.togglePlatformLiveFavoritesView() }
        }
        return
    }
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 236.dp),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.padding(bottom = 24.dp),
    ) {
        items(live.favorites, key = { it.id }) { room ->
            RoomCard(
                room = room,
                favorite = true,
                onClick = { state.openPlatformLiveRoom(room) },
                onToggleFavorite = { state.togglePlatformLiveFavorite(room) },
            )
        }
    }
}

@Composable
private fun RoomCard(
    room: PlatformLiveRoom,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    var imageFailed by remember(room.cover) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (hovered) AppColors.colors.surfaceHover else AppColors.colors.surface)
            .border(
                1.dp,
                when {
                    focused -> AppColors.colors.accent
                    hovered -> AppColors.colors.borderStrong
                    else -> AppColors.colors.border
                },
                RoundedCornerShape(11.dp),
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(126.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.colors.surfaceRaised),
        ) {
            if (room.cover.isNotBlank() && !imageFailed) {
                AsyncImage(
                    model = room.cover,
                    contentDescription = room.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = { imageFailed = true },
                )
            }
            if (room.cover.isBlank() || imageFailed) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LIVE", style = MaterialTheme.typography.labelMedium, color = AppColors.colors.accent)
                    Text(room.title, style = MaterialTheme.typography.labelMedium, color = AppColors.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (room.online > 0) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xCC06100C))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = formatOnline(room.online),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.colors.accent,
                    )
                }
            }
            FavoriteButton(
                favorite = favorite,
                onClick = onToggleFavorite,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = room.title,
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = listOf(room.anchor, room.categoryName).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FavoriteButton(favorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (hovered || favorite) Color(0xCC06100C) else Color(0x6606100C))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                contentDescription = if (favorite) "取消收藏" else "收藏直播间"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (favorite) "★" else "☆",
            style = MaterialTheme.typography.titleMedium,
            color = if (favorite) AppColors.colors.warning else Color(0xCCFFFFFF),
        )
    }
}

private fun formatOnline(count: Int): String =
    if (count >= 10_000) "${count / 10_000}.${(count % 10_000) / 1_000}万" else count.toString()
