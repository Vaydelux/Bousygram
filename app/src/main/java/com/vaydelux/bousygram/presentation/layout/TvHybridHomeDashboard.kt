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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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
import com.vaydelux.bousygram.presentation.components.ShimmerCard
import com.vaydelux.bousygram.presentation.components.rememberMiniThumbnail
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
    onDeleteMedia: (MediaItem) -> Unit = {}
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

    val backdropTarget = focusedMediaForBackdrop ?: continueItem
    val context = LocalContext.current
    val backdropMiniThumbnail = backdropTarget?.let { rememberMiniThumbnail(it.chatId, it.messageId, it.miniThumbnailBytes) }
    val backdropImageData = backdropTarget?.thumbnailPath?.let { path ->
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070D14))
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
                            Color(0xFF070D14).copy(alpha = 0.35f),
                            Color(0xFF070D14).copy(alpha = 0.85f),
                            Color(0xFF070D14)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = if (compact) 14.dp else 24.dp,
                    top = if (compact) 8.dp else 12.dp,
                    end = if (compact) 14.dp else 24.dp,
                    bottom = 32.dp
                ),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Source selector tabs if multiple Telegram sources are available
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

            // Forum Topics Channel Rail (Discord / Slack style)
            if (availableTopics.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "CHANNELS",
                            color = Color(0xFF64748B),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        )
                        Text(
                            text = "• ${availableTopics.size} topics",
                            color = Color(0xFF475569),
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
                            val shape = RoundedCornerShape(12.dp)
                            Surface(
                                shape = shape,
                                color = if (isSelected) Color(0xFF5EE5BE) else Color(0xFF131D2E),
                                modifier = Modifier
                                    .clip(shape)
                                    .clickable { onSelectTopic(null) }
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF5EE5BE) else Color(0xFF223249),
                                        shape
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "#",
                                        color = if (isSelected) Color(0xFF091418) else Color(0xFF64748B),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "all-channels",
                                        color = if (isSelected) Color(0xFF091418) else Color(0xFFCBD5E1),
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                        items(availableTopics, key = { it.id }) { topic ->
                            val isSelected = selectedTopicId == topic.id
                            val shape = RoundedCornerShape(12.dp)
                            Surface(
                                shape = shape,
                                color = if (isSelected) Color(0xFF5EE5BE) else Color(0xFF131D2E),
                                modifier = Modifier
                                    .clip(shape)
                                    .clickable { onSelectTopic(topic.id) }
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF5EE5BE) else Color(0xFF223249),
                                        shape
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    val icon = if (topic.isGeneral) "★" else "#"
                                    Text(
                                        text = icon,
                                        color = if (isSelected) Color(0xFF091418) else Color(0xFF64748B),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = topic.name.lowercase().replace(" ", "-"),
                                        color = if (isSelected) Color(0xFF091418) else Color(0xFFCBD5E1),
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    val count = topicGroups[topic.name]?.size ?: 0
                                    if (count > 0) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isSelected) Color(0xFF091418).copy(alpha = 0.2f) else Color(0xFF1E2E48),
                                            modifier = Modifier.padding(vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "$count",
                                                color = if (isSelected) Color(0xFF091418) else Color(0xFF5EE5BE),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    } else if (topic.unreadCount > 0) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) Color(0xFF091418) else Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${topic.unreadCount.coerceAtMost(99)}",
                                                    color = Color.White,
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

            // Category filter chips: "All Videos" | "Series & Shows" | "Topics"
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                com.vaydelux.bousygram.domain.model.MediaCategoryTab.entries.forEach { tab ->
                    val isSelected = tab == selectedCategoryTab
                    val shape = RoundedCornerShape(20.dp)
                    Surface(
                        shape = shape,
                        color = if (isSelected) Color(0xFF5EE5BE) else Color(0xFF131D2E),
                        modifier = Modifier
                            .clip(shape)
                            .clickable {
                                onSelectCategoryTab(tab)
                            }
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFF5EE5BE) else Color(0xFF223249),
                                shape
                            )
                    ) {
                        Text(
                            text = tab.label,
                            color = if (isSelected) Color(0xFF091418) else Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Error banner if TDLib reports issue
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            // Shimmer loading cards
            if (isLoading && featuredVideos.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Loading media…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(if (compact) 2 else 4) {
                            ShimmerCard(Modifier.width(cardWidth).height(if (compact) 110.dp else 134.dp))
                        }
                    }
                }
            } else if (featuredVideos.isEmpty() && !isLoading) {
                EmptyStateView(
                    title = "No videos in this source",
                    message = "Switch to another Telegram chat above or search across your channels.",
                    actionText = "Search",
                    onAction = onOpenSearch,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
                )
            } else when (selectedCategoryTab) {
                com.vaydelux.bousygram.domain.model.MediaCategoryTab.ALL -> {
                    // Top Hero Card: "PICK UP WHERE YOU LEFT OFF"
                    ModernHeroBanner(
                        video = continueItem,
                        lastPlayedTitle = lastPlayedTitle,
                        onResume = {
                            if (continueItem != null) onVideoSelected(continueItem) else onOpenLastPlayed()
                        },
                        onDetails = {
                            detailMediaItem = continueItem
                            showDetailsDialog = true
                        }
                    )

                    // Continue Watching Rail (Top 12 unfinished videos)
                    if (continueWatchingRail.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Continue Watching",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF1E2E48)
                                ) {
                                    Text(
                                        text = "${continueWatchingRail.size}",
                                        color = Color(0xFF5EE5BE),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            LazyRow(
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
                                        onFocusChanged = { if (it) { focusedMediaForBackdrop = matchingMedia; onVideoFocused(matchingMedia) } }
                                    )
                                }
                            }
                        }
                    }

                    // Section 1: "Recently added"
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Recently added",
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
                                featuredVideos,
                                key = { index, item -> "recent_${item.chatId}_${item.messageId}_$index" }
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

                    // Section 2: "Your favorites"
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Your favorites",
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
                                favoriteVideos,
                                key = { index, item -> "fav_${item.chatId}_${item.messageId}_$index" }
                            ) { index, item ->
                                ModernVideoCard(
                                    media = item,
                                    index = index,
                                    isFavoriteSection = true,
                                    onClick = { onVideoSelected(item) },
                                    cardWidth = cardWidth,
                                    onFocusChanged = { if (it) { focusedMediaForBackdrop = item; onVideoFocused(item) } },
                                    onOpenDetails = { detailMediaItem = item; showDetailsDialog = true }
                                )
                            }
                        }
                    }

                    // Section 3: "Channels & Sources" (Grouped by Source Overview inspired by telemedia-hub)
                    if (sourceChats.size > 1) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Channels & Sources",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF1E2E48)
                                ) {
                                    Text(
                                        text = "${sourceChats.size}",
                                        color = Color(0xFF38BDF8),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(end = 24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(sourceChats, key = { it.chatId }) { chat ->
                                    val isSelected = chat.chatId == selectedChatId
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) Color(0xFF162A45) else Color(0xFF111C2B),
                                        modifier = Modifier
                                            .width(220.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { onSelectChat(chat) }
                                            .border(
                                                1.dp,
                                                if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E2E48),
                                                RoundedCornerShape(14.dp)
                                            )
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color(0xFF223654),
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = chat.title.take(1).uppercase(),
                                                            color = Color(0xFF38BDF8),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 16.sp
                                                        )
                                                    }
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = chat.title,
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = if (chat.isForum) "${chat.topicsCount} topics" else chat.kind.label,
                                                        color = if (chat.isForum) Color(0xFF5EE5BE) else Color(0xFF64748B),
                                                        style = MaterialTheme.typography.labelSmall
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

                com.vaydelux.bousygram.domain.model.MediaCategoryTab.SERIES -> {
                    val activeSeries = seriesGroups.firstOrNull { it.title == selectedSeriesTitle }
                    if (activeSeries != null) {
                        var selectedSeasonFilter by remember(selectedSeriesTitle) { mutableStateOf<Int?>(null) }
                        val seasons = remember(activeSeries) {
                            activeSeries.episodes.mapNotNull { it.seasonNumber }.distinct().sorted()
                        }
                        val displayedEpisodes = remember(activeSeries, selectedSeasonFilter) {
                            if (selectedSeasonFilter == null) activeSeries.episodes
                            else activeSeries.episodes.filter { it.seasonNumber == selectedSeasonFilter }
                        }
                        // Series Episode View
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1E2E48),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectSeries(null) }
                                ) {
                                    Text(
                                        text = "← All Shows",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                                Text(
                                    text = activeSeries.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF5EE5BE).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "${activeSeries.totalSeasons} Season(s) • ${activeSeries.episodes.size} Episodes",
                                        color = Color(0xFF5EE5BE),
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
                                        val shape = RoundedCornerShape(12.dp)
                                        Surface(
                                            shape = shape,
                                            color = if (isSelected) Color(0xFF5EE5BE) else Color(0xFF131D2E),
                                            modifier = Modifier
                                                .clip(shape)
                                                .clickable { selectedSeasonFilter = null }
                                                .border(
                                                    1.dp,
                                                    if (isSelected) Color(0xFF5EE5BE) else Color(0xFF223249),
                                                    shape
                                                )
                                        ) {
                                            Text(
                                                text = "All Seasons (${activeSeries.episodes.size})",
                                                color = if (isSelected) Color(0xFF091418) else Color(0xFFCBD5E1),
                                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                            )
                                        }
                                    }
                                    items(seasons) { seasonNum ->
                                        val isSelected = selectedSeasonFilter == seasonNum
                                        val count = activeSeries.episodes.count { it.seasonNumber == seasonNum }
                                        val shape = RoundedCornerShape(12.dp)
                                        Surface(
                                            shape = shape,
                                            color = if (isSelected) Color(0xFF5EE5BE) else Color(0xFF131D2E),
                                            modifier = Modifier
                                                .clip(shape)
                                                .clickable { selectedSeasonFilter = seasonNum }
                                                .border(
                                                    1.dp,
                                                    if (isSelected) Color(0xFF5EE5BE) else Color(0xFF223249),
                                                    shape
                                                )
                                        ) {
                                            Text(
                                                text = "Season $seasonNum ($count)",
                                                color = if (isSelected) Color(0xFF091418) else Color(0xFFCBD5E1),
                                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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
                    } else if (seriesGroups.isEmpty()) {
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
                                            color = Color(0xFF1E2E48)
                                        ) {
                                            Text(
                                                text = "${topicVideos.size}",
                                                color = Color(0xFFCBD5E1),
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
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
    lastPlayedTitle: String?,
    onResume: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val title = lastPlayedTitle?.takeIf { it.isNotBlank() }
        ?: video?.title?.takeIf { it.isNotBlank() }
        ?: "Continue watching"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF16253C),
                        Color(0xFF131F33),
                        Color(0xFF0F1829)
                    )
                )
            )
            .border(1.dp, Color(0xFF1F304B), shape)
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "PICK UP WHERE YOU LEFT OFF",
                color = Color(0xFF5EE5BE),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp
                ),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (title == "Continue watching") "Continue watching" else title,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    letterSpacing = (-0.3).sp
                ),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Resume at your saved position",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModernResumeButton(
                    text = "Resume",
                    onClick = onResume
                )
                ModernDetailsButton(
                    text = "Details",
                    onClick = onDetails
                )
            }
        }
    }
}

