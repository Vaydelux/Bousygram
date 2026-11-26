package com.vaydelux.bousygram.core.cast

import java.net.Inet4Address
import java.net.InetAddress
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

/** Versioned visual bootstrap. No DNS, public IP, URI or arbitrary endpoint.
 * The exact receiver certificate is transferred out of band, not learned
 * from the connection. Treat the entire encoded text as an ephemeral secret. */
internal object RollingHlsLanInvitation {
    private const val PREFIX = "BGLAN1"
    const val MAX_TEXT = 1600
    private const val MAX_CERT = 1024

    data class Endpoint(val address: Inet4Address, val port: Int) {
        init { require(isPrivate(address) && port in 1..65535) }
    }
    class Decoded(val endpoint: Endpoint, val offer: RollingHlsPeerPairing.Offer,
                  val certificate: X509Certificate) : AutoCloseable {
        override fun close() = offer.close()
    }

    fun privateAddress(text: String): Inet4Address? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        for ((i, part) in parts.withIndex()) {
            val n = part.toIntOrNull() ?: return null
            if (n !in 0..255 || n.toString() != part) return null
            bytes[i] = n.toByte()
        }
        return (InetAddress.getByAddress(bytes) as Inet4Address).takeIf(::isPrivate)
    }

    fun isPrivate(address: Inet4Address): Boolean {
        val bytes = address.address
        val a = bytes[0].toInt() and 255
        val b = bytes[1].toInt() and 255
        return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
    }

    fun sameSubnet(local: Inet4Address, remote: Inet4Address, prefix: Int): Boolean {
        if (!isPrivate(local) || !isPrivate(remote) || prefix !in 1..32) return false
        val a = local.address
        val b = remote.address
        return (0 until prefix).all { bit ->
            ((a[bit / 8].toInt() xor b[bit / 8].toInt()) and (1 shl (7 - bit % 8))) == 0
        }
    }

    fun encode(endpoint: Endpoint, offer: RollingHlsPeerPairing.Offer, cert: X509Certificate): String? =
        try {
            val der = cert.encoded
            RollingHlsEnrollment.validate(cert)
            if (der.size !in 1..MAX_CERT || !MessageDigest.isEqual(offer.receiverPin,
                    RollingHlsPeerIdentity.fingerprint(cert))) null
            else RollingHlsPairingQr.encode(offer)?.let { invitation ->
                listOf(PREFIX, endpoint.address.hostAddress, endpoint.port.toString(), invitation,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(der)).joinToString("|")
                    .takeIf { it.length <= MAX_TEXT }
            }
        } catch (_: Exception) { null }

    fun decode(value: String): Decoded? {
        if (value.length !in 1..MAX_TEXT) return null
        val fields = value.split('|')
        if (fields.size != 5 || fields[0] != PREFIX) return null
        val address = privateAddress(fields[1]) ?: return null
        val port = fields[2].toIntOrNull()?.takeIf { it in 1..65535 && it.toString() == fields[2] } ?: return null
        if (fields[4].any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it != '-' && it != '_' }) return null
        val offer = RollingHlsPairingQr.decode(fields[3]) ?: return null
        var success = false
        try {
            val der = Base64.getUrlDecoder().decode(fields[4])
            if (der.size !in 1..MAX_CERT || Base64.getUrlEncoder().withoutPadding().encodeToString(der) != fields[4]) return null
            val cert = der.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate }
            RollingHlsEnrollment.validate(cert)
            if (!cert.encoded.contentEquals(der) || !MessageDigest.isEqual(offer.receiverPin,
                    RollingHlsPeerIdentity.fingerprint(cert))) return null
            return Decoded(Endpoint(address, port), offer, cert).also { success = true }
        } catch (_: Exception) { return null }
        finally { if (!success) offer.close() }
    }
}
