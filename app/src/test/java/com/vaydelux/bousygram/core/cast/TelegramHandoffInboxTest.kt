package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSessionController
import org.junit.Assert.*
import org.junit.Test

class TelegramHandoffInboxTest {
    @Test fun oneShotExpiryAndAccountRevocation() {
        val accounts = AccountSessionController()
        val owner = checkNotNull(accounts.activate(101, accounts.generation()))
        var now = 100L
        val inbox = TelegramHandoffInbox(accounts) { now }
        val media = ResolvedTelegramTvHandoff(TelegramTvHandoff(-100, 12, 0, false), 44)
        val entry = checkNotNull(inbox.offer(owner, media))
        assertNull(inbox.offer(owner, media))
        assertEquals(media, inbox.take(entry))
        assertNull(inbox.take(entry))
        val expired = checkNotNull(inbox.offer(owner, media))
        now += 30_000
        assertNull(inbox.take(expired))
        val stale = checkNotNull(inbox.offer(owner, media))
        accounts.invalidate()
        accounts.activate(102, accounts.generation())
        assertNull(inbox.take(stale))
        assertNull(inbox.offer(owner, media))
    }
}
