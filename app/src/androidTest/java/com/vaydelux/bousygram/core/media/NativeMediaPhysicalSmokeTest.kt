package com.vaydelux.bousygram.core.media

import android.graphics.SurfaceTexture
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real libmpv/LibVLC timeline test with synthetic MKV/TS files over loopback
 * HTTP 206 byte ranges. Explicit physical-device opt-in; no Telegram account,
 * production release, or visible rendered-frame proof is implied.
 */
@RunWith(AndroidJUnit4::class)
class NativeMediaPhysicalSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun requirePhysicalArm() {
        assumeTrue("Enable with -e physicalNativeMedia true",
            InstrumentationRegistry.getArguments().getString("physicalNativeMedia") == "true")
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        assertTrue("Requires physical ARM ABI, got $abi",
            abi == "armeabi-v7a" || abi == "arm64-v8a")
        assertFalse("Emulators cannot qualify native ARM playback",
            Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("sdk_gphone", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true))
    }

    private fun fixture(name: String): File {
        val directory = requireNotNull(context.getExternalFilesDir("decoder-fixtures")) {
            "Fixture directory unavailable"
        }
        val file = File(directory, name)
        assertTrue("Missing synthetic fixture $name", file.isFile && file.length() > 2_000L)
        return file
    }

    private fun waitFor(
        engine: PlayerEngine,
        description: String,
        timeoutMs: Long = 35_000L,
        predicate: (PlayerEngine) -> Boolean
    ) {
        val until = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < until) {
            val status = engine.playbackState.value
            if (status is EnginePlaybackState.Error) {
                fail("$description native failure: " + status.message)
            }
            if (predicate(engine)) return
            SystemClock.sleep(150L)
        }
        fail("$description timed out: " + engine.playbackState.value +
            ", position=" + engine.currentPositionMs + ", wantsPlay=" + engine.playWhenReady)
    }

    private fun exercise(engineName: String, media: File, mime: String) {
        // Shared server sends real byte ranges, so seeking exercises a real
        // native demuxer rather than mocked JVM media playback.
        PlaybackDecoderFixtureInstrumentedTest.RangeFixtureServer(media).use { server ->
            val texture = SurfaceTexture(0)
            val surface = Surface(texture)
            var owned: PlayerEngine? = null
            try {
                assertTrue("Off-screen surface invalid", surface.isValid)
                val engine: PlayerEngine = when (engineName) {
                    "mpv" -> MpvPlayerEngine(context)
                    "vlc" -> LibVlcPlayerEngine(context)
                    else -> error("Unsupported native engine")
                }
                owned = engine
                engine.attachSurface(surface)
                engine.prepare(server.url(mime))
                engine.play()
                waitFor(engine, "$engineName initial playback") {
                    it.playWhenReady && it.isPlaying && it.currentPositionMs >= 450L
                }
                val seekable = engine.canSeek
                if (media.extension == "mkv") {
                    assertTrue("$engineName indexed MKV has no native seek map", seekable)
                }
                engine.pause()
                waitFor(engine, "$engineName manual Pause") {
                    !it.playWhenReady && !it.isPlaying
                }
                val paused = engine.currentPositionMs
                SystemClock.sleep(450L)
                assertFalse("Unexpected audio/video resume after Pause", engine.isPlaying)
                assertFalse("Native seek/rebuffer rearmed user Play", engine.playWhenReady)
                assertTrue("Native position moved during Pause",
                    engine.currentPositionMs - paused <= 900L)
                if (seekable) {
                    engine.seekTo(3_500L)
                    waitFor(engine, "$engineName paused seek") {
                        it.currentPositionMs in 2_900L..5_500L
                    }
                    assertFalse("Seek rearmed a paused engine", engine.playWhenReady)
                }
                val beforeResume = engine.currentPositionMs
                engine.play()
                waitFor(engine, "$engineName Play after Pause/seek") {
                    it.playWhenReady && it.isPlaying && it.currentPositionMs > beforeResume + 300L
                }
            } finally {
                owned?.let { engine ->
                    runCatching { engine.detachSurface() }
                    runCatching { engine.stop() }
                    runCatching { engine.release() }
                }
                surface.release()
                texture.release()
            }
        }
    }

    @Test
    fun syntheticMkvAndTsExerciseActualNativePauseSeekAndResume() {
        requirePhysicalArm()
        val fixtures = listOf(
            fixture("indexed.mkv") to "video/x-matroska",
            fixture("unindexed.ts") to "video/mp2t"
        )
        fixtures.forEach { (file, mime) ->
            // Sequential native ownership avoids unnecessary ARM32 peak RAM.
            exercise("mpv", file, mime)
            exercise("vlc", file, mime)
        }
        // Off-screen timeline progress is NOT proof of a rendered video frame.
    }
}
