package com.vaydelux.bousygram.core.cast

import java.io.File
import java.net.InetAddress
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory

fun main(args: Array<String>) {
    val password = "qualification-only".toCharArray()
    fun load(role: String) = KeyStore.getInstance("PKCS12").apply {
        File(args[0], "$role.p12").inputStream().use { load(it, password) }
    }
    val tv = load("tv")
    val sender = load("sender")
    val rogue = load("rogue")
    fun cert(store: KeyStore) = store.getCertificate("peer") as X509Certificate
    fun pin(store: KeyStore) = RollingHlsPeerIdentity.fingerprint(cert(store))
    val address = checkNotNull(RollingHlsLanInvitation.privateAddress("192.168.1.20"))
    val endpoint = RollingHlsLanInvitation.Endpoint(address, 49152)
    RollingHlsPeerPairing.Offer(ByteArray(16) { 1 }, ByteArray(32) { 2 }, pin(tv)).use { offer ->
        val encoded = checkNotNull(RollingHlsLanInvitation.encode(endpoint, offer, cert(tv)))
        check(RollingHlsLanInvitation.encode(endpoint, offer, cert(rogue)) == null)
        checkNotNull(RollingHlsLanInvitation.decode(encoded)).use { decoded ->
            check(decoded.endpoint == endpoint && decoded.offer.secret.contentEquals(offer.secret))
            check(decoded.certificate.encoded.contentEquals(cert(tv).encoded))
        }
        for (invalid in listOf("127.0.0.1", "8.8.8.8", "192.168.01.20", "192.168.1.256", "localhost", "169.254.2.1")) {
            check(RollingHlsLanInvitation.decode(encoded.replace("192.168.1.20", invalid)) == null)
        }
        check(RollingHlsLanInvitation.decode(encoded.replace("49152", "65536")) == null)
        check(RollingHlsLanInvitation.decode(encoded + "=") == null)
        check(RollingHlsLanInvitation.decode(encoded.substringBeforeLast('|') + "|" +
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(cert(rogue).encoded)) == null)
    }
    check(RollingHlsLanInvitation.sameSubnet(address, checkNotNull(RollingHlsLanInvitation.privateAddress("192.168.1.99")), 24))
    check(!RollingHlsLanInvitation.sameSubnet(address, checkNotNull(RollingHlsLanInvitation.privateAddress("192.168.2.99")), 24))
    println("LAN_INVITATION_POLICY_PASS")

    fun context(own: KeyStore, remote: KeyStore): SSLContext {
        val manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(own, password) }
        val trust = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null); setCertificateEntry("peer", cert(remote)) }
        val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
        return SSLContext.getInstance("TLS").apply { init(manager.keyManagers, tm.trustManagers, null) }
    }
    val server = context(tv, sender)
    val local = InetAddress.getByName("127.0.0.1")
    fun exchange(client: SSLContext, clientPin: ByteArray, expected: Boolean) {
        val executor = Executors.newSingleThreadExecutor()
        try {
            (server.serverSocketFactory.createServerSocket(0, 1, local) as SSLServerSocket).use { listener ->
                listener.soTimeout = 5000
                val received = executor.submit<Boolean> {
                    runCatching { (listener.accept() as SSLSocket).use { RollingHlsLanProbe.receive(it, pin(sender)) { true } } }.isSuccess
                }
                val sent = runCatching {
                    (client.socketFactory.createSocket(local, listener.localPort) as SSLSocket).use { RollingHlsLanProbe.send(it, clientPin) { true } }
                }.isSuccess
                check(sent == expected && received.get(8, TimeUnit.SECONDS) == expected)
            }
        } finally { executor.shutdownNow(); check(executor.awaitTermination(2, TimeUnit.SECONDS)) }
    }
    exchange(context(sender, tv), pin(tv), true)
    exchange(context(rogue, tv), pin(tv), false)
    exchange(context(sender, tv), pin(rogue), false)
    println("LAN_MTLS_PROBE_AND_REJECTION_PASS bytes=${RollingHlsLanProbe.BYTES}")
}
