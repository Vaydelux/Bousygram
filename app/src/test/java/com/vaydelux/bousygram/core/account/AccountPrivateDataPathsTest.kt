package com.vaydelux.bousygram.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AccountPrivateDataPathsTest {
    @Test fun purgePathsAreLimitedToVerifiedAccount() {
        val root = File("sandbox/files")
        val first = privateDataPaths(root, 111L)
        val second = privateDataPaths(root, 222L)
        assertEquals(File(root, "accounts/111"), first.accountDirectory)
        assertEquals("bousygram_account_111.db", first.roomDatabaseName)
        assertEquals("account_111_enterprise", first.preferencesName)
        assertTrue(first.accountDirectory != second.accountDirectory)
        assertTrue(first.roomDatabaseName != second.roomDatabaseName)
    }

    @Test fun cachedMediaIsScopedToAnAccountAndNotAnotherUsersDirectory() {
        val root = File("sandbox/cache")
        val first = privateAccountCacheDirectory(root, 111L)
        val second = privateAccountCacheDirectory(root, 222L)
        assertEquals(File(root, "accounts/111"), first)
        assertEquals(File(root, "accounts/222"), second)
        assertTrue(first != second)
        assertTrue(first.parentFile == second.parentFile)
    }

    @Test fun accountCachePathRejectsUnverifiedAccountId() {
        assertThrows(IllegalArgumentException::class.java) {
            privateAccountCacheDirectory(File("sandbox/cache"), 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            privateAccountCacheDirectory(File("sandbox/cache"), -1L)
        }
    }

    @Test fun unknownAccountIdsCannotBePurged() {
        assertThrows(IllegalArgumentException::class.java) { privateDataPaths(File("sandbox"), 0L) }
        assertThrows(IllegalArgumentException::class.java) { privateDataPaths(File("sandbox"), -1L) }
    }
}
