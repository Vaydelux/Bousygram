package com.vaydelux.bousygram.core.account

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses synthetic account IDs and never touches an authenticated TDLib identity. */
@RunWith(AndroidJUnit4::class)
class AccountPrivateDataPurgerInstrumentedTest {
    @Test fun logoutRemovesPlaybackPreferencesWithoutDeletingAnotherAccountsProgress() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
