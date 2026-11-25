package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Ephemeral out-of-band certificate pin enrollment for a controlled receiver.
 * A trusted QR channel must carry the offer and receiver pin; an authenticated
 * proof is NOT user consent. No storage, QR UI, socket or certificate creation.
 * Existing Telegram reference handoff remains separate.
 */
internal class RollingHlsPeerPairing(
    private val current: (AccountSnapshot) -> Boolean,
    private val nowMs: () -> Long
) : AutoCloseable {
    class Offer internal constructor(val id: ByteArray, val secret: ByteArray,
                                    val receiverPin: ByteArray) : AutoCloseable {
        override fun close() { secret.fill(0) }
    }
    class Pending internal constructor(internal val owner: AccountSnapshot,
                                      senderPin: ByteArray, receiverPin: ByteArray) {
        private val sender = senderPin.copyOf()
        private val receiver = receiverPin.copyOf()
        fun senderPin() = sender.copyOf()
        internal fun confirmedPins() = Pins(owner, sender, receiver)
    }
    class Pins internal constructor(val owner: AccountSnapshot, sender: ByteArray, receiver: ByteArray) {
        private val sender = sender.copyOf()
        private val receiver = receiver.copyOf()
        fun senderPin() = sender.copyOf()
        fun receiverPin() = receiver.copyOf()
    }
    private val random = SecureRandom()
    private var owner: AccountSnapshot? = null
    private var offer: Offer? = null
    private var pending: Pending? = null
    private var deadline = 0L
    private var issued = 0L
    private var attempts = 0

    @Synchronized fun issue(owner: AccountSnapshot, receiverPin: ByteArray): Offer? {
        close()
        val now = nowMs()
        if (!current(owner) || receiverPin.size != 32 || now < 0 || now > Long.MAX_VALUE - TTL_MS)
            return null
        val next = Offer(ByteArray(16).also(random::nextBytes), ByteArray(32).also(random::nextBytes),
            receiverPin.copyOf())
        this.owner = owner
        offer = next
        issued = now
        deadline = now + TTL_MS
        return Offer(next.id.copyOf(), next.secret.copyOf(), next.receiverPin.copyOf())
    }

    private fun valid(): Boolean {
        val account = owner ?: return false
        if (!current(account) || nowMs() !in issued until deadline) {
            close()
            return false
        }
        return true
    }

    @Synchronized fun receive(senderPin: ByteArray, proof: ByteArray): Pending? {
        if (!valid()) return null
        val active = offer ?: return null
        attempts++
        val expected = proofFor(active, senderPin)
        val ok = expected != null && proof.size == 32 && MessageDigest.isEqual(expected, proof)
        expected?.fill(0)
        if (!ok) {
            if (attempts >= MAX_ATTEMPTS) close()
            return null
        }
        val next = Pending(checkNotNull(owner), senderPin.copyOf(), active.receiverPin.copyOf())
        active.close()
        offer = null
        pending = next
        return next
    }

    /** Caller must obtain a separate local confirmation showing the peer
     * identity. Consume the exact pending ticket even on denial. */
    @Synchronized fun confirm(ticket: Pending, acceptedOnTv: Boolean): Pins? {
        if (!valid() || pending !== ticket) return null
        val result = if (acceptedOnTv && current(ticket.owner))
            ticket.confirmedPins() else null
        close()
        return result
    }

    @Synchronized override fun close() {
        offer?.close()
        offer = null
        pending = null
        owner = null
        attempts = 0
        issued = 0
        deadline = 0
    }

    companion object {
        private const val TTL_MS = 120_000L
        private const val MAX_ATTEMPTS = 8

        /** Authenticate both roles and the exact one-time invitation.
         * The sender must have independently obtained the QR offer. */
        fun proofFor(offer: Offer, senderPin: ByteArray): ByteArray? {
            if (offer.id.size != 16 || offer.secret.size != 32 || offer.receiverPin.size != 32 ||
                senderPin.size != 32 || offer.secret.all { it == 0.toByte() } ||
                MessageDigest.isEqual(senderPin, offer.receiverPin)) return null
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(offer.secret, "HmacSHA256"))
            mac.update("Bousygram/rolling-peer-pairing/v1".toByteArray(Charsets.US_ASCII))
            mac.update(offer.id)
            mac.update(offer.receiverPin)
            mac.update(senderPin)
            return mac.doFinal()
        }
    }
}
