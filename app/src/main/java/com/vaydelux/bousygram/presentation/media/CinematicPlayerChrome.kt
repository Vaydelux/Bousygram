package com.vaydelux.bousygram.presentation.media

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout

private val red = Color(0xFFD72C42)
internal fun cinematicFraction(position: Long, duration: Long) =
    if (duration <= 0L) 0f else (position.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)

@Composable internal fun CinematicPassiveProgress(position: Long, duration: Long, modifier: Modifier = Modifier) {
    val fraction = cinematicFraction(position, duration)
    Canvas(modifier.fillMaxWidth().height(1.dp).testTag("cinematic_passive_progress")) {
        drawLine(Color.White.copy(alpha = 0.22f), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
        drawLine(Color.White.copy(alpha = 0.76f), Offset.Zero, Offset(size.width * fraction, 0f), 1.dp.toPx())
    }
}

/** Two-dp active hairline. The hit target remains 36dp and the thumb appears only on drag. */
@Composable internal fun CinematicScrubber(
    mediaKey: String, position: Long, buffered: Long, duration: Long,
    onSeek: (Long) -> Unit, onScrub: (Boolean) -> Unit, focus: FocusRequester?
) {
    var dragging by remember(mediaKey) { mutableStateOf(false) }
    var selected by remember(mediaKey) { mutableLongStateOf(0L) }
    val active = duration > 0L
    // Never restart pointer handlers or cancel an active scrub when a media
    // extractor refines a *known* duration (common with MKV/TS containers).
    val latestDuration by rememberUpdatedState(duration)
    val latestPosition by rememberUpdatedState(position)
    val latestSeek by rememberUpdatedState(onSeek)
    val latestScrub by rememberUpdatedState(onScrub)
    LaunchedEffect(mediaKey, active) {
        dragging = false
        latestScrub(false)
    }
    val shown = if (dragging) selected else position
    Box(
        Modifier.fillMaxWidth().height(36.dp)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onKeyEvent { event ->
                if (!active || event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeek((shown - 10_000L).coerceAtLeast(0L)); true }
                    Key.DirectionRight -> { onSeek((shown + 10_000L).coerceAtMost(duration)); true }
                    else -> false
                }
            }
            .focusable(active)
            .pointerInput(mediaKey, active) {
                if (!active) return@pointerInput
                detectTapGestures { point ->
                    playerScrubTarget(point.x / size.width.coerceAtLeast(1).toFloat(), latestDuration)
                        ?.let(latestSeek)
                }
            }
            .pointerInput(mediaKey, active) {
                if (!active) return@pointerInput
                detectDragGestures(
                    onDragStart = { point ->
                        dragging = true
                        latestScrub(true)
                        selected = playerScrubTarget(
                            point.x / size.width.coerceAtLeast(1).toFloat(), latestDuration
                        ) ?: latestPosition
                    },
                    onDragEnd = {
                        if (dragging) latestSeek(selected.coerceIn(0L, latestDuration))
                        dragging = false
                        latestScrub(false)
                    },
                    onDragCancel = {
                        dragging = false
                        latestScrub(false)
                    }
                ) { change, _ ->
                    change.consume()
                    selected = playerScrubTarget(
                        change.position.x / size.width.coerceAtLeast(1).toFloat(), latestDuration
                    ) ?: selected
                }
            }
            .semantics { contentDescription = "Playback timeline" }
            .testTag("cinematic_scrubber"), contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            val y = size.height / 2f
            drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), size.height)
            drawLine(Color.White.copy(alpha = 0.36f), Offset(0f, y),
                Offset(size.width * cinematicFraction(buffered, duration), y), size.height)
            val x = size.width * cinematicFraction(shown, duration)
            drawLine(red, Offset(0f, y), Offset(x, y), size.height)
            if (dragging) drawCircle(red, 5.dp.toPx(), Offset(x, y))
        }
    }
}

