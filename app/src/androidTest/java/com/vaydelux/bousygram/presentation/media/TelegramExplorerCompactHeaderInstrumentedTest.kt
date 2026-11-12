package com.vaydelux.bousygram.presentation.media

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaydelux.bousygram.presentation.media.vm.ExplorerSection
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Layout contract only; no Telegram data, API keys or network required. */
@RunWith(AndroidJUnit4::class)
class TelegramExplorerCompactHeaderInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun HeaderFixture(
        width: Dp,
        height: Dp,
        television: Boolean,
        forum: Boolean = false,
        hasError: Boolean = false,
        loading: Boolean = false,
        onFavorites: () -> Unit = {}
    ) {
        var section by remember {
            mutableStateOf(if (forum) ExplorerSection.GROUPS else ExplorerSection.SAVED)
        }
        BousygramTheme {
            TelegramExplorerCompactHeader(
                section = section,
                folderTitle = if (forum) "Forum topics" else "Saved Messages",
                collectionStatus = if (forum) "18 topics" else "42 files",
                widthDp = width,
                heightDp = height,
                isTv = television,
                privacyMode = false,
                isLoading = loading,
                canGoUp = forum,
                showSources = false,
                showTopics = forum,
                hasResume = false,
                hasTopicError = hasError,
                channelCount = 12,
                groupCount = 5,
                onSelectTab = { section = it },
                onGoUp = {},
                onBrowseAllMedia = {},
                onRefreshSources = {},
                onRefreshTopics = {},
                onRefreshMedia = {},
                onResume = {},
                onSearch = {},
                onFavorites = onFavorites,
                onSettings = {}
            )
        }
    }

    @Test fun shortTvHeaderStaysUnderOneThirdOf540dpViewport() {
        compose.setContent { HeaderFixture(720.dp, 540.dp, television = true) }
        compose.onNodeWithText("Your library").assertExists()
        compose.onNodeWithTag("telegram-library-collection").assertExists()
        compose.onNodeWithTag("telegram-library-tab-saved").assertIsSelected()
        compose.onNodeWithTag("telegram-library-tab-favorites").assertIsDisplayed()
        compose.onNodeWithTag("telegram-explorer-more").assertExists()

        val heightPx = compose.onNodeWithTag("telegram-explorer-toolbar")
            .fetchSemanticsNode().boundsInRoot.height
        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
        assertTrue("TV toolbar consumed ${heightPx / density}dp, expected at most 160dp",
            heightPx <= 160f * density)
    }

    @Test fun narrowPortraitKeepsFavoritesVisibleAndOpensCollection() {
        var favoritesOpens = 0
        compose.setContent {
            Box(Modifier.width(320.dp)) {
                HeaderFixture(320.dp, 700.dp, television = false,
                    onFavorites = { favoritesOpens++ })
            }
        }
        compose.onNodeWithTag("telegram-library-tab-saved").assertIsSelected()
        compose.onNodeWithTag("telegram-library-tab-favorites")
            .assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, favoritesOpens) }
        // Navigating to Favorites is not switching the Telegram source tab.
        compose.onNodeWithTag("telegram-library-tab-saved").assertIsSelected()
    }

    @Test fun phoneLandscapeKeepsFavoritesPinnedWithScrollableSourceTabs() {
        var favoritesOpens = 0
        compose.setContent {
            Box(Modifier.width(844.dp)) {
                HeaderFixture(844.dp, 390.dp, television = false,
                    onFavorites = { favoritesOpens++ })
            }
        }
        compose.onNodeWithTag("telegram-library-tab-favorites")
            .assertIsDisplayed().performClick()
        compose.onNodeWithTag("telegram-library-source-tabs").assertExists()
        compose.runOnIdle { assertEquals(1, favoritesOpens) }
    }

    @Test fun retryButtonAppearsOnlyOnForumErrors() {
        compose.setContent {
            HeaderFixture(720.dp, 540.dp, television = true, forum = true, hasError = true)
        }
        compose.onNodeWithTag("forum-retry-topics").assertExists()
        compose.onNodeWithText("Retry topics").assertExists()
        compose.onNodeWithTag("telegram-explorer-more").performClick()
        compose.onNodeWithText("Retry forum topics").assertExists()
    }

    @Test fun loadingForumDoesNotShowDuplicateRetryButton() {
        compose.setContent {
            HeaderFixture(720.dp, 540.dp, television = true, forum = true, loading = true)
        }
        compose.onNodeWithTag("forum-retry-topics").assertDoesNotExist()
        compose.onNodeWithTag("forum-browse-all-media").assertExists()
    }

    @Test fun portraitAndForumControlsRemainReachableWithoutStackedPanel() {
        compose.setContent { HeaderFixture(393.dp, 852.dp, television = false, forum = true) }
        compose.onNodeWithTag("forum-browse-all-media").assertExists()
        compose.onNodeWithTag("telegram-explorer-go-up").assertExists()
        compose.onNodeWithTag("telegram-library-tab-groups").assertIsSelected()
        compose.onNodeWithTag("telegram-explorer-more").performClick()
        compose.onNodeWithText("Refresh forum topics").assertExists()
    }
}
