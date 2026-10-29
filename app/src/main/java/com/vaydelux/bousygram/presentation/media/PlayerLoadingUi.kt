package com.vaydelux.bousygram.presentation.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/** Pure state priority: errors are handled by the real player's Retry UI, never covered. */
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

/**
 * Drawn only before the video is ready. No network calls, focus targets, or gestures:
 * this skeleton cannot intercept remote playback controls or block loading the actual media.
 */
@Composable
internal fun PlayerOpeningSkeleton(compact: Boolean, modifier: Modifier = Modifier) {
    val shimmer = rememberShimmerBrush()
    val shape = RoundedCornerShape(if (compact) 16.dp else 24.dp)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF070C15), Color(0xFF0B1424), Color(0xFF080C15))
                )
            )
            .testTag("player_opening_skeleton")
            .semantics { contentDescription = "Preparing video" },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (compact) 0.76f else 0.60f)
                .widthIn(max = 620.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (compact) 110.dp else 180.dp)
                    .clip(shape)
                    .background(shimmer)
            )
            Box(
                Modifier
                    .fillMaxWidth(0.62f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmer)
            )
            Box(
                Modifier
                    .fillMaxWidth(0.40f)
                    .height(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(shimmer)
            )
        }
    }
}

/** Short, non-modal stream status. Small initial delays prevent flashing on fast cache hits. */
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
            delay(350L)
            show = true
        }
    }
    AnimatedVisibility(
        visible = show && phase != PlayerLoadingPhase.NONE,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val title = when (phase) {
            PlayerLoadingPhase.OPENING -> "Preparing video"
            PlayerLoadingPhase.RECONNECTING -> "Reconnecting stream"
            PlayerLoadingPhase.BUFFERING -> "Buffering video"
            PlayerLoadingPhase.NONE -> ""
        }
        val detail = when (phase) {
            PlayerLoadingPhase.OPENING -> "Fetching the first video segments from Telegram"
            PlayerLoadingPhase.RECONNECTING -> if (retryCount > 0) {
                "Trying again · attempt " + retryCount
            } else "Restoring your connection"
            PlayerLoadingPhase.BUFFERING -> "Loading the next part of the video"
            PlayerLoadingPhase.NONE -> ""
        }
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = if (compact) 310.dp else 380.dp)
                .testTag("player_loading_banner")
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = title + ". " + detail
                },
            shape = RoundedCornerShape(18.dp),
            color = Color(0xF0121B29),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color(0x553F83AD)),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        detail,
                        color = Color(0xFFC1CEDC),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
