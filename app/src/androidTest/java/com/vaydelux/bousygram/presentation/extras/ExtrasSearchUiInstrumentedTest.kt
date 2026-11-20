package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
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

    @Test fun shortViewportLoadingSkeletonMatchesCompactPosterHeight() {
        rule.setContent {
            BousygramTheme {
                androidx.compose.foundation.layout.Box(Modifier.width(340.dp).height(540.dp)) {
                    ExtrasCatalogLoadingSkeleton(
                        isTv = true, compact = false, shortViewport = true
                    )
                }
            }
        }
        val poster = rule.onNodeWithTag("extras-loading-poster-0")
            .fetchSemanticsNode().boundsInRoot.height
        val density = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue("Compact loading poster too tall: ${poster / density}dp",
            poster > 0f && poster / density < 210f)
    }

    @Test fun filterToolbarUsesMeasuredDpSpacingAndAlignedControls() {
        val layout = ExtrasLayoutPolicy.resolve(320f, 720f, isTv = false)
        rule.setContent {
            BousygramTheme {
                Column(Modifier.width(320.dp)) {
                    ExtrasSearchInput(
                        type = ExtrasType.MOVIES,
                        query = "",
                        onQueryChanged = {},
                        onSearch = {},
                        onClear = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(layout.searchFilterGapDp.dp))
                    ExtrasFilterDisclosure(
                        expanded = false, active = false, onToggle = {},
                        minHeightDp = layout.minControlHeightDp
                    )
                    Spacer(Modifier.height(layout.filterResultsGapDp.dp))
                    Box(Modifier.fillMaxWidth().height(64.dp).testTag("qa-results"))
                }
            }
        }
        val search = rule.onNodeWithTag("extras-search-field").fetchSemanticsNode().boundsInRoot
        val filter = rule.onNodeWithTag("extras-filter-disclosure")
            .fetchSemanticsNode().boundsInRoot
        val results = rule.onNodeWithTag("qa-results").fetchSemanticsNode().boundsInRoot
        val density = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue("Filters overlap search", filter.top - search.bottom >= 11f * density)
        assertTrue("Results overlap filters", results.top - filter.bottom >= 11f * density)
        assertTrue("Filter control must be remote/touch accessible",
            filter.height / density >= 48f)
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

    @Test fun shortScreenCatalogViewportGivesResultsMostOfTheHeight() {
        rule.setContent {
            BousygramTheme {
                Column(Modifier.width(340.dp).height(540.dp).testTag("extras-viewport-fixture")) {
                    androidx.compose.material3.Text("Search")
                    ExtrasFilterDisclosure(expanded = false, active = false, onToggle = {})
                    ExtrasCatalogViewport {
                        androidx.compose.material3.Text(
                            "Visible media results",
                            modifier = Modifier.testTag("extras-viewport-content")
                        )
                    }
                }
            }
        }
        rule.onNodeWithTag("extras-viewport-content").assertExists()
        val overall = rule.onNodeWithTag("extras-viewport-fixture")
            .fetchSemanticsNode().boundsInRoot.height
        val results = rule.onNodeWithTag("extras-results-viewport")
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue("Extras results lost viewport height ($results / $overall px)",
            overall > 0f && results / overall >= 0.65f)
    }

    @Test fun compactDetailSkeletonHasAStablePlaceholderSurface() {
        rule.setContent {
            BousygramTheme { ExtrasDetailLoadingSkeleton(compact = true) }
        }
        rule.onNodeWithTag("extras-detail-loading").assertExists()
        rule.onNodeWithText("Loading details…").assertExists()
    }

    @Test fun loadMoreFooterActivatesOnceByRemoteAndDisablesDuringRequest() {
        val loading = mutableStateOf(false)
        val calls = mutableIntStateOf(0)
        rule.setContent {
            BousygramTheme {
                ExtrasMoreTitlesAction(loading = loading.value) {
                    calls.intValue += 1
                }
            }
        }
        val action = rule.onNodeWithTag("extras-more-titles")
        action.performSemanticsAction(SemanticsActions.RequestFocus)
        action.performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle {
            assertEquals(1, calls.intValue)
            loading.value = true
        }
        action.assertIsNotEnabled()
    }
    @Test fun actualLandscapeSearchAndActionsDoNotOverlapAndReservePosterHeight() {
        rule.setContent {
            BousygramTheme {
                Column(
                    Modifier.requiredWidth(744.dp).height(360.dp)
                        .testTag("qa-landscape-frame")
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth()
                            .testTag("qa-landscape-toolbar"),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        ExtrasSearchInput(
                            type = ExtrasType.MOVIES,
                            query = "",
                            onQueryChanged = {},
                            onSearch = {},
                            onClear = {},
                            modifier = Modifier.weight(1f)
                        )
                        ExtrasFilterDisclosure(
                            expanded = false,
                            active = false,
                            onToggle = {},
                            onLibrary = {},
                            minHeightDp = 48,
                            compactToolbar = true
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    ExtrasBrowseActivityRow(
                        resultCount = 20,
                        currentPage = 1,
                        updating = false,
                        onRefresh = {},
                        minimumHeightDp = 48,
                        compact = true
                    )
                    ExtrasCatalogViewport {
                        Box(Modifier.fillMaxSize().testTag("qa-landscape-posters"))
                    }
                }
            }
        }
        rule.onNodeWithTag("extras-filter-toggle").assertExists()
        rule.onNodeWithTag("extras-open-my-library").assertExists()
        val filter = rule.onNodeWithTag("extras-filter-disclosure")
            .fetchSemanticsNode().boundsInRoot
        val search = rule.onNodeWithTag("extras-search-field")
            .fetchSemanticsNode().boundsInRoot
        val viewport = rule.onNodeWithTag("qa-landscape-frame")
            .fetchSemanticsNode().boundsInRoot
        val posters = rule.onNodeWithTag("extras-results-viewport")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Search and filters overlap", search.right <= filter.left + 3f)
        assertTrue("Filters overflow the landscape dp width", filter.right <= viewport.right + 3f)
        assertTrue("Search is clipped on left", search.left >= viewport.left - 3f)
        assertTrue(
            "Posters lose more than 38% of short landscape height",
            viewport.height > 0f && posters.height / viewport.height >= 0.62f
        )
    }


    @Test fun actualSearchHintStaysInsideFieldWithoutFloatingLabel() {
        rule.setContent {
            BousygramTheme {
                Column(Modifier.width(320.dp)) {
                    ExtrasSearchInput(
                        type = ExtrasType.MOVIES,
                        query = "",
                        onQueryChanged = {},
                        onSearch = {},
                        onClear = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        val field = rule.onNodeWithTag("extras-search-field")
            .fetchSemanticsNode().boundsInRoot
        val hint = rule.onNodeWithText("Search Movies · TMDB", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Search hint floats above outlined field", hint.top >= field.top - 1f)
        assertTrue("Search hint clips below outlined field", hint.bottom <= field.bottom + 1f)
        rule.onNodeWithText("Title or keyword", useUnmergedTree = true).assertDoesNotExist()
    }


}
