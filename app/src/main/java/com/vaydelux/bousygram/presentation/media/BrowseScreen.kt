package com.vaydelux.bousygram.presentation.media

import android.app.Activity
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
import com.vaydelux.bousygram.presentation.components.MediaPosterCard
import com.vaydelux.bousygram.presentation.layout.TvHybridHomeDashboard
import com.vaydelux.bousygram.presentation.media.vm.MediaViewModel

@Composable
fun BrowseScreen(
    onPlay: (PlaybackRequest) -> Unit,
    onOpenSearch: (chatId: Long?, chatTitle: String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onStreamError: (String) -> Unit = {},
    libraryMode: Boolean = false,
    resumeSignal: Int = 0,
    onResumeConsumed: () -> Unit = {},
    viewModel: MediaViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val adaptive = rememberAdaptiveMetrics()
    val context = LocalContext.current
    var favoriteKeys by remember { mutableStateOf(EnterprisePreferences.favoriteKeys()) }
    var safeMode by remember { mutableStateOf(EnterprisePreferences.safeMode()) }
    var hideWatched by remember { mutableStateOf(EnterprisePreferences.hideWatched()) }
    var pinnedChats by remember { mutableStateOf(EnterprisePreferences.pinnedChatIds()) }
    var showAllChatsPicker by remember { mutableStateOf(false) }
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
    val libraryGridState = rememberLazyGridState()

    // The library fetches media metadata a page at a time from TDLib. Near the end of the
    // currently loaded grid, prefetch the next metadata page automatically, without downloading
    // video bytes; playback remains the responsibility of the existing ExoPlayer stream path.
    LaunchedEffect(libraryMode, videos.size, state.hasMore, state.isLoading, state.isLoadingMore) {
        if (!libraryMode) return@LaunchedEffect
        snapshotFlow { libraryGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
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

    fun openVideo(item: MediaItem) {
        val fileId = item.fileId ?: return
        fun onUiThread(block: () -> Unit) {
            val activity = context as? Activity
            if (activity != null) activity.runOnUiThread(block) else block()
        }
        EnterprisePreferences.setLastPlayed(item.chatId, item.messageId, fileId, item.title)
        OfflineCacheManager.markFileAccessed(fileId)
        viewModel.preparePlayback(item) { url, error ->
            onUiThread {
                if (url.isNullOrBlank()) {
                    onStreamError(error ?: "Could not prepare stream")
                    return@onUiThread
                }
                val queueItems = if (videos.any { it.chatId == item.chatId && it.messageId == item.messageId }) {
                    videos
                } else {
                    listOf(item) + videos
                }
                onPlay(buildPlaybackRequest(item, url, queueItems, null))
            }
        }
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

    val pagePadding = if (adaptive.isCompact) 12.dp else 28.dp
    val posterMin = if (adaptive.isCompact) 160.dp else 220.dp
    Box(modifier = modifier.fillMaxSize()) {
        if (libraryMode) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)))
                    .padding(horizontal = pagePadding, vertical = if (adaptive.isCompact) 12.dp else 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Video library",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = if (adaptive.isCompact) {
                                MaterialTheme.typography.headlineSmall
                            } else {
                                MaterialTheme.typography.headlineLarge
                            }
                        )
                        Text(
                            if (safeMode) "Private browsing · video media only" else "${state.selectedChatTitle} · ${videos.size} videos",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = if (adaptive.isCompact) {
                                MaterialTheme.typography.bodySmall
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            maxLines = 1
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.hasMore) {
                            RefreshChip(if (state.isLoadingMore) "Loading…" else "Load more") {
                                viewModel.loadMoreIfNeeded()
                            }
                        }
                        RefreshChip("Search source") {
                            val chatId = if (state.isSavedMessagesSelected) null else state.selectedChatId
                            onOpenSearch(chatId, state.selectedChatTitle)
                        }
                    }
                }
                ChatSourceTabs(
                    chats = state.videoChats,
                    savedSelected = state.isSavedMessagesSelected,
                    selectedChatId = state.selectedChatId,
                    pinnedChatIds = pinnedChats,
                    onSelectSavedMessages = viewModel::load,
                    onSelectChat = { viewModel.loadChat(it.chatId, it.title) },
                    onOpenAllChats = { showAllChatsPicker = true }
                )
                if (state.isSidebarLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Syncing Telegram chats…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { SortChip("Newest", state.sortOrder == SortOrder.DATE_DESC) { viewModel.setSortOrder(SortOrder.DATE_DESC) } }
                    item { SortChip("Oldest", state.sortOrder == SortOrder.DATE_ASC) { viewModel.setSortOrder(SortOrder.DATE_ASC) } }
                    item { SortChip("Largest", state.sortOrder == SortOrder.SIZE_DESC) { viewModel.setSortOrder(SortOrder.SIZE_DESC) } }
                    item { SortChip("Smallest", state.sortOrder == SortOrder.SIZE_ASC) { viewModel.setSortOrder(SortOrder.SIZE_ASC) } }
                    item { SortChip("Continue only", hideWatched) {
                        hideWatched = !hideWatched
                        EnterprisePreferences.setHideWatched(hideWatched)
                    } }
                    item { SortChip("Privacy mode", safeMode) {
                        safeMode = !safeMode
                        EnterprisePreferences.setSafeMode(safeMode)
                    } }
                    item { SortChip("Pin source", EnterprisePreferences.isPinnedChat(state.selectedChatId)) {
                        EnterprisePreferences.togglePinnedChat(state.selectedChatId)
                        pinnedChats = EnterprisePreferences.pinnedChatIds()
                    } }
                    item { RefreshChip("Refresh", { viewModel.refresh(); viewModel.refreshVideoChats() }) }
                }
                if (!state.sidebarError.isNullOrBlank()) {
                    Text(state.sidebarError.orEmpty(), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                }
                if (!state.error.isNullOrBlank()) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.isLoading && videos.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        videos.isEmpty() -> EmptyStateView(
                            title = "No videos in this source",
                            message = if (safeMode) "No videos in this source. Choose another source or tap Refresh." else "No videos found in ${state.selectedChatTitle}. Choose another source or refresh.",
                            actionText = "Refresh",
                            onAction = { viewModel.refresh(); viewModel.refreshVideoChats() },
                            modifier = Modifier.align(Alignment.Center)
                        )
                        else -> LazyVerticalGrid(
                            state = libraryGridState,
                            columns = GridCells.Adaptive(posterMin),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                        ) {
                            items(videos, key = { "${it.chatId}:${it.messageId}" }) { item ->
                                MediaPosterCard(
                                    media = item,
                                    subtitle = state.selectedChatTitle,
                                    onClick = { openVideo(item) },
                                    cardWidth = null,
                                    privacyMode = safeMode,
                                    modifier = Modifier.fillMaxWidth(),
                                    onFocusChanged = { focused -> if (focused) viewModel.onItemFocused(item) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            TvHybridHomeDashboard(
                featuredVideos = videos,
                channelLabel = state.selectedChatTitle,
                isLoading = state.isLoading,
                errorMessage = state.error,
                onVideoSelected = ::openVideo,
                onOpenSearch = {
                    val chatId = if (state.isSavedMessagesSelected) null else state.selectedChatId
                    onOpenSearch(chatId, state.selectedChatTitle)
                },
                onOpenLibrary = onOpenLibrary,
                onOpenFavorites = onOpenFavorites,
                onOpenSettings = onOpenSettings,
                onOpenLastPlayed = ::openLastPlayed,
                lastPlayedFileId = lastPlayed?.fileId,
                lastPlayedTitle = lastPlayed?.title,
                onToggleFavorite = {
                    EnterprisePreferences.toggleFavorite(it.chatId, it.messageId)
                    favoriteKeys = EnterprisePreferences.favoriteKeys()
                },
                onToggleOffline = ::toggleOffline,
                onVideoFocused = viewModel::onItemFocused,
                favoriteKeys = favoriteKeys,
                sourceChats = state.videoChats,
                savedMessagesSelected = state.isSavedMessagesSelected,
                selectedChatId = state.selectedChatId,
                pinnedChatIds = pinnedChats,
                onSelectSavedMessages = viewModel::load,
                onSelectChat = { viewModel.loadChat(it.chatId, it.title) },
                onOpenAllChats = { showAllChatsPicker = true },
                offlineFileIds = videos.mapNotNull { it.fileId }.filter { OfflineCacheManager.isPinned(it) }.toSet(),
                resumeMessageKeys = videos
                    .filter { PlaybackProgressTracker.hasResume(it.chatId, it.messageId, it.fileId) }
                    .map { "${it.chatId}:${it.messageId}" }
                    .toSet(),
                safeMode = safeMode,
                selectedCategoryTab = state.selectedCategoryTab,
                selectedSeriesTitle = state.selectedSeriesTitle,
                seriesGroups = state.seriesGroups,
                topicGroups = state.topicGroups,
                availableTopics = state.availableTopics,
                selectedTopicId = state.selectedTopicId,
                onSelectCategoryTab = viewModel::setCategoryTab,
                onSelectSeries = viewModel::selectSeries,
                onSelectTopic = viewModel::selectTopic,
                onOpenExternalPlayer = { item ->
                    ExternalPlayerLauncher.launch(context, item)
                },
                onDeleteMedia = { item ->
                    viewModel.deleteMediaItem(item) {
                        viewModel.refresh()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (showAllChatsPicker) {
            AllChatsPickerDialog(
                chats = state.videoChats,
                selectedChatId = state.selectedChatId,
                savedSelected = state.isSavedMessagesSelected,
                pinnedChatIds = pinnedChats,
                isLoading = state.isSidebarLoading,
                onDismiss = { showAllChatsPicker = false },
                onSelectSavedMessages = {
                    showAllChatsPicker = false
                    viewModel.load()
                },
                onSelectChat = { chat ->
                    showAllChatsPicker = false
                    viewModel.loadChat(chat.chatId, chat.title)
                }
            )
        }
        if (state.preparingFileId != null) {
            LoadingOverlay(
                text = "Preparing video...",
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f))
            )
        }
    }
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
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
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
                1.dp,
                if (focused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
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
