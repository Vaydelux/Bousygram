package com.vaydelux.bousygram.core.cast

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.net.ssl.SSLSocket

/** Debug connectivity proof: deterministic synthetic bytes, never user media.
 * Both peers require the enrolled TLS1.3 leaf before any application bytes. */
internal object RollingHlsLanProbe {
    const val BYTES = 1024 * 1024
    private const val MAGIC = 0x42475031
    private fun pattern(index: Int): Byte = ((index * 31) xor (index ushr 7)).toByte()
    private fun access(permitted: () -> Boolean) {
        if (!permitted()) throw IOException("LAN probe revoked")
    }
    fun send(socket: SSLSocket, pin: ByteArray, permitted: () -> Boolean) {
        access(permitted)
        socket.useClientMode = true
        socket.enabledProtocols = arrayOf("TLSv1.3")
        socket.soTimeout = 5000
        socket.startHandshake()
        RollingHlsPeerIdentity.requirePeer(socket.session, pin)
        access(permitted)
        val output = DataOutputStream(socket.outputStream)
        val input = DataInputStream(socket.inputStream)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        try {
            output.writeInt(MAGIC)
            output.writeInt(BYTES)
            var offset = 0
            while (offset < BYTES) {
                access(permitted)
                val n = minOf(buffer.size, BYTES - offset)
                for (i in 0 until n) buffer[i] = pattern(offset + i)
                digest.update(buffer, 0, n)
                output.write(buffer, 0, n)
                offset += n
            }
            output.flush()
            access(permitted)
            if (input.readInt() != MAGIC) throw IOException("Invalid probe receipt")
            val receipt = ByteArray(32).also { input.readFully(it) }
            if (!MessageDigest.isEqual(digest.digest(), receipt)) throw IOException("Probe integrity mismatch")
            access(permitted)
        } finally { buffer.fill(0) }
    }
    fun receive(socket: SSLSocket, pin: ByteArray, permitted: () -> Boolean) {
        access(permitted)
        socket.useClientMode = false
        socket.needClientAuth = true
        socket.enabledProtocols = arrayOf("TLSv1.3")
        socket.soTimeout = 5000
        socket.startHandshake()
        RollingHlsPeerIdentity.requirePeer(socket.session, pin)
        access(permitted)
        val input = DataInputStream(socket.inputStream)
        val output = DataOutputStream(socket.outputStream)
        if (input.readInt() != MAGIC || input.readInt() != BYTES) throw IOException("Invalid probe length")
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        try {
            var offset = 0
            while (offset < BYTES) {
                access(permitted)
                val n = minOf(buffer.size, BYTES - offset)
                input.readFully(buffer, 0, n)
                access(permitted)
                for (i in 0 until n) if (buffer[i] != pattern(offset + i)) throw IOException("Invalid probe payload")
                digest.update(buffer, 0, n)
                offset += n
            }
            access(permitted)
            output.writeInt(MAGIC)
            output.write(digest.digest())
            output.flush()
            access(permitted)
        } finally { buffer.fill(0) }
    }
}
