package com.vaydelux.bousygram.presentation.media

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vaydelux.bousygram.core.media.PlaybackCacheStopReason
import com.vaydelux.bousygram.core.media.PlaybackRetryDecision
import com.vaydelux.bousygram.core.media.PlaybackSession
import com.vaydelux.bousygram.core.media.PlaybackSessionController
import com.vaydelux.bousygram.core.media.TelegramProxyPlayerFactory
import com.vaydelux.bousygram.core.media.PlaybackPresentationState
import com.vaydelux.bousygram.core.media.playbackPresentationState
import com.vaydelux.bousygram.core.observability.PlaybackErrorMapper
import com.vaydelux.bousygram.core.observability.StructuredPlaybackLogger
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.core.tdlib.server.TelegramPreCacheEngine
import com.vaydelux.bousygram.core.tdlib.server.TelegramProxyManager
import com.vaydelux.bousygram.core.tdlib.server.VideoResolutionOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client

sealed class VideoPlayerUiState {
    data object Loading : VideoPlayerUiState()
    data object Playing : VideoPlayerUiState()
    data object Buffering : VideoPlayerUiState()
    data object Paused : VideoPlayerUiState()
    data class Error(val message: String) : VideoPlayerUiState()
    data object Ended : VideoPlayerUiState()
}

class VideoPlayerViewModel(application: Application) : AndroidViewModel(application) {
    val player: ExoPlayer = TelegramProxyPlayerFactory.build(application.applicationContext)

    private val _uiState = MutableStateFlow<VideoPlayerUiState>(VideoPlayerUiState.Loading)
    val uiState: StateFlow<VideoPlayerUiState> = _uiState

