package com.vaydelux.bousygram.presentation.media

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.domain.model.ForumTopicChannel
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.VideoChatItem
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BrowseGridShimmerSkeleton
import com.vaydelux.bousygram.presentation.components.MediaPosterCard
import com.vaydelux.bousygram.presentation.components.PremiumGridBackdrop
import com.vaydelux.bousygram.presentation.components.BousygramSectionTab
import com.vaydelux.bousygram.presentation.components.TvFocusSurface
import com.vaydelux.bousygram.presentation.theme.bousygramResponsiveTokens
import com.vaydelux.bousygram.presentation.components.BousygramPageHeading
import com.vaydelux.bousygram.presentation.components.BousygramStatusPill
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.media.vm.BrowseRestoration
import com.vaydelux.bousygram.presentation.media.vm.ExplorerLevel
import com.vaydelux.bousygram.presentation.media.vm.ExplorerPosition
import com.vaydelux.bousygram.presentation.media.vm.ExplorerSection
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay

/** Real Telegram directories. No synthetic topics, no horizontal rails. */
@Composable
fun TelegramExplorerDashboard(
    position: ExplorerPosition,
    sources: List<VideoChatItem>,
    topics: List<ForumTopicChannel>,
    selectedChatTitle: String,
    selectedChatId: Long?,
    selectedTopicId: Int?,
    videos: List<MediaItem>,
    isLoading: Boolean,
    isLoadingSources: Boolean,
    isLoadingTopics: Boolean,
    errorMessage: String?,
    sourceErrorMessage: String?,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    discardedMediaCount: Int,
    privacyMode: Boolean,
    gridState: LazyGridState,
    sourceFolderRestoration: BrowseRestoration,
    onSourceFocused: (VideoChatItem) -> Unit,
    onSourceViewport: (Int, Int) -> Unit,
    onSelectTab: (ExplorerSection) -> Unit,
    onSelectSource: (VideoChatItem) -> Unit,
    onSelectTopic: (Int) -> Unit,
    onBrowseAllMedia: () -> Unit,
    focusedTopicId: Int?,
    onTopicFocused: (Int) -> Unit,
    onGoUp: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onFocusMedia: (MediaItem) -> Unit,
    restoreFocusedMediaKey: String?,
    restoreFocusRequester: FocusRequester,
    onLoadMore: () -> Unit,
    onRefreshSources: () -> Unit,
    onRefreshTopics: () -> Unit,
    onRefreshMedia: () -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit,
    hasResume: Boolean,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val adaptive = rememberAdaptiveMetrics()
    val design = bousygramResponsiveTokens(adaptive.profile)
    val gap = design.gridGap
    val cardMin = design.cardMinWidth
    val directory = remember(sources, position.section) {
        explorerSources(sources, position.section)
    }
    val channelCount = remember(sources) { explorerSources(sources, ExplorerSection.CHANNELS).size }
    val groupCount = remember(sources) { explorerSources(sources, ExplorerSection.GROUPS).size }
    val showSources = position.level == ExplorerLevel.SOURCES && position.section != ExplorerSection.SAVED
    val showTopics = position.level == ExplorerLevel.TOPICS && position.section == ExplorerSection.GROUPS
    val showMedia = !showSources && !showTopics
    val folderGrid = remember(position.section) {
        LazyGridState(
            firstVisibleItemIndex = sourceFolderRestoration.firstVisibleItemIndex
                .coerceAtMost((directory.size - 1).coerceAtLeast(0)),
            firstVisibleItemScrollOffset = sourceFolderRestoration.firstVisibleItemScrollOffset
        )
    }
    val sourceFocusRequester = remember(position.section) { FocusRequester() }
    val topicGrid = remember(selectedChatId) { LazyGridState() }
    val topicFocusRequester = remember(selectedChatId) { FocusRequester() }

    LaunchedEffect(showTopics, selectedChatId, focusedTopicId, topics.size) {
        if (!showTopics || focusedTopicId == null) return@LaunchedEffect
        val targetIndex = topics.indexOfFirst { it.id == focusedTopicId }
        if (targetIndex >= 0) {
            topicGrid.scrollToItem(targetIndex)
            delay(60L)
            runCatching { topicFocusRequester.requestFocus() }
        }
    }

    DisposableEffect(showSources, position.section, folderGrid) {
        onDispose {
            if (showSources) {
                onSourceViewport(folderGrid.firstVisibleItemIndex, folderGrid.firstVisibleItemScrollOffset)
            }
        }
    }

    // An async GetChat batch changes directory.size repeatedly. Restore focus once
    // per directory visit, not on every batch (which steals D-pad focus mid-navigation).
    var restoredDirectoryFocus by remember(position.section) { mutableStateOf(false) }
    LaunchedEffect(showSources) {
        if (!showSources) restoredDirectoryFocus = false
    }
    LaunchedEffect(showSources, directory.size, sourceFolderRestoration.focusedMediaKey) {
        if (!showSources || restoredDirectoryFocus) return@LaunchedEffect
        val target = sourceFolderRestoration.focusedMediaKey ?: return@LaunchedEffect
        val targetIndex = directory.indexOfFirst { it.chatId.toString() == target }
        if (targetIndex >= 0) {
            folderGrid.scrollToItem(targetIndex)
            delay(60L)
            if (runCatching { sourceFocusRequester.requestFocus() }.isSuccess) {
                restoredDirectoryFocus = true
            }
        }
    }

    BackHandler(enabled = position.section != ExplorerSection.SAVED && position.level != ExplorerLevel.SOURCES) {
        onGoUp()
    }
    LaunchedEffect(showMedia, videos.size, hasMore, isLoading, isLoadingMore, discardedMediaCount) {
        if (!showMedia || videos.isEmpty()) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { index ->
                if (index >= videos.size - 6 && hasMore && discardedMediaCount == 0 && !isLoading && !isLoadingMore) onLoadMore()
            }
    }

    PremiumGridBackdrop(modifier = modifier) {
    Column(
        modifier = Modifier.fillMaxSize()
            .padding(
                horizontal = design.pagePadding,
                vertical = if (adaptive.isTv) 18.dp else 14.dp
            ),
        verticalArrangement = Arrangement.spacedBy(if (adaptive.isTv) 8.dp else 10.dp)
    ) {
        // Content-first toolbar: two compact rows instead of a full page hero,
        // three separate navigation/action rows and an oversized collection panel.
        // Preserve collection identity in one line so media remains above the fold.
        val folderTitle = when {
            position.section == ExplorerSection.SAVED -> "Saved Messages"
            showSources && position.section == ExplorerSection.CHANNELS -> "Your channels"
            showSources -> "Your groups"
            showTopics -> "Forum topics"
            else -> selectedChatTitle.ifBlank { "Media" }
        }
        val collectionStatus = when {
            showSources -> explorerDirectoryStatus(position.section, directory.size, isLoadingSources)
            showTopics -> if (isLoadingTopics) "${topics.size} topics · Syncing"
                else "${topics.size} topics"
            isLoading -> "${videos.size} files · Loading"
            else -> "${videos.size} files"
        }
        TelegramExplorerCompactHeader(
            section = position.section,
            folderTitle = folderTitle,
            collectionStatus = collectionStatus,
            widthDp = adaptive.widthDp,
            heightDp = adaptive.heightDp,
            isTv = adaptive.isTv,
            privacyMode = privacyMode,
            isLoading = (showSources && isLoadingSources) ||
                (showTopics && isLoadingTopics) || (showMedia && isLoading),
            canGoUp = position.section != ExplorerSection.SAVED &&
                position.level != ExplorerLevel.SOURCES,
            showSources = showSources,
            showTopics = showTopics,
            hasResume = hasResume,
            hasTopicError = errorMessage != null,
            channelCount = channelCount,
            groupCount = groupCount,
            onSelectTab = onSelectTab,
            onGoUp = onGoUp,
            onBrowseAllMedia = onBrowseAllMedia,
            onRefreshSources = onRefreshSources,
            onRefreshTopics = onRefreshTopics,
            onRefreshMedia = onRefreshMedia,
            onResume = onResume,
            onSearch = onSearch,
            onFavorites = onFavorites,
            onSettings = onSettings
        )
        Box(Modifier.fillMaxWidth().weight(1f).testTag("telegram-explorer-grid")) {
            when {
                showSources -> {
                    if (directory.isEmpty()) {
                        if (isLoadingSources) ExplorerLoadingGrid("Loading source directory…", cardMin)
                        else ExplorerMessage(
                            message = sourceErrorMessage ?: "No joined sources in this section",
                            onRetry = onRefreshSources
                        )
                    } else LazyVerticalGrid(
                        state = folderGrid, columns = GridCells.Adaptive(cardMin),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(gap),
                        contentPadding = PaddingValues(3.dp, 4.dp, 3.dp, 16.dp)
                    ) {
                        items(directory, key = { "chat:" + it.chatId }) { source ->
                            ExplorerFolderCard(
                                title = source.title,
                                subtitle = explorerSourceLabel(source),
                                detail = explorerSourceDetail(source),
                                badge = if (source.isArchived) "ARCHIVED" else if (source.isForum) "TOPICS" else "MEDIA",
                                symbol = if (source.isForum) "▦" else if (source.kind == com.vaydelux.bousygram.domain.model.ChatSourceKind.CHANNEL) "▣" else "▤",
                                isTv = adaptive.isTv,
                                modifier = if (sourceFolderRestoration.focusedMediaKey == source.chatId.toString()) {
                                    Modifier.focusRequester(sourceFocusRequester)
                                } else Modifier,
                                onFocused = { onSourceFocused(source) },
                                onClick = {
                                    onSourceFocused(source)
                                    onSelectSource(source)
                                }
                            )
                        }
                    }
                }
                showTopics -> {
                    if (topics.isEmpty()) {
                        if (isLoadingTopics) ExplorerLoadingGrid("Loading forum topics…", cardMin)
                        else ExplorerMessage(
                            message = errorMessage ?: "No accessible topics in this group",
                            onRetry = onRefreshTopics
                        )
                    } else LazyVerticalGrid(
                        state = topicGrid,
                        columns = GridCells.Adaptive(cardMin),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(gap),
                        contentPadding = PaddingValues(3.dp, 4.dp, 3.dp, 16.dp)
                    ) {
                        items(topics, key = { "topic:" + selectedChatId + ":" + it.id }) { topic ->
                            ExplorerFolderCard(
                                title = topic.name,
                                subtitle = if (topic.isGeneral) "General topic" else "Forum topic",
                                detail = "Open messages and shared media",
                                badge = if (topic.isGeneral) "GENERAL" else "TOPIC",
                                symbol = "▦",
                                isTv = adaptive.isTv,
                                modifier = if (focusedTopicId == topic.id) {
                                    Modifier.focusRequester(topicFocusRequester)
                                } else Modifier,
                                onFocused = { onTopicFocused(topic.id) },
                                onClick = {
                                    onTopicFocused(topic.id)
                                    onSelectTopic(topic.id)
                                }
                            )
                        }
                    }
                }
                videos.isEmpty() -> {
                    if (isLoading) ExplorerLoadingGrid("Loading media…", cardMin)
                    else ExplorerMessage(
                        message = errorMessage ?: "No playable media found in this folder",
                        onRetry = onRefreshMedia
                    )
                }
                else -> LazyVerticalGrid(
                    state = gridState, columns = GridCells.Adaptive(cardMin),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    contentPadding = PaddingValues(3.dp, 4.dp, 3.dp, 16.dp)
                ) {
                    items(videos, key = { it.chatId.toString() + ":" + it.messageId }) { item ->
                        MediaPosterCard(
                            media = item,
                            subtitle = selectedChatTitle,
                            cardWidth = null,
                            privacyMode = privacyMode,
                            modifier = Modifier.fillMaxWidth()
                                .then(
                                    if (restoreFocusedMediaKey == item.chatId.toString() + ":" + item.messageId) {
                                        Modifier.focusRequester(restoreFocusRequester)
                                    } else Modifier
                                ),
                            onClick = { onPlay(item) },
                            onFocusChanged = { focused -> if (focused) onFocusMedia(item) }
                        )
                    }
                    if (discardedMediaCount > 0) {
                        item(key = "bounded-media-notice", span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().testTag("telegram-media-window-notice"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Showing ${videos.size} results in this window. Earlier cards were released to save memory.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = onRefreshMedia,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("telegram-media-from-start")
                                ) { Text("From start") }
                            }
                        }
                        if (hasMore && !isLoadingMore) {
                            item(key = "manual-older-media", span = { GridItemSpan(maxLineSpan) }) {
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .testTag("telegram-media-load-older"),
                                    onClick = onLoadMore
                                ) { Text("More older media") }
                            }
                        }
                    }
                    if (isLoadingMore) item(key = "loading-more") { Text("Loading more…") }
                }
            }
        }
    }
    }
}

/** Skeletons are shown only when the selected folder has no cached content. */
@Composable
private fun ExplorerLoadingGrid(message: String, cardMin: androidx.compose.ui.unit.Dp) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        BrowseGridShimmerSkeleton(
            columns = GridCells.Adaptive(cardMin),
            itemCount = 8,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ExplorerMessage(message: String, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onRetry != null) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Retry")
                }
            }
        }
    }
}

/**
 * One shared card for channels, groups and real forum topics.
 * Metadata is derived from TDLib; no placeholder ratings, thumbnails or item counts.
 * The whole card remains accessible via touch, keyboard and TV D-pad.
 */
@Composable
private fun ExplorerFolderCard(
    title: String,
    subtitle: String,
    detail: String,
    badge: String,
    symbol: String,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    TvFocusSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (isTv) BousygramUiTokens.folderMinTv
                else BousygramUiTokens.folderMinPhone)
            .semantics { contentDescription = "$title, $subtitle. $detail" }
            .onFocusChanged { if (it.isFocused) onFocused() }
    ) { focused ->
        Column(
            modifier = Modifier.padding(if (isTv) 18.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        symbol,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    badge,
                    color = if (focused) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "›",
                    color = accent,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
