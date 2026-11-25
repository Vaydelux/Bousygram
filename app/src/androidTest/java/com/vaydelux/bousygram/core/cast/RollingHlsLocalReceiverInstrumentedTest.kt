package com.vaydelux.bousygram.core.cast

import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.Surface
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaydelux.bousygram.core.account.AccountSnapshot
import java.io.File
import java.security.KeyStore
import java.security.Signature
import com.vaydelux.bousygram.core.tdlib.utils.QrCodeGenerator
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
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

    @Test fun continuousHlsPlaybackPauseResumeAndRetireOnlyAfterRelease() = qualifyPlayback(false)

    @Test fun pairedKeystoreTlsCarriesActualMedia3HlsPlayback() = qualifyPlayback(true)

    private fun qualifyPlayback(encrypted: Boolean) {
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
                    val keys = mutableListOf<RollingHlsKeystoreIdentity>()
                    var transport: RollingHlsTlsLoopback? = null
                    try {
                    if (encrypted) {
                        val tv = RollingHlsKeystoreIdentity.create(owner) { it == owner }.also(keys::add)
                        val sender = RollingHlsKeystoreIdentity.create(owner) { it == owner }.also(keys::add)
                        val pins = RollingHlsPeerPairing({ it == owner }, SystemClock::elapsedRealtime).use { pairing ->
                            pairing.issue(owner, tv.fingerprint())!!.use { offer ->
                                val proof = RollingHlsPeerPairing.proofFor(offer, sender.fingerprint())!!
                                try {
                                    val pending = pairing.receive(sender.fingerprint(), proof)!!
                                    pairing.confirm(pending, true)!!
                                } finally { proof.fill(0) }
                            }
                        }
                        assertThrows(IllegalStateException::class.java) {
                            RollingHlsPairedTls.create(owner, pins, sender, sender.certificate(), false) { it == owner }
                        }
                        val server = RollingHlsPairedTls.create(owner, pins, sender, tv.certificate(), false) { it == owner }
                        val client = RollingHlsPairedTls.create(owner, pins, tv, sender.certificate(), true) { it == owner }
                        // Negative pin check uses the genuine TLS trust context
                        // but an incorrect out-of-band expected server pin.
                        RollingHlsTlsLoopback(session, server,
                            RollingHlsPairedTls.Peer(client.context, ByteArray(32))).use { wrong ->
                            val source = wrong.factory().createDataSource()
                            try {
                                assertThrows(IOException::class.java) {
                                    source.open(DataSpec(RollingHlsLocalDataSource.playlistUri(session)))
                                }
                            } finally { source.close() }
                            assertEquals(0, wrong.diagnostics().responses)
                        }
                        transport = RollingHlsTlsLoopback(session, server, client)
                        // Isolate the handshake/data path from the decoder.
                        val uri = RollingHlsLocalDataSource.playlistUri(session)
                        val source = transport.factory().createDataSource()
                        try {
                            val length = source.open(DataSpec(uri))
                            assertTrue(length in 1L..4096L)
                            val bytes = ByteArrayOutputStream()
                            val chunk = ByteArray(128)
                            while (true) {
                                val n = source.read(chunk, 0, chunk.size)
                                if (n < 0) break
                                bytes.write(chunk, 0, n)
                            }
                            assertEquals(length, bytes.size().toLong())
                            assertTrue(bytes.toString("UTF-8").startsWith("#EXTM3U"))
                        } catch (error: Exception) {
                            throw AssertionError("TLS preflight: ${transport.diagnostics()}", error)
                        } finally { source.close() }
                    }
                    val factory = transport?.factory() ?: RollingHlsLocalDataSource.factory(session)
                    val receiver = main { RollingHlsLocalReceiver(context, session, surface, factory) }
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
                    assertFalse(receiver.retireAfterRelease())
                    assertEquals(listOf(2L), store.snapshot().map { it.sequence })
                    transport?.diagnostics()?.let { diagnostics ->
                        assertTrue("Expected playlist, init and two media transfers: $diagnostics",
                            diagnostics.handshakes >= 4 && diagnostics.responses >= 4)
                    }
                    } finally {
                        try { transport?.close() }
                        finally { keys.asReversed().forEach { it.close() } }
                    }
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

    @Test fun realQrRoundTripEnrollsKeystoreCertificateOnlyAfterLocalConsent() {
        var account = owner
        RollingHlsKeystoreIdentity.create(owner) { it == account }.use { tv ->
            RollingHlsKeystoreIdentity.create(owner) { it == account }.use { sender ->
                RollingHlsPeerPairing({ it == account }, SystemClock::elapsedRealtime).use { pairing ->
                    pairing.issue(owner, tv.fingerprint())!!.use { invitation ->
                        val encoded = checkNotNull(RollingHlsPairingQr.encode(invitation))
                        val bitmap = checkNotNull(QrCodeGenerator.generate(encoded, 480, 480))
                        val scanned = try {
                            val pixels = IntArray(bitmap.width * bitmap.height)
                            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                            try {
                                QRCodeReader().decode(BinaryBitmap(HybridBinarizer(
                                    RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
                            } finally { pixels.fill(0) }
                        } finally { bitmap.eraseColor(android.graphics.Color.BLACK); bitmap.recycle() }
                        assertTrue(encoded == scanned) // Never print the secret on failure.
                        RollingHlsPairingQr.decode(scanned)!!.use { offer ->
                            assertArrayEquals(tv.fingerprint(), offer.receiverPin)
                            val pin = sender.fingerprint()
                            val proof = RollingHlsPeerPairing.proofFor(offer, pin)!!
                            try {
                                val pending = pairing.receive(pin, proof)!!
                                assertNull(pairing.receive(pin, proof))
                                assertArrayEquals(pin, pending.senderPin())
                                val confirmed = pairing.confirm(pending, true)!!
                                assertArrayEquals(pin, confirmed.senderPin())
                                assertArrayEquals(tv.fingerprint(), confirmed.receiverPin())
                                assertNull(pairing.confirm(pending, true))
                            } finally { proof.fill(0) }
                        }
                        account = AccountSnapshot(2, 2)
                        assertNull(pairing.issue(owner, ByteArray(32) { 1 }))
                    }
                }
            }
        }
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
            // Conscrypt delegates prehashed ECDSA to the raw signature API.
            val digest = MessageDigest.getInstance("SHA-256").digest(data)
            val raw = Signature.getInstance("NONEwithECDSA").run {
                initSign(it.privateKey()); update(digest); sign()
            }
            assertTrue(Signature.getInstance("NONEwithECDSA").run {
                initVerify(it.certificate()); update(digest); verify(raw)
            })
            account = AccountSnapshot(2, 2)
            assertThrows(IllegalStateException::class.java) { it.privateKey() }
        }
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias))
    }
}
