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
import java.util.UUID
import javax.net.ssl.SSLSocket

/** One consent-bound selected file, random reads from its original descriptor.
 * No arbitrary path or URL, no full-file copy, no disk cache, no transcoder.
 * Both directions use the already enrolled TLS identity; no permanent listener. */
@UnstableApi
internal object RollingLanDirectMedia {
    private const val MAGIC = 0x42474431
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
             permitted: () -> Boolean) {
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
        output.writeInt(MAGIC)
        output.writeLong(size)
        output.flush()
        // No requests during a pause: bound idle ownership separately from total duration.
        socket.soTimeout = 5 * 60 * 1000
        val buffer = ByteArray(RollingLanDirectMediaPolicy.MAX_READ_BYTES)
        try {
            while (true) {
                access(permitted)
                when (input.readUnsignedByte()) {
                    READ -> {
                        val offset = input.readLong()
                        val count = input.readInt()
                        if (!RollingLanDirectMediaPolicy.validRange(size, offset, count))
                            throw IOException("Invalid direct media request")
                        access(permitted)
                        source.readAt(offset, buffer, count)
                        access(permitted)
                        output.writeInt(count)
                        output.write(buffer, 0, count)
                        output.flush()
                    }
                    FINISH -> {
                        access(permitted)
                        if (input.readUnsignedByte() != 1) throw IOException("Playback not confirmed")
                        output.writeByte(1)
                        output.flush()
                        return
                    }
                    else -> throw IOException("Invalid direct media command")
                }
            }
        } finally { buffer.fill(0) }
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
        val client = Client(size, input, output, permitted)
        try {
            if (!playback(client.uri, client.factory()) || !permitted()) throw IOException("Direct playback incomplete")
            client.finish()
        } finally { client.revoke() }
    }

    private class Client(private val size: Long, private val input: DataInputStream,
                         private val output: DataOutputStream, private val permitted: () -> Boolean) {
        val uri: Uri = Uri.parse("bousygram-direct://${UUID.randomUUID()}/video")
        @Volatile private var closed = false
        @Volatile private var failed = false
        private fun active() = !closed && !failed && permitted()
        fun revoke() { closed = true }

        @Synchronized fun fetch(offset: Long, count: Int, buffer: ByteArray, start: Int) {
            try {
                if (!active() || !RollingLanDirectMediaPolicy.validRange(size, offset, count))
                    throw IOException("Direct media read refused")
                output.writeByte(READ)
                output.writeLong(offset)
                output.writeInt(count)
                output.flush()
                if (input.readInt() != count) throw IOException("Invalid direct media response")
                input.readFully(buffer, start, count)
                if (!active()) throw IOException("Direct media read revoked")
            } catch (error: Exception) {
                failed = true // never reuse a possibly desynchronized stream
                buffer.fill(0, start, start + count)
                throw error
            }
        }

        @Synchronized fun finish() {
            if (!active()) throw IOException("Direct media session unavailable")
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
