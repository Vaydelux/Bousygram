package com.vaydelux.bousygram.core.cast

import android.os.SystemClock
import androidx.media3.common.util.UnstableApi
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLSocket

/** One approved session carries a bounded synthetic live stream. Control
 * socket authenticates the separate sender endpoint/authority and terminal
 * receipt. Receipt is a trusted receiver assertion, not playback attestation.
 * No remote delete, Telegram input, public URL or permanent pairing. */
@UnstableApi
internal object RollingHlsLanLiveBridge {
    private const val MAGIC = 0x42474d31
    private const val END = 0x42474d32

    private fun handshake(socket: SSLSocket, peer: RollingHlsPairedTls.Peer, client: Boolean) {
        socket.useClientMode = client
        socket.enabledProtocols = arrayOf("TLSv1.3")
        if (!client) socket.needClientAuth = true
        socket.soTimeout = 5000
        socket.startHandshake()
        RollingHlsPeerIdentity.requirePeer(socket.session, peer.pin())
    }

    fun send(control: SSLSocket, peer: RollingHlsPairedTls.Peer, local: Inet4Address,
             remote: Inet4Address, cache: File, input: InputStream, permitted: () -> Boolean,
             progress: (Long) -> Unit = {}) {
        input.use { source ->
            handshake(control, peer, true)
            check(permitted())
            val dir = Files.createTempDirectory(cache.toPath(), "lan-live-").toFile()
            val allowed = AtomicBoolean(true)
            fun active() = allowed.get() && permitted()
            val clock = { SystemClock.elapsedRealtime() * 1000L }
            val store = RollingHlsLiveFileStore(dir, 6_000_000, 8, 32L * 1024 * 1024,
                clock, ::active, { additional -> dir.usableSpace >= 500L * 1024 * 1024 + additional })
            val verified = RollingHlsLiveVerifiedInput(source, ::active)
            val bounded = object : RollingHlsLiveProducer.VerifiedInput {
                private var count = 0
                override fun initialization() = verified.initialization()
                override fun next(): RollingHlsLiveProducer.Unit? {
                    val unit = verified.next()
                    if (unit == null) { check(count == 12); return null }
                    if (++count > 12) { unit.bytes.fill(0); throw IOException("Test fragment limit") }
                    return unit
                }
                override fun close() = verified.close()
            }
            val producer = RollingHlsLiveProducer(bounded, store, 6_000_000, clock, ::active)
            val published = AtomicLong()
            val done = AtomicBoolean()
            val failure = AtomicReference<Throwable?>(null)
            val resources = RollingHlsLiveLocalTransport(store)
            val worker = Thread({
                try {
                    while (active()) {
                        when (val step = producer.step()) {
                            is RollingHlsLiveProducer.Step.Published -> {
                                published.set(step.sequence)
                                progress(step.sequence)
                            }
                            is RollingHlsLiveProducer.Step.Waiting -> Thread.sleep(((step.retryAfterUs + 999) / 1000).coerceIn(1, 100))
                            RollingHlsLiveProducer.Step.End -> { done.set(true); return@Thread }
                        }
                    }
                } catch (error: Throwable) {
                    if (active()) { failure.set(error); runCatching { control.close() } }
                }
            }, "lan-synthetic-producer")
            try {
                RollingHlsLiveLanServer(local, remote, store, resources, peer, ::active).use { server ->
                    worker.start()
                    val deadline = SystemClock.elapsedRealtime() + 25_000
                    while (published.get() < 3 && active() && SystemClock.elapsedRealtime() < deadline) {
                        failure.get()?.let { throw IOException("Live producer failed", it) }
                        Thread.sleep(50)
                    }
                    check(active() && published.get() == 3L)
                    val output = DataOutputStream(control.outputStream)
                    output.writeInt(MAGIC)
                    output.writeShort(server.port)
                    output.writeUTF(checkNotNull(server.uri().encodedAuthority))
                    output.flush()
                    control.soTimeout = 135_000
                    val receipt = DataInputStream(control.inputStream)
                    if (receipt.readInt() != END || receipt.readUnsignedByte() != 1 ||
                        !active() || !done.get() || published.get() != 12L)
                        throw IOException("Receiver completion refused")
                    failure.get()?.let { throw IOException("Live producer failed", it) }
                    check(server.diagnostics().responses >= 12 && resources.diagnostics().endListRead)
                }
            } finally {
                allowed.set(false)
                worker.interrupt()
                if (worker.state != Thread.State.NEW) worker.join(5000)
                check(!worker.isAlive)
                producer.close()
                check(dir.listFiles()?.isEmpty() == true && dir.delete())
            }
        }
    }

    fun receive(control: SSLSocket, peer: RollingHlsPairedTls.Peer, local: Inet4Address,
                remote: Inet4Address, permitted: () -> Boolean,
                playback: (RollingHlsLiveLanClient) -> Boolean) {
        handshake(control, peer, false)
        check(permitted())
        control.soTimeout = 30_000
        val input = DataInputStream(control.inputStream)
        if (input.readInt() != MAGIC) throw IOException("Incorrect LAN test mode")
        val port = input.readUnsignedShort()
        // Bounded ASCII field: sender emits modified UTF length 36; check before allocation.
        if (input.readUnsignedShort() != 36) throw IOException("Invalid live authority length")
        val bytes = ByteArray(36).also { input.readFully(it) }
        val authority = String(bytes, Charsets.US_ASCII)
        if (UUID.fromString(authority).toString() != authority) throw IOException("Invalid live authority")
        RollingHlsLiveLanClient(local, RollingHlsLanInvitation.Endpoint(remote, port), authority, peer, permitted).use { client ->
            check(playback(client) && permitted())
        }
        val output = DataOutputStream(control.outputStream)
        output.writeInt(END)
        output.writeByte(1)
        output.flush()
    }
}
