package com.tvbox.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvbox.app.data.ApiLines
import com.tvbox.app.data.AppLog
import com.tvbox.app.data.SpeedResult
import com.tvbox.app.data.LogLevel
import com.tvbox.app.domain.ApiLine
import com.tvbox.app.domain.AppSettings
import com.tvbox.app.domain.HomeFeedMode
import com.tvbox.app.domain.ProxyMode
import com.tvbox.desktop.DesktopVersion
import com.tvbox.desktop.data.DesktopImages
import com.tvbox.desktop.data.DesktopPaths
import com.tvbox.desktop.state.DesktopAppState
import com.tvbox.desktop.state.LogState
import com.tvbox.desktop.state.SpeedTestState
import com.tvbox.desktop.state.*
import com.tvbox.desktop.ui.components.Chip
import com.tvbox.desktop.ui.components.GhostButton
import com.tvbox.desktop.ui.components.PageHeader
import com.tvbox.desktop.ui.components.PrimaryButton
import com.tvbox.desktop.ui.components.SectionHeader
import com.tvbox.desktop.ui.theme.AppColors
import com.tvbox.desktop.ui.theme.DesktopFocus
import com.tvbox.desktop.ui.theme.DesktopShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

private val SPEED_OPTIONS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/** 日志时间窗：键为分钟数，0 表示不限时间。 */
private val LOG_WINDOWS = listOf(
    0 to "全部时间",
    60 to "最近 1 小时",
    1440 to "最近 24 小时",
    10080 to "最近 7 天",
)

/** 设置页章节：左侧导航与右侧内容一一对应。 */
private enum class SettingsSection(
    val title: String,
    val shortTitle: String,
    val subtitle: String,
) {
    General("常规", "常规", "首页来源与内容偏好"),
    Playback("播放", "播放", "选源、缓冲、倍速、测速与追剧检查"),
    Network("网络与数据源", "网络", "网络代理与平台直播解析服务"),
    Storage("存储", "存储", "图片与接口缓存的占用和清理"),
    Advanced("高级与诊断", "诊断", "播放健康记录与运行日志"),
    About("关于", "关于", "版本、数据目录与播放内核"),
}

/** 输入框保存反馈：保存中 / 已保存 / 保存失败。 */
private enum class SaveStatus { Idle, Saving, Saved, Failed }

/**
 * 设置页。采用桌面客户端常见的「左侧章节 + 右侧滚动内容」结构：
 * 左侧固定导航负责切换章节，右侧只渲染当前章节的卡片，避免一屏堆满开关。
 */
