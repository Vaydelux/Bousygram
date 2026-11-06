package com.vaydelux.bousygram.core.media

import android.content.Context
import android.util.Log
import android.view.Surface
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Locale

/**
 * Universal media player engine wrapping libmpv (dev.jdtech.mpv:libmpv).
 *
 * Provides native hardware/software decoding across all media formats (MKV, AVI, FLV, WMV,
 * MP4, TS), advanced audio formats (AC-3, E-AC-3, DTS, TrueHD, Opus, FLAC), and complex
 * styled subtitles (SSA/ASS, PGS, SRT).
 *
 * Implements adaptive demuxer buffer management tuned for TDLib progressive loopback streaming.
 */
/** Native mpv timeline values are seconds; reject invalid/unbounded timestamps. */
internal fun nativeMpvTimelineMs(seconds: Double): Long? {
    if (!seconds.isFinite() || seconds < 0.0) return null
    val millis = seconds * 1_000.0
    return if (millis.isFinite() && millis <= Long.MAX_VALUE.toDouble()) millis.toLong() else null
}

/** Seek only to a real nonnegative timestamp, bounded by a known container duration. */
internal fun boundedMpvSeekMs(requestedMs: Long, durationMs: Long): Long =
    if (durationMs > 0L) requestedMs.coerceIn(0L, durationMs)
    else requestedMs.coerceAtLeast(0L)

/** Clamp cache-ahead indicators without wrapping signed Long values. */
internal fun nativeMpvBufferedPositionMs(positionMs: Long, aheadMs: Long): Long =
    if (aheadMs > Long.MAX_VALUE - positionMs.coerceAtLeast(0L)) Long.MAX_VALUE
    else positionMs.coerceAtLeast(0L) + aheadMs.coerceAtLeast(0L)

/** Native end-file notifications also occur on explicit stop or file replacement. */
internal fun shouldReportNativeMpvEnded(nativeEofReached: Boolean): Boolean =
    nativeEofReached

/** Terminal notifications during Stop or replacement are not the active file's EOF. */
internal fun shouldProcessNativeMpvTerminalEvent(
    sourceRequested: Boolean,
    fileLoaded: Boolean
): Boolean = sourceRequested && fileLoaded

/** An unexpected end while READY is a stream failure, not an episode completion. */
internal fun shouldRetryUnexpectedMpvEnd(
    nativeEofReached: Boolean, playbackState: EnginePlaybackState
): Boolean = !nativeEofReached && playbackState is EnginePlaybackState.Ready

