package com.vaydelux.bousygram.presentation.extras

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** No TMDB credentials or AniList network needed for autocomplete regression tests. */
@RunWith(AndroidJUnit4::class)
class ExtrasSearchUiInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun onlyTheCurrentProviderCanAppearAndDpadCenterOpensItsResult() {
        val anime = ExtrasItem(701, ExtrasType.ANIME, "Anime fixture")
        val movie = ExtrasItem(702, ExtrasType.MOVIES, "Movie fixture")
        rule.setContent {
            BousygramTheme {
                var selected by remember { mutableStateOf<ExtrasItem?>(null) }
                ExtrasSearchSuggestions(
                    type = ExtrasType.ANIME,
                    suggestions = listOf(movie, anime),
                    onOpen = { selected = it },
                    onDismiss = {}
                )
                if (selected != null) {
                    androidx.compose.material3.Text("Opened: ${selected?.title}")
                }
            }
        }
        rule.onNodeWithTag("extras-suggestion-movies-702").assertDoesNotExist()
        val result = rule.onNodeWithTag("extras-suggestion-anime-701")
        result.assertExists()
        result.performSemanticsAction(SemanticsActions.RequestFocus)
        result.performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onNodeWithText("Opened: Anime fixture").assertExists()
    }

    @Test fun dismissRemovesAutocompleteWithoutChangingProvider() {
        val series = ExtrasItem(123, ExtrasType.TV, "TV fixture")
        rule.setContent {
            BousygramTheme {
                var expanded by remember { mutableStateOf(true) }
                if (expanded) ExtrasSearchSuggestions(
                    type = ExtrasType.TV,
                    suggestions = listOf(series),
                    onOpen = {},
                    onDismiss = { expanded = false }
                )
            }
        }
        rule.onNodeWithTag("extras-search-suggestions").assertExists()
        rule.onNodeWithTag("extras-suggestions-dismiss").performClick()
        rule.onNodeWithTag("extras-search-suggestions").assertDoesNotExist()
    }

    @Test fun pendingTmdbSetupRoutesToIndependentAnimeWithoutNetwork() {
        rule.setContent {
            BousygramTheme {
                var animeChosen by remember { mutableStateOf(false) }
                ExtrasTmdbPendingSetup(onBrowseAnime = { animeChosen = true })
                if (animeChosen) androidx.compose.material3.Text("Anime selected")
            }
        }
        rule.onNodeWithTag("extras-tmdb-pending").assertExists()
        val button = rule.onNodeWithTag("extras-go-anime")
        button.assertExists()
        button.performSemanticsAction(SemanticsActions.RequestFocus)
        button.performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onNodeWithText("Anime selected").assertExists()
    }

    @Test fun previousCatalogPageCanBeRequestedWithDpadCenter() {
        rule.setContent {
            BousygramTheme {
                var previousRequested by remember { mutableStateOf(false) }
                ExtrasPreviousPageAction { previousRequested = true }
                if (previousRequested) androidx.compose.material3.Text("Previous page requested")
            }
        }
        val previous = rule.onNodeWithTag("extras-previous-page")
        previous.assertExists()
        previous.performSemanticsAction(SemanticsActions.RequestFocus)
        previous.performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onNodeWithText("Previous page requested").assertExists()
    }

    @Test fun boundedPagingNoticeHasDpadAccessibleRewindAction() {
        rule.setContent {
            BousygramTheme {
                var rewound by remember { mutableStateOf(false) }
                ExtrasPagingWindowNotice(retainedCount = 120) { rewound = true }
                if (rewound) androidx.compose.material3.Text("First page requested")
            }
        }
        rule.onNodeWithTag("extras-paging-window-notice").assertExists()
        val action = rule.onNodeWithTag("extras-start-over")
        action.assertExists()
        action.performSemanticsAction(SemanticsActions.RequestFocus)
        action.performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onNodeWithText("First page requested").assertExists()
    }

    @Test fun catalogSkeletonRendersWithoutLoadingProviderData() {
        rule.setContent {
            BousygramTheme { ExtrasCatalogLoadingSkeleton(isTv = true, compact = false) }
        }
        rule.onNodeWithTag("extras-catalog-loading").assertExists()
        rule.onNodeWithTag("extras-loading-poster-0").assertExists()
    }

    @Test fun optionalFiltersPreserveGridSpaceUntilExplicitlyExpanded() {
        rule.setContent {
            BousygramTheme {
                var expanded by remember { mutableStateOf(false) }
                androidx.compose.foundation.layout.Column {
                    ExtrasFilterDisclosure(expanded, active = false, onToggle = {
                        expanded = !expanded
                    })
                    if (expanded) {
                        androidx.compose.material3.Text(
                            "Advanced filters visible",
                            modifier = androidx.compose.ui.Modifier.testTag("extras-filter-panel-fixture")
                        )
                    }
                }
            }
        }
        rule.onNodeWithTag("extras-filter-panel-fixture").assertDoesNotExist()
        rule.onNodeWithTag("extras-filter-toggle").performClick()
        rule.onNodeWithTag("extras-filter-panel-fixture").assertExists()
        rule.onNodeWithTag("extras-filter-toggle").performClick()
        rule.onNodeWithTag("extras-filter-panel-fixture").assertDoesNotExist()
    }

    @Test fun compactDetailSkeletonHasAStablePlaceholderSurface() {
        rule.setContent {
            BousygramTheme { ExtrasDetailLoadingSkeleton(compact = true) }
        }
        rule.onNodeWithTag("extras-detail-loading").assertExists()
        rule.onNodeWithText("Loading details…").assertExists()
    }
}
