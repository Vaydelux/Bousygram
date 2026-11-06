package com.vaydelux.bousygram.core.media

import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in ARM32/ARM64 real-decoder profiling with synthetic media.
 * Unlike the idle native soak, this creates a real Media3 AudioTrack/session.
 * Does NOT certify audible quality, Bluetooth routing, or libmpv/LibVLC.
 */
@RunWith(AndroidJUnit4::class)
class AudioPlayingPhysicalPerformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun <T> onMain(block: () -> T): T {
        var value: Any? = null
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try { value = block() } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun p95(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        return sorted[((sorted.size * 95 + 99) / 100 - 1).coerceIn(0, sorted.lastIndex)]
    }

    private fun memory() = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }

    @Test
    fun optInRealAudioPlaybackMeasuresEffectSwitchCpuAndPss() {
        assumeTrue("Explicit -e physicalAudioPlayingPerf true required",
            InstrumentationRegistry.getArguments().getString("physicalAudioPlayingPerf") == "true")
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        assertTrue("Physical ARM hardware required", abi == "arm64-v8a" || abi == "armeabi-v7a")
        val baselineOnly = InstrumentationRegistry.getArguments()
            .getString("audioPerfBaseline") == "true"
        val context = instrumentation.targetContext
        val fixture = File(context.getExternalFilesDir("decoder-fixtures"), "seek-control.mp4")
        assertTrue("Push the synthetic seek-control.mp4 fixture before running", fixture.isFile)
        assertTrue("Synthetic decoder fixture missing bytes", fixture.length() > 2_000L)

        val startPss = memory()
        val cpuStart = Process.getElapsedCpuTime()
        val wallStart = SystemClock.elapsedRealtime()
        val applyTimesMs = mutableListOf<Long>()
        val nativeEffectsApplied = AtomicInteger(0)
        var validSessionSeen = false
        var positionAdvanced = false
        var engine: ExoPlayerEngine? = null

        try {
            PlaybackDecoderFixtureInstrumentedTest.RangeFixtureServer(fixture).use { server ->
                engine = onMain {
                    ExoPlayerEngine.create(context).also { created ->
                        // No foreground Activity: avoid headless focus-policy interference.
                        created.player.setAudioAttributes(
                            androidx.media3.common.AudioAttributes.Builder()
                                .setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                                .build(),
                            false
                        )
                        created.prepare(server.url("video/mp4"))
                        created.play()
                    }
                }
                val active = requireNotNull(engine)
                val readyDeadline = SystemClock.elapsedRealtime() + 12_000L
                var initialPosition = 0L
                var started = false
                while (SystemClock.elapsedRealtime() < readyDeadline) {
                    val state = onMain {
                        Triple(active.playbackState.value, active.isPlaying,
                            active.player.audioSessionId)
                    }
                    if (state.third > 0) validSessionSeen = true
                    if (state.first is EnginePlaybackState.Error) {
                        throw AssertionError("Physical test media decoder reported an error")
                    }
                    if (state.first is EnginePlaybackState.Ready && state.second &&
                        state.third > 0) {
                        initialPosition = onMain { active.currentPositionMs }
                        started = true
                        break
                    }
                    SystemClock.sleep(120L)
                }
                assertTrue("Physical Media3 player never reached READY with a valid audio session", started)
                val gains = List(10) { index -> if (index % 2 == 0) 2f else -2f }

                repeat(30) { cycle ->
                    val profile = if (baselineOnly) AudioProcessingProfile() else when (cycle % 6) {
                        0 -> AudioProcessingProfile()
                        1 -> AudioProcessingProfile(preset = AudioEqualizerPreset.DIALOGUE,
                            smartVolume = SmartVolumeMode.NIGHT)
                        2 -> AudioProcessingProfile(preset = AudioEqualizerPreset.CUSTOM,
                            customGainsDb = gains)
                        3 -> AudioProcessingProfile(preset = AudioEqualizerPreset.CUSTOM,
                            customGainsDb = gains.map { -it },
                            smartVolume = SmartVolumeMode.DIALOGUE_BOOST)
                        4 -> AudioProcessingProfile(preset = AudioEqualizerPreset.CINEMA,
                            smartVolume = SmartVolumeMode.AUTO_LEVEL)
                        else -> AudioProcessingProfile(smartVolume = SmartVolumeMode.NIGHT)
                    }
                    val startedAt = SystemClock.elapsedRealtimeNanos()
                    val statuses = onMain {
                        active.applyAudioProcessing(profile)
                        Triple(active.audioEqualizerStatus.value,
                            active.smartVolumeStatus.value,
                            active.player.audioSessionId)
                    }
                    val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000L
                    applyTimesMs.add(elapsedMs)
                    if (statuses.first == AudioEffectStatus.APPLIED ||
                        statuses.second == AudioEffectStatus.APPLIED) {
                        nativeEffectsApplied.incrementAndGet()
                    }
                    if (statuses.third > 0) validSessionSeen = true
                    if ((cycle + 1) % 5 == 0) {
                        val current = memory()
                        Log.i("BousyAudioPerf",
                            "cycle=${cycle + 1}/30 baselineOnly=$baselineOnly " +
                                "process64bit=${Process.is64Bit()} " +
                                "pssKiB=${current.totalPss} nativePssKiB=${current.nativePss} " +
                                "cpuMs=${Process.getElapsedCpuTime() - cpuStart} " +
                                "eq=${statuses.first.name} volume=${statuses.second.name}")
                    }
                    // Allow the actual decoder/audio track to progress between updates.
                    SystemClock.sleep(140L)
                }
                positionAdvanced = onMain { active.currentPositionMs > initialPosition + 300L }
            }
        } finally {
            engine?.let { active ->
                onMain {
                    runCatching { active.stop() }
                    active.release()
                }
            }
            val finalPss = memory()
            Log.i("BousyAudioPerf",
                "final baselineOnly=$baselineOnly process64bit=${Process.is64Bit()} " +
                    "cycles=${applyTimesMs.size} " +
                    "nativeEffectsApplied=${nativeEffectsApplied.get()} " +
                    "p95ApplyMs=${p95(applyTimesMs)} " +
                    "maxApplyMs=${applyTimesMs.maxOrNull() ?: 0L} " +
                    "pssDeltaKiB=${finalPss.totalPss - startPss.totalPss} " +
                    "nativePssDeltaKiB=${finalPss.nativePss - startPss.nativePss} " +
                    "cpuDeltaMs=${Process.getElapsedCpuTime() - cpuStart} " +
                    "wallDeltaMs=${SystemClock.elapsedRealtime() - wallStart}")
        }

        assertTrue("A real Media3 audio session was not observed", validSessionSeen)
        assertTrue("Decoder timeline did not advance during EQ/Smart Volume changes",
            positionAdvanced)
        assertTrue("The 30-profile sound processing sequence did not complete",
            applyTimesMs.size == 30)
        // PSS and CPU are measurements, not arbitrary hardcoded pass thresholds.
        // APPLIED depends on OEM route capabilities; do not fabricate availability.
    }
}
