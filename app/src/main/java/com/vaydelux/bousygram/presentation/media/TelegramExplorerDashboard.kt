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
    val gap = if (adaptive.isTv) 18.dp else 12.dp
    val cardMin = when {
        adaptive.isTv -> 224.dp
        adaptive.isTabletLayout -> 176.dp
        else -> 142.dp
    }
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

    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxSize().background(colors.background)
            .padding(
                horizontal = if (adaptive.isTv) BousygramUiTokens.tvPagePadding
                    else BousygramUiTokens.phonePagePadding,
                vertical = if (adaptive.isTv) 18.dp else 14.dp
            ),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        // The shell already displays the Bousygram brand; use this area
        // for screen identity and the user's available library actions.
        if (adaptive.isTv || adaptive.isTabletLayout) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BousygramPageHeading(
                    eyebrow = "Telegram media",
                    title = "Your library",
                    subtitle = "Saved media, channels and communities.",
                    compact = false,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasResume) OutlinedButton(
                        onClick = onResume,
                        modifier = Modifier.heightIn(min = 52.dp)
                    ) { Text("▶ Continue") }
                    OutlinedButton(onClick = onSearch, modifier = Modifier.heightIn(min = 52.dp)) {
                        Text("Search")
                    }
                    OutlinedButton(onClick = onFavorites, modifier = Modifier.heightIn(min = 52.dp)) {
                        Text("Favorites")
                    }
                    OutlinedButton(onClick = onSettings, modifier = Modifier.heightIn(min = 52.dp)) {
                        Text("Settings")
                    }
                }
            }
        } else {
            BousygramPageHeading(
                eyebrow = "Telegram media",
                title = "Your library",
                subtitle = "Saved media, channels and communities.",
                compact = true
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasResume) OutlinedButton(onClick = onResume, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("▶ Continue")
                }
                OutlinedButton(onClick = onSearch, modifier = Modifier.heightIn(min = 48.dp)) { Text("Search") }
                OutlinedButton(onClick = onFavorites, modifier = Modifier.heightIn(min = 48.dp)) { Text("Favorites") }
                OutlinedButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Settings") }
            }
        }
        // Auto-width section pills avoid cramming "Saved messages" into
        // three tiny equal-width tabs on compact phones.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .testTag("telegram-library-sections"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExplorerSection.entries.forEach { section ->
                val label = when (section) {
                    ExplorerSection.SAVED -> if (adaptive.isPhoneLayout) "Saved" else "Saved messages"
                    ExplorerSection.CHANNELS -> "Channels"
                    ExplorerSection.GROUPS -> "Groups"
                }
                val count = when (section) {
                    ExplorerSection.SAVED -> null
                    ExplorerSection.CHANNELS -> channelCount
                    ExplorerSection.GROUPS -> groupCount
                }
                val title = if (count != null && !adaptive.isPhoneLayout) "$label  $count" else label
                val active = section == position.section
                var tabFocused by remember(section) { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { if (!active) onSelectTab(section) },
                    modifier = Modifier.heightIn(min = if (adaptive.isTv) 56.dp else 48.dp)
                        .widthIn(min = if (adaptive.isTv) 155.dp else 94.dp)
                        .semantics { selected = active }
                        .onFocusChanged { tabFocused = it.isFocused }
                        .testTag("telegram-library-tab-" + section.name.lowercase()),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        if (tabFocused) 3.dp else 1.dp,
                        if (tabFocused) colors.primary
                        else if (active) colors.primary.copy(alpha = 0.65f)
                        else colors.outlineVariant
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = when {
                            active -> colors.primaryContainer
                            tabFocused -> colors.surfaceContainerHighest
                            else -> colors.surfaceContainerLow
                        },
                        contentColor = if (active) colors.onPrimaryContainer else colors.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        title, maxLines = 1,
                        style = if (adaptive.isTv) MaterialTheme.typography.titleSmall
                            else MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
        // Contextual collection header: concise on phones, comfortably readable on TV.
        val folderTitle = when {
            position.section == ExplorerSection.SAVED -> "Saved Messages"
            showSources && position.section == ExplorerSection.CHANNELS -> "Your channels"
            showSources -> "Your groups"
            showTopics -> "Forum topics"
            else -> selectedChatTitle
        }
        val folderDescription = when {
            position.section == ExplorerSection.SAVED -> "Media saved to your own Telegram chat"
            showSources && position.section == ExplorerSection.CHANNELS -> "Explore media from channels you follow"
            showSources -> "Explore your groups and forum discussions"
            showTopics -> "Choose a topic to browse its shared media"
            else -> "Videos, audio and documents in this Telegram collection"
        }
        val collectionStatus = when {
            showSources -> explorerDirectoryStatus(position.section, directory.size, isLoadingSources)
            showTopics -> if (isLoadingTopics) "${topics.size} topics found · Syncing" else "${topics.size} topics available"
            isLoading -> "${videos.size} media shown · Loading"
            else -> "${videos.size} media shown"
        }
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("telegram-library-collection"),
            shape = RoundedCornerShape(BousygramUiTokens.panelCorner),
            color = colors.surfaceContainerLow,
            border = BorderStroke(1.dp, colors.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (adaptive.isTv) 22.dp else 16.dp,
                    vertical = if (adaptive.isTv) 16.dp else 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!showSources && position.section != ExplorerSection.SAVED) {
                        OutlinedButton(
                            onClick = onGoUp,
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text("‹ Back") }
                    }
                    Text(
                        when (position.section) {
                            ExplorerSection.SAVED -> "PERSONAL  /  SAVED"
                            ExplorerSection.CHANNELS -> "TELEGRAM  /  CHANNELS"
                            ExplorerSection.GROUPS -> "TELEGRAM  /  GROUPS"
                        },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            folderTitle,
                            style = if (adaptive.isTv) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            folderDescription,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = if (adaptive.isPhoneLayout) 2 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (showSources && !isLoadingSources) {
                        OutlinedButton(
                            onClick = onRefreshSources,
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("Refresh") }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Reserve progress-icon width so asynchronous source updates do not
                    // make the title shift horizontally on each loading transition.
                    Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                        if ((showSources && isLoadingSources) || (showTopics && isLoadingTopics) || (showMedia && isLoading)) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        }
                    }
                    BousygramStatusPill(
                        collectionStatus,
                        accent = !isLoading && !isLoadingSources && !isLoadingTopics
                    )
                    if (privacyMode && !adaptive.isPhoneLayout) {
                        BousygramStatusPill("Privacy mode")
                    }
                }
                if (privacyMode && adaptive.isPhoneLayout) {
                    Text(
                        "Privacy mode enabled",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
                if (showTopics) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onBrowseAllMedia,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .testTag("forum-browse-all-media"),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) { Text("Browse group media", maxLines = 2) }
                        if (isLoadingTopics || errorMessage != null) {
                            OutlinedButton(
                                onClick = onRefreshTopics,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    .testTag("forum-retry-topics"),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) { Text("Retry topics", maxLines = 2) }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
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
                        contentPadding = PaddingValues(3.dp, 8.dp, 3.dp, 40.dp)
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
                        contentPadding = PaddingValues(3.dp, 8.dp, 3.dp, 40.dp)
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
                    contentPadding = PaddingValues(3.dp, 8.dp, 3.dp, 40.dp)
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
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(BousygramUiTokens.cardCorner)
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (isTv) 182.dp else 164.dp)
            .border(
                if (focused) BousygramUiTokens.tvFocusOutline else 1.dp,
                if (focused) accent else MaterialTheme.colorScheme.outlineVariant,
                shape
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$title, $subtitle. $detail" },
        shape = shape,
        color = if (focused) MaterialTheme.colorScheme.surfaceContainerHighest
            else MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = if (focused) 3.dp else 0.dp
    ) {
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
