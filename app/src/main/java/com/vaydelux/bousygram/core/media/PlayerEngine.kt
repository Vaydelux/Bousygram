package com.vaydelux.bousygram.core.media

import android.view.Surface
import kotlinx.coroutines.flow.StateFlow

sealed class EnginePlaybackState {
    data object Idle : EnginePlaybackState()
    data object Buffering : EnginePlaybackState()
    data object Ready : EnginePlaybackState()
    data object Ended : EnginePlaybackState()
    data class Error(val message: String, val cause: Throwable? = null) : EnginePlaybackState()
}

data class MediaTrackInfo(
    val id: Int,
    val type: TrackType,
    val name: String,
    val language: String? = null,
    val isSelected: Boolean = false
) {
    enum class TrackType { AUDIO, SUBTITLE, VIDEO }
}

interface PlayerEngine {
    val playbackState: StateFlow<EnginePlaybackState>
    val currentPositionMs: Long
    val durationMs: Long
    val bufferedPositionMs: Long
    val isPlaying: Boolean
    val activeDecoder: String
    val tracks: StateFlow<List<MediaTrackInfo>>
    val videoDimensions: StateFlow<Pair<Int, Int>>

    fun attachSurface(surface: Surface)
    fun detachSurface()
    fun prepare(streamUrl: String)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun setResizeMode(mode: Int)
    fun selectTrack(track: MediaTrackInfo)
    fun deselectTrack(type: MediaTrackInfo.TrackType)
    fun updateNetworkState(isCellular: Boolean)
    fun stop()
    fun release()
}

