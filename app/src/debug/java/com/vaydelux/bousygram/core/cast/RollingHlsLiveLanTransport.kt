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

import java.net.Inet4Address
import java.util.UUID

/** Debug-only separate endpoints. No loopback transport change. Listener binds
 * to one selected private interface and one enrolled peer address. Every
 * request uses mutual TLS, exact pins and a bounded canonical resource. */
@UnstableApi
internal class RollingHlsLiveLanServer(
    private val local: Inet4Address, private val remote: Inet4Address,
    private val store: RollingHlsLiveFileStore,
    private val resources: RollingHlsLiveLocalTransport,
    private val serverPeer: RollingHlsPairedTls.Peer,
    private val permitted: () -> Boolean
) : AutoCloseable {
    init { require(RollingHlsLanInvitation.isPrivate(local) && RollingHlsLanInvitation.isPrivate(remote) && permitted()) }
    private val listener = (serverPeer.context.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
        enabledProtocols = arrayOf("TLSv1.3")
        needClientAuth = true
        soTimeout = 250
        bind(InetSocketAddress(local, 0), 1)
    }
    private val watchdog = Executors.newSingleThreadScheduledExecutor()
    private val lock = Any()
    @Volatile private var stopped = false
    private var accepted: SSLSocket? = null
    private val handshakes = AtomicInteger()
    private val responses = AtomicInteger()
    private val rangeResponses = AtomicInteger()
    private val lastFailure = AtomicReference<String?>(null)

    data class Diagnostics(val handshakes: Int, val responses: Int, val lastFailure: String?,
                           val rangeResponses: Int)
    fun diagnostics() = Diagnostics(handshakes.get(), responses.get(), lastFailure.get(), rangeResponses.get())
    fun uri(): Uri = resources.uri()
    val port: Int get() = listener.localPort

    private fun record(stage: String, error: Exception) {
        // Never include provider messages, URIs, tokens, pins or cert bytes.
        val label = "$stage:${error.javaClass.simpleName}"
        lastFailure.set(label)
        android.util.Log.w("RollingHlsTls", label)
    }
    private val worker = Thread(::serve, "rolling-hls-live-tls-qualification").apply { start() }

    private fun active() = !stopped && permitted() && store.available()

    private fun serve() {
        while (active()) {
            val socket = try { listener.accept() as SSLSocket }
                catch (_: java.net.SocketTimeoutException) { continue }
                catch (_: IOException) { break }
            synchronized(lock) {
                if (stopped) { socket.close(); return }
                accepted = socket
            }
            val deadline = watchdog.schedule({ runCatching { socket.close() } }, LiveLanWire.TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
            var stage = "server-handshake"
            try {
                socket.use {
                    if (!active() || it.inetAddress != remote) return@use
                    it.soTimeout = LiveLanWire.TIMEOUT
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
                    if (input.readInt() != LiveLanWire.MAGIC) throw IOException("Invalid transport version")
                    val authority = LiveLanWire.readField(input, 36)
                    val path = LiveLanWire.readField(input, 32)
                    val offset = input.readLong()
                    val requestedLength = input.readLong()
                    if (authority != uri().encodedAuthority || !LiveLanWire.validPath(path) ||
                        offset !in 0L until RollingHlsBudget.MAX_SEGMENT_BYTES ||
                        (requestedLength != C.LENGTH_UNSET.toLong() &&
                            requestedLength !in 1L..RollingHlsBudget.MAX_SEGMENT_BYTES) || !active())
                        throw IOException("Invalid live request")
                    stage = "server-response"
                    val requested = uri().buildUpon().encodedPath(path).build()
                    val spec = DataSpec.Builder().setUri(requested).setPosition(offset)
                        .setLength(requestedLength).build()
                    val source = resources.factory().createDataSource()
                    val buffer = ByteArray(32 * 1024)
                    try {
                        val length = try { source.open(spec) } catch (_: IOException) {
                            output.writeInt(404)
                            output.writeLong(0L)
                            output.flush()
                            return@use
                        }
                        if (length !in 1L..RollingHlsBudget.MAX_SEGMENT_BYTES || !active())
                            throw IOException("Invalid live response length")
                        output.writeInt(200)
                        output.writeLong(length)
                        var left = length
                        while (left > 0) {
                            if (!active()) throw IOException("Live transport revoked")
                            val n = source.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                            if (n <= 0 || n > left || !active()) throw IOException("Live response incomplete")
                            output.write(buffer, 0, n)
                            left -= n
                        }
                        output.flush()
                        if (!active()) throw IOException("Live transport revoked")
                        responses.incrementAndGet()
                        if (offset != 0L || requestedLength != C.LENGTH_UNSET.toLong()) rangeResponses.incrementAndGet()
                    } finally {
                        buffer.fill(0)
                        source.close()
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

    override fun close() {
        stopped = true
        try { listener.close() } finally {
            synchronized(lock) { accepted }?.let { runCatching { it.close() } }
            try { worker.join(6000) } finally { watchdog.shutdownNow() }
        }
        check(!worker.isAlive && watchdog.awaitTermination(1, TimeUnit.SECONDS))
    }
}

@UnstableApi
internal class RollingHlsLiveLanClient(
    private val local: Inet4Address,
    private val remote: RollingHlsLanInvitation.Endpoint,
    authority: String,
    private val clientPeer: RollingHlsPairedTls.Peer,
    private val permitted: () -> Boolean
) : AutoCloseable {
    init {
        require(RollingHlsLanInvitation.isPrivate(local) && UUID.fromString(authority).toString() == authority && permitted())
    }
    private val root = Uri.Builder().scheme("bousygram-live").authority(authority).appendPath("index.m3u8").build()
    private val watchdog = Executors.newSingleThreadScheduledExecutor()
    private val lock = Any()
    @Volatile private var stopped = false
    private val clients = mutableMapOf<SSLSocket, ScheduledFuture<*>>()
    fun uri(): Uri = root
    private fun active() = !stopped && permitted()
    private fun record(stage: String, error: Exception) {
        android.util.Log.w("RollingHlsLan", "$stage:${error.javaClass.simpleName}")
    }
    private fun open(spec: DataSpec): Pair<SSLSocket, DataInputStream> {
        val requested = spec.uri
        val path = requested.encodedPath ?: throw IOException("Missing live resource")
        if (!active() || requested.scheme != uri().scheme ||
            requested.encodedAuthority != uri().encodedAuthority ||
            requested.encodedQuery != null || requested.encodedFragment != null ||
            !LiveLanWire.validPath(path) || spec.position !in 0L until RollingHlsBudget.MAX_SEGMENT_BYTES ||
            (spec.length != C.LENGTH_UNSET.toLong() && spec.length !in 1L..RollingHlsBudget.MAX_SEGMENT_BYTES))
            throw IOException("Invalid live resource")
        val socket = clientPeer.context.socketFactory.createSocket() as SSLSocket
        synchronized(lock) {
            if (stopped || clients.size >= 2) { socket.close(); throw IOException("Receiver busy") }
            try {
                clients[socket] = watchdog.schedule({ runCatching { socket.close() } },
                    LiveLanWire.TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
            } catch (error: Exception) {
                socket.close()
                throw error
            }
        }
        var stage = "client-connect"
        try {
            socket.bind(InetSocketAddress(local, 0))
            socket.connect(InetSocketAddress(remote.address, remote.port), LiveLanWire.TIMEOUT)
            socket.soTimeout = LiveLanWire.TIMEOUT
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
            output.writeInt(LiveLanWire.MAGIC)
            LiveLanWire.writeField(output, checkNotNull(uri().encodedAuthority), 36)
            LiveLanWire.writeField(output, path, 32)
            output.writeLong(spec.position)
            output.writeLong(spec.length)
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
                dataSpec.position < 0L) throw IOException("Unsupported receiver request")
            transferInitializing(dataSpec)
            try {
                val (connection, stream) = this@RollingHlsLiveLanClient.open(dataSpec)
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
        val sockets = synchronized(lock) { stopped = true; clients.keys.toList() }
        sockets.forEach { runCatching { release(it) } }
        watchdog.shutdownNow()
        check(watchdog.awaitTermination(1, TimeUnit.SECONDS))
    }
}

private object LiveLanWire {
        const val MAGIC = 0x42474c31
        fun validPath(path: String): Boolean {
            if (path == "/index.m3u8" || path == "/init.mp4") return true
            if (!path.startsWith("/segment/") || !path.endsWith(".m4s") || path.length !in 14..32) return false
            val value = path.substring(9, path.length - 4)
            return value.firstOrNull() != '0' && value.all { it in '0'..'9' } &&
                value.toLongOrNull()?.let { it in 1L until Long.MAX_VALUE } == true
        }
        const val TIMEOUT = 5_000
        fun readField(input: DataInputStream, cap: Int): String {
            val n = input.readUnsignedByte()
            if (n !in 1..cap) throw IOException("Invalid request field")
            val data = ByteArray(n).also { input.readFully(it) }
            try {
                if (data.any { it.toInt() !in 33..126 }) throw IOException("Invalid request encoding")
                return String(data, Charsets.US_ASCII)
            } finally { data.fill(0) }
        }
        fun writeField(output: DataOutputStream, value: String, cap: Int) {
            if (value.length !in 1..cap || value.any { it.code !in 33..126 }) throw IOException("Invalid request field")
            val data = value.toByteArray(Charsets.US_ASCII)
            try { output.writeByte(data.size); output.write(data) } finally { data.fill(0) }
        }
    }
