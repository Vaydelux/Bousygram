package com.vaydelux.bousygram.core.media

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Surface
import com.vaydelux.bousygram.core.media.EnginePlaybackState
import com.vaydelux.bousygram.core.media.MediaTrackInfo
import com.vaydelux.bousygram.core.media.PlayerEngine
import com.vaydelux.bousygram.core.media.isTrustedExperimentalLocalStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VideoLAN 3.6.5 compatibility adapter, included as an explicit player choice.
 * Auto mode continues to use ExoPlayer/libmpv; one player owns native resources.
 *
 * No attempt is made to turn non-indexed MPEG-TS into a seekable file.
 */
class LibVlcPlayerEngine(context: Context) : PlayerEngine {
    private val released = AtomicBoolean(false)
    private val vlc = LibVLC(
        context.applicationContext,
        arrayListOf("--no-video-title-show", "--no-stats")
    )
    private val nativePlayer = MediaPlayer(vlc)
    private val state = MutableStateFlow<EnginePlaybackState>(EnginePlaybackState.Idle)
    private val activeTracks = MutableStateFlow<List<MediaTrackInfo>>(emptyList())
    private val dimensions = MutableStateFlow(0 to 0)
    private var surface: Surface? = null
    private var viewsAttached = false
    @Volatile private var playing = false
    @Volatile private var wantedPlayback = false
    @Volatile private var filePrepared = false
    @Volatile private var stoppedByOwner = false
    @Volatile private var timestampMs = 0L
    @Volatile private var knownDurationMs = 0L

    init {
        nativePlayer.setEventListener { event ->
            if (released.get()) return@setEventListener
            when (event.type) {
                MediaPlayer.Event.Opening, MediaPlayer.Event.Buffering -> {
                    playing = false
                    state.value = EnginePlaybackState.Buffering
                }
                MediaPlayer.Event.Playing -> {
                    // Native VLC may deliver a stale Playing event after Pause
                    // during seek/rebuffer. Only explicit intent may rearm audio.
                    if (!wantedPlayback) {
                        playing = false
                        state.value = EnginePlaybackState.Ready
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if (!released.get() && !wantedPlayback && filePrepared) {
                                runCatching {
                                    if (nativePlayer.isPlaying) nativePlayer.pause()
                                }
                            }
                        }
                    } else {
                        playing = true
                        state.value = EnginePlaybackState.Ready
                        knownDurationMs = runCatching { nativePlayer.length }.getOrDefault(-1L)
                            .coerceAtLeast(0L)
                        refreshTracks()
                    }
                }
                MediaPlayer.Event.Paused -> {
                    playing = false
                    if (filePrepared) state.value = EnginePlaybackState.Ready
                }
                MediaPlayer.Event.TimeChanged -> {
                    timestampMs = event.timeChanged.coerceAtLeast(0L)
                }
                MediaPlayer.Event.LengthChanged -> {
                    knownDurationMs = runCatching { nativePlayer.length }.getOrDefault(-1L)
                        .coerceAtLeast(0L)
                }
                MediaPlayer.Event.EndReached -> {
                    playing = false
                    if (!stoppedByOwner) state.value = EnginePlaybackState.Ended
                }
                MediaPlayer.Event.Stopped -> {
                    playing = false
                    if (!stoppedByOwner && filePrepared &&
                        state.value !is EnginePlaybackState.Ended
                    ) {
                        // VLC can report Stopped after EndReached. Preserve a
                        // genuine EOF instead of converting it into a false
                        // stream error and triggering unnecessary retry.
                        state.value = EnginePlaybackState.Error(
                            "Experimental LibVLC stopped before playback completed"
                        )
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    playing = false
                    state.value = EnginePlaybackState.Error(
                        "Experimental LibVLC could not decode or stream this file"
                    )
                }
            }
        }
    }

    override val engineName = "LibVLC"
    override val playbackState: StateFlow<EnginePlaybackState> get() = state
    override val tracks: StateFlow<List<MediaTrackInfo>> get() = activeTracks
    override val videoDimensions: StateFlow<Pair<Int, Int>> get() = dimensions
    override val currentPositionMs: Long get() = timestampMs
    override val durationMs: Long get() = knownDurationMs
    override val bufferedPositionMs: Long get() = timestampMs
    override val isPlaying: Boolean get() = playing
    override val playWhenReady: Boolean get() = wantedPlayback
    override val canSeek: Boolean
        get() = !released.get() && filePrepared &&
            runCatching { nativePlayer.isSeekable }.getOrDefault(false)
    override val seekableContainerDuration: Boolean get() = canSeek && durationMs > 0L
    override val activeDecoder: String get() = "VideoLAN native"
    override val bufferConfigDescription = "VideoLAN native; 450ms network cache"
    override val diagnosticDetails: Map<String, String>
        get() = mapOf(
            "engine" to engineName,
            "seekable" to canSeek.toString(),
            "streaming" to "localhost-only"
        )

    override fun attachSurface(surface: Surface) {
        if (released.get() || !surface.isValid) return
        if (viewsAttached && this.surface === surface) return
        detachSurface()
        val out = nativePlayer.vlcVout
        try {
            out.setVideoSurface(surface, null)
            out.attachViews()
            this.surface = surface
            viewsAttached = true
        } catch (failure: Throwable) {
            Log.e(TAG, "LibVLC video surface attach failed", failure)
            runCatching { out.detachViews() }
            this.surface = null
            viewsAttached = false
            state.value = EnginePlaybackState.Error("LibVLC video surface unavailable", failure)
        }
    }

