package com.vaydelux.bousygram.presentation.layout

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.EmptyStateView
import com.vaydelux.bousygram.presentation.components.MediaPosterCard
import com.vaydelux.bousygram.presentation.components.ShimmerCard
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
    offlineFileIds: Set<Int>,
    resumeFileIds: Set<Int>,
    safeMode: Boolean,
    modifier: Modifier = Modifier,
    onVideoFocused: (MediaItem) -> Unit = {}
) {
    var focusedItem by remember(featuredVideos) {
        mutableStateOf(featuredVideos.firstOrNull())
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0F))
    ) {
        Crossfade(targetState = focusedItem, label = "heroBackdrop") { video ->
            if (video != null) {
                // Do not request or render sensitive media artwork while privacy mode is enabled.
                val imageData = if (safeMode) null else video.thumbnailPath?.let { path ->
                    if (path.startsWith("http")) path else File(path)
                }
                Box(Modifier.fillMaxSize()) {
                    if (imageData != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageData)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().blur(30.dp)
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.18f),
                                        Color(0xFF0D0D0F).copy(alpha = 0.44f),
                                        Color(0xFF0D0D0F)
                                    )
                                )
                            )
                    )
                    if (imageData != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageData)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .fillMaxWidth(0.78f)
                                .fillMaxHeight(0.82f)
                                .padding(top = 14.dp, end = 12.dp)
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFF0D0D0F),
                                            Color(0xFF0D0D0F).copy(alpha = 0.95f),
                                            Color(0xFF0D0D0F).copy(alpha = 0.10f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Transparent, Color(0xFF0D0D0F).copy(alpha = 0.88f))
                                    )
                                )
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 32.dp, top = 24.dp, bottom = 24.dp, end = 16.dp)
        ) {
            RowHeader(
                onOpenSearch = onOpenSearch,
                onOpenLibrary = onOpenLibrary,
                onOpenFavorites = onOpenFavorites,
                onOpenSettings = onOpenSettings,
                onOpenLastPlayed = onOpenLastPlayed,
                lastPlayedTitle = lastPlayedTitle
            )
            Spacer(Modifier.height(12.dp))

            Box(Modifier.fillMaxWidth().height(330.dp)) {
                focusedItem?.let { video ->
                    val labels = mediaDisplayLabels(video, channelLabel)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(0.55f)
                            .padding(bottom = 24.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = if (safeMode) "PRIVATE MODE" else channelLabel.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = if (safeMode) "Private video" else labels.title,
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                            maxLines = 2
                        )
                        Text(
                            text = if (safeMode) "Media details hidden" else "${labels.subtitle} • ${labels.durationLabel}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BousygramButton("Play", { onVideoSelected(video) })
                            BousygramButton(
                                if (favoriteKeys.contains(mediaKey(video))) "Favorited" else "Favorite",
                                { onToggleFavorite(video) },
                                selected = favoriteKeys.contains(mediaKey(video))
                            )
                            if (video.fileId != null) {
                                BousygramButton(
                                    if (offlineFileIds.contains(video.fileId)) "Offline" else "Pin offline",
                                    { onToggleOffline(video) },
                                    selected = offlineFileIds.contains(video.fileId)
                                )
                            }
                        }
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (isLoading) Text("Loading videos...", color = Color.White.copy(alpha = 0.6f))

            if (featuredVideos.isEmpty() && !isLoading) {
                EmptyStateView(
                    title = "No videos yet",
                    message = "Telegram media from this source will appear here.",
                    actionText = "Search",
                    onAction = onOpenSearch,
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            } else if (isLoading && featuredVideos.isEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    repeat(4) { ShimmerCard(Modifier.width(260.dp).height(200.dp)) }
                }
            } else {
                val continueItems = featuredVideos
                    .filter { item ->
                        val fileId = item.fileId ?: return@filter false
                        resumeFileIds.contains(fileId) || fileId == lastPlayedFileId
                    }
                    .distinctBy { "${it.chatId}:${it.messageId}" }
                val favoriteItems = featuredVideos.filter { favoriteKeys.contains(mediaKey(it)) }
                val offlineItems = featuredVideos.filter { offlineFileIds.contains(it.fileId ?: -1) }
                if (continueItems.isNotEmpty()) {
                    MediaRail("Continue watching", continueItems, channelLabel, "Continue", safeMode, onVideoSelected) {
                        focusedItem = it
                        onVideoFocused(it)
                    }
                }
                if (favoriteItems.isNotEmpty()) {
                    MediaRail("Favorites", favoriteItems, channelLabel, "Favorite", safeMode, onVideoSelected) {
                        focusedItem = it
                        onVideoFocused(it)
                    }
                }
                if (offlineItems.isNotEmpty()) {
                    MediaRail("Offline pins", offlineItems, channelLabel, "Offline", safeMode, onVideoSelected) {
                        focusedItem = it
                        onVideoFocused(it)
                    }
                }
                MediaRail(if (safeMode) "Recently viewed" else "Recent videos in $channelLabel", featuredVideos, channelLabel, null, safeMode, onVideoSelected) {
                    focusedItem = it
                    onVideoFocused(it)
                }
            }
        }
    }
}

@Composable
private fun MediaRail(
    title: String,
    items: List<MediaItem>,
    channelLabel: String,
    badge: String?,
    safeMode: Boolean,
    onVideoSelected: (MediaItem) -> Unit,
    onFocused: (MediaItem) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.68f),
        modifier = Modifier.padding(bottom = 10.dp)
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(end = 32.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(items, key = { _, item -> "${item.chatId}:${item.messageId}:$title" }) { _, video ->
            MediaPosterCard(
                media = video,
                subtitle = channelLabel,
                onClick = { onVideoSelected(video) },
                badge = badge,
                privacyMode = safeMode,
                onFocusChanged = { focused ->
                    if (focused) onFocused(video)
                }
            )
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun RowHeader(
    onOpenSearch: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLastPlayed: () -> Unit,
    lastPlayedTitle: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Bousygram",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BousygramButton(
                if (lastPlayedTitle.isNullOrBlank()) "Resume" else "Resume last",
                onOpenLastPlayed
            )
            BousygramButton("Library", onOpenLibrary)
            BousygramButton("Favorites", onOpenFavorites)
            BousygramButton("Search", onOpenSearch)
            BousygramButton("Settings", onOpenSettings)
        }
    }
}

private fun mediaKey(item: MediaItem): String = "${item.chatId}:${item.messageId}"
