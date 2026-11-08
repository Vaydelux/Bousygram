package com.vaydelux.bousygram.presentation.extras

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
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
import com.vaydelux.bousygram.core.media.LibVlcUnexpectedPauseAction
import com.vaydelux.bousygram.core.media.libVlcUnexpectedPauseAction
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.isActive

/** Only Extras uses this decoder; Telegram's loopback-only LibVlcPlayerEngine is untouched. */
internal class ExtrasLibVlcSession(
    context: Context,
    private val media: InspectedMedia,
    private val subtitle: CaptionCandidate?,
    private val guarded: MediaForgeGuardedProgressiveSource,
    private val onStatus: (String) -> Unit
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

    val wantsPlayback: Boolean get() = playbackRequested
    val wantsResumeAfterFocus: Boolean get() = focusRecovery.hasPendingResume

    fun setForeground(active: Boolean) {
        inForeground = active
    }

    init {
        player.setEventListener { event ->
            if (released.get()) return@setEventListener
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    if (playbackRequested) postStatus("Playing in LibVLC")
                    else {
                        // Native Playing can arrive AFTER a user Pause while the
                        // file was buffering. Do not resurrect playback.
                        enforceNativePauseAfterDelayedStart()
                    }
                }
                MediaPlayer.Event.Buffering -> {
                    if (playbackRequested) postStatus("Buffering")
                    else enforceNativePauseAfterDelayedStart()
                }
                MediaPlayer.Event.Paused -> {
                    when (libVlcUnexpectedPauseAction(
                        playRequested = playbackRequested,
                        prepared = initialized,
                        stoppedByOwner = released.get(),
                        alreadyRetried = unexpectedPauseRetries.get()
                    )) {
                        LibVlcUnexpectedPauseAction.IGNORE -> postStatus("Paused")
                        LibVlcUnexpectedPauseAction.RETRY -> {
                            unexpectedPauseRetries.incrementAndGet()
                            handler.post {
                                if (!released.get() && initialized && playbackRequested) {
                                    runCatching { player.play() }.onFailure {
                                        playbackRequested = false
                                        releaseAudioFocus()
                                        onStatus("LibVLC could not resume; return to WebView")
                                    }
                                }
                            }
                        }
                        LibVlcUnexpectedPauseAction.FAIL -> {
                            playbackRequested = false
                            handler.post {
                                if (!released.get()) {
                                    releaseAudioFocus()
                                    onStatus("LibVLC paused repeatedly; return to WebView")
                                }
                            }
                        }
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    playbackRequested = false
                    handler.post { focusRecovery.cancel() }
                    handler.post {
                        if (!released.get()) {
                            releaseAudioFocus()
                            onStatus("LibVLC cannot open this stream; return to WebView")
                        }
                    }
                }
                MediaPlayer.Event.EndReached -> {
                    playbackRequested = false
                    handler.post { focusRecovery.cancel() }
                    handler.post {
                        if (!released.get()) {
                            releaseAudioFocus()
                            onStatus("Playback finished")
                        }
                    }
                }
            }
        }
    }

    private fun postStatus(message: String) {
        handler.post { if (!released.get()) onStatus(message) }
    }

    private fun enforceNativePauseAfterDelayedStart() {
        handler.post {
            if (!released.get() && !playbackRequested) {
                runCatching {
                    if (player.isPlaying) player.pause()
                }
                onStatus("Paused")
            }
        }
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
        play()
    }

    fun play() {
        if (released.get() || !initialized || !inForeground) return
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
        unexpectedPauseRetries.set(0)
        playbackRequested = true
        runCatching { player.play() }.onFailure {
            playbackRequested = false
            releaseAudioFocus()
            onStatus("LibVLC could not start playback")
        }
    }

    /**
     * Focus loss suppresses decoder output without abandoning the focus
     * request. Android can now deliver GAIN; a regular Pause must NOT do this.
     */
    private fun pauseForAudioFocus() {
        playbackRequested = false
        unexpectedPauseRetries.set(0)
        if (released.get()) return
        if (runCatching { player.isPlaying }.getOrDefault(false)) {
            runCatching { player.pause() }
        }
        onStatus("Paused for audio focus")
    }

    fun pause() {
        focusRecovery.cancel()
        pauseForAudioFocus()
        releaseAudioFocus()
    }

    private fun releaseAudioFocus() {
        if (!hasAudioFocus) return
        hasAudioFocus = false
        runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
    }

    fun skip(deltaMs: Long) {
        if (released.get()) return
        runCatching {
            if (player.isSeekable) {
                val position = (player.time + deltaMs).coerceAtLeast(0L)
                val duration = player.length
                player.setTime(if (duration > 0L) position.coerceAtMost(duration) else position)
            }
        }
    }

    fun detach() {
        if (released.get() || !attached) return
        runCatching { player.detachViews() }
        attached = false
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
    onBack: () -> Unit,
    onUseMedia3: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember(media.stream.url) { mutableStateOf("Preparing LibVLC…") }
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
    // LibVLC initialization can fail on devices without a compatible ABI.
    val sessionResult = remember(source, media.stream.url, caption?.url) {
        source?.let { guardedSource ->
            runCatching {
                ExtrasLibVlcSession(context, media, caption, guardedSource) { status = it }
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
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBack) { Text("Back to WebView") }
            Text(title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
        }
        if (session == null) {
            if (initError == null && source == null) {
                CircularProgressIndicator(Modifier.padding(16.dp))
                Text("Preparing guarded range playback…", color = Color.White)
            } else {
                Text(
                    initError ?: "LibVLC is unavailable on this device. Use Media3.",
                    color = Color(0xFFFFC7C7),
                    modifier = Modifier.padding(16.dp)
                )
                Button(
                    onClick = onUseMedia3,
                    modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 52.dp)
                ) { Text("Continue with Media3") }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    VLCVideoLayout(ctx).also {
                        runCatching { session.attach(it) }.onFailure {
                            status = "LibVLC could not prepare this source."
                        }
                    }
                },
                onRelease = { session.detach() },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Text(status, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp))
            Row(
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = session::play, modifier = Modifier.heightIn(min = 52.dp)
                ) { Text("Play") }
                OutlinedButton(
                    onClick = session::pause, modifier = Modifier.heightIn(min = 52.dp)
                ) { Text("Pause") }
                OutlinedButton(
                    onClick = { session.skip(-10_000) }, modifier = Modifier.heightIn(min = 52.dp)
                ) { Text("-10s") }
                OutlinedButton(
                    onClick = { session.skip(10_000) }, modifier = Modifier.heightIn(min = 52.dp)
                ) { Text("+10s") }
                // Release the current native surface before opening Media3.
                // The app disposes this LibVLC composition and its guarded FD.
                Button(
                    onClick = onUseMedia3, modifier = Modifier.heightIn(min = 52.dp)
                ) { Text("Try Media3") }
            }
        }
    }
}
