package com.vaydelux.bousygram.presentation.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.components.rememberShimmerBrush
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.theme.rememberBousygramMotionPolicy
import kotlinx.coroutines.delay

/** Errors belong to the player's focusable Retry UI, not a blocking loading overlay. */
internal enum class PlayerLoadingPhase { NONE, OPENING, RECONNECTING, BUFFERING }

internal fun playerLoadingPhase(
    opening: Boolean,
    reconnecting: Boolean,
    buffering: Boolean,
    error: String?
): PlayerLoadingPhase = when {
    error != null -> PlayerLoadingPhase.NONE
    reconnecting -> PlayerLoadingPhase.RECONNECTING
    opening -> PlayerLoadingPhase.OPENING
    buffering -> PlayerLoadingPhase.BUFFERING
    else -> PlayerLoadingPhase.NONE
}

/** When casting, the remote session—not the local decoder—owns the visible loading state. */
internal fun playerLoadingPhaseForSession(
    castConnected: Boolean,
    castBuffering: Boolean,
    opening: Boolean,
    reconnecting: Boolean,
    buffering: Boolean,
    error: String?
): PlayerLoadingPhase = if (castConnected) {
    if (castBuffering) PlayerLoadingPhase.BUFFERING else PlayerLoadingPhase.NONE
} else {
    playerLoadingPhase(opening, reconnecting, buffering, error)
}

internal data class PlayerLoadingCopy(val title: String, val detail: String)

/** Loading must not advertise automatic resume after an explicit user pause. */
internal fun playerPlaybackStatusText(
    error: Boolean,
    reconnecting: Boolean,
    opening: Boolean,
    buffering: Boolean,
    playRequested: Boolean
): String = when {
    error -> "Playback error"
    !playRequested -> "Paused"
    reconnecting -> "Reconnecting · auto-resume"
    opening -> "Preparing playback"
    buffering -> "Buffering · auto-resume"
    else -> "Playing"
}

/** Never invent a percentage: the TDLib stream can seek and buffer discontinuous ranges. */
internal fun playerLoadingCopy(phase: PlayerLoadingPhase, retryCount: Int): PlayerLoadingCopy = when (phase) {
    PlayerLoadingPhase.OPENING -> PlayerLoadingCopy("Preparing playback", "Connecting to your Telegram video")
    PlayerLoadingPhase.RECONNECTING -> PlayerLoadingCopy(
        "Reconnecting stream",
        if (retryCount > 0) "Restoring playback · attempt $retryCount" else "Restoring your connection"
    )
    PlayerLoadingPhase.BUFFERING -> PlayerLoadingCopy("Buffering video", "Waiting for the next part of the stream")
    PlayerLoadingPhase.NONE -> PlayerLoadingCopy("", "")
}

/**
 * Decorative presentation only: no touch interception, focus targets, fake progress, or
 * additional media requests. An opening state disappears when the engine becomes ready.
 */
@Composable
internal fun PlayerOpeningSkeleton(compact: Boolean, modifier: Modifier = Modifier) {
    val shimmer = rememberShimmerBrush()
    val shortWindow = rememberAdaptiveMetrics().isShortWindow
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.background
            )))
            .testTag("player_opening_skeleton")
            .semantics { contentDescription = "Preparing playback" },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (compact) 0.84f else 0.64f)
                .widthIn(max = 680.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .height(if (shortWindow) 76.dp else if (compact) 122.dp else 210.dp)
                    .clip(RoundedCornerShape(if (compact) 16.dp else 24.dp))
                    .background(shimmer)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(if (compact) 30.dp else 40.dp).clip(CircleShape).background(shimmer))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.fillMaxWidth(0.82f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
                    Box(Modifier.fillMaxWidth(0.45f).height(9.dp).clip(RoundedCornerShape(5.dp)).background(shimmer))
                }
            }
            Text(
                "Getting your video ready",
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Consistent non-interactive status chip, with no remote focus or playback state ownership. */
@Composable
internal fun PlayerStatusChip(label: String, isError: Boolean, isBusy: Boolean) {
    val accent = when {
        isError -> MaterialTheme.colorScheme.error
        isBusy -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.34f)),
        modifier = Modifier.testTag("player_status_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Brief status banner with a distinct start/rebuffer delay to avoid flicker on cache hits. */
@Composable
internal fun PlayerLoadingBanner(
    phase: PlayerLoadingPhase,
    compact: Boolean,
    retryCount: Int,
    modifier: Modifier = Modifier
) {
    val motion = rememberBousygramMotionPolicy()
    val metrics = rememberAdaptiveMetrics()
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(phase) {
        show = false
        if (phase != PlayerLoadingPhase.NONE) {
            delay(if (phase == PlayerLoadingPhase.BUFFERING) 650L else 350L)
            show = true
        }
    }
    AnimatedVisibility(
        visible = show && phase != PlayerLoadingPhase.NONE,
        enter = fadeIn(animationSpec = tween(motion.duration(170))),
        exit = fadeOut(animationSpec = tween(motion.duration(120))),
        modifier = modifier
    ) {
        val copy = playerLoadingCopy(phase, retryCount)
        // The banner fits both 320dp portrait and short landscape: width is capped
        // by the actual parent, not a presumed device or the Android CPU ABI.
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = if (compact) 328.dp else 410.dp)
                    .fillMaxWidth()
                    .testTag("player_loading_banner")
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = copy.title + ". " + copy.detail
                    },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = if (motion.lowRam) 0.dp else 6.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = if (compact) 12.dp else 16.dp,
                            vertical = if (metrics.isShortWindow) 8.dp else 12.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (motion.animateIndeterminateProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(if (compact) 18.dp else 22.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            // Static accessible busy indicator: no infinite GPU loop on
                            // reduced-motion or low-RAM devices.
                            Box(
                                Modifier.size(12.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                copy.title,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                copy.detail,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = if (metrics.isShortWindow) 1 else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (motion.animateIndeterminateProgress) {
                        // Indeterminate activity only; never invent buffered percentages.
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}
