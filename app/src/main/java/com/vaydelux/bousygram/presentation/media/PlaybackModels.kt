package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.core.observability.PlaybackRecoveryAction
import com.vaydelux.bousygram.core.tdlib.server.VideoResolutionOption

data class PlaybackRequest(
    /**
     * Loopback URL prepared during the bounded launch preflight, or null when preflight timed
     * out / was skipped. ExoPlayer always uses the direct TDLib data source; mpv prepares a fresh
     * loopback URL inside the player when this is null.
     */
    val initialStreamUrl: String?,
    val chatId: Long,
    val messageId: Long,
    val fileId: Int,
    val title: String,
    val queue: List<PlaybackQueueItem> = emptyList(),
    val subtitleUrl: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val durationSeconds: Int = 0,
    val fileSizeBytes: Long = 0L,
    val alternativeVideos: List<com.vaydelux.bousygram.domain.model.MediaAlternativeVideo> = emptyList(),
    /** Real TDLib message topic/thread identity; null is not an inferred topic. */
    val topicId: Long? = null
)

data class PlaybackQueueItem(
    /** Null until this item becomes active; then the player prepares a fresh scoped stream URL. */
    val url: String? = null,
    val chatId: Long,
    val messageId: Long,
    val title: String,
    val fileId: Int,
    val fileName: String? = null,
    val mimeType: String? = null,
    val durationSeconds: Int = 0,
    val fileSizeBytes: Long = 0L,
    val alternativeVideos: List<com.vaydelux.bousygram.domain.model.MediaAlternativeVideo> = emptyList(),
    /** Real TDLib message topic/thread identity; null is not an inferred topic. */
    val topicId: Long? = null
)

data class PlayerHudState(
    val title: String,
    val fileName: String?,
    val positionMs: Long,
    val durationMs: Long,
    val durationTrusted: Boolean,
    val speed: Float,
    val isPlaying: Boolean,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean,
    val isOpening: Boolean = false,
    val isReconnecting: Boolean = false,
    val retryCount: Int = 0,
    val error: String?,
    val trackLabel: String,
    val cacheStateLabel: String? = null,
    val bufferedPositionMs: Long = 0L,
    val resizeMode: Int = 0,
    val mediaKey: String = ""
)

sealed class PlaybackUiState {
    data object Loading : PlaybackUiState()
    data class Preparing(val title: String, val cacheState: String) : PlaybackUiState()
    data class Buffering(val title: String, val retryCount: Int, val cacheState: String) : PlaybackUiState()
    data class Playing(val title: String, val positionMs: Long, val durationMs: Long) : PlaybackUiState()
    data class Reconnecting(val title: String, val retryCount: Int) : PlaybackUiState()
    data class Error(
        val title: String,
        val message: String,
        val primaryAction: PlaybackRecoveryAction,
        val secondaryAction: PlaybackRecoveryAction?
    ) : PlaybackUiState()
    data object Ended : PlaybackUiState()
}

sealed class PlaybackUiEvent {
    data object Play : PlaybackUiEvent()
    data object Pause : PlaybackUiEvent()
    data class Seek(val positionMs: Long) : PlaybackUiEvent()
    data object Retry : PlaybackUiEvent()
    data object Skip : PlaybackUiEvent()
    data class ChangeSpeed(val speed: Float) : PlaybackUiEvent()
    data object SelectTrack : PlaybackUiEvent()
    data object LowerQuality : PlaybackUiEvent()
    data object CacheFirst : PlaybackUiEvent()
}

sealed class PlaybackEffect {
    data class Snackbar(val message: String) : PlaybackEffect()
    data object NavigateBack : PlaybackEffect()
    data class RequestQualitySwitch(val quality: QualityDecision) : PlaybackEffect()
}

data class QualityDecision(
    val selected: VideoResolutionOption,
    val reason: String,
    val resumePositionMs: Long
)