class MpvPlayerEngine(
    context: Context,
    initialIsCellular: Boolean = false
) : PlayerEngine {

    companion object {
        private const val TAG = "MpvPlayerEngine"
    }

    private val appContext: Context = context.applicationContext ?: context
    private val mpv: MPVLib = MPVLib.create(context)
        ?: throw IllegalStateException("Failed to create libmpv instance from native context")
    private val isDestroyed = AtomicBoolean(false)
    private val _audioEqStatus = MutableStateFlow(AudioEffectStatus.BYPASSED)
    override val audioEqualizerStatus: StateFlow<AudioEffectStatus> = _audioEqStatus.asStateFlow()
    private var customEqualizerInstalled = false
    private var appliedEqPreset = AudioEqualizerPreset.ORIGINAL
    private var activeAudioProfile = AudioProcessingProfile()
    private val _smartVolumeStatus = MutableStateFlow(AudioEffectStatus.BYPASSED)
    override val smartVolumeStatus: StateFlow<AudioEffectStatus> = _smartVolumeStatus.asStateFlow()
    private var smartFilterInstalled = false
    private var appliedSmartMode = SmartVolumeMode.OFF

    override fun applyAudioProcessing(profile: AudioProcessingProfile): Boolean {
        if (isDestroyed.get()) return false
        // A custom curve may change even when the preset enum remains CUSTOM.
        if (profile.preset == AudioEqualizerPreset.CUSTOM &&
            activeAudioProfile.sanitizedGains != profile.sanitizedGains
        ) appliedEqPreset = AudioEqualizerPreset.ORIGINAL
        activeAudioProfile = profile.copy(customGainsDb = profile.sanitizedGains)
        val eqApplied = applyAudioEqualizer(profile.preset)
        val volumeApplied = applySmartVolume(profile.smartVolume)
        return eqApplied && volumeApplied
    }

    private fun verifySmartFilter(): FilterVerification =
        runCatching { verifyNamedMpvAudioFilter(mpv.getPropertyString("af"), "bousy_smart") }
            .getOrDefault(FilterVerification.UNVERIFIABLE)

    private fun applySmartVolume(mode: SmartVolumeMode): Boolean {
        if (isDestroyed.get()) return false
        if (mode == appliedSmartMode &&
            (if (mode == SmartVolumeMode.OFF) verifySmartFilter() == FilterVerification.ABSENT
             else verifySmartFilter() == FilterVerification.PRESENT)) {
            _smartVolumeStatus.value = if (mode == SmartVolumeMode.OFF) AudioEffectStatus.BYPASSED
                else AudioEffectStatus.APPLIED
            return true
        }
        val currentSmart = verifySmartFilter()
        // Confirmed absence wins over a stale local flag (e.g. after a decoder reset).
        if (currentSmart == FilterVerification.ABSENT) smartFilterInstalled = false
        if (mayNeedMpvFilterRemoval(currentSmart, smartFilterInstalled)) {
            val removed = runCatching {
                mpv.command(arrayOf("af", "remove", "@bousy_smart"))
                verifySmartFilter() == FilterVerification.ABSENT
            }.getOrDefault(false)
            if (!removed) {
                _smartVolumeStatus.value = AudioEffectStatus.UNSUPPORTED
                return false
            }
            smartFilterInstalled = false
        }
        if (mode == SmartVolumeMode.OFF) {
            appliedSmartMode = SmartVolumeMode.OFF
            _smartVolumeStatus.value = AudioEffectStatus.BYPASSED
            return true
        }
        // Adaptive normalization uses history and can be expensive on ARM32 TVs.
        // Never silently select the CPU-intensive mode on a 32-bit process.
        if (mode == SmartVolumeMode.AUTO_LEVEL && !android.os.Process.is64Bit()) {
            _smartVolumeStatus.value = AudioEffectStatus.UNSUPPORTED
            return false
        }
        return runCatching {
            val filters = checkNotNull(smartVolumeMpvGraph(
                mode, is32BitProcess = !android.os.Process.is64Bit()
            ))
            // Mark uncertainty before the call: the filter may survive a thrown exception.
            smartFilterInstalled = true
            mpv.command(arrayOf("af", "add", "@bousy_smart:lavfi=[$filters]"))
            val result = verifySmartFilter()
            smartFilterInstalled = result != FilterVerification.ABSENT
            if (result == FilterVerification.PRESENT) {
                appliedSmartMode = mode
                _smartVolumeStatus.value = AudioEffectStatus.APPLIED
                true
            } else {
                _smartVolumeStatus.value = AudioEffectStatus.UNSUPPORTED
                false
            }
        }.getOrElse { error ->
            Log.w(TAG, "Smart Volume native filter unsupported", error)
            _smartVolumeStatus.value = AudioEffectStatus.UNSUPPORTED
            false
        }
    }

    private fun verifyEq(): FilterVerification =
        runCatching { verifyNamedMpvAudioFilter(mpv.getPropertyString("af"), "bousy_eq") }
            .getOrDefault(FilterVerification.UNVERIFIABLE)

    override fun applyAudioEqualizer(preset: AudioEqualizerPreset): Boolean {
        if (isDestroyed.get()) return false
        if (preset == appliedEqPreset &&
            (if (preset.isEnabled) verifyEq() == FilterVerification.PRESENT
             else verifyEq() == FilterVerification.ABSENT)) {
            _audioEqStatus.value = if (preset.isEnabled) AudioEffectStatus.APPLIED
                else AudioEffectStatus.BYPASSED
            return true
        }

        val current = verifyEq()
        if (current == FilterVerification.ABSENT) customEqualizerInstalled = false
        if (mayNeedMpvFilterRemoval(current, customEqualizerInstalled)) {
            val removed = runCatching {
                mpv.command(arrayOf("af", "remove", "@bousy_eq"))
                verifyEq() == FilterVerification.ABSENT
            }.getOrDefault(false)
            if (!removed) {
                // Never claim Original has bypassed processing when removal is unknown.
                _audioEqStatus.value = AudioEffectStatus.UNSUPPORTED
                Log.w(TAG, "Native audio filter removal could not be verified")
                return false
            }
            customEqualizerInstalled = false
        }

        if (!preset.isEnabled) {
            appliedEqPreset = AudioEqualizerPreset.ORIGINAL
            _audioEqStatus.value = AudioEffectStatus.BYPASSED
            return true
        }

        return runCatching {
            val filters = listOf(90, 400, 2_000, 8_000).joinToString(",") { hz ->
                val gain = String.format(Locale.US, "%.1f", activeAudioProfile.gainDbAt(hz.toFloat()))
                "equalizer=f=$hz:t=q:w=1:g=$gain"
            }
            val preamp = String.format(Locale.US, "%.1f", activeAudioProfile.preampDb)
            // A native add can partially succeed and then raise; remember that
            // until the named filter is verified absent on a later update.
            customEqualizerInstalled = true
            mpv.command(arrayOf("af", "add", "@bousy_eq:lavfi=[volume=${preamp}dB,$filters]"))
            val installed = verifyEq()
            customEqualizerInstalled = installed != FilterVerification.ABSENT
            if (installed == FilterVerification.PRESENT) {
                appliedEqPreset = preset
                _audioEqStatus.value = AudioEffectStatus.APPLIED
                true
            } else {
                _audioEqStatus.value = AudioEffectStatus.UNSUPPORTED
                false
            }
        }.getOrElse { failure ->
            _audioEqStatus.value = AudioEffectStatus.UNSUPPORTED
            Log.w(TAG, "MPV EQ unavailable for current output", failure)
            false
        }
    }
    // Ignore queued property/end notifications after owner Stop or while a
    // replacement loadfile is waiting for its own FILE_LOADED event.
    @Volatile private var nativeSourceRequested = false
    @Volatile private var nativeFileLoaded = false
    @Volatile private var pausedForCache = false
    // END_FILE also occurs for explicit stop and replacement; it is not EOF proof.
    @Volatile private var naturalEofObserved = false
    private var isCellular = initialIsCellular
    private var bufferConfig = PlayerBufferConfig.resolveMpvConfig(context, initialIsCellular)

    override val bufferConfigDescription: String get() = bufferConfig.description
    val localStreamingServerPort: Int get() = com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer.port

    private val _playbackState = MutableStateFlow<EnginePlaybackState>(EnginePlaybackState.Idle)
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: Long get() = _currentPositionMs.value

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: Long get() = _durationMs.value
    // libmpv's demuxer-derived file duration can be trusted even for MKV/TS
    // bigger than 150 MB, but only while it confirms byte-seek support.
    override val seekableContainerDuration: Boolean
        get() = _durationMs.value > 0L && canSeek
    override val canSeek: Boolean
        get() = runCatching { mpv.getPropertyBoolean("seekable") == true }.getOrDefault(false)

    private fun acceptNativeDuration(seconds: Double) {
        if (!seconds.isFinite() || seconds < 1.0 || seconds > 7.0 * 24 * 60 * 60) return
        val candidate = (seconds * 1000.0).toLong()
        if (candidate > _durationMs.value) _durationMs.value = candidate
    }

    private fun readNativeDurationAfterLoad() {
        // Some TS demuxers publish duration at FILE_LOADED rather than via the
        // asynchronous property-change event. Read the current property as well.
        runCatching { mpv.getPropertyString("duration/full") }
            .getOrNull()?.toDoubleOrNull()?.let(::acceptNativeDuration)
    }

    private val _bufferedPositionMs = MutableStateFlow(0L)
    override val bufferedPositionMs: Long get() = _bufferedPositionMs.value

    override val engineName: String = "mpv"

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: Boolean get() = _isPlaying.value

    private val _playWhenReady = MutableStateFlow(true)
    override val playWhenReady: Boolean get() = _playWhenReady.value

    private val _activeDecoder = MutableStateFlow("Auto")
    override val activeDecoder: String get() = _activeDecoder.value

    private val _videoCodec = MutableStateFlow("Unknown")
    private val _audioCodec = MutableStateFlow("Unknown")

    override val videoDecoderName: String
        get() {
            val codec = _videoCodec.value.ifBlank { "Unknown" }
            val hw = _activeDecoder.value
            return if (codec != "Unknown") "$codec ($hw)" else hw
        }

    override val audioDecoderName: String
        get() = _audioCodec.value.ifBlank { "Unknown" }

    private val _tracks = MutableStateFlow<List<MediaTrackInfo>>(emptyList())
    override val tracks: StateFlow<List<MediaTrackInfo>> = _tracks.asStateFlow()

    private val _videoDimensions = MutableStateFlow<Pair<Int, Int>>(Pair(0, 0))
    override val videoDimensions: StateFlow<Pair<Int, Int>> = _videoDimensions.asStateFlow()

    override val renderedOutputBufferCount: Int
        get() = (runCatching { mpv.getPropertyInt("frame-count") }.getOrNull() ?: 0)
    override val droppedBufferCount: Int
        get() = (runCatching { mpv.getPropertyInt("frame-drop-count") }.getOrNull() ?: 0)
    override val isVideoTrackSelected: Boolean
        get() = runCatching { mpv.getPropertyString("vid") }.getOrNull()?.let { it != "no" && it.isNotBlank() } ?: true
    override val diagnosticDetails: Map<String, String>
        get() = mapOf(
            "vo" to (runCatching { mpv.getPropertyString("vo") }.getOrNull() ?: "unknown"),
            "gpu-context" to (runCatching { mpv.getPropertyString("gpu-context") }.getOrNull() ?: "unknown"),
            "hwdec" to (runCatching { mpv.getPropertyString("hwdec") }.getOrNull() ?: "unknown"),
            "hwdec-current" to (runCatching { mpv.getPropertyString("hwdec-current") }.getOrNull() ?: "unknown"),
            "vo-configured" to (runCatching { mpv.getPropertyBoolean("vo-configured") }.getOrNull()?.toString() ?: "unknown"),
            "dwidth" to (runCatching { mpv.getPropertyInt("dwidth") }.getOrNull()?.toString() ?: "0"),
            "dheight" to (runCatching { mpv.getPropertyInt("dheight") }.getOrNull()?.toString() ?: "0"),
            "video-params" to (runCatching { mpv.getPropertyString("video-params/pixelformat") }.getOrNull() ?: "unknown")
        )

    fun setHwDecAndVoMode(hwdec: String, vo: String) {
        runCatching {
            mpv.setPropertyString("vo", vo)
            mpv.setPropertyString("hwdec", hwdec)
            Log.i(TAG, "Switched MPV hwdec=$hwdec vo=$vo")
        }
    }

    private var currentResizeMode: Int = 0

    private val eventObserver = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) {
            if (isDestroyed.get() || !nativeSourceRequested) return
            when (property) {
                "track-list" -> refreshTracks()
            }
        }

        override fun eventProperty(property: String, value: Long) {
            if (isDestroyed.get() || !nativeSourceRequested) return
            when (property) {
                "dwidth", "video-params/w" -> {
                    if (value > 0L) {
                        _videoDimensions.value = _videoDimensions.value.copy(first = value.toInt())
                    }
                }
                "dheight", "video-params/h" -> {
                    if (value > 0L) {
                        _videoDimensions.value = _videoDimensions.value.copy(second = value.toInt())
                    }
                }
            }
        }

        override fun eventProperty(property: String, value: Double) {
            if (isDestroyed.get() || !nativeSourceRequested) return
            when (property) {
                "time-pos" -> nativeMpvTimelineMs(value)?.let { posMs ->
                    _currentPositionMs.value = posMs
                }
                "duration", "duration/full" -> acceptNativeDuration(value)
                "demuxer-cache-time" -> nativeMpvTimelineMs(value)?.let { cacheAheadMs ->
                    _bufferedPositionMs.value = nativeMpvBufferedPositionMs(
                        _currentPositionMs.value, cacheAheadMs
                    )
                }
            }
        }

        override fun eventProperty(property: String, value: Boolean) {
            if (isDestroyed.get() || !nativeSourceRequested) return
            when (property) {
                "pause" -> {
                    // Native output stops during cache stalls without changing user intent.
                    _isPlaying.value = _playWhenReady.value && !value && !pausedForCache &&
                        _playbackState.value is EnginePlaybackState.Ready
                }
                "paused-for-cache" -> {
                    pausedForCache = value
                    if (value) {
                        _isPlaying.value = false
                        _playbackState.value = EnginePlaybackState.Buffering
                    } else if (_playbackState.value is EnginePlaybackState.Buffering) {
                        _playbackState.value = EnginePlaybackState.Ready
                        // mpv's internal cache pause is not a user-requested pause.
                        // Unblock playback once bytes return, unless the user paused explicitly.
                        if (_playWhenReady.value) {
                            runCatching { mpv.setPropertyBoolean("pause", false) }
                                .onFailure { Log.w(TAG, "Unable to resume after cache fill", it) }
                        }
                    }
                }
                "eof-reached" -> {
                    if (value && shouldProcessNativeMpvTerminalEvent(
                        nativeSourceRequested, nativeFileLoaded
                    )) {
                        naturalEofObserved = true
                        _playbackState.value = EnginePlaybackState.Ended
                        _isPlaying.value = false
                        _playWhenReady.value = false
                    }
                }
            }
        }

        override fun eventProperty(property: String, value: String) {
            if (isDestroyed.get() || !nativeSourceRequested) return
            when (property) {
                "hwdec-current" -> {
                    if (value.isNotBlank() && value != "no") {
                        _activeDecoder.value = "Hardware ($value)"
                    } else {
                        _activeDecoder.value = "Software (FFmpeg)"
                    }
                }
                "video-codec" -> {
                    if (value.isNotBlank()) _videoCodec.value = value
                }
                "audio-codec" -> {
                    if (value.isNotBlank()) _audioCodec.value = value
                }
            }
        }

        override fun event(eventId: Int) {
            if (isDestroyed.get() || !nativeSourceRequested) return
            when (eventId) {
                MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
                    nativeFileLoaded = false
                    naturalEofObserved = false
                    _playbackState.value = EnginePlaybackState.Buffering
                }
                MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                    nativeFileLoaded = true
                    _playbackState.value = EnginePlaybackState.Ready
                    // A newly loaded MKV must not inherit a paused state from the old file.
                    // Respect an explicit user pause if one happened while buffering.
                    if (_playWhenReady.value) {
                        runCatching { mpv.setPropertyBoolean("pause", false) }
                            .onFailure { Log.w(TAG, "Unable to start loaded media", it) }
                    }
                    _isPlaying.value = _playWhenReady.value && !pausedForCache
                    readNativeDurationAfterLoad()
                    refreshTracks()
                    setResizeMode(currentResizeMode)
                }
                MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                    if (!shouldProcessNativeMpvTerminalEvent(
                        nativeSourceRequested, nativeFileLoaded
                    )) return // prior stop/replacement, not this loaded source
                    _isPlaying.value = false
                    // STOP and loadfile also emit END_FILE. Only an observed
                    // native EOF can trigger auto-next or terminal end handling.
                    if (shouldReportNativeMpvEnded(naturalEofObserved)) {
                        _playbackState.value = EnginePlaybackState.Ended
                    } else if (shouldRetryUnexpectedMpvEnd(naturalEofObserved, _playbackState.value)) {
                        // Unexpected native termination must not leave a
                        // permanently READY but silent/paused playback.
                        _playbackState.value = EnginePlaybackState.Error(
                            "The native decoder ended before the stream finished"
                        )
                    }
                }
                MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                    if (!nativeFileLoaded) return
                    _playbackState.value = EnginePlaybackState.Ready
                    // A native seek/cache restart may leave mpv paused despite
                    // a still-active user Play intent. Never override Pause.
                    if (_playWhenReady.value && !pausedForCache) {
                        runCatching { mpv.setPropertyBoolean("pause", false) }
                            .onFailure { Log.w(TAG, "Resume after native seek unavailable", it) }
                    }
                    _isPlaying.value = _playWhenReady.value && !pausedForCache
                    readNativeDurationAfterLoad()
                }
            }
        }
    }

    init {
        try {
            val hwdecPreference = com.vaydelux.bousygram.data.store.EnterprisePreferences.mpvHwDecMode()
            when (hwdecPreference) {
                "mediacodec_embed" -> {
                    mpv.setOptionString("vo", "mediacodec_embed")
                    mpv.setOptionString("hwdec", "mediacodec")
                }
                "software" -> {
                    mpv.setOptionString("vo", "gpu")
                    mpv.setOptionString("gpu-context", "android")
                    mpv.setOptionString("hwdec", "no")
                }
                else -> {
                    // Safe default for Mali-G52: vo=gpu with mediacodec-copy
                    mpv.setOptionString("vo", "gpu")
                    mpv.setOptionString("gpu-context", "android")
                    mpv.setOptionString("hwdec", "mediacodec-copy")
                }
            }
            mpv.setOptionString("hwdec-codecs", "all")
            mpv.setOptionString("ao", "audiotrack,opensles")
            mpv.setOptionString("tls-verify", "yes")
            mpv.setOptionString("audio-pitch-correction", "yes")

            // On Android TV, display-resample drops frames without Choreographer VSync; use audio sync
            mpv.setOptionString("video-sync", "audio")

            // Demuxer adaptive buffer configuration
            mpv.setOptionString("cache", bufferConfig.cacheEnabled)
            mpv.setOptionString("demuxer-max-bytes", bufferConfig.demuxerMaxBytes.toString())
            mpv.setOptionString("demuxer-max-back-bytes", bufferConfig.demuxerMaxBackBytes.toString())
            mpv.setOptionString("demuxer-readahead-secs", bufferConfig.demuxerReadaheadSecs.toString())
            mpv.setOptionString("cache-secs", bufferConfig.demuxerReadaheadSecs.toString())

            mpv.init()

            mpv.addObserver(eventObserver)
            mpv.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty("duration/full", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty("demuxer-cache-time", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty("hwdec-current", MPVLib.MpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty("track-list", MPVLib.MpvFormat.MPV_FORMAT_NODE)
            mpv.observeProperty("dwidth", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            mpv.observeProperty("dheight", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            mpv.observeProperty("video-params/w", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            mpv.observeProperty("video-params/h", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            mpv.observeProperty("video-codec", MPVLib.MpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty("audio-codec", MPVLib.MpvFormat.MPV_FORMAT_STRING)
            Log.i(TAG, "MpvPlayerEngine initialized successfully")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize MpvPlayerEngine", e)
            isDestroyed.set(true)
            runCatching { mpv.removeObserver(eventObserver) }
            runCatching { mpv.detachSurface() }
            runCatching { mpv.destroy() }
            throw IllegalStateException("Failed to initialize the libmpv playback engine", e)
        }
    }

    override fun attachSurface(surface: Surface) {
        if (isDestroyed.get()) return
        try {
            mpv.attachSurface(surface)
        } catch (e: Throwable) {
            Log.e(TAG, "Error attaching surface to mpv", e)
        }
    }

    override fun detachSurface() {
        if (isDestroyed.get()) return
        try {
            mpv.detachSurface()
        } catch (e: Throwable) {
            Log.e(TAG, "Error detaching surface from mpv", e)
        }
    }

    override fun prepare(streamUrl: String) {
        if (isDestroyed.get()) return
        nativeSourceRequested = true
        nativeFileLoaded = false
        try {
            naturalEofObserved = false
            _currentPositionMs.value = 0L
            _durationMs.value = 0L
            _bufferedPositionMs.value = 0L
            _tracks.value = emptyList()
            _videoDimensions.value = 0 to 0
            _videoCodec.value = "Unknown"
            _audioCodec.value = "Unknown"
            pausedForCache = false
            _isPlaying.value = false
            _playbackState.value = EnginePlaybackState.Buffering
            // Re-preparing after a proxy rebind, network handoff or rebuffer must
            // not silently override a user pause. New sessions explicitly call play().
            mpv.command(arrayOf("loadfile", streamUrl))
        } catch (e: Throwable) {
            nativeSourceRequested = false
            nativeFileLoaded = false
            Log.e(TAG, "MPV failed to load the active local stream (${e.javaClass.simpleName})")
            _playbackState.value = EnginePlaybackState.Error("The playback engine could not load this stream", e)
        }
    }

    override fun play() {
        if (isDestroyed.get()) return
        try {
            _playWhenReady.value = true
            mpv.setPropertyBoolean("pause", false)
            _isPlaying.value = !pausedForCache && _playbackState.value is EnginePlaybackState.Ready
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting pause false", e)
        }
    }

    override fun pause() {
        if (isDestroyed.get()) return
        try {
            _playWhenReady.value = false
            mpv.setPropertyBoolean("pause", true)
            _isPlaying.value = false
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting pause true", e)
        }
    }

    override fun seekTo(positionMs: Long) {
        if (isDestroyed.get()) return
        // Never publish a fabricated timestamp for an unseekable TS or MKV.
        // The time-pos observer is the sole authority for a completed seek.
        if (!canSeek) {
            Log.w(TAG, "Seek ignored until the native demuxer publishes a seek map")
            return
        }
        try {
            val seconds = boundedMpvSeekMs(positionMs, durationMs) / 1000.0
            mpv.command(arrayOf("seek", seconds.toString(), "absolute"))
        } catch (e: Throwable) {
            Log.e(TAG, "Error seeking mpv to $positionMs ms", e)
        }
    }

    /** Text subtitle styling only; embedded bitmap subtitles cannot change color. */
    fun applySubtitleAppearance(appearance: SubtitleAppearance) {
        if (isDestroyed.get()) return
        val preset = appearance.preset
        // Apply each independent property defensively: vendor libmpv builds may
        // support different subtitle style properties. None affects playback.
        runCatching { mpv.setPropertyString("sub-color", preset.mpvTextRgb) }
            .onFailure { Log.w(TAG, "Subtitle foreground setting unavailable", it) }
        runCatching { mpv.setPropertyString("sub-border-color", preset.mpvEdgeRgb) }
            .onFailure { Log.w(TAG, "Subtitle outline setting unavailable", it) }
        runCatching { mpv.setPropertyDouble("sub-scale", appearance.mpvScale) }
            .onFailure { Log.w(TAG, "Subtitle scale setting unavailable", it) }
        runCatching { mpv.setPropertyInt("sub-border-size", 2) }
            .onFailure { Log.w(TAG, "Subtitle outline size unavailable", it) }
        runCatching { mpv.setPropertyString("sub-back-color",
            if (preset == SubtitleColorPreset.BLACK) "#FFFFFF" else "#000000") }
            .onFailure { Log.w(TAG, "Subtitle background setting unavailable", it) }
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (isDestroyed.get()) return
        try {
            mpv.setPropertyDouble("speed", speed.toDouble())
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting playback speed", e)
        }
    }

    override fun setResizeMode(mode: Int) {
        if (isDestroyed.get()) return
        currentResizeMode = mode
        try {
            when (mode) {
                // 4 = AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> Crop & Fill
                4 -> {
                    mpv.setPropertyDouble("panscan", 1.0)
                    mpv.setPropertyBoolean("keepaspect", true)
                }
                // 3 = AspectRatioFrameLayout.RESIZE_MODE_FILL -> Stretch to Fill
                3 -> {
                    mpv.setPropertyDouble("panscan", 0.0)
                    mpv.setPropertyBoolean("keepaspect", false)
                }
                // 0 = AspectRatioFrameLayout.RESIZE_MODE_FIT -> Letterbox Fit
                else -> {
                    mpv.setPropertyDouble("panscan", 0.0)
                    mpv.setPropertyBoolean("keepaspect", true)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting resize mode", e)
        }
    }

    override fun selectTrack(track: MediaTrackInfo) {
        if (isDestroyed.get()) return
        try {
            when (track.type) {
                MediaTrackInfo.TrackType.SUBTITLE -> mpv.setPropertyInt("sid", track.id)
                MediaTrackInfo.TrackType.AUDIO -> mpv.setPropertyInt("aid", track.id)
                MediaTrackInfo.TrackType.VIDEO -> mpv.setPropertyInt("vid", track.id)
            }
            refreshTracks()
        } catch (e: Throwable) {
            Log.e(TAG, "Error selecting track ${track.id}", e)
        }
    }

    override fun deselectTrack(type: MediaTrackInfo.TrackType) {
        if (isDestroyed.get()) return
        try {
            when (type) {
                MediaTrackInfo.TrackType.SUBTITLE -> mpv.setPropertyString("sid", "no")
                MediaTrackInfo.TrackType.AUDIO -> mpv.setPropertyString("aid", "no")
                MediaTrackInfo.TrackType.VIDEO -> mpv.setPropertyString("vid", "no")
            }
            refreshTracks()
        } catch (e: Throwable) {
            Log.e(TAG, "Error deselecting track $type", e)
        }
    }

    override fun updateNetworkState(isCellular: Boolean) {
        if (isDestroyed.get()) return
        this.isCellular = isCellular
        try {
            val newConfig = PlayerBufferConfig.resolveMpvConfig(appContext, isCellular)
            this.bufferConfig = newConfig
            mpv.setPropertyString("cache", newConfig.cacheEnabled)
            mpv.setPropertyString("demuxer-max-bytes", newConfig.demuxerMaxBytes.toString())
            mpv.setPropertyString("demuxer-max-back-bytes", newConfig.demuxerMaxBackBytes.toString())
            mpv.setPropertyString("demuxer-readahead-secs", newConfig.demuxerReadaheadSecs.toString())
            mpv.setPropertyString("cache-secs", newConfig.demuxerReadaheadSecs.toString())
            Log.i(TAG, "Updated mpv buffer config: ${newConfig.description}")
        } catch (e: Throwable) {
            Log.e(TAG, "Error updating mpv network demuxer buffer", e)
        }
    }

    override fun stop() {
        if (isDestroyed.get()) return
        nativeSourceRequested = false
        nativeFileLoaded = false
        naturalEofObserved = false
        _playWhenReady.value = false
        try {
            mpv.command(arrayOf("stop"))
            _playbackState.value = EnginePlaybackState.Idle
            _isPlaying.value = false
            _tracks.value = emptyList()
            _videoDimensions.value = 0 to 0
        } catch (e: Throwable) {
            Log.e(TAG, "Error stopping mpv", e)
        }
    }

    override fun release() {
        if (isDestroyed.compareAndSet(false, true)) {
            nativeSourceRequested = false
            nativeFileLoaded = false
            // Each cleanup must run even if another native cleanup operation fails.
            runCatching { mpv.removeObserver(eventObserver) }
            runCatching { mpv.detachSurface() }
            runCatching { mpv.destroy() }.onFailure { Log.e(TAG, "Error destroying mpv", it) }
            customEqualizerInstalled = false
            _audioEqStatus.value = AudioEffectStatus.BYPASSED
            _smartVolumeStatus.value = AudioEffectStatus.BYPASSED
            _playbackState.value = EnginePlaybackState.Idle
            _isPlaying.value = false
            _playWhenReady.value = false
            _tracks.value = emptyList()
            _videoDimensions.value = 0 to 0
            Log.i(TAG, "MpvPlayerEngine released")
        }
    }

    private fun refreshTracks() {
        if (isDestroyed.get()) return
        try {
            val count = mpv.getPropertyInt("track-list/count") ?: 0
            val list = mutableListOf<MediaTrackInfo>()
            for (i in 0 until PlaybackTrackBudget.boundedCount(count)) {
                val typeStr = mpv.getPropertyString("track-list/$i/type") ?: continue
                val id = mpv.getPropertyInt("track-list/$i/id") ?: (i + 1)
                val title = mpv.getPropertyString("track-list/$i/title")
                val lang = PlaybackTrackBudget.language(mpv.getPropertyString("track-list/$i/lang"))
                val selected = mpv.getPropertyBoolean("track-list/$i/selected") ?: false

                val trackType = when (typeStr) {
                    "sub" -> MediaTrackInfo.TrackType.SUBTITLE
                    "audio" -> MediaTrackInfo.TrackType.AUDIO
                    "video" -> MediaTrackInfo.TrackType.VIDEO
                    else -> continue
                }

                val displayName = PlaybackTrackBudget.label(
                    title, if (lang != null) "Language: $lang" else "Track #$id"
                )

                list.add(
                    MediaTrackInfo(
                        id = id,
                        type = trackType,
                        name = displayName,
                        language = lang,
                        isSelected = selected
                    )
                )
            }
            _tracks.value = list
            runCatching { mpv.getPropertyString("video-codec") }.getOrNull()?.takeIf { it.isNotBlank() }?.let { _videoCodec.value = it }
            runCatching { mpv.getPropertyString("audio-codec") }.getOrNull()?.takeIf { it.isNotBlank() }?.let { _audioCodec.value = it }
        } catch (e: Throwable) {
            Log.e(TAG, "Error refreshing mpv tracks", e)
        }
    }

    fun setHwDecAndVoMode(mode: String) {
        if (isDestroyed.get()) return
        try {
            when (mode) {
                "mediacodec_embed", "mediacodec" -> {
                    mpv.setPropertyString("hwdec", "mediacodec")
                }
                "no", "software" -> {
                    mpv.setPropertyString("hwdec", "no")
                }
                else -> {
                    mpv.setPropertyString("hwdec", "mediacodec-copy")
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error switching mpv hwdec mode to $mode", e)
        }
    }
}
