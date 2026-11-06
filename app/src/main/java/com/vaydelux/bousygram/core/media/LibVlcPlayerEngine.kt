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
import java.util.concurrent.atomic.AtomicLong

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
    private val nativePlayer = createNativeChildOrReleaseParent(
        vlc,
        create = { MediaPlayer(it) },
        releaseParent = { it.release() }
    )
    // Keep native VLC calls off its event callback thread to prevent reentry
    // deadlocks. This Handler belongs only to this engine and is cleared on release.
    private val eventHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var activeEqualizer: MediaPlayer.Equalizer? = null

    override fun applyAudioEqualizer(preset: AudioEqualizerPreset): Boolean {
        if (released.get()) return false
        return runCatching {
            if (!preset.isEnabled) {
                val disabled = nativePlayer.setEqualizer(null)
                activeEqualizer = null
                return@runCatching disabled
            }
            val eq = MediaPlayer.Equalizer.create()
            eq.setPreAmp(preset.preampDb)
            val count = MediaPlayer.Equalizer.getBandCount().coerceIn(0, 32)
            for (index in 0 until count) {
                val freqHz = MediaPlayer.Equalizer.getBandFrequency(index)
                eq.setAmp(index, preset.gainDbAt(freqHz))
            }
            val applied = nativePlayer.setEqualizer(eq)
            if (applied) activeEqualizer = eq
            applied
        }.getOrElse { failure ->
            Log.w(TAG, "LibVLC equalizer unavailable for current output", failure)
            false
        }
    }
    private val state = MutableStateFlow<EnginePlaybackState>(EnginePlaybackState.Idle)
    private val activeTracks = MutableStateFlow<List<MediaTrackInfo>>(emptyList())
    private val dimensions = MutableStateFlow(0 to 0)
    private var surface: Surface? = null
    private var viewsAttached = false
    @Volatile private var playing = false
    @Volatile private var wantedPlayback = false
    @Volatile private var filePrepared = false
    @Volatile private var playbackStarted = false
    // Discard delayed main-thread native callback work after stop/reprepare.
    private val mediaGeneration = AtomicLong(0L)
    @Volatile private var stoppedByOwner = false
    // LibVLC pause() toggles; command once until acknowledged or re-armed by Play.
    @Volatile private var pauseCommandPending = false
    @Volatile private var timestampMs = 0L
    @Volatile private var knownDurationMs = 0L

    private fun requestNativePause() {
        if (released.get() || !filePrepared || pauseCommandPending) return
        try {
            if (nativePlayer.isPlaying) {
                pauseCommandPending = true
                nativePlayer.pause()
            }
        } catch (failure: Throwable) {
            pauseCommandPending = false
            Log.w(TAG, "Unable to request native Pause", failure)
        }
    }

    init {
        try {
        nativePlayer.setEventListener { event ->
            if (!acceptsLibVlcMediaEvent(stoppedByOwner, filePrepared, released.get())) {
                return@setEventListener
            }
            when (event.type) {
                MediaPlayer.Event.Opening -> {
                    playing = false
                    state.value = EnginePlaybackState.Buffering
                }
                MediaPlayer.Event.Buffering -> {
                    // A buffering notification does not mean a running
                    // decoder stopped. Avoid calling native VLC from its
                    // callback thread and preserve the user Pause intention.
                    val running = playing && wantedPlayback
                    state.value = libVlcBufferState(wantedPlayback, running)
                    if (!wantedPlayback) {
                        val generation = mediaGeneration.get()
                        eventHandler.post {
                            if (generation == mediaGeneration.get() &&
                                acceptsLibVlcMediaEvent(stoppedByOwner, filePrepared, released.get()) &&
                                !wantedPlayback
                            ) requestNativePause()
                        }
                    }
                }
                MediaPlayer.Event.Playing -> {
                    playbackStarted = true
                    // Native VLC may deliver a stale Playing event after Pause
                    // during seek/rebuffer. Only explicit intent may rearm audio.
                    if (!wantedPlayback) {
                        playing = false
                        state.value = EnginePlaybackState.Ready
                        val generation = mediaGeneration.get()
                        eventHandler.post {
                            if (generation == mediaGeneration.get() &&
                                acceptsLibVlcMediaEvent(stoppedByOwner, filePrepared, released.get()) &&
                                !wantedPlayback
                            ) requestNativePause()
                        }
                    } else {
                        pauseCommandPending = false
                        playing = true
                        state.value = EnginePlaybackState.Ready
                        val generation = mediaGeneration.get()
                        eventHandler.post {
                            if (generation == mediaGeneration.get() &&
                                acceptsLibVlcMediaEvent(stoppedByOwner, filePrepared, released.get()) &&
                                wantedPlayback
                            ) {
                                knownDurationMs = runCatching { nativePlayer.length }
                                    .getOrDefault(-1L).coerceAtLeast(0L)
                                refreshTracks()
                            }
                        }
                    }
                }
                MediaPlayer.Event.Paused -> {
                    pauseCommandPending = false
                    playing = false
                    if (filePrepared) state.value = EnginePlaybackState.Ready
                }
                MediaPlayer.Event.TimeChanged -> {
                    timestampMs = event.timeChanged.coerceAtLeast(0L)
                }
                MediaPlayer.Event.LengthChanged -> {
                    val generation = mediaGeneration.get()
                    eventHandler.post {
                        if (generation == mediaGeneration.get() &&
                            acceptsLibVlcMediaEvent(stoppedByOwner, filePrepared, released.get())
                        ) {
                            knownDurationMs = runCatching { nativePlayer.length }
                                .getOrDefault(-1L).coerceAtLeast(0L)
                        }
                    }
                }
                MediaPlayer.Event.EndReached -> {
                    if (shouldReportLibVlcEnded(activeMedia = true, playbackStarted = playbackStarted)) {
                        playing = false
                        state.value = EnginePlaybackState.Ended
                    }
                }
                MediaPlayer.Event.Stopped -> {
                    playing = false
                    if (shouldReportLibVlcUnexpectedStop(
                            activeMedia = true,
                            playbackStarted = playbackStarted,
                            alreadyEnded = state.value is EnginePlaybackState.Ended
                        )
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
        } catch (failure: Throwable) {
            // Construction can fail while installing native event callbacks.
            // The Kotlin object never reaches release(), so close both native
            // resources here. Do not mask the original creation failure.
            released.set(true)
            eventHandler.removeCallbacksAndMessages(null)
            runCatching { nativePlayer.release() }.onFailure { failure.addSuppressed(it) }
            runCatching { vlc.release() }.onFailure { failure.addSuppressed(it) }
            throw failure
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

    /** Update native VLC vout geometry without releasing an active decoder. */
    fun onVideoSurfaceSizeChanged(width: Int, height: Int) {
        if (released.get() || !viewsAttached || width <= 0 || height <= 0) return
        runCatching { nativePlayer.vlcVout.setWindowSize(width, height) }
            .onFailure { Log.w(TAG, "LibVLC surface size update failed", it) }
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
        mediaGeneration.incrementAndGet()
        stoppedByOwner = false
        pauseCommandPending = false
        filePrepared = false
        playbackStarted = false
        playing = false
        wantedPlayback = false
        timestampMs = 0L
        knownDurationMs = 0L
        activeTracks.value = emptyList()
        dimensions.value = 0 to 0
        state.value = EnginePlaybackState.Buffering
        val media = try {
            Media(vlc, uri)
        } catch (failure: Throwable) {
            state.value = EnginePlaybackState.Error("Unable to create native LibVLC media", failure)
            throw failure
        }
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
        pauseCommandPending = false
        runCatching {
            if (!nativePlayer.isPlaying) nativePlayer.play()
        }.onFailure {
            state.value = EnginePlaybackState.Error("LibVLC could not start this video", it)
        }
    }

    override fun pause() {
        wantedPlayback = false
        requestNativePause()
    }

    override fun seekTo(positionMs: Long) {
        if (!canSeek) return
        // Native TimeChanged is authoritative. Never publish requested time
        // until the decoder actually confirms playback at that timestamp.
        runCatching { nativePlayer.setTime(boundedLibVlcSeekMs(positionMs, durationMs)) }
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
                .asSequence()
                .filter { it.id >= 0 }
                .take(PlaybackTrackBudget.MAX_UI_TRACKS)
                .map {
                    MediaTrackInfo(
                        id = it.id,
                        type = MediaTrackInfo.TrackType.AUDIO,
                        name = PlaybackTrackBudget.label(it.name, "Audio ${it.id}"),
                        isSelected = it.id == audioSelected
                    )
                }.toList()
            val subtitle = nativePlayer.spuTracks.orEmpty()
                .asSequence()
                .filter { it.id >= 0 }
                .take(PlaybackTrackBudget.MAX_UI_TRACKS - audio.size)
                .map {
                    MediaTrackInfo(
                        id = it.id,
                        type = MediaTrackInfo.TrackType.SUBTITLE,
                        name = PlaybackTrackBudget.label(it.name, "Subtitles ${it.id}"),
                        isSelected = it.id == spuSelected
                    )
                }.toList()
            activeTracks.value = audio + subtitle
        }.onFailure { Log.w(TAG, "LibVLC track enumeration unavailable", it) }
    }

    override fun updateNetworkState(isCellular: Boolean) {
        // Deliberately no dynamic native buffer reconfiguration while playing.
    }

    override fun stop() {
        if (released.get()) return
        mediaGeneration.incrementAndGet()
        stoppedByOwner = true
        playbackStarted = false
        wantedPlayback = false
        playing = false
        filePrepared = false
        runCatching { nativePlayer.stop() }
        activeTracks.value = emptyList()
        dimensions.value = 0 to 0
        state.value = EnginePlaybackState.Idle
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        mediaGeneration.incrementAndGet()
        eventHandler.removeCallbacksAndMessages(null)
        playbackStarted = false
        stoppedByOwner = true
        playing = false
        wantedPlayback = false
        runCatching { nativePlayer.setEventListener(null) }
        runCatching { nativePlayer.setEqualizer(null) }
        activeEqualizer = null
        runCatching { nativePlayer.vlcVout.detachViews() }
        viewsAttached = false
        surface = null
        runCatching { nativePlayer.stop() }
        runCatching { nativePlayer.release() }
        runCatching { vlc.release() }
        state.value = EnginePlaybackState.Idle
        activeTracks.value = emptyList()
        dimensions.value = 0 to 0
    }

    companion object {
        private const val TAG = "LibVlcEngine"
    }
}
