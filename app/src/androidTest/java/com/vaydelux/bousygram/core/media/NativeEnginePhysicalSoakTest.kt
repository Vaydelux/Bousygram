package com.vaydelux.bousygram.core.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
            assertTrue("Native cycle $cycle finished", cycle >= 0)
        }
    }
}
