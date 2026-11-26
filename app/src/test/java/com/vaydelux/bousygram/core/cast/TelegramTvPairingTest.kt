package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSessionController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TelegramTvPairingTest {
    private val request = TelegramTvHandoff(-100123L, 42L, 5000L, false)

    @Test fun encryptedPacketIsSingleUseAndTamperingFails() {
        val window = TelegramTvPairingWindow { 1000L }
        window.issue().use { offer ->
            val packet = TelegramTvPairingPacket.seal(offer, request)!!
            assertEquals(TelegramTvPairingPacket.PACKET_BYTES, packet.size)
            val tampered = packet.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
            assertNull(window.receive(tampered))
            assertEquals(request, window.receive(packet))
            assertNull(window.receive(packet))
        }
    }

    @Test fun expiredReplacedAndExhaustedOffersFailClosed() {
        var clock = 0L
        val window = TelegramTvPairingWindow { clock }
        window.issue().use { old ->
            val packet = TelegramTvPairingPacket.seal(old, request)!!
            clock = TelegramTvPairingWindow.TTL_MS
            assertNull(window.receive(packet))
            window.issue().use { fresh ->
                assertNull(window.receive(packet))
                val next = TelegramTvPairingPacket.seal(fresh, request)!!
                repeat(TelegramTvPairingWindow.MAX_ATTEMPTS) {
                    assertNull(window.receive(ByteArray(TelegramTvPairingPacket.PACKET_BYTES)))
                }
                assertNull(window.receive(next))
            }
        }
    }

    @Test fun receiverAccountMustRemainCurrentAndPublicationIsSingleUse() = runBlocking {
        val accounts = AccountSessionController()
        var calls = 0
        val receiver = TelegramTvAccountHandoff(accounts) { _, _ -> calls++; 987 }
        assertNull(receiver.prepare(request, true))
        accounts.activate(1L, accounts.generation())
        assertNull(receiver.prepare(request, false))
        assertEquals(0, calls)
        val prepared = receiver.prepare(request, true)!!
        var published = 0
        assertTrue(receiver.publish(prepared) { assertEquals(987, it.localFileId); published++ })
        assertFalse(receiver.publish(prepared) { published++ })
        assertEquals(1, published)
        val stale = receiver.prepare(request, true)!!
        accounts.invalidate()
        accounts.activate(2L, accounts.generation())
        assertFalse(receiver.publish(stale) { published++ })
        val switching = TelegramTvAccountHandoff(accounts) { _, _ ->
            accounts.invalidate()
            accounts.activate(3L, accounts.generation())
            123
        }
        assertNull(switching.prepare(request, true))
    }
}
