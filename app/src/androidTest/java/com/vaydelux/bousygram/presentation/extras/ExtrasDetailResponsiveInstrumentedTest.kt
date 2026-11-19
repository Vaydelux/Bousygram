package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Offline QA for short landscape details and review remote navigation. */
@RunWith(AndroidJUnit4::class)
class ExtrasDetailResponsiveInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun shortLandscapeOmitsGiantHeroAndPlayWorksWithDpad() {
        var launches = 0
        rule.setContent {
            BousygramTheme {
                val focus = remember { FocusRequester() }
                Box(Modifier.fillMaxSize()) {
                    ExtrasDetailContent(
                        info = ExtrasDetail(
                            item = ExtrasItem(42, ExtrasType.MOVIES, "Short viewport fixture",
                                year = "2026"),
                            description = "A compact detail page with an immediately reachable action.",
                            genres = listOf("Action"),
                            facts = listOf("2026"),
                            cast = emptyList(), reviews = emptyList(),
                            seasons = emptyList(),
                            backdrop = "https://example.com/unused-backdrop.jpg"
                        ),
                        episodes = emptyList(),
                        selectedSeason = null,
                        seasonLoading = false,
                        episodeError = null,
                        onSeason = {},
                        onSeasonRetry = {},
                        onPlayMovie = { launches++ },
                        onPlayAnime = {},
                        onPlayEpisode = {},
                        onOpenItem = {},
                        modifier = Modifier,
                        compact = true,
                        isAndroidTv = false,
                        layout = resolveExtrasDetailLayout(720.dp, 400.dp, false),
                        primaryActionFocus = focus
                    )
                }
            }
        }
        rule.onNodeWithTag("extras-detail-backdrop").assertDoesNotExist()
        val play = rule.onNodeWithTag("extras-play-movie")
        play.assertExists()
        play.performSemanticsAction(SemanticsActions.RequestFocus)
        play.performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(1, launches) }
    }

    @Test fun reviewStartsCollapsedAndExpandsOnlyAfterUserAction() {
        val review = ExtrasReview(
            author = "Film club",
            score = "8/10",
            content = ("The direction has impressive lighting but the pacing is uneven. ").repeat(20)
        )
        rule.setContent {
            BousygramTheme {
                ExtrasReviewCard(review, collapsedLines = 3)
            }
        }
        val before = rule.onNodeWithTag("extras-review-content")
            .fetchSemanticsNode().boundsInRoot.height
        rule.onNodeWithTag("extras-review-expand").performClick()
        rule.onNodeWithText("Show less").assertExists()
        val after = rule.onNodeWithTag("extras-review-content")
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue("Expanding a review must reveal more text", after > before)
        rule.onNodeWithTag("extras-review-expand").performClick()
        rule.onNodeWithText("Read full review").assertExists()
    }

    @Test fun synopsisNeverRequiresUnboundedScrollingToFindMoreAction() {
        rule.setContent {
            BousygramTheme {
                ExtrasSynopsis(
                    description = ("A long provider overview with many details. ").repeat(25),
                    compact = true
                )
            }
        }
        rule.onNodeWithTag("extras-synopsis-toggle").assertExists()
        rule.onNodeWithTag("extras-synopsis-toggle").performClick()
        rule.onNodeWithText("Show less").assertExists()
    }
}
