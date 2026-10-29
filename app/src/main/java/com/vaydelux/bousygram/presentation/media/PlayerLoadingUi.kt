package com.vaydelux.bousygram.presentation.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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

/** Never invent a percentage: the TDLib stream can seek and buffer discontinuous ranges. */
internal fun playerLoadingCopy(phase: PlayerLoadingPhase, retryCount: Int): PlayerLoadingCopy = when (phase) {
    PlayerLoadingPhase.OPENING -> PlayerLoadingCopy("Preparing playback", "Connecting to your Telegram video")
    PlayerLoadingPhase.RECONNECTING -> PlayerLoadingCopy(
        "Reconnecting stream",
        if (retryCount > 0) "Restoring playback · attempt $retryCount" else "Restoring your connection"
    )
    PlayerLoadingPhase.BUFFERING -> PlayerLoadingCopy("Buffering video", "Getting the next part ready")
    PlayerLoadingPhase.NONE -> PlayerLoadingCopy("", "")
}

/**
 * Decorative presentation only: no touch interception, focus targets, fake progress, or
 * additional media requests. An opening state disappears when the engine becomes ready.
 */
@Composable
internal fun PlayerOpeningSkeleton(compact: Boolean, modifier: Modifier = Modifier) {
    val shimmer = rememberShimmerBrush()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF070C15), Color(0xFF101C2D), Color(0xFF070C15))))
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
                    .height(if (compact) 122.dp else 210.dp)
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
                color = Color(0xFFB8C8D9)
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
                color = Color(0xFFE9F1FA),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
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
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val copy = playerLoadingCopy(phase, retryCount)
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = if (compact) 310.dp else 390.dp)
                .testTag("player_loading_banner")
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = copy.title + ". " + copy.detail
                },
            shape = RoundedCornerShape(18.dp),
            color = Color(0xF2131F2F),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color(0x66547798)),
            shadowElevation = 10.dp
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(if (compact) 20.dp else 24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            copy.title,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            copy.detail,
                            color = Color(0xFFC1CEDC),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Indeterminate activity only: a fake buffered percentage would mislead users.
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0xFF26394F)
                )
            }
        }
    }
}
