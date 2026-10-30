package com.tvbox.desktop.ui.detail

import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvbox.app.data.SpeedResult
import com.tvbox.app.domain.EpisodeMatrix
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.buildEpisodeMatrix
import com.tvbox.desktop.state.DetailState
import com.tvbox.desktop.state.DesktopAppState
import com.tvbox.desktop.state.*
import com.tvbox.desktop.ui.components.EmptyBox
import com.tvbox.desktop.ui.components.ErrorBox
import com.tvbox.desktop.ui.components.GhostButton
import com.tvbox.desktop.ui.components.LoadingBox
import com.tvbox.desktop.ui.components.PlayIcon
import com.tvbox.desktop.ui.components.PosterImage
import com.tvbox.desktop.ui.components.PrimaryButton
import com.tvbox.desktop.ui.shell.BackButton
import com.tvbox.desktop.ui.theme.AppColors

/**
 * 详情页。左侧固定海报与基础信息，右侧自上而下是「操作 → 简介 → 播放设置 → 选集」，
 * 线路与选集都放进卡片容器里，避免信息全堆在背景上显得杂乱。
 */
@Composable
fun DetailScreen(
    state: DesktopAppState,
    detail: DetailState,
    modifier: Modifier = Modifier,
) {
    // Esc 与「返回」等价：详情是叠加在页面之上的一层，键盘用户不该只能靠鼠标回退。
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }
    Column(
        modifier
            .fillMaxSize()
            .focusRequester(backFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    state.setDetail(null)
                    true
                } else {
                    false
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = { state.setDetail(null) })
            Spacer(Modifier.weight(1f))
            detail.movie?.let {
                Text(
                    text = if (detail.totalSources > 0) {
                        "来源 ${detail.completedSources}/${detail.totalSources.coerceAtLeast(detail.completedSources)} 条"
                    } else {
                        "正在统计来源…"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.colors.textTertiary,
                )
            }
        }

        when {
            detail.loading && detail.movie == null -> LoadingBox("正在获取影片详情…", Modifier.fillMaxSize())
            detail.error != null && detail.movie == null -> ErrorBox(
                message = detail.error,
                modifier = Modifier.fillMaxSize(),
                onRetry = { state.openDetail(detail.movieId) },
            )
            detail.movie == null -> EmptyBox(title = "没有获取到详情", modifier = Modifier.fillMaxSize())
            else -> DetailContent(state, detail, detail.movie)
        }
    }
}

@Composable
private fun DetailContent(state: DesktopAppState, detail: DetailState, movie: Movie) {
    val matrix = remember(movie.playSources) { buildEpisodeMatrix(movie.playSources) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 860.dp
        val content: @Composable ColumnScope.() -> Unit = {
            HeaderBlock(state, detail, movie)
            if (movie.description.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                SynopsisBlock(movie)
            }
            if (movie.director.isNotBlank() || movie.actor.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                InfoRow("导演", movie.director)
                InfoRow("主演", movie.actor)
            }
            Spacer(Modifier.height(20.dp))
            if (movie.playSources.isEmpty() || matrix.episodes.isEmpty()) {
                DetailCard {
                    EmptyBox(
                        title = "正在为你匹配其他线路…",
                        description = "这条线路暂时取不到播放源，稍等一下会自动换到有资源的线路",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                PlaySettingsCard(state, detail, movie, matrix)
                Spacer(Modifier.height(18.dp))
                EpisodesCard(state, detail, movie, matrix)
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(248.dp)) { PosterColumn(movie) }
                Spacer(Modifier.width(30.dp))
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                        .padding(end = 6.dp, bottom = 28.dp),
                    content = content,
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(end = 6.dp, bottom = 28.dp),
            ) {
                CompactPosterHeader(movie)
                Spacer(Modifier.height(18.dp))
                content()
            }
        }
    }
}

@Composable
private fun CompactPosterHeader(movie: Movie) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.width(132.dp).height(198.dp).clip(RoundedCornerShape(10.dp))
                .background(AppColors.colors.surfaceRaised)
                .border(1.dp, AppColors.colors.border, RoundedCornerShape(10.dp)),
        ) {
            PosterImage(movie.posterUrl, movie.name, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            PosterMeta("线路", movie.apiLineName.ifBlank { movie.apiLineId })
            if (movie.remarks.isNotBlank()) PosterMeta("状态", movie.remarks)
            if (movie.year.isNotBlank()) PosterMeta("年份", movie.year)
            if (movie.area.isNotBlank()) PosterMeta("地区", movie.area)
            if (movie.duration.isNotBlank()) PosterMeta("时长", movie.duration)
        }
    }
}

