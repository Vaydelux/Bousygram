package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.data.repository.FavoritesRepository
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.*
import org.junit.Assert.assertEquals

class MediaActionsDropdownInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val account = 8_800_000_003L

    @Before fun activateTestAccount() {
        AccountSession.invalidate()
        checkNotNull(AccountSession.activate(account, AccountSession.generation()))
    }

    @After fun eraseTestAccount() {
        AccountSession.invalidate()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("bousygram_account_$account.db")
        context.getSharedPreferences("account_${account}_enterprise", 0).edit().clear().commit()
    }

    @Test fun dropdownFavoritesDoNotActivatePlayback() {
        var plays = 0
        val playbackFocus = FocusRequester()
        val media = MediaItem(-100, 25, 0, MediaType.Video, "Saved episode", null)
        compose.setContent {
            BousygramTheme {
                Box(Modifier.width(320.dp)) {
                    MediaPosterCard(media, "Telegram", onClick = { plays++ }, cardWidth = null,
                        playbackFocusRequester = playbackFocus)
                }
            }
        }
        compose.waitUntil(10_000) { FavoritesRepository.ready.value }
        compose.runOnIdle { check(playbackFocus.requestFocus()) { "Poster did not accept focus" } }
        compose.onNodeWithTag("media-play--100:25").assertIsFocused()
        compose.onNodeWithTag("media-play--100:25").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithContentDescription("Actions for Saved episode").assertIsFocused()
        compose.onNodeWithContentDescription("Actions for Saved episode").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("media-play--100:25").assertIsFocused()
        compose.onNodeWithContentDescription("Actions for Saved episode").assertIsDisplayed().performClick()
        compose.onNodeWithText("Add to favorites").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { "-100:25" in FavoritesRepository.keys.value }
        writeUiEvidence(compose, "media-favorites")
        assertEquals(0, plays)
        compose.onNodeWithContentDescription("Actions for Saved episode").performClick()
        compose.onNodeWithText("Remove from favorites").performClick()
        compose.waitUntil(10_000) { "-100:25" !in FavoritesRepository.keys.value }
        assertEquals(0, plays)
    }
}
