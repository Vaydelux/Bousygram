package com.vaydelux.bousygram.core.cast

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** Bootstrap only: QR-pinned TLS server plus sender proof of key possession.
 * A successful exchange creates a pending invitation, not an authorized peer.
 * Caller owns sockets, deadline, local consent and subsequent mutual TLS. */
internal object RollingHlsEnrollment {
    private const val MAGIC = 0x42474531
    private val domain = "Bousygram/enrollment/v1".toByteArray(Charsets.US_ASCII)
    data class Request(val pending: RollingHlsPeerPairing.Pending, val certificate: X509Certificate)

    private fun validate(cert: X509Certificate) {
        cert.checkValidity()
        val key = cert.publicKey as? ECPublicKey ?: throw CertificateException("Expected EC identity")
        if (key.params.curve.field.fieldSize != 256 || key.params.order.bitLength() != 256 ||
            cert.subjectX500Principal != cert.issuerX500Principal || cert.encoded.size !in 1..4096)
            throw CertificateException("Invalid enrollment identity")
        cert.verify(key)
    }

    fun clientContext(pin: ByteArray): SSLContext {
        require(pin.size == 32)
        val expected = pin.copyOf()
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException("Client trust not supported")
            }
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val cert = chain?.singleOrNull() ?: throw CertificateException("Expected one enrolled leaf")
                try {
                    validate(cert)
                    if (!MessageDigest.isEqual(expected, RollingHlsPeerIdentity.fingerprint(cert)))
                        throw CertificateException("Incorrect receiver pin")
                } catch (error: Exception) { throw CertificateException("Receiver identity refused", error) }
            }
        }
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    }

    private fun read(input: DataInputStream, size: Int): ByteArray = ByteArray(size).also { input.readFully(it) }
    private fun field(input: DataInputStream, limit: Int): ByteArray {
        val size = input.readUnsignedShort()
        if (size !in 1..limit) throw IOException("Invalid enrollment field size")
        return read(input, size)
    }
    private fun field(output: DataOutputStream, value: ByteArray, limit: Int) {
        if (value.size !in 1..limit) throw IOException("Invalid enrollment field size")
        output.writeShort(value.size)
        output.write(value)
    }
    private fun transcript(signature: Signature, challenge: ByteArray, pin: ByteArray, proof: ByteArray) {
        signature.update(domain)
        signature.update(challenge)
        signature.update(pin)
        signature.update(proof)
    }

    fun send(socket: SSLSocket, offer: RollingHlsPeerPairing.Offer, cert: X509Certificate,
             key: PrivateKey, permitted: () -> Boolean): Boolean {
        check(permitted())
        validate(cert)
        socket.useClientMode = true
        socket.enabledProtocols = arrayOf("TLSv1.3")
        socket.soTimeout = 5000
        socket.startHandshake()
        RollingHlsPeerIdentity.requirePeer(socket.session, offer.receiverPin)
        check(permitted())
        val input = DataInputStream(socket.inputStream)
        val output = DataOutputStream(socket.outputStream)
        if (input.readInt() != MAGIC) throw IOException("Invalid enrollment version")
        val challenge = read(input, 32)
        val pin = RollingHlsPeerIdentity.fingerprint(cert)
        val proof = checkNotNull(RollingHlsPeerPairing.proofFor(offer, pin))
        try {
            val signature = Signature.getInstance("SHA256withECDSA").run {
                initSign(key); transcript(this, challenge, pin, proof); sign()
            }
            try {
                check(permitted())
                output.writeInt(MAGIC)
                field(output, cert.encoded, 4096)
                output.write(proof)
                field(output, signature, 128)
                output.flush()
                val status = input.readUnsignedByte()
                if (status !in 0..1) throw IOException("Invalid enrollment status")
                return status == 1 && permitted()
            } finally { signature.fill(0) }
        } finally { challenge.fill(0); proof.fill(0) }
    }

    fun receive(socket: SSLSocket, pairing: RollingHlsPeerPairing, permitted: () -> Boolean): Request? {
        check(permitted())
        socket.useClientMode = false
        socket.needClientAuth = false
        socket.enabledProtocols = arrayOf("TLSv1.3")
        socket.soTimeout = 5000
        socket.startHandshake()
        check(permitted())
        val input = DataInputStream(socket.inputStream)
        val output = DataOutputStream(socket.outputStream)
        val challenge = ByteArray(32).also(SecureRandom()::nextBytes)
        try {
            output.writeInt(MAGIC); output.write(challenge); output.flush()
            val request = try {
                if (input.readInt() != MAGIC) throw IOException("Invalid enrollment version")
                val der = field(input, 4096)
                val cert = der.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate }
                if (!cert.encoded.contentEquals(der)) throw CertificateException("Noncanonical identity")
                validate(cert)
                val proof = read(input, 32)
                try {
                    val sig = field(input, 128)
                    val pin = RollingHlsPeerIdentity.fingerprint(cert)
                    val valid = Signature.getInstance("SHA256withECDSA").run {
                        initVerify(cert.publicKey); transcript(this, challenge, pin, proof); verify(sig)
                    }
                    if (!valid || !permitted()) null else pairing.receive(pin, proof)?.let { Request(it, cert) }
                } finally { proof.fill(0) }
            } catch (_: Exception) { null }
            if (!permitted()) throw IOException("Enrollment revoked")
            output.writeByte(if (request == null) 0 else 1)
            output.flush()
            return request
        } finally { challenge.fill(0) }
    }
}
