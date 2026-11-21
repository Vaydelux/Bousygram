package com.vaydelux.bousygram.presentation.extras

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** No network, TMDB key, Telegram account or provider WebView needed. */
@RunWith(AndroidJUnit4::class)
class ExtrasEpisodeNavigationInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun playerPreviousNextAndSeasonPickerKeepTmdbIdentity() {
        val guide = ExtrasEpisodeGuide(
            catalogType = ExtrasType.TV, titleId = 9999,
            seasons = listOf(
                ExtrasSeason(1, "Season One", 2),
                ExtrasSeason(2, "Season Two", 3)
            )
        )
        rule.setContent {
            BousygramTheme {
                var request by remember {
                    mutableStateOf(ExtrasEmbedRequest.episode(9999, 1, 2, "Example"))
                }
                ExtrasPlayerEpisodeControls(request, guide) { request = it }
            }
        }
        rule.onNodeWithTag("extras-player-next-episode").performClick()
        rule.onNodeWithText("S2 · E1").assertExists()
        rule.onNodeWithTag("extras-player-previous-episode").performClick()
        rule.onNodeWithText("S1 · E2").assertExists()
        rule.onNodeWithTag("extras-player-episode-picker").performClick()
        rule.onNodeWithTag("extras-player-episode-dialog").assertExists()
        rule.onNodeWithTag("extras-player-season-2").performClick()
        rule.onNodeWithTag("extras-player-episode-2-3").performClick()
        rule.onNodeWithText("S2 · E3").assertExists()
    }

    @Test fun localAnimeMatchPersistsPublicIdsAndCanBeRemoved() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ExtrasAnimeTmdbMatchStore(ctx)
        val animeId = 1_984_270
        store.remove(animeId)
        val match = ExtrasAnimeTmdbMatch(animeId, 882, 4, 13, "A mapped TV series")
        try {
            assertNull(store.get(animeId))
            assertEquals(true, store.save(match))
            assertEquals(match, ExtrasAnimeTmdbMatchStore(ctx).get(animeId))
        } finally {
            store.remove(animeId)
        }
        assertNull(store.get(animeId))
    }
}
