package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import java.io.File
import java.net.InetAddress
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

fun main(args: Array<String>) {
    val pass = "qualification-only".toCharArray()
    fun load(role: String) = KeyStore.getInstance("PKCS12").apply { File(args[0], "$role.p12").inputStream().use { load(it, pass) } }
    val tv = load("tv")
    val sender = load("sender")
    val rogue = load("rogue")
    val cert = sender.getCertificate("peer") as X509Certificate
    val key = sender.getKey("peer", pass) as PrivateKey
    val wrongKey = rogue.getKey("peer", pass) as PrivateKey
    val pin = RollingHlsPeerIdentity.fingerprint(tv.getCertificate("peer") as X509Certificate)
    val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(tv, pass) }
    val context = SSLContext.getInstance("TLS").apply { init(km.keyManagers, null, null) }
    val owner = AccountSnapshot(1, 1)
    var now = 0L
    val pairing = RollingHlsPeerPairing({ it == owner }, { now })
    val loopback = InetAddress.getByName("127.0.0.1")
    fun exchange(offer: RollingHlsPeerPairing.Offer, signingKey: PrivateKey, expectedPin: ByteArray = pin): Pair<Boolean, RollingHlsEnrollment.Request?> {
        val executor = Executors.newSingleThreadExecutor()
        try {
            (context.serverSocketFactory.createServerSocket(0, 1, loopback) as SSLServerSocket).use { listener ->
                listener.soTimeout = 5000
                val result = executor.submit<RollingHlsEnrollment.Request?> {
                    try { (listener.accept() as SSLSocket).use { RollingHlsEnrollment.receive(it, pairing) { true } } }
                    catch (_: Exception) { null }
                }
                val accepted = try {
                    (RollingHlsEnrollment.clientContext(tv.getCertificate("peer") as X509Certificate, expectedPin).socketFactory.createSocket(loopback, listener.localPort) as SSLSocket).use {
                        RollingHlsEnrollment.send(it, offer, cert, signingKey) { true }
                    }
                } catch (_: Exception) { false }
                return accepted to result.get(8, TimeUnit.SECONDS)
            }
        } finally { executor.shutdownNow(); check(executor.awaitTermination(2, TimeUnit.SECONDS)) }
    }
    pairing.use {
        pairing.issue(owner, pin)!!.use { offer ->
            check(exchange(offer, key, ByteArray(32)) == (false to null))
            check(exchange(offer, wrongKey) == (false to null))
            val wrong = RollingHlsPeerPairing.Offer(offer.id.copyOf(), offer.secret.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }, pin)
            wrong.use { check(exchange(it, key) == (false to null)) }
            val (accepted, request) = exchange(offer, key)
            check(accepted && request != null)
            check(request.certificate.encoded.contentEquals(cert.encoded))
            check(exchange(offer, key) == (false to null))
            check(pairing.confirm(request.pending, false) == null)
            println("ENROLLMENT_PIN_KEY_SECRET_REPLAY_DENIAL_PASS")
        }
        pairing.issue(owner, pin)!!.use { offer ->
            val (accepted, request) = exchange(offer, key)
            check(accepted && request != null)
            val pins = checkNotNull(pairing.confirm(request.pending, true))
            check(pins.senderPin().contentEquals(RollingHlsPeerIdentity.fingerprint(cert)))
            check(pins.receiverPin().contentEquals(pin))
            println("ENROLLMENT_CONSENT_PASS")
        }
        pairing.issue(owner, pin)!!.use { offer ->
            now = 120_000
            check(exchange(offer, key) == (false to null))
            println("ENROLLMENT_EXPIRY_PASS")
        }
    }
}
