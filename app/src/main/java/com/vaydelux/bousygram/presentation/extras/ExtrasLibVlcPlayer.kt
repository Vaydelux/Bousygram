package com.vaydelux.bousygram.presentation.extras

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vaydelux.mediaforge.core.CaptionCandidate
import com.vaydelux.mediaforge.core.InspectedMedia
import com.vaydelux.mediaforge.core.MediaForgeUrlPolicy
import com.vaydelux.mediaforge.players.MediaForgeGuardedProgressiveSource
import com.vaydelux.mediaforge.players.MediaForgeRangeMetricsSnapshot
import com.vaydelux.bousygram.core.media.LibVlcUnexpectedPauseAction
import com.vaydelux.bousygram.core.media.libVlcUnexpectedPauseAction
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

/** Only Extras uses this decoder; Telegram's loopback-only LibVlcPlayerEngine is untouched. */
internal class ExtrasLibVlcSession(
    context: Context,
    private val media: InspectedMedia,
    private val subtitle: CaptionCandidate?,
    private val guarded: MediaForgeGuardedProgressiveSource,
    initialPositionMs: Long,
    private val initiallyPlayRequested: Boolean,
    private val onStatus: (String) -> Unit,
    private val onControls: (ExtrasLibVlcControlState) -> Unit
) {
    private val released = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusRecovery = ExtrasAudioFocusRecoveryIntent()
    private var inForeground = true
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (!released.get()) handler.post {
            if (released.get()) return@post
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    focusRecovery.onFocusLost(
                        transient = true, wasPlayRequested = playbackRequested
                    )
                    pauseForAudioFocus()
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    focusRecovery.onFocusLost(
                        transient = false, wasPlayRequested = playbackRequested
                    )
                    pauseForAudioFocus()
                    releaseAudioFocus()
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (focusRecovery.onFocusGained(inForeground)) play()
                }
            }
        }
    }
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
        )
        .setOnAudioFocusChangeListener(audioFocusListener, handler)
        .build()

    private val libVlc = LibVLC(
        context.applicationContext,
        arrayListOf("--no-video-title-show", "--no-stats")
    )
    private val player = try {
        MediaPlayer(libVlc)
    } catch (error: Throwable) {
        libVlc.release()
        throw error
    }
    private var attached = false
    private var initialized = false
    private var hasAudioFocus = false
    @Volatile private var playbackRequested = false
    private val unexpectedPauseRetries = AtomicInteger(0)
    private var hasPlaybackError = false
    private var reachedEnd = false
    private val initialSeek = ExtrasVlcInitialSeek(initialPositionMs)
    private val latencyProbe = ExtrasVlcLatencyProbe()
    private val progressTicker = object : Runnable {
        override fun run() {
            if (released.get() || !attached || !inForeground) return
            restoreSubtitleChangePosition()
            publishControls()
            if (playbackRequested || latencyProbe.hasPendingSeek) {
                handler.postDelayed(this, 750L)
            }
        }
    }

    private fun restoreSubtitleChangePosition() {
        if (released.get() || !attached || !playbackRequested || hasPlaybackError) return
        val playing = runCatching { player.isPlaying }.getOrDefault(false)
        val seekable = runCatching { player.isSeekable }.getOrDefault(false)
        val duration = runCatching { player.length }.getOrDefault(0L)
        val requested = initialSeek.nextTarget(playing, seekable, duration) ?: return
        val applied = runCatching { player.setTime(requested) }
        initialSeek.onApplied(applied.isSuccess)
        if (applied.isFailure && !initialSeek.isPending) {
            onStatus("Could not restore position after subtitle change")
        }
    }

    private fun publishControls() {
        if (released.get()) return
        val position = runCatching { player.time }.getOrDefault(0L).coerceAtLeast(0L)
        val duration = runCatching { player.length }.getOrDefault(0L).coerceAtLeast(0L)
        latencyProbe.observeNativePosition(position, SystemClock.elapsedRealtime())
        onControls(
            ExtrasLibVlcControlState(
                positionMs = position,
                durationMs = duration,
                seekable = runCatching { player.isSeekable }.getOrDefault(false),
                playing = runCatching { player.isPlaying }.getOrDefault(false),
                playRequested = playbackRequested,
                failed = hasPlaybackError,
                ended = reachedEnd,
                timing = latencyProbe.snapshot(),
                rangeMetrics = guarded.metrics.snapshot()
            )
        )
    }

    private fun postControls() {
        handler.post { publishControls() }
    }

    private fun scheduleProgress() {
        handler.removeCallbacks(progressTicker)
        if (!released.get() && attached && inForeground) handler.post(progressTicker)
    }

    val wantsPlayback: Boolean get() = playbackRequested
    val wantsResumeAfterFocus: Boolean get() = focusRecovery.hasPendingResume

    fun setForeground(active: Boolean) {
        inForeground = active
        if (active) scheduleProgress()
        else handler.removeCallbacks(progressTicker)
    }

    init {
        player.setEventListener { event ->
            // LibVLC delivers callbacks on its native event dispatcher.
            // Never mutate playback intent, focus, retry state or Compose
            // snapshots from that thread. Capture the immutable event type
            // and serialize all processing on the main Handler.
            if (released.get()) return@setEventListener
            val type = event.type
            // VLC also emits frequent TimeChanged/PositionChanged events.
            // Their snapshots are sampled by the bounded ticker instead;
            // posting every native event would flood Android TV's UI Looper.
            when (type) {
                MediaPlayer.Event.Playing,
                MediaPlayer.Event.Buffering,
                MediaPlayer.Event.Paused,
                MediaPlayer.Event.EncounteredError,
                MediaPlayer.Event.EndReached -> handler.post {
                    if (!released.get()) handleNativeEvent(type)
                }
                else -> Unit
            }
        }
    }

    private fun handleNativeEvent(type: Int) {
        when (type) {
            MediaPlayer.Event.Playing -> {
                if (playbackRequested) {
                    latencyProbe.onNativePlaying(SystemClock.elapsedRealtime())
                    onStatus("Playing")
                    publishControls()
                } else {
                    // A late native Playing callback may follow user Pause
                    // while buffering. Never resurrect playback intent.
                    enforceNativePauseAfterDelayedStart()
                }
            }
            MediaPlayer.Event.Buffering -> {
                if (playbackRequested) {
                    onStatus("Buffering…")
                    publishControls()
                } else {
                    enforceNativePauseAfterDelayedStart()
                }
            }
            MediaPlayer.Event.Paused -> {
                when (libVlcUnexpectedPauseAction(
                    playRequested = playbackRequested,
                    prepared = initialized,
                    stoppedByOwner = released.get(),
                    alreadyRetried = unexpectedPauseRetries.get()
                )) {
                    LibVlcUnexpectedPauseAction.IGNORE -> {
                        if (!hasPlaybackError && !reachedEnd &&
                            !focusRecovery.hasPendingResume
                        ) onStatus("Paused")
                        publishControls()
                    }
                    LibVlcUnexpectedPauseAction.RETRY -> {
                        unexpectedPauseRetries.incrementAndGet()
                        if (!released.get() && initialized && playbackRequested) {
                            runCatching { player.play() }.onFailure {
                                playbackRequested = false
                                hasPlaybackError = true
                                releaseAudioFocus()
                                onStatus("LibVLC could not resume; try Retry or Media3")
                                publishControls()
                            }
                        }
                    }
                    LibVlcUnexpectedPauseAction.FAIL -> {
                        playbackRequested = false
                        hasPlaybackError = true
                        handler.removeCallbacks(progressTicker)
                        releaseAudioFocus()
                        onStatus("LibVLC paused repeatedly; try Retry or Media3")
                        publishControls()
                    }
                }
            }
            MediaPlayer.Event.EncounteredError -> {
                playbackRequested = false
                hasPlaybackError = true
                reachedEnd = false
                focusRecovery.cancel()
                handler.removeCallbacks(progressTicker)
                releaseAudioFocus()
                onStatus("LibVLC cannot open this stream; use Retry or Media3")
                publishControls()
            }
            MediaPlayer.Event.EndReached -> {
                playbackRequested = false
                reachedEnd = true
                hasPlaybackError = false
                focusRecovery.cancel()
                handler.removeCallbacks(progressTicker)
                releaseAudioFocus()
                onStatus("Playback finished")
                publishControls()
            }
        }
    }

    private fun enforceNativePauseAfterDelayedStart() {
        if (released.get() || playbackRequested) return
        runCatching { if (player.isPlaying) player.pause() }
        if (!hasPlaybackError && !reachedEnd && !focusRecovery.hasPendingResume) {
            onStatus("Paused")
        }
        publishControls()
    }

    fun attach(layout: VLCVideoLayout) {
        // Native VLC redirects and subresources do not honor the Media3 DNS policy.
        check(com.vaydelux.bousygram.BuildConfig.MEDIAFORGE_EXTRAS_LIBVLC_ENABLED) {
            "Extras LibVLC is restricted to development builds"
        }
        if (released.get() || attached) return
        require(MediaForgeUrlPolicy.safeHttpsUrl(media.stream.url) != null) {
            "Only validated HTTPS sources can enter the Extras player"
        }
        player.attachViews(layout, null, true, false)
        attached = true
        player.setAspectRatio(null)
        player.setScale(0f) // Natural fit rather than zoom/crop.
        if (!initialized) {
            // Native VLC sees a local seekable FD, not a remote URI.
            // The FD's read callbacks perform all remote requests through
            // MediaForge's guarded HTTP client, including range requests.
            val vlcMedia = Media(libVlc, guarded.descriptor.fileDescriptor)
            try {
                player.media = vlcMedia
                initialized = true
            } finally {
                vlcMedia.release()
            }
            guarded.localSubtitle?.let {
                val accepted = runCatching {
                    player.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(it), true)
                }.getOrDefault(false)
                if (!accepted) onStatus("External subtitle is not supported by LibVLC")
            }
            if (subtitle != null && guarded.localSubtitle == null) {
                onStatus("Subtitle unavailable through guarded LibVLC; use Media3")
            }
        }
        if (initiallyPlayRequested) {
            play()
        } else {
            // Engine switches and subtitle changes are not an implicit Play.
            onStatus("Paused")
            publishControls()
        }
        scheduleProgress()
    }

    fun play() {
        if (released.get() || !initialized || !inForeground) return
        // A second Play during buffering must not reset native pause-recovery
        // state, retrigger the decoder, or restart the startup stopwatch.
        if (playbackRequested && !hasPlaybackError && !reachedEnd) return
        focusRecovery.cancel()
        if (!hasAudioFocus) {
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                onStatus("Audio output unavailable")
                return
            }
            hasAudioFocus = true
        }
        // Arm user intent BEFORE calling native play: some LibVLC callbacks
        // can race with the caller. Never mark Playing as an unsolicited start.
        val restartEndedMedia = reachedEnd
        hasPlaybackError = false
        reachedEnd = false
        unexpectedPauseRetries.set(0)
        playbackRequested = true
        latencyProbe.onPlayRequested(SystemClock.elapsedRealtime())
        onStatus("Starting playback…")
        runCatching {
            if (restartEndedMedia && player.isSeekable) player.setTime(0L)
            player.play()
        }.onFailure {
            playbackRequested = false
            hasPlaybackError = true
            releaseAudioFocus()
            onStatus("LibVLC could not start playback; use Retry or Media3")
        }
        scheduleProgress()
        publishControls()
    }

    fun retry() {
        if (released.get() || !initialized || !inForeground) return
        hasPlaybackError = false
        reachedEnd = false
        runCatching { player.stop() }
        play()
    }

    /**
     * Focus loss suppresses decoder output without abandoning the focus
     * request. Android can now deliver GAIN; a regular Pause must NOT do this.
     */
    private fun pauseForAudioFocus() {
        playbackRequested = false
        unexpectedPauseRetries.set(0)
        handler.removeCallbacks(progressTicker)
        if (released.get()) return
        if (runCatching { player.isPlaying }.getOrDefault(false)) {
            runCatching { player.pause() }
        }
        if (!hasPlaybackError && !reachedEnd) onStatus("Paused for audio focus")
        publishControls()
        // A seek issued just before Pause may still be acknowledging.
        // Keep its 15-second bounded position probe alive in foreground.
        if (latencyProbe.hasPendingSeek) scheduleProgress()
    }

    fun pause() {
        focusRecovery.cancel()
        pauseForAudioFocus()
        if (!released.get() && !hasPlaybackError && !reachedEnd) onStatus("Paused")
        releaseAudioFocus()
    }

    private fun releaseAudioFocus() {
        if (!hasAudioFocus) return
        hasAudioFocus = false
        runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
    }

    fun skip(deltaMs: Long) {
        if (released.get()) return
        val seek = runCatching {
            if (!player.isSeekable) null
            else {
                val previous = player.time.coerceAtLeast(0L)
                Pair(extrasVlcSeekTarget(previous, deltaMs, player.length), previous)
            }
        }
        val requested = seek.getOrNull()
        if (requested == null) {
            latencyProbe.onSeekRejected()
            onStatus("Seeking is not available for this stream")
        } else {
            val (target, previous) = requested
            issueSeek(target, previous)
        }
        publishControls()
    }

    fun seekTo(positionMs: Long) {
        if (released.get()) return
        val seek = runCatching {
            if (!player.isSeekable) null
            else {
                val previous = player.time.coerceAtLeast(0L)
                Pair(extrasVlcSeekAbsolute(positionMs, player.length), previous)
            }
        }
        val requested = seek.getOrNull()
        if (requested == null) {
            latencyProbe.onSeekRejected()
            onStatus("Seeking is not available for this stream")
        } else {
            val (target, previous) = requested
            issueSeek(target, previous)
        }
        publishControls()
    }

    private fun issueSeek(targetMs: Long, previousPositionMs: Long) {
        val startedMs = SystemClock.elapsedRealtime()
        if (runCatching { player.setTime(targetMs) }.isSuccess) {
            latencyProbe.onSeekRequested(targetMs, startedMs, previousPositionMs)
            if (latencyProbe.hasPendingSeek) scheduleProgress()
        } else {
            latencyProbe.onSeekRejected()
            onStatus("Seek failed in LibVLC; try another position or Media3")
        }
    }

    fun detach() {
        if (released.get() || !attached) return
        runCatching { player.detachViews() }
        attached = false
        handler.removeCallbacks(progressTicker)
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        focusRecovery.cancel()
        inForeground = false
        playbackRequested = false
        handler.removeCallbacksAndMessages(null)
        runCatching { player.setEventListener(null) }
        runCatching { player.stop() }
        runCatching { player.detachViews() }
        attached = false
        runCatching { player.release() }
        runCatching { libVlc.release() }
        guarded.close()
        releaseAudioFocus()
    }
}

