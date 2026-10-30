package com.vaydelux.bousygram.core.media

import android.content.Context
import android.util.Log
import android.view.Surface
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Universal media player engine wrapping libmpv (dev.jdtech.mpv:libmpv).
 *
 * Provides native hardware/software decoding across all media formats (MKV, AVI, FLV, WMV,
 * MP4, TS), advanced audio formats (AC-3, E-AC-3, DTS, TrueHD, Opus, FLAC), and complex
 * styled subtitles (SSA/ASS, PGS, SRT).
 *
 * Implements adaptive demuxer buffer management tuned for TDLib progressive loopback streaming.
 */
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
            if (isDestroyed.get()) return
            when (property) {
                "track-list" -> refreshTracks()
            }
        }

        override fun eventProperty(property: String, value: Long) {
            if (isDestroyed.get()) return
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
            if (isDestroyed.get()) return
            when (property) {
                "time-pos" -> {
                    val posMs = (value * 1000.0).toLong().coerceAtLeast(0L)
                    _currentPositionMs.value = posMs
                }
                "duration" -> {
                    if (value > 0.0) {
                        _durationMs.value = (value * 1000.0).toLong()
                    }
                }
                "demuxer-cache-time" -> {
                    val cacheAheadMs = (value * 1000.0).toLong().coerceAtLeast(0L)
                    _bufferedPositionMs.value = _currentPositionMs.value + cacheAheadMs
                }
            }
        }

        override fun eventProperty(property: String, value: Boolean) {
            if (isDestroyed.get()) return
            when (property) {
                "pause" -> {
                    _isPlaying.value = !value
                }
                "paused-for-cache" -> {
                    if (value) {
                        _playbackState.value = EnginePlaybackState.Buffering
                    } else if (_playbackState.value is EnginePlaybackState.Buffering) {
                        _playbackState.value = EnginePlaybackState.Ready
                    }
                }
                "eof-reached" -> {
                    if (value) {
                        _playbackState.value = EnginePlaybackState.Ended
                        _isPlaying.value = false
                        _playWhenReady.value = false
                    }
                }
            }
        }

        override fun eventProperty(property: String, value: String) {
            if (isDestroyed.get()) return
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
            if (isDestroyed.get()) return
            when (eventId) {
                MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
                    _playbackState.value = EnginePlaybackState.Buffering
                }
                MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                    _playbackState.value = EnginePlaybackState.Ready
                    // A newly loaded MKV must not inherit a paused state from the old file.
                    // Respect an explicit user pause if one happened while buffering.
                    if (_playWhenReady.value) {
                        runCatching { mpv.setPropertyBoolean("pause", false) }
                            .onFailure { Log.w(TAG, "Unable to start loaded media", it) }
                    }
                    _isPlaying.value = _playWhenReady.value
                    refreshTracks()
                    setResizeMode(currentResizeMode)
                }
                MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                    _playbackState.value = EnginePlaybackState.Ended
                    _isPlaying.value = false
                }
                MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                    _playbackState.value = EnginePlaybackState.Ready
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
        try {
            _currentPositionMs.value = 0L
            _durationMs.value = 0L
            _bufferedPositionMs.value = 0L
            _playbackState.value = EnginePlaybackState.Buffering
            _playWhenReady.value = true
            mpv.command(arrayOf("loadfile", streamUrl))
        } catch (e: Throwable) {
            Log.e(TAG, "MPV failed to load the active local stream (${e.javaClass.simpleName})")
            _playbackState.value = EnginePlaybackState.Error("The playback engine could not load this stream", e)
        }
    }

    override fun play() {
        if (isDestroyed.get()) return
        try {
            _playWhenReady.value = true
            mpv.setPropertyBoolean("pause", false)
            _isPlaying.value = true
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
        try {
            val seconds = (positionMs.coerceAtLeast(0L)) / 1000.0
            mpv.command(arrayOf("seek", seconds.toString(), "absolute"))
            _currentPositionMs.value = positionMs
        } catch (e: Throwable) {
            Log.e(TAG, "Error seeking mpv to $positionMs ms", e)
        }
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
        try {
            mpv.command(arrayOf("stop"))
            _playbackState.value = EnginePlaybackState.Idle
            _isPlaying.value = false
        } catch (e: Throwable) {
            Log.e(TAG, "Error stopping mpv", e)
        }
    }

    override fun release() {
        if (isDestroyed.compareAndSet(false, true)) {
            // Each cleanup must run even if another native cleanup operation fails.
            runCatching { mpv.removeObserver(eventObserver) }
            runCatching { mpv.detachSurface() }
            runCatching { mpv.destroy() }.onFailure { Log.e(TAG, "Error destroying mpv", it) }
            _playbackState.value = EnginePlaybackState.Idle
            _isPlaying.value = false
            _playWhenReady.value = false
            Log.i(TAG, "MpvPlayerEngine released")
        }
    }

    private fun refreshTracks() {
        if (isDestroyed.get()) return
        try {
            val count = mpv.getPropertyInt("track-list/count") ?: 0
            val list = mutableListOf<MediaTrackInfo>()
            for (i in 0 until count) {
                val typeStr = mpv.getPropertyString("track-list/$i/type") ?: continue
                val id = mpv.getPropertyInt("track-list/$i/id") ?: (i + 1)
                val title = mpv.getPropertyString("track-list/$i/title")
                val lang = mpv.getPropertyString("track-list/$i/lang")
                val selected = mpv.getPropertyBoolean("track-list/$i/selected") ?: false

                val trackType = when (typeStr) {
                    "sub" -> MediaTrackInfo.TrackType.SUBTITLE
                    "audio" -> MediaTrackInfo.TrackType.AUDIO
                    "video" -> MediaTrackInfo.TrackType.VIDEO
                    else -> continue
                }

                val displayName = when {
                    !title.isNullOrBlank() -> title
                    !lang.isNullOrBlank() -> "Language: $lang"
                    else -> "Track #$id"
                }

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
