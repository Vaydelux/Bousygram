package com.vaydelux.bousygram.core.cast

import java.util.Base64

/**
 * Fixed-size QR payload for the existing one-time receiver invitation.
 * Not a URL, no address, account ID or control token. Only use through a
 * trusted visual channel; never log, persist or share this secret string.
 * Receiver-side monotonic expiry and local confirmation remain mandatory.
 * The QR string is necessarily immutable JVM text and cannot be zeroized.
 */
internal object RollingHlsPairingQr {
    private const val PREFIX = "BGPAIR1:"
    private const val BYTES = 81
    private const val ENCODED_LENGTH = 108

    fun encode(offer: RollingHlsPeerPairing.Offer): String? {
        if (offer.id.size != 16 || offer.secret.size != 32 || offer.receiverPin.size != 32 ||
            offer.secret.all { it == 0.toByte() }) return null
        val data = ByteArray(BYTES)
        return try {
            data[0] = 1
            offer.id.copyInto(data, 1)
            offer.secret.copyInto(data, 17)
            offer.receiverPin.copyInto(data, 49)
            PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(data)
        } finally { data.fill(0) }
    }

    fun decode(value: String): RollingHlsPeerPairing.Offer? {
        if (value.length != PREFIX.length + ENCODED_LENGTH || !value.startsWith(PREFIX)) return null
        val encoded = value.substring(PREFIX.length)
        if (encoded.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it != '-' && it != '_' })
            return null
        val data = try { Base64.getUrlDecoder().decode(encoded) } catch (_: IllegalArgumentException) { return null }
        return try {
            if (data.size != BYTES || data[0] != 1.toByte() ||
                (17 until 49).all { data[it] == 0.toByte() }) null
            else RollingHlsPeerPairing.Offer(data.copyOfRange(1,17),
                data.copyOfRange(17,49),data.copyOfRange(49,81))
        } finally { data.fill(0) }
    }
}