    override fun detachSurface() {
        if (!viewsAttached) return
        runCatching { nativePlayer.vlcVout.detachViews() }
            .onFailure { Log.w(TAG, "LibVLC surface detach failed", it) }
        viewsAttached = false
        surface = null
    }

    override fun prepare(streamUrl: String) {
        check(!released.get()) { "LibVLC engine was released" }
        val uri = Uri.parse(streamUrl)
        require(isTrustedExperimentalLocalStream(uri)) {
            "Experimental LibVLC accepts account-authorized loopback streams only"
        }
        stoppedByOwner = false
        filePrepared = false
        playing = false
        wantedPlayback = false
        timestampMs = 0L
        knownDurationMs = 0L
        activeTracks.value = emptyList()
        state.value = EnginePlaybackState.Buffering
        val media = Media(vlc, uri)
        try {
            // The same private per-file token URL used by libmpv is supplied.
            // Keep caching bounded; the native engine must not download ahead
            // without limits or assume a missing transport-stream seek index.
            media.addOption(":network-caching=450")
            nativePlayer.media = media
            filePrepared = true
        } catch (failure: Throwable) {
            state.value = EnginePlaybackState.Error("Unable to prepare LibVLC stream", failure)
            throw failure
        } finally {
            media.release()
        }
    }

    override fun play() {
        if (released.get() || !filePrepared) return
        wantedPlayback = true
        stoppedByOwner = false
        if (!nativePlayer.isPlaying) nativePlayer.play()
    }

    override fun pause() {
        wantedPlayback = false
        if (!released.get() && filePrepared) {
            // LibVLC pause() toggles, so never invoke it repeatedly when paused.
            runCatching {
                if (nativePlayer.isPlaying) nativePlayer.pause()
            }.onFailure { Log.w(TAG, "LibVLC pause failed", it) }
        }
    }

    override fun seekTo(positionMs: Long) {
        if (!canSeek) return
        // Native TimeChanged is authoritative. Never publish requested time
        // until the decoder actually confirms playback at that timestamp.
        runCatching { nativePlayer.setTime(positionMs.coerceAtLeast(0L)) }
            .onFailure { Log.w(TAG, "LibVLC seek rejected", it) }
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (released.get()) return
        if (!speed.isFinite() || speed !in 0.25f..3.0f) return
        runCatching { nativePlayer.rate = speed }
            .onFailure { Log.w(TAG, "LibVLC speed setting unsupported", it) }
    }

    override fun setResizeMode(mode: Int) {
        // LibVLC uses the native vout. Aspect-fill/zoom parity is not qualified.
    }

    override fun selectTrack(track: MediaTrackInfo) {
        if (released.get()) return
        runCatching {
            when (track.type) {
                MediaTrackInfo.TrackType.AUDIO -> nativePlayer.setAudioTrack(track.id)
                MediaTrackInfo.TrackType.SUBTITLE -> nativePlayer.setSpuTrack(track.id)
                MediaTrackInfo.TrackType.VIDEO -> Unit
            }
        }.onFailure { Log.w(TAG, "LibVLC track selection failed", it) }
        refreshTracks()
    }

    override fun deselectTrack(type: MediaTrackInfo.TrackType) {
        if (released.get()) return
        runCatching {
            when (type) {
                MediaTrackInfo.TrackType.AUDIO -> nativePlayer.setAudioTrack(-1)
                MediaTrackInfo.TrackType.SUBTITLE -> nativePlayer.setSpuTrack(-1)
                MediaTrackInfo.TrackType.VIDEO -> Unit
            }
        }
        refreshTracks()
    }

    private fun refreshTracks() {
        if (released.get()) return
        runCatching {
            val audioSelected = nativePlayer.audioTrack
            val spuSelected = nativePlayer.spuTrack
            val audio = nativePlayer.audioTracks.orEmpty()
                .filter { it.id >= 0 }
                .map {
                    MediaTrackInfo(
                        id = it.id,
                        type = MediaTrackInfo.TrackType.AUDIO,
                        name = it.name ?: "Audio ${it.id}",
                        isSelected = it.id == audioSelected
                    )
                }
            val subtitle = nativePlayer.spuTracks.orEmpty()
                .filter { it.id >= 0 }
                .map {
                    MediaTrackInfo(
                        id = it.id,
                        type = MediaTrackInfo.TrackType.SUBTITLE,
                        name = it.name ?: "Subtitles ${it.id}",
                        isSelected = it.id == spuSelected
                    )
                }
            activeTracks.value = audio + subtitle
        }.onFailure { Log.w(TAG, "LibVLC track enumeration unavailable", it) }
    }

    override fun updateNetworkState(isCellular: Boolean) {
        // Deliberately no dynamic native buffer reconfiguration while playing.
    }

    override fun stop() {
        if (released.get()) return
        stoppedByOwner = true
        wantedPlayback = false
        playing = false
        filePrepared = false
        runCatching { nativePlayer.stop() }
        state.value = EnginePlaybackState.Idle
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        stoppedByOwner = true
        playing = false
        wantedPlayback = false
        runCatching { nativePlayer.setEventListener(null) }
        runCatching { nativePlayer.vlcVout.detachViews() }
        viewsAttached = false
        surface = null
        runCatching { nativePlayer.stop() }
        runCatching { nativePlayer.release() }
        runCatching { vlc.release() }
        state.value = EnginePlaybackState.Idle
        activeTracks.value = emptyList()
    }

    companion object {
        private const val TAG = "LibVlcEngine"
    }
}
