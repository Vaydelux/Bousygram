package com.vaydelux.bousygram.core.cast

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.system.Os
import android.system.OsConstants
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLServerSocket
import java.util.UUID
import javax.net.ssl.SSLSocket

/** One consent-bound selected file, random reads from its original descriptor.
 * No arbitrary path or URL, no full-file copy, no disk cache, no transcoder.
 * Both directions use the already enrolled TLS identity; no permanent listener. */
@UnstableApi
internal object RollingLanDirectMedia {
    private const val MAGIC = 0x42474432
    private const val DATA_MAGIC = 0x42474433
    private const val READ = 1
    private const val FINISH = 2

    interface Source {
        val size: Long
        fun readAt(offset: Long, buffer: ByteArray, count: Int)
    }

    private class Descriptor(private val asset: AssetFileDescriptor) : Source {
        private val descriptor = asset.fileDescriptor
        private val stat = Os.fstat(descriptor)
        private val start = asset.startOffset
        override val size = asset.declaredLength.takeIf { it >= 0 } ?: (stat.st_size - start)

        init {
            require(OsConstants.S_ISREG(stat.st_mode) && start in 0L..stat.st_size &&
                RollingLanDirectMediaPolicy.validSize(size) && size <= stat.st_size - start)
        }

        override fun readAt(offset: Long, buffer: ByteArray, count: Int) {
            if (!RollingLanDirectMediaPolicy.validRange(size, offset, count) || count > buffer.size)
                throw IOException("Invalid selected media range")
            try {
                val current = Os.fstat(descriptor)
                if (current.st_dev != stat.st_dev || current.st_ino != stat.st_ino ||
                    current.st_size != stat.st_size || current.st_mtime != stat.st_mtime ||
                    current.st_ctime != stat.st_ctime) throw IOException("Selected media changed")
                var done = 0
                while (done < count) {
                    val n = Os.pread(descriptor, buffer, done, count - done, start + offset + done)
                    if (n <= 0 || n > count - done) throw IOException("Selected media truncated")
                    done += n
                }
            } catch (error: android.system.ErrnoException) {
                throw IOException("Selected media cannot be read", error)
            }
        }
    }

    private fun access(permitted: () -> Boolean) {
        if (!permitted()) throw IOException("Direct media session revoked")
    }

    private fun handshake(socket: SSLSocket, peer: RollingHlsPairedTls.Peer, client: Boolean,
                          permitted: () -> Boolean) {
        access(permitted)
        socket.useClientMode = client
        socket.enabledProtocols = arrayOf("TLSv1.3")
        if (!client) socket.needClientAuth = true
        socket.soTimeout = 5_000
        socket.startHandshake()
        RollingHlsPeerIdentity.requirePeer(socket.session, peer.pin())
        access(permitted)
    }

    fun sendSelected(context: Context, uri: Uri, socket: SSLSocket, peer: RollingHlsPairedTls.Peer,
                     permitted: () -> Boolean) {
        require(uri.scheme == "content")
        access(permitted)
        val asset = context.contentResolver.openAssetFileDescriptor(uri, "r")
            ?: throw IOException("Selected video is unavailable")
        asset.use { send(socket, peer, Descriptor(it), permitted) }
    }

