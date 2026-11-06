package com.vaydelux.bousygram.core.media

import android.content.Context
import android.net.Uri
import android.media.audiofx.Equalizer
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean
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
    val bufferConfig: PlayerBufferConfig.ExoBufferConfig? = null,
    private val outputContext: Context? = null
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
            return ExoPlayerEngine(exo, bufferConfig, context.applicationContext ?: context)
        }
    }

    // Media3 audio sessions can be replaced asynchronously. A disposed decoder must
    // never reacquire AudioFX through a stale settings or session callback.
    private val isReleased = AtomicBoolean(false)
    @Volatile private var audioOutputTopologyEvents = 0
    @Volatile private var audioOutputRefreshAttempts = 0
    private val outputTopologyMonitor = outputContext?.let { appContext ->
        AudioOutputTopologyMonitor(appContext, player.applicationLooper) {
            onAudioOutputTopologyChanged()
        }
    }
    private var outputMonitorRegistered = false

    /**
     * A connected-device change is not proof that Media3 switched its routed
     * output. Rebind only local effects; never restart, pause or seek playback.
     */
    private fun onAudioOutputTopologyChanged() {
        if (isReleased.get()) return
        audioOutputTopologyEvents++
        when (audioTopologyRecoveryAction(
            activeAudioProfile,
            hasMedia = player.mediaItemCount > 0,
            isReady = player.playbackState == Player.STATE_READY,
            sessionId = player.audioSessionId
        )) {
            AudioTopologyRecoveryAction.IGNORE -> Unit
            AudioTopologyRecoveryAction.WAIT_FOR_OUTPUT -> {
                releaseAudioFx()
                releaseSmartVolume()
                markEffectsWaitingForOutput()
            }
            AudioTopologyRecoveryAction.REAPPLY -> {
                releaseAudioFx()
                releaseSmartVolume()
                markEffectsWaitingForOutput()
                audioOutputRefreshAttempts++
                applyAudioProcessing(activeAudioProfile)
            }
        }
    }

    override val engineName: String = "ExoPlayer"
    override val bufferConfigDescription: String get() = bufferConfig?.description ?: "exo:default"

    private val _audioEqStatus = MutableStateFlow(AudioEffectStatus.BYPASSED)
    override val audioEqualizerStatus: StateFlow<AudioEffectStatus> = _audioEqStatus.asStateFlow()
    private var requestedEqPreset = AudioEqualizerPreset.ORIGINAL
    private var activeAudioProfile = AudioProcessingProfile()
    private val _smartVolumeStatus = MutableStateFlow(AudioEffectStatus.BYPASSED)
    override val smartVolumeStatus: StateFlow<AudioEffectStatus> = _smartVolumeStatus.asStateFlow()
    private var dynamicsEffect: DynamicsProcessing? = null
    private var dynamicsSession = -1
    private var dynamicsMode = SmartVolumeMode.OFF

    /** Stopped media has no active audible output even if the session id is cached. */
    private fun markEffectsWaitingForOutput() {
        _audioEqStatus.value = audioEffectStatusWithoutOutput(activeAudioProfile.preset.isEnabled)
        _smartVolumeStatus.value = audioEffectStatusWithoutOutput(
            activeAudioProfile.smartVolume != SmartVolumeMode.OFF
        )
    }

    private fun releaseSmartVolume() {
        runCatching { dynamicsEffect?.let { it.enabled = false } }
        runCatching { dynamicsEffect?.release() }
        dynamicsEffect = null
        dynamicsSession = -1
        dynamicsMode = SmartVolumeMode.OFF
    }

    override fun applyAudioProcessing(profile: AudioProcessingProfile): Boolean {
        if (isReleased.get()) return false
        activeAudioProfile = profile.copy(customGainsDb = profile.sanitizedGains)
        if (player.mediaItemCount == 0) {
            // A retained Media3 session ID does not indicate audible output.
            releaseAudioFx()
            releaseSmartVolume()
            markEffectsWaitingForOutput()
            return !profile.preset.isEnabled && profile.smartVolume == SmartVolumeMode.OFF
        }
        val equalizerApplied = applyAudioEqualizer(profile.preset)
        val volumeApplied = applySmartVolume(profile.smartVolume)
        return equalizerApplied && volumeApplied
    }

    private fun applySmartVolume(mode: SmartVolumeMode): Boolean {
        if (isReleased.get()) return false
        if (mode == SmartVolumeMode.OFF) {
            releaseSmartVolume()
            _smartVolumeStatus.value = AudioEffectStatus.BYPASSED
            return true
        }
        val session = player.audioSessionId
        if (session <= 0) {
            releaseSmartVolume()
            _smartVolumeStatus.value = AudioEffectStatus.PENDING
            return false
        }
        if (Build.VERSION.SDK_INT < 28) {
            releaseSmartVolume()
            _smartVolumeStatus.value = AudioEffectStatus.UNSUPPORTED
            return false
        }
        return runCatching {
            if (dynamicsSession != session || dynamicsMode != mode || dynamicsEffect == null) {
                releaseSmartVolume()
                val ratio = when (mode) {
                    SmartVolumeMode.NIGHT -> 2.5f
                    SmartVolumeMode.DIALOGUE_BOOST -> 3.0f
                    SmartVolumeMode.AUTO_LEVEL -> 4.0f
                    SmartVolumeMode.OFF -> 1.0f
                }
                val threshold = when (mode) {
                    SmartVolumeMode.NIGHT -> -18f
                    SmartVolumeMode.DIALOGUE_BOOST -> -23f
                    SmartVolumeMode.AUTO_LEVEL -> -28f
                    SmartVolumeMode.OFF -> -12f
                }
                // One compressor band plus linked peak limiter, avoiding multiple
                // FFT bands and large lookahead allocations on ARM32 Android TV.
                val compressor = DynamicsProcessing.Mbc(true, true, 1)
                compressor.setBand(0, DynamicsProcessing.MbcBand(
                    true, 20_000f, 12f, 230f, ratio, threshold, 4f, -80f, 1f,
                    0f, if (mode == SmartVolumeMode.AUTO_LEVEL) 1.5f else 0f
                ))
                val limiter = DynamicsProcessing.Limiter(
                    true, true, 0, 3f, 90f, 10f, -1.5f, 0f
                )
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
                    2, false, 0, true, 1, false, 0, true
                ).setMbcAllChannelsTo(compressor)
                    .setLimiterAllChannelsTo(limiter)
                    .build()
                val fx = DynamicsProcessing(0, session, config)
                if (!fx.hasControl()) {
                    fx.release()
                    throw IllegalStateException("DynamicsProcessing has no audio control")
                }
                dynamicsEffect = fx
                dynamicsSession = session
                dynamicsMode = mode
            }
            val effect = requireNotNull(dynamicsEffect)
            effect.enabled = true
            val confirmed = effect.enabled && effect.hasControl()
            _smartVolumeStatus.value = if (confirmed) AudioEffectStatus.APPLIED
                else AudioEffectStatus.UNSUPPORTED
            confirmed
        }.getOrElse { error ->
            Log.w("BousyAudioEq", "Smart Volume unavailable on this audio session", error)
            releaseSmartVolume()
            _smartVolumeStatus.value = AudioEffectStatus.UNSUPPORTED
            false
        }
    }
    private var audioFx: Equalizer? = null
    private var audioFxSessionId = -1

    private fun releaseAudioFx() {
        runCatching { audioFx?.let { it.enabled = false } }
        runCatching { audioFx?.release() }
        audioFx = null
        audioFxSessionId = -1
    }

    override fun applyAudioEqualizer(preset: AudioEqualizerPreset): Boolean {
        if (isReleased.get()) return false
        requestedEqPreset = preset
        if (!preset.isEnabled) {
            releaseAudioFx()
            _audioEqStatus.value = AudioEffectStatus.BYPASSED
            return true
        }
        val session = player.audioSessionId
        // Never create a global output-mix effect on session 0.
        if (session <= 0) {
            releaseAudioFx()
            _audioEqStatus.value = AudioEffectStatus.PENDING
            return false
        }
        return runCatching {
            if (audioFx == null || audioFxSessionId != session) {
                releaseAudioFx()
                val created = Equalizer(0, session)
                if (!created.hasControl()) {
                    created.release()
                    _audioEqStatus.value = AudioEffectStatus.UNSUPPORTED
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
                    band, safeAudioFxBandMillibels(activeAudioProfile.copy(preset = preset),
                        frequencyHz, range[0].toInt(), range[1].toInt())
                )
            }
            fx.enabled = true
            val confirmed = fx.enabled && fx.hasControl()
            _audioEqStatus.value = if (confirmed) AudioEffectStatus.APPLIED
                else AudioEffectStatus.UNSUPPORTED
            confirmed
        }.getOrElse { error ->
            Log.w("BousyAudioEq", "Android AudioFX unavailable for this output", error)
            releaseAudioFx()
            _audioEqStatus.value = AudioEffectStatus.UNSUPPORTED
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
            "audioOutputMonitor" to if (outputMonitorRegistered) "registered" else "unavailable",
            "audioTopologyChanges" to audioOutputTopologyEvents.toString(),
            "audioEffectRefreshAttempts" to audioOutputRefreshAttempts.toString(),
            "audioEqualizerStatus" to _audioEqStatus.value.name,
            "smartVolumeStatus" to _smartVolumeStatus.value.name,
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
            if (isReleased.get()) return
            // Media3 can replace its AudioTrack/session across format or route changes.
            releaseAudioFx()
            releaseSmartVolume()
            if (player.mediaItemCount == 0) {
                markEffectsWaitingForOutput()
            } else {
                applyAudioProcessing(activeAudioProfile)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> _playbackState.value = EnginePlaybackState.Buffering
                Player.STATE_READY -> {
                    _playbackState.value = EnginePlaybackState.Ready
                    // On some devices a reusable session ID survives stop/prepare,
                    // so no new session callback is emitted for the next item.
                    if (!isReleased.get() && player.mediaItemCount > 0 &&
                        shouldRetryPendingAudioEffectsOnReady(
                            activeAudioProfile, _audioEqStatus.value,
                            _smartVolumeStatus.value, player.audioSessionId
                        )
                    ) applyAudioProcessing(activeAudioProfile)
                }
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
        outputMonitorRegistered = outputTopologyMonitor?.start() == true
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
        if (isReleased.get()) return
        _firstFrameRendered.value = false
        // Retain the user's preference, not the disposed effect instance.
        // Recreating it while a stopped decoder has no output is misleading.
        player.stop()
        player.clearMediaItems()
        releaseAudioFx()
        releaseSmartVolume()
        markEffectsWaitingForOutput()
        _playbackState.value = EnginePlaybackState.Idle
    }

    override fun release() {
        // Exactly one teardown may touch the underlying native audio session.
        if (!isReleased.compareAndSet(false, true)) return
        // Unregister first, before stopping effects and native output.
        runCatching { outputTopologyMonitor?.close() }
            .onFailure { Log.w("BousyAudioEq", "Audio route monitor cleanup failed", it) }
        outputMonitorRegistered = false
        releaseAudioFx()
        releaseSmartVolume()
        _smartVolumeStatus.value = AudioEffectStatus.BYPASSED
        _audioEqStatus.value = AudioEffectStatus.BYPASSED
        runCatching { player.removeListener(listener) }
            .onFailure { Log.w("BousyAudioEq", "Media3 listener cleanup failed", it) }
        runCatching { player.removeAnalyticsListener(analyticsListener) }
            .onFailure { Log.w("BousyAudioEq", "Media3 analytics cleanup failed", it) }
        runCatching { player.release() }
            .onFailure { Log.e("BousyAudioEq", "Media3 player release failed", it) }
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