@Composable
fun SettingsScreen(state: DesktopAppState, modifier: Modifier = Modifier) {
    val settingsState by state.settings.collectAsState()
    val speedState by state.speedTest.collectAsState()
    val logState by state.logs.collectAsState()
    val followState by state.follow.collectAsState()
    val settings = settingsState.settings
    var serviceUrl by remember(settingsState.ready) { mutableStateOf(state.platformLiveServiceUrl) }
    var proxyHost by remember(settings.proxyHost) { mutableStateOf(settings.proxyHost) }
    var proxyPort by remember(settings.proxyPort) { mutableStateOf(settings.proxyPort.toString()) }

    val allLines: List<ApiLine> = remember(settings.customVideoApiLines) {
        ApiLines.defaults + settings.customVideoApiLines.map { it.toApiLine() }
    }

    var sourcePickerOpen by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(SettingsSection.General) }
    val contentScroll = rememberScrollState()

    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 窄窗口（< 1120dp）折叠左侧章节为窄标签列；内容区过窄时同样折叠，保证不横向溢出。
            val windowWidth = LocalWindowInfo.current.containerDpSize.width
            val compact = windowWidth < 1120.dp || maxWidth < 880.dp
            Row(Modifier.fillMaxSize()) {
                SettingsSectionNav(
                    selected = section,
                    compact = compact,
                    onSelect = { section = it },
                )
                Spacer(Modifier.width(if (compact) 14.dp else 22.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    PageHeader(title = section.title, subtitle = section.subtitle)
                    Spacer(Modifier.height(18.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(contentScroll)
                            .padding(bottom = 40.dp),
                    ) {
                        when (section) {
                            SettingsSection.General -> HomeSourceCard(
                                state = state,
                                settings = settings,
                                allLines = allLines,
                                onOpenPicker = { sourcePickerOpen = true },
                            )

                            SettingsSection.Playback -> PlaybackSection(
                                state = state,
                                settings = settings,
                                allLines = allLines,
                                speedState = speedState,
                                followState = followState,
                            )

                            SettingsSection.Network -> NetworkSection(
                                state = state,
                                settings = settings,
                                proxyHost = proxyHost,
                                proxyPort = proxyPort,
                                serviceUrl = serviceUrl,
                                onProxyHostChange = { proxyHost = it },
                                onProxyPortChange = { proxyPort = it },
                                onServiceUrlChange = { serviceUrl = it },
                            )

                            SettingsSection.Storage -> StorageSection(state, settings)
                            SettingsSection.Advanced -> AdvancedSection(state, settingsState, logState)
                            SettingsSection.About -> AboutSection()
                        }
                    }
                }
            }
            LaunchedEffect(section) { contentScroll.scrollTo(0) }
        }

        if (sourcePickerOpen) {
            SourcePickerOverlay(
                lines = allLines,
                selectedId = settings.homeApiLineId,
                speeds = speedState.lineResults,
                onDismiss = { sourcePickerOpen = false },
                onPick = { line ->
                    state.saveSettings { it.copy(homeApiLineId = line.id) }
                    state.loadHome(reset = true)
                    sourcePickerOpen = false
                },
            )
        }
    }
}

/**
 * 左侧章节导航：每项是一个 Role.Tab，点击或方向键 / Home / End 都能切换章节。
 */
