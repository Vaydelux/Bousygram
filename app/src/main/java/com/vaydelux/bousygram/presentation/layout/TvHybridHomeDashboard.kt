package com.vaydelux.bousygram.presentation.layout

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.VideoChatItem
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import androidx.compose.material3.LinearProgressIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaydelux.bousygram.core.tdlib.utils.PlaybackProgressTracker
import com.vaydelux.bousygram.data.repository.ContinueWatchingEntry
import com.vaydelux.bousygram.data.repository.ContinueWatchingRepository
import com.vaydelux.bousygram.presentation.components.CenteredPlayBadge
import com.vaydelux.bousygram.presentation.components.ChatSourceTabs
import com.vaydelux.bousygram.presentation.components.EmptyStateView
import com.vaydelux.bousygram.presentation.components.GlassPill
import com.vaydelux.bousygram.presentation.components.ImagePlaceholderIcon
import com.vaydelux.bousygram.presentation.components.MediaPosterSkeletonCard
import com.vaydelux.bousygram.presentation.components.NoSearchResultsEmptyState
import com.vaydelux.bousygram.presentation.components.RecentSearchesQuickBar
import com.vaydelux.bousygram.presentation.components.ShimmerCard
import com.vaydelux.bousygram.presentation.components.rememberMiniThumbnail
import com.vaydelux.bousygram.presentation.components.TvFocusSurface
import com.vaydelux.bousygram.presentation.media.mediaDisplayLabels
import java.io.File

