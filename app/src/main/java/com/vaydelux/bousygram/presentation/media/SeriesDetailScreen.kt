package com.vaydelux.bousygram.presentation.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vaydelux.bousygram.core.tdlib.utils.PlaybackProgressTracker
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.SeriesGroup
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramChip
import com.vaydelux.bousygram.presentation.components.rememberMiniThumbnail
import java.io.File

/**
 * Series Detail Screen:
 * TV and Tablet:
 * +--------------------------------------------------------------+
 * | poster | Title, year, rating, description        [Play]      |
 * |        | Season: [1][2][3]                                   |
 * |        | Episodes list: thumbnail, title, duration, progress |
 * +--------------------------------------------------------------+
 * Phone:
 * - Poster header, Play button, season dropdown/chips, episode list.
 */
@Composable
fun SeriesDetailScreen(
    series: SeriesGroup,
    onPlayEpisode: (MediaItem) -> Unit,
    onBack: () -> Unit,
    isPhone: Boolean,
    privacyMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val seasons = remember(series) {
        series.episodes.mapNotNull { it.seasonNumber }.distinct().sorted()
    }
    var selectedSeason by remember(series) {
        mutableStateOf<Int?>(seasons.firstOrNull())
    }

    val displayedEpisodes = remember(series, selectedSeason) {
        if (selectedSeason == null) series.episodes
        else series.episodes.filter { it.seasonNumber == selectedSeason }
    }

    val sampleEpisode = series.episodes.firstOrNull()
    val posterPath = sampleEpisode?.tmdbPosterPath ?: sampleEpisode?.thumbnailPath
    val backdropPath = sampleEpisode?.tmdbBackdropPath ?: posterPath

    val releaseYear = sampleEpisode?.releaseYear ?: sampleEpisode?.let { (it.date / 31536000 + 1970) }
    val rating = sampleEpisode?.tmdbRating
    val overview = sampleEpisode?.tmdbOverview ?: "Watch episodes from this series in Telegram."

    val firstOrNextEpisode = remember(series) {
        series.episodes.firstOrNull { PlaybackProgressTracker.hasResume(it.chatId, it.messageId, it.fileId) }
            ?: series.episodes.firstOrNull()
    }

    if (isPhone) {
        // Phone Layout: Poster header, Play button, season chips/dropdown, episode list
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header with Backdrop & Back Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                if (!privacyMode && backdropPath != null) {
                    val model = if (backdropPath.startsWith("http")) backdropPath else File(backdropPath)
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(model).crossfade(true).build(),
                        contentDescription = series.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BousygramButton("← Back", onBack)
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = series.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (releaseYear != null) {
                            Text(
                                text = "$releaseYear",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (rating != null && rating > 0f) {
                            Text(
                                text = "★ %.1f".format(rating),
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${series.totalSeasons} Season${if (series.totalSeasons > 1) "s" else ""} • ${series.episodes.size} Episodes",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Play Button & Description
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (firstOrNextEpisode != null) {
                    BousygramButton(
                        text = "▶ Play Episode",
                        onClick = { onPlayEpisode(firstOrNextEpisode) },
                        selected = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = overview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Season Selector Chips
            if (seasons.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    items(seasons) { seasonNum ->
                        BousygramChip(
                            text = "Season $seasonNum",
                            selected = selectedSeason == seasonNum,
                            onClick = { selectedSeason = seasonNum }
                        )
                    }
                }
            }

            // Episodes List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(displayedEpisodes, key = { idx, ep -> "${ep.chatId}_${ep.messageId}_$idx" }) { idx, ep ->
                    EpisodeListRow(
                        episode = ep,
                        index = idx + 1,
                        onClick = { onPlayEpisode(ep) },
                        privacyMode = privacyMode
                    )
                }
            }
        }
    } else {
        // TV & Tablet Layout:
        // Left: Poster | Right: Title, year, rating, description, [Play], Season chips, Episodes list
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Top Navigation Back Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BousygramButton("← Back to Library", onBack)
            }

            // Top Area: Poster (left) + Metadata & Actions (right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Poster
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .width(170.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    if (!privacyMode && posterPath != null) {
                        val model = if (posterPath.startsWith("http")) posterPath else File(posterPath)
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(model).crossfade(true).build(),
                            contentDescription = series.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("▶", color = MaterialTheme.colorScheme.primary, fontSize = 36.sp)
                        }
                    }
                }

                // Metadata Column: Title, Year, Rating, Description, [Play] Button
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = series.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (releaseYear != null) {
                            Text(
                                text = "$releaseYear",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        if (rating != null && rating > 0f) {
                            Text(
                                text = "★ %.1f".format(rating),
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${series.totalSeasons} Season${if (series.totalSeasons > 1) "s" else ""} • ${series.episodes.size} Episodes",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = overview,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.weight(1f))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (firstOrNextEpisode != null) {
                            BousygramButton(
                                text = "▶ Play Episode",
                                onClick = { onPlayEpisode(firstOrNextEpisode) },
                                selected = true
                            )
                        }
                    }
                }
            }

            // Season Chips: [1] [2] [3]
            if (seasons.size > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Season:",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    seasons.forEach { seasonNum ->
                        val isSelected = selectedSeason == seasonNum
                        BousygramChip(
                            text = "Season $seasonNum",
                            selected = isSelected,
                            onClick = { selectedSeason = seasonNum }
                        )
                    }
                }
            }

            // Episodes List: thumbnail, title, duration, progress
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(displayedEpisodes, key = { idx, ep -> "${ep.chatId}_${ep.messageId}_$idx" }) { idx, ep ->
                    EpisodeListRow(
                        episode = ep,
                        index = idx + 1,
                        onClick = { onPlayEpisode(ep) },
                        privacyMode = privacyMode
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeListRow(
    episode: MediaItem,
    index: Int,
    onClick: () -> Unit,
    privacyMode: Boolean,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val context = LocalContext.current

    val progressRatio = remember(episode) {
        val duration = episode.durationSeconds * 1000L
        val saved = PlaybackProgressTracker.getMessagePosition(episode.chatId, episode.messageId)
        if (duration > 0L && saved > 0L) (saved.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    }

    val epTitle = remember(episode) {
        val epNum = episode.episodeNumber
        if (epNum != null) "E$epNum. ${episode.title}" else "$index. ${episode.title}"
    }

    val durationText = remember(episode.durationSeconds) {
        val min = episode.durationSeconds / 60
        val sec = episode.durationSeconds % 60
        if (min > 0) "${min}m ${sec}s" else "${sec}s"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (focused) 3.5.dp else 1.dp,
                color = if (focused) colors.primary else colors.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = colors.primary),
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (focused) colors.surfaceVariant else colors.surface
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Thumbnail with progress bar
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.background)
            ) {
                val thumb = episode.thumbnailPath
                if (!privacyMode && thumb != null) {
                    val model = if (thumb.startsWith("http")) thumb else File(thumb)
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(model).crossfade(true).build(),
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("▶", color = colors.primary, fontSize = 16.sp)
                    }
                }

                if (progressRatio > 0f) {
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter),
                        color = colors.primary,
                        trackColor = colors.surfaceVariant
                    )
                }
            }

            // Episode title and duration
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = epTitle,
                    color = if (focused) colors.primary else colors.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = durationText,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Play icon indicator
            Text(
                text = "▶",
                color = if (focused) colors.primary else colors.onSurfaceVariant,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
