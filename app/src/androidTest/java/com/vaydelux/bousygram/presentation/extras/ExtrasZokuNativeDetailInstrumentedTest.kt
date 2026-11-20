package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Offline proof of the native Zoku-inspired detail components.
 * No TMDB key, account, AniList API or remote streaming URLs required.
 */
@RunWith(AndroidJUnit4::class)
class ExtrasZokuNativeDetailInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    private fun fixture(description: String): ExtrasDetail = ExtrasDetail(
        item = ExtrasItem(
            id = 2026, type = ExtrasType.MOVIES,
            title = "Native responsive fixture", year = "2026", rating = "8.8"
        ),
        description = description,
        genres = listOf("Drama", "Adventure"),
        facts = listOf("123 minutes"),
        cast = emptyList(),
        reviews = emptyList(),
        seasons = emptyList()
    )

    @Test fun phoneSynopsisExpandsWithoutDuplicatingThePrimaryHeading() {
        val spec = ExtrasDetailLayoutPolicy.resolve(320f, 568f, false)
        val longDescription = (1..80).joinToString(" ") {
            "A film description that stays readable on a narrow Android screen."
        }
        rule.setContent {
            BousygramTheme {
                Column(Modifier.width(300.dp)) {
                    ExtrasDetailSummary(fixture(longDescription), spec)
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("extras-detail-title").assertIsDisplayed()
        rule.onNodeWithTag("extras-detail-overview").assertIsDisplayed()
        rule.onNodeWithTag("extras-description-toggle").assertExists().performClick()
        rule.onNodeWithText("Show less").assertExists()
        rule.onNodeWithTag("extras-description-toggle").performClick()
        rule.onNodeWithText("Read full description").assertExists()
        assertEquals(1, rule.onAllNodesWithTag("extras-detail-title").fetchSemanticsNodes().size)
    }

    @Test fun episodeCardHasOneWorkingNativePlayAction() {
        val plays = mutableIntStateOf(0)
        rule.setContent {
            BousygramTheme {
                Column(Modifier.width(340.dp).testTag("episode-fixture")) {
                    ExtrasEpisodeTile(
                        episode = ExtrasEpisode(
                            number = 7, title = "Chapter Seven",
                            overview = "A complete, local-only episode description.",
                            still = null
                        ),
                        canPlay = true,
                        onPlay = { plays.intValue += 1 }
                    )
                }
            }
        }
        rule.onNodeWithTag("extras-episode-card-7").assertExists()
        rule.onNodeWithTag("extras-play-episode").performClick()
        rule.runOnIdle { assertEquals(1, plays.intValue) }
    }

    @Test fun unavailableEpisodeDoesNotExposeFakePlayback() {
        rule.setContent {
            BousygramTheme {
                ExtrasEpisodeTile(
                    episode = ExtrasEpisode(0, "Unavailable", "", null),
                    canPlay = false,
                    onPlay = { error("Must not play") },
                    isTv = true
                )
            }
        }
        rule.onNodeWithTag("extras-episode-card-0").assertExists()
        rule.onNodeWithTag("extras-play-episode").assertDoesNotExist()
    }
}
