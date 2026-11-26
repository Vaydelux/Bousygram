package com.vaydelux.bousygram.presentation.media

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIconRef
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.components.BousygramTooltipBox
import com.vaydelux.bousygram.presentation.components.BousygramTooltipPlacement
import com.vaydelux.bousygram.presentation.components.liquidGlassSheen
import com.vaydelux.bousygram.presentation.components.rememberBousygramTooltipState
import com.vaydelux.bousygram.presentation.theme.BousygramDesignStyle
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.LocalBousygramDisplaySpec
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors
import com.vaydelux.bousygram.presentation.theme.LocalBousygramReduceTransparency
import com.vaydelux.bousygram.presentation.theme.LocalBousygramStyleSpec
import com.vaydelux.bousygram.presentation.theme.playerHudSurfaceOpacity
import com.vaydelux.bousygram.presentation.theme.rememberBousygramMotionPolicy
import com.vaydelux.bousygram.presentation.theme.rememberBousygramPlaybackPalette
import com.vaydelux.bousygram.presentation.theme.tabular
import kotlin.math.roundToInt

internal fun cinematicFraction(position: Long, duration: Long) =
    if (duration <= 0L) 0f else (position.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)

@Composable
internal fun CinematicPassiveProgress(position: Long, duration: Long, modifier: Modifier = Modifier) {
    val fraction = cinematicFraction(position, duration)
    val playback = rememberBousygramPlaybackPalette()
    val cinemaAccent = playback.played
    val cinemaAccentEnd = playback.playedEnd
    Canvas(modifier.fillMaxWidth().height(1.5.dp).testTag("cinematic_passive_progress")) {
        val h = size.height
        val y = h / 2f
        drawLine(playback.unplayed, Offset(0f, y), Offset(size.width, y), h)
        if (fraction > 0f) {
            drawLine(
                brush = Brush.horizontalGradient(listOf(cinemaAccent, cinemaAccentEnd)),
                start = Offset(0f, y),
                end = Offset(size.width * fraction, y),
                strokeWidth = h
            )
        }
    }
}

/**
 * Cinema Halo timeline scrubber. Maintains a 48dp accessible hit target,
 * expands cleanly on TV focus/scrub with a live floating timestamp pill, and
 * omits a resting thumb (drag-only) per accessibility & instrumentation contracts.
 */
