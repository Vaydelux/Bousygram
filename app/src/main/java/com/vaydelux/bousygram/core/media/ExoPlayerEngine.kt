package com.vaydelux.bousygram.core.media

import android.content.Context
import android.net.Uri
import android.media.audiofx.Equalizer
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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

    private var requestedEqPreset = AudioEqualizerPreset.ORIGINAL
    private var audioFx: Equalizer? = null
    private var audioFxSessionId = -1

    private fun releaseAudioFx() {
        runCatching { audioFx?.let { it.enabled = false } }
        runCatching { audioFx?.release() }
        audioFx = null
        audioFxSessionId = -1
    }

    override fun applyAudioEqualizer(preset: AudioEqualizerPreset): Boolean {
        requestedEqPreset = preset
        if (!preset.isEnabled) {
            releaseAudioFx()
            return true
        }
        val session = player.audioSessionId
        // Never create a global output-mix effect on session 0.
        if (session <= 0) {
            releaseAudioFx()
            return false
        }
        return runCatching {
            if (audioFx == null || audioFxSessionId != session) {
                releaseAudioFx()
                val created = Equalizer(0, session)
                if (!created.hasControl()) {
                    created.release()
                    return@runCatching false
                }
                audioFx = created
                audioFxSessionId = session
            }
            val fx = requireNotNull(audioFx)
            val range = fx.bandLevelRange
            for (index in 0 until fx.numberOfBands.toInt()) {
                val band = index.toShort()
                val frequencyHz = fx.getCenterFreq(band) / 1_000f
                fx.setBandLevel(
                    band, audioEqMillibels(preset, frequencyHz, range[0].toInt(), range[1].toInt())
                )
            }
            fx.enabled = true
            fx.enabled
        }.getOrElse { error ->
            Log.w("BousyAudioEq", "Android AudioFX unavailable for this output", error)
            releaseAudioFx()
            false
        }
    }

    private val _playbackState = MutableStateFlow<EnginePlaybackState>(EnginePlaybackState.Idle)
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()
    private val _firstFrameRendered = MutableStateFlow(false)
    override val firstFrameRendered: StateFlow<Boolean> = _firstFrameRendered.asStateFlow()

    override val currentPositionMs: Long get() = player.currentPosition.coerceAtLeast(0L)
    override val durationMs: Long get() = player.duration.coerceAtLeast(0L)
    private var isContainerWithFullFileSeekMap = false
    override val seekableContainerDuration: Boolean
        get() = isContainerWithFullFileSeekMap && player.isCurrentMediaItemSeekable &&
            player.duration > 0L && player.duration != C.TIME_UNSET
    override val bufferedPositionMs: Long get() = player.bufferedPosition.coerceAtLeast(0L)
    override val isPlaying: Boolean get() = player.isPlaying
    override val playWhenReady: Boolean get() = player.playWhenReady
    override val canSeek: Boolean get() = player.isCurrentMediaItemSeekable

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
        override fun onRenderedFirstFrame(
            eventTime: AnalyticsListener.EventTime,
            output: Any,
            renderTimeMs: Long
        ) {
            // STATE_READY is not proof that video has actually appeared on the surface.
            _firstFrameRendered.value = true
        }

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
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            // Media3 can replace its AudioTrack/session across format or route changes.
            releaseAudioFx()
            if (requestedEqPreset.isEnabled) applyAudioEqualizer(requestedEqPreset)
        }

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
        _firstFrameRendered.value = false
        val uri = Uri.parse(streamUrl)
        val name = uri.getQueryParameter("name").orEmpty()
        // Telegram documents often have only a MIME type or title, not a usable filename.
        // The source URI carries the already validated progressive/container MIME hint.
        val mime = uri.getQueryParameter("mime")
            ?: VideoFormatSupport.exoMimeType(null, name, name)
        isContainerWithFullFileSeekMap = mime == MimeTypes.VIDEO_MATROSKA ||
            mime == MimeTypes.VIDEO_WEBM || mime == MimeTypes.VIDEO_MP2T
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .apply {
                if (mime == MimeTypes.VIDEO_MATROSKA ||
                    mime == MimeTypes.VIDEO_WEBM || mime == MimeTypes.VIDEO_MP2T
                ) {
                    // .ts and .mkv names are hidden in tdlib:// query parameters.
                    // Explicitly identify their progressive container for Media3.
                    setMimeType(mime)
                }
            }
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
        val target = decodeExoTrackUiId(track.id) ?: return
        val group = player.currentTracks.groups.getOrNull(target.first) ?: return
        if (group.type != targetType || target.second !in 0 until group.length) return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            // Selecting a subtitle after "Off" must re-enable text tracks.
            .setTrackTypeDisabled(targetType, false)
            .setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, listOf(target.second))
            )
            .build()
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
        _firstFrameRendered.value = false
        player.stop()
        player.clearMediaItems()
        _playbackState.value = EnginePlaybackState.Idle
    }

    override fun release() {
        releaseAudioFx()
        player.removeListener(listener)
        player.removeAnalyticsListener(analyticsListener)
        player.release()
    }

    private fun updateTracks(tracks: Tracks) {
        val list = mutableListOf<MediaTrackInfo>()
        for ((groupIndex, group) in tracks.groups.withIndex()) {
            val type = when (group.type) {
                C.TRACK_TYPE_AUDIO -> MediaTrackInfo.TrackType.AUDIO
                C.TRACK_TYPE_TEXT -> MediaTrackInfo.TrackType.SUBTITLE
                C.TRACK_TYPE_VIDEO -> MediaTrackInfo.TrackType.VIDEO
                else -> continue
            }
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val isSelected = group.isTrackSelected(i)
                // Format IDs are not unique across Media3 groups. Use an
                // encoded group+track index for unambiguous UI selection.
                val trackId = exoTrackUiId(groupIndex, i)
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