@Composable
private fun SettingsSectionNav(
    selected: SettingsSection,
    compact: Boolean,
    onSelect: (SettingsSection) -> Unit,
) {
    val entries = SettingsSection.entries
    val focusRequesters = remember { entries.map { FocusRequester() } }

    fun choose(target: SettingsSection, focus: Boolean) {
        if (target != selected) onSelect(target)
        if (focus) runCatching { focusRequesters[target.ordinal].requestFocus() }
    }

    Column(
        modifier = Modifier
            .width(if (compact) 84.dp else 208.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.colors.surface)
            .border(1.dp, AppColors.colors.border, RoundedCornerShape(14.dp))
            .padding(if (compact) 8.dp else 12.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val current = selected.ordinal
                val target = when (event.key) {
                    Key.DirectionDown -> (current + 1).coerceAtMost(entries.lastIndex)
                    Key.DirectionUp -> (current - 1).coerceAtLeast(0)
                    Key.MoveHome -> 0
                    Key.MoveEnd -> entries.lastIndex
                    else -> return@onPreviewKeyEvent false
                }
                if (target != current) choose(entries[target], focus = true)
                true
            },
    ) {
        if (compact) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        } else {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge,
                color = AppColors.colors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "所有配置保存在本机，不随账号同步",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textTertiary,
            )
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            entries.forEach { item ->
                SettingsSectionTab(
                    section = item,
                    selected = item == selected,
                    compact = compact,
                    focusRequester = focusRequesters[item.ordinal],
                    onClick = { choose(item, focus = false) },
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionTab(
    section: SettingsSection,
    selected: Boolean,
    compact: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(DesktopShapes.control)
    val background = when {
        selected -> AppColors.colors.accentSoft
        hovered -> AppColors.colors.surfaceHover
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(
                DesktopFocus.strokeWidth,
                if (focused) AppColors.colors.accent else Color.Transparent,
                shape,
            )
            .focusRequester(focusRequester)
            .hoverable(interaction)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = if (compact) 4.dp else 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) AppColors.colors.accent else Color.Transparent),
        )
        Spacer(Modifier.width(if (compact) 5.dp else 9.dp))
        Text(
            text = if (compact) section.shortTitle else section.title,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) AppColors.colors.accent else AppColors.colors.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaybackSection(
    state: DesktopAppState,
    settings: AppSettings,
    allLines: List<ApiLine>,
    speedState: SpeedTestState,
    followState: FollowState,
) {
    SettingsCard("播放", "影响点播的选源、集数与起播行为") {
        Column(Modifier.fillMaxWidth()) {
            ToggleRow(
                title = "自动避开故障线路",
                description = "线路连续失败或长时间卡顿后，一段时间内优先选择其他线路",
                checked = settings.playbackAgentAutoSwitchEnabled,
                onChange = { enabled -> state.saveSettings { it.copy(playbackAgentAutoSwitchEnabled = enabled) } },
            )
            Spacer(Modifier.height(14.dp))
            StepperRow(
                title = "切源错误阈值",
                description = "同一线路连续失败多少次后自动换源",
                value = settings.autoSwitchErrorThreshold,
                range = 1..5,
                suffix = "次",
                onChange = { count -> state.saveSettings { it.copy(autoSwitchErrorThreshold = count) } },
            )
            Spacer(Modifier.height(14.dp))
            StepperRow(
                title = "缓冲超时",
                description = "起播超过这个时间仍未出画面，视为该线路不可用",
                value = settings.bufferingTimeoutSeconds,
                range = 5..60,
                suffix = "秒",
                onChange = { seconds -> state.saveSettings { it.copy(bufferingTimeoutSeconds = seconds) } },
            )
            Spacer(Modifier.height(14.dp))
            StepperRow(
                title = "预取后续集数",
                description = "提前解析后面几集的播放地址，切集更快（不下载视频文件）",
                value = settings.prefetchEpisodeCount,
                range = 0..5,
                suffix = "集",
                onChange = { count -> state.saveSettings { it.copy(prefetchEpisodeCount = count) } },
            )
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                title = "自动跳过片头",
                description = "按每部剧记录的片头结束位置跳过，倒计时 3 秒后执行",
                checked = settings.autoSkipIntro,
                onChange = { enabled -> state.saveSettings { it.copy(autoSkipIntro = enabled) } },
            )
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                title = "自动跳过片尾",
                description = "到达片尾标记后倒计时 8 秒，自动播放下一集",
                checked = settings.autoSkipOutro,
                onChange = { enabled -> state.saveSettings { it.copy(autoSkipOutro = enabled) } },
            )
            Spacer(Modifier.height(14.dp))
            SettingRow(title = "默认倍速", description = "进入播放页时的初始速度") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SPEED_OPTIONS.forEach { speed ->
                        Chip(
                            text = formatSpeed(speed),
                            selected = settings.preferredPlaybackSpeed == speed,
                            onClick = { state.saveSettings { it.copy(preferredPlaybackSpeed = speed) } },
                        )
                    }
                }
            }
        }
    }

    SettingsCard("测速", "按真实下载速率评估线路质量，不用 ping 冒充带宽") {
        Column(Modifier.fillMaxWidth()) {
            ToggleRow(
                title = "播放前测速",
                description = "打开详情页时自动测一次当前影片的播放源",
                checked = settings.speedTestOnOpen,
                onChange = { enabled -> state.saveSettings { it.copy(speedTestOnOpen = enabled) } },
            )
            Spacer(Modifier.height(14.dp))
            StepperRow(
                title = "定时测速",
                description = "后台按固定间隔测量所有线路，0 表示关闭",
                value = settings.speedTestIntervalMinutes,
                range = 0..180,
                step = 15,
                suffix = "分钟",
                onChange = { minutes ->
                    state.saveSettings { it.copy(speedTestIntervalMinutes = minutes) }
                    state.restartSpeedTimer()
                },
            )
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                title = "默认使用最快线路",
                description = "影片内测速完成后自动切到最快的播放源（可在详情页按剧覆盖）",
                checked = settings.preferFastestLine,
                onChange = { enabled -> state.saveSettings { it.copy(preferFastestLine = enabled) } },
            )
            Spacer(Modifier.height(16.dp))
            SpeedSummary(
                speedState = speedState,
                lineName = { id -> allLines.firstOrNull { it.id == id }?.name ?: id },
                onTest = { state.runLineSpeedTest() },
                onApply = { state.applyFastestLine() },
            )
        }
    }

    SettingsCard("追剧", "定时回访追剧条目，发现新集会在右下角提醒") {
        Column(Modifier.fillMaxWidth()) {
            StepperRow(
                title = "自动检查更新",
                description = "应用运行期间按此间隔检查，0 表示只在打开追剧页时检查",
                value = settings.followRefreshMinutes,
                range = 0..180,
                step = 15,
                suffix = "分钟",
                onChange = { minutes ->
                    state.saveSettings { it.copy(followRefreshMinutes = minutes) }
                    state.restartFollowTimer()
                },
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "当前追剧 ${followState.items.size} 部 · 有新集 ${followState.items.count { it.hasNewEpisodes }} 部",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                GhostButton(
                    text = if (followState.checking) "检查中…" else "立即检查",
                    enabled = !followState.checking && followState.items.isNotEmpty(),
                    onClick = { state.refreshFollows(force = true) },
                )
            }
        }
    }
}

