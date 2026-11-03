package com.vaydelux.bousygram.presentation.media

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val CinemaRed = Color(0xFFE50924)
private val CinemaWhite = Color.White

/**
 * Full-bleed video stays below this chrome. The PlayerScreen owns every real
 * engine, Cast, track, resume and seek callback; this file is presentation only.
 */
@Composable
internal fun CinematicPlayerOverlay(
    state: PlayerHudState,
    controlsVisible: Boolean,
    compact: Boolean,
    isTv: Boolean,
    optionsVisible: Boolean,
    onScrubActiveChanged: (Boolean) -> Unit,
    onOptionsToggle: () -> Unit,
    onOptionsClose: () -> Unit,
    onExit: () -> Unit,
    onStartOver: () -> Unit,
    onRetry: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onTracks: () -> Unit,
    onToggleResizeMode: () -> Unit,
    onSpeed: () -> Unit,
    onEpisodes: (() -> Unit)?,
    episodesFocusRequester: FocusRequester,
    onSwitchEngine: (() -> Unit)?,
    playPauseFocusRequester: FocusRequester,
    optionsFocusRequester: FocusRequester,
    firstOptionFocusRequester: FocusRequester,
    seekBarFocusRequester: FocusRequester,
    retryFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val edge = if (isTv) 30.dp else if (compact) 16.dp else 28.dp
    val playing = (state.isPlaying || state.playWhenReady) && state.error == null
    val durationKnown = state.durationTrusted && state.durationMs > 0L
    val status = when {
        state.error != null -> "Playback interrupted"
        state.isReconnecting -> "Reconnecting…"
        state.isOpening -> "Preparing video…"
        state.isBuffering -> "Buffering…"
        else -> state.trackLabel
    }
    BoxWithConstraints(modifier.fillMaxSize().testTag("cinematic_player_overlay")) {
        // Exactly 70% of the real viewport remains entirely free of vignette.
        val topVignetteHeight = maxHeight * 0.12f
        val bottomVignetteHeight = maxHeight * 0.18f
        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)) {
            Box(Modifier.fillMaxWidth().height(topVignetteHeight).background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = .52f), Color.Transparent))
            ))
        }
        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)) {
            Box(Modifier.fillMaxWidth().height(bottomVignetteHeight).background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .62f)))
            ))
        }
        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)) {
            CinematicTopEdge(state.title, state.fileName?.takeIf { it.isNotBlank() && it != state.title },
                compact, edge, onExit)
        }
        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = edge, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(status, color = CinemaWhite.copy(alpha = .8f),
                        style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${cinematicTime(state.positionMs)} / " +
                        (if (durationKnown) cinematicTime(state.durationMs) else "--:--"),
                        color = CinemaWhite, style = MaterialTheme.typography.labelMedium)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CinematicEdgeAction(if (playing) "Ⅱ Pause" else "▶ Play",
                            if (playing) "Pause playback" else "Play video",
                            onPlayPause, focusRequester = playPauseFocusRequester,
                            tag = "cinematic_play_pause")
                        if (isTv && onEpisodes != null) {
                            CinematicEdgeAction("▤ Episodes", "Browse episodes", onEpisodes,
                                focusRequester = episodesFocusRequester, tag = "cinematic_episodes")
                        }
                        if (state.error != null) {
                            CinematicEdgeAction("↻ Retry", "Retry playback", onRetry,
                                focusRequester = retryFocusRequester, tag = "cinematic_retry")
                        }
                    }
                    CinematicEdgeAction("⋯ More",
                        if (optionsVisible) "Close playback options" else "More playback options",
                        onOptionsToggle, focusRequester = optionsFocusRequester,
                        tag = "cinematic_more")
                }
            }
        }
        if (controlsVisible && optionsVisible) {
            CinematicNavigationDropdown(
                state = state, isTv = isTv,
                onTracks = onTracks, onSpeed = onSpeed, onAspect = onToggleResizeMode,
                onEpisodes = onEpisodes, onSwitchEngine = onSwitchEngine,
                onPrevious = onSkipPrevious, onNext = onSkipNext,
                onSeekBack30 = { onSeekBy(-30_000L) },
                onSeekForward30 = { onSeekBy(30_000L) },
                onStartOver = onStartOver, onClose = onOptionsClose,
                firstFocusRequester = firstOptionFocusRequester,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .padding(end = edge, bottom = 92.dp)
            )
        }
        // Always anchored to the absolute window bottom, even with no HUD.
        CinematicHairline(
            mediaKey = state.mediaKey, positionMs = state.positionMs,
            durationMs = state.durationMs, enabled = durationKnown,
            visible = controlsVisible, onSeekTo = onSeekTo,
            onScrubActiveChanged = onScrubActiveChanged,
            focusRequester = seekBarFocusRequester,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CinematicTopEdge(title: String, subtitle: String?, compact: Boolean,
    edge: Dp, onExit: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding()
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        .padding(horizontal = edge, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        CinematicEdgeAction("‹ Back", "Exit video", onExit, tag = "cinematic_back")
        Column(Modifier.weight(1f)) {
            Text(title, color = CinemaWhite,
                style = if (compact) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            if (subtitle != null && !compact) {
                Text(subtitle, color = CinemaWhite.copy(alpha = .7f),
                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CinematicEdgeAction(label: String, description: String,
    onClick: () -> Unit, modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null, tag: String? = null) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Box(modifier
        .then(if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester))
        .onFocusChanged { focused = it.isFocused }
        .border(if (focused) 2.dp else 0.dp, CinemaWhite, shape)
        .clip(shape)
        .clickable(role = Role.Button, onClick = onClick)
        .then(if (tag == null) Modifier else Modifier.testTag(tag))
        .semantics { contentDescription = description }
        .heightIn(min = 48.dp)
        .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = CinemaWhite, style = MaterialTheme.typography.labelLarge,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

/** Future navigation features can be appended here without expanding the HUD. */
@Composable
private fun CinematicNavigationDropdown(
    state: PlayerHudState, isTv: Boolean,
    onTracks: () -> Unit, onSpeed: () -> Unit, onAspect: () -> Unit,
    onEpisodes: (() -> Unit)?, onSwitchEngine: (() -> Unit)?,
    onPrevious: () -> Unit, onNext: () -> Unit,
    onSeekBack30: () -> Unit, onSeekForward30: () -> Unit,
    onStartOver: () -> Unit, onClose: () -> Unit,
    firstFocusRequester: FocusRequester, modifier: Modifier = Modifier
) {
    val height = (LocalConfiguration.current.screenHeightDp - 120).coerceIn(140, 380).dp
    LaunchedEffect(isTv) {
        if (isTv) {
            delay(80L)
            runCatching { firstFocusRequester.requestFocus() }
        }
    }
    Column(modifier.widthIn(min = 210.dp, max = 288.dp)
        .heightIn(max = height).clip(RoundedCornerShape(12.dp))
        .background(Color.Black.copy(alpha = .86f))
        .border(1.dp, CinemaWhite.copy(alpha = .2f), RoundedCornerShape(12.dp))
        .verticalScroll(rememberScrollState()).padding(10.dp)
        .testTag("cinematic_options_dropdown"),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("PLAYBACK", style = MaterialTheme.typography.labelSmall,
            color = CinemaWhite.copy(alpha = .65f),
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
        CinematicEdgeAction("Audio & subtitles", "Choose audio or subtitle tracks",
            onTracks, modifier = Modifier.fillMaxWidth(), focusRequester = firstFocusRequester, tag = "cinematic_audio")
        CinematicEdgeAction("Speed · ${state.speed}×", "Change playback speed", onSpeed, modifier = Modifier.fillMaxWidth())
        CinematicEdgeAction("Aspect ratio", "Change video aspect ratio", onAspect, modifier = Modifier.fillMaxWidth())
        if (onEpisodes != null) {
            CinematicEdgeAction("Episodes & queue", "Browse playback queue", onEpisodes, modifier = Modifier.fillMaxWidth())
        }
        CinematicEdgeAction("Previous video", "Play previous video", onPrevious, modifier = Modifier.fillMaxWidth())
        CinematicEdgeAction("Next video", "Play next video", onNext, modifier = Modifier.fillMaxWidth())
        CinematicEdgeAction("Rewind 30 seconds", "Seek backward 30 seconds", onSeekBack30, modifier = Modifier.fillMaxWidth())
        CinematicEdgeAction("Forward 30 seconds", "Seek forward 30 seconds", onSeekForward30, modifier = Modifier.fillMaxWidth())
        if (state.durationTrusted && state.durationMs > 0L) {
            CinematicEdgeAction("Start over", "Restart video from beginning", onStartOver, modifier = Modifier.fillMaxWidth())
        }
        if (onSwitchEngine != null) {
            CinematicEdgeAction("Playback engine", "Switch video decoder", onSwitchEngine, modifier = Modifier.fillMaxWidth())
        }
        CinematicEdgeAction("Close menu", "Close playback options", onClose, modifier = Modifier.fillMaxWidth())
    }
}

internal fun cinematicProgressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f
    else (positionMs.coerceIn(0L, durationMs).toDouble() / durationMs.toDouble()).toFloat()

private fun cinematicTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    val remainder = seconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, remainder)
        else "%d:%02d".format(minutes, remainder)
}

/** Red 2dp active progress; ghost-white 1dp passive progress. No static thumb. */
@Composable
internal fun CinematicHairline(
    mediaKey: String, positionMs: Long, durationMs: Long, enabled: Boolean,
    visible: Boolean, onSeekTo: (Long) -> Unit,
    onScrubActiveChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester, modifier: Modifier = Modifier
) {
    var dragging by remember(mediaKey) { mutableStateOf(false) }
    var dragFraction by remember(mediaKey) { mutableFloatStateOf(0f) }
    val targetFraction = cinematicProgressFraction(positionMs, durationMs)
    val fraction = if (dragging) dragFraction else targetFraction
    val seek by rememberUpdatedState(onSeekTo)
    val scrub by rememberUpdatedState(onScrubActiveChanged)
    LaunchedEffect(mediaKey) {
        dragging = false
        scrub(false)
    }
    val activeModifier = if (visible && enabled) {
        Modifier.focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                val delta = when (event.key) {
                    Key.DirectionLeft -> -10_000L
                    Key.DirectionRight -> 10_000L
                    else -> return@onKeyEvent false
                }
                seek((positionMs + delta).coerceIn(0L, durationMs))
                true
            }
            .focusable()
            .semantics {
                contentDescription = "Seek video"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { ratio ->
                    playerScrubTarget(ratio, durationMs)?.let { seek(it); true } ?: false
                }
            }
            .pointerInput(mediaKey, durationMs) {
                detectDragGestures(
                    onDragStart = { point ->
                        dragging = true
                        scrub(true)
                        dragFraction = (point.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        playerScrubTarget(dragFraction, durationMs)?.let(seek)
                        dragging = false
                        scrub(false)
                    },
                    onDragCancel = {
                        dragging = false
                        scrub(false)
                    }
                )
            }
            .pointerInput(mediaKey, durationMs) {
                detectTapGestures { point ->
                    playerScrubTarget(point.x / size.width.coerceAtLeast(1), durationMs)
                        ?.let(seek)
                }
            }
    } else Modifier
    Box(modifier.fillMaxWidth().height(if (visible) 34.dp else 1.dp)
        .then(activeModifier)
        .testTag(if (visible) "cinematic_progress_active" else "cinematic_progress_passive")) {
        val lineThickness = if (visible) 2.dp else 1.dp
        Canvas(Modifier.fillMaxWidth().height(lineThickness).align(Alignment.BottomCenter)) {
            drawRect(if (visible) CinemaWhite.copy(alpha = .35f)
                else CinemaWhite.copy(alpha = .16f))
            drawRect(if (visible) CinemaRed else CinemaWhite.copy(alpha = .65f),
                size = Size(size.width * fraction, size.height))
        }
        if (dragging && visible) {
            Canvas(Modifier.fillMaxWidth().height(20.dp).align(Alignment.BottomCenter)) {
                drawCircle(CinemaRed, radius = 5.dp.toPx(),
                    center = Offset(size.width * fraction, size.height - 2.dp.toPx()))
            }
        }
    }
}

/**
 * Invisible touch gesture surface; never used for TV D-pad focus.
 * Single tap toggles play/pause; double-tap left/right seeks ±10 seconds.
 * Vertical drags retain existing brightness/volume callbacks.
 */
@Composable
internal fun CinematicGesturePad(
    onSingleTap: () -> Unit,
    onDoubleTap: (forward: Boolean) -> Unit,
    onVerticalDrag: (leftHalf: Boolean, dragPx: Float, heightPx: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val tap by rememberUpdatedState(onSingleTap)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val verticalDrag by rememberUpdatedState(onVerticalDrag)
    Box(modifier.fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { tap() },
                onDoubleTap = { point -> doubleTap(point.x >= size.width / 2f) }
            )
        }
        .pointerInput(Unit) {
            var leftHalf = false
            detectDragGestures(
                onDragStart = { leftHalf = it.x < size.width / 2f },
                onDrag = { change, delta ->
                    change.consume()
                    verticalDrag(leftHalf, delta.y, size.height.toFloat())
                }
            )
        }
        .testTag("cinematic_gesture_pad"))
}
