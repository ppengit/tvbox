package com.tvbox.desktop.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvbox.app.data.SpeedResult
import com.tvbox.app.domain.PlaybackIssueType
import com.tvbox.desktop.state.DesktopAppState
import com.tvbox.desktop.state.PlayerSourceOption
import com.tvbox.desktop.state.PlayerState
import com.tvbox.desktop.state.*
import com.tvbox.desktop.ui.components.ExitFullscreenIcon
import com.tvbox.desktop.ui.components.FullscreenIcon
import com.tvbox.desktop.ui.components.PauseIcon
import com.tvbox.desktop.ui.components.PlayIcon
import com.tvbox.desktop.ui.components.SkipNextIcon
import com.tvbox.desktop.ui.components.SkipPreviousIcon
import com.tvbox.desktop.ui.components.VolumeIcon
import com.tvbox.desktop.ui.theme.AppColors
import kotlinx.coroutines.delay
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.PlaybackEvent
import org.openani.mediamp.compose.MediampPlayerSurface
import org.openani.mediamp.compose.rememberMediampPlayer
import org.openani.mediamp.errorOrNull
import org.openani.mediamp.features.AudioLevelController
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.toggleMute
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.togglePlayWhenReady

private const val CONTROLS_IDLE_MS = 3_000L
private const val SEEK_STEP_MS = 10_000L
private const val VOLUME_STEP = 0.05f
private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private data class SeekHint(
    val forward: Boolean,
    val seconds: Long,
    val targetMs: Long,
    val durationMs: Long,
    val nonce: Int,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(
    state: DesktopAppState,
    playerState: PlayerState,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
) {
    val player = rememberMediampPlayer()
    val playState by player.state.collectAsState()
    val positionMs by player.currentPositionMillis.collectAsState()
    val mediaProps by player.mediaProperties.collectAsState()
    val settingsState by state.settings.collectAsState()
    val detailState by state.detail.collectAsState()
    val settings = settingsState.settings
    val sourceSpeeds = detailState?.sourceSpeeds.orEmpty()

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var speed by remember(playerState.currentUrl) { mutableStateOf(settings.preferredPlaybackSpeed) }
    var retryToken by remember { mutableIntStateOf(0) }
    var pendingStart by remember(playerState.currentUrl) { mutableLongStateOf(playerState.startPositionMs) }
    var showSourceMenu by remember { mutableStateOf(false) }
    var errorStreak by remember(playerState.currentUrl) { mutableIntStateOf(0) }
    // 换源后旧媒体的 Error 仍留在 mediaStatus 里：同一个错误对象只处理一次，
    // 否则一次起播失败会被当成新线路的失败，连续跳过多条本来可用的线路。
    var lastHandledError by remember { mutableStateOf<Throwable?>(null) }
    var bufferingStartedAt by remember { mutableLongStateOf(0L) }

    val durationMs = mediaProps?.durationMillis ?: 0L
    // 时长要等媒体属性就绪后才拿得到；定时落盘和退出补存都在长生命周期协程里，
    // 必须读最新值，否则会一直把 0 写进观看历史。
    val latestDurationMs by rememberUpdatedState(durationMs)
    val volumeFeature = player.features[AudioLevelController]
    val speedFeature = player.features[PlaybackSpeed]
    val muted by (volumeFeature?.isMute?.collectAsState() ?: remember { mutableStateOf(false) })
    val volume by (volumeFeature?.volume?.collectAsState() ?: remember { mutableStateOf(1f) })
    val options = state.playerSourceOptions()
    val fastestIndex = sourceSpeeds.entries
        .filter { it.value.ok }
        .minWithOrNull(compareBy({ it.value.latencyMs.coerceAtLeast(1L) }, { -it.value.kbps }))
        ?.key
    // 点播到第一集/最后一集时禁用切集按钮，避免「点了没反应」；直播频道首尾循环，保持可用。
    val hasPreviousEpisode = playerState.isLive || playerState.episodeIndex > 0
    val hasNextEpisode = playerState.isLive || playerState.episodeCount <= 0 ||
        playerState.episodeIndex < playerState.episodeCount - 1
    val currentLiveChannel = playerState.liveChannels.getOrNull(playerState.liveChannelIndex)
    val canSwitchLiveLine = playerState.isLive && playerState.platformStream == null &&
        (currentLiveChannel?.lines?.size ?: 0) > 1
    val skipState by state.skipMarks.collectAsState()
    val marks = playerState.movie?.let { skipState["${it.apiLineId}:${it.id}"] }
    var skipPanelOpen by remember { mutableStateOf(false) }
    var introHandled by remember(playerState.currentUrl) { mutableStateOf(false) }
    var outroHandled by remember(playerState.currentUrl) { mutableStateOf(false) }
    var introCountdown by remember(playerState.currentUrl) { mutableIntStateOf(0) }
    var outroCountdown by remember(playerState.currentUrl) { mutableIntStateOf(0) }
    var readySinceOpen by remember(playerState.currentUrl) { mutableStateOf(false) }
    var openingSeconds by remember(playerState.currentUrl) { mutableIntStateOf(0) }
    var seekHint by remember { mutableStateOf<SeekHint?>(null) }
    var seekAccumulatedMs by remember { mutableLongStateOf(0L) }
    var seekLastAtMs by remember { mutableLongStateOf(0L) }
    var seekTargetMs by remember { mutableLongStateOf(0L) }

    val introEndMs = marks?.introEndMs ?: 0L
    val outroStartMs = marks?.outroStartMs ?: 0L
    val inIntro = !playerState.isLive && introEndMs > 0L && positionMs < introEndMs && introEndMs < 30 * 60_000L
    val inOutro = !playerState.isLive && outroStartMs > 0L && durationMs > outroStartMs + 5_000L &&
        positionMs >= outroStartMs

    fun markInteraction() {
        lastInteraction = System.currentTimeMillis()
        controlsVisible = true
    }

    fun setVolume(next: Float) {
        volumeFeature?.setVolume(next.coerceIn(0f, 1f))
    }

    fun applySpeed(next: Float) {
        speed = next
        runCatching { speedFeature?.set(next) }
    }

    /**
     * 方向键快进/快退：单次 10 秒，连续按键在 0.9 秒内累加并给出一块 OSD，
     * 让用户看到「本次一共快进了多少、目标位置在哪」，而不是只跳一下没有任何反馈。
     */
    fun seekBy(deltaMs: Long) {
        val now = System.currentTimeMillis()
        val continuing = seekAccumulatedMs != 0L &&
            (seekAccumulatedMs > 0L) == (deltaMs > 0L) &&
            now - seekLastAtMs <= 900L
        val accumulated = if (continuing) seekAccumulatedMs + deltaMs else deltaMs
        val base = if (continuing) seekTargetMs else positionMs
        val upper = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (base + deltaMs).coerceIn(0L, upper)
        seekAccumulatedMs = accumulated
        seekLastAtMs = now
        seekTargetMs = target
        player.seekTo(target)
        seekHint = SeekHint(
            forward = accumulated > 0L,
            seconds = kotlin.math.abs(accumulated) / 1000,
            targetMs = target,
            durationMs = durationMs,
            nonce = (seekHint?.nonce ?: 0) + 1,
        )
        markInteraction()
    }

    // 装载媒体：URL 变化时重新打开，起始位置默认沿用进度。
    LaunchedEffect(playerState.currentUrl, retryToken) {
        if (playerState.currentUrl.isBlank()) return@LaunchedEffect
        errorMessage = null
        errorStreak = 0
        readySinceOpen = false
        openingSeconds = 0
        bufferingStartedAt = 0L
        runCatching {
            player.setMediaData(
                data = UriMediaData(
                    uri = playerState.currentUrl,
                    headers = playerState.headers,
                    extraFiles = MediaExtraFiles.EMPTY,
                ),
                playWhenReady = true,
                startPositionMillis = pendingStart,
            )
            applySpeed(speed)
        }.onFailure { throwable ->
            val reason = throwable.localizedMessage ?: "无法打开该线路"
            // 起播就失败时，只要还有其他线路就静默换源，不把错误先抛给用户。
            val canTryFailover = !playerState.isLive && options.size > 1
            val switched = canTryFailover && state.handlePlaybackFailure(reason)
            if (switched) {
                errorMessage = null
                errorStreak = 0
            } else {
                errorMessage = reason
                // handlePlaybackFailure 已经在失败线路上记过一次，这里不要再记一遍。
                if (!playerState.isLive && !canTryFailover) {
                    state.recordPlaybackIssue(PlaybackIssueType.Error)
                }
            }
        }
    }

    // 用户主动点「重试」：允许同一个错误对象再被处理一次。
    LaunchedEffect(retryToken) {
        if (retryToken > 0) lastHandledError = null
    }

    // 播放结束：点播自动下一集；直播不处理。
    LaunchedEffect(player) {
        player.events.collect { event ->
            if (event is PlaybackEvent.MediaEnded && !playerState.isLive) {
                state.savePlayerProgress(event.finalPositionMillis, event.durationMillis ?: durationMs)
                state.playNextEpisode()
            }
        }
    }

    // 播放错误：达到阈值且开着自动避障时先换线路，换不动才把错误抛给用户。
    LaunchedEffect(playState.mediaStatus, playerState.currentUrl) {
        when (val status = playState.mediaStatus) {
            is MediaStatus.Error -> {
                val mediaError = status.error
                // 换源后 mediaStatus 仍是旧线路的 Error：同一个错误只处理一次。
                if (lastHandledError === mediaError) return@LaunchedEffect
                lastHandledError = mediaError
                val reason = mediaError.localizedMessage ?: "播放失败"
                errorStreak++
                // 还没出过画面的首次起播：哪怕用户关了自动避障，也要先把其他线路试一遍；
                // 出过画面之后的异常才交给“自动避障”设置决定。
                val canFailover = !playerState.isLive &&
                    options.size > 1 &&
                    (!readySinceOpen || state.effectiveAutoSwitch(playerState.movie))
                val threshold = if (readySinceOpen) {
                    settings.autoSwitchErrorThreshold.coerceAtLeast(1)
                } else {
                    1
                }
                if (canFailover && errorStreak >= threshold) {
                    // 换源失败（所有线路都试过）时，这条错误已经由状态层记录。
                    if (state.handlePlaybackFailure(reason)) {
                        errorMessage = null
                        errorStreak = 0
                    } else {
                        errorMessage = reason
                    }
                } else {
                    errorMessage = reason
                    if (!playerState.isLive) state.recordPlaybackIssue(PlaybackIssueType.Error)
                }
            }
            MediaStatus.Ready -> {
                // 新媒体正常出画面后，后续同名错误要重新计数。
                lastHandledError = null
                errorMessage = null
                errorStreak = 0
                readySinceOpen = true
                if (!playerState.isLive) state.recordPlaybackSuccess()
            }
            else -> Unit
        }
    }

    // 缓冲看护：起播或卡缓冲超过阈值视为线路不可用，按设置决定是否自动换源。
    LaunchedEffect(playState.isBuffering, playState.mediaStatus) {
        val buffering = playState.isBuffering || playState.mediaStatus == MediaStatus.Opening
        bufferingStartedAt = when {
            !buffering -> 0L
            bufferingStartedAt == 0L -> System.currentTimeMillis()
            else -> bufferingStartedAt
        }
    }
    LaunchedEffect(playerState.currentUrl) {
        while (true) {
            delay(1_000)
            val timeoutSeconds = settings.bufferingTimeoutSeconds
            val startedAt = bufferingStartedAt
            if (playerState.isLive || timeoutSeconds <= 0 || startedAt == 0L) continue
            if (System.currentTimeMillis() - startedAt < timeoutSeconds * 1000L) continue
            bufferingStartedAt = 0L
            val switched = state.effectiveAutoSwitch(playerState.movie) && options.size > 1 &&
                state.handlePlaybackFailure("缓冲超过 $timeoutSeconds 秒")
            errorMessage = if (switched) null else "缓冲超过 $timeoutSeconds 秒，线路可能不可用"
        }
    }

    // 片头/片尾自动跳过：先给倒计时，留出反悔时间。
    LaunchedEffect(inIntro, playerState.currentUrl, settings.autoSkipIntro) {
        if (!inIntro || introHandled || !settings.autoSkipIntro) {
            introCountdown = 0
            return@LaunchedEffect
        }
        introCountdown = 3
        while (introCountdown > 0) {
            delay(1_000)
            introCountdown--
        }
        if (!introHandled && player.currentPositionMillis.value < introEndMs) {
            player.seekTo(introEndMs)
            introHandled = true
        }
    }
    LaunchedEffect(inOutro, playerState.currentUrl, settings.autoSkipOutro) {
        if (!inOutro || outroHandled || !settings.autoSkipOutro) {
            outroCountdown = 0
            return@LaunchedEffect
        }
        outroCountdown = 8
        while (outroCountdown > 0) {
            delay(1_000)
            outroCountdown--
        }
        if (!outroHandled && player.currentPositionMillis.value >= outroStartMs) {
            outroHandled = true
            state.playNextEpisode()
        }
    }

    // 每 10 秒落一次进度，退出时还会补一次。
    LaunchedEffect(playerState.currentUrl) {
        while (true) {
            delay(10_000)
            if (!playerState.isLive) {
                state.savePlayerProgress(player.currentPositionMillis.value, latestDurationMs)
            }
        }
    }

    // 快进/快退提示停留 1.4 秒后自动消失。
    LaunchedEffect(seekHint?.nonce) {
        val hint = seekHint ?: return@LaunchedEffect
        delay(1_400)
        if (seekHint?.nonce == hint.nonce) seekHint = null
    }

    // 起播计时：用于在加载层里显示等待秒数，超过缓冲阈值时明确提示会自动换源。
    LaunchedEffect(playerState.currentUrl) {
        openingSeconds = 0
        while (!readySinceOpen) {
            delay(1_000)
            if (!readySinceOpen) openingSeconds++
        }
    }

    LaunchedEffect(playerState.switchNotice) {
        if (playerState.switchNotice != null) {
            delay(4_000)
            state.clearSwitchNotice()
        }
    }

    // 控制层自动隐藏。面板展开期间保持可见，否则面板会随控制层一起消失。
    LaunchedEffect(lastInteraction, playState.playWhenReady, showSourceMenu, skipPanelOpen, errorMessage) {
        while (true) {
            delay(400)
            val idle = System.currentTimeMillis() - lastInteraction
            controlsVisible = idle < CONTROLS_IDLE_MS || !playState.playWhenReady || showSourceMenu ||
                skipPanelOpen || errorMessage != null
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPointerEvent(PointerEventType.Move) { markInteraction() }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                markInteraction()
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta != 0f) setVolume(volume + if (delta < 0) VOLUME_STEP else -VOLUME_STEP)
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Spacebar -> {
                        player.togglePlayWhenReady()
                        markInteraction()
                        true
                    }
                    Key.DirectionLeft -> {
                        if (!playerState.isLive) seekBy(-SEEK_STEP_MS)
                        markInteraction()
                        true
                    }
                    Key.DirectionRight -> {
                        if (!playerState.isLive) seekBy(SEEK_STEP_MS)
                        markInteraction()
                        true
                    }
                    Key.DirectionUp -> {
                        setVolume(volume + VOLUME_STEP)
                        markInteraction()
                        true
                    }
                    Key.DirectionDown -> {
                        setVolume(volume - VOLUME_STEP)
                        markInteraction()
                        true
                    }
                    Key.M -> {
                        volumeFeature?.toggleMute()
                        markInteraction()
                        true
                    }
                    Key.Equals, Key.Plus, Key.NumPadAdd -> {
                        applySpeed(SPEED_OPTIONS.firstOrNull { it > speed } ?: SPEED_OPTIONS.last())
                        markInteraction()
                        true
                    }
                    Key.Minus, Key.NumPadSubtract -> {
                        applySpeed(SPEED_OPTIONS.lastOrNull { it < speed } ?: SPEED_OPTIONS.first())
                        markInteraction()
                        true
                    }
                    Key.N -> {
                        if (!playerState.isLive) state.playNextEpisode()
                        markInteraction()
                        true
                    }
                    Key.P -> {
                        if (!playerState.isLive) state.playPreviousEpisode()
                        markInteraction()
                        true
                    }
                    Key.S -> {
                        if (!playerState.isLive && options.size > 1) {
                            val position = options.indexOfFirst { it.index == playerState.sourceIndex }
                            state.switchPlayerSource(options[(position + 1) % options.size].index)
                        }
                        markInteraction()
                        true
                    }
                    Key.F -> {
                        onToggleFullscreen()
                        markInteraction()
                        true
                    }
                    Key.Escape -> {
                        when {
                            skipPanelOpen -> skipPanelOpen = false
                            showSourceMenu -> showSourceMenu = false
                            isFullscreen -> onToggleFullscreen()
                            else -> state.closePlayer()
                        }
                        markInteraction()
                        true
                    }
                    else -> false
                }
            },
    ) {
        MediampPlayerSurface(player, Modifier.fillMaxSize())

        if (playState.isLoadingOrBuffering() && errorMessage == null) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BufferedSpinner(44.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (!readySinceOpen) {
                            "正在打开「${playerState.sourceName.ifBlank { "当前线路" }}」"
                        } else {
                            "缓冲中…"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    // 直播没有「集」的概念，起播层不要拼出「第 1 集」这种误导文案。
                    val loadingSubtitle = if (playerState.isLive) {
                        ""
                    } else {
                        listOfNotNull(
                            playerState.episodeTitle.takeIf { it.isNotBlank() },
                            "第 ${playerState.episodeIndex + 1} 集",
                        ).distinct().joinToString(" · ")
                    }
                    if (loadingSubtitle.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = loadingSubtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xB3FFFFFF),
                        )
                    }
                    if (!readySinceOpen && openingSeconds >= 1) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (openingSeconds >= 4) {
                                "已等待 ${openingSeconds} 秒，超过 ${settings.bufferingTimeoutSeconds} 秒无画面会自动切换线路"
                            } else {
                                "已等待 ${openingSeconds} 秒，线路较慢时会自动切换"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0x99FFFFFF),
                        )
                    }
                }
            }
        }

        // 快进/快退反馈：连续按键时显示累计秒数和目标位置，避免“按了没反应”的错觉。
        AnimatedVisibility(
            visible = seekHint != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 128.dp),
        ) {
            val hint = seekHint ?: return@AnimatedVisibility
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xD9101615))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (hint.forward) "▶▶" else "◀◀",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.colors.accent,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${if (hint.forward) "快进" else "快退"} ${hint.seconds} 秒",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = if (hint.durationMs > 0L) {
                        "${formatTime(hint.targetMs)} / ${formatTime(hint.durationMs)}"
                    } else {
                        formatTime(hint.targetMs)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xB3FFFFFF),
                )
            }
        }

        // 顶部信息层
        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xD9000000), Color.Transparent)))
                    .padding(horizontal = 26.dp, vertical = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackChip(onClick = { state.closePlayer() })
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = playerState.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = if (playerState.isLive) {
                            "直播"
                        } else {
                            listOfNotNull(
                                playerState.episodeTitle.takeIf { it.isNotBlank() },
                                if (playerState.episodeCount > 1) {
                                    "共 ${playerState.episodeCount} 集"
                                } else {
                                    null
                                },
                                playerState.sourceName.takeIf { it.isNotBlank() }?.let { "线路 $it" },
                            ).joinToString(" · ")
                        }
                        if (subtitle.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xCCFFFFFF),
                            )
                        }
                    }
                }
            }
        }

        // 底部控制层
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .padding(start = 26.dp, end = 26.dp, top = 40.dp, bottom = 18.dp),
            ) {
                Column {
                    if (durationMs > 0L && !playerState.isLive) {
                        SeekBar(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            bufferedMs = 0L,
                            enabled = true,
                            onSeek = { target ->
                                player.seekTo(target)
                                markInteraction()
                            },
                        )
                        Spacer(Modifier.height(2.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ControlIconButton(
                            label = if (playState.playWhenReady) "暂停" else "播放",
                            onClick = {
                                player.togglePlayWhenReady()
                                markInteraction()
                            },
                        ) {
                            if (playState.playWhenReady) {
                                PauseIcon(Color.White, 26.dp)
                            } else {
                                PlayIcon(Color.White, 26.dp)
                            }
                        }

                        Spacer(Modifier.width(4.dp))

                        if (playerState.isLive) {
                            ControlIconButton(label = "上一个频道", onClick = { state.stepLiveChannel(-1); markInteraction() }) {
                                SkipPreviousIcon(Color.White, 20.dp)
                            }
                            ControlIconButton(label = "下一个频道", onClick = { state.stepLiveChannel(1); markInteraction() }) {
                                SkipNextIcon(Color.White, 20.dp)
                            }
                        } else {
                            ControlIconButton(
                                label = "上一集",
                                enabled = hasPreviousEpisode,
                                onClick = { state.playPreviousEpisode(); markInteraction() },
                            ) {
                                SkipPreviousIcon(Color.White, 20.dp)
                            }
                            ControlIconButton(
                                label = "下一集",
                                enabled = hasNextEpisode,
                                onClick = { state.playNextEpisode(); markInteraction() },
                            ) {
                                SkipNextIcon(Color.White, 20.dp)
                            }
                        }

                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = if (playerState.isLive) {
                                "LIVE"
                            } else {
                                "${formatTime(positionMs)} / ${formatTime(durationMs)}"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xE6FFFFFF),
                            fontSize = 12.sp,
                        )

                        Spacer(Modifier.weight(1f))

                        ControlIconButton(
                            label = if (muted) "取消静音" else "静音",
                            onClick = { volumeFeature?.toggleMute(); markInteraction() },
                        ) {
                            VolumeIcon(Color.White, muted = muted, size = 20.dp)
                        }
                        Text(
                            text = "${(volume.coerceIn(0f, 1f) * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0x99FFFFFF),
                            modifier = Modifier.width(44.dp),
                        )

                        Spacer(Modifier.width(8.dp))
                        TextChip(
                            text = "${trimSpeed(speed)}x",
                            onClick = {
                                val next = SPEED_OPTIONS[(SPEED_OPTIONS.indexOf(speed) + 1) % SPEED_OPTIONS.size]
                                applySpeed(next)
                                markInteraction()
                            },
                        )

                        Spacer(Modifier.width(8.dp))
                        if (!playerState.isLive) {
                            TextChip(
                                text = if (marks?.hasIntro == true || marks?.hasOutro == true) "片头尾 ●" else "片头尾",
                                onClick = {
                                    skipPanelOpen = !skipPanelOpen
                                    if (skipPanelOpen) showSourceMenu = false
                                    markInteraction()
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        if (!playerState.isLive && options.size > 1) {
                            TextChip(
                                text = buildString {
                                    // 线路名比序号有用；同一条线路在不同剧里序号会变，名字才认得出。
                                    append(playerState.sourceName.ifBlank { "线路 ${playerState.sourceIndex + 1}" })
                                    if (fastestIndex == playerState.sourceIndex) append(" · 最快")
                                },
                                modifier = Modifier.widthIn(max = 240.dp),
                                onClick = {
                                    showSourceMenu = !showSourceMenu
                                    if (showSourceMenu) skipPanelOpen = false
                                    markInteraction()
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        if (canSwitchLiveLine) {
                            TextChip(
                                text = "换线路",
                                onClick = {
                                    state.stepLiveLine(1)
                                    markInteraction()
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                        }

                        ControlIconButton(
                            label = if (isFullscreen) "退出全屏" else "全屏",
                            onClick = { onToggleFullscreen(); markInteraction() },
                        ) {
                            if (isFullscreen) {
                                ExitFullscreenIcon(Color.White, 20.dp)
                            } else {
                                FullscreenIcon(Color.White, 20.dp)
                            }
                        }
                    }
                }
            }
        }

        // 设置面板做成根层浮层：控制栏高度有限，嵌在胶囊按钮里会被窗口下沿裁掉。
        if (!playerState.isLive && skipPanelOpen) {
            PopoverDismissLayer { skipPanelOpen = false }
            SkipPanel(
                marks = marks,
                positionMs = positionMs,
                autoIntro = settings.autoSkipIntro,
                autoOutro = settings.autoSkipOutro,
                onSetIntro = { playerState.movie?.let { state.setIntroEnd(it, positionMs) } },
                onSetOutro = { playerState.movie?.let { state.setOutroStart(it, positionMs) } },
                onClearIntro = { playerState.movie?.let { state.clearIntroMark(it) } },
                onClearOutro = { playerState.movie?.let { state.clearOutroMark(it) } },
                onClear = { playerState.movie?.let { state.clearSkipMarks(it) } },
                onToggleIntro = {
                    state.saveSettings { it.copy(autoSkipIntro = !settings.autoSkipIntro) }
                },
                onToggleOutro = {
                    state.saveSettings { it.copy(autoSkipOutro = !settings.autoSkipOutro) }
                },
                onDismiss = { skipPanelOpen = false },
                // 贴着控制栏右侧弹出，和「片头尾」胶囊在同一侧，不要飘到窗口最左边。
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 76.dp),
            )
        }
        if (!playerState.isLive && showSourceMenu && options.size > 1) {
            PopoverDismissLayer { showSourceMenu = false }
            SourceMenu(
                options = options,
                speeds = sourceSpeeds,
                fastestIndex = fastestIndex,
                current = playerState.sourceIndex,
                testing = detailState?.speedTesting == true,
                onSpeedTest = { state.runSourceSpeedTest() },
                onSelect = { index ->
                    showSourceMenu = false
                    state.switchPlayerSource(index)
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 76.dp),
            )
        }

        // 片头/片尾提示：右下角浮层，点击立即跳过，倒计时结束自动执行。
        // 片头尾面板或线路菜单展开时隐藏，否则三者都贴右下角会互相压住。
        if (!playerState.isLive && (inIntro || inOutro) && !skipPanelOpen && !showSourceMenu) {
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 112.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (inIntro) {
                    SkipPrompt(
                        title = "跳过片头",
                        detail = if (settings.autoSkipIntro && !introHandled) {
                            "${introCountdown.coerceAtLeast(1)} 秒后自动跳过"
                        } else {
                            "片头 ${formatSkipTime(introEndMs)} 结束"
                        },
                        onClick = {
                            player.seekTo(introEndMs)
                            introHandled = true
                        },
                    )
                }
                if (inOutro) {
                    SkipPrompt(
                        title = "跳过片尾",
                        detail = if (settings.autoSkipOutro && !outroHandled) {
                            "${outroCountdown.coerceAtLeast(1)} 秒后播放下一集"
                        } else {
                            "片尾开始于 ${formatSkipTime(outroStartMs)}"
                        },
                        onClick = {
                            outroHandled = true
                            state.playNextEpisode()
                        },
                    )
                }
            }
        }

        // 错误层
        if (errorMessage != null) {
            Box(
                Modifier.fillMaxSize().background(Color(0xD9050807)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .width(540.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.colors.surface)
                        .border(1.dp, AppColors.colors.borderStrong, RoundedCornerShape(14.dp))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("播放失败", style = MaterialTheme.typography.titleLarge, color = AppColors.colors.danger)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colors.textSecondary,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlayerActionButton("重试") {
                            pendingStart = player.currentPositionMillis.value
                            retryToken++
                            markInteraction()
                        }
                        if (!playerState.isLive && options.size > 1) {
                            PlayerActionButton("换最优线路") {
                                val target = fastestIndex?.takeIf { it != playerState.sourceIndex }
                                    ?: options.firstOrNull { it.index != playerState.sourceIndex }?.index
                                if (target != null) state.switchPlayerSource(target)
                                markInteraction()
                            }
                        }
                        if (canSwitchLiveLine) {
                            PlayerActionButton("换线路") {
                                errorMessage = null
                                state.stepLiveLine(1)
                                markInteraction()
                            }
                        }
                        PlayerActionButton("返回") { state.closePlayer() }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "快捷键：空格 暂停 · ←/→ 快退快进 · ↑/↓ 音量 · +/− 倍速 · N/P 切集 · S 换线路 · F 全屏",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.colors.textTertiary,
                    )
                }
            }
        }

        if (playerState.switchNotice != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier
                        .padding(top = 88.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC101615))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(playerState.switchNotice, style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(playerState.currentUrl) {
        onDispose {
            if (!playerState.isLive) {
                state.savePlayerProgress(player.currentPositionMillis.value, latestDurationMs)
            }
        }
    }
}

/** 透明遮罩：点浮层以外的任何位置都收起，避免面板只能通过原按钮关掉。 */
@Composable
private fun PopoverDismissLayer(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )
}

/**
 * 片头片尾记录面板。剧集之间片头片尾通常一致，所以标记按剧保存；
 * 这里同时提供「用当前进度」和自动跳过开关，避免去设置页来回切。
 */
@Composable
private fun SkipPanel(
    marks: com.tvbox.desktop.data.SkipMarks?,
    positionMs: Long,
    autoIntro: Boolean,
    autoOutro: Boolean,
    onSetIntro: () -> Unit,
    onSetOutro: () -> Unit,
    onClearIntro: () -> Unit,
    onClearOutro: () -> Unit,
    onClear: () -> Unit,
    onToggleIntro: () -> Unit,
    onToggleOutro: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(320.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.colors.surfaceRaised)
            .border(1.dp, AppColors.colors.borderStrong, RoundedCornerShape(8.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "片头片尾 · 本剧通用",
                style = MaterialTheme.typography.labelLarge,
                color = AppColors.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            PanelCloseButton(onClick = onDismiss)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            "在片头结束、片尾开始处各点一次，之后每集都按这个位置跳过。",
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colors.textTertiary,
        )
        Spacer(Modifier.height(12.dp))
        MarkRow(
            label = "片头结束",
            value = formatSkipTime(marks?.introEndMs ?: 0L),
            action = "用当前 ${formatSkipTime(positionMs)}",
            onSet = onSetIntro,
            onClear = onClearIntro.takeIf { marks?.hasIntro == true },
        )
        Spacer(Modifier.height(8.dp))
        MarkRow(
            label = "片尾开始",
            value = formatSkipTime(marks?.outroStartMs ?: 0L),
            action = "用当前 ${formatSkipTime(positionMs)}",
            onSet = onSetOutro,
            onClear = onClearOutro.takeIf { marks?.hasOutro == true },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TogglePill(text = "自动跳过片头", checked = autoIntro, onClick = onToggleIntro)
            TogglePill(text = "自动跳过片尾", checked = autoOutro, onClick = onToggleOutro)
        }
        if (marks != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "清除本剧标记",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colors.danger,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onClear)
                    .padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PanelCloseButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (hovered) AppColors.colors.surfaceActive else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "✕",
            style = MaterialTheme.typography.labelMedium,
            color = if (hovered) AppColors.colors.textPrimary else AppColors.colors.textTertiary,
        )
    }
}

@Composable
private fun MarkRow(
    label: String,
    value: String,
    action: String,
    onSet: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.colors.textTertiary)
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                color = AppColors.colors.textPrimary,
            )
        }
        if (onClear != null) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text("清除", style = MaterialTheme.typography.labelMedium, color = AppColors.colors.danger)
            }
            Spacer(Modifier.width(4.dp))
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(AppColors.colors.surfaceActive)
                .clickable(onClick = onSet)
                .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            Text(action, style = MaterialTheme.typography.labelMedium, color = AppColors.colors.accent)
        }
    }
}