@Composable
private fun NetworkSection(
    state: DesktopAppState,
    settings: AppSettings,
    proxyHost: String,
    proxyPort: String,
    serviceUrl: String,
    onProxyHostChange: (String) -> Unit,
    onProxyPortChange: (String) -> Unit,
    onServiceUrlChange: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var proxyStatus by remember { mutableStateOf(SaveStatus.Idle) }
    var serviceStatus by remember { mutableStateOf(SaveStatus.Idle) }

    SettingsCard("网络代理", "决定请求是否再套一层 HTTP 代理。Clash TUN 模式在系统层接管流量，应用内无法绕过") {
        Column(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProxyMode.entries.forEach { mode ->
                    Chip(
                        text = mode.displayName,
                        selected = ProxyMode.fromStorageKey(settings.proxyMode) == mode,
                        onClick = {
                            state.saveSettings { it.copy(proxyMode = mode.storageKey) }
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = ProxyMode.fromStorageKey(settings.proxyMode).description,
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textTertiary,
            )
            if (ProxyMode.fromStorageKey(settings.proxyMode) == ProxyMode.Custom) {
                // NetworkProxy 在自定义地址无效时会静默退回直连（core/data/NetworkProxy.kt），
                // 这里必须把已保存配置的真实效果说出来，避免用户以为代理生效了。
                if (settings.proxyHost.isBlank() || settings.proxyPort !in 1..65535) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "当前保存的代理地址无效，网络请求实际按直连处理",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.colors.warning,
                    )
                }
                Spacer(Modifier.height(12.dp))
                val portValue = proxyPort.trim().toIntOrNull()
                val inputValid = proxyHost.isNotBlank() && portValue != null && portValue in 1..65535
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    LabeledTextField(
                        label = "代理主机",
                        value = proxyHost,
                        onChange = {
                            onProxyHostChange(it)
                            if (proxyStatus != SaveStatus.Saving) proxyStatus = SaveStatus.Idle
                        },
                        placeholder = "127.0.0.1",
                        modifier = Modifier.weight(2f),
                    )
                    Spacer(Modifier.width(8.dp))
                    LabeledTextField(
                        label = "端口",
                        value = proxyPort,
                        onChange = { raw ->
                            onProxyPortChange(raw.filter { it.isDigit() }.take(5))
                            if (proxyStatus != SaveStatus.Saving) proxyStatus = SaveStatus.Idle
                        },
                        placeholder = "7890",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    PrimaryButton(
                        text = "保存",
                        enabled = proxyStatus != SaveStatus.Saving && inputValid,
                        onClick = {
                            val host = proxyHost.trim()
                            val port = portValue
                            if (host.isNotBlank() && port != null && port in 1..65535) {
                                proxyStatus = SaveStatus.Saving
                                state.saveSettings { it.copy(proxyHost = host, proxyPort = port) }
                                scope.launch {
                                    proxyStatus = if (proxySettingsPersisted(state, host, port)) {
                                        SaveStatus.Saved
                                    } else {
                                        SaveStatus.Failed
                                    }
                                }
                            }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                SaveStatusHint(
                    status = proxyStatus,
                    idle = "修改主机或端口后点击保存，立即生效",
                    error = if (!inputValid) "请填写代理主机和 1-65535 之间的端口" else null,
                )
            }
        }
    }

    SettingsCard("平台直播", "斗鱼、虎牙等直播间需要一个解析服务，地址可填本机或远程部署的服务") {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                LabeledTextField(
                    label = "解析服务地址",
                    value = serviceUrl,
                    onChange = {
                        onServiceUrlChange(it)
                        if (serviceStatus != SaveStatus.Saving) serviceStatus = SaveStatus.Idle
                    },
                    placeholder = "http://127.0.0.1:8787",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                PrimaryButton("保存") {
                    val normalized = serviceUrl.trim()
                    state.savePlatformLiveServiceUrl(normalized)
                    serviceStatus = if (state.platformLiveServiceUrl == normalized) {
                        SaveStatus.Saved
                    } else {
                        SaveStatus.Failed
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "当前：${state.platformLiveServiceUrl.ifBlank { "未配置" }}",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textTertiary,
            )
            Spacer(Modifier.height(4.dp))
            SaveStatusHint(serviceStatus, idle = "保存后立即按新地址重新加载直播站点")
        }
    }
}

@Composable
private fun StorageSection(state: DesktopAppState, settings: AppSettings) {
    SettingsCard("缓存", "图片与接口缓存，播放缓冲由 libmpv 管理") {
        Column(Modifier.fillMaxWidth()) {
            StepperRow(
                title = "图片缓存上限",
                description = "海报缩略图的磁盘缓存上限，修改后下次启动生效",
                value = settings.imageCacheMaxMb,
                range = 64..2048,
                step = 64,
                suffix = "MB",
                onChange = { mb -> state.saveSettings { it.copy(imageCacheMaxMb = mb) } },
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "播放缓冲由 libmpv 根据网络状况自动管理，无需手动调整。",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colors.textTertiary,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "图片缓存占用：${formatBytes(DesktopImages.cacheSizeBytes())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                GhostButton("清理图片缓存") {
                    DesktopImages.clearCaches()
                    state.notify("已清理图片缓存")
                    AppLog.info("Cache", "清理图片缓存")
                }
                Spacer(Modifier.width(8.dp))
                GhostButton("清理接口缓存") {
                    state.movieRepository.clearCaches()
                    state.notify("已清理接口缓存")
                    AppLog.info("Cache", "清理接口缓存")
                }
            }
        }
    }
}

@Composable
private fun AdvancedSection(state: DesktopAppState, settingsState: AppSettingsState, logState: LogState) {
    SettingsCard("播放健康记录", "本机记录的线路故障与恢复情况，用于自动选源") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${settingsState.playbackHealth.entryCount} 条线路记录 · 失败 ${settingsState.playbackHealth.failureCount} 次 · 慢缓冲 ${settingsState.playbackHealth.slowBufferCount} 次",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colors.textSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "清除后所有线路都会被当作健康状态重新评估",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.colors.textTertiary,
                )
            }
            GhostButton("清除记录", onClick = { state.clearPlaybackHealth() })
        }
    }

    LogCard(state, logState)
}

