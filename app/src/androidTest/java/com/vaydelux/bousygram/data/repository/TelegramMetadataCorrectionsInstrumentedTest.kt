package com.vaydelux.bousygram.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelegramMetadataCorrectionsInstrumentedTest {
    private val testAccount = 98822005121L
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(
        "telegram_metadata_corrections_" + testAccount, Context.MODE_PRIVATE
    )
    private fun item(chatId: Long = -100L, messageId: Long = 9L) = MediaItem(
        chatId = chatId, messageId = messageId, date = 1,
        type = MediaType.Video, title = "Original.Private.Video.2021.mkv",
        fileId = 42, fileName = "Original.Private.Video.2021.mkv"
    )
    private val candidate = TmdbCorrectionCandidate(
        1001, false, "Chosen Movie", 2021, "A public synopsis.",
        "/abcdefgh1234.jpg", 8.0f
    )

    @Before fun prepare() {
        AccountSession.invalidate()
        checkNotNull(AccountSession.activate(testAccount, AccountSession.generation()))
        prefs.edit().clear().commit()
    }

    @After fun cleanup() {
        prefs.edit().clear().commit()
        AccountSession.invalidate()
    }

    @Test fun chosenMatchSurvivesReloadAndNeverChangesTelegramIdentity() {
        val original = item()
        assertTrue(TelegramMetadataCorrections.choose(context, original, candidate))
        val record = TelegramMetadataCorrections.read(context, original)
        assertEquals("Chosen Movie", record?.title)
        val revised = TelegramMetadataCorrections.apply(context, original)
        assertEquals(original.chatId, revised.chatId)
        assertEquals(original.messageId, revised.messageId)
        assertEquals(original.fileId, revised.fileId)
        assertEquals(original.fileName, revised.fileName)
        assertEquals("Chosen Movie", revised.tmdbTitle)
        assertEquals(2021, revised.releaseYear)
    }

    @Test fun correctionIsScopedToTheExactMessage() {
        assertTrue(TelegramMetadataCorrections.choose(context, item(), candidate))
        assertNull(TelegramMetadataCorrections.read(context, item(-200L, 9L)))
        assertNull(TelegramMetadataCorrections.read(context, item(-100L, 10L)))
    }

    @Test fun skipSuppressesMatchAndClearRestoresUnmodifiedSource() {
        val original = item().copy(tmdbTitle = "Wrong cached title")
        assertTrue(TelegramMetadataCorrections.skip(context, original))
        assertNull(TelegramMetadataCorrections.apply(context, original).tmdbTitle)
        assertTrue(TelegramMetadataCorrections.clear(context, original))
        assertNull(TelegramMetadataCorrections.read(context, original))
    }

    @Test fun corruptedRecordCannotBeSilentlyOverwritten() {
        val original = item()
        val invalid = "{bad-json"
        prefs.edit().putString("m_-100_9", invalid).commit()
        assertTrue(runCatching {
            TelegramMetadataCorrections.choose(context, original, candidate)
        }.exceptionOrNull() is IOException)
        assertEquals(invalid, prefs.getString("m_-100_9", null))
    }

    @Test fun logoutAndAccountSwitchCannotReadPreviousCorrections() {
        val original = item()
        assertTrue(TelegramMetadataCorrections.choose(context, original, candidate))
        AccountSession.invalidate()
        checkNotNull(AccountSession.activate(testAccount + 1, AccountSession.generation()))
        assertNull(TelegramMetadataCorrections.read(context, original))
        assertFalse(TelegramMetadataCorrections.clear(context, item(0, 0)))
    }
}
