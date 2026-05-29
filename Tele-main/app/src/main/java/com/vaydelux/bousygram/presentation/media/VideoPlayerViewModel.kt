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
    private var bufferingJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> showBufferingDebounced()
                Player.STATE_READY -> setState(VideoPlayerUiState.Playing)
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
            val message = error.message ?: "Playback failed"
            Log.e(TAG, "Playback error: $message", error)
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

        if (playbackOptions.isEmpty()) {
            _uiState.value = VideoPlayerUiState.Error("No playable Telegram video file was provided.")
            return
        }

        val playbackKey = playbackOptions.joinToString("|") { "${it.quality}:${it.fileId}" }
        if (currentPlaybackKey == playbackKey && player.mediaItemCount > 0) return
        currentPlaybackKey = playbackKey

        setState(VideoPlayerUiState.Loading)
        TelegramProxyManager.startProxy()
        tdLibClient?.let { client ->
            playbackOptions.forEach { option ->
                TelegramPreCacheEngine.primeVideoCache(client, option.fileId)
            }
        }

        val streamUrl = if (playbackOptions.size > 1) {
            TelegramProxyManager.adaptiveStreamUrl(playbackOptions)
        } else {
            TelegramProxyManager.baseStreamUrl(playbackOptions.first().fileId)
        }

        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
        player.play()
    }

    fun pausePlayback() {
        player.pause()
    }

    fun resumePlayback() {
        if (player.mediaItemCount > 0 && (player.playWhenReady || player.playbackState == Player.STATE_READY)) {
            player.play()
        }
    }

    fun stopPlayback() {
        bufferingJob?.cancel()
        player.pause()
    }

    override fun onCleared() {
        bufferingJob?.cancel()
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

    private companion object {
        private const val TAG = "VideoPlayerViewModel"
        private const val BUFFERING_DEBOUNCE_MS = 150L
    }
}