    fun send(socket: SSLSocket, peer: RollingHlsPairedTls.Peer, source: Source,
             permitted: () -> Boolean, interruptAfterWrite: ((Long) -> Boolean)? = null) {
        handshake(socket, peer, true, permitted)
        val size = source.size
        if (!RollingLanDirectMediaPolicy.validSize(size)) throw IOException("Invalid selected media size")
        val header = ByteArray(minOf(size, 512L).toInt())
        try {
            source.readAt(0L, header, header.size)
            if (!RollingLanDirectMediaPolicy.supportedHeader(header)) throw IOException("Unsupported selected container")
        } finally { header.fill(0) }
        access(permitted)
        val input = DataInputStream(socket.inputStream)
        val output = DataOutputStream(socket.outputStream)
        val listener = peer.context.serverSocketFactory.createServerSocket() as SSLServerSocket
        val done = AtomicBoolean(false)
        val accepted = AtomicReference<SSLSocket?>(null)
        val fatal = AtomicReference<Exception?>(null)
        fun active() = !done.get() && permitted()
        var worker: Thread? = null
        try {
            listener.enabledProtocols = arrayOf("TLSv1.3")
            listener.needClientAuth = true
            listener.soTimeout = 250
            listener.bind(InetSocketAddress(socket.localAddress, 0), 1)
            val reader = Thread({
                val buffer = ByteArray(RollingLanDirectMediaPolicy.MAX_READ_BYTES)
                try {
                    while (active()) {
                        val connection = try { listener.accept() as SSLSocket }
                            catch (_: SocketTimeoutException) { continue }
                        accepted.set(connection)
                        try {
                            connection.use { data ->
                                if (!active() || data.inetAddress != socket.inetAddress) return@use
                                handshake(data, peer, false, ::active)
                                data.soTimeout = 15_000
                                val request = DataInputStream(data.inputStream)
                                val response = DataOutputStream(data.outputStream)
                                if (request.readInt() != DATA_MAGIC) throw IOException("Invalid direct channel")
                                response.writeInt(DATA_MAGIC)
                                response.flush()
                                while (active()) {
                                    if (request.readUnsignedByte() != READ) throw IOException("Invalid direct command")
                                    val offset = request.readLong()
                                    val count = request.readInt()
                                    if (!RollingLanDirectMediaPolicy.validRange(size, offset, count))
                                        throw IOException("Invalid direct media request")
                                    access(::active)
                                    try { source.readAt(offset, buffer, count) }
                                    catch (error: Exception) {
                                        fatal.set(error)
                                        runCatching { socket.close() }
                                        return@use
                                    }
                                    access(::active)
                                    response.writeInt(count)
                                    if (count > 1 && interruptAfterWrite?.invoke(offset) == true) {
                                        response.write(buffer, 0, count / 2)
                                        response.flush()
                                        return@use
                                    }
                                    response.write(buffer, 0, count)
                                    response.flush()
                                }
                            }
                        } catch (_: IOException) {
                            // A failed data socket never grants enrollment or
                            // changes the file. The same pinned peer may reconnect.
                        } finally { accepted.compareAndSet(connection, null) }
                    }
                } catch (error: Exception) {
                    if (active()) { fatal.set(error); runCatching { socket.close() } }
                } finally { buffer.fill(0) }
            }, "direct-media-data")
            worker = reader
            reader.start()
            output.writeInt(MAGIC)
            output.writeLong(size)
            output.writeShort(listener.localPort)
            output.flush()
            socket.soTimeout = RollingLanSessionLifetime.DIRECT_VIDEO.operationMs.toInt()
            if (input.readUnsignedByte() != FINISH || input.readUnsignedByte() != 1)
                throw IOException("Direct playback not confirmed")
            access(permitted)
            fatal.get()?.let { throw IOException("Direct source failed", it) }
            output.writeByte(1)
            output.flush()
        } finally {
            done.set(true)
            runCatching { listener.close() }
            accepted.get()?.let { runCatching { it.close() } }
            worker?.let { it.join(5000); check(!it.isAlive) }
        }
    }

    fun receive(socket: SSLSocket, peer: RollingHlsPairedTls.Peer, permitted: () -> Boolean,
                playback: (Uri, DataSource.Factory) -> Boolean) {
        handshake(socket, peer, false, permitted)
        socket.soTimeout = 15_000
        val input = DataInputStream(socket.inputStream)
        val output = DataOutputStream(socket.outputStream)
        if (input.readInt() != MAGIC) throw IOException("Incorrect direct media mode")
        val size = input.readLong()
        if (!RollingLanDirectMediaPolicy.validSize(size)) throw IOException("Invalid remote media size")
        val port = input.readUnsignedShort()
        if (port == 0) throw IOException("Invalid direct data port")
        val client = Client(size, socket, port, peer, input, output, permitted)
        try {
            if (!playback(client.uri, client.factory()) || !permitted()) throw IOException("Direct playback incomplete")
            client.finish()
        } finally { client.revoke() }
    }

