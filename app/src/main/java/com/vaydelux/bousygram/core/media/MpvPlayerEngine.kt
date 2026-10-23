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
        private const val CELLULAR_DEMUXER_MAX_BYTES = "6291456"    // 6 MB runway
        private const val WIFI_DEMUXER_MAX_BYTES = "33554432"        // 32 MB runway
        private const val DEMUXER_MAX_BACK_BYTES = "2097152"         // 2 MB back cache
        private const val DEMUXER_CACHE_SECS = "60"
    }

    private val mpv: MPVLib = MPVLib.create(context)
        ?: throw IllegalStateException("Failed to create libmpv instance from native context")
    private val isDestroyed = AtomicBoolean(false)
    private var isCellular = initialIsCellular

    private val _playbackState = MutableStateFlow<EnginePlaybackState>(EnginePlaybackState.Idle)
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: Long get() = _currentPositionMs.value

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: Long get() = _durationMs.value

    private val _bufferedPositionMs = MutableStateFlow(0L)
    override val bufferedPositionMs: Long get() = _bufferedPositionMs.value

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: Boolean get() = _isPlaying.value

    private val _activeDecoder = MutableStateFlow("Auto")
    override val activeDecoder: String get() = _activeDecoder.value

    private val _tracks = MutableStateFlow<List<MediaTrackInfo>>(emptyList())
    override val tracks: StateFlow<List<MediaTrackInfo>> = _tracks.asStateFlow()

    private val _videoDimensions = MutableStateFlow<Pair<Int, Int>>(Pair(0, 0))
    override val videoDimensions: StateFlow<Pair<Int, Int>> = _videoDimensions.asStateFlow()

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
                    _isPlaying.value = true
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
            val isTv = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)

            // Core options before init
            mpv.setOptionString("vo", "gpu")
            mpv.setOptionString("gpu-context", "android")
            mpv.setOptionString("hwdec", "mediacodec,mediacodec-copy")
            mpv.setOptionString("hwdec-codecs", "all")
            mpv.setOptionString("ao", "audiotrack,opensles")
            mpv.setOptionString("tls-verify", "no")
            mpv.setOptionString("audio-pitch-correction", "yes")

            // HDMI Audio Bitstreaming Passthrough
            mpv.setOptionString("audio-spdif", "ac3,eac3,dts,dts-hd,truehd")

            // Android TV frame rate display sync
            if (isTv) {
                mpv.setOptionString("video-sync", "display-resample")
            }

            // Demuxer adaptive buffer configuration
            val demuxerBytes = if (isCellular) CELLULAR_DEMUXER_MAX_BYTES else WIFI_DEMUXER_MAX_BYTES
            mpv.setOptionString("demuxer-max-bytes", demuxerBytes)
            mpv.setOptionString("demuxer-max-back-bytes", DEMUXER_MAX_BACK_BYTES)
            mpv.setOptionString("cache-secs", DEMUXER_CACHE_SECS)

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
            Log.i(TAG, "MpvPlayerEngine initialized successfully")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize MpvPlayerEngine", e)
            _playbackState.value = EnginePlaybackState.Error("Failed to init libmpv engine: ${e.message}", e)
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
            mpv.command(arrayOf("loadfile", streamUrl))
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading stream in mpv: $streamUrl", e)
            _playbackState.value = EnginePlaybackState.Error("Failed to load stream: ${e.message}", e)
        }
    }

    override fun play() {
        if (isDestroyed.get()) return
        try {
            mpv.setPropertyBoolean("pause", false)
            _isPlaying.value = true
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting pause false", e)
        }
    }

    override fun pause() {
        if (isDestroyed.get()) return
        try {
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
            val demuxerBytes = if (isCellular) CELLULAR_DEMUXER_MAX_BYTES else WIFI_DEMUXER_MAX_BYTES
            mpv.setPropertyString("demuxer-max-bytes", demuxerBytes)
            Log.i(TAG, "Updated mpv demuxer-max-bytes to $demuxerBytes (isCellular=$isCellular)")
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
            try {
                mpv.removeObserver(eventObserver)
                mpv.detachSurface()
                mpv.destroy()
                _playbackState.value = EnginePlaybackState.Idle
                _isPlaying.value = false
                Log.i(TAG, "MpvPlayerEngine released")
            } catch (e: Throwable) {
                Log.e(TAG, "Error releasing mpv", e)
            }
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
        } catch (e: Throwable) {
            Log.e(TAG, "Error refreshing mpv tracks", e)
        }
    }
}
