package com.vaydelux.bousygram.core.cast

import android.os.SystemClock
import com.vaydelux.bousygram.core.account.AccountSnapshot
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** Debug two-device synthetic qualification, not a media server. A single
 * foreground operation owns all sockets and ephemeral identities. Explicit
 * close from UI revokes immediately and interrupts blocking socket IO. */
internal class RollingHlsLanSession(
    private val local: Inet4Address, private val prefix: Int,
    private val permitted: () -> Boolean,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime
) : AutoCloseable {
    enum class Result { VERIFIED, DENIED }
    private val lock = Any()
    private val sockets = mutableSetOf<Closeable>()
    private val watchdog = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var stopped = false
    private val owner = AccountSnapshot(1, 1)
    private val current: (AccountSnapshot) -> Boolean = { it == owner && active() }

    init { require(RollingHlsLanInvitation.isPrivate(local) && prefix in 1..32) }
    fun active(): Boolean = !stopped && permitted()
    private fun access() { if (!active()) throw IOException("LAN session revoked") }

    private fun <T : Closeable, R> using(resource: T, action: (T) -> R): R {
        synchronized(lock) {
            if (!active()) { resource.close(); throw IOException("LAN session revoked") }
            sockets.add(resource)
        }
        try { return action(resource) }
        finally {
            try { resource.close() } finally { synchronized(lock) { sockets.remove(resource) } }
        }
    }

    private fun <R> bounded(socket: SSLSocket, millis: Long, action: () -> R): R {
        access()
        val timer = watchdog.schedule({ runCatching { socket.close() } }, millis, TimeUnit.MILLISECONDS)
        try { return action() } finally { timer.cancel(false) }
    }

    private fun listener(context: SSLContext): SSLServerSocket =
        context.serverSocketFactory.createServerSocket() as SSLServerSocket

    private fun bind(socket: SSLServerSocket) {
        access()
        socket.enabledProtocols = arrayOf("TLSv1.3")
        socket.soTimeout = 250
        socket.bind(InetSocketAddress(local, 0), 1)
    }

    private fun accept(server: SSLServerSocket, deadline: Long): SSLSocket {
        while (active() && nowMs() < deadline) {
            try { return server.accept() as SSLSocket }
            catch (_: SocketTimeoutException) { }
        }
        throw IOException("LAN invitation expired or revoked")
    }

    private fun validPeer(socket: SSLSocket, expected: Inet4Address? = null): Boolean {
        val address = socket.inetAddress as? Inet4Address ?: return false
        return RollingHlsLanInvitation.sameSubnet(local, address, prefix) &&
            (expected == null || expected == address)
    }

    private fun connect(socket: SSLSocket, endpoint: RollingHlsLanInvitation.Endpoint) {
        access()
        if (!RollingHlsLanInvitation.sameSubnet(local, endpoint.address, prefix))
            throw IOException("Peer is outside the selected private subnet")
        socket.bind(InetSocketAddress(local, 0))
        socket.connect(InetSocketAddress(endpoint.address, endpoint.port), 5000)
        access()
    }

    fun receive(onInvitation: (String, Long) -> Unit,
                onPending: (String, Long) -> Boolean,
                onApproved: () -> Unit): Result {
        access()
        RollingHlsKeystoreIdentity.create(owner, current).use { identity ->
            RollingHlsPeerPairing(current, nowMs).use { pairing ->
                val context = RollingHlsPairedTls.bootstrapServer(owner, identity, current)
                return using(listener(context)) outer@ { server ->
                    bind(server)
                    val issued = nowMs()
                    val deadline = Math.addExact(issued, 120_000L)
                    checkNotNull(pairing.issue(owner, identity.fingerprint())).use { offer ->
                        val endpoint = RollingHlsLanInvitation.Endpoint(local, server.localPort)
                        val invitation = checkNotNull(RollingHlsLanInvitation.encode(endpoint, offer, identity.certificate()))
                        onInvitation(invitation, deadline)
                    }
                    repeat(8) {
                        val result = using(accept(server, deadline)) peer@ { socket ->
                            if (!validPeer(socket)) return@peer null
                            val request = try { bounded(socket, 8_000) {
                                RollingHlsEnrollment.receive(socket, pairing) { active() && nowMs() in issued until deadline }
                            } } catch (_: Exception) { null } ?: return@peer null
                            val remote = socket.inetAddress as Inet4Address
                            val consentDeadline = minOf(deadline, nowMs() + 30_000L)
                            val fingerprint = request.pending.senderPin().joinToString(":") {
                                String.format(Locale.ROOT, "%02X", it.toInt() and 255)
                            }
                            val accept = onPending(fingerprint, consentDeadline) &&
                                active() && nowMs() in issued until consentDeadline
                            val pins = pairing.confirm(request.pending, accept)
                            if (pins == null) {
                                if (active()) bounded(socket, 5_000) { approval(socket, 0) }
                                return@peer Result.DENIED
                            }
                            val tls = RollingHlsPairedTls.create(owner, pins, identity, request.certificate, true, current)
                            using(listener(tls.context)) { probeServer ->
                                bind(probeServer)
                                bounded(socket, 5_000) { approval(socket, probeServer.localPort) }
                                // Bootstrap listener and connection no longer accept work.
                                server.close()
                                socket.close()
                                onApproved()
                                using(accept(probeServer, nowMs() + 15_000L)) { probe ->
                                    if (!validPeer(probe, remote)) throw IOException("Unexpected probe endpoint")
                                    bounded(probe, 15_000) { RollingHlsLanProbe.receive(probe, tls.pin(), ::active) }
                                }
                            }
                            Result.VERIFIED
                        }
                        if (result != null) return@outer result
                        access()
                    }
                    throw IOException("LAN enrollment attempts exhausted")
                }
            }
        }
    }

    fun send(invitation: RollingHlsLanInvitation.Decoded, onPending: () -> Unit,
             onApproved: () -> Unit): Result {
        access()
        val endpoint = invitation.endpoint
        if (!RollingHlsLanInvitation.sameSubnet(local, endpoint.address, prefix))
            throw IOException("Peer is outside the selected private subnet")
        RollingHlsKeystoreIdentity.create(owner, current).use { identity ->
            val bootstrap = RollingHlsEnrollment.clientContext(invitation.certificate, invitation.offer.receiverPin)
            val port = using(bootstrap.socketFactory.createSocket() as SSLSocket) { socket ->
                bounded(socket, 45_000) {
                    connect(socket, endpoint)
                    if (!RollingHlsEnrollment.send(socket, invitation.offer, identity.certificate(), identity.privateKey(), ::active))
                        throw IOException("Enrollment refused")
                    onPending()
                    socket.soTimeout = 35_000
                    val input = DataInputStream(socket.inputStream)
                    if (input.readInt() != APPROVAL) throw IOException("Invalid approval response")
                    input.readUnsignedShort()
                }
            }
            access()
            if (port == 0) return Result.DENIED
            val pins = RollingHlsPeerPairing.Pins(owner, identity.fingerprint(), invitation.offer.receiverPin)
            val tls = RollingHlsPairedTls.create(owner, pins, identity, invitation.certificate, false, current)
            onApproved()
            using(tls.context.socketFactory.createSocket() as SSLSocket) { socket ->
                bounded(socket, 15_000) {
                    connect(socket, RollingHlsLanInvitation.Endpoint(endpoint.address, port))
                    RollingHlsLanProbe.send(socket, tls.pin(), ::active)
                }
            }
            return Result.VERIFIED
        }
    }

    private fun approval(socket: SSLSocket, port: Int) {
        access()
        val output = DataOutputStream(socket.outputStream)
        output.writeInt(APPROVAL)
        output.writeShort(port)
        output.flush()
        access()
    }

    override fun close() {
        val pending = synchronized(lock) {
            stopped = true
            sockets.toList()
        }
        pending.forEach { runCatching { it.close() } }
        watchdog.shutdownNow()
    }

    companion object { private const val APPROVAL = 0x42474131 }
}
