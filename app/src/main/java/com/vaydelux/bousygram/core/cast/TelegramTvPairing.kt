package com.vaydelux.bousygram.core.cast

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * One-time TV-issued pairing invitation. The secret is intended for a QR
 * displayed on the TV, not a six-digit password or a LAN broadcast.
 * Do not log, persist, or include it in a URL. No network transport here.
 */
internal class TelegramTvPairingOffer internal constructor(
    internal val id: ByteArray,
    internal val secret: ByteArray
) : AutoCloseable {
    override fun close() { secret.fill(0) }
}

/** Authenticated encryption of the fixed-size reference; never sends a local file ID. */
internal object TelegramTvPairingPacket {
    private const val VERSION: Byte = 1
    private const val ID_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val BODY_BYTES = 25
    private const val TAG_BYTES = 16
    const val PACKET_BYTES = 1 + ID_BYTES + NONCE_BYTES + BODY_BYTES + TAG_BYTES
    private val random = SecureRandom()

    fun newOffer(): TelegramTvPairingOffer = TelegramTvPairingOffer(
        ByteArray(ID_BYTES).also(random::nextBytes),
        ByteArray(32).also(random::nextBytes)
    )

    fun seal(offer: TelegramTvPairingOffer, request: TelegramTvHandoff): ByteArray? {
        if (!request.isValid() || offer.id.size != ID_BYTES || offer.secret.size != 32 ||
            offer.secret.all { it == 0.toByte() }) return null
        return try {
            val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
            val header = byteArrayOf(VERSION) + offer.id + nonce
            val plaintext = ByteBuffer.allocate(BODY_BYTES).putLong(request.chatId)
                .putLong(request.messageId).putLong(request.positionMs)
                .put(if (request.playRequested) 1.toByte() else 0.toByte()).array()
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(offer.secret, "AES"), GCMParameterSpec(128, nonce))
                cipher.updateAAD(header)
                header + cipher.doFinal(plaintext)
            } finally {
                plaintext.fill(0)
            }
        } catch (_: Exception) { null }
    }

    fun open(offer: TelegramTvPairingOffer, packet: ByteArray): TelegramTvHandoff? {
        if (packet.size != PACKET_BYTES || packet[0] != VERSION ||
            offer.id.size != ID_BYTES || offer.secret.size != 32 ||
            offer.secret.all { it == 0.toByte() }) return null
        val id = packet.copyOfRange(1, 1 + ID_BYTES)
        if (!java.security.MessageDigest.isEqual(id, offer.id)) return null
        val headerLength = 1 + ID_BYTES + NONCE_BYTES
        val nonce = packet.copyOfRange(1 + ID_BYTES, headerLength)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(offer.secret, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(packet.copyOfRange(0, headerLength))
            val plaintext = cipher.doFinal(packet, headerLength, packet.size - headerLength)
            try {
                val buffer = ByteBuffer.wrap(plaintext)
                val chat = buffer.long
                val message = buffer.long
                val position = buffer.long
                val intent = buffer.get().toInt()
                if (intent !in 0..1) null else
                    TelegramTvHandoff(chat, message, position, intent == 1).takeIf { it.isValid() }
            } finally {
                plaintext.fill(0)
            }
        } catch (_: Exception) { null }
    }
}

/**
 * TV-side invitation window. One valid packet consumes the offer before any
 * account lookup. A failed authentication does not consume it, but attempts
 * are capped. A monotonic clock is supplied by the host (elapsedRealtime).
 * The host must require a local confirmation before resolving the reference.
 */
internal class TelegramTvPairingWindow(private val nowMs: () -> Long) : AutoCloseable {
    private var offer: TelegramTvPairingOffer? = null
    private var issuedAt = 0L
    private var attempts = 0

    @Synchronized fun issue(): TelegramTvPairingOffer {
        close()
        val next = TelegramTvPairingPacket.newOffer()
        offer = next
        issuedAt = nowMs()
        attempts = 0
        // The QR/UI owns its copy; closing it cannot invalidate receiver storage.
        return TelegramTvPairingOffer(next.id.copyOf(), next.secret.copyOf())
    }

    @Synchronized fun receive(packet: ByteArray): TelegramTvHandoff? {
        val active = offer ?: return null
        val age = nowMs() - issuedAt
        if (age < 0L || age >= TTL_MS || attempts >= MAX_ATTEMPTS) {
            close()
            return null
        }
        attempts++
        val request = TelegramTvPairingPacket.open(active, packet)
        if (request != null || attempts >= MAX_ATTEMPTS) close()
        return request
    }

    @Synchronized override fun close() {
        offer?.close()
        offer = null
        issuedAt = 0
        attempts = 0
    }

    companion object {
        const val TTL_MS = 120_000L
        const val MAX_ATTEMPTS = 8
    }
}
