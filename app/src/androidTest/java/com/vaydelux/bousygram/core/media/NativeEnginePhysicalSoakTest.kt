package com.vaydelux.bousygram.core.media

import android.graphics.SurfaceTexture
import android.os.Debug
import android.util.Log
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in, physical-device-only native lifecycle stress.
 *
 * This is intentionally excluded from the emulator smoke suite: a JVM or x86
 * emulator cannot qualify the ARM32/ARM64 native player and shared C++ runtime.
 * No account, Telegram content, or stream URL is used by this test.
 */
@RunWith(AndroidJUnit4::class)
class NativeEnginePhysicalSoakTest {
    @Test
    fun repeatedMpvAndLibVlcInitialization_releasesBothNativeRuntimes() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Run on ARM hardware with -e physicalNativeSoak true; not part of regular CI",
            args.getString("physicalNativeSoak") == "true"
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        assertTrue("Expected ARM device ABI, found $abi", abi == "armeabi-v7a" || abi == "arm64-v8a")

        // PSS values are only observations, not a hardcoded leak threshold.
        // Log no account, file ID, URI, token, or device serial.
        val baseline = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        Log.i("BousyNativeSoak", "Initial app PSS KiB: ${baseline.totalPss}")
        // Alternating initialization catches resource ownership and shared-runtime
        // faults that isolated unit mocks and one-time native load checks miss.
        repeat(30) { cycle ->
            val mpv = MpvPlayerEngine(context)
            try {
                assertEquals("mpv", mpv.engineName)
                mpv.pause()
                mpv.stop()
                mpv.detachSurface()
            } finally {
                mpv.release()
                mpv.release() // Release must be idempotent.
            }

            val vlc = LibVlcPlayerEngine(context)
            try {
                assertEquals("LibVLC", vlc.engineName)
                vlc.pause()
                vlc.stop()
                vlc.detachSurface()
            } finally {
                vlc.release()
                vlc.release()
            }
            if ((cycle + 1) % 5 == 0) {
                val sample = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                Log.i("BousyNativeSoak", "Native init/release cycle ${cycle + 1}/30, app PSS KiB: ${sample.totalPss}, native PSS KiB: ${sample.nativePss}")
            }
        }
    }

    @Test
    fun optInPhysicalAudioEffectsStressMeasuresPssAndCpuButNotAcousticOutput() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Explicit -e physicalNativeAudioSoak true on ARM TV is required",
            args.getString("physicalNativeAudioSoak") == "true"
        )
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        assertTrue("Physical ARM32/ARM64 required", abi == "armeabi-v7a" || abi == "arm64-v8a")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val initial = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val cpuStartMs = android.os.Process.getElapsedCpuTime()
        Log.i("BousyNativeAudio", "Initial PSS KiB=${initial.totalPss} native=${initial.nativePss}")
        val modes = listOf(
            SmartVolumeMode.OFF, SmartVolumeMode.NIGHT,
            SmartVolumeMode.DIALOGUE_BOOST, SmartVolumeMode.AUTO_LEVEL
        )
        repeat(30) { cycle ->
            val profile = AudioProcessingProfile(
                preset = if (cycle % 2 == 0) AudioEqualizerPreset.CUSTOM
                    else AudioEqualizerPreset.DIALOGUE,
                customGainsDb = List(10) { index -> if (index % 2 == 0) 2f else -2f },
                smartVolume = modes[cycle % modes.size]
            )
            // One native engine at a time: no simultaneous decoder peak allocations.
            val mpv = MpvPlayerEngine(context)
            try {
                mpv.applyAudioProcessing(profile)
                mpv.applyAudioProcessing(AudioProcessingProfile()) // explicit bypass
            } finally {
                mpv.stop()
                mpv.release()
                mpv.release()
                // No effect may be reactivated by a late UI or settings update.
                assertFalse(mpv.applyAudioProcessing(profile))
                assertEquals(AudioEffectStatus.BYPASSED, mpv.audioEqualizerStatus.value)
                assertEquals(AudioEffectStatus.BYPASSED, mpv.smartVolumeStatus.value)
            }
            val vlc = LibVlcPlayerEngine(context)
            try {
                vlc.applyAudioProcessing(profile)
                vlc.applyAudioProcessing(AudioProcessingProfile())
            } finally {
                vlc.stop()
                vlc.release()
                vlc.release()
                // No effect may be reactivated by a late UI or settings update.
                assertFalse(vlc.applyAudioProcessing(profile))
                assertEquals(AudioEffectStatus.BYPASSED, vlc.audioEqualizerStatus.value)
                assertEquals(AudioEffectStatus.BYPASSED, vlc.smartVolumeStatus.value)
            }
            val exo = ExoPlayerEngine.create(context)
            try {
                exo.applyAudioProcessing(profile)
                exo.applyAudioProcessing(AudioProcessingProfile())
            } finally {
                exo.stop()
                exo.release()
                exo.release()
                // No effect may be reactivated by a late UI or settings update.
                assertFalse(exo.applyAudioProcessing(profile))
                assertEquals(AudioEffectStatus.BYPASSED, exo.audioEqualizerStatus.value)
                assertEquals(AudioEffectStatus.BYPASSED, exo.smartVolumeStatus.value)
            }
            if ((cycle + 1) % 5 == 0) {
                val mem = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                Log.i("BousyNativeAudio", "Audio lifecycle cycle=${cycle + 1}/30 " +
                    "PSS KiB=${mem.totalPss} native=${mem.nativePss} " +
                    "processCpuMs=${android.os.Process.getElapsedCpuTime() - cpuStartMs}")
            }
        }
        val final = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        Log.i("BousyNativeAudio", "Audio final PSS KiB=${final.totalPss} " +
            "deltaKiB=${final.totalPss - initial.totalPss} " +
            "cpuMs=${android.os.Process.getElapsedCpuTime() - cpuStartMs}")
        // No assertion of audio effect audible output: decoders are idle without a stream.
    }

    @Test
    fun optInPhysicalSurfaceAttachDetachSmokeWithoutPlaybackClaims() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires explicit physicalNativeSurface true", args.getString("physicalNativeSurface") == "true")
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        assertTrue("ARM32 or ARM64 physical device required", abi == "armeabi-v7a" || abi == "arm64-v8a")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val texture = SurfaceTexture(0)
        val surface = Surface(texture)
        try {
            assertTrue("Off-screen native test surface is invalid", surface.isValid)
            // Sequentially own a single native player to avoid ARM32 peak-RAM
            // pressure from two simultaneous native decode engines.
            repeat(10) {
                val mpv = MpvPlayerEngine(context)
                try {
                    mpv.attachSurface(surface)
                    mpv.detachSurface()
                    mpv.detachSurface()
                } finally {
                    mpv.release()
                }
                val vlc = LibVlcPlayerEngine(context)
                try {
                    vlc.attachSurface(surface)
                    vlc.detachSurface()
                    vlc.detachSurface()
                    assertTrue("LibVLC surface attachment was rejected", vlc.playbackState.value !is EnginePlaybackState.Error)
                } finally {
                    vlc.release()
                }
            }
        } finally {
            surface.release()
            texture.release()
        }
        // No real video frames or Telegram authorization are verified here.
    }
}
