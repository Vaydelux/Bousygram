package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.Signature
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509KeyManager

/**
 * Build a TLS context from exactly one Android Keystore identity and the
 * certificate whose fingerprint was explicitly enrolled out of band.
 * No global AndroidKeyStore alias selection, trust-all or TOFU. The caller
 * must perform the TLS1.3 handshake and exact peer pin check before any data.
 */
internal object RollingHlsPairedTls {
    class Peer internal constructor(val context: SSLContext, pin: ByteArray) {
        private val expected = pin.copyOf()
        fun pin() = expected.copyOf()
    }

    fun create(owner: AccountSnapshot, pins: RollingHlsPeerPairing.Pins,
               identity: RollingHlsKeystoreIdentity, peer: X509Certificate,
               receiver: Boolean, current: (AccountSnapshot) -> Boolean): Peer {
        check(pins.owner == owner && current(owner))
        val ownPin = if (receiver) pins.receiverPin() else pins.senderPin()
        val peerPin = if (receiver) pins.senderPin() else pins.receiverPin()
        val local = identity.certificate()
        local.checkValidity()
        peer.checkValidity()
        check(MessageDigest.isEqual(ownPin, RollingHlsPeerIdentity.fingerprint(local)))
        check(MessageDigest.isEqual(peerPin, RollingHlsPeerIdentity.fingerprint(peer)))
        check(!MessageDigest.isEqual(ownPin, peerPin))
        // These qualification certificates are self-signed, enrolled as
        // exact leaf trust anchors. No default public CA trust is added.
        peer.verify(peer.publicKey)
        // Fail before opening a listener if the platform cannot sign a
        // prehashed ECDSA transcript with this nonexportable key.
        val challenge = ByteArray(32).also(SecureRandom()::nextBytes)
        try {
            val signature = Signature.getInstance("NONEwithECDSA").run {
                initSign(identity.privateKey())
                update(challenge)
                sign()
            }
            try {
                check(Signature.getInstance("NONEwithECDSA").run {
                    initVerify(local.publicKey)
                    update(challenge)
                    verify(signature)
                }) { "TLS signing preflight failed" }
            } finally { signature.fill(0) }
        } finally { challenge.fill(0) }
        check(current(owner))
        val trust = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("enrolled-peer", peer)
        }
        val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tm.init(trust)
        val manager = object : X509KeyManager {
            private fun aliases(type: String?): Array<String>? =
                if (current(owner) && type in listOf("EC", "EC_EC", "EC_RSA")) arrayOf("session") else null
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = aliases(keyType)
            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = aliases(keyType)
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? =
                keyType?.firstNotNullOfOrNull { aliases(it)?.firstOrNull() }
            override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? =
                aliases(keyType)?.firstOrNull()
            override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
                if (alias == "session" && current(owner)) arrayOf(identity.certificate()) else null
            override fun getPrivateKey(alias: String?): PrivateKey? =
                if (alias == "session" && current(owner)) identity.privateKey() else null
        }
        val context = SSLContext.getInstance("TLS").apply {
            init(arrayOf(manager), tm.trustManagers, null)
        }
        check(current(owner))
        return Peer(context, peerPin)
    }
}
