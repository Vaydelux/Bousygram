package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.components.BousygramButton

/**
 * Layout choice follows input hardware, not simply screen width: a TV must never receive
 * touch-first overflowing primary transport controls just because its screen is small.
 */
internal enum class PlayerChromeLayout { TV, TOUCH_COMPACT, TOUCH_WIDE }

internal fun playerChromeLayout(isTv: Boolean, compact: Boolean): PlayerChromeLayout = when {
    isTv -> PlayerChromeLayout.TV
    compact -> PlayerChromeLayout.TOUCH_COMPACT
    else -> PlayerChromeLayout.TOUCH_WIDE
}

/** Keep the HUD inside the window even with expanded options and landscape keyboard/insets. */
internal fun playerHudHeightLimit(windowHeightDp: Int, compact: Boolean): androidx.compose.ui.unit.Dp {
    val verticalInsetDp = if (compact) 24 else 64
    return (windowHeightDp - verticalInsetDp).coerceAtLeast(1).dp
}

/**
 * Player auto-hide is only permitted during uninterrupted playback. Menus, a pending
 * touch scrub, playback errors, and countdown prompts always keep controls available.
 */
internal fun shouldAutoHidePlayerHud(
    visible: Boolean,
    playing: Boolean,
    buffering: Boolean,
    hasError: Boolean,
    menuOpen: Boolean,
    scrubbing: Boolean,
    autoNextVisible: Boolean
): Boolean = visible && playing && !buffering && !hasError &&
    !menuOpen && !scrubbing && !autoNextVisible

/** Never substitute a stale local duration for an active Cast session. */
internal fun playerSeekDurationMs(
    localDurationMs: Long,
    castDurationMs: Long,
    castConnected: Boolean
): Long = (if (castConnected) castDurationMs else localDurationMs).coerceAtLeast(0L)

/**
 * The seek thumb is presentation state, not a download percentage. Clamp only finite,
 * valid playback targets, and never generate a fake seek for unknown-duration streams.
 */
internal fun playerScrubTarget(fraction: Float, durationMs: Long): Long? {
    if (durationMs <= 0L || !fraction.isFinite()) return null
    return (fraction.coerceIn(0f, 1f) * durationMs.toDouble()).toLong().coerceIn(0L, durationMs)
}

/**
 * All buttons delegate to the real PlayerScreen playback callbacks. No local playback
 * state is stored here and the remote's input controller retains exclusive ownership
 * of hidden-HUD seek preview and Back behavior.
 */
@Composable
internal fun PlayerTransportControls(
    layout: PlayerChromeLayout,
    isPlayingOrPending: Boolean,
    optionsExpanded: Boolean,
    onPrevious: () -> Unit,
    onSeekBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onNext: () -> Unit,
    onOptions: () -> Unit,
    playPauseFocusRequester: FocusRequester,
    optionsFocusRequester: FocusRequester
) {
    val isTv = layout == PlayerChromeLayout.TV
    val isWide = layout != PlayerChromeLayout.TOUCH_COMPACT
    val touchMin = if (isTv) 46.dp else 52.dp
    Row(
        modifier = Modifier.fillMaxWidth().testTag("player_transport_controls"),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 6.dp)
    ) {
        if (isWide && !isTv) {
            BousygramButton(
                text = "⏮",
                onClick = onPrevious,
                modifier = Modifier.weight(1f).heightIn(min = touchMin)
                    .semantics { contentDescription = "Previous video" }
                    .testTag("player_previous")
            )
        }
        if (!isTv) BousygramButton(
            text = "−10",
            onClick = onSeekBack,
            modifier = Modifier.weight(1f).heightIn(min = touchMin)
                .semantics { contentDescription = "Seek backward 10 seconds" }
                .testTag("player_seek_back")
        )
        BousygramButton(
            text = if (isTv) {
                if (isPlayingOrPending) "❚❚ Pause" else "▶ Play"
            } else if (isPlayingOrPending) "❚❚" else "▶",
            onClick = onPlayPause,
            selected = true,
            modifier = Modifier
                .weight(if (isTv) 1f else 1.3f)
                .heightIn(min = touchMin)
                .focusRequester(playPauseFocusRequester)
                .semantics { contentDescription = if (isPlayingOrPending) "Pause playback" else "Play video" }
                .testTag("player_toggle_playback")
        )
        if (!isTv) BousygramButton(
            text = "+10",
            onClick = onSeekForward,
            modifier = Modifier.weight(1f).heightIn(min = touchMin)
                .semantics { contentDescription = "Seek forward 10 seconds" }
                .testTag("player_seek_forward")
        )
        if (isWide && !isTv) {
            BousygramButton(
                text = "⏭",
                onClick = onNext,
                modifier = Modifier.weight(1f).heightIn(min = touchMin)
                    .semantics { contentDescription = "Next video" }
                    .testTag("player_next")
            )
        }
        BousygramButton(
            text = if (isTv) "⚙ More" else "⋯",
            onClick = onOptions,
            selected = optionsExpanded,
            modifier = Modifier.weight(1f).heightIn(min = touchMin)
                .focusRequester(optionsFocusRequester)
                .semantics { contentDescription = if (optionsExpanded) "Close playback options" else "More playback options" }
                .testTag("player_options_toggle")
        )
    }
}