/** Native transparent touch/focus target: no pill/card backgrounds over the film. */
@Composable private fun EdgeButton(label: String, description: String, click: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) Color.White else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .background(
                if (focused) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = click)
            .semantics { contentDescription = description }
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun CinematicPlayerChrome(
    state: PlayerHudState, isTv: Boolean, optionsVisible: Boolean,
    controlsVisible: Boolean = true,
    menuSection: CinematicMenuSection,
    onMenuSectionChange: (CinematicMenuSection) -> Unit,
    onScrub: (Boolean) -> Unit, onOptions: () -> Unit, onCloseOptions: () -> Unit,
    onExit: () -> Unit, onStartOver: () -> Unit, onRetry: () -> Unit,
    onPlayPause: () -> Unit, onSeekBy: (Long) -> Unit, onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, onTracks: () -> Unit,
    onAspect: () -> Unit, onSpeed: () -> Unit, onEpisodes: (() -> Unit)?,
    episodesFocus: FocusRequester?, onDecoder: (() -> Unit)?,
    onDiagnostics: (() -> Unit)? = null,
    playFocus: FocusRequester, optionsFocus: FocusRequester,
    firstOptionFocus: FocusRequester, seekFocus: FocusRequester?,
    retryFocus: FocusRequester?, modifier: Modifier = Modifier
) {
    val duration = state.durationMs.takeIf { state.durationTrusted && it > 0L } ?: 0L
    Box(modifier.fillMaxSize().testTag("cinematic_player_chrome")) {
        // Animate two short edge bands rather than translating a fullscreen
        // layer on every HUD open/close. The video center stays untouched.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
        ) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().height(110.dp).align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.58f), Color.Transparent)
                        ))
                )
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = if (isTv) 22.dp else 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EdgeButton("‹", "Back", onExit)
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.title, color = Color.White,
                            style = MaterialTheme.typography.titleMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("cinematic_title")
                        )
                        Text(
                            state.trackLabel, color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (duration > 0L) EdgeButton("↺", "Start over", onStartOver)
                }
            }
        }
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().height(150.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                        ))
                )
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(
                            horizontal = if (isTv) 22.dp else 12.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatCinematicTime(state.positionMs) + " / " +
                                (if (duration > 0L) formatCinematicTime(duration) else "--:--"),
                            color = Color.White, style = MaterialTheme.typography.labelSmall
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            EdgeButton(
                                if (state.isPlaying || state.playWhenReady) "Ⅱ" else "▶",
                                "Play or pause", onPlayPause,
                                Modifier.focusRequester(playFocus).testTag("cinematic_play")
                            )
                            if (isTv && onEpisodes != null) {
                                EdgeButton(
                                    "Episodes", "Browse episodes", onEpisodes,
                                    if (episodesFocus != null) Modifier.focusRequester(episodesFocus)
                                    else Modifier
                                )
                            }
                            EdgeButton(
                                "⋯ More", "More playback options", onOptions,
                                Modifier.focusRequester(optionsFocus).testTag("cinematic_more")
                            )
                        }
                    }
                    CinematicScrubber(
                        state.mediaKey, state.positionMs, state.bufferedPositionMs,
                        duration, onSeekTo, onScrub, seekFocus
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = controlsVisible && optionsVisible,
            enter = fadeIn(tween(160)) + slideInVertically(
                animationSpec = tween(170), initialOffsetY = { it / 8 }
            ),
            exit = fadeOut(tween(130)) + slideOutVertically(
                animationSpec = tween(140), targetOffsetY = { it / 8 }
            ),
            modifier = Modifier.align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = if (isTv) 24.dp else 12.dp, bottom = 76.dp)
        ) {
            val aspect = when (state.resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
                else -> "Fit"
            }
            CinematicNavigationMenu(
                section = menuSection,
                isTv = isTv,
                onSectionChange = onMenuSectionChange,
                onClose = onCloseOptions,
                onTracks = onTracks,
                onSpeed = onSpeed,
                speedLabel = "${state.speed}x",
                onAspect = onAspect,
                aspectLabel = aspect,
                onEpisodes = onEpisodes,
                onPrevious = onPrevious,
                onNext = onNext,
                onSeekBy = onSeekBy,
                onDecoder = onDecoder,
                onDiagnostics = onDiagnostics,
                firstFocus = firstOptionFocus,
                modifier = Modifier.heightIn(max = 320.dp)
            )
        }
        if (state.error != null) {
            Column(Modifier.align(Alignment.Center).widthIn(max = 400.dp)
                .background(Color.Black.copy(alpha = 0.86f), RoundedCornerShape(12.dp))
                .padding(16.dp).testTag("cinematic_error"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.error, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EdgeButton("Retry", "Retry playback", onRetry,
                        if (retryFocus != null) Modifier.focusRequester(retryFocus) else Modifier)
                    EdgeButton("Skip", "Skip video", onNext)
                }
            }
        }
    }
}

private fun formatCinematicTime(ms: Long): String {
    val s = ms.coerceAtLeast(0L) / 1000L
    return if (s >= 3600L) "%d:%02d:%02d".format(s / 3600L, s / 60L % 60L, s % 60L)
        else "%d:%02d".format(s / 60L, s % 60L)
}
