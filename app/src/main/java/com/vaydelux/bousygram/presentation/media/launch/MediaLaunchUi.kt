package com.vaydelux.bousygram.presentation.media.launch

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.theme.rememberBousygramMotionPolicy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.media.PlaybackRequest
import kotlinx.coroutines.CoroutineScope

/** Screen-scoped handle onto the shared [PlaybackLaunchCoordinator]. */
@Stable
class MediaLauncher internal constructor(
    private val coordinator: PlaybackLaunchCoordinator,
    private val scope: CoroutineScope,
    private val screenKey: String,
    private val onPlay: State<(PlaybackRequest) -> Unit>,
    private val onError: State<(String) -> Unit>,
    val state: State<LaunchState>
) {
    val pendingKey: String? get() = state.value.pendingKey
    val isPending: Boolean get() = pendingKey != null

    fun isPending(item: MediaItem): Boolean = pendingKey == PlaybackLaunchCoordinator.mediaKey(item)

    fun launch(item: MediaItem, queue: List<MediaItem>, sectionKey: String? = null) {
        val anchor = LaunchAnchor(screenKey, sectionKey, PlaybackLaunchCoordinator.mediaKey(item))
        coordinator.launch(scope, item, queue, anchor) { outcome ->
            when (outcome) {
                is LaunchOutcome.Open -> onPlay.value(outcome.request)
                is LaunchOutcome.Rejected -> onError.value(outcome.reason)
                LaunchOutcome.Cancelled, LaunchOutcome.Duplicate -> Unit
            }
        }
    }

    fun cancel(): Boolean = coordinator.cancel()

    /** Anchor of the last launch from this screen, for focus restoration after the player. */
    fun anchor(): LaunchAnchor? = coordinator.anchorFor(screenKey)
}

@Composable
fun rememberMediaLauncher(
    screenKey: String,
    onPlay: (PlaybackRequest) -> Unit,
    onError: (String) -> Unit,
    coordinator: PlaybackLaunchCoordinator = PlaybackLaunch.coordinator
): MediaLauncher {
    val scope = rememberCoroutineScope()
    val playState = rememberUpdatedState(onPlay)
    val errorState = rememberUpdatedState(onError)
    val launchState = coordinator.state.collectAsState()
    val launcher = remember(coordinator, scope, screenKey) {
        MediaLauncher(coordinator, scope, screenKey, playState, errorState, launchState)
    }
    // Back during the ≤500 ms window cancels the launch and keeps focus on the card.
    BackHandler(enabled = launchState.value.isPending) { launcher.cancel() }
    return launcher
}

/** Small non-blocking indicator shown only when preparation exceeds ~120 ms. */
@Composable
fun LaunchOpeningIndicator(launcher: MediaLauncher, modifier: Modifier = Modifier) {
    val state by launcher.state
    val motion = rememberBousygramMotionPolicy()
    val metrics = rememberAdaptiveMetrics()
    AnimatedVisibility(
        visible = state.isPending && state.showIndicator,
        enter = fadeIn(animationSpec = tween(motion.duration(140))),
        exit = fadeOut(animationSpec = tween(motion.duration(120))),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .widthIn(max = if (metrics.isCompact)
                    (metrics.widthDp - 24.dp).coerceAtLeast(1.dp) else 420.dp)
                .semantics { contentDescription = "Opening media. Back cancels" }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (motion.animateIndeterminateProgress) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Box(Modifier.size(12.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape))
            }
            Text(
                "Opening… (Back to cancel)",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