/**
 * Advanced controls are reachable through one explicit focus target on TV, and one
 * touch target on phones. Never trap D-pad focus inside a horizontally scrolling row.
 */
@Composable
internal fun PlayerOptionsPanel(
    layout: PlayerChromeLayout,
    onTracks: () -> Unit,
    onSpeed: () -> Unit,
    speedLabel: String,
    onAspect: () -> Unit,
    aspectLabel: String,
    onEpisodes: (() -> Unit)?,
    onSwitchEngine: (() -> Unit)?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekBack30: () -> Unit,
    onSeekForward30: () -> Unit,
    onClose: () -> Unit,
    firstOptionFocusRequester: FocusRequester
) {
    val isTv = layout == PlayerChromeLayout.TV
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            .testTag("player_options_panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (isTv) "Playback options  ·  Back to return to controls" else "More playback controls",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = if (isTv) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BousygramButton(
                "Audio / CC", onTracks,
                modifier = (if (isTv) Modifier.weight(1f) else Modifier).heightIn(min = 48.dp)
                    .focusRequester(firstOptionFocusRequester)
            )
            BousygramButton(
                "$speedLabel Speed", onSpeed,
                modifier = (if (isTv) Modifier.weight(1f) else Modifier).heightIn(min = 48.dp)
            )
            BousygramButton(
                aspectLabel, onAspect,
                modifier = (if (isTv) Modifier.weight(1f) else Modifier).heightIn(min = 48.dp)
            )
            if (onEpisodes != null) {
                BousygramButton(
                    "☰ Queue", onEpisodes,
                    modifier = (if (isTv) Modifier.weight(1f) else Modifier).heightIn(min = 48.dp)
                )
            }
            BousygramButton(
                "✕ Done", onClose,
                modifier = (if (isTv) Modifier.weight(1f) else Modifier).heightIn(min = 48.dp)
            )
        }
        Row(
            modifier = if (isTv) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isTv) {
                BousygramButton("⏮ Previous", onPrevious, modifier = Modifier.heightIn(min = 48.dp))
                BousygramButton("Next ⏭", onNext, modifier = Modifier.heightIn(min = 48.dp))
            }
            BousygramButton(
                "↶ 30s", onSeekBack30,
                modifier = (if (isTv) Modifier.weight(1f) else Modifier).heightIn(min = 48.dp)
            )
            BousygramButton(
                "30s ↷", onSeekForward30,
                modifier = (if (isTv) Modifier.weight(1f) else Modifier).heightIn(min = 48.dp)
            )
            if (onSwitchEngine != null) {
                BousygramButton(
                    "⚙ Decoder", onSwitchEngine,
                    modifier = (if (isTv) Modifier.weight(1f) else Modifier).heightIn(min = 48.dp)
                )
            }
        }
    }
}
