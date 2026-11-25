package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory

/**
 * Host replay of real Android-generated synthetic fMP4. Exact application
 * assembler, verifier, store and delivery dispatcher; loopback TLS 1.3
 * mutual authentication plus out-of-band leaf pins. This receiver verifies
 * byte consumption, NOT decoder/playback state. Not a LAN or HTTP server.
 */
private const val TIMEOUT = 10_000
private const val CHUNK = 32 * 1024
private val password = "qualification-only".toCharArray()

private fun keyStore(file: File): KeyStore = KeyStore.getInstance("PKCS12").apply {
    file.inputStream().use { load(it, password) }
}
private fun context(keys: File, trust: File): SSLContext {
    val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    km.init(keyStore(keys), password)
    val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    tm.init(keyStore(trust))
    return SSLContext.getInstance("TLS").apply { init(km.keyManagers, tm.trustManagers, null) }
}
private fun pin(file: File): ByteArray =
    RollingHlsPeerIdentity.fingerprint(keyStore(file).getCertificate("peer") as X509Certificate)
private fun digest() = MessageDigest.getInstance("SHA-256")
private fun sha(file: File): ByteArray {
    val hash = digest()
    file.inputStream().use { input ->
        val buffer = ByteArray(CHUNK)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            hash.update(buffer, 0, n)
        }
    }
    return hash.digest()
}
private fun listener(ctx: SSLContext): SSLServerSocket =
    (ctx.serverSocketFactory.createServerSocket(0, 1, InetAddress.getByName("127.0.0.1")) as SSLServerSocket).apply {
        enabledProtocols = arrayOf("TLSv1.3")
        needClientAuth = true
        soTimeout = TIMEOUT
    }
private fun connect(ctx: SSLContext, port: Int): SSLSocket =
    (ctx.socketFactory.createSocket("localhost", port) as SSLSocket).apply {
        enabledProtocols = arrayOf("TLSv1.3")
        soTimeout = TIMEOUT
        sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
    }

private fun receive(socket: SSLSocket, expectedPin: ByteArray, expectedFragments: Long,
                    expectedHash: ByteArray): Long {
    socket.soTimeout = TIMEOUT
    socket.startHandshake()
    RollingHlsPeerIdentity.requirePeer(socket.session, expectedPin)
    val input = DataInputStream(socket.inputStream)
    val output = DataOutputStream(socket.outputStream)
    val all = digest()
    val buffer = ByteArray(CHUNK)
    var sequence = 0L
    var bytes = 0L
    var total = 0L
    var unit = digest()
    while (true) {
        val seq = input.readLong()
        val n = input.readInt()
        if (seq == -1L) {
            check(n == 0 && sequence == expectedFragments + 1 && bytes == 0L)
            check(MessageDigest.isEqual(all.digest(), expectedHash)) { "Receiver byte conservation failed" }
            output.writeLong(-1L); output.flush()
            return total
        }
        check(seq == sequence && n in 0..CHUNK)
        if (n == 0) {
            check(bytes > 0)
            output.writeLong(sequence)
            output.writeLong(bytes)
            output.write(unit.digest())
            output.flush()
            sequence++; bytes = 0; unit = digest()
        } else {
            val limit = if (sequence == 0L) 512L * 1024 else RollingHlsBudget.MAX_SEGMENT_BYTES
            check(bytes <= limit - n)
            input.readFully(buffer, 0, n)
            unit.update(buffer, 0, n); all.update(buffer, 0, n)
            bytes += n; total += n
        }
    }
}

private class Sender(socket: SSLSocket) {
    private val input = DataInputStream(socket.inputStream)
    private val output = DataOutputStream(socket.outputStream)
    private var sequence = 0L
    private var size = 0L
    private var hash = digest()
    fun write(seq: Long, data: ByteArray, count: Int) {
        if (seq == 1L && sequence == 0L) check(endUnit(0))
        check(seq == sequence && count in 1..CHUNK && count <= data.size)
        output.writeLong(seq); output.writeInt(count); output.write(data, 0, count)
        hash.update(data, 0, count); size += count
    }
    fun endUnit(seq: Long): Boolean {
        check(seq == sequence && size > 0)
        output.writeLong(seq); output.writeInt(0); output.flush()
        val ack = input.readLong()
        val length = input.readLong()
        val remoteHash = ByteArray(32).also { input.readFully(it) }
        val ok = ack == sequence && length == size && MessageDigest.isEqual(hash.digest(), remoteHash)
        sequence++; size = 0; hash = digest()
        return ok
    }
    fun finish() {
        check(size == 0L)
        output.writeLong(-1L); output.writeInt(0); output.flush()
        check(input.readLong() == -1L)
    }
}

