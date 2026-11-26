package com.vaydelux.bousygram.core.cast

import org.junit.Assert.*
import org.junit.Test

class RollingHlsPairingQrTest {
    @Test fun fixedSizeRoundTripAndStrictSyntax() {
        val id = ByteArray(16) { 1 }
        val secret = ByteArray(32) { 2 }
        val receiver = ByteArray(32) { 3 }
        RollingHlsPeerPairing.Offer(id, secret, receiver).use { offer ->
            val text = RollingHlsPairingQr.encode(offer)!!
            assertEquals(116, text.length)
            assertFalse(text.contains("://"))
            RollingHlsPairingQr.decode(text)!!.use { decoded ->
                assertArrayEquals(id, decoded.id)
                assertArrayEquals(secret, decoded.secret)
                assertArrayEquals(receiver, decoded.receiverPin)
            }
            for (bad in listOf(text.dropLast(1), text + "=", " " + text,
                "BGPAIR2:" + text.substring(8), text.replaceRange(10, 11, "/")))
                assertNull(RollingHlsPairingQr.decode(bad))
            offer.close()
            assertNull(RollingHlsPairingQr.encode(offer))
        }
    }
}
