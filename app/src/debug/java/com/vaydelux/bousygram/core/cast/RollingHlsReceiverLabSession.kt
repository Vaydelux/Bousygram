package com.vaydelux.bousygram.core.cast

import android.graphics.Bitmap
import android.os.SystemClock
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.tdlib.utils.QrCodeGenerator
import java.util.Locale
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** Synthetic receiver lab; all methods on a single IO owner. No real account,
 * remote sender, scanner, LAN listener or user media. A simulated sender still
 * needs a separate explicit local approval. No persistence of invitations. */
internal class RollingHlsReceiverLabSession(
    private val current: () -> Boolean,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime
) : AutoCloseable {
    data class Invitation(val bitmap: Bitmap, val expiresAtMs: Long)
    private val owner = AccountSnapshot(1, 1)
    private val permitted: (AccountSnapshot) -> Boolean = { it == owner && current() }
    private val pairing = RollingHlsPeerPairing(permitted, nowMs)
    private var tv: RollingHlsKeystoreIdentity? = null
    private var sender: RollingHlsKeystoreIdentity? = null
    private var offer: RollingHlsPeerPairing.Offer? = null
    private var pending: RollingHlsPeerPairing.Pending? = null
    private var deadline = 0L
    private var issued = 0L
    private var closed = false

    private fun active() = !closed && current() && nowMs() in issued until deadline

    fun issue(): Invitation {
        check(!closed && tv == null && current())
        val identity = RollingHlsKeystoreIdentity.create(owner, permitted).also { tv = it }
        issued = nowMs()
        deadline = Math.addExact(issued, 120_000L)
        val invitation = checkNotNull(pairing.issue(owner, identity.fingerprint())).also { offer = it }
        val encoded = checkNotNull(RollingHlsPairingQr.encode(invitation))
        val bitmap = checkNotNull(QrCodeGenerator.generate(encoded, 480, 480))
        if (!active()) {
            bitmap.eraseColor(android.graphics.Color.BLACK)
            bitmap.recycle()
            error("Invitation expired")
        }
        return Invitation(bitmap, deadline)
    }

    fun simulateSender(): String {
        check(active() && pending == null && sender == null)
        val invitation = checkNotNull(offer)
        val identity = RollingHlsKeystoreIdentity.create(owner, permitted).also { sender = it }
        val pin = identity.fingerprint()
        try {
            val request = exchange(invitation, identity)
            pending = request.pending
            check(active() && request.certificate.encoded.contentEquals(identity.certificate().encoded))
            return pin.joinToString(":") { String.format(Locale.ROOT, "%02X", it.toInt() and 255) }
        } finally {
            invitation.close()
            offer = null
        }
    }

    /** One-shot synthetic enrollment on loopback. Both endpoints are
     * independent Keystore identities. Only pending status crosses the wire;
     * no media capability or approval is sent. Deadline closes both sockets. */
    private fun exchange(invitation: RollingHlsPeerPairing.Offer,
                         identity: RollingHlsKeystoreIdentity): RollingHlsEnrollment.Request {
        val receiver = checkNotNull(tv)
        val serverContext = RollingHlsPairedTls.bootstrapServer(owner, receiver, permitted)
        val local = InetAddress.getByName("127.0.0.1")
        val listener = serverContext.serverSocketFactory.createServerSocket(0, 1, local) as SSLServerSocket
        val worker = Executors.newSingleThreadExecutor()
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        val accepted = AtomicReference<SSLSocket?>()
        val client = AtomicReference<SSLSocket?>()
        val stop = java.util.concurrent.atomic.AtomicBoolean()
        fun allowed() = !stop.get() && active()
        fun closeSockets() {
            stop.set(true)
            runCatching { listener.close() }
            runCatching { accepted.get()?.close() }
            runCatching { client.get()?.close() }
        }
        try {
            listener.soTimeout = 5000
            watchdog.schedule({ closeSockets() }, 8, TimeUnit.SECONDS)
            val response = worker.submit<RollingHlsEnrollment.Request?> {
                (listener.accept() as SSLSocket).use { socket ->
                    accepted.set(socket)
                    check(allowed())
                    RollingHlsEnrollment.receive(socket, pairing, ::allowed)
                }
            }
            val ctx = RollingHlsEnrollment.clientContext(receiver.certificate(), invitation.receiverPin)
            val ok = (ctx.socketFactory.createSocket() as SSLSocket).use { socket ->
                client.set(socket)
                check(allowed())
                socket.connect(InetSocketAddress(local, listener.localPort), 5000)
                RollingHlsEnrollment.send(socket, invitation, identity.certificate(), identity.privateKey(), ::allowed)
            }
            check(ok && allowed())
            return checkNotNull(response.get(8, TimeUnit.SECONDS))
        } finally {
            closeSockets()
            watchdog.shutdownNow()
            worker.shutdownNow()
            check(worker.awaitTermination(2, TimeUnit.SECONDS) && watchdog.awaitTermination(2, TimeUnit.SECONDS))
        }
    }

    /** Verify enrolled identities and mutual TLS signing compatibility;
     * bootstrap enrollment already used a real pinned TLS handshake, but
     * this confirmation does not start media delivery or a LAN listener. */
    fun confirm(accept: Boolean): Boolean {
        check(active())
        val ticket = checkNotNull(pending)
        pending = null
        val pins = pairing.confirm(ticket, accept)
        if (!accept) { close(); return false }
        checkNotNull(pins)
        val receiver = checkNotNull(tv)
        val remote = checkNotNull(sender)
        RollingHlsPairedTls.create(owner, pins, remote, receiver.certificate(), false, permitted)
        RollingHlsPairedTls.create(owner, pins, receiver, remote.certificate(), true, permitted)
        check(active())
        close()
        return true
    }

    override fun close() {
        closed = true
        pairing.close()
        offer?.close()
        offer = null
        pending = null
        var failure: Exception? = null
        for (key in listOfNotNull(sender, tv)) {
            try { key.close() }
            catch (error: Exception) {
                val previous = failure
                if (previous == null) failure = error else previous.addSuppressed(error)
            }
        }
        failure?.let { throw it }
        sender = null
        tv = null
    }
}