@Composable
private fun AboutSection() {
    SettingsCard("关于", "小鸡影视 Windows 桌面版") {
        Column {
            AboutRow("版本", DesktopVersion.name)
            AboutRow("数据目录", DesktopPaths.root.toString())
            AboutRow("播放内核", "libmpv（随应用分发）")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "桌面版与电视版共享同一套影视线路与播放策略，但不包含语音找片、扫码配置和 APK 更新等电视端专属能力。",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textTertiary,
            )
        }
    }
}

/** 保存结束后回读仓库，确认输入框的值确实写入了磁盘。 */
private suspend fun proxySettingsPersisted(state: DesktopAppState, host: String, port: Int): Boolean {
    withTimeoutOrNull(12_000) {
        state.settings.first { !it.saving }
    } ?: return false
    // 成功与失败的收尾状态相同，只能以落盘结果为准；失败时短暂重试，避免误报。
    repeat(4) { attempt ->
        if (attempt > 0) delay(120)
        val persisted = runCatching { state.settingsRepository.getSettings() }.getOrNull()
        if (persisted != null && persisted.proxyHost == host && persisted.proxyPort == port) return true
    }
    return false
}

@Composable
private fun SaveStatusHint(
    status: SaveStatus,
    idle: String,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    val invalid = status == SaveStatus.Idle && error != null
    val text = when {
        invalid -> error.orEmpty()
        status == SaveStatus.Idle -> idle
        status == SaveStatus.Saving -> "正在保存…"
        status == SaveStatus.Saved -> "已保存"
        else -> "保存失败，请重试"
    }
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = when {
            invalid -> AppColors.colors.danger
            status == SaveStatus.Idle -> AppColors.colors.textTertiary
            status == SaveStatus.Saving -> AppColors.colors.textSecondary
            status == SaveStatus.Saved -> AppColors.colors.accent
            else -> AppColors.colors.danger
        },
        modifier = modifier,
    )
}

