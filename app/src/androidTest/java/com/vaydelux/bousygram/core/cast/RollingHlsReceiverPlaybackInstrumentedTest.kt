package com.vaydelux.bousygram.core.cast

import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.SystemClock
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic-only decoder gate. No TDLib, real account, network or player UI.
 * Play independent verified init+media units on a real Media3 player. This
 * qualifies decoder completion, not seamless transitions or standard HLS. */
@RunWith(AndroidJUnit4::class)
class RollingHlsReceiverPlaybackInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun verifiedFragmentsRequireActualDecoderCompletionBeforeReceipt() {
        val context = instrumentation.targetContext
        val fixture = File(context.getExternalFilesDir("decoder-fixtures"), "rolling-receiver.mp4")
        assertTrue(fixture.isFile && fixture.length() in 1L..16L * 1024 * 1024)
        val directory = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "receiver-test-").toFile()
        try {
            fixture.inputStream().use { input ->
                val parser = RollingFmp4Framing(input) { true }
                val verifier = RollingFmp4SampleVerifier()
                val init = parser.readInit()
                assertTrue(verifier.verifyInit(init))
                var count = 0
                while (true) {
                    val fragment = parser.readFragment() ?: break
                    val timing = checkNotNull(verifier.verifyFragment(init, fragment.sequence, fragment.bytes))
                    val file = File(directory, "unit.mp4")
                    try {
                        file.outputStream().use { it.write(init); it.write(fragment.bytes) }
                        assertTrue(file.length() <= RollingHlsBudget.MAX_SEGMENT_BYTES + 512L * 1024)
                        play(file, fragment.sequence, timing.durationUs)
                    } finally {
                        assertTrue(file.delete())
                    }
                    count++
                    assertTrue(count <= 3)
                }
                assertEquals(2, count)
            }
            assertTrue(directory.listFiles()?.isEmpty() == true)
        } finally { directory.deleteRecursively() }
    }

    private fun play(file: File, sequence: Long, durationUs: Long) {
        val receipt = RollingHlsPlaybackReceipt { true }
        val result = AtomicReference<Long?>(null)
        val error = AtomicReference<Throwable?>(null)
        lateinit var player: ExoPlayer
        lateinit var surface: Surface
        lateinit var texture: SurfaceTexture
        instrumentation.runOnMainSync {
            assertTrue(receipt.begin(sequence, durationUs))
            texture = SurfaceTexture(10)
            surface = Surface(texture)
            player = ExoPlayer.Builder(instrumentation.targetContext).build()
            player.setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, false)
            player.setVideoSurface(surface)
            player.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() { receipt.renderedFirstFrame() }
                override fun onPlayerError(problem: PlaybackException) {
                    receipt.revoke()
                    error.set(problem)
                }
            })
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
            player.play()
        }
        try {
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while (SystemClock.elapsedRealtime() < deadline && result.get() == null && error.get() == null) {
                instrumentation.runOnMainSync {
                    val value = receipt.observe(player.currentPosition, player.isPlaying,
                        player.playbackState == Player.STATE_ENDED)
                    if (value != null) result.set(value)
                    else if (player.playbackState == Player.STATE_ENDED)
                        error.set(IOException("Decoder ended without valid playback receipt"))
                }
                SystemClock.sleep(50)
            }
            error.get()?.let { throw AssertionError("Fragment receiver decoder failed", it) }
            assertEquals(sequence, result.get())
        } finally {
            instrumentation.runOnMainSync {
                receipt.revoke()
                try { player.clearVideoSurface(); player.release() }
                finally { surface.release(); texture.release() }
            }
        }
    }
}