@Composable
private fun HeaderBlock(state: DesktopAppState, detail: DetailState, movie: Movie) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = movie.name,
            style = MaterialTheme.typography.headlineMedium,
            color = AppColors.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        MetaTags(movie)
        Spacer(Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton(
                text = detail.resumeHint?.let { "继续播放 · ${formatPosition(it.positionMs)}" } ?: "立即播放",
                enabled = movie.playSources.isNotEmpty(),
                leading = { PlayIcon(Color(0xFF06150E), size = 13.dp) },
                onClick = {
                    val hint = detail.resumeHint
                    if (hint != null) {
                        state.resumeHistory(hint)
                    } else {
                        state.playDetailEpisode(detail.sourceIndex, detail.episodeIndex, 0L)
                    }
                },
            )
            GhostButton(
                text = if (detail.followed) "已追剧" else "追剧",
                onClick = {
                    state.toggleFollow(movie, detail.episodeIndex, detail.resumeHint?.positionMs ?: 0L)
                },
            )
            GhostButton(
                text = if (detail.speedTesting) "测速中…" else "测速",
                enabled = !detail.speedTesting && movie.playSources.isNotEmpty(),
                onClick = { state.runSourceSpeedTest() },
            )
        }
    }
}

@Composable
private fun MetaTags(movie: Movie) {
    val tags = buildList {
        movie.year.takeIf { it.isNotBlank() }?.let(::add)
        movie.area.takeIf { it.isNotBlank() }?.let(::add)
        movie.language.takeIf { it.isNotBlank() }?.let(::add)
        movie.remarks.takeIf { it.isNotBlank() }?.let(::add)
    }
    if (tags.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            Box(
                Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.colors.surface)
                    .border(1.dp, AppColors.colors.border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(tag, style = MaterialTheme.typography.labelMedium, color = AppColors.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun SynopsisBlock(movie: Movie) {
    var expanded by remember(movie.apiLineId, movie.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = movie.description,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.colors.textSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 22.sp,
        )
        if (movie.description.length > 90) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (expanded) "收起" else "展开全部",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PosterColumn(movie: Movie) {
    Column(Modifier.width(248.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(372.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.colors.surfaceRaised)
                .border(1.dp, AppColors.colors.border, RoundedCornerShape(12.dp)),
        ) {
            PosterImage(movie.posterUrl, movie.name, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(14.dp))
        PosterMeta("线路", movie.apiLineName.ifBlank { movie.apiLineId })
        if (movie.remarks.isNotBlank()) PosterMeta("状态", movie.remarks)
        if (movie.year.isNotBlank()) PosterMeta("年份", movie.year)
        if (movie.area.isNotBlank()) PosterMeta("地区", movie.area)
        if (movie.duration.isNotBlank()) PosterMeta("时长", movie.duration)
    }
}

@Composable
private fun PosterMeta(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colors.textTertiary,
            modifier = Modifier.width(46.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colors.textTertiary,
            modifier = Modifier.width(46.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SkipMarksRow(state: DesktopAppState, movie: Movie) {
    val marksMap by state.skipMarks.collectAsState()
    val marks = marksMap["${movie.apiLineId}:${movie.id}"]
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "片头片尾",
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colors.textSecondary,
            modifier = Modifier.width(104.dp),
        )
        Text(
            text = if (marks == null) {
                "未设置 · 播放页点「片头尾」用当前进度一键记录，本剧各集通用"
            } else {
                "片头 ${formatSkipTime(marks.introEndMs)} · 片尾 ${formatSkipTime(marks.outroStartMs)} · 播放页可调整"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (marks == null) AppColors.colors.textTertiary else AppColors.colors.textSecondary,
        )
    }
}

@Composable
private fun PlaySettingsCard(
    state: DesktopAppState,
    detail: DetailState,
    movie: Movie,
    matrix: EpisodeMatrix,
) {
    // 测速后按延迟升序：已测成功的排前面，未测的保持原顺序，失败的排最后。
    val orderedSources = remember(movie.playSources, detail.sourceSpeeds, detail.speedTesting) {
        if (detail.speedTesting) {
            movie.playSources.withIndex().toList()
        } else {
            movie.playSources.withIndex().sortedWith(
                compareBy(
                    { entry ->
                        val speed = detail.sourceSpeeds[entry.index]
                        when {
                            speed?.ok == true -> 0
                            speed == null -> 1
                            else -> 2
                        }
                    },
                    { entry -> detail.sourceSpeeds[entry.index]?.latencyMs?.coerceAtLeast(1L) ?: Long.MAX_VALUE },
                    { entry -> detail.sourceSpeeds[entry.index]?.let { -it.kbps } ?: 0.0 },
                    { entry -> entry.index },
                ),
            )
        }
    }
    var sourcesExpanded by remember(movie.apiLineId, movie.id) { mutableStateOf(false) }
    val collapsedLimit = 10
    val visibleSources = remember(orderedSources, sourcesExpanded, detail.sourceIndex) {
        if (sourcesExpanded || orderedSources.size <= collapsedLimit) {
            orderedSources
        } else {
            val head = orderedSources.take(collapsedLimit)
            // 收起时也要保证当前线路可见，否则用户看不到自己在用哪条。
            if (head.any { it.index == detail.sourceIndex }) {
                head
            } else {
                head + orderedSources.first { it.index == detail.sourceIndex }
            }
        }
    }
    // 用 collectAsState 订阅设置，否则「跟随全局 · 开/关」会停留在进入页面时的旧值。
    val settings by state.settings.collectAsState()
    val appSettings = settings.settings

    DetailCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "播放线路",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.colors.textPrimary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "共 ${movie.playSources.size} 条 · 最全 ${matrix.episodes.size} 集",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textTertiary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                // 有缓存结果时显示测速时间，让用户知道排序不是刚测出来的。
                text = speedFreshnessHint(
                    speedTesting = detail.speedTesting,
                    measuredAtMs = detail.sourceSpeedsMeasuredAtMs,
                    nowMs = System.currentTimeMillis(),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textTertiary,
            )
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            visibleSources.forEach { entry ->
                val index = entry.index
                val source = entry.value
                val playable = matrix.availability(index).count { it }
                key("${source.name}:${index}") {
                    SourceChip(
                        name = source.name,
                        episodes = "$playable/${matrix.episodes.size} 集",
                        speed = detail.sourceSpeeds[index],
                        selected = index == detail.sourceIndex,
                        onClick = { state.selectDetailSource(index) },
                    )
                }
            }
        }
        if (orderedSources.size > collapsedLimit) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (sourcesExpanded) {
                    "收起线路"
                } else {
                    "展开全部 ${orderedSources.size} 条线路"
                },
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { sourcesExpanded = !sourcesExpanded }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
            )
        }

        if (movie.playSources.size > 1) {
            Spacer(Modifier.height(18.dp))
            DetailDivider()
            Spacer(Modifier.height(14.dp))
            // 两个策略并排一行，卡片高度能省一半；窄窗口时自动换行。
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PolicyRow(
                    label = "自动切源",
                    value = detail.policy?.autoSwitch,
                    globalValue = appSettings.playbackAgentAutoSwitchEnabled,
                    onSelect = { state.setMoviePolicy(it, detail.policy?.preferFastest) },
                )
                PolicyRow(
                    label = "优先最快线路",
                    value = detail.policy?.preferFastest,
                    globalValue = appSettings.preferFastestLine,
                    onSelect = { state.setMoviePolicy(detail.policy?.autoSwitch, it) },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        DetailDivider()
        Spacer(Modifier.height(14.dp))
        SkipMarksRow(state, movie)
    }
}

@Composable
private fun PolicyRow(
    label: String,
    value: Boolean?,
    globalValue: Boolean,
    onSelect: (Boolean?) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colors.textSecondary,
            modifier = Modifier.width(88.dp),
        )
        PolicyOption(
            text = "跟随全局 · ${if (globalValue) "开" else "关"}",
            selected = value == null,
            onClick = { onSelect(null) },
        )
        Spacer(Modifier.width(6.dp))
        PolicyOption(text = "强制开", selected = value == true, onClick = { onSelect(true) })
        Spacer(Modifier.width(6.dp))
        PolicyOption(text = "强制关", selected = value == false, onClick = { onSelect(false) })
    }
}

@Composable
private fun EpisodesCard(
    state: DesktopAppState,
    detail: DetailState,
    movie: Movie,
    matrix: EpisodeMatrix,
) {
    val availability = matrix.availability(detail.sourceIndex)
    var pendingEpisode by remember(movie.apiLineId, movie.id) { mutableStateOf<Int?>(null) }
    DetailCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "选集",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.colors.textPrimary,
            )
            Spacer(Modifier.width(10.dp))
            if (pendingEpisode != null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = AppColors.colors.accent,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "正在打开 ${matrix.episodes.getOrNull(pendingEpisode!!)?.title.orEmpty()}…",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.colors.accent,
                )
            } else {
                Text(
                    text = "当前 ${matrix.episodes.getOrNull(detail.episodeIndex)?.title.orEmpty()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.colors.accent,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "点击任意一集即可播放；灰底表示当前线路缺这一集，点击会自动换线",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textTertiary,
            )
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            matrix.episodes.forEachIndexed { index, episode ->
                val playableHere = availability.getOrElse(index) { false }
                val playableSomewhere = matrix.sources.any { it.available.getOrElse(index) { false } }
                val reason = when {
                    !playableSomewhere -> "所有线路都没有这一集"
                    !playableHere -> "当前线路缺这一集，点击会自动切换到其他线路"
                    else -> null
                }
                EpisodeChip(
                    title = episode.title,
                    selected = index == detail.episodeIndex,
                    playableHere = playableHere,
                    playableSomewhere = playableSomewhere,
                    reason = reason,
                    loading = pendingEpisode == index,
                    enabled = pendingEpisode == null,
                    onClick = {
                        if (pendingEpisode != null) return@EpisodeChip
                        pendingEpisode = index
                        state.playDetailEpisode(detail.sourceIndex, index, 0L)
                        // 没有成功进入播放页（例如所有线路缺集）时立刻恢复，避免一直转圈。
                        if (state.player.value == null) pendingEpisode = null
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.colors.surface)
            .border(1.dp, AppColors.colors.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        content = content,
    )
}

@Composable
private fun DetailDivider() {
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(AppColors.colors.border))
}

@Composable
private fun SourceChip(
    name: String,
    episodes: String,
    speed: SpeedResult?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    selected -> AppColors.colors.accentSoft
                    hovered -> AppColors.colors.surfaceHover
                    else -> AppColors.colors.surfaceRaised
                },
            )
            .border(
                1.dp,
                when {
                    focused -> AppColors.colors.accent
                    selected -> AppColors.colors.accent.copy(alpha = 0.55f)
                    else -> AppColors.colors.border
                },
                RoundedCornerShape(8.dp),
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) AppColors.colors.accent else AppColors.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = episodes,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colors.textTertiary,
        )
        if (speed != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = speed.formatLatency(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = when {
                    !speed.ok -> AppColors.colors.danger
                    selected -> AppColors.colors.accent
                    else -> AppColors.colors.warning
                },
            )
        }
    }
}