@Composable
private fun TogglePill(text: String, checked: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) AppColors.colors.accentSoft else AppColors.colors.surfaceActive)
            .border(
                1.dp,
                if (checked) AppColors.colors.accent else AppColors.colors.border,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text = if (checked) "✓ $text" else text,
            style = MaterialTheme.typography.labelMedium,
            color = if (checked) AppColors.colors.accent else AppColors.colors.textSecondary,
        )
    }
}

@Composable
private fun SkipPrompt(title: String, detail: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (hovered) Color(0xF21B2522) else Color(0xE6151D1A))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(9.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Text(detail, style = MaterialTheme.typography.labelMedium, color = Color(0xB3FFFFFF))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ControlIconButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    TooltipArea(
        tooltip = {
            Box(
                Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xEE1B211F))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        },
        delayMillis = 350,
        tooltipPlacement = TooltipPlacement.ComponentRect(),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (hovered && enabled) Color(0x33FFFFFF) else Color.Transparent)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                    if (!enabled) disabled()
                }
                .hoverable(interaction, enabled = enabled)
                .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
                .alpha(if (enabled) 1f else 0.4f),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun TextChip(
    text: String,
    onClick: () -> Unit,
    contentDescription: String = text,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) Color(0x33FFFFFF) else Color(0x1AFFFFFF))
            .semantics { this.contentDescription = contentDescription; role = Role.Button }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xE6FFFFFF),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BackChip(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (hovered) Color(0x33FFFFFF) else Color(0x1AFFFFFF))
            .semantics {
                contentDescription = "返回"
                role = Role.Button
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("‹", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.width(4.dp))
        Text("返回", style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
private fun PlayerActionButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (hovered) AppColors.colors.surfaceHover else AppColors.colors.surfaceRaised)
            .border(1.dp, AppColors.colors.border, RoundedCornerShape(7.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = AppColors.colors.textPrimary)
    }
}

