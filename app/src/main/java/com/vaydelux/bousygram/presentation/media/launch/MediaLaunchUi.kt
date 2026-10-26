package com.vaydelux.bousygram.presentation.media.launch

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
    AnimatedVisibility(
        visible = state.isPending && state.showIndicator,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .semantics { contentDescription = "Opening media" }
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                "Opening… (Back to cancel)",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
