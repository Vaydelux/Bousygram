package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * One episode controller across Anime and TV WebView players. The selected
 * server is provided by the caller's current ExtrasEmbedRequest, not reset
 * to its default. No automatic next-episode playback or unsafe provider URLs.
 */
@Composable
internal fun ExtrasPlayerEpisodeControls(
    request: ExtrasEmbedRequest,
    guide: ExtrasEpisodeGuide,
    onSelect: (ExtrasEmbedRequest) -> Unit
) {
    val currentSeason = request.season ?: return
    val currentEpisode = request.episode ?: return
    if (!guide.contains(currentSeason, currentEpisode)) return

    val previous = guide.adjacent(currentSeason, currentEpisode, -1)
    val next = guide.adjacent(currentSeason, currentEpisode, 1)
    var pickerOpen by remember(request.anilistId, request.tmdbId) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().testTag("extras-player-episode-navigation"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { previous?.let { (s, e) -> onSelect(request.forEpisode(s, e)) } },
            enabled = previous != null,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .testTag("extras-player-previous-episode")
        ) { Text("Previous", maxLines = 1) }
        OutlinedButton(
            onClick = { pickerOpen = true },
            modifier = Modifier.weight(1.3f).heightIn(min = 48.dp)
                .testTag("extras-player-episode-picker")
        ) {
            Text(
                if (request.anilistId > 0) "Episode $currentEpisode"
                else "S$currentSeason · E$currentEpisode",
                maxLines = 1
            )
        }
        OutlinedButton(
            onClick = { next?.let { (s, e) -> onSelect(request.forEpisode(s, e)) } },
            enabled = next != null,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .testTag("extras-player-next-episode")
        ) { Text("Next", maxLines = 1) }
    }
    if (pickerOpen) {
        ExtrasPlayerEpisodePicker(
            request = request,
            guide = guide,
            onDismiss = { pickerOpen = false },
            onSelect = { season, episode ->
                pickerOpen = false
                onSelect(request.forEpisode(season, episode))
            }
        )
    }
}

@Composable
private fun ExtrasPlayerEpisodePicker(
    request: ExtrasEmbedRequest,
    guide: ExtrasEpisodeGuide,
    onDismiss: () -> Unit,
    onSelect: (Int, Int) -> Unit
) {
    var season by remember(request.tmdbId, request.anilistId) {
        mutableIntStateOf(request.season ?: 1)
    }
    var pageStart by remember(season) {
        mutableIntStateOf(((request.episode ?: 1) - 1).coerceAtLeast(0) / 48 * 48 + 1)
    }
    val range = guide.page(season, pageStart)
    val count = guide.count(season) ?: 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(0.94f)) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.88f)
                    .testTag("extras-player-episode-dialog"),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text("Choose an episode", style = MaterialTheme.typography.titleLarge)
                        if (request.anilistId > 0) {
                            Text(
                                "AniList #${request.anilistId} · Other anime seasons/cours may have separate AniList IDs.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (guide.catalogType == ExtrasType.TV) item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(guide.seasons.filter { it.episodeCount > 0 }, key = { it.number }) { option ->
                                FilterChip(
                                    selected = season == option.number,
                                    onClick = { season = option.number },
                                    label = { Text(option.name) },
                                    modifier = Modifier.testTag("extras-player-season-${option.number}")
                                )
                            }
                        }
                    }
                    if (range != null) {
                        items(range.toList(), key = { "episode:${season}:$it" }) { number ->
                            OutlinedButton(
                                onClick = { onSelect(season, number) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag("extras-player-episode-${season}-${number}")
                            ) {
                                Text(
                                    if (guide.catalogType == ExtrasType.ANIME) "Episode $number"
                                    else "Season $season · Episode $number",
                                    maxLines = 1
                                )
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { pageStart = (pageStart - 48).coerceAtLeast(1) },
                                    enabled = pageStart > 1,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Earlier") }
                                Text(
                                    "${range.first}–${range.last} / $count",
                                    modifier = Modifier.weight(1f).padding(top = 10.dp),
                                    style = MaterialTheme.typography.labelLarge
                                )
                                TextButton(
                                    onClick = { pageStart = (range.last + 1).coerceAtMost(count) },
                                    enabled = range.last < count,
                                    modifier = Modifier.weight(1f)
                                ) { Text("More") }
                            }
                        }
                    }
                    item { TextButton(onClick = onDismiss) { Text("Close") } }
                }
            }
        }
    }
}
