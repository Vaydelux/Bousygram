package com.vaydelux.bousygram.presentation.media

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vaydelux.bousygram.core.media.ExternalPlayerLauncher
import com.vaydelux.bousygram.core.tdlib.cache.OfflineCacheManager
import com.vaydelux.bousygram.core.tdlib.utils.PlaybackProgressTracker
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.SortOrder
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.EmptyStateView
import com.vaydelux.bousygram.presentation.components.LoadingOverlay
import com.vaydelux.bousygram.presentation.components.ChatSourceTabs
import com.vaydelux.bousygram.presentation.components.AllChatsPickerDialog
import com.vaydelux.bousygram.presentation.components.BrowseGridShimmerSkeleton
import com.vaydelux.bousygram.presentation.components.MediaPosterCard
import com.vaydelux.bousygram.presentation.components.MediaPosterSkeletonCard
import com.vaydelux.bousygram.presentation.components.NoSearchResultsEmptyState
import com.vaydelux.bousygram.presentation.components.RecentSearchesSection
import com.vaydelux.bousygram.presentation.components.SidebarListShimmerSkeleton
import com.vaydelux.bousygram.presentation.layout.TvHybridHomeDashboard
import com.vaydelux.bousygram.presentation.media.vm.MediaViewModel
import com.vaydelux.bousygram.data.database.RecentSearchQuery

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.vaydelux.bousygram.presentation.components.BousygramTextField
import com.vaydelux.bousygram.presentation.components.TvFocusSurface
import com.vaydelux.bousygram.presentation.media.launch.LaunchOpeningIndicator
import com.vaydelux.bousygram.presentation.media.launch.rememberMediaLauncher
import com.vaydelux.bousygram.presentation.search.SearchBarVoiceButton
import com.vaydelux.bousygram.presentation.search.SearchMode
import com.vaydelux.bousygram.presentation.search.buildVoiceSearchPrompt
import com.vaydelux.bousygram.presentation.search.rememberVoiceSearchController