@Composable
internal fun ExtrasLibVlcPlayer(
    media: InspectedMedia,
    caption: CaptionCandidate?,
    title: String,
    isTv: Boolean,
    initialPositionMs: Long,
    initiallyPlayRequested: Boolean,
    onBack: () -> Unit,
    onUseMedia3: (ExtrasPlaybackHandoff) -> Unit,
    onChooseSubtitles: (ExtrasPlaybackHandoff) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember(media.stream.url, caption?.url) { mutableStateOf("Preparing LibVLC…") }
    var controls by remember(media.stream.url, caption?.url) { mutableStateOf(ExtrasLibVlcControlState()) }
    var source by remember(media.stream.url, caption?.url) {
        mutableStateOf<MediaForgeGuardedProgressiveSource?>(null)
    }
    var initError by remember(media.stream.url, caption?.url) {
        mutableStateOf<String?>(null)
    }
    LaunchedEffect(media.stream.url, caption?.url) {
        if (!com.vaydelux.bousygram.BuildConfig.MEDIAFORGE_EXTRAS_LIBVLC_ENABLED ||
            !MediaForgeGuardedProgressiveSource.supported(media.stream)
        ) {
            initError = "Guarded LibVLC supports debug MP4/WebM only."
            return@LaunchedEffect
        }
        try {
            val prepared = MediaForgeGuardedProgressiveSource.open(context, media.stream, caption)
            if (isActive) source = prepared else prepared.close()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            initError = "This source does not support secure byte-range playback. Use Media3."
        }
    }
    DisposableEffect(source) {
        onDispose { source?.close() }
    }
    // Failed native initialization is shown as a recoverable Media3 fallback.
    val sessionResult = remember(
        source, media.stream.url, caption?.url, initialPositionMs, initiallyPlayRequested
    ) {
        source?.let { guardedSource ->
            runCatching {
                ExtrasLibVlcSession(
                    context, media, caption, guardedSource,
                    initialPositionMs = initialPositionMs,
                    initiallyPlayRequested = initiallyPlayRequested,
                    onStatus = { status = it },
                    onControls = { controls = it }
                )
            }
        }
    }
    val session = sessionResult?.getOrNull()
    val foregroundIntent = remember(session) { ExtrasForegroundPlaybackIntent() }
    DisposableEffect(session, lifecycleOwner) {
        session?.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    foregroundIntent.onBackground(
                        session?.wantsPlayback == true || session?.wantsResumeAfterFocus == true
                    )
                    session?.setForeground(false)
                    session?.pause()
                }
                Lifecycle.Event.ON_START -> {
                    session?.setForeground(true)
                    if (foregroundIntent.onForeground()) session?.play()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            foregroundIntent.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            session?.release()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val compact = maxWidth < 620.dp
        if (session == null) {
            Column(
                Modifier.fillMaxSize().padding(if (isTv) 24.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExtrasVlcAction("Back to WebView", onClick = onBack)
                Text(title, color = Color.White, fontSize = if (isTv) 22.sp else 18.sp)
                if (initError == null && source == null) {
                    CircularProgressIndicator(Modifier.padding(16.dp), color = Color(0xFF84DEFF))
                    Text("Preparing guarded playback…", color = Color.White)
                } else {
                    Text(
                        initError ?: "LibVLC is unavailable on this device. Use Media3.",
                        color = Color(0xFFFFD5CF),
                        fontSize = if (isTv) 20.sp else 16.sp
                    )
                    ExtrasVlcAction(
                        "Continue with Media3",
                        emphasized = true,
                        // A failed guarded-LibVLC probe must not erase the
                        // seekable position handed off by the Media3 player.
                        onClick = {
                            onUseMedia3(ExtrasPlaybackHandoff(
                                positionMs = initialPositionMs.coerceAtLeast(0L),
                                playRequested = initiallyPlayRequested
                            ))
                        }
                    )
                }
            }
        } else {
            val playFocus = remember(session) { FocusRequester() }
            val videoFocus = remember(session) { FocusRequester() }
            var showDiagnostics by remember(session) { mutableStateOf(false) }
            var processPssKiB by remember(session) { mutableStateOf<Long?>(null) }
            var pssSampleRequest by remember(session) { mutableIntStateOf(0) }
            LaunchedEffect(session, showDiagnostics, pssSampleRequest) {
                if (showDiagnostics) {
                    // PSS collection can pause the caller while Android walks
                    // process mappings. Keep it away from Compose's UI thread.
                    processPssKiB = withContext(Dispatchers.Default) {
                        runCatching { Debug.getPss() }.getOrNull()
                    }
                }
            }
            var controlsVisible by remember(session) { mutableStateOf(true) }
            var headerFocused by remember(session) { mutableStateOf(false) }
            var footerFocused by remember(session) { mutableStateOf(false) }
            var requestControlFocus by remember(session) { mutableStateOf(false) }
            var scrubFraction by remember(session) { mutableStateOf<Float?>(null) }

            LaunchedEffect(session) {
                // Native player begins with video focused. D-pad select or
                // down reveals controls rather than accidentally starting
                // playback twice during buffering.
                withFrameNanos { }
                runCatching { videoFocus.requestFocus() }
            }
            LaunchedEffect(controlsVisible, requestControlFocus) {
                if (controlsVisible && requestControlFocus) {
                    withFrameNanos { }
                    runCatching { playFocus.requestFocus() }
                    requestControlFocus = false
                }
            }
            LaunchedEffect(
                controlsVisible, controls.playRequested, controls.playing,
                controls.failed, controls.ended, status, showDiagnostics,
                headerFocused, footerFocused
            ) {
                // Buffering, errors, Pause, diagnostics, and focused buttons
                // must never disappear, even if native isPlaying stays true
                // throughout a network stall.
                if (controlsVisible && extrasVlcChromeMayAutoHide(
                    controls, status, showDiagnostics, headerFocused, footerFocused
                )) {
                    delay(5_000L)
                    if (extrasVlcChromeMayAutoHide(
                        controls, status, showDiagnostics, headerFocused, footerFocused
                    )) {
                        controlsVisible = false
                        runCatching { videoFocus.requestFocus() }
                    }
                }
            }
            LaunchedEffect(
                controlsVisible, controls.playRequested, controls.playing,
                controls.failed, controls.ended, showDiagnostics, status
            ) {
                if (!controlsVisible && extrasVlcChromeMustRemainVisible(
                    controls, status, showDiagnostics
                )) controlsVisible = true
            }

            // Fullscreen, aspect-fit VLC video; the native decoder never sees
            // the third-party network URL, only MediaForge's guarded local FD.
            AndroidView(
                factory = { ctx ->
                    VLCVideoLayout(ctx).also { layout ->
                        runCatching { session.attach(layout) }.onFailure {
                            status = "LibVLC could not prepare this source; use Media3"
                        }
                    }
                },
                onRelease = { session.detach() },
                modifier = Modifier.fillMaxSize()
            )
            // Video-only D-pad surface: arrows seek when THIS surface is
            // focused, not when the user is navigating buttons or the slider.
            Box(
                Modifier.fillMaxSize()
                    .focusRequester(videoFocus)
                    .testTag("extras-libvlc-video-dpad")
                    .pointerInput(session) {
                        detectTapGestures {
                            controlsVisible = if (extrasVlcChromeMustRemainVisible(
                                controls, status, showDiagnostics
                            )) true else !controlsVisible
                        }
                    }
                    .onPreviewKeyEvent { key ->
                        if (key.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (key.key) {
                            Key.DirectionLeft -> {
                                session.skip(-10_000L)
                                controlsVisible = true
                                true
                            }
                            Key.DirectionRight -> {
                                session.skip(10_000L)
                                controlsVisible = true
                                true
                            }
                            Key.DirectionDown, Key.DirectionCenter, Key.Enter -> {
                                controlsVisible = true
                                requestControlFocus = true
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
            )

            if (controlsVisible) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .onFocusChanged { headerFocused = it.hasFocus }
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xED07101B), Color(0x0007101B))
                        )
                    )
                    .padding(horizontal = if (isTv) 22.dp else 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExtrasVlcAction("Back", onClick = onBack)
                Text(
                    title, color = Color.White, modifier = Modifier.weight(1f),
                    maxLines = 1, fontSize = if (isTv) 20.sp else 16.sp
                )
                Text(
                    "FIT", color = Color(0xFFB5EAFB), fontSize = 13.sp,
                    modifier = Modifier.testTag("extras-libvlc-aspect-fit")
                )
                ExtrasVlcAction(
                    if (showDiagnostics) "Hide stats" else "Stats",
                    modifier = Modifier.testTag("extras-libvlc-diagnostics-toggle"),
                    onClick = { showDiagnostics = !showDiagnostics }
                )
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .onFocusChanged { footerFocused = it.hasFocus }
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xDA06111C), Color(0xF506111C))
                        )
                    )
                    .padding(horizontal = if (isTv) 24.dp else 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (isTv) 12.dp else 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (controls.playRequested && status.startsWith("Buffering")) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp), color = Color(0xFF88DFFC),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        status, color = if (controls.failed) Color(0xFFFFD5CF) else Color.White,
                        fontSize = if (isTv) 18.sp else 14.sp,
                        maxLines = 2,
                        modifier = Modifier.testTag("extras-libvlc-playback-status")
                    )
                }

                val validDuration = controls.durationMs > 0L
                val currentFraction = scrubFraction ?: extrasVlcProgress(
                    controls.positionMs, controls.durationMs
                )
                Slider(
                    value = currentFraction.coerceIn(0f, 1f),
                    enabled = validDuration && controls.seekable && !controls.failed,
                    onValueChange = { scrubFraction = it },
                    onValueChangeFinished = {
                        val chosen = scrubFraction
                        scrubFraction = null
                        if (chosen != null && controls.durationMs > 0L) {
                            session.seekTo(
                                (chosen.toDouble() * controls.durationMs.toDouble()).toLong()
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                        .testTag("extras-libvlc-seek-bar")
                        .semantics { contentDescription = "Seek in video" }
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val displayedPosition = if (scrubFraction != null && validDuration) {
                        (scrubFraction!!.toDouble() * controls.durationMs.toDouble()).toLong()
                    } else controls.positionMs
                    Text(
                        extrasVlcTimeLabel(displayedPosition), color = Color.White,
                        fontSize = if (isTv) 17.sp else 14.sp
                    )
                    Text(
                        if (validDuration) extrasVlcTimeLabel(controls.durationMs) else "Time unavailable",
                        color = Color(0xFFD1E2F3),
                        fontSize = if (isTv) 17.sp else 14.sp
                    )
                }

                if (compact) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExtrasVlcAction(
                            "-10s", enabled = controls.seekable,
                            modifier = Modifier.weight(1f).testTag("extras-libvlc-rewind"),
                            onClick = { session.skip(-10_000L) }
                        )
                        ExtrasVlcAction(
                            if (controls.failed) "Retry" else if (controls.ended) "Replay"
                            else if (controls.playRequested) "Pause" else "Play",
                            emphasized = true,
                            modifier = Modifier.weight(1f).focusRequester(playFocus)
                                .testTag("extras-libvlc-play-pause"),
                            onClick = {
                                val starting = !controls.playRequested
                                when {
                                    controls.failed -> session.retry()
                                    controls.playRequested -> session.pause()
                                    else -> session.play()
                                }
                                // After starting playback, let the video surface
                                // hold focus so chrome can auto-hide on idle TV.
                                if (starting) runCatching { videoFocus.requestFocus() }
                            }
                        )
                        ExtrasVlcAction(
                            "+10s", enabled = controls.seekable,
                            modifier = Modifier.weight(1f).testTag("extras-libvlc-forward"),
                            onClick = { session.skip(10_000L) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExtrasVlcAction(
                            "Subtitles", enabled = media.captions.isNotEmpty(),
                            modifier = Modifier.weight(1f).testTag("extras-libvlc-subtitles"),
                            onClick = { onChooseSubtitles(extrasLibVlcHandoff(controls)) }
                        )
                        ExtrasVlcAction(
                            "Use Media3",
                            modifier = Modifier.weight(1f).testTag("extras-libvlc-use-media3"),
                            onClick = {
                                onUseMedia3(extrasLibVlcHandoff(controls))
                            }
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExtrasVlcAction(
                            "-10s", enabled = controls.seekable,
                            modifier = Modifier.weight(1f).testTag("extras-libvlc-rewind"),
                            onClick = { session.skip(-10_000L) }
                        )
                        ExtrasVlcAction(
                            if (controls.failed) "Retry" else if (controls.ended) "Replay"
                            else if (controls.playRequested) "Pause" else "Play",
                            emphasized = true,
                            modifier = Modifier.weight(1.15f).focusRequester(playFocus)
                                .testTag("extras-libvlc-play-pause"),
                            onClick = {
                                val starting = !controls.playRequested
                                when {
                                    controls.failed -> session.retry()
                                    controls.playRequested -> session.pause()
                                    else -> session.play()
                                }
                                // After starting playback, let the video surface
                                // hold focus so chrome can auto-hide on idle TV.
                                if (starting) runCatching { videoFocus.requestFocus() }
                            }
                        )
                        ExtrasVlcAction(
                            "+10s", enabled = controls.seekable,
                            modifier = Modifier.weight(1f).testTag("extras-libvlc-forward"),
                            onClick = { session.skip(10_000L) }
                        )
                        ExtrasVlcAction(
                            if (caption == null) "Subtitles" else "Captions ✓",
                            enabled = media.captions.isNotEmpty(),
                            modifier = Modifier.weight(1.2f).testTag("extras-libvlc-subtitles"),
                            onClick = { onChooseSubtitles(extrasLibVlcHandoff(controls)) }
                        )
                        ExtrasVlcAction(
                            "Use Media3",
                            modifier = Modifier.weight(1.2f).testTag("extras-libvlc-use-media3"),
                            onClick = {
                                onUseMedia3(extrasLibVlcHandoff(controls))
                            }
                        )
                    }
                }
                if (showDiagnostics) {
                    val range = controls.rangeMetrics
                    val timing = controls.timing
                    val pssLabel = processPssKiB?.let { "${it / 1024L} MiB" } ?: "unavailable"
                    Column(
                        Modifier.fillMaxWidth()
                            .background(Color(0xFF132332), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                            .testTag("extras-libvlc-diagnostics"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Debug diagnostics · this playback only · no URLs recorded",
                            color = Color(0xFFB5EAFB), fontSize = 14.sp
                        )
                        Text(
                            "Startup → native Playing: ${timing.startupToNativePlayingMs?.let { "${it} ms" } ?: "pending"}" +
                                "  ·  Seek → reported position: ${timing.lastSeekPositionMs?.let { "${it} ms" } ?: "no sample"}" +
                                "  (${timing.seekSamples} samples)" +
                                " · rejected: ${timing.seekRejectedCount}" +
                                " · superseded: ${timing.seekSupersededCount}" +
                                " · timed out: ${timing.seekTimeouts}" +
                                " · unchanged: ${timing.seekNoChangeCount}",
                            color = Color.White, fontSize = 14.sp
                        )
                        Text(
                            "Mean acknowledged seek: ${timing.averageSeekPositionMs?.let { "${it} ms" } ?: "no sample"}" +
                                " · longest: ${timing.longestSeekPositionMs?.let { "${it} ms" } ?: "no sample"}",
                            color = Color(0xFFD1E2F3), fontSize = 14.sp
                        )
                        Text(
                            if (range == null) "Range metrics unavailable"
                            else "HTTP windows: ${range.httpRequests} · failed/canceled: ${range.failedRequests}" +
                                " · cache hits: ${range.cacheHits} (${range.cacheHitPercent}%)" +
                                " · downloaded: ${range.downloadedBytes / 1024L} KiB",
                            color = Color.White, fontSize = 14.sp
                        )
                        Text(
                            if (range == null) "Window cache unavailable"
                            else "Window cache: ${range.retainedCacheBytes / 1024} KiB" +
                                " · longest HTTP fetch: ${range.longestFetchMillis} ms" +
                                " · process PSS snapshot: $pssLabel",
                            color = Color(0xFFD1E2F3), fontSize = 14.sp
                        )
                        Text(
                            "PSS covers the whole app. Seek metric reflects native position, not rendered frames.",
                            color = Color(0xFFA9BAC9), fontSize = 12.sp
                        )
                        ExtrasVlcAction(
                            "Sample PSS",
                            onClick = {
                                pssSampleRequest += 1
                            },
                            modifier = Modifier.testTag("extras-libvlc-diagnostics-sample")
                        )
                    }
                }
                if (isTv) {
                    Text(
                        "D-pad: select a control, or focus video and press Left/Right to seek 10 seconds",
                        color = Color(0xFFC7D6E4), fontSize = 13.sp,
                        maxLines = 2
                    )
                }
            }
            } // Keep both top and bottom chrome together.
        }
    }
}

/** High-contrast controls with visible TV focus, independent of light/dark theme. */
@Composable
private fun ExtrasVlcAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val focusColor = if (focused) Color(0xFF95E8FF) else Color.Transparent
    val surface = modifier.onFocusChanged { focused = it.isFocused }
        .border(if (focused) 3.dp else 1.dp, focusColor, shape)
        .heightIn(min = 52.dp)
    if (emphasized) {
        Button(
            onClick = onClick, enabled = enabled, modifier = surface,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF8EDFFF),
                contentColor = Color(0xFF082333),
                disabledContainerColor = Color(0xFF34505F),
                disabledContentColor = Color(0xFFC4D0DA)
            )
        ) {
            Text(label, maxLines = 1, fontSize = 15.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick, enabled = enabled, modifier = surface,
            shape = shape,
            border = BorderStroke(1.dp, Color(0xFF8EA9BA)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White,
                disabledContentColor = Color(0xFF93A3B0)
            )
        ) {
            Text(label, maxLines = 1, fontSize = 15.sp)
        }
    }
}