@Composable
internal fun CinematicScrubber(
    mediaKey: String,
    position: Long,
    buffered: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onScrub: (Boolean) -> Unit,
    focus: FocusRequester?
) {
    var dragging by remember(mediaKey) { mutableStateOf(false) }
    var selected by remember(mediaKey) { mutableLongStateOf(0L) }
    var isFocused by remember(mediaKey) { mutableStateOf(false) }
    val active = duration > 0L
    val halo = LocalBousygramHaloColors.current
    val colors = MaterialTheme.colorScheme
    // Use the same theme-aware video palette as passive progress, Extras
    // native seekbars and player chrome. Do not substitute default cyan.
    val playback = rememberBousygramPlaybackPalette()
    val cinemaAccent = playback.played
    val cinemaAccentEnd = playback.playedEnd
    val latestDuration by rememberUpdatedState(duration)
    val latestPosition by rememberUpdatedState(position)
    val latestSeek by rememberUpdatedState(onSeek)
    val latestScrub by rememberUpdatedState(onScrub)

    LaunchedEffect(mediaKey, active) {
        dragging = false
        latestScrub(false)
    }

    val shown = if (dragging) selected else position
    val shownFraction = cinematicFraction(shown, duration)
    val bufferedFraction = cinematicFraction(buffered, duration)

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (!active || event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        latestSeek((shown - 10_000L).coerceAtLeast(0L))
                        true
                    }
                    Key.DirectionRight -> {
                        latestSeek((shown + 10_000L).coerceAtMost(latestDuration))
                        true
                    }
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
            .semantics {
                contentDescription = if (active) "Playback timeline" else "Playback duration unknown"
                if (active) {
                    progressBarRangeInfo = ProgressBarRangeInfo(shownFraction, 0f..1f)
                    stateDescription = "${formatCinematicTime(shown)} of ${formatCinematicTime(duration)}"
                    setProgress { fraction ->
                        val target = playerScrubTarget(fraction, latestDuration)
                        if (target == null) false
                        else {
                            latestSeek(target)
                            true
                        }
                    }
                }
            }
            .testTag("cinematic_scrubber"),
        contentAlignment = Alignment.BottomCenter
    ) {
        val density = LocalDensity.current
        val trackWidthPx = with(density) { maxWidth.toPx() }
        // Floating live timestamp pill above the active scrub point during drag or TV focus.
        if (active && (dragging || isFocused)) {
            val pillHalfPx = with(density) { 42.dp.toPx() }
            val clampedCenterX = (trackWidthPx * shownFraction)
                .coerceIn(pillHalfPx, (trackWidthPx - pillHalfPx).coerceAtLeast(pillHalfPx))
            Surface(
                shape = RoundedCornerShape(BousygramUiTokens.tooltipCorner),
                color = halo.tooltipFill,
                border = BorderStroke(1.dp, halo.tooltipBorder),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clearAndSetSemantics { }
                    .offset {
                        IntOffset((clampedCenterX - pillHalfPx).roundToInt(), 0)
                    }
            ) {
                Text(
                    text = formatCinematicTime(shown),
                    color = halo.tooltipContent,
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
        }

        val barHeight = when {
            dragging -> 7.dp
            isFocused -> 6.dp
            else -> 3.dp
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .height(barHeight)
        ) {
            val h = size.height
            val radius = CornerRadius(h / 2f, h / 2f)
            // Unplayed track
            drawRoundRect(
                color = playback.unplayed.copy(
                    alpha = if (isFocused || halo.isHighContrast) 0.42f else playback.unplayed.alpha
                ),
                size = Size(size.width, h),
                cornerRadius = radius
            )
            // Buffered range
            if (bufferedFraction > 0f) {
                drawRoundRect(
                    color = playback.buffered,
                    size = Size(size.width * bufferedFraction, h),
                    cornerRadius = radius
                )
            }
            // The active timeline sits on the themed transport surface.
            val fillWidth = (size.width * shownFraction).coerceAtLeast(0f)
            if (fillWidth > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(cinemaAccent, cinemaAccentEnd)),
                    size = Size(fillWidth, h),
                    cornerRadius = radius
                )
            }
            // Drag-only thumb; omitted at rest to satisfy accessibility contract.
            if (dragging) {
                val center = Offset(fillWidth, h / 2f)
                drawCircle(cinemaAccent.copy(alpha = 0.20f), 10.dp.toPx(), center)
                drawCircle(playback.thumb, 6.dp.toPx(), center)
                drawCircle(cinemaAccentEnd, 3.5.dp.toPx(), center)
            }
        }
    }
}

/**
 * Cinema HUD icon button with built-in floating tooltip above/below, crisp
 * Lucide stroke icon, subtle frosted hover/focus backing, and zero clutter over the frame.
 */
