package com.vaydelux.bousygram.core.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramCastSelectionTest {
    private val source = TelegramCastTarget(10L, 20L, 30)

    @Test fun firstSelectionStartsPreparation() {
        assertEquals(TelegramCastSelection.Decision.PREPARE, TelegramCastSelection.decide(null, source))
    }

    @Test fun repeatedSelectionDoesNotRestartPreparationOrLoad() {
        assertEquals(TelegramCastSelection.Decision.KEEP, TelegramCastSelection.decide(source, source.copy()))
    }

    @Test fun queueChangeDisconnectsEvenWhenFileIdIsShared() {
        for (next in listOf(source.copy(chatId = 11L), source.copy(messageId = 21L), source.copy(fileId = 31))) {
            assertEquals(TelegramCastSelection.Decision.DISCONNECT, TelegramCastSelection.decide(source, next))
        }
    }

    @Test fun disappearingSelectionDisconnects() {
        assertEquals(TelegramCastSelection.Decision.DISCONNECT, TelegramCastSelection.decide(source, null))
        assertEquals(TelegramCastSelection.Decision.DISCONNECT, TelegramCastSelection.decide(null, null))
    }
}