    private var tdLibClient: Client? = null
    private var currentPlaybackKey: String? = null
    private var activeOptions: List<VideoResolutionOption> = emptyList()
    private var activeOptionIndex = 0
    private var lastGoodPositionMs = 0L
    private var bufferingJob: Job? = null
    private var activeSession: PlaybackSession? = null
    private var currentErrorRetryCount = 0
    private var resumeAfterBackground = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Output can stop during cache stalls without the user requesting Pause.
            val playRequested = player.playWhenReady
            PlaybackSessionController.setVideoPlaying(
                isPlaying || (playRequested && player.playbackState == Player.STATE_BUFFERING)
            )
            if (player.playbackState == Player.STATE_READY) {
                reportReadyDecoderState()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (player.playbackState == Player.STATE_READY) {
                reportReadyDecoderState()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    if (player.playWhenReady) showBufferingDebounced()
                    else setState(VideoPlayerUiState.Paused)
                }
                Player.STATE_READY -> {
                    if (player.currentPosition > 0L) {
                        lastGoodPositionMs = player.currentPosition
                    }
                    reportReadyDecoderState()
                }
                Player.STATE_ENDED -> setState(VideoPlayerUiState.Ended)
                Player.STATE_IDLE -> {
                    if (player.mediaItemCount == 0) {
                        setState(VideoPlayerUiState.Loading)
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            bufferingJob?.cancel()
            val resolution = PlaybackErrorMapper.fromPlaybackException(error, retryCount = 0)
            val message = "${resolution.title}: ${resolution.message}"
            Log.e(TAG, "Playback error: $message", error)
            StructuredPlaybackLogger.error("video_player_vm_error", error)
            val session = activeSession
            val decision = if (session != null) {
                PlaybackSessionController.recordError(session, error, currentErrorRetryCount)
            } else {
                PlaybackRetryDecision.RetrySameQuality
            }
            if (resolution.recoverable && retryCurrentQuality(decision)) return
            if (resolution.recoverable && fallbackToLowerQuality()) return
            _uiState.value = VideoPlayerUiState.Error(message)
        }
    }

    init {
        player.addListener(playerListener)
        TelegramProxyManager.startProxy()
    }

    fun bindTdLibClient(client: Client) {
        tdLibClient = client
    }

    fun playVideo(options: List<VideoResolutionOption>) {
        val playbackOptions = options
            .filter { it.fileId > 0 }
            .distinctBy { it.fileId }
            .sortedByDescending { it.quality }

        if (playbackOptions.isEmpty()) {
            _uiState.value = VideoPlayerUiState.Error("No playable Telegram video file was provided.")
            return
        }

        val playbackKey = playbackOptions.joinToString("|") { "${it.quality}:${it.fileId}" }
        if (currentPlaybackKey == playbackKey && player.mediaItemCount > 0) return
        stopActiveCaching(PlaybackCacheStopReason.TrackSwitch)
        currentPlaybackKey = playbackKey
        activeOptions = playbackOptions
        activeOptionIndex = 0
        lastGoodPositionMs = 0L
        currentErrorRetryCount = 0

        resumeAfterBackground = false
        setState(VideoPlayerUiState.Loading)
        TelegramProxyManager.startProxy()
        playOption(playbackOptions.first(), resumePositionMs = 0L)
    }

    /** User pause must survive buffering, lifecycle transitions and Activity recreation. */
    fun pausePlayback() {
        resumeAfterBackground = false
        player.pause()
        setState(VideoPlayerUiState.Paused)
    }

    fun resumePlayback() {
        if (player.mediaItemCount > 0) {
            primeActiveOption()
            player.play()
        }
    }

    fun onActivityStopped() {
        resumeAfterBackground = player.playWhenReady
        player.pause()
        PlaybackSessionController.setVideoPlaying(false)
    }

    fun onActivityStarted() {
        if (resumeAfterBackground) {
            resumeAfterBackground = false
            resumePlayback()
        }
    }

    fun stopPlayback() {
        resumeAfterBackground = false
        bufferingJob?.cancel()
        player.pause()
        player.stop()
        player.clearMediaItems()
        stopActiveCaching(PlaybackCacheStopReason.ViewModelStop)
        currentPlaybackKey = null
        activeOptions = emptyList()
    }

    override fun onCleared() {
        PlaybackSessionController.setVideoPlaying(false)
        bufferingJob?.cancel()
        stopActiveCaching(PlaybackCacheStopReason.ViewModelCleared)
        player.removeListener(playerListener)
        player.release()
        super.onCleared()
    }

    /**
     * Media3 STATE_READY means the decoder is prepared, not necessarily
     * producing output (for example after a seek or audio-focus suppression).
     * A late Ready event must not revive playback after an explicit Pause.
     */
    private fun reportReadyDecoderState() {
        when (playbackPresentationState(
            ready = true,
            playWhenReady = player.playWhenReady,
            isPlaying = player.isPlaying
        )) {
            PlaybackPresentationState.PAUSED -> setState(VideoPlayerUiState.Paused)
            PlaybackPresentationState.PLAYING -> setState(VideoPlayerUiState.Playing)
            PlaybackPresentationState.BUFFERING -> showBufferingDebounced()
        }
    }

    private fun showBufferingDebounced() {
        bufferingJob?.cancel()
        bufferingJob = viewModelScope.launch {
            delay(BUFFERING_DEBOUNCE_MS)
            // READY without a decoded output is also waiting, not Playing.
            // A user Pause before the debounce expires must always win.
            if (player.playWhenReady && (
                    player.playbackState == Player.STATE_BUFFERING ||
                    (player.playbackState == Player.STATE_READY && !player.isPlaying)
                )
            ) {
                _uiState.value = VideoPlayerUiState.Buffering
            }
        }
    }

    private fun setState(state: VideoPlayerUiState) {
        bufferingJob?.cancel()
        _uiState.value = state
    }

    private fun fallbackToLowerQuality(): Boolean {
        if (activeOptionIndex >= activeOptions.lastIndex) return false
        val resumePosition = maxOf(player.currentPosition, lastGoodPositionMs).coerceAtLeast(0L)
        activeOptionIndex++
        val nextOption = activeOptions[activeOptionIndex]
        activeSession?.let {
            PlaybackSessionController.stop(it, PlaybackCacheStopReason.ErrorRecovery)
        }
        currentErrorRetryCount = 0
        Log.w(
            TAG,
            "Restarting adaptive playback at ${nextOption.quality}p fileId=${nextOption.fileId} " +
                "position=$resumePosition"
        )
        setState(VideoPlayerUiState.Buffering)
        tdLibClient?.let { TelegramPreCacheEngine.primeVideoCache(it, nextOption.fileId) }
        playOption(nextOption, resumePosition)
        return true
    }

    private fun retryCurrentQuality(decision: PlaybackRetryDecision): Boolean {
        if (currentErrorRetryCount >= 1 && decision != PlaybackRetryDecision.CacheFirstRetry) return false
        val option = activeOptions.getOrNull(activeOptionIndex) ?: return false
        val resumePosition = maxOf(player.currentPosition, lastGoodPositionMs).coerceAtLeast(0L)
        currentErrorRetryCount++
        setState(VideoPlayerUiState.Buffering)
        activeSession?.let { session ->
            if (decision == PlaybackRetryDecision.CacheFirstRetry) {
                val size = LocalStreamingServer.resolveFileSize(option.fileId)
                PlaybackSessionController.cacheAhead(
                    session = session,
                    totalSize = size,
                    positionMs = resumePosition,
                    durationMs = 0L,
                    startup = true
                )
            }
        }
        tdLibClient?.let { TelegramPreCacheEngine.primeVideoCache(it, option.fileId) }
        playOption(option, resumePosition)
        return true
    }

    private fun playOption(option: VideoResolutionOption, resumePositionMs: Long) {
        player.stop()
        player.clearMediaItems()
        activeSession = PlaybackSessionController.start(option.fileId, resumePositionMs)
        player.setMediaItem(MediaItem.fromUri(TelegramProxyManager.baseStreamUrl(option.fileId)))
        player.prepare()
        if (resumePositionMs > 0L) {
            player.seekTo(resumePositionMs)
        }
        player.play()
    }

    private fun primeActiveOption() {
        val client = tdLibClient ?: return
        val option = activeOptions.getOrNull(activeOptionIndex) ?: return
        TelegramPreCacheEngine.primeVideoCache(client, option.fileId)
    }

    private fun stopActiveCaching(reason: PlaybackCacheStopReason) {
        activeSession?.let { PlaybackSessionController.stop(it, reason) }
        activeOptions.forEach { option ->
            TelegramPreCacheEngine.stopCaching(option.fileId)
        }
        activeSession = null
    }

    private companion object {
        private const val TAG = "VideoPlayerViewModel"
        private const val BUFFERING_DEBOUNCE_MS = 150L
    }
}
