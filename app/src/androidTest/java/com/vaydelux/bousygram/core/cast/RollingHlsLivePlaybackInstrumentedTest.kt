package com.vaydelux.bousygram.core.cast

import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.Surface
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real-time synthetic live playback, one actual HLS player and concurrent
 * paced verified producer. No network, real Telegram account or private input.
 * Observes player progress, not exact video/audio output at physical display. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class RollingHlsLivePlaybackInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun <T> main(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    @Test fun livePlaylistRefreshAndReclamationDuringContinuousPlayback() {
        val context = instrumentation.targetContext
        val fixture = File(context.getExternalFilesDir("decoder-fixtures"), "rolling-receiver-live.mp4")
        assertTrue(fixture.isFile && fixture.length() in 1L..16L * 1024 * 1024)
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "live-receiver-").toFile()
        val allowed = AtomicBoolean(true)
        val failure = AtomicReference<Throwable?>(null)
        val published = AtomicLong()
        val peakBytes = AtomicLong()
        val peakSlots = AtomicInteger()
        val reclaimed = AtomicBoolean()
        val producerDone = AtomicBoolean()
        val clock = { SystemClock.elapsedRealtime() * 1000L }
        val store = RollingHlsLiveFileStore(directory, 6_000_000, 8, 32L * 1024 * 1024,
            clock, allowed::get, { true })
        val producer = RollingHlsLiveProducer(RollingHlsLiveVerifiedInput(fixture.inputStream(), allowed::get),
            store, 6_000_000, clock, allowed::get)
        val transport = RollingHlsLiveLocalTransport(store)
        val worker = Thread({
            try {
                while (allowed.get()) {
                    when (val step = producer.step()) {
                        is RollingHlsLiveProducer.Step.Published -> {
                            val count = store.retainedCount()
                            val bytes = store.retainedBytes()
                            check(count <= 8 && bytes <= 32L * 1024 * 1024)
                            peakSlots.updateAndGet { maxOf(it, count) }
                            peakBytes.updateAndGet { maxOf(it, bytes) }
                            if (count.toLong() < step.sequence) reclaimed.set(true)
                            published.set(step.sequence)
                        }
                        is RollingHlsLiveProducer.Step.Waiting ->
                            Thread.sleep(((step.retryAfterUs + 999) / 1000).coerceIn(1, 100))
                        RollingHlsLiveProducer.Step.End -> {
                            producerDone.set(true)
                            return@Thread
                        }
                    }
                }
            } catch (error: Throwable) {
                if (allowed.get()) failure.set(error)
            }
        }, "synthetic-live-producer")
        var player: ExoPlayer? = null
        var texture: SurfaceTexture? = null
        var surface: Surface? = null
        val rendered = AtomicBoolean()
        val seek = AtomicBoolean()
        try {
            worker.start()
            val startup = SystemClock.elapsedRealtime() + 25_000
            while (published.get() < 3 && SystemClock.elapsedRealtime() < startup) {
                failure.get()?.let { throw AssertionError("Live producer failed", it) }
                SystemClock.sleep(50)
            }
            assertEquals(3L, published.get())
            val playback = main {
                texture = SurfaceTexture(10)
                surface = Surface(checkNotNull(texture))
                ExoPlayer.Builder(context).build().also { engine ->
                    player = engine
                    engine.setAudioAttributes(AudioAttributes.DEFAULT, false)
                    engine.setVideoSurface(surface)
                    engine.addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { rendered.set(true) }
                        override fun onPlayerError(error: PlaybackException) { failure.set(error) }
                        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo, reason: Int) {
                            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) seek.set(true)
                        }
                    })
                    val item = MediaItem.Builder().setUri(transport.uri())
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .setLiveConfiguration(MediaItem.LiveConfiguration.Builder()
                            .setTargetOffsetMs(18_000).setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build())
                        .build()
                    engine.setMediaSource(HlsMediaSource.Factory(transport.factory()).createMediaSource(item))
                    engine.prepare()
                    engine.play()
                }
            }
            val deadline = SystemClock.elapsedRealtime() + 115_000
            var playingMs = 0L
            var lastTick = SystemClock.elapsedRealtime()
            var paused = false
            var ended = false
            while (SystemClock.elapsedRealtime() < deadline) {
                failure.get()?.let { throw AssertionError("Live playback failed", it) }
                val state = main { playback.isPlaying to playback.playbackState }
                val now = SystemClock.elapsedRealtime()
                if (state.first && rendered.get()) playingMs += (now - lastTick).coerceIn(0, 250)
                lastTick = now
                if (!paused && playingMs >= 10_000) {
                    main { playback.pause() }
                    SystemClock.sleep(1000)
                    assertFalse(main { playback.isPlaying })
                    main { playback.play() }
                    lastTick = SystemClock.elapsedRealtime()
                    paused = true
                }
                if (state.second == Player.STATE_ENDED) { ended = true; break }
                SystemClock.sleep(50)
            }
            failure.get()?.let { throw AssertionError("Live playback failed", it) }
            val stats = transport.diagnostics()
            assertTrue("Live decoder did not finish: $stats", ended && rendered.get() && paused && !seek.get())
            assertTrue("Insufficient observed playing time: $playingMs", playingMs >= 45_000)
            assertTrue(producerDone.get() && published.get() == 12L)
            assertTrue("Missing refreshed playlists: $stats", stats.playlists >= 8 && stats.endListRead)
            assertTrue("Missing final media: $stats", stats.newestCompletedSequence == 12L &&
                stats.completedMediaReads >= 8)
            assertTrue("No physical reclamation during playback", reclaimed.get())
            assertTrue(peakSlots.get() in 1..8 && peakBytes.get() in 1L..32L * 1024 * 1024)
            assertNull(store.open(1))
            println("LIVE_MEDIA3_PASS fragments=12 playing_ms=$playingMs peak_bytes=${peakBytes.get()} peak_slots=${peakSlots.get()} $stats")
        } finally {
            allowed.set(false)
            try {
                main {
                    try { player?.clearVideoSurface(); player?.release() }
                    finally { surface?.release(); texture?.release() }
                }
            } finally {
                worker.interrupt()
                worker.join(5_000)
                check(!worker.isAlive) { "Live producer did not exit" }
                producer.close()
                check(directory.listFiles()?.isEmpty() == true)
                check(directory.delete())
            }
        }
    }
}
