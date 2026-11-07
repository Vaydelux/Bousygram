package com.vaydelux.bousygram.presentation.extras

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.concurrent.atomic.AtomicBoolean

/** Only Extras uses this decoder; Telegram's loopback-only LibVlcPlayerEngine is untouched. */
internal class ExtrasLibVlcSession(
    context: Context,
    private val media: InspectedMedia,
    private val subtitle: CaptionCandidate?,
    private val onStatus: (String) -> Unit
) {
    private val released = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (!released.get()) handler.post { pause() }
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

    init {
        player.setEventListener { event ->
            val text = when (event.type) {
                MediaPlayer.Event.Playing -> "Playing in LibVLC"
                MediaPlayer.Event.Buffering -> "Buffering"
                MediaPlayer.Event.Paused -> "Paused"
                MediaPlayer.Event.EncounteredError -> "LibVLC cannot open this stream; return to WebView"
                MediaPlayer.Event.EndReached -> "Playback finished"
                else -> null
            }
            if (text != null) handler.post { if (!released.get()) onStatus(text) }
        }
    }

    fun attach(layout: VLCVideoLayout) {
        if (released.get() || attached) return
        require(MediaForgeUrlPolicy.safeHttpsUrl(media.stream.url) != null) {
            "Only validated HTTPS sources can enter the Extras player"
        }
        player.attachViews(layout, null, true, false)
        attached = true
        player.setAspectRatio(null)
        player.setScale(0f) // Natural fit rather than zoom/crop.
        if (!initialized) {
            val vlcMedia = Media(libVlc, Uri.parse(media.stream.url))
            try {
                vlcMedia.addOption(":network-caching=500")
                player.media = vlcMedia
                initialized = true
            } finally {
                vlcMedia.release()
            }
            subtitle?.takeIf { MediaForgeUrlPolicy.safeHttpsUrl(it.url) != null }?.let {
                val accepted = runCatching {
                    player.addSlave(IMedia.Slave.Type.Subtitle, Uri.parse(it.url), true)
                }.getOrDefault(false)
                if (!accepted) onStatus("External subtitles unavailable in LibVLC")
            }
        }
        play()
    }

    fun play() {
        if (released.get() || !initialized) return
        if (!hasAudioFocus) {
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                onStatus("Audio output unavailable")
                return
            }
            hasAudioFocus = true
        }
        runCatching { player.play() }.onFailure { onStatus("LibVLC could not start playback") }
    }

    fun pause() {
        if (released.get()) return
        if (runCatching { player.isPlaying }.getOrDefault(false)) {
            runCatching { player.pause() }
        }
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
        handler.removeCallbacksAndMessages(null)
        runCatching { player.setEventListener(null) }
        runCatching { player.stop() }
        runCatching { player.detachViews() }
        attached = false
        runCatching { player.release() }
        runCatching { libVlc.release() }
        releaseAudioFocus()
    }
}

@Composable
internal fun ExtrasLibVlcPlayer(
    media: InspectedMedia,
    caption: CaptionCandidate?,
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember(media.stream.url) { mutableStateOf("Preparing LibVLC…") }
    // LibVLC initialization can fail on unsupported devices/ABIs: show recovery UI.
    val sessionResult = remember(media.stream.url, caption?.url) {
        runCatching { ExtrasLibVlcSession(context, media, caption) { status = it } }
    }
    val session = sessionResult.getOrNull()
    DisposableEffect(session, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) session?.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
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
            Text(
                "LibVLC is unavailable on this device. Return to WebView or select Media3.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
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
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = session::play) { Text("Play") }
                OutlinedButton(onClick = session::pause) { Text("Pause") }
                OutlinedButton(onClick = { session.skip(-10_000) }) { Text("-10s") }
                Button(onClick = { session.skip(10_000) }) { Text("+10s") }
            }
        }
    }
}