@Composable
private fun HomeSourceCard(
    state: DesktopAppState,
    settings: AppSettings,
    allLines: List<ApiLine>,
    onOpenPicker: () -> Unit,
) {
    val speedState by state.speedTest.collectAsState()
    val current = allLines.firstOrNull { it.id == settings.homeApiLineId }
    val currentSpeed = current?.let { speedState.lineResults[it.id]?.formatLatency() }
    SettingsCard("首页", "决定首页、搜索和片库默认使用哪个资源站") {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "数据源",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.colors.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append(current?.name ?: "未选择")
                            append(" · 共 ${allLines.size} 个可选")
                            if (!currentSpeed.isNullOrBlank()) append(" · $currentSpeed")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.colors.textTertiary,
                    )
                }
                GhostButton("更换数据源", onClick = onOpenPicker)
            }
            Spacer(Modifier.height(16.dp))
            SettingRow(title = "首页内容", description = "豆瓣热播适合发现新剧；本站最新跟随数据源的更新速度") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HomeFeedMode.entries.forEach { mode ->
                        Chip(
                            text = mode.displayName,
                            selected = HomeFeedMode.fromStorageKey(settings.homeFeed) == mode,
                            onClick = { state.setHomeFeed(mode) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 数据源选择面板：六十多个线路全部平铺会让设置页变成一张巨大的网格，
 * 这里改成「当前线路 + 可搜索的列表面板」。
 */
@Composable
private fun SourcePickerOverlay(
    lines: List<ApiLine>,
    selectedId: String,
    speeds: Map<String, SpeedResult>,
    onDismiss: () -> Unit,
    onPick: (ApiLine) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }
    val filtered = remember(query, lines) {
        val keyword = query.trim()
        if (keyword.isEmpty()) lines else lines.filter {
            it.name.contains(keyword, ignoreCase = true) || it.id.contains(keyword, ignoreCase = true)
        }
    }
    val outside = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xC7050707))
            .clickable(interactionSource = outside, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        val inside = remember { MutableInteractionSource() }
        Column(
            Modifier
                .width(560.dp)
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.colors.surface)
                .border(1.dp, AppColors.colors.borderStrong, RoundedCornerShape(16.dp))
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
                .clickable(interactionSource = inside, indication = null) {}
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "选择数据源",
                    style = MaterialTheme.typography.titleLarge,
                    color = AppColors.colors.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${filtered.size} 个",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.colors.textTertiary,
                )
            }
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = AppColors.colors.textPrimary),
                cursorBrush = SolidColor(AppColors.colors.accent),
                modifier = Modifier
                    .focusRequester(searchFocus)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.colors.surfaceRaised)
                    .border(1.dp, AppColors.colors.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            "搜索名称或标识…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.colors.textTertiary,
                        )
                    }
                    inner()
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filtered, key = { it.id }) { line ->
                    SourceRow(
                        line = line,
                        selected = line.id == selectedId,
                        speed = speeds[line.id]?.formatLatency(),
                        onClick = { onPick(line) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "切换后会立即重新加载首页，搜索与片库也会跟随这里的选择。",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun SourceRow(line: ApiLine, selected: Boolean, speed: String?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(
                when {
                    selected -> AppColors.colors.accentSoft
                    hovered -> AppColors.colors.surfaceHover
                    else -> AppColors.colors.surfaceRaised
                },
            )
            .border(
                1.dp,
                if (selected) AppColors.colors.accent.copy(alpha = 0.5f) else AppColors.colors.border,
                RoundedCornerShape(9.dp),
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = line.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) AppColors.colors.accent else AppColors.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = line.baseUrls.firstOrNull().orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!speed.isNullOrBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = speed,
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.textSecondary,
            )
        }
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = "当前",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LogCard(state: DesktopAppState, logState: LogState) {
    SettingsCard("日志", "记录测速、切源、播放错误等事件，可按级别过滤并导出") {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogLevel.entries.forEach { level ->
                    Chip(
                        text = level.label,
                        selected = logState.minLevel == level,
                        onClick = { state.pushLogs(level, logState.sinceMinutes) },
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${logState.entries.size} 条",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.colors.textTertiary,
                )
                Spacer(Modifier.width(10.dp))
                GhostButton("导出") { exportLogs(state, logState) }
                Spacer(Modifier.width(8.dp))
                GhostButton("清空") {
                    AppLog.clear()
                    state.pushLogs(logState.minLevel, logState.sinceMinutes)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LOG_WINDOWS.forEach { (minutes, label) ->
                    Chip(
                        text = label,
                        selected = logState.sinceMinutes == minutes,
                        onClick = { state.pushLogs(logState.minLevel, minutes) },
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            if (logState.entries.isEmpty()) {
                Text(
                    "暂无日志",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.colors.textTertiary,
                )
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.colors.background)
                        .border(1.dp, AppColors.colors.border, RoundedCornerShape(8.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                ) {
                    logState.entries.takeLast(120).reversed().forEach { entry ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                text = AppLog.formatTime(entry.timeMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = AppColors.colors.textTertiary,
                                modifier = Modifier.width(96.dp),
                            )
                            Text(
                                text = entry.level.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = when (entry.level) {
                                    LogLevel.Error -> AppColors.colors.danger
                                    LogLevel.Warn -> AppColors.colors.warning
                                    LogLevel.Info -> AppColors.colors.textSecondary
                                },
                                modifier = Modifier.width(52.dp),
                            )
                            Text(
                                text = entry.tag,
                                style = MaterialTheme.typography.labelMedium,
                                color = AppColors.colors.accent,
                                modifier = Modifier.width(84.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = entry.message,
                                style = MaterialTheme.typography.labelMedium,
                                color = AppColors.colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun exportLogs(state: DesktopAppState, logState: LogState) {
    val stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    val file = File(DesktopPaths.logs.toFile(), "xiaoji-$stamp.log")
    val cutoff = if (logState.sinceMinutes > 0) {
        System.currentTimeMillis() - logState.sinceMinutes * 60_000L
    } else {
        0L
    }
    val count = runCatching { AppLog.export(file, cutoff, logState.minLevel) }.getOrDefault(0)
    if (count > 0) {
        state.notify("已导出 $count 条日志：${file.name}")
    } else {
        state.notify("没有可导出的日志")
    }
}

@Composable
private fun SpeedSummary(
    speedState: SpeedTestState,
    lineName: (String) -> String,
    onTest: () -> Unit,
    onApply: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (speedState.lastRunAtMs > 0) {
                val fastest = speedState.fastestLineId()?.let(lineName) ?: "—"
                "上次测速：${AppLog.formatTime(speedState.lastRunAtMs)} · 最快：$fastest"
            } else {
                "还没有测速结果"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        GhostButton(if (speedState.running) "测速中…" else "立即测速", enabled = !speedState.running, onClick = onTest)
        Spacer(Modifier.width(8.dp))
        PrimaryButton("使用最快线路", onClick = onApply)
    }
}

@Composable
private fun SettingsCard(title: String, description: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.colors.surface)
            .border(1.dp, AppColors.colors.border, RoundedCornerShape(12.dp))
            .padding(20.dp),
    ) {
        SectionHeader(title) {}
        Text(
            text = description,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colors.textTertiary,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        content()
    }
}

@Composable
private fun SettingRow(title: String, description: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.colors.textPrimary)
            Spacer(Modifier.height(3.dp))
            Text(description, style = MaterialTheme.typography.labelMedium, color = AppColors.colors.textTertiary)
        }
        Spacer(Modifier.width(20.dp))
        control()
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, description) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF06150E),
                checkedTrackColor = AppColors.colors.accent,
                uncheckedThumbColor = AppColors.colors.textSecondary,
                uncheckedTrackColor = AppColors.colors.surfaceActive,
                uncheckedBorderColor = AppColors.colors.border,
            ),
        )
    }
}

@Composable
private fun StepperRow(
    title: String,
    description: String,
    value: Int,
    range: IntRange,
    suffix: String,
    step: Int = 1,
    onChange: (Int) -> Unit,
) {
    SettingRow(title, description) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton("−", enabled = value > range.first) {
                onChange((value - step).coerceIn(range))
            }
            Box(Modifier.width(74.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (value == 0) "关闭" else "$value$suffix",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.colors.textPrimary,
                )
            }
            StepperButton("+", enabled = value < range.last) {
                onChange((value + step).coerceIn(range))
            }
        }
    }
}

@Composable
private fun StepperButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    !enabled -> AppColors.colors.surface
                    hovered -> AppColors.colors.surfaceActive
                    else -> AppColors.colors.surfaceRaised
                },
            )
            .border(1.dp, AppColors.colors.border, RoundedCornerShape(6.dp))
            .hoverable(interaction, enabled = enabled)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) AppColors.colors.textPrimary else AppColors.colors.textTertiary,
        )
    }
}

@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colors.textSecondary,
        )
        Spacer(Modifier.height(6.dp))
        TextField(value, onChange, placeholder)
    }
}

@Composable
private fun TextField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.colors.surface)
            .border(
                1.dp,
                if (focused) AppColors.colors.accent.copy(alpha = 0.65f) else AppColors.colors.border,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppColors.colors.textPrimary),
            cursorBrush = SolidColor(AppColors.colors.accent),
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = AppColors.colors.textTertiary)
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colors.textTertiary,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colors.textSecondary,
        )
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format("%.1f GB", bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024)
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
