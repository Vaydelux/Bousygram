package com.vaydelux.bousygram.core.tdlib.utils

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressTrackerTest {
    @Test
    fun accountScopedProgressStorageCannotAliasAnotherTelegramAccount() {
        val accountA = PlaybackProgressTracker.accountPreferenceName(101L)
        val accountB = PlaybackProgressTracker.accountPreferenceName(202L)
        assertNotEquals(accountA, accountB)
        assertNotEquals("playback_resume_positions", accountA)
        assertTrue(accountA.contains("101"))
        assertTrue(accountB.contains("202"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidAccountIdentityCannotSelectPlaybackStorage() {
        PlaybackProgressTracker.accountPreferenceName(0L)
    }
}
