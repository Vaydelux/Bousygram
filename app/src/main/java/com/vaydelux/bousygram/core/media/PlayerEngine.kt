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
    val engineName: String
    val playbackState: StateFlow<EnginePlaybackState>
    /** Null for engines without an authoritative video-output callback (currently libmpv). */
    val firstFrameRendered: StateFlow<Boolean>? get() = null
    val currentPositionMs: Long
    val durationMs: Long
    /** True only for a demuxer-provided, seekable whole-file duration; never a partial-buffer guess. */
    val seekableContainerDuration: Boolean get() = false
    val bufferedPositionMs: Long
    val isPlaying: Boolean
    val playWhenReady: Boolean
    val activeDecoder: String
    val videoDecoderName: String get() = activeDecoder
    val audioDecoderName: String get() = "Unknown"
    val tracks: StateFlow<List<MediaTrackInfo>>
    val videoDimensions: StateFlow<Pair<Int, Int>>
    val bufferConfigDescription: String get() = ""
    val renderedOutputBufferCount: Int get() = 0
    val droppedBufferCount: Int get() = 0
    val skippedOutputBufferCount: Int get() = 0
    val inputBufferCount: Int get() = 0
    val isVideoTrackSelected: Boolean get() = true
    val disabledTrackTypesDesc: String get() = "None"
    val diagnosticDetails: Map<String, String> get() = emptyMap()

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