private fun replay(file: File, directory: File, server: SSLContext, client: SSLContext,
                   serverPin: ByteArray, clientPin: ByteArray, count: Long) {
    val expected = sha(file)
    val executor = Executors.newSingleThreadExecutor()
    val owner = AccountSnapshot(1, 1)
    var peak = 0L
    try {
        listener(server).use { listener ->
            val receiver = executor.submit<Long> {
                (listener.accept() as SSLSocket).use { receive(it, clientPin, count, expected) }
            }
            connect(client, listener.localPort).use { socket ->
                socket.startHandshake()
                RollingHlsPeerIdentity.requirePeer(socket.session, serverPin)
                val sender = Sender(socket)
                RollingHlsControlledQualification(owner, 7, directory, { it == owner }, { true },
                    { System.nanoTime() / 1_000_000L }, { true },
                    { bytes, n -> sender.write(0, bytes, n) },
                    { segment, bytes, n -> sender.write(segment.sequence, bytes, n) },
                    { segment -> sender.endUnit(segment.sequence) }).use { q ->
                    file.inputStream().use { input ->
                        val chunk = ByteArray(4093)
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            q.write(chunk, 0, n)
                            check(q.snapshot().size <= 2)
                            peak = maxOf(peak, q.retainedBytes())
                        }
                    }
                    q.finish()
                    check(q.consumeFinal())
                    check(q.acknowledgedCount() == count - 1)
                    check(q.snapshot().single().sequence == count)
                    check(q.playlist()?.endsWith("#EXT-X-ENDLIST\n") == true)
                    sender.finish()
                }
            }
            check(receiver.get(TIMEOUT.toLong(), TimeUnit.MILLISECONDS) == file.length())
        }
    } finally {
        executor.shutdownNow()
        check(executor.awaitTermination(TIMEOUT.toLong(), TimeUnit.MILLISECONDS))
    }
    check(directory.listFiles()?.isEmpty() == true)
    check(peak in 1L..RollingHlsBudget.MAX_WINDOW_BYTES)
    println("MTLS_REPLAY_PASS ${file.name} fragments=$count bytes=${file.length()} retained_peak=$peak scratch_remaining=0")
}

private fun rejected(server: SSLContext, client: SSLContext, serverPin: ByteArray, clientPin: ByteArray,
                     label: String) {
    val executor = Executors.newSingleThreadExecutor()
    try {
        listener(server).use { listener ->
            val peer = executor.submit<Boolean> {
                try {
                    (listener.accept() as SSLSocket).use { socket ->
                        socket.soTimeout = TIMEOUT
                        socket.startHandshake()
                        RollingHlsPeerIdentity.requirePeer(socket.session, clientPin)
                        socket.outputStream.write(1); socket.outputStream.flush()
                        socket.inputStream.read() == 1
                    }
                } catch (_: Exception) { false }
            }
            val accepted = try {
                connect(client, listener.localPort).use { socket ->
                    socket.startHandshake()
                    RollingHlsPeerIdentity.requirePeer(socket.session, serverPin)
                    if (socket.inputStream.read() != 1) false
                    else { socket.outputStream.write(1); socket.outputStream.flush(); true }
                }
            } catch (_: Exception) { false }
            check(!accepted && !peer.get(TIMEOUT.toLong(), TimeUnit.MILLISECONDS)) { "Unexpected peer accepted: $label" }
            println("MTLS_REJECT_PASS $label")
        }
    } finally {
        executor.shutdownNow()
        check(executor.awaitTermination(TIMEOUT.toLong(), TimeUnit.MILLISECONDS))
    }
}

fun main(args: Array<String>) {
    check(args.size == 3)
    val fixtures = File(args[0])
    val scratch = File(args[1])
    val certs = File(args[2])
    val server = context(File(certs, "server.p12"), File(certs, "server-trust.p12"))
    val client = context(File(certs, "client.p12"), File(certs, "client-trust.p12"))
    val rogue = context(File(certs, "rogue.p12"), File(certs, "client-trust.p12"))
    val serverPin = pin(File(certs, "server.p12"))
    val clientPin = pin(File(certs, "client.p12"))
    val roguePin = pin(File(certs, "rogue.p12"))
    rejected(server, rogue, serverPin, clientPin, "untrusted client certificate")
    rejected(server, client, roguePin, clientPin, "incorrect server pin")
    rejected(server, client, serverPin, roguePin, "incorrect client pin")
    for ((subdir, count) in listOf("" to 12L, "extended" to 60L)) {
        for (ext in listOf("mkv", "ts")) {
            val file = File(File(fixtures, subdir), "converted.$ext.mp4")
            check(file.length() in 1L..64L * 1024 * 1024)
            val directory = File(scratch, "segments-$count-$ext")
            check(directory.mkdir())
            replay(file, directory, server, client, serverPin, clientPin, count)
            check(directory.delete())
        }
    }
}