@Composable
private fun ModernResumeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "resumeScale")
    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        shape = shape,
        color = if (focused) Color(0xFF75F0CC) else Color(0xFF5EE5BE)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color(0xFF091418),
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
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (focused) Color(0xFF1E2E48) else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (focused) Color.White else Color(0xFFCBD5E1),
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
    val imageData = media.thumbnailPath?.let { path ->
        if (path.startsWith("http")) path else File(path)
    } ?: miniThumbnail
    val imageRequest = remember(imageData) {
        ImageRequest.Builder(context)
            .data(imageData)
            .crossfade(false)
            .size(640, 360)
            .build()
    }

    val defaultGradient = remember(index, isFavoriteSection) {
        if (!isFavoriteSection) {
            when (index % 3) {
                0 -> listOf(Color(0xFF3B4B5E), Color(0xFF2C3949))
                1 -> listOf(Color(0xFF294144), Color(0xFF1E3134))
                else -> listOf(Color(0xFF45384E), Color(0xFF33293A))
            }
        } else {
            when (index % 3) {
                0 -> listOf(Color(0xFF3F3546), Color(0xFF2F2734))
                1 -> listOf(Color(0xFF2C3843), Color(0xFF1E2831))
                else -> listOf(Color(0xFF434534), Color(0xFF323425))
            }
        }
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
                    color = if (focused) colors.primary else Color(0xFF1F2E45),
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
            color = Color(0xFF16243A)
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
                        tint = Color.White.copy(alpha = 0.78f),
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
                        color = Color(0xFF5EE5BE),
                        trackColor = Color.Black.copy(alpha = 0.45f)
                    )
                }

                if (onOpenDetails != null) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable { onOpenDetails() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("ⓘ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = media.title.ifBlank { if (!isFavoriteSection) "Video ${index + 1}" else "Favorite ${index + 1}" },
            color = if (focused) colors.primary else Color.White,
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
    val imageData = entry.thumbnailPath?.let { path ->
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
                    color = if (focused) Color(0xFF5EE5BE) else Color(0xFF1F2E45),
                    shape = shape
                )
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(color = Color(0xFF5EE5BE)),
                    onClick = onClick
                )
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChanged(it.isFocused)
                }
                .focusable(),
            shape = shape,
            color = Color(0xFF16243A)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF243B55), Color(0xFF141E30)))),
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
                    color = Color(0xFF5EE5BE),
                    trackColor = Color.Black.copy(alpha = 0.50f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = entry.title,
            color = if (focused) Color(0xFF5EE5BE) else Color.White,
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

    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(24.dp)
        val context = LocalContext.current
        val miniThumbnail = video?.let { rememberMiniThumbnail(it.chatId, it.messageId, it.miniThumbnailBytes) }
        val imageData = video?.thumbnailPath?.let { path ->
            if (path.startsWith("http")) path else File(path)
        } ?: miniThumbnail

        val imageRequest = remember(imageData) {
            ImageRequest.Builder(context)
                .data(imageData)
                .crossfade(true)
                .size(960, 540)
                .build()
        }

        Surface(
            shape = shape,
            color = Color(0xFF0F172A),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(1.dp, Color(0xFF223249), shape)
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
                        .background(Brush.linearGradient(listOf(Color(0xFF1E2E48), Color(0xFF101B2B))))
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
                                            Color(0xFF0F172A).copy(alpha = 0.5f),
                                            Color(0xFF0F172A)
                                        )
                                    )
                                )
                        )
                    }

                    // Top Close Button
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { onDismiss() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                                color = Color(0xFF5EE5BE)
                            ) {
                                Text(
                                    text = detectResolutionBadge(video),
                                    color = Color(0xFF091418),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (video.seasonNumber != null && video.episodeNumber != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF38BDF8)
                                ) {
                                    Text(
                                        text = "S${video.seasonNumber}:E${video.episodeNumber}",
                                        color = Color(0xFF091418),
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
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = labels.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8)
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
                                modifier = Modifier.weight(1f)
                            )

                            // Play from start button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E2E48),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onPlayFromStart() }
                            ) {
                                Text(
                                    text = "↺ Start",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }

                            // Favorite Toggle Button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isFavorite) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF1E2E48),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onToggleFavorite() }
                                    .border(
                                        1.dp,
                                        if (isFavorite) Color(0xFFEF4444) else Color(0xFF2B3D59),
                                        RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Text(
                                    text = if (isFavorite) "♥ Saved" else "♡ Favorite",
                                    color = if (isFavorite) Color(0xFFF87171) else Color(0xFFCBD5E1),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }

                            // Offline Pin Toggle Button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isOffline) Color(0xFF5EE5BE).copy(alpha = 0.2f) else Color(0xFF1E2E48),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onToggleOffline() }
                                    .border(
                                        1.dp,
                                        if (isOffline) Color(0xFF5EE5BE) else Color(0xFF2B3D59),
                                        RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Text(
                                    text = if (isOffline) "✓ Pinned" else "📥 Pin",
                                    color = if (isOffline) Color(0xFF5EE5BE) else Color(0xFFCBD5E1),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }

                            // External Player Button (VLC / MPV / MX Player)
                            if (onOpenExternalPlayer != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF1E2E48),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onOpenExternalPlayer() }
                                        .border(
                                            1.dp,
                                            Color(0xFF2B3D59),
                                            RoundedCornerShape(10.dp)
                                        )
                                ) {
                                    Text(
                                        text = "⎘ External",
                                        color = Color(0xFF38BDF8),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    )
                                }
                            }

                            // Delete from Telegram Button
                            if (onDeleteMedia != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF2D151F),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { showDeleteConfirmDialog = true }
                                        .border(
                                            1.dp,
                                            Color(0xFF5A2232),
                                            RoundedCornerShape(10.dp)
                                        )
                                ) {
                                    Text(
                                        text = "🗑 Delete",
                                        color = Color(0xFFF87171),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }

                        // Related Series Episodes
                        if (relatedEpisodes.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "More from ${video.seriesTitle ?: "this show"} (${relatedEpisodes.size})",
                                color = Color.White,
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
                                        color = if (isCurrent) Color(0xFF1E3A5F) else Color(0xFF141F30),
                                        modifier = Modifier
                                            .width(130.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onSelectRelatedEpisode(ep) }
                                            .border(
                                                1.dp,
                                                if (isCurrent) Color(0xFF5EE5BE) else Color(0xFF223249),
                                                RoundedCornerShape(10.dp)
                                            )
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = ep.episodeNumber?.let { "Episode $it" } ?: "Part ${idx + 1}",
                                                color = if (isCurrent) Color(0xFF5EE5BE) else Color(0xFF38BDF8),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = ep.title.ifBlank { "Episode ${idx + 1}" },
                                                color = Color.White,
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
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        ModernResumeButton(text = "Play Now", onClick = onPlay)
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Delete from Telegram?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will permanently delete this media message from Telegram. This action cannot be undone.",
                    color = Color(0xFFCBD5E1),
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
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF131D2E),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SpecChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF182335)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "$label:", color = Color(0xFF64748B), style = MaterialTheme.typography.labelSmall)
            Text(text = value, color = Color(0xFFCBD5E1), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = Color.White,
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
    val imageData = firstEpisode?.thumbnailPath?.let { path ->
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
                    color = if (focused) colors.primary else Color(0xFF1F2E45),
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
            color = Color(0xFF16243A)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF2B3A4F), Color(0xFF182333)))),
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
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Text(
                        text = "${series.episodes.size} Episodes",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = series.title,
            color = if (focused) colors.primary else Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${series.totalSeasons} Season(s)",
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

