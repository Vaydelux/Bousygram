package com.vaydelux.bousygram.core.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNextAccountCleanupTest {
    @Test fun naturalEofAndProgressWritesUseTheSameAccountScopedIdentity() {
        val progressId = watchNextProviderId(101L, -200L, 300L, 4)
        val completedId = watchNextProviderId(101L, -200L, 300L, 4)
        assertTrue(isRevokedAccountWatchNextId(progressId, 101L))
        assertEquals(progressId, completedId)
        assertEquals("bousy_account_101_-200_300_4", progressId)
        assertFalse(isRevokedAccountWatchNextId(progressId, 10L))
        assertFalse(progressId == watchNextProviderId(102L, -200L, 300L, 4))
        assertFalse(progressId == watchNextProviderId(101L, -200L, 301L, 4))
        assertFalse(progressId == watchNextProviderId(101L, -200L, 300L, 5))
        assertEquals("bousy_account_101_-200_300_null", watchNextProviderId(101L, -200L, 300L, null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownAccountCannotCreateSystemLauncherHistoryId() {
        watchNextProviderId(0L, -200L, 300L, 4)
    }

    @Test fun revokedAccountMatchingRequiresExactAccountBoundary() {
        assertTrue(isRevokedAccountWatchNextId("bousy_account_101_-200_300_4", 101L))
        assertFalse(isRevokedAccountWatchNextId("bousy_account_101_-200_300_4", 10L))
        assertFalse(isRevokedAccountWatchNextId("bousy_account_1010_-200_300_4", 101L))
        assertFalse(isRevokedAccountWatchNextId("bousy_account_101", 101L))
        assertFalse(isRevokedAccountWatchNextId("unrelated_account_101_-200_300_4", 101L))
        assertFalse(isRevokedAccountWatchNextId("bousy_account_101_-200_300_4", 0L))
    }
}
