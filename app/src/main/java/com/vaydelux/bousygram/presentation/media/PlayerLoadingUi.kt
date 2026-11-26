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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.components.BousygramBrandMark
import com.vaydelux.bousygram.presentation.components.BousygramPlayerDynamicIsland
import com.vaydelux.bousygram.presentation.components.DynamicIslandTone
import com.vaydelux.bousygram.presentation.components.rememberShimmerBrush
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors
import com.vaydelux.bousygram.presentation.theme.LocalBousygramReduceTransparency
import com.vaydelux.bousygram.presentation.theme.LocalBousygramStyleSpec
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
            .background(MaterialTheme.colorScheme.background)
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
            val styleSpec = LocalBousygramStyleSpec.current
            Box(
                Modifier.fillMaxWidth()
                    .height(if (shortWindow) 76.dp else if (compact) 122.dp else 210.dp)
                    .clip(RoundedCornerShape(styleSpec.panelCorner))
                    .background(shimmer),
                contentAlignment = Alignment.Center
            ) {
                BousygramBrandMark(
                    size = if (shortWindow) 38.dp else if (compact) 48.dp else 62.dp,
                    squircleFrame = true
                )
            }
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

/** Consistent non-interactive Dynamic Island style status chip, with no remote focus ownership. */
@Composable
internal fun PlayerStatusChip(label: String, isError: Boolean, isBusy: Boolean) {
    val halo = LocalBousygramHaloColors.current
    val accent = when {
        isError -> MaterialTheme.colorScheme.error
        isBusy -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val style = LocalBousygramStyleSpec.current
    val reduced = LocalBousygramReduceTransparency.current
    val themed = halo.isHighContrast || !style.isLiquidGlass
    val chipBg = if (themed) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        Color(0xFF090D16).copy(alpha = if (reduced) 1f else 0.92f)
    }
    val chipFg = if (themed) MaterialTheme.colorScheme.onSurface else Color(0xFFF5F8FF)
    Surface(
        shape = RoundedCornerShape(if (style.isLiquidGlass) BousygramUiTokens.pillCorner else style.tabCorner),
        color = chipBg,
        border = BorderStroke(1.dp, if (isError) accent.copy(alpha = 0.65f) else if (themed) MaterialTheme.colorScheme.outline else Color.White.copy(alpha = 0.18f)),
        modifier = Modifier.testTag("player_status_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(6.5.dp).clip(CircleShape).background(accent))
            Text(
                label,
                color = chipFg,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Brief top-center Dynamic Island status capsule with a distinct start/rebuffer delay to avoid flicker on cache hits. */
@Composable
internal fun PlayerLoadingBanner(
    phase: PlayerLoadingPhase,
    compact: Boolean,
    retryCount: Int,
    modifier: Modifier = Modifier
) {
    val motion = rememberBousygramMotionPolicy()
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
        val islandTone = when (phase) {
            PlayerLoadingPhase.RECONNECTING -> DynamicIslandTone.WARNING
            PlayerLoadingPhase.BUFFERING -> DynamicIslandTone.SYNCING
            else -> DynamicIslandTone.ACCENT
        }
        // Compact non-focusable Dynamic Island capsule fits 320dp portrait and 540dp landscape safely.
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            BousygramPlayerDynamicIsland(
                title = copy.title,
                detail = copy.detail,
                isBusy = true,
                compact = compact,
                tone = islandTone,
                modifier = Modifier.testTag("player_loading_banner")
            )
        }
    }
}
