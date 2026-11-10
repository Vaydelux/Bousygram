package com.vaydelux.bousygram.core.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNextAccountCleanupTest {
    @Test fun revokedAccountMatchingRequiresExactAccountBoundary() {
        assertTrue(isRevokedAccountWatchNextId("bousy_account_101_-200_300_4", 101L))
        assertFalse(isRevokedAccountWatchNextId("bousy_account_101_-200_300_4", 10L))
        assertFalse(isRevokedAccountWatchNextId("bousy_account_1010_-200_300_4", 101L))
        assertFalse(isRevokedAccountWatchNextId("bousy_account_101", 101L))
        assertFalse(isRevokedAccountWatchNextId("unrelated_account_101_-200_300_4", 101L))
        assertFalse(isRevokedAccountWatchNextId("bousy_account_101_-200_300_4", 0L))
    }
}
