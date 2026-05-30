package com.vaydelux.bousygram.presentation.media

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vaydelux.bousygram.core.media.TelegramProxyPlayerFactory
import com.vaydelux.bousygram.core.observability.PlaybackErrorMapper
import com.vaydelux.bousygram.core.observability.StructuredPlaybackLogger
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

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> showBufferingDebounced()
                Player.STATE_READY -> {
                    if (player.currentPosition > 0L) {
                        lastGoodPositionMs = player.currentPosition
                    }
                    setState(VideoPlayerUiState.Playing)
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
            if (fallbackToLowerQuality()) return
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
        stopActiveCaching()
        currentPlaybackKey = playbackKey
        activeOptions = playbackOptions
        activeOptionIndex = 0
        lastGoodPositionMs = 0L

        setState(VideoPlayerUiState.Loading)
        TelegramProxyManager.startProxy()
        tdLibClient?.let { client ->
            playbackOptions.forEach { option ->
                TelegramPreCacheEngine.primeVideoCache(client, option.fileId)
            }
        }

        playOption(playbackOptions.first(), resumePositionMs = 0L)
    }

    fun pausePlayback() {
        player.pause()
    }

    fun resumePlayback() {
        if (player.mediaItemCount > 0 && (player.playWhenReady || player.playbackState == Player.STATE_READY)) {
            primeActiveOption()
            player.play()
        }
    }

    fun stopPlayback() {
        bufferingJob?.cancel()
        player.pause()
        stopActiveCaching()
    }

    override fun onCleared() {
        bufferingJob?.cancel()
        stopActiveCaching()
        player.removeListener(playerListener)
        player.release()
        super.onCleared()
    }

    private fun showBufferingDebounced() {
        bufferingJob?.cancel()
        bufferingJob = viewModelScope.launch {
            delay(BUFFERING_DEBOUNCE_MS)
            if (player.playbackState == Player.STATE_BUFFERING) {
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
        TelegramPreCacheEngine.stopCaching(activeOptions[activeOptionIndex - 1].fileId)
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

    private fun playOption(option: VideoResolutionOption, resumePositionMs: Long) {
        player.stop()
        player.clearMediaItems()
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

    private fun stopActiveCaching() {
        activeOptions.forEach { option ->
            TelegramPreCacheEngine.stopCaching(option.fileId)
        }
    }

    private companion object {
        private const val TAG = "VideoPlayerViewModel"
        private const val BUFFERING_DEBOUNCE_MS = 150L
    }
}
