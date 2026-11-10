package com.vaydelux.bousygram.core.account

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.core.tdlib.utils.PlaybackProgressTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses synthetic account IDs and never touches an authenticated TDLib identity. */
@RunWith(AndroidJUnit4::class)
class AccountPrivateDataPurgerInstrumentedTest {
    @Test fun firstLoginWithoutProgressDocumentCanBePurged() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val untouched = 99101003L
        val name = privatePlaybackProgressPreferenceName(untouched)
        context.deleteSharedPreferences(name)
        AccountPrivateDataPurger.purge(context, untouched)
        val file = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
        assertFalse(file.exists())
    }

    @Test fun delayedOldPlayerPositionCannotLeakIntoNewAccountAfterLogout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val revoked = 99101021L
        val newAccount = 99101022L
        val newPrefs = privatePlaybackProgressPreferenceName(newAccount)
        val key = "m:-400:500"
        context.deleteSharedPreferences(privatePlaybackProgressPreferenceName(revoked))
        context.deleteSharedPreferences(newPrefs)

        // Synthetic verified identities: no TDLib credentials or real media are used.
        AccountSession.invalidate()
        val oldOwner = checkNotNull(AccountSession.activate(revoked, AccountSession.generation()))
        try {
            PlaybackProgressTracker.saveMessagePosition(
                chatId = -400L, messageId = 500L, positionMs = 31_000,
                durationMs = 120_000L, fileId = 80, title = "Synthetic test",
                expectedOwner = oldOwner
            )
            val oldStore = context.getSharedPreferences(
                privatePlaybackProgressPreferenceName(revoked), Context.MODE_PRIVATE
            )
            assertEquals(31_000, oldStore.getInt(key, -1))

            AccountSession.invalidate()
            val ownerB = checkNotNull(AccountSession.activate(newAccount, AccountSession.generation()))
            // The source has been disposed/revoked; its delayed callback must not
            // borrow the current account, even if the file/chat IDs are identical.
            PlaybackProgressTracker.saveMessagePosition(
                chatId = -400L, messageId = 500L, positionMs = 42_000,
                durationMs = 120_000L, fileId = 80, title = "Old account",
                expectedOwner = oldOwner
            )
            val newStore = context.getSharedPreferences(newPrefs, Context.MODE_PRIVATE)
            assertFalse("Old playback must not recreate new-account progress", newStore.contains(key))
            PlaybackProgressTracker.saveMessagePosition(
                chatId = -400L, messageId = 500L, positionMs = 55_000,
                durationMs = 120_000L, fileId = 80, title = "Current account",
                expectedOwner = ownerB
            )
            assertEquals(55_000, newStore.getInt(key, -1))
            PlaybackProgressTracker.clearMessagePosition(
                chatId = -400L, messageId = 500L, expectedOwner = oldOwner
            )
            assertEquals("Stale EOF cannot mark the new account complete",
                55_000, newStore.getInt(key, -1))
        } finally {
            AccountSession.invalidate()
            AccountPrivateDataPurger.purge(context, revoked)
            AccountPrivateDataPurger.purge(context, newAccount)
        }
    }

    @Test fun logoutRemovesPlaybackPreferencesWithoutDeletingAnotherAccountsProgress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val revoked = 99101001L
        val retained = 99101002L
        val revokedName = privatePlaybackProgressPreferenceName(revoked)
        val retainedName = privatePlaybackProgressPreferenceName(retained)
        val first = context.getSharedPreferences(revokedName, Context.MODE_PRIVATE)
        val second = context.getSharedPreferences(retainedName, Context.MODE_PRIVATE)
        try {
            assertTrue(first.edit().putInt("m:123:456", 33000).commit())
            assertTrue(second.edit().putInt("m:789:101", 44000).commit())
            assertTrue(first.contains("m:123:456"))
            AccountPrivateDataPurger.purge(context, revoked)
            assertFalse(context.getSharedPreferences(revokedName, Context.MODE_PRIVATE).contains("m:123:456"))
            val preferenceFile = File(context.applicationInfo.dataDir, "shared_prefs/$revokedName.xml")
            assertFalse("Logout must erase the account's playback preference file", preferenceFile.exists())
            assertTrue(context.getSharedPreferences(retainedName, Context.MODE_PRIVATE).contains("m:789:101"))
        } finally {
            context.deleteSharedPreferences(revokedName)
            context.deleteSharedPreferences(retainedName)
        }
    }
}
