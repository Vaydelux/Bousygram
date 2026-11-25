package com.vaydelux.bousygram.core.cast

import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.Surface
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaydelux.bousygram.core.account.AccountSnapshot
import java.io.File
import java.security.KeyStore
import java.security.Signature
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual single-player HLS qualification; no LAN socket or Telegram account. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class RollingHlsLocalReceiverInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val owner = AccountSnapshot(1, 1)

    private fun <T> main(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    @Test fun continuousHlsPlaybackPauseResumeAndRetireOnlyAfterRelease() {
        val context = instrumentation.targetContext
        val file = File(context.getExternalFilesDir("decoder-fixtures"), "rolling-receiver.mp4")
        assertTrue(file.isFile)
        val dir = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "local-hls-").toFile()
        val store = RollingHlsSegmentStore(dir, { true }, { true }, firstSequence = 1, capacitySegments = 2)
        val session = RollingHlsDeliverySession(owner, 7, store, { it == owner }, { true },
            SystemClock::elapsedRealtime)
        try {
            RollingHlsStreamingSink(store, session::active, RollingFmp4SampleVerifier(), { false },
                SystemClock::elapsedRealtime, session::installInitialization).use { sink ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(4093)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        sink.write(buffer, 0, n)
                    }
                }
                sink.finish()
                assertTrue(session.markComplete(2))
                assertEquals(2, store.snapshot().size)
                val texture = main { SurfaceTexture(10) }
                val surface = main { Surface(texture) }
                try {
                    val receiver = main { RollingHlsLocalReceiver(context, session, surface) }
                    try {
                        main { receiver.play() }
                        await(receiver) { it.playing && it.positionMs in 300..3000 }
                        main { receiver.pause() }
                        val paused = main { receiver.poll() }.positionMs
                        SystemClock.sleep(350)
                        assertFalse(main { receiver.poll() }.playing)
                        assertTrue(main { receiver.poll() }.positionMs - paused < 250)
                        assertEquals(2, store.snapshot().size)
                        assertFalse(receiver.retireAfterRelease())
                        main { receiver.play() }
                        await(receiver) { it.playing && it.positionMs in 6500..11000 }
                        await(receiver) { it.completed }
                        assertEquals(2, store.snapshot().size)
                    } finally { main { receiver.close() } }
                    assertTrue(receiver.retireAfterRelease())
                    assertEquals(listOf(2L), store.snapshot().map { it.sequence })
                } finally { main { surface.release(); texture.release() } }
            }
        } finally {
            session.close()
            assertTrue(dir.listFiles()?.isEmpty() == true)
            assertTrue(dir.delete())
        }
    }

    private fun await(receiver: RollingHlsLocalReceiver, predicate: (RollingHlsLocalReceiver.State) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 25_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate(main { receiver.poll() })) return
            SystemClock.sleep(50)
        }
        fail("Continuous receiver playback timed out")
    }

    @Test fun nonExportableIdentitySignsAndDeletesOnClose() {
        var account = owner
        val identity = RollingHlsKeystoreIdentity.create(owner) { it == account }
        val alias = identity.alias
        identity.use {
            assertEquals(32, it.fingerprint().size)
            val data = byteArrayOf(1, 2, 3)
            val signed = Signature.getInstance("SHA256withECDSA").run {
                initSign(it.privateKey()); update(data); sign()
            }
            assertTrue(Signature.getInstance("SHA256withECDSA").run {
                initVerify(it.certificate()); update(data); verify(signed)
            })
            account = AccountSnapshot(2, 2)
            assertThrows(IllegalStateException::class.java) { it.privateKey() }
        }
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias))
    }
}
