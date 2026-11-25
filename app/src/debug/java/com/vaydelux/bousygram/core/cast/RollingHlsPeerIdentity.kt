package com.vaydelux.bousygram.core.cast

import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession

/**
 * Additional peer identity gate AFTER a successful mutually authenticated
 * TLS handshake. Does not create a trust manager, bypass certificate checks,
 * establish pairing or authorize Telegram resources. Pins are provisioned
 * through a trusted out-of-band channel, never learned from the connection.
 */
internal object RollingHlsPeerIdentity {
    fun fingerprint(certificate: X509Certificate): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded)

    fun requirePeer(session: SSLSession, expected: ByteArray) {
        if (expected.size != 32 || session.protocol != "TLSv1.3")
            throw SSLPeerUnverifiedException("Unsupported peer identity policy")
        val leaf = session.peerCertificates.firstOrNull() as? X509Certificate
            ?: throw SSLPeerUnverifiedException("Missing peer certificate")
        leaf.checkValidity()
        if (!MessageDigest.isEqual(expected, fingerprint(leaf)))
            throw SSLPeerUnverifiedException("Unexpected peer identity")
    }
}
