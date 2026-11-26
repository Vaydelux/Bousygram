package com.vaydelux.bousygram.core.cast

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlinx.coroutines.runBlocking
import com.vaydelux.bousygram.core.account.AccountSessionController
import org.junit.Assert.*
import org.junit.Test

class TelegramHandoffWireTest {
    private val request = TelegramTvHandoff(-100123, 44, 5000, false)
    private fun bytes(): ByteArray = ByteArrayOutputStream().also {
        TelegramHandoffWire.writeRequest(DataOutputStream(it), request)
    }.toByteArray()
    private fun input(bytes: ByteArray) = DataInputStream(ByteArrayInputStream(bytes))

    @Test fun fixedWidthRequestAndStrictFlags() {
        val bytes = bytes()
        assertEquals(29, bytes.size)
        assertEquals(request, TelegramHandoffWire.readRequest(input(bytes)))
        for (length in 0 until bytes.size) {
            assertThrows(IOException::class.java) { TelegramHandoffWire.readRequest(input(bytes.copyOf(length))) }
        }
        assertThrows(IOException::class.java) {
            TelegramHandoffWire.readRequest(input(bytes.copyOf().apply { this[28] = 2 }))
        }
        assertThrows(IOException::class.java) {
            TelegramHandoffWire.readRequest(input(bytes.copyOf().apply { this[0] = 0 }))
        }
    }

    @Test fun repliesAndInvalidRequest() {
        for (accepted in listOf(false, true)) {
            val out = ByteArrayOutputStream()
            TelegramHandoffWire.writeReply(DataOutputStream(out), accepted)
            assertEquals(5, out.size())
            assertEquals(accepted, TelegramHandoffWire.readReply(input(out.toByteArray())))
            assertThrows(IOException::class.java) {
                TelegramHandoffWire.readReply(input(out.toByteArray().apply { this[4] = 2 }))
            }
        }
        assertThrows(IOException::class.java) {
            TelegramHandoffWire.writeRequest(DataOutputStream(ByteArrayOutputStream()), request.copy(chatId = 0))
        }
    }

    @Test fun deniesProtectedSecretSelfDestructAndMismatchedMedia() {
        fun allowed(chat: Long = request.chatId, message: Long = request.messageId,
                    resolvedChat: Long = request.chatId, saved: Boolean = true, protected: Boolean = false,
                    secret: Boolean = false, selfDestruct: Boolean = false, file: Int? = 12) =
            TelegramHandoffAuthorization.permits(request, chat, message, resolvedChat, saved, protected, secret, selfDestruct, file)
        assertTrue(allowed())
        assertFalse(allowed(saved = false))
        assertFalse(allowed(protected = true))
        assertFalse(allowed(secret = true))
        assertFalse(allowed(selfDestruct = true))
        assertFalse(allowed(chat = 99))
        assertFalse(allowed(message = 99))
        assertFalse(allowed(resolvedChat = 99))
        assertFalse(allowed(file = null))
        assertFalse(allowed(file = 0))
    }

    @Test fun receiverAccountOwnershipAndOneShotPublication() = runBlocking {
        val accounts = AccountSessionController()
        val owner = checkNotNull(accounts.activate(101, accounts.generation()))
        var resolved = 0
        val handoff = TelegramTvAccountHandoff(accounts) { snapshot, reference ->
            assertEquals(owner, snapshot)
            assertEquals(request, reference)
            resolved++
            987
        }
        assertNull(handoff.prepare(request, false))
        assertEquals(0, resolved)
        val ready = checkNotNull(handoff.prepare(request, true))
        assertTrue(handoff.publish(ready) { assertEquals(987, it.localFileId) })
        assertFalse(handoff.publish(ready) { error("Replay") })
        val stale = checkNotNull(handoff.prepare(request, true))
        accounts.invalidate()
        accounts.activate(102, accounts.generation())
        assertFalse(handoff.publish(stale) { error("Cross-account publication") })
    }
}
