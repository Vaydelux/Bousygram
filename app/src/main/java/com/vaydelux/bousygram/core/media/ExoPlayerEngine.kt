package com.vaydelux.bousygram.core.media

import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ExoPlayer implementation of [PlayerEngine].
 * Wraps [ExoPlayer] created by [TelegramProxyPlayerFactory].
 */
@OptIn(UnstableApi::class)
class ExoPlayerEngine(
    val player: ExoPlayer,
    val bufferConfig: PlayerBufferConfig.ExoBufferConfig? = null
) : PlayerEngine {

    companion object {
        fun create(
            context: Context,
            preferredSpeed: Float = 1.0f
        ): ExoPlayerEngine {
            val bufferConfig = PlayerBufferConfig.resolveExoConfig(context)
            val exo = TelegramProxyPlayerFactory.build(
                context = context,
                preferredSpeed = preferredSpeed,
                bufferConfig = bufferConfig
            )
            return ExoPlayerEngine(exo, bufferConfig)
        }
    }

    override val engineName: String = "ExoPlayer"
    override val bufferConfigDescription: String get() = bufferConfig?.description ?: "exo:default"

    private val _playbackState = MutableStateFlow<EnginePlaybackState>(EnginePlaybackState.Idle)
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()

    override val currentPositionMs: Long get() = player.currentPosition.coerceAtLeast(0L)
    override val durationMs: Long get() = player.duration.coerceAtLeast(0L)
    override val bufferedPositionMs: Long get() = player.bufferedPosition.coerceAtLeast(0L)
    override val isPlaying: Boolean get() = player.isPlaying
    override val playWhenReady: Boolean get() = player.playWhenReady

    private val _activeDecoder = MutableStateFlow("MediaCodec (Hardware)")
    override val activeDecoder: String get() = _activeDecoder.value

    private var _videoDecoderName: String = "Unknown"
    override val videoDecoderName: String get() = _videoDecoderName

    private var _audioDecoderName: String = "Unknown"
    override val audioDecoderName: String get() = _audioDecoderName

    private val _tracks = MutableStateFlow<List<MediaTrackInfo>>(emptyList())
    override val tracks: StateFlow<List<MediaTrackInfo>> = _tracks.asStateFlow()

    private val _videoDimensions = MutableStateFlow<Pair<Int, Int>>(Pair(0, 0))
    override val videoDimensions: StateFlow<Pair<Int, Int>> = _videoDimensions.asStateFlow()

    override val renderedOutputBufferCount: Int
        get() = player.videoDecoderCounters?.renderedOutputBufferCount ?: 0
    override val droppedBufferCount: Int
        get() = player.videoDecoderCounters?.droppedBufferCount ?: 0
    override val skippedOutputBufferCount: Int
        get() = player.videoDecoderCounters?.skippedOutputBufferCount ?: 0
    override val inputBufferCount: Int
        get() = player.videoDecoderCounters?.queuedInputBufferCount ?: 0
    override val isVideoTrackSelected: Boolean
        get() = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
    override val disabledTrackTypesDesc: String
        get() = player.trackSelectionParameters.disabledTrackTypes.joinToString { type ->
            when (type) {
                C.TRACK_TYPE_VIDEO -> "VIDEO"
                C.TRACK_TYPE_AUDIO -> "AUDIO"
                C.TRACK_TYPE_TEXT -> "TEXT"
                else -> type.toString()
            }
        }.ifBlank { "None" }
    override val diagnosticDetails: Map<String, String>
        get() = mapOf(
            "hasSurface" to (player.videoSize.width > 0).toString(),
            "playWhenReady" to player.playWhenReady.toString(),
            "playbackState" to when (player.playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> player.playbackState.toString()
            }
        )

    private val analyticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            _videoDecoderName = decoderName
            updateActiveDecoder()
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            _audioDecoderName = decoderName
            updateActiveDecoder()
        }
    }

    private fun updateActiveDecoder() {
        if (_videoDecoderName != "Unknown" || _audioDecoderName != "Unknown") {
            _activeDecoder.value = "$_videoDecoderName / $_audioDecoderName"
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> _playbackState.value = EnginePlaybackState.Buffering
                Player.STATE_READY -> _playbackState.value = EnginePlaybackState.Ready
                Player.STATE_ENDED -> _playbackState.value = EnginePlaybackState.Ended
                Player.STATE_IDLE -> {
                    if (player.mediaItemCount == 0) {
                        _playbackState.value = EnginePlaybackState.Idle
                    }
                }
            }
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                _videoDimensions.value = Pair(videoSize.width, videoSize.height)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.value = EnginePlaybackState.Error(
                error.localizedMessage ?: "ExoPlayer error",
                error
            )
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateTracks(tracks)
        }
    }

    init {
        player.addListener(listener)
        player.addAnalyticsListener(analyticsListener)
    }

    override fun attachSurface(surface: Surface) {
        player.setVideoSurface(surface)
    }

    override fun detachSurface() {
        player.clearVideoSurface()
    }

    override fun prepare(streamUrl: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    override fun setResizeMode(mode: Int) {
        // Handled via PlayerView or VideoSurface layout
    }

    override fun selectTrack(track: MediaTrackInfo) {
        val targetType = when (track.type) {
            MediaTrackInfo.TrackType.AUDIO -> C.TRACK_TYPE_AUDIO
            MediaTrackInfo.TrackType.SUBTITLE -> C.TRACK_TYPE_TEXT
            MediaTrackInfo.TrackType.VIDEO -> C.TRACK_TYPE_VIDEO
        }
        val currentTracks = player.currentTracks
        for (group in currentTracks.groups) {
            if (group.type == targetType) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (format.id?.toIntOrNull() == track.id || i == track.id) {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(i)))
                            .build()
                        return
                    }
                }
            }
        }
    }

    override fun deselectTrack(type: MediaTrackInfo.TrackType) {
        val targetType = when (type) {
            MediaTrackInfo.TrackType.AUDIO -> C.TRACK_TYPE_AUDIO
            MediaTrackInfo.TrackType.SUBTITLE -> C.TRACK_TYPE_TEXT
            MediaTrackInfo.TrackType.VIDEO -> C.TRACK_TYPE_VIDEO
        }
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(targetType, true)
            .build()
    }

    override fun updateNetworkState(isCellular: Boolean) {
        // ExoPlayer DefaultLoadControl manages buffering runway
    }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
        _playbackState.value = EnginePlaybackState.Idle
    }

    override fun release() {
        player.removeListener(listener)
        player.removeAnalyticsListener(analyticsListener)
        player.release()
    }

    private fun updateTracks(tracks: Tracks) {
        val list = mutableListOf<MediaTrackInfo>()
        for (group in tracks.groups) {
            val type = when (group.type) {
                C.TRACK_TYPE_AUDIO -> MediaTrackInfo.TrackType.AUDIO
                C.TRACK_TYPE_TEXT -> MediaTrackInfo.TrackType.SUBTITLE
                C.TRACK_TYPE_VIDEO -> MediaTrackInfo.TrackType.VIDEO
                else -> continue
            }
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val isSelected = group.isTrackSelected(i)
                val trackId = format.id?.toIntOrNull() ?: i
                val displayName = format.label ?: format.language ?: "Track $i"
                list.add(
                    MediaTrackInfo(
                        id = trackId,
                        type = type,
                        name = displayName,
                        language = format.language,
                        isSelected = isSelected
                    )
                )
            }
        }
        _tracks.value = list
    }
}
