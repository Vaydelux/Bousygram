package com.vaydelux.bousygram.core.cast

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * Synthetic, loopback-only Android receiver transport. Each GET uses a fresh
 * mutually authenticated TLS1.3 socket and one bounded binary response.
 * Not HTTP/HLS over LAN; control ACK is a separate authenticated operation.
 * No arbitrary host, redirects or range.
 * The caller retains the session and must close this transport before keys.
 */
@UnstableApi
internal class RollingHlsTlsLoopback(
    private val session: RollingHlsDeliverySession,
    private val serverPeer: RollingHlsPairedTls.Peer,
    private val clientPeer: RollingHlsPairedTls.Peer
) : AutoCloseable {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val listener = (serverPeer.context.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
        enabledProtocols = arrayOf("TLSv1.3")
        needClientAuth = true
        soTimeout = 250
        bind(InetSocketAddress(loopback, 0), 1)
    }
    private val watchdog = Executors.newSingleThreadScheduledExecutor()
    private val lock = Any()
    @Volatile private var stopped = false
    private var accepted: SSLSocket? = null
    private val clients = mutableMapOf<SSLSocket, ScheduledFuture<*>>()
    private val handshakes = AtomicInteger()
    private val responses = AtomicInteger()
    private val acknowledgements = AtomicInteger()
    private val lastFailure = AtomicReference<String?>(null)

    data class Diagnostics(val handshakes: Int, val responses: Int, val lastFailure: String?,
                           val acknowledgements: Int)
    fun diagnostics() = Diagnostics(handshakes.get(), responses.get(), lastFailure.get(), acknowledgements.get())

    private fun record(stage: String, error: Exception) {
        // Never include provider messages, URIs, tokens, pins or cert bytes.
        val label = "$stage:${error.javaClass.simpleName}"
        lastFailure.set(label)
        android.util.Log.w("RollingHlsTls", label)
    }
    private val dispatcher = RollingHlsDeliveryDispatcher(session)
    private val worker = Thread(::serve, "rolling-hls-tls-qualification").apply { start() }

    private fun active() = !stopped && session.active()

    private fun serve() {
        while (active()) {
            val socket = try { listener.accept() as SSLSocket }
                catch (_: java.net.SocketTimeoutException) { continue }
                catch (_: IOException) { break }
            synchronized(lock) {
                if (stopped) { socket.close(); return }
                accepted = socket
            }
            val deadline = watchdog.schedule({ runCatching { socket.close() } }, TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
            var stage = "server-handshake"
            try {
                socket.use {
                    if (!active() || !it.inetAddress.isLoopbackAddress) return@use
                    it.soTimeout = TIMEOUT
                    it.useClientMode = false
                    it.enabledProtocols = arrayOf("TLSv1.3")
                    it.needClientAuth = true
                    it.startHandshake()
                    stage = "server-peer-pin"
                    RollingHlsPeerIdentity.requirePeer(it.session, serverPeer.pin())
                    handshakes.incrementAndGet()
                    stage = "server-request"
                    val input = DataInputStream(it.inputStream)
                    val output = DataOutputStream(it.outputStream)
                    if (input.readInt() != MAGIC) throw IOException("Invalid transport version")
                    val operation = input.readUnsignedByte()
                    if (operation != GET && operation != ACK) throw IOException("Invalid transport operation")
                    val resource = readField(input, 24)
                    val token = readField(input, 43)
                    val path = readField(input, 64)
                    if (!active()) throw IOException("Receiver revoked")
                    stage = "server-response"
                    val request = if (operation == GET)
                        RollingHlsDeliveryDispatcher.Request("GET", resource, path, token)
                    else RollingHlsDeliveryDispatcher.Request("POST", resource, path, controlToken = token)
                    dispatcher.dispatch(request).use { response ->
                        val length = response.length ?: 0L
                        if (length !in 0L..RollingHlsBudget.MAX_SEGMENT_BYTES) throw IOException("Invalid response length")
                        output.writeInt(response.status)
                        output.writeLong(length)
                        if (response.status == 200) {
                            response.transmit { bytes, count ->
                                if (!active()) throw IOException("Receiver revoked")
                                output.write(bytes, 0, count)
                                output.flush()
                            }
                        }
                        output.flush()
                        if (response.status == 200) responses.incrementAndGet()
                        if (operation == ACK && response.status == 204) acknowledgements.incrementAndGet()
                    }
                }
            } catch (error: Exception) {
                record(stage, error)
                // Individual malformed, rejected or disconnected requests
                // never authorize playback. No request secrets are logged.
            } finally {
                deadline.cancel(false)
                synchronized(lock) { if (accepted === socket) accepted = null }
            }
        }
    }

    private fun open(requested: Uri): Pair<SSLSocket, DataInputStream> {
        if (!active() || requested.scheme != "bousygram-hls" ||
            requested.encodedAuthority != session.resourceId ||
            requested.encodedQuery != "token=${session.readToken}" ||
            requested.encodedFragment != null) throw IOException("Invalid receiver resource")
        val path = requested.encodedPath?.takeIf { it.startsWith("/") }?.drop(1)
            ?: throw IOException("Missing receiver resource")
        return request(GET, session.readToken, path)
    }

    /** Only trusted receiver lifecycle code calls this after actual playback
     * and player release. A trusted peer's ACK is not cryptographic playback
     * attestation; server still checks complete delivery and contiguous order.
     * A lost response is terminal for this attempt; never retry blindly. */
    fun acknowledge(sequence: Long, controlToken: String = session.controlToken): Boolean {
        if (sequence !in 1L until Long.MAX_VALUE || !active()) return false
        val (socket, input) = request(ACK, controlToken, "ack/$sequence")
        try {
            val status = input.readInt()
            val length = input.readLong()
            if (length != 0L) throw IOException("Invalid control response")
            return status == 204 && active()
        } finally { release(socket) }
    }

    private fun request(operation: Int, token: String, path: String): Pair<SSLSocket, DataInputStream> {
        if (!active()) throw IOException("Receiver revoked")
        val socket = clientPeer.context.socketFactory.createSocket() as SSLSocket
        synchronized(lock) {
            if (stopped || clients.size >= 2) { socket.close(); throw IOException("Receiver busy") }
            try {
                clients[socket] = watchdog.schedule({ runCatching { socket.close() } },
                    TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
            } catch (error: Exception) {
                socket.close()
                throw error
            }
        }
        var stage = "client-connect"
        try {
            socket.connect(InetSocketAddress(loopback, listener.localPort), TIMEOUT)
            socket.soTimeout = TIMEOUT
            socket.useClientMode = true
            socket.enabledProtocols = arrayOf("TLSv1.3")
            stage = "client-handshake"
            socket.startHandshake()
            // Identity is the explicitly enrolled certificate, not DNS.
            stage = "client-peer-pin"
            RollingHlsPeerIdentity.requirePeer(socket.session, clientPeer.pin())
            stage = "client-request"
            if (!active()) throw IOException("Receiver revoked")
            val output = DataOutputStream(socket.outputStream)
            output.writeInt(MAGIC)
            output.writeByte(operation)
            writeField(output, session.resourceId, 24)
            writeField(output, token, 43)
            writeField(output, path, 64)
            output.flush()
            return socket to DataInputStream(socket.inputStream)
        } catch (error: Exception) {
            record(stage, error)
            try { release(socket) } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            throw error
        }
    }

    private fun release(socket: SSLSocket) {
        try { socket.close() } finally { synchronized(lock) { clients.remove(socket)?.cancel(false) } }
    }

    fun factory(): DataSource.Factory = DataSource.Factory { Source() }

    private inner class Source : BaseDataSource(true) {
        private var socket: SSLSocket? = null
        private var input: DataInputStream? = null
        private var uri: Uri? = null
        private var remaining = 0L
        private var started = false
        override fun getUri(): Uri? = uri

        override fun open(dataSpec: DataSpec): Long {
            check(socket == null && !started)
            if (dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET || dataSpec.httpBody != null ||
                dataSpec.position != 0L) throw IOException("Unsupported receiver request")
            transferInitializing(dataSpec)
            try {
                val (connection, stream) = this@RollingHlsTlsLoopback.open(dataSpec.uri)
                socket = connection
                input = stream
                val status = stream.readInt()
                val length = stream.readLong()
                if (status != 200 || length !in 1L..RollingHlsBudget.MAX_SEGMENT_BYTES ||
                    (dataSpec.length != C.LENGTH_UNSET.toLong() && dataSpec.length != length) || !active())
                    throw IOException("Receiver resource refused")
                remaining = length
                uri = dataSpec.uri
                started = true
                transferStarted(dataSpec)
                return remaining
            } catch (error: Exception) {
                // Preserve the more specific handshake diagnosis from open().
                if (socket != null) record("client-response", error)
                try { close() } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
                throw error
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
            if (length == 0) return 0
            try {
                if (!started || !active()) throw IOException("Receiver revoked")
                if (remaining == 0L) return C.RESULT_END_OF_INPUT
                val count = minOf(remaining, length.toLong(), 32L * 1024).toInt()
                val n = checkNotNull(input).read(buffer, offset, count)
                if (n !in 1..count || !active()) throw IOException("Incomplete or revoked receiver transfer")
                remaining -= n
                bytesTransferred(n)
                return n
            } catch (error: Exception) {
                buffer.fill(0, offset, offset + length)
                record("client-body", error)
                throw error
            }
        }

        override fun close() {
            try { socket?.let(::release) } finally {
                socket = null; input = null; uri = null; remaining = 0
                if (started) { started = false; transferEnded() }
            }
        }
    }

    override fun close() {
        stopped = true
        listener.close()
        val sockets = synchronized(lock) { listOfNotNull(accepted) + clients.keys.toList() }
        sockets.forEach { runCatching { it.close() } }
        try { worker.join((TIMEOUT + 1000).toLong()) }
        finally { watchdog.shutdownNow() }
        check(!worker.isAlive && watchdog.awaitTermination(1, TimeUnit.SECONDS))
    }

    companion object {
        private const val MAGIC = 0x42475232
        private const val GET = 1
        private const val ACK = 2
        private const val TIMEOUT = 5_000
        private fun readField(input: DataInputStream, cap: Int): String {
            val n = input.readUnsignedByte()
            if (n !in 1..cap) throw IOException("Invalid request field")
            val data = ByteArray(n).also { input.readFully(it) }
            try {
                if (data.any { it.toInt() !in 33..126 }) throw IOException("Invalid request encoding")
                return String(data, Charsets.US_ASCII)
            } finally { data.fill(0) }
        }
        private fun writeField(output: DataOutputStream, value: String, cap: Int) {
            if (value.length !in 1..cap || value.any { it.code !in 33..126 }) throw IOException("Invalid request field")
            val data = value.toByteArray(Charsets.US_ASCII)
            try { output.writeByte(data.size); output.write(data) } finally { data.fill(0) }
        }
    }
}