    private class Client(private val size: Long, private val control: SSLSocket,
                         private val port: Int, private val peer: RollingHlsPairedTls.Peer,
                         private val input: DataInputStream, private val output: DataOutputStream,
                         private val permitted: () -> Boolean) {
        val uri: Uri = Uri.parse("bousygram-direct://${UUID.randomUUID()}/video")
        @Volatile private var closed = false
        @Volatile private var failed = false
        private fun active() = !closed && !failed && permitted()
        private val dataSocket = AtomicReference<SSLSocket?>(null)
        private var dataInput: DataInputStream? = null
        private var dataOutput: DataOutputStream? = null

        private fun disconnect() {
            dataSocket.getAndSet(null)?.let { runCatching { it.close() } }
            dataInput = null
            dataOutput = null
        }
        fun revoke() {
            closed = true
            // Close outside the synchronized read monitor to interrupt blocked IO.
            dataSocket.getAndSet(null)?.let { runCatching { it.close() } }
        }

        private fun connect() {
            if (dataSocket.get() != null) return
            access(::active)
            val socket = peer.context.socketFactory.createSocket() as SSLSocket
            dataSocket.set(socket)
            try {
                access(::active)
                socket.bind(InetSocketAddress(control.localAddress, 0))
                socket.connect(InetSocketAddress(control.inetAddress, port), 5000)
                handshake(socket, peer, true, ::active)
                socket.soTimeout = 15_000
                val request = DataOutputStream(socket.outputStream)
                val response = DataInputStream(socket.inputStream)
                request.writeInt(DATA_MAGIC)
                request.flush()
                if (response.readInt() != DATA_MAGIC) throw IOException("Invalid direct data channel")
                dataInput = response
                dataOutput = request
            } catch (error: Exception) {
                disconnect()
                throw error
            }
        }

        @Synchronized fun fetch(offset: Long, count: Int, buffer: ByteArray, start: Int) {
            var retries = 0
            try {
                if (!active() || !RollingLanDirectMediaPolicy.validRange(size, offset, count))
                    throw IOException("Direct media read refused")
                while (true) {
                    try {
                        connect()
                        val request = checkNotNull(dataOutput)
                        val response = checkNotNull(dataInput)
                        request.writeByte(READ)
                        request.writeLong(offset)
                        request.writeInt(count)
                        request.flush()
                        if (response.readInt() != count) throw IOException("Invalid direct media response")
                        response.readFully(buffer, start, count)
                        if (!active()) throw IOException("Direct media read revoked")
                        return
                    } catch (error: IOException) {
                        disconnect()
                        buffer.fill(0, start, start + count)
                        if (!active() || retries >= 2 || !RollingLanDirectRecovery.retryable(error)) throw error
                        retries++
                    }
                }
            } catch (error: Exception) {
                failed = true
                disconnect()
                buffer.fill(0, start, start + count)
                throw error
            }
        }

        @Synchronized fun finish() {
            if (!active()) throw IOException("Direct media session unavailable")
            disconnect()
            output.writeByte(FINISH)
            output.writeByte(1)
            output.flush()
            if (input.readUnsignedByte() != 1 || !active()) throw IOException("Direct completion refused")
            closed = true
        }

        fun factory(): DataSource.Factory = DataSource.Factory { object : BaseDataSource(true) {
            private var opened = false
            private var cursor = 0L
            private var remaining = 0L
            override fun getUri(): Uri? = if (opened) this@Client.uri else null
            override fun open(dataSpec: DataSpec): Long {
                if (opened || !active() || dataSpec.uri != this@Client.uri || dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET ||
                    dataSpec.httpBody != null || dataSpec.position !in 0L..size)
                    throw IOException("Invalid direct media resource")
                val available = size - dataSpec.position
                val length = if (dataSpec.length == C.LENGTH_UNSET.toLong()) available else dataSpec.length
                if (length !in 0L..available) throw IOException("Invalid direct media range")
                transferInitializing(dataSpec)
                cursor = dataSpec.position
                remaining = length
                opened = true
                transferStarted(dataSpec)
                return length
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
                if (length == 0) return 0
                if (!opened || !active()) throw IOException("Direct media session revoked")
                if (remaining == 0L) return C.RESULT_END_OF_INPUT
                val count = minOf(remaining, length.toLong(), RollingLanDirectMediaPolicy.MAX_READ_BYTES.toLong()).toInt()
                fetch(cursor, count, buffer, offset)
                cursor += count
                remaining -= count
                bytesTransferred(count)
                return count
            }
            override fun close() {
                if (opened) { opened = false; transferEnded() }
                cursor = 0; remaining = 0
            }
        } }
    }
}