@Composable
fun BrowseScreen(
    onPlay: (PlaybackRequest) -> Unit,
    onOpenSearch: (chatId: Long?, chatTitle: String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSearchWithQuery: (chatId: Long?, chatTitle: String, query: String) -> Unit = { chatId, chatTitle, _ ->
        onOpenSearch(chatId, chatTitle)
    },
    onStreamError: (String) -> Unit = {},
    libraryMode: Boolean = false,
    resumeSignal: Int = 0,
    onResumeConsumed: () -> Unit = {},
    onBackToNavBar: () -> Unit = {},
    viewModel: MediaViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val explorerPosition by viewModel.explorerPosition.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    // Saved Messages has its own permanent entry. Never list the same chat again.
    val libraryChatSources = remember(state.videoChats) {
        libraryOtherChatSources(state.videoChats)
    }
    val adaptive = rememberAdaptiveMetrics()
    val context = LocalContext.current
    var favoriteKeys by remember { mutableStateOf(EnterprisePreferences.favoriteKeys()) }
    var safeMode by remember { mutableStateOf(EnterprisePreferences.safeMode()) }
    var hideWatched by remember { mutableStateOf(EnterprisePreferences.hideWatched()) }
    var pinnedChats by remember { mutableStateOf(EnterprisePreferences.pinnedChatIds()) }
    var showAllChatsPicker by remember { mutableStateOf(false) }
    // Keep the sidebar viewport across incremental Telegram chat catalog updates.
    val sourceSidebarListState = rememberLazyListState()
    val lastPlayed = EnterprisePreferences.lastPlayed()
    val videos = remember(state.filteredAndSortedItems, hideWatched) {
        val playable = playableVideos(state.filteredAndSortedItems)
        if (hideWatched) {
            val continueOnlyItems = playable.filter { item ->
                PlaybackProgressTracker.hasResume(item.chatId, item.messageId, item.fileId)
            }
            // UX fallback: if nothing has progress yet, don't show an empty media area.
            if (continueOnlyItems.isEmpty()) playable else continueOnlyItems
        } else {
            playable
        }
    }
    val browseStateKey = viewModel.currentBrowseStateKey()
    val browseRestoration = remember(browseStateKey) { viewModel.browseRestoration(browseStateKey) }
    val libraryGridState = remember(browseStateKey) {
        LazyGridState(
            firstVisibleItemIndex = browseRestoration.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = browseRestoration.firstVisibleItemScrollOffset
        )
    }
    val restoreFocusRequester = remember(browseStateKey) { FocusRequester() }
    var restorationApplied by remember(browseStateKey) { mutableStateOf(false) }

    DisposableEffect(browseStateKey, libraryGridState) {
        onDispose {
            viewModel.saveBrowseViewport(
                browseStateKey,
                libraryGridState.firstVisibleItemIndex,
                libraryGridState.firstVisibleItemScrollOffset
            )
        }
    }

    // The library fetches media metadata a page at a time from TDLib. Near the end of the
    // currently loaded grid, prefetch the next metadata page automatically, without downloading
    // video bytes; playback remains the responsibility of the existing ExoPlayer stream path.
    LaunchedEffect(libraryMode, videos.size, state.hasMore, state.isLoading, state.isLoadingMore) {
        if (!libraryMode) return@LaunchedEffect
        snapshotFlow { libraryGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                viewModel.saveBrowseViewport(
                    browseStateKey,
                    libraryGridState.firstVisibleItemIndex,
                    libraryGridState.firstVisibleItemScrollOffset
                )
                val threshold = (videos.size - LIBRARY_PREFETCH_ITEMS_REMAINING).coerceAtLeast(0)
                if (!state.isLoading && !state.isLoadingMore && state.hasMore &&
                    videos.isNotEmpty() && lastVisibleIndex >= threshold
                ) {
                    viewModel.loadMoreIfNeeded()
                }
            }
    }

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    LaunchedEffect(libraryMode, browseStateKey, videos.size, browseRestoration.focusedMediaKey) {
        if ((!libraryMode && explorerPosition.level != com.vaydelux.bousygram.presentation.media.vm.ExplorerLevel.MEDIA) ||
            restorationApplied || videos.isEmpty()
        ) return@LaunchedEffect
        val targetKey = browseRestoration.focusedMediaKey
        if (targetKey == null) {
            restorationApplied = true
            return@LaunchedEffect
        }
        val targetIndex = videos.indexOfFirst { "${it.chatId}:${it.messageId}" == targetKey }
        if (targetIndex >= 0) {
            libraryGridState.scrollToItem(targetIndex)
            delay(80L)
            runCatching { restoreFocusRequester.requestFocus() }
        }
        restorationApplied = true
    }

    BackHandler(enabled = state.quickSearchQuery.isNotBlank()) {
        viewModel.clearQuickSearchQuery()
    }

    BackHandler(enabled = libraryMode && state.selectedTopicId != null && state.quickSearchQuery.isBlank()) {
        viewModel.selectTopic(null)
    }

    // Home is a directory explorer, not a flat category carousel.
    // One Back moves Media -> Topics/Source and Topics -> Sources before the
    // Activity can return to the product mode picker.
    BackHandler(
        enabled = !libraryMode &&
            state.quickSearchQuery.isBlank() &&
            explorerPosition.section != com.vaydelux.bousygram.presentation.media.vm.ExplorerSection.SAVED &&
            explorerPosition.level != com.vaydelux.bousygram.presentation.media.vm.ExplorerLevel.SOURCES
    ) {
        viewModel.explorerBack()
    }

    val launcher = rememberMediaLauncher(
        screenKey = if (libraryMode) LAUNCH_SCREEN_LIBRARY else LAUNCH_SCREEN_HOME,
        onPlay = onPlay,
        onError = onStreamError
    )

    fun openVideo(item: MediaItem, sectionKey: String? = null) {
        viewModel.recordBrowseFocus(item)
        launcher.launch(item, videos, sectionKey)
    }

    fun openLastPlayed() {
        val last = EnterprisePreferences.lastPlayed()
        val loaded = last?.let { saved ->
            state.filteredAndSortedItems.firstOrNull {
                it.chatId == saved.chatId && it.messageId == saved.messageId
            } ?: videos.firstOrNull { it.fileId == saved.fileId }
        }
        if (loaded != null) {
            openVideo(loaded)
            return
        }
        viewModel.loadLastPlayed { item, error ->
            val activity = context as? Activity
            val action = {
                if (item != null) openVideo(item) else onStreamError(error ?: "Could not load the last-played item")
            }
            if (activity != null) activity.runOnUiThread(action) else action()
        }
    }

    fun toggleOffline(item: MediaItem) {
        val fileId = item.fileId ?: return
        if (OfflineCacheManager.isPinned(fileId)) OfflineCacheManager.unpinFile(fileId)
        else OfflineCacheManager.pinFile(fileId)
    }

    LaunchedEffect(resumeSignal) {
        if (resumeSignal > 0 && !libraryMode) {
            openLastPlayed()
            onResumeConsumed()
        }
    }

    val voiceSearchController = rememberVoiceSearchController(
        promptProvider = {
            buildVoiceSearchPrompt(SearchMode.InChat, state.selectedChatTitle)
        },
        onPartialText = { partial ->
            viewModel.setQuickSearchQuery(partial)
        },
        onQueryRecognized = { recognizedQuery ->
            viewModel.executeRecentSearch(recognizedQuery)
        }
    )

    val pagePadding = if (adaptive.isCompact) 12.dp else 28.dp
    val posterMin = if (adaptive.isCompact) 160.dp else 220.dp
    // One explorer implementation for Home and Library: no legacy horizontal rails.
    Box(modifier = modifier.fillMaxSize()) {
            TelegramExplorerDashboard(
                position = explorerPosition,
                sources = state.videoChats,
                topics = state.availableTopics,
                selectedChatTitle = state.selectedChatTitle,
                selectedChatId = state.selectedChatId,
                selectedTopicId = state.selectedTopicId,
                videos = videos,
                isLoading = state.isLoading,
                isLoadingSources = state.isSidebarLoading,
                isLoadingTopics = state.isLoadingTopics,
                errorMessage = state.error,
                sourceErrorMessage = state.sidebarError,
                isLoadingMore = state.isLoadingMore,
                hasMore = state.hasMore,
                privacyMode = safeMode,
                gridState = libraryGridState,
                onSelectTab = viewModel::selectExplorerSection,
                onSelectSource = viewModel::enterExplorerSource,
                onSelectTopic = viewModel::enterExplorerTopic,
                onGoUp = { viewModel.explorerBack() },
                onPlay = { openVideo(it) },
                onFocusMedia = { item ->
                    viewModel.recordBrowseFocus(item)
                    viewModel.onItemFocused(item)
                },
                restoreFocusedMediaKey = browseRestoration.focusedMediaKey,
                restoreFocusRequester = restoreFocusRequester,
                onLoadMore = viewModel::loadMoreIfNeeded,
                onRefreshSources = viewModel::refreshVideoChats,
                onRefreshTopics = { state.selectedChatId?.let(viewModel::loadForumTopics) },
                onRefreshMedia = viewModel::refreshCurrentMedia,
                onSearch = {
                    val chatId = if (state.isSavedMessagesSelected) null else state.selectedChatId
                    onOpenSearch(chatId, state.selectedChatTitle)
                },
                onFavorites = onOpenFavorites,
                onSettings = onOpenSettings,
                hasResume = lastPlayed != null,
                onResume = ::openLastPlayed,
                modifier = Modifier.fillMaxSize()
            )
        LaunchOpeningIndicator(
            launcher = launcher,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

/**
 * Search bar with integrated microphone voice search button for BrowseScreen.
 */
@Composable
fun BrowseSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onVoiceSearch: () -> Unit,
    isVoiceListening: Boolean = false,
    placeholder: String = "Search videos",
    modifier: Modifier = Modifier
) {
    BousygramTextField(
        value = query,
        onValueChange = onQueryChange,
        label = if (isVoiceListening) "Listening... speak now" else placeholder,
        modifier = modifier
            .fillMaxWidth()
            .testTag("browse_search_bar"),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        trailingIcon = {
            SearchBarVoiceButton(
                onClick = onVoiceSearch,
                isListening = isVoiceListening
            )
        }
    )
}

/**
 * Recent Searches component in BrowseScreen that displays cached search queries from
 * the Room database, allowing users to tap on any chip to re-execute the search.
 */
@Composable
fun RecentSearches(
    recentSearches: List<RecentSearchQuery>,
    activeQuery: String,
    onSelectQuery: (String) -> Unit,
    onRemoveQuery: (String) -> Unit,
    onClearActiveQuery: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenFullSearch: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    RecentSearchesSection(
        recentSearches = recentSearches,
        activeQuery = activeQuery,
        onSelectQuery = onSelectQuery,
        onRemoveQuery = onRemoveQuery,
        onClearActiveQuery = onClearActiveQuery,
        onClearHistory = onClearHistory,
        onOpenFullSearch = onOpenFullSearch,
        modifier = modifier
    )
}


@Composable
private fun SortChip(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "chipScale")
    val color = when {
        selected -> MaterialTheme.colorScheme.primary
        focused -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
    }
    Surface(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .border(
                if (focused) 3.dp else if (selected) 1.5.dp else 0.dp,
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(14.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        color = color,
        shape = RoundedCornerShape(14.dp)
    ) {
        val textColor = when {
            selected -> MaterialTheme.colorScheme.onPrimary
            focused -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface
        }
        Text(
            text = text,
            color = textColor,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun RefreshChip(text: String = "Refresh", onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "refreshScale")
    Surface(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(14.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

private const val LIBRARY_PREFETCH_ITEMS_REMAINING = 6
const val LAUNCH_SCREEN_HOME = "home"
const val LAUNCH_SCREEN_LIBRARY = "library"
