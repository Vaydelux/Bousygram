package com.vaydelux.bousygram.presentation.preview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.fillMaxSize
import com.vaydelux.bousygram.presentation.media.TelegramExplorerDashboard
import com.vaydelux.bousygram.presentation.media.vm.BrowseRestoration
import com.vaydelux.bousygram.presentation.media.vm.ExplorerPosition

/**
 * Debug-only preview of the actual v2 explorer, without authentication/network
 * or fabricated chat IDs. It is intentionally empty and cannot start playback.
 */
@Composable
fun TvHybridPreviewHost() {
    // Debug-only preview stays available for repeated D-pad/back QA. It has no
    // authenticated parent route to return to and must not finish the Activity.
    BackHandler(enabled = true) { }
    TelegramExplorerDashboard(
        position = ExplorerPosition(),
        sources = emptyList(),
        topics = emptyList(),
        selectedChatTitle = "Saved Messages",
        selectedChatId = null,
        selectedTopicId = null,
        videos = emptyList(),
        isLoading = false,
        isLoadingSources = false,
        isLoadingTopics = false,
        errorMessage = null,
        sourceErrorMessage = null,
        isLoadingMore = false,
        hasMore = false,
        privacyMode = false,
        gridState = rememberLazyGridState(),
        sourceFolderRestoration = BrowseRestoration(),
        onSourceFocused = {},
        onSourceViewport = { _, _ -> },
        onSelectTab = {},
        onSelectSource = {},
        onSelectTopic = {},
        focusedTopicId = null,
        onTopicFocused = {},
        onGoUp = {},
        onPlay = {},
        onFocusMedia = {},
        restoreFocusedMediaKey = null,
        restoreFocusRequester = remember { FocusRequester() },
        onLoadMore = {},
        onRefreshSources = {},
        onRefreshTopics = {},
        onRefreshMedia = {},
        onSearch = {},
        onFavorites = {},
        onSettings = {},
        hasResume = false,
        onResume = {},
        modifier = Modifier.fillMaxSize()
    )
}