@Composable
fun TvHybridHomeDashboard(
    featuredVideos: List<MediaItem>,
    channelLabel: String,
    isLoading: Boolean,
    errorMessage: String?,
    onVideoSelected: (MediaItem) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLastPlayed: () -> Unit,
    lastPlayedFileId: Int?,
    lastPlayedTitle: String?,
    onToggleFavorite: (MediaItem) -> Unit,
    onToggleOffline: (MediaItem) -> Unit,
    favoriteKeys: Set<String>,
    sourceChats: List<VideoChatItem>,
    savedMessagesSelected: Boolean,
    selectedChatId: Long?,
    pinnedChatIds: Set<Long>,
    onSelectSavedMessages: () -> Unit,
    onSelectChat: (VideoChatItem) -> Unit,
    onOpenAllChats: () -> Unit,
    offlineFileIds: Set<Int>,
    resumeMessageKeys: Set<String>,
    safeMode: Boolean,
    modifier: Modifier = Modifier,
    onVideoFocused: (MediaItem) -> Unit = {},
    selectedCategoryTab: com.vaydelux.bousygram.domain.model.MediaCategoryTab = com.vaydelux.bousygram.domain.model.MediaCategoryTab.ALL,
    selectedSeriesTitle: String? = null,
    seriesGroups: List<com.vaydelux.bousygram.domain.model.SeriesGroup> = emptyList(),
    topicGroups: Map<String, List<MediaItem>> = emptyMap(),
    availableTopics: List<com.vaydelux.bousygram.domain.model.ForumTopicChannel> = emptyList(),
    selectedTopicId: Int? = null,
    onSelectCategoryTab: (com.vaydelux.bousygram.domain.model.MediaCategoryTab) -> Unit = {},
    onSelectSeries: (String?) -> Unit = {},
    onSelectTopic: (Int?) -> Unit = {},
    onOpenExternalPlayer: (MediaItem) -> Unit = {},
    onDeleteMedia: (MediaItem) -> Unit = {},
    onBackToNavBar: () -> Unit = {},
    recentSearches: List<com.vaydelux.bousygram.data.database.RecentSearchQuery> = emptyList(),
    activeQuickSearchQuery: String = "",
    onSelectRecentSearch: (String) -> Unit = {},
    onRemoveRecentSearch: (String) -> Unit = {},
    onClearQuickSearch: () -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
    onOpenSearchWithQuery: (String) -> Unit = {}
) {
    val adaptive = rememberAdaptiveMetrics()
    val compact = adaptive.isCompact
    val cardWidth: Dp = if (compact) 180.dp else 224.dp

    val continueWatchingRail by ContinueWatchingRepository.railItems.collectAsStateWithLifecycle()

    val continueItem = remember(featuredVideos, lastPlayedFileId, continueWatchingRail) {
        val topRail = continueWatchingRail.firstOrNull()
        if (topRail != null) {
            featuredVideos.firstOrNull { it.chatId == topRail.chatId && it.messageId == topRail.messageId }
                ?: MediaItem(
                    chatId = topRail.chatId,
                    messageId = topRail.messageId,
                    date = (topRail.updatedAt / 1000).toInt(),
                    type = com.vaydelux.bousygram.domain.model.MediaType.Video,
                    title = topRail.title,
                    fileId = topRail.fileId,
                    durationSeconds = (topRail.durationMs / 1000).toInt(),
                    thumbnailPath = topRail.thumbnailPath
                )
        } else {
            featuredVideos.firstOrNull { it.fileId == lastPlayedFileId }
                ?: featuredVideos.firstOrNull { resumeMessageKeys.contains("${it.chatId}:${it.messageId}") }
                ?: featuredVideos.firstOrNull()
        }
    }

    val favoriteVideos = remember(featuredVideos, favoriteKeys) {
        val favs = featuredVideos.filter { favoriteKeys.contains("${it.chatId}:${it.messageId}") }
        if (favs.isNotEmpty()) favs else featuredVideos.take(6)
    }

    var showDetailsDialog by remember { mutableStateOf(false) }
    var detailMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    var focusedMediaForBackdrop by remember { mutableStateOf<MediaItem?>(null) }
    var focusedMediaForHero by remember { mutableStateOf<MediaItem?>(null) }

    val activeHeroMedia = focusedMediaForHero ?: continueItem ?: featuredVideos.firstOrNull()
    val isPhoneLayout = compact || adaptive.isPhoneLayout

    val continueWatchingRowState = rememberLazyListState()
    val newInChatsRowState = rememberLazyListState()
    val chatRowStates = remember { mutableMapOf<Long, LazyListState>() }

    val isHeroFavorite = remember(activeHeroMedia, favoriteKeys) {
        if (activeHeroMedia == null) false
        else favoriteKeys.contains("${activeHeroMedia.chatId}:${activeHeroMedia.messageId}")
    }
    val hasHeroResume = remember(activeHeroMedia, continueWatchingRail) {
        if (activeHeroMedia == null) false
        else PlaybackProgressTracker.hasResume(activeHeroMedia.chatId, activeHeroMedia.messageId, activeHeroMedia.fileId) ||
                continueWatchingRail.any { it.chatId == activeHeroMedia.chatId && it.messageId == activeHeroMedia.messageId }
    }

    val backdropTarget = focusedMediaForHero ?: focusedMediaForBackdrop ?: continueItem
    val context = LocalContext.current
    val backdropMiniThumbnail = backdropTarget?.let { rememberMiniThumbnail(it.chatId, it.messageId, it.miniThumbnailBytes) }
    val preferredBackdrop = backdropTarget?.tmdbBackdropPath ?: backdropTarget?.tmdbPosterPath ?: backdropTarget?.thumbnailPath
    val backdropImageData = preferredBackdrop?.let { path ->
        if (path.startsWith("http")) path else File(path)
    } ?: backdropMiniThumbnail
    val backdropImageRequest = remember(backdropImageData) {
        backdropImageData?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .size(1280, 720)
                .build()
        }
    }

    val heroFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            heroFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Ambient dynamic backdrop sampling focused card poster
        if (backdropImageRequest != null) {
            AsyncImage(
                model = backdropImageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.22f }
                    .blur(36.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.background.copy(alpha = 0.35f),
                            colors.background.copy(alpha = 0.85f),
                            colors.background
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onKeyEvent { event ->
                    if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK && event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        onBackToNavBar()
                        true
                    } else {
                        false
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(
                    start = if (compact) 14.dp else 24.dp,
                    top = if (compact) 8.dp else 12.dp,
                    end = if (compact) 14.dp else 24.dp,
                    bottom = 32.dp
                ),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // 1. Hero banner at top
            ModernHeroBanner(
                video = activeHeroMedia,
                lastPlayedTitle = lastPlayedTitle,
                isFavorite = isHeroFavorite,
                hasResume = hasHeroResume,
                onPlayOrResume = {
                    if (activeHeroMedia != null) onVideoSelected(activeHeroMedia) else onOpenLastPlayed()
                },
                onToggleMyList = {
                    if (activeHeroMedia != null) onToggleFavorite(activeHeroMedia)
                },
                onDetails = {
                    detailMediaItem = activeHeroMedia
                    showDetailsDialog = true
                },
                isPhone = isPhoneLayout,
                focusRequester = heroFocusRequester
            )

            // 2. Row of chat sources
            if (sourceChats.isNotEmpty()) {
                ChatSourceTabs(
                    chats = sourceChats,
                    savedSelected = savedMessagesSelected,
                    selectedChatId = selectedChatId,
                    onSelectSavedMessages = onSelectSavedMessages,
                    onSelectChat = onSelectChat,
                    onOpenAllChats = onOpenAllChats,
                    pinnedChatIds = pinnedChatIds
                )
            }

            // Forum Topics Channel Rail (with # all-channels as first item)
            if (availableTopics.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "TOPICS",
                            color = colors.primary,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        )
                        Text(
                            text = "• ${availableTopics.size} topics",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            val isSelected = selectedTopicId == null
                            TvFocusSurface(
                                onClick = { onSelectTopic(null) },
                                selected = isSelected
                            ) { focused ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "#",
                                        color = if (isSelected) colors.primary else if (focused) colors.onSurface else colors.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "all-channels",
                                        color = if (isSelected) colors.primary else if (focused) colors.onSurface else colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                        fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                        items(availableTopics, key = { it.id }) { topic ->
                            val isSelected = selectedTopicId == topic.id
                            TvFocusSurface(
                                onClick = { onSelectTopic(topic.id) },
                                selected = isSelected
                            ) { focused ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    val icon = if (topic.isGeneral) "★" else "#"
                                    Text(
                                        text = icon,
                                        color = if (isSelected) colors.primary else if (focused) colors.onSurface else colors.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = topic.name.lowercase().replace(" ", "-"),
                                        color = if (isSelected) colors.primary else if (focused) colors.onSurface else colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                        fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium
                                    )
                                    val count = topicGroups[topic.name]?.size
                                        ?: topicGroups.entries.firstOrNull { it.key.equals(topic.name, ignoreCase = true) }?.value?.size
                                        ?: 0
                                    if (count > 0) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isSelected) colors.primary.copy(alpha = 0.25f) else colors.surfaceVariant,
                                            modifier = Modifier.padding(vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "$count",
                                                color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    } else if (topic.unreadCount > 0) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = colors.error,
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${topic.unreadCount.coerceAtMost(99)}",
                                                    color = colors.onError,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Rows: Continue Watching first (if available and no series detail opened)
            if (continueWatchingRail.isNotEmpty() && selectedSeriesTitle == null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Continue Watching",
                            color = colors.onBackground,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colors.surfaceVariant
                        ) {
                            Text(
                                text = "${continueWatchingRail.size}",
                                color = colors.primary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    LazyRow(
                        state = continueWatchingRowState,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(end = 24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(
                            continueWatchingRail,
                            key = { index, item -> "cw_${item.chatId}_${item.messageId}_$index" }
                        ) { index, item ->
                            val matchingMedia = featuredVideos.firstOrNull {
                                it.chatId == item.chatId && it.messageId == item.messageId
                            } ?: MediaItem(
                                chatId = item.chatId,
                                messageId = item.messageId,
                                date = (item.updatedAt / 1000).toInt(),
                                type = com.vaydelux.bousygram.domain.model.MediaType.Video,
                                title = item.title,
                                fileId = item.fileId,
                                durationSeconds = (item.durationMs / 1000).toInt(),
                                thumbnailPath = item.thumbnailPath
                            )
                            ContinueWatchingCard(
                                entry = item,
                                media = matchingMedia,
                                onClick = { onVideoSelected(matchingMedia) },
                                cardWidth = cardWidth,
                                onFocusChanged = {
                                    if (it) {
                                        focusedMediaForBackdrop = matchingMedia
                                        focusedMediaForHero = matchingMedia
                                        onVideoFocused(matchingMedia)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Quick access to Room-cached recent search queries
            if (!safeMode && (recentSearches.isNotEmpty() || activeQuickSearchQuery.isNotBlank())) {
                RecentSearchesQuickBar(
                    recentSearches = recentSearches,
                    activeQuery = activeQuickSearchQuery,
                    onSelectQuery = onSelectRecentSearch,
                    onRemoveQuery = onRemoveRecentSearch,
                    onClearActiveQuery = onClearQuickSearch,
                    onClearHistory = onClearRecentSearches,
                    onOpenFullSearch = onOpenSearchWithQuery
                )
            }

            // Series inference is intentionally excluded: Telegram chats/topics are the hierarchy.
            if (selectedSeriesTitle == null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    com.vaydelux.bousygram.domain.model.MediaCategoryTab.entries
                        .filterNot { it == com.vaydelux.bousygram.domain.model.MediaCategoryTab.SERIES }
                        .forEach { tab ->
                        val isSelected = tab == selectedCategoryTab
                        TvFocusSurface(
                            onClick = { onSelectCategoryTab(tab) },
                            selected = isSelected
                        ) { focused ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = tab.label,
                                    color = if (isSelected) colors.primary else if (focused) colors.onSurface else colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                    fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Error banner if TDLib reports issue
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.errorContainer.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        errorMessage,
                        color = colors.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            // Shimmer loading skeletons while waiting for TDLib media content
            if (isLoading && featuredVideos.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    repeat(2) { shelfIdx ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ShimmerCard(
                                    modifier = Modifier
                                        .width(if (shelfIdx == 0) 180.dp else 150.dp)
                                        .height(20.dp),
                                    rounded = 8.dp
                                )
                                ShimmerCard(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(18.dp),
                                    rounded = 6.dp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                repeat(if (compact) 2 else 4) {
                                    MediaPosterSkeletonCard(cardWidth = cardWidth)
                                }
                            }
                        }
                    }
                }
            } else if (featuredVideos.isEmpty() && !isLoading) {
                if (activeQuickSearchQuery.isNotBlank()) {
                    NoSearchResultsEmptyState(
                        query = activeQuickSearchQuery,
                        scopeLabel = channelLabel,
                        onClearQuery = onClearQuickSearch,
                        secondaryActionText = "Search All Telegram",
                        onSecondaryAction = { onOpenSearchWithQuery(activeQuickSearchQuery) },
                        suggestedSearches = recentSearches
                            .map { it.query }
                            .filterNot { it.equals(activeQuickSearchQuery, ignoreCase = true) },
                        onSelectSuggestion = onSelectRecentSearch,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)
                    )
                } else {
                    EmptyStateView(
                        title = "No videos in this source",
                        message = "Switch to another Telegram chat above or search across your channels.",
                        actionText = "Search",
                        onAction = onOpenSearch,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
                    )
                }
            } else if (seriesGroups.firstOrNull { it.title == selectedSeriesTitle } != null) {
                val activeSeries = seriesGroups.first { it.title == selectedSeriesTitle }
                var selectedSeasonFilter by remember(selectedSeriesTitle) { mutableStateOf<Int?>(null) }
                val seasons = remember(activeSeries) {
                    activeSeries.episodes.mapNotNull { it.seasonNumber }.distinct().sorted()
                }
                val displayedEpisodes = remember(activeSeries, selectedSeasonFilter) {
                    if (selectedSeasonFilter == null) activeSeries.episodes
                    else activeSeries.episodes.filter { it.seasonNumber == selectedSeasonFilter }
                }
                // Series Episode View (Chat -> Topic -> Series -> Episodes)
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TvFocusSurface(
                            onClick = { onSelectSeries(null) }
                        ) { focused ->
                            Text(
                                text = "← Back to Shows",
                                color = if (focused) colors.primary else colors.onSurface,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        Text(
                            text = activeSeries.title,
                            color = colors.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.primary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${activeSeries.totalSeasons} Season(s) • ${activeSeries.episodes.size} Episodes",
                                color = colors.primary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Season Filter Tabs if series has multiple seasons
                    if (seasons.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                val isSelected = selectedSeasonFilter == null
                                TvFocusSurface(
                                    onClick = { selectedSeasonFilter = null },
                                    selected = isSelected
                                ) { focused ->
                                    Text(
                                        text = "All Seasons (${activeSeries.episodes.size})",
                                        color = if (isSelected) colors.primary else if (focused) colors.onSurface else colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                        fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                            items(seasons) { seasonNum ->
                                val isSelected = selectedSeasonFilter == seasonNum
                                val count = activeSeries.episodes.count { it.seasonNumber == seasonNum }
                                TvFocusSurface(
                                    onClick = { selectedSeasonFilter = seasonNum },
                                    selected = isSelected
                                ) { focused ->
                                    Text(
                                        text = "Season $seasonNum ($count)",
                                        color = if (isSelected) colors.primary else if (focused) colors.onSurface else colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                        fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(end = 24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(
                            displayedEpisodes,
                            key = { idx, ep -> "ep_${ep.chatId}_${ep.messageId}_$idx" }
                        ) { index, episode ->
                            ModernVideoCard(
                                media = episode,
                                index = index,
                                isFavoriteSection = false,
                                onClick = { onVideoSelected(episode) },
                                cardWidth = cardWidth,
                                onFocusChanged = { if (it) { focusedMediaForBackdrop = episode; onVideoFocused(episode) } },
                                onOpenDetails = { detailMediaItem = episode; showDetailsDialog = true }
                            )
                        }
                    }
                }
            } else when (selectedCategoryTab) {
                com.vaydelux.bousygram.domain.model.MediaCategoryTab.ALL -> {
                    // When a topic is selected, group media into posters by series using TMDB metadata
                    if (selectedTopicId != null) {
                        val topicName = availableTopics.firstOrNull { it.id == selectedTopicId }?.name ?: "Topic Series"
                        if (seriesGroups.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(
                                    text = "$topicName Series (${seriesGroups.size})",
                                    color = colors.onBackground,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                    fontWeight = FontWeight.Bold
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(end = 24.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(
                                        seriesGroups,
                                        key = { idx, s -> "topic_series_${s.title}_$idx" }
                                    ) { _, series ->
                                        ModernSeriesCard(
                                            series = series,
                                            onClick = { onSelectSeries(series.title) },
                                            cardWidth = cardWidth
                                        )
                                    }
                                }
                            }
                        }
                        val standaloneVideos = remember(featuredVideos, seriesGroups) {
                            featuredVideos.filter { v ->
                                seriesGroups.none { g -> g.episodes.any { ep -> ep.chatId == v.chatId && ep.messageId == v.messageId } }
                            }
                        }
                        if (standaloneVideos.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = if (seriesGroups.isNotEmpty()) "Other Videos (${standaloneVideos.size})" else "$topicName Videos (${standaloneVideos.size})",
                                    color = colors.onBackground,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                    fontWeight = FontWeight.Bold
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(end = 24.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(
                                        standaloneVideos,
                                        key = { index, item -> "topic_standalone_${item.chatId}_${item.messageId}_$index" }
                                    ) { index, item ->
                                        ModernVideoCard(
                                            media = item,
                                            index = index,
                                            isFavoriteSection = false,
                                            onClick = { onVideoSelected(item) },
                                            cardWidth = cardWidth,
                                            onFocusChanged = {
                                                if (it) {
                                                    focusedMediaForBackdrop = item
                                                    focusedMediaForHero = item
                                                    onVideoFocused(item)
                                                }
                                            },
                                            onOpenDetails = { detailMediaItem = item; showDetailsDialog = true }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // # all-channels: shows everything
                        // If series detected across all channels, show series posters row
                        if (seriesGroups.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(
                                    text = "TV Shows & Series (${seriesGroups.size})",
                                    color = colors.onBackground,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                    fontWeight = FontWeight.Bold
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(end = 24.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(
                                        seriesGroups,
                                        key = { idx, s -> "all_series_${s.title}_$idx" }
                                    ) { _, series ->
                                        ModernSeriesCard(
                                            series = series,
                                            onClick = { onSelectSeries(series.title) },
                                            cardWidth = cardWidth
                                        )
                                    }
                                }
                            }
                        }

                        // New in your chats row
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "New in your chats",
                                color = colors.onBackground,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                fontWeight = FontWeight.Bold
                            )
                            LazyRow(
                                state = newInChatsRowState,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(end = 24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(
                                    featuredVideos,
                                    key = { index, item -> "recent_${item.chatId}_${item.messageId}_$index" }
                                ) { index, item ->
                                    ModernVideoCard(
                                        media = item,
                                        index = index,
                                        isFavoriteSection = false,
                                        onClick = { onVideoSelected(item) },
                                        cardWidth = cardWidth,
                                        onFocusChanged = {
                                            if (it) {
                                                focusedMediaForBackdrop = item
                                                focusedMediaForHero = item
                                                onVideoFocused(item)
                                            }
                                        },
                                        onOpenDetails = { detailMediaItem = item; showDetailsDialog = true }
                                    )
                                }
                            }
                        }

                        // Chat source rows
                        val videosByChat = remember(featuredVideos) { featuredVideos.groupBy { it.chatId } }
                        val displayedChats = remember(sourceChats, videosByChat) {
                            val withVideos = sourceChats.filter { (videosByChat[it.chatId]?.size ?: 0) > 0 }
                            if (withVideos.isNotEmpty()) withVideos else sourceChats.take(5)
                        }
                        if (displayedChats.isNotEmpty()) {
                            displayedChats.forEach { chat ->
                                val chatVideos = videosByChat[chat.chatId] ?: if (chat.chatId == selectedChatId) featuredVideos else emptyList()
                                if (chatVideos.isNotEmpty()) {
                                    val rowState = chatRowStates.getOrPut(chat.chatId) { LazyListState() }
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = chat.title,
                                                color = colors.onBackground,
                                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = colors.surfaceVariant
                                            ) {
                                                Text(
                                                    text = "${chatVideos.size}",
                                                    color = colors.secondary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        LazyRow(
                                            state = rowState,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            contentPadding = PaddingValues(end = 24.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            itemsIndexed(
                                                chatVideos,
                                                key = { index, item -> "chat_${chat.chatId}_${item.messageId}_$index" }
                                            ) { index, item ->
                                                ModernVideoCard(
                                                    media = item,
                                                    index = index,
                                                    isFavoriteSection = false,
                                                    onClick = { onVideoSelected(item) },
                                                    cardWidth = cardWidth,
                                                    onFocusChanged = {
                                                        if (it) {
                                                            focusedMediaForBackdrop = item
                                                            focusedMediaForHero = item
                                                            onVideoFocused(item)
                                                        }
                                                    },
                                                    onOpenDetails = { detailMediaItem = item; showDetailsDialog = true }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (channelLabel.isNotBlank() && featuredVideos.isNotEmpty()) {
                            val rowState = chatRowStates.getOrPut(-1L) { LazyListState() }
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = channelLabel,
                                    color = colors.onBackground,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                    fontWeight = FontWeight.Bold
                                )
                                LazyRow(
                                    state = rowState,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(end = 24.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(
                                        featuredVideos,
                                        key = { index, item -> "ch_${item.chatId}_${item.messageId}_$index" }
                                    ) { index, item ->
                                        ModernVideoCard(
                                            media = item,
                                            index = index,
                                            isFavoriteSection = false,
                                            onClick = { onVideoSelected(item) },
                                            cardWidth = cardWidth,
                                            onFocusChanged = {
                                                if (it) {
                                                    focusedMediaForBackdrop = item
                                                    focusedMediaForHero = item
                                                    onVideoFocused(item)
                                                }
                                            },
                                            onOpenDetails = { detailMediaItem = item; showDetailsDialog = true }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                com.vaydelux.bousygram.domain.model.MediaCategoryTab.SERIES -> {
                    if (seriesGroups.isEmpty()) {
                        EmptyStateView(
                            title = "No TV Series Detected",
                            message = "Videos with episode numbering (e.g. S01E01 or 1x01) will automatically appear grouped here.",
                            actionText = "Show All",
                            onAction = { onSelectCategoryTab(com.vaydelux.bousygram.domain.model.MediaCategoryTab.ALL) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
                        )
                    } else {
                        // Series Cards Grid / Row
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "TV Shows & Series (${seriesGroups.size})",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                fontWeight = FontWeight.Bold
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(end = 24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(
                                    seriesGroups,
                                    key = { idx, s -> "series_${s.title}_$idx" }
                                ) { _, series ->
                                    ModernSeriesCard(
                                        series = series,
                                        onClick = { onSelectSeries(series.title) },
                                        cardWidth = cardWidth
                                    )
                                }
                            }
                        }
                    }
                }

                com.vaydelux.bousygram.domain.model.MediaCategoryTab.AUDIO -> {
                    val audioItems = remember(featuredVideos) {
                        featuredVideos.filter { it.type == com.vaydelux.bousygram.domain.model.MediaType.Audio || it.mimeType?.startsWith("audio/") == true }
                    }
                    if (audioItems.isEmpty()) {
                        EmptyStateView(
                            title = "No Audio or Music Detected",
                            message = "Audio tracks, podcasts, and music files will automatically appear grouped here.",
                            actionText = "Show All",
                            onAction = { onSelectCategoryTab(com.vaydelux.bousygram.domain.model.MediaCategoryTab.ALL) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "Audio & Music (${audioItems.size})",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                fontWeight = FontWeight.Bold
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(end = 24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(
                                    audioItems,
                                    key = { idx, item -> "audio_${item.chatId}_${item.messageId}_$idx" }
                                ) { index, item ->
                                    ModernVideoCard(
                                        media = item,
                                        index = index,
                                        isFavoriteSection = false,
                                        onClick = { onVideoSelected(item) },
                                        cardWidth = cardWidth,
                                        onFocusChanged = { if (it) { focusedMediaForBackdrop = item; onVideoFocused(item) } },
                                        onOpenDetails = { detailMediaItem = item; showDetailsDialog = true }
                                    )
                                }
                            }
                        }
                    }
                }

                com.vaydelux.bousygram.domain.model.MediaCategoryTab.DOCUMENTS -> {
                    val docItems = remember(featuredVideos) {
                        featuredVideos.filter { it.type == com.vaydelux.bousygram.domain.model.MediaType.Document }
                    }
                    if (docItems.isEmpty()) {
                        EmptyStateView(
                            title = "No Documents Detected",
                            message = "Documents, archives (ZIP/RAR), APKs, and ebooks will appear here.",
                            actionText = "Show All",
                            onAction = { onSelectCategoryTab(com.vaydelux.bousygram.domain.model.MediaCategoryTab.ALL) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "Documents & Files (${docItems.size})",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                fontWeight = FontWeight.Bold
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(end = 24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(
                                    docItems,
                                    key = { idx, item -> "doc_${item.chatId}_${item.messageId}_$idx" }
                                ) { index, item ->
                                    ModernVideoCard(
                                        media = item,
                                        index = index,
                                        isFavoriteSection = false,
                                        onClick = { onVideoSelected(item) },
                                        cardWidth = cardWidth,
                                        onFocusChanged = { if (it) { focusedMediaForBackdrop = item; onVideoFocused(item) } },
                                        onOpenDetails = { detailMediaItem = item; showDetailsDialog = true }
                                    )
                                }
                            }
                        }
                    }
                }

                com.vaydelux.bousygram.domain.model.MediaCategoryTab.TOPICS -> {
                    if (topicGroups.isEmpty()) {
                        EmptyStateView(
                            title = "No Topics Found",
                            message = "This chat does not contain forum topics or message threads.",
                            actionText = "Show All",
                            onAction = { onSelectCategoryTab(com.vaydelux.bousygram.domain.model.MediaCategoryTab.ALL) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            topicGroups.forEach { (topicTitle, topicVideos) ->
                                // Telegram topics are the presentation hierarchy. Do not infer TV series
                                // from filenames inside a topic; doing so can mix otherwise unrelated media.
                                val topicSeries = emptyList<com.vaydelux.bousygram.domain.model.SeriesGroup>()
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = topicTitle,
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = if (topicSeries.isNotEmpty()) "${topicSeries.size} series • ${topicVideos.size} videos" else "${topicVideos.size} videos",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (topicSeries.isNotEmpty()) {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            contentPadding = PaddingValues(end = 24.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            itemsIndexed(
                                                topicSeries,
                                                key = { idx, s -> "topseries_${topicTitle}_${s.title}_$idx" }
                                            ) { _, series ->
                                                ModernSeriesCard(
                                                    series = series,
                                                    onClick = { onSelectSeries(series.title) },
                                                    cardWidth = cardWidth
                                                )
                                            }
                                        }
                                    } else {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            contentPadding = PaddingValues(end = 24.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            itemsIndexed(
                                                topicVideos,
                                                key = { idx, itm -> "topic_${topicTitle}_${itm.chatId}_${itm.messageId}_$idx" }
                                            ) { index, item ->
                                                ModernVideoCard(
                                                    media = item,
                                                    index = index,
                                                    isFavoriteSection = false,
                                                    onClick = { onVideoSelected(item) },
                                                    cardWidth = cardWidth,
                                                    onFocusChanged = { if (it) { focusedMediaForBackdrop = item; onVideoFocused(item) } },
                                                    onOpenDetails = { detailMediaItem = item; showDetailsDialog = true }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Details Dialog popup
        if (showDetailsDialog) {
            val dialogMedia = detailMediaItem ?: continueItem
            val relatedEpisodes = remember(dialogMedia, seriesGroups) {
                if (dialogMedia?.seriesTitle != null) {
                    seriesGroups.firstOrNull { it.title == dialogMedia.seriesTitle }?.episodes ?: emptyList()
                } else {
                    emptyList()
                }
            }
            val isDialogFavorite = remember(dialogMedia, favoriteKeys) {
                if (dialogMedia == null) false
                else favoriteKeys.contains("${dialogMedia.chatId}:${dialogMedia.messageId}")
            }
            val isDialogOffline = remember(dialogMedia, offlineFileIds) {
                if (dialogMedia?.fileId == null) false
                else offlineFileIds.contains(dialogMedia.fileId)
            }

            MediaDetailsDialog(
                video = dialogMedia,
                channelLabel = channelLabel,
                isFavorite = isDialogFavorite,
                isOffline = isDialogOffline,
                onToggleFavorite = { dialogMedia?.let { onToggleFavorite(it) } },
                onToggleOffline = { dialogMedia?.let { onToggleOffline(it) } },
                onDismiss = {
                    showDetailsDialog = false
                    detailMediaItem = null
                },
                onPlay = {
                    showDetailsDialog = false
                    if (dialogMedia != null) onVideoSelected(dialogMedia) else onOpenLastPlayed()
                },
                onPlayFromStart = {
                    showDetailsDialog = false
                    if (dialogMedia != null) {
                        PlaybackProgressTracker.clearMessagePosition(dialogMedia.chatId, dialogMedia.messageId)
                        onVideoSelected(dialogMedia)
                    }
                },
                relatedEpisodes = relatedEpisodes,
                onSelectRelatedEpisode = { ep ->
                    detailMediaItem = ep
                },
                onOpenExternalPlayer = {
                    dialogMedia?.let { onOpenExternalPlayer(it) }
                },
                onDeleteMedia = {
                    dialogMedia?.let {
                        onDeleteMedia(it)
                        showDetailsDialog = false
                        detailMediaItem = null
                    }
                }
            )
        }
    }
}

@Composable
fun ModernHeroBanner(
    video: MediaItem?,
    lastPlayedTitle: String? = null,
    isFavorite: Boolean = false,
    hasResume: Boolean = false,
    onPlayOrResume: () -> Unit,
    onToggleMyList: () -> Unit,
    onDetails: () -> Unit = {},
    isPhone: Boolean = false,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (isPhone) 16.dp else 22.dp)
    val title = video?.tmdbTitle?.takeIf { it.isNotBlank() }
        ?: video?.seriesTitle?.takeIf { it.isNotBlank() }
        ?: video?.title?.takeIf { it.isNotBlank() }
        ?: lastPlayedTitle?.takeIf { it.isNotBlank() }
        ?: "Featured Title"

    val year = video?.releaseYear?.toString()
        ?: video?.let { if (it.date > 0) (it.date / 31536000 + 1970).toString() else null }
    val rating = video?.tmdbRating?.takeIf { it > 0f }?.let { "★ %.1f".format(it) }
    val episodeTag = if (video?.seasonNumber != null || video?.episodeNumber != null) {
        val s = video.seasonNumber ?: 1
        val e = video.episodeNumber ?: 1
        val ep = video.episodeTitle?.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
        "S$s:E$e$ep"
    } else null
    val description = video?.tmdbOverview?.takeIf { it.isNotBlank() }
        ?: "High quality streaming media from your Telegram chats."

    val backdropPath = video?.tmdbBackdropPath ?: video?.tmdbPosterPath ?: video?.thumbnailPath
    val backdropImageData = backdropPath?.let { path ->
        if (path.startsWith("http")) path else File(path)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isPhone) 190.dp else 280.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), shape)
    ) {
        if (backdropImageData != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(backdropImageData)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.45f }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            colors.background.copy(alpha = 0.95f),
                            colors.background.copy(alpha = 0.75f),
                            colors.background.copy(alpha = 0.25f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isPhone) 16.dp else 28.dp,
                    vertical = if (isPhone) 14.dp else 24.dp
                ),
            verticalArrangement = Arrangement.Center
        ) {
            val metaBadges = listOfNotNull(year, rating, episodeTag)
            if (metaBadges.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    metaBadges.forEachIndexed { idx, badge ->
                        Text(
                            text = if (idx == 0) badge else "•  $badge",
                            color = if (badge.startsWith("★")) colors.tertiary else colors.primary,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (isPhone) 11.sp else 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = title,
                color = colors.onSurface,
                style = if (isPhone) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 26.sp,
                    letterSpacing = (-0.5).sp
                ),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                color = colors.onSurfaceVariant,
                style = if (isPhone) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(if (isPhone) 10.dp else 16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModernResumeButton(
                    text = if (hasResume) "▶ Resume" else "▶ Play",
                    onClick = onPlayOrResume,
                    focusRequester = focusRequester
                )
                ModernMyListButton(
                    isFavorite = isFavorite,
                    onClick = onToggleMyList
                )
            }
        }
    }
}

@Composable
fun ModernHeroBanner(
    video: MediaItem?,
    lastPlayedTitle: String?,
    onResume: () -> Unit,
    onDetails: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) = ModernHeroBanner(
    video = video,
    lastPlayedTitle = lastPlayedTitle,
    isFavorite = false,
    hasResume = false,
    onPlayOrResume = onResume,
    onToggleMyList = {},
    onDetails = onDetails,
    isPhone = false,
    focusRequester = focusRequester,
    modifier = modifier
)

@Composable
private fun ModernMyListButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "myListScale")
    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = colors.primary),
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        shape = shape,
        color = if (focused) colors.surfaceVariant else colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (focused) colors.primary else colors.outlineVariant)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isFavorite) "✓ In List" else "+ My List",
                color = if (focused || isFavorite) colors.primary else colors.onSurface,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ModernResumeButton(
    text: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "resumeScale")
    Surface(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = colors.onPrimary),
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        shape = shape,
        color = if (focused) colors.primary.copy(alpha = 0.85f) else colors.primary
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = colors.onPrimary,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ModernDetailsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (focused) colors.surfaceVariant else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = colors.primary),
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (focused) colors.onSurface else colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun ModernVideoCard(
    media: MediaItem,
    index: Int,
    isFavoriteSection: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 220.dp,
    onFocusChanged: (Boolean) -> Unit = {},
    onOpenDetails: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardScale"
    )
    val context = LocalContext.current
    val miniThumbnail = rememberMiniThumbnail(media.chatId, media.messageId, media.miniThumbnailBytes)
    val preferredThumbnail = media.tmdbPosterPath ?: media.thumbnailPath
    val imageData = preferredThumbnail?.let { path ->
        if (path.startsWith("http")) path else File(path)
    } ?: miniThumbnail
    val imageRequest = remember(imageData) {
        ImageRequest.Builder(context)
            .data(imageData)
            .crossfade(false)
            .size(640, 360)
            .build()
    }

    val defaultGradient = remember(index, isFavoriteSection, colors) {
        val s = colors.surface
        val sv = colors.surfaceVariant
        listOf(sv, s)
    }

    val durationMs = remember(media.trustedDurationMs, media.durationSeconds) {
        media.trustedDurationMs.takeIf { it > 0L }
            ?: media.durationSeconds.takeIf { it > 0 }?.times(1000L)
            ?: 0L
    }
    val isWatched = remember(media.chatId, media.messageId, durationMs) {
        PlaybackProgressTracker.isWatched(media.chatId, media.messageId, durationMs)
    }
    val positionMs = remember(media.chatId, media.messageId, durationMs) {
        PlaybackProgressTracker.getMessagePosition(media.chatId, media.messageId, durationMs)
    }
    val progress = if (durationMs > 0L && positionMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .width(cardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                if (isWatched) alpha = 0.58f
            }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) colors.primary else colors.outlineVariant.copy(alpha = 0.5f),
                    shape = shape
                )
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(color = colors.primary),
                    onClick = onClick
                )
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChanged(it.isFocused)
                }
                .focusable(),
            shape = shape,
            color = colors.surface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(defaultGradient)),
                contentAlignment = Alignment.Center
            ) {
                if (imageData != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = media.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                    )
                }

                if (!isFavoriteSection) {
                    CenteredPlayBadge()
                } else {
                    ImagePlaceholderIcon(
                        tint = colors.onSurface.copy(alpha = 0.78f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (isWatched) {
                    GlassPill(
                        text = "✓ Watched",
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        accent = true
                    )
                } else {
                    val formatBadge = when (media.type) {
                        com.vaydelux.bousygram.domain.model.MediaType.Audio -> media.docType ?: "AUDIO"
                        com.vaydelux.bousygram.domain.model.MediaType.Document -> media.docType ?: "DOC"
                        else -> null
                    }
                    if (formatBadge != null) {
                        GlassPill(
                            text = formatBadge,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                            accent = true
                        )
                    }
                }
                if (progress in 0.05f..0.95f) {
                    GlassPill(
                        text = "Resume ${(progress * 100).toInt()}%",
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    )
                }

                if (!isWatched && progress in 0.05f..0.95f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.5.dp),
                        color = colors.primary,
                        trackColor = colors.surfaceVariant
                    )
                }

                if (onOpenDetails != null) {
                    Surface(
                        shape = CircleShape,
                        color = colors.surface.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable { onOpenDetails() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("ⓘ", color = colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = media.title.ifBlank { if (!isFavoriteSection) "Video ${index + 1}" else "Favorite ${index + 1}" },
            color = if (focused) colors.primary else colors.onSurface,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ContinueWatchingCard(
    entry: ContinueWatchingEntry,
    media: MediaItem,
    onClick: () -> Unit,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cwScale"
    )
    val context = LocalContext.current
    val miniThumbnail = rememberMiniThumbnail(entry.chatId, entry.messageId, media.miniThumbnailBytes)
    val preferredThumbnail = media.tmdbPosterPath ?: media.tmdbBackdropPath ?: entry.thumbnailPath
    val imageData = preferredThumbnail?.let { path ->
        if (path.startsWith("http")) path else File(path)
    } ?: miniThumbnail
    val imageRequest = remember(imageData) {
        ImageRequest.Builder(context)
            .data(imageData)
            .crossfade(false)
            .size(640, 360)
            .build()
    }

    val progress = entry.progress

    Column(
        modifier = modifier
            .width(cardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) colors.primary else colors.outlineVariant.copy(alpha = 0.5f),
                    shape = shape
                )
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(color = colors.primary),
                    onClick = onClick
                )
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChanged(it.isFocused)
                }
                .focusable(),
            shape = shape,
            color = colors.surface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(colors.surfaceVariant, colors.surface))),
                contentAlignment = Alignment.Center
            ) {
                if (imageData != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = entry.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.28f))
                    )
                }

                CenteredPlayBadge()

                GlassPill(
                    text = "Resume ${(progress * 100).toInt()}%",
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                )

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp),
                    color = colors.primary,
                    trackColor = colors.surfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = entry.title,
            color = if (focused) colors.primary else colors.onSurface,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MediaDetailsDialog(
    video: MediaItem?,
    channelLabel: String,
    isFavorite: Boolean,
    isOffline: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleOffline: () -> Unit,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayFromStart: () -> Unit,
    relatedEpisodes: List<MediaItem> = emptyList(),
    onSelectRelatedEpisode: (MediaItem) -> Unit = {},
    onOpenExternalPlayer: (() -> Unit)? = null,
    onDeleteMedia: (() -> Unit)? = null
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val resumeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100L)
        runCatching { resumeFocusRequester.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(24.dp)
        val context = LocalContext.current
        val miniThumbnail = video?.let { rememberMiniThumbnail(it.chatId, it.messageId, it.miniThumbnailBytes) }
        val preferredDetailsImage = video?.tmdbBackdropPath ?: video?.tmdbPosterPath ?: video?.thumbnailPath
        val imageData = preferredDetailsImage?.let { path ->
            if (path.startsWith("http")) path else File(path)
        } ?: miniThumbnail

        val imageRequest = remember(imageData) {
            ImageRequest.Builder(context)
                .data(imageData)
                .crossfade(true)
                .size(960, 540)
                .build()
        }

        val colors = MaterialTheme.colorScheme
        Surface(
            shape = shape,
            color = colors.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), shape)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 20.dp)
            ) {
                // Header Backdrop Banner with subtle gradient fade
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Brush.linearGradient(listOf(colors.surfaceVariant, colors.surface)))
                ) {
                    if (imageData != null) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = video?.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            colors.surface.copy(alpha = 0.5f),
                                            colors.surface
                                        )
                                    )
                                )
                        )
                    }

                    // Top Close Button
                    Surface(
                        shape = CircleShape,
                        color = colors.surface.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { onDismiss() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✕", color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Resolution & Season Badges on Banner
                    if (video != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.primary
                            ) {
                                Text(
                                    text = detectResolutionBadge(video),
                                    color = colors.onPrimary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (video.seasonNumber != null && video.episodeNumber != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = colors.secondary
                                ) {
                                    Text(
                                        text = "S${video.seasonNumber}:E${video.episodeNumber}",
                                        color = colors.onSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (video.releaseYear != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = colors.tertiary
                                ) {
                                    Text(
                                        text = "${video.releaseYear}",
                                        color = colors.onTertiary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (video.tmdbRating != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = colors.tertiary
                                ) {
                                    Text(
                                        text = "★ ${String.format(java.util.Locale.US, "%.1f", video.tmdbRating)}",
                                        color = colors.onTertiary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (video != null) {
                        val labels = mediaDisplayLabels(video, channelLabel)

                        Text(
                            text = labels.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = labels.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )

                        // Specs Grid
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (labels.durationLabel.isNotBlank()) {
                                SpecChip(label = "Duration", value = labels.durationLabel)
                            }
                            if (!labels.fileSizeLabel.isNullOrBlank()) {
                                SpecChip(label = "Size", value = labels.fileSizeLabel)
                            }
                            if (!video.performer.isNullOrBlank()) {
                                SpecChip(label = "Artist", value = video.performer)
                            }
                            val ext = video.docType ?: video.fileName?.substringAfterLast('.', "")?.uppercase()
                            if (!ext.isNullOrBlank()) {
                                SpecChip(label = "Format", value = ext)
                            }
                        }

                        if (!video.tmdbOverview.isNullOrBlank()) {
                            Text(
                                text = video.tmdbOverview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 4,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Primary Play / Resume Button
                            ModernResumeButton(
                                text = "▶ Resume",
                                onClick = onPlay,
                                focusRequester = resumeFocusRequester,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            )

                            // Play from start button
                            var startFocused by remember { mutableStateOf(false) }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (startFocused) colors.primary.copy(alpha = 0.25f) else colors.surfaceVariant,
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        if (startFocused) 2.dp else 1.dp,
                                        if (startFocused) colors.primary else colors.outlineVariant,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .onFocusChanged { startFocused = it.isFocused }
                                    .focusable()
                                    .clickable { onPlayFromStart() }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                    Text(
                                        text = "↺ Start",
                                        color = if (startFocused) colors.primary else colors.onSurface,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                }
                            }

                            // Favorite Toggle Button
                            var favFocused by remember { mutableStateOf(false) }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    favFocused -> (if (isFavorite) colors.error else colors.primary).copy(alpha = 0.25f)
                                    isFavorite -> colors.error.copy(alpha = 0.2f)
                                    else -> colors.surfaceVariant
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        if (favFocused) 2.dp else 1.dp,
                                        when {
                                            favFocused -> if (isFavorite) colors.error else colors.primary
                                            isFavorite -> colors.error
                                            else -> colors.outlineVariant
                                        },
                                        RoundedCornerShape(10.dp)
                                    )
                                    .onFocusChanged { favFocused = it.isFocused }
                                    .focusable()
                                    .clickable { onToggleFavorite() }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                    Text(
                                        text = if (isFavorite) "♥ Saved" else "♡ Favorite",
                                        color = if (isFavorite) colors.error else if (favFocused) colors.primary else colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    )
                                }
                            }

                            // Offline Pin Toggle Button
                            var pinFocused by remember { mutableStateOf(false) }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    pinFocused -> colors.primary.copy(alpha = 0.3f)
                                    isOffline -> colors.primary.copy(alpha = 0.2f)
                                    else -> colors.surfaceVariant
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        if (pinFocused) 2.dp else 1.dp,
                                        if (pinFocused || isOffline) colors.primary else colors.outlineVariant,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .onFocusChanged { pinFocused = it.isFocused }
                                    .focusable()
                                    .clickable { onToggleOffline() }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                    Text(
                                        text = if (isOffline) "✓ Pinned" else "📥 Pin",
                                        color = if (isOffline || pinFocused) colors.primary else colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    )
                                }
                            }

                            // External Player Button (VLC / MPV / MX Player)
                            if (onOpenExternalPlayer != null) {
                                var extFocused by remember { mutableStateOf(false) }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (extFocused) colors.secondary.copy(alpha = 0.25f) else colors.surfaceVariant,
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            if (extFocused) 2.dp else 1.dp,
                                            if (extFocused) colors.secondary else colors.outlineVariant,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .onFocusChanged { extFocused = it.isFocused }
                                        .focusable()
                                        .clickable { onOpenExternalPlayer() }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                        Text(
                                            text = "⎘ External",
                                            color = if (extFocused) colors.secondary else colors.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }

                            // Delete from Telegram Button
                            if (onDeleteMedia != null) {
                                var delFocused by remember { mutableStateOf(false) }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (delFocused) colors.error.copy(alpha = 0.3f) else colors.error.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            if (delFocused) 2.dp else 1.dp,
                                            colors.error,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .onFocusChanged { delFocused = it.isFocused }
                                        .focusable()
                                        .clickable { showDeleteConfirmDialog = true }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                        Text(
                                            text = "🗑 Delete",
                                            color = colors.error,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Related Series Episodes
                        if (relatedEpisodes.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "More from ${video.seriesTitle ?: "this show"} (${relatedEpisodes.size})",
                                color = colors.onSurface,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(
                                    relatedEpisodes,
                                    key = { idx, ep -> "rel_${ep.chatId}_${ep.messageId}_$idx" }
                                ) { idx, ep ->
                                    val isCurrent = ep.chatId == video.chatId && ep.messageId == video.messageId
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isCurrent) colors.primary.copy(alpha = 0.2f) else colors.surfaceVariant,
                                        modifier = Modifier
                                            .width(130.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onSelectRelatedEpisode(ep) }
                                            .border(
                                                1.dp,
                                                if (isCurrent) colors.primary else colors.outlineVariant,
                                                RoundedCornerShape(10.dp)
                                            )
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = ep.episodeNumber?.let { "Episode $it" } ?: "Part ${idx + 1}",
                                                color = if (isCurrent) colors.primary else colors.secondary,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = ep.title.ifBlank { "Episode ${idx + 1}" },
                                                color = colors.onSurface,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Ready to stream directly from Telegram high-speed servers.",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        ModernResumeButton(text = "Play Now", onClick = onPlay)
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        val dialogColors = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Delete from Telegram?",
                    color = dialogColors.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will permanently delete this media message from Telegram. This action cannot be undone.",
                    color = dialogColors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteMedia?.invoke()
                    }
                ) {
                    Text("Delete", color = dialogColors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    Text("Cancel", color = dialogColors.onSurfaceVariant)
                }
            },
            containerColor = dialogColors.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SpecChip(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "$label:", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Text(text = value, color = colors.onSurface, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
    }
}

private fun detectResolutionBadge(media: MediaItem): String {
    if (media.type == com.vaydelux.bousygram.domain.model.MediaType.Audio) {
        return media.docType?.takeIf { it.isNotBlank() } ?: "AUDIO"
    }
    if (media.type == com.vaydelux.bousygram.domain.model.MediaType.Document) {
        return media.docType?.takeIf { it.isNotBlank() } ?: "DOC"
    }
    val name = (media.fileName ?: "") + " " + media.title
    return when {
        media.thumbnailWidth >= 3840 || media.thumbnailHeight >= 2160 || name.contains("2160", ignoreCase = true) || name.contains("4k", ignoreCase = true) -> "4K UHD"
        media.thumbnailWidth >= 1920 || media.thumbnailHeight >= 1080 || name.contains("1080", ignoreCase = true) || name.contains("fhd", ignoreCase = true) -> "1080p FHD"
        media.thumbnailWidth >= 1280 || media.thumbnailHeight >= 720 || name.contains("720", ignoreCase = true) || name.contains("hd", ignoreCase = true) -> "720p HD"
        else -> media.docType?.takeIf { it.isNotBlank() } ?: "HD"
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
fun ModernSeriesCard(
    series: com.vaydelux.bousygram.domain.model.SeriesGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 220.dp
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "seriesCardScale"
    )
    val context = LocalContext.current
    val firstEpisode = series.episodes.firstOrNull()
    val miniThumbnail = firstEpisode?.let { rememberMiniThumbnail(it.chatId, it.messageId, it.miniThumbnailBytes) }
    val preferredSeriesImage = firstEpisode?.tmdbPosterPath ?: firstEpisode?.tmdbBackdropPath ?: firstEpisode?.thumbnailPath
    val imageData = preferredSeriesImage?.let { path ->
        if (path.startsWith("http")) path else File(path)
    } ?: miniThumbnail
    val imageRequest = remember(imageData) {
        ImageRequest.Builder(context)
            .data(imageData)
            .crossfade(false)
            .size(640, 360)
            .build()
    }

    Column(
        modifier = modifier
            .width(cardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) colors.primary else colors.outlineVariant.copy(alpha = 0.5f),
                    shape = shape
                )
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(color = colors.primary),
                    onClick = onClick
                )
                .onFocusChanged { focused = it.isFocused }
                .focusable(),
            shape = shape,
            color = colors.surface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(colors.surfaceVariant, colors.surface))),
                contentAlignment = Alignment.Center
            ) {
                if (imageData != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = series.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surface.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Text(
                        text = "${series.episodes.size} Episodes",
                        color = colors.onSurface,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = series.title,
            color = if (focused) colors.primary else colors.onSurface,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${series.totalSeasons} Season(s)",
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}