@Composable
private fun PolicyOption(text: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    selected -> AppColors.colors.accentSoft
                    hovered -> AppColors.colors.surfaceHover
                    else -> Color.Transparent
                },
            )
            .border(
                1.dp,
                when {
                    focused -> AppColors.colors.accent
                    selected -> AppColors.colors.accent.copy(alpha = 0.55f)
                    else -> AppColors.colors.border
                },
                RoundedCornerShape(6.dp),
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) AppColors.colors.accent else AppColors.colors.textSecondary,
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun EpisodeChip(
    title: String,
    selected: Boolean,
    playableHere: Boolean,
    playableSomewhere: Boolean,
    reason: String?,
    loading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val background = when {
        selected -> AppColors.colors.accent
        !playableSomewhere -> AppColors.colors.surface
        !playableHere -> AppColors.colors.surfaceRaised
        hovered -> AppColors.colors.surfaceHover
        else -> AppColors.colors.surfaceRaised
    }
    val borderColor = when {
        focused -> AppColors.colors.accent
        selected -> AppColors.colors.accent
        hovered -> AppColors.colors.borderStrong
        else -> AppColors.colors.border
    }
    val textColor = when {
        selected -> Color(0xFF06150E)
        !playableSomewhere -> AppColors.colors.textTertiary.copy(alpha = 0.45f)
        !playableHere -> AppColors.colors.textTertiary
        else -> AppColors.colors.textSecondary
    }

    val chip: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(background)
                .border(1.dp, borderColor, RoundedCornerShape(7.dp))
                .hoverable(interaction, enabled = playableSomewhere && enabled)
                .clickable(
                    enabled = playableSomewhere && enabled,
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                )
                .semantics {
                    this.selected = selected
                    role = Role.RadioButton
                    if (!playableSomewhere || !enabled) disabled()
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = textColor,
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (reason == null || selected) {
        chip()
    } else {
        TooltipArea(
            tooltip = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppColors.colors.surfaceRaised)
                        .border(1.dp, AppColors.colors.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.colors.textSecondary,
                    )
                }
            },
            delayMillis = 350,
            tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 10.dp)),
            content = chip,
        )
    }
}

private fun formatPosition(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