/**
 * 线路菜单：同时承载切换与测速结果。测速后按延迟（ms）升序排列，
 * 最快线路用绿色标注；没有测速结果时只显示集数，不编造速度。
 */
@Composable
private fun SourceMenu(
    options: List<PlayerSourceOption>,
    speeds: Map<Int, SpeedResult>,
    fastestIndex: Int?,
    current: Int,
    testing: Boolean,
    onSpeedTest: () -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 与详情页保持同一排序：成功的在前（按 ms 升序），延迟相同看带宽，失败次之，未测速垫底。
    val ordered = remember(options, speeds) {
        options.sortedWith(
            compareBy(
                { option ->
                    val speed = speeds[option.index]
                    when {
                        speed == null -> 2
                        speed.ok -> 0
                        else -> 1
                    }
                },
                { option -> speeds[option.index]?.latencyMs?.takeIf { it > 0L } ?: Long.MAX_VALUE },
                { option -> -(speeds[option.index]?.kbps ?: 0.0) },
                { option -> option.index },
            ),
        )
    }
    Column(
        modifier = modifier
            .width(280.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.colors.surfaceRaised)
            .border(1.dp, AppColors.colors.borderStrong, RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "线路与测速",
                style = MaterialTheme.typography.labelLarge,
                color = AppColors.colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(AppColors.colors.surfaceActive)
                    .clickable(enabled = !testing, onClick = onSpeedTest)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (testing) "测速中…" else "测速",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (testing) AppColors.colors.textTertiary else AppColors.colors.accent,
                )
            }
        }
        Spacer(Modifier.height(1.dp).fillMaxWidth().background(AppColors.colors.border))
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            ordered.forEach { option ->
                val selected = option.index == current
                val speed = speeds[option.index]
                val fastest = option.index == fastestIndex
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) AppColors.colors.surfaceActive else Color.Transparent)
                        .clickable { onSelect(option.index) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) AppColors.colors.accent else AppColors.colors.textSecondary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (fastest) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AppColors.colors.accentSoft)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    "最快",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppColors.colors.accent,
                                    fontSize = 10.sp,
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        if (selected) {
                            Text("当前", style = MaterialTheme.typography.labelMedium, color = AppColors.colors.accent)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${option.playable}/${option.total} 集",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (option.playable == option.total) {
                                AppColors.colors.textTertiary
                            } else {
                                AppColors.colors.warning
                            },
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = when {
                                speed == null -> "未测速"
                                !speed.ok -> "测速失败"
                                else -> speed.formatLatency()
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                speed == null -> AppColors.colors.textTertiary
                                !speed.ok -> AppColors.colors.danger
                                fastest -> AppColors.colors.accent
                                else -> AppColors.colors.textSecondary
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun trimSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()

private fun org.openani.mediamp.PlayerState.isLoadingOrBuffering(): Boolean =
    mediaStatus == MediaStatus.Opening || isBuffering