@Composable
private fun CinemaHudButton(
    icon: BousygramIconRef,
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tooltipDetail: String? = null,
    label: String? = null,
    selected: Boolean = false,
    hero: Boolean = false,
    isTv: Boolean = false,
    tooltipPlacement: BousygramTooltipPlacement = BousygramTooltipPlacement.AUTO_ABOVE
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val spec = LocalBousygramDisplaySpec.current
    val styleSpec = LocalBousygramStyleSpec.current
    val motion = rememberBousygramMotionPolicy()
    val tooltipState = rememberBousygramTooltipState()
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    val minTarget: Dp = when {
        hero -> spec.playerHeroButtonSize
        isTv -> spec.playerControlButtonSize
        else -> BousygramUiTokens.touchTarget
    }
    val iconSize: Dp = when {
        hero -> spec.iconSizeHero
        isTv -> 22.dp
        else -> 20.dp
    }
    val shape = when {
        hero && label == null -> if (styleSpec.style == BousygramDesignStyle.IPHONE_GLASS) RoundedCornerShape(18.dp) else CircleShape
        label != null -> RoundedCornerShape(styleSpec.buttonCorner)
        styleSpec.style == BousygramDesignStyle.IPHONE_GLASS -> RoundedCornerShape(14.dp)
        else -> RoundedCornerShape(BousygramUiTokens.pillCorner)
    }

    val scale by animateFloatAsState(
        targetValue = if (focused && motion.animateFocusScale) 1.04f else 1f,
        animationSpec = tween(motion.duration(BousygramUiTokens.focusDurationMs)),
        label = "cinemaBtnScale"
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            hero && focused -> colors.primary
            hero -> colors.primaryContainer
            selected || focused -> colors.secondaryContainer
            else -> colors.surfaceContainerHigh
        },
        animationSpec = tween(motion.duration(BousygramUiTokens.focusDurationMs)),
        label = "cinemaBtnBg"
    )
    val borderColor = when {
        focused && hero -> colors.onPrimary
        focused || selected -> colors.primary
        halo.isHighContrast -> colors.outline
        styleSpec.isLiquidGlass -> if (halo.isLight) Color(0xFF0F172A).copy(alpha = 0.10f) else Color.White.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val borderWidth = when {
        focused -> BousygramUiTokens.tvFocusOutline
        selected -> 1.5.dp
        halo.isHighContrast -> 1.dp
        styleSpec.isLiquidGlass -> 1.dp
        else -> 0.dp
    }
    val fgColor = when {
        hero && focused -> colors.onPrimary
        hero -> colors.onPrimaryContainer
        selected || focused -> colors.onSecondaryContainer
        else -> colors.onSurface
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = minTarget, minHeight = minTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bgColor)
            .then(
                if (borderWidth > 0.dp) Modifier.border(
                    width = borderWidth,
                    color = borderColor,
                    shape = shape
                ) else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription = tooltip
                this.selected = selected
            }
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = fgColor),
                role = Role.Button,
                onClick = {
                    if (!isTv && label == null) tooltipState.flashSelection()
                    onClick()
                }
            )
            .padding(
                horizontal = if (label != null) 14.dp else 12.dp,
                vertical = if (hero) 12.dp else 10.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        BousygramTooltipBox(
            tooltip = tooltip,
            detail = tooltipDetail,
            icon = icon,
            focused = focused,
            placement = tooltipPlacement,
            dwellMs = if (label == null) 0L else BousygramUiTokens.tooltipDwellMs,
            state = tooltipState,
            enabled = label == null
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)
            ) {
                BousygramIcon(
                    icon = icon,
                    contentDescription = null,
                    tint = fgColor,
                    size = iconSize
                )
                if (label != null) {
                    Text(
                        text = label,
                        color = fgColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CinemaMetaBadge(
    text: String,
    accent: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (accent) colors.secondaryContainer else colors.surfaceContainerHigh,
        border = if (halo.isHighContrast) BorderStroke(1.dp, colors.outline) else null
    ) {
        Text(
            text = text,
            color = if (accent) colors.onSecondaryContainer else colors.onSurface,
            style = MaterialTheme.typography.labelSmall.tabular(),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun CinematicPlayerChrome(
    state: PlayerHudState,
    isTv: Boolean,
    optionsVisible: Boolean,
    controlsVisible: Boolean = true,
    menuSection: CinematicMenuSection,
    onMenuSectionChange: (CinematicMenuSection) -> Unit,
    onScrub: (Boolean) -> Unit,
    onOptions: () -> Unit,
    onCloseOptions: () -> Unit,
    onExit: () -> Unit,
    onStartOver: () -> Unit,
    onRetry: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTracks: () -> Unit,
    onAspect: () -> Unit,
    onSpeed: () -> Unit,
    onEpisodes: (() -> Unit)?,
    episodesFocus: FocusRequester?,
    onDecoder: (() -> Unit)?,
    onDiagnostics: (() -> Unit)? = null,
    playFocus: FocusRequester,
    optionsFocus: FocusRequester,
    firstOptionFocus: FocusRequester,
    seekFocus: FocusRequester?,
    retryFocus: FocusRequester?,
    modifier: Modifier = Modifier
) {
    val duration = state.durationMs.takeIf { state.durationTrusted && it > 0L } ?: 0L
    val motion = rememberBousygramMotionPolicy()
    val halo = LocalBousygramHaloColors.current
    val spec = LocalBousygramDisplaySpec.current
    val styleSpec = LocalBousygramStyleSpec.current
    val reduceTransparency = LocalBousygramReduceTransparency.current
    val colors = MaterialTheme.colorScheme
    val baseHudAlpha = playerHudSurfaceOpacity(colors.background)
    val hudAlpha = if (reduceTransparency && !halo.isHighContrast) maxOf(baseHudAlpha, 0.96f) else baseHudAlpha
    val hudSurface = colors.surfaceContainer.copy(alpha = hudAlpha)
    val glassRimBrush = Brush.verticalGradient(
        listOf(
            if (halo.isLight) Color.White.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.24f),
            if (halo.isLight) Color(0xFF0F172A).copy(alpha = 0.10f) else Color.White.copy(alpha = 0.08f)
        )
    )
    val topBarShape = RoundedCornerShape(styleSpec.panelCorner)
    val density = LocalDensity.current
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val maxMenuHeight = cinematicMenuHeightLimit(windowHeight)
    val horizontalPad = if (isTv) spec.horizontalPadding else 14.dp
    val effectiveWidth = windowWidth / density.fontScale.coerceAtLeast(1f)
    val showWideSecondaryLabels = isTv && effectiveWidth >= 880.dp
    val showTouchStepControls = !isTv || effectiveWidth >= 780.dp
    val aspectLabel = when (state.resizeMode) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
        else -> "Fit"
    }
    val speedDisplay = if (state.speed == 1.0f) "1.0x" else "${"%.2f".format(state.speed).trimEnd('0').trimEnd('.')}x"

    Box(modifier.fillMaxSize().testTag("cinematic_player_chrome")) {
        // TOP CINEMA HEADER BAND
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(motion.duration(160))),
            exit = fadeOut(tween(motion.duration(150))),
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
        ) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(halo.scrimTop.copy(alpha = 0.60f), Color.Transparent)
                            )
                        )
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = horizontalPad, vertical = if (isTv) 14.dp else 10.dp)
                        .clip(topBarShape)
                        .background(hudSurface, topBarShape)
                        .liquidGlassSheen(
                            enabled = styleSpec.isLiquidGlass && !halo.isHighContrast && !reduceTransparency,
                            topColor = if (halo.isLight) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.09f),
                            coverageFraction = 0.42f
                        )
                        .then(
                            if (halo.isHighContrast) Modifier.border(1.5.dp, colors.outline, topBarShape)
                            else if (styleSpec.isLiquidGlass) Modifier.border(1.dp, glassRimBrush, topBarShape)
                            else Modifier
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CinemaHudButton(
                        icon = BousygramIcons.Back,
                        tooltip = "Back",
                        tooltipDetail = "Exit player",
                        onClick = onExit,
                        isTv = isTv,
                        tooltipPlacement = BousygramTooltipPlacement.AUTO_BELOW
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = state.title,
                            color = colors.onSurface,
                            style = if (isTv) MaterialTheme.typography.titleLarge
                                else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("cinematic_title")
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (state.trackLabel.isNotBlank()) {
                                CinemaMetaBadge(state.trackLabel)
                            }
                            if (state.speed != 1.0f) {
                                CinemaMetaBadge(speedDisplay, accent = true)
                            }
                            if (aspectLabel != "Fit") {
                                CinemaMetaBadge(aspectLabel, accent = true)
                            }
                            state.cacheStateLabel
                                ?.takeIf { it.isNotBlank() && windowWidth >= 480.dp }
                                ?.let { cacheLabel ->
                                    Text(
                                        text = cacheLabel,
                                        color = colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                        }
                    }
                    if (duration > 0L) {
                        CinemaHudButton(
                            icon = BousygramIcons.StartOver,
                            tooltip = "Start over",
                            tooltipDetail = "0:00",
                            onClick = onStartOver,
                            isTv = isTv,
                            tooltipPlacement = BousygramTooltipPlacement.AUTO_BELOW
                        )
                    }
                }
            }
        }

        // BOTTOM CINEMA TRANSPORT DOCK & SCRUBBER
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(motion.duration(160))),
            exit = fadeOut(tween(motion.duration(150))),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (isTv) 120.dp else 108.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, halo.scrimBottom.copy(alpha = 0.65f))
                            )
                        )
                )
                val dockShape = if (styleSpec.playerDockFloatingCapsule && !halo.isHighContrast) {
                    RoundedCornerShape(styleSpec.panelCorner)
                } else {
                    RoundedCornerShape(topStart = styleSpec.panelCorner, topEnd = styleSpec.panelCorner)
                }
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            start = horizontalPad,
                            end = horizontalPad,
                            bottom = if (isTv) 16.dp else 10.dp
                        )
                        .clip(dockShape)
                        .background(hudSurface, dockShape)
                        .liquidGlassSheen(
                            enabled = styleSpec.isLiquidGlass && !halo.isHighContrast && !reduceTransparency,
                            topColor = if (halo.isLight) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.09f),
                            coverageFraction = 0.36f
                        )
                        .then(
                            if (halo.isHighContrast) Modifier.border(1.5.dp, colors.outline, dockShape)
                            else if (styleSpec.isLiquidGlass) Modifier.border(1.dp, glassRimBrush, dockShape)
                            else if (styleSpec.panelBorderWidth > 0.dp) Modifier.border(styleSpec.panelBorderWidth, colors.outlineVariant.copy(alpha = 0.55f), dockShape)
                            else Modifier
                        )
                        .padding(horizontal = if (isTv) 18.dp else 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Timeline Scrubber
                    CinematicScrubber(
                        mediaKey = state.mediaKey,
                        position = state.positionMs,
                        buffered = state.bufferedPositionMs,
                        duration = duration,
                        onSeek = onSeekTo,
                        onScrub = onScrub,
                        focus = seekFocus
                    )

                    // Tabular Elapsed & Remaining/Total Timestamps
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatCinematicTime(state.positionMs),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            fontWeight = FontWeight.SemiBold
                        )
                        val remainingText = if (duration > 0L) {
                            val rem = (duration - state.positionMs).coerceAtLeast(0L)
                            "-${formatCinematicTime(rem)}  ·  ${formatCinematicTime(duration)}"
                        } else {
                            "--:--"
                        }
                        Text(
                            text = remainingText,
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium.tabular()
                        )
                    }

                    // Ergonomic Cinema Transport Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left / Center Transport Cluster
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 6.dp)
                        ) {
                            if (showTouchStepControls) {
                                CinemaHudButton(
                                    icon = BousygramIcons.SeekBack,
                                    tooltip = "Rewind 10 seconds",
                                    tooltipDetail = "-10s",
                                    onClick = { onSeekBy(-10_000L) },
                                    isTv = isTv
                                )
                            }
                            val playingNow = state.isPlaying || state.playWhenReady
                            CinemaHudButton(
                                icon = if (playingNow) BousygramIcons.Pause else BousygramIcons.Play,
                                tooltip = if (playingNow) "Pause" else "Play",
                                onClick = onPlayPause,
                                hero = true,
                                isTv = isTv,
                                modifier = Modifier
                                    .focusRequester(playFocus)
                                    .testTag("cinematic_play")
                            )
                            if (showTouchStepControls) {
                                CinemaHudButton(
                                    icon = BousygramIcons.SeekForward,
                                    tooltip = "Forward 10 seconds",
                                    tooltipDetail = "+10s",
                                    onClick = { onSeekBy(10_000L) },
                                    isTv = isTv
                                )
                            }
                            if (!isTv && windowWidth >= 600.dp) {
                                CinemaHudButton(
                                    icon = BousygramIcons.Previous,
                                    tooltip = "Previous video",
                                    onClick = onPrevious,
                                    isTv = false
                                )
                                CinemaHudButton(
                                    icon = BousygramIcons.Next,
                                    tooltip = "Next video",
                                    onClick = onNext,
                                    isTv = false
                                )
                            }
                        }

                        // Right Utility Cluster (1-tap Tracks, Episodes, More Options)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 6.dp)
                        ) {
                            CinemaHudButton(
                                icon = BousygramIcons.Subtitles,
                                tooltip = "Audio & Subtitles",
                                label = if (showWideSecondaryLabels) "Audio / CC" else null,
                                onClick = onTracks,
                                isTv = isTv,
                                modifier = Modifier.testTag("cinematic_tracks")
                            )
                            if (onEpisodes != null && (isTv || windowWidth >= 420.dp)) {
                                CinemaHudButton(
                                    icon = BousygramIcons.Episodes,
                                    tooltip = "Episodes & Queue",
                                    label = if (showWideSecondaryLabels) "Episodes" else null,
                                    onClick = onEpisodes,
                                    isTv = isTv,
                                    modifier = if (episodesFocus != null) Modifier.focusRequester(episodesFocus)
                                        else Modifier
                                )
                            }
                            CinemaHudButton(
                                icon = BousygramIcons.Options,
                                tooltip = if (optionsVisible) "Close options" else "More playback options",
                                label = if (showWideSecondaryLabels) "More" else null,
                                selected = optionsVisible,
                                onClick = onOptions,
                                isTv = isTv,
                                modifier = Modifier
                                    .focusRequester(optionsFocus)
                                    .testTag("cinematic_more")
                            )
                        }
                    }
                }
            }
        }

        // FLOATING NAVIGATION MENU
        AnimatedVisibility(
            visible = controlsVisible && optionsVisible,
            enter = fadeIn(tween(motion.duration(160))) + slideInVertically(
                animationSpec = tween(motion.duration(170)),
                initialOffsetY = { it / 8 }
            ),
            exit = fadeOut(tween(motion.duration(130))) + slideOutVertically(
                animationSpec = tween(motion.duration(140)),
                targetOffsetY = { it / 8 }
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(
                    end = horizontalPad,
                    bottom = if (windowHeight < 400.dp) 62.dp else if (isTv) 106.dp else 94.dp
                )
        ) {
            CinematicNavigationMenu(
                section = menuSection,
                isTv = isTv,
                onSectionChange = onMenuSectionChange,
                onClose = onCloseOptions,
                onTracks = onTracks,
                onSpeed = onSpeed,
                speedLabel = speedDisplay,
                onAspect = onAspect,
                aspectLabel = aspectLabel,
                onEpisodes = onEpisodes,
                onPrevious = onPrevious,
                onNext = onNext,
                onSeekBy = onSeekBy,
                onDecoder = onDecoder,
                onDiagnostics = onDiagnostics,
                firstFocus = firstOptionFocus,
                modifier = Modifier.heightIn(max = maxMenuHeight)
            )
        }

        // PLAYBACK ERROR CARD
        state.error?.let { errorMessage ->
            Surface(
                shape = RoundedCornerShape(styleSpec.panelCorner),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.72f)),
                shadowElevation = if (motion.lowRam) 0.dp else 12.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 440.dp)
                    .testTag("cinematic_error")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BousygramIcon(
                            icon = BousygramIcons.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            size = 22.dp
                        )
                        Text(
                            text = "Playback interrupted",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BousygramButton(
                            text = "Retry",
                            leadingIcon = BousygramIcons.Refresh,
                            selected = true,
                            onClick = onRetry,
                            modifier = (if (retryFocus != null) Modifier.focusRequester(retryFocus) else Modifier)
                                .weight(1f)
                        )
                        BousygramButton(
                            text = "Skip",
                            leadingIcon = BousygramIcons.Next,
                            onClick = onNext,
                            modifier = Modifier.weight(1f)
                        )
                    }
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

/** Upper bound for player menus on compact landscape phones and Android TVs. */
internal fun cinematicMenuHeightLimit(windowHeight: Dp) =
    (windowHeight.coerceAtLeast(0.dp) * 0.58f).coerceIn(112.dp, 320.dp)
