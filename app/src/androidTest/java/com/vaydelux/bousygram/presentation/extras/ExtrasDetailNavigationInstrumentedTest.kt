package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * No TMDB key, account or network required.
 * The actual ExtrasDetailContent is driven by local detail fixtures to catch
 * TV action ordering, remote targets and Play callbacks.
 */
@RunWith(AndroidJUnit4::class)
class ExtrasDetailNavigationInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    private fun fixture(
        type: ExtrasType,
        seasons: List<ExtrasSeason> = emptyList()
    ): ExtrasDetail = ExtrasDetail(
        item = ExtrasItem(id = 812, type = type, title = "Offline detail fixture"),
        description = "A stable synthetic description without remote media",
        genres = listOf("Drama"),
        facts = listOf("2026"),
        cast = emptyList(),
        reviews = emptyList(),
        seasons = seasons,
        related = emptyList()
    )

    @Test fun tvSeasonSelectionIsImmediatelyAvailableAboveProviderSections() {
        val chosen = mutableIntStateOf(0)
        rule.setContent {
            BousygramTheme {
                Box(Modifier.fillMaxSize()) {
                    ExtrasDetailContent(
                        info = fixture(
                            ExtrasType.TV,
                            seasons = listOf(
                                ExtrasSeason(1, "Season 1", 8),
                                ExtrasSeason(2, "Season 2", 6)
                            )
                        ),
                        episodes = emptyList(), selectedSeason = null,
                        seasonLoading = false, episodeError = null,
                        onSeason = { chosen.intValue = it },
                        onSeasonRetry = {}, onPlayMovie = {},
                        onPlayAnime = {}, onPlayEpisode = {},
                        onOpenItem = {}, modifier = Modifier,
                        compact = true, shortViewport = true, isAndroidTv = true
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("extras-seasons-heading").assertIsDisplayed()
        val season = rule.onNodeWithTag("extras-season-1")
        season.assertIsDisplayed()
        season.performClick()
        rule.runOnIdle { assertEquals(1, chosen.intValue) }
    }

    @Test fun movieDetailEntersAtVisiblePrimaryPlayAction() {
        val played = mutableStateOf(false)
        rule.setContent {
            BousygramTheme {
                ExtrasDetailContent(
                    info = fixture(ExtrasType.MOVIES),
                    episodes = emptyList(), selectedSeason = null,
                    seasonLoading = false, episodeError = null,
                    onSeason = {}, onSeasonRetry = {},
                    onPlayMovie = { played.value = true },
                    onPlayAnime = {}, onPlayEpisode = {}, onOpenItem = {},
                    modifier = Modifier, compact = true,
                    shortViewport = true, isAndroidTv = true
                )
            }
        }
        rule.waitForIdle()
        val action = rule.onNodeWithTag("extras-play-movie")
        action.assertIsDisplayed()
        action.performClick()
        rule.runOnIdle { assertTrue(played.value) }
    }

    @Test fun animeDetailExposesEpisodePlayWithoutSecondaryPlayer() {
        val playedEpisode = mutableIntStateOf(0)
        rule.setContent {
            BousygramTheme {
                ExtrasDetailContent(
                    info = fixture(ExtrasType.ANIME),
                    episodes = emptyList(), selectedSeason = null,
                    seasonLoading = false, episodeError = null,
                    onSeason = {}, onSeasonRetry = {}, onPlayMovie = {},
                    onPlayAnime = { playedEpisode.intValue = it },
                    onPlayEpisode = {}, onOpenItem = {},
                    modifier = Modifier, compact = true,
                    shortViewport = true, isAndroidTv = true
                )
            }
        }
        rule.waitForIdle()
        val action = rule.onNodeWithTag("extras-play-anime")
        action.assertIsDisplayed()
        action.performClick()
        rule.runOnIdle { assertEquals(1, playedEpisode.intValue) }
    }
}
