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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vaydelux.bousygram.presentation.components.BousygramCenteredStatus
import kotlinx.coroutines.CancellationException

/**
 * Search candidates; do not automatically assign IDs from similar titles.
 * A TV season and starting episode must be reviewed by the user because
 * TMDB sometimes groups multiple AniList cours into one season.
 */
@Composable
internal fun ExtrasAnimeTmdbMatchDialog(
    anime: ExtrasDetail,
    existing: ExtrasAnimeTmdbMatch?,
    onDismiss: () -> Unit,
    onConfirm: (ExtrasAnimeTmdbMatch) -> Unit,
    onRemove: () -> Unit
) {
    require(anime.item.type == ExtrasType.ANIME)
    var query by remember(anime.item.id) { mutableStateOf(anime.item.title) }
    var searchNumber by remember(anime.item.id) { mutableIntStateOf(1) }
    var candidates by remember(anime.item.id) { mutableStateOf<List<ExtrasItem>>(emptyList()) }
    var searching by remember(anime.item.id) { mutableStateOf(false) }
    var error by remember(anime.item.id) { mutableStateOf<String?>(null) }
    var selected by remember(anime.item.id) { mutableStateOf<ExtrasItem?>(null) }
    var selectedInfo by remember(anime.item.id) { mutableStateOf<ExtrasDetail?>(null) }
    var season by remember(anime.item.id) { mutableStateOf<Int?>(null) }
    var firstEpisode by remember(anime.item.id) { mutableStateOf("1") }

    LaunchedEffect(anime.item.id, searchNumber) {
        if (!ExtrasCatalogApi.hasTmdbCredentials) {
            error = "TMDB metadata is not configured in this build. AniList episode playback still works."
            return@LaunchedEffect
        }
        searching = true
        error = null
        selected = null
        selectedInfo = null
        season = null
        candidates = emptyList()
        try {
            candidates = ExtrasCatalogApi.animeTmdbCandidates(query.trim())
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            error = "TMDB matching is unavailable. Check your connection or search another title."
        } finally {
            searching = false
        }
    }

    LaunchedEffect(selected?.id) {
        val candidate = selected ?: return@LaunchedEffect
        error = null
        selectedInfo = null
        season = null
        searching = true
        try {
            val fetched = ExtrasCatalogApi.details(candidate)
            if (fetched.item.id == candidate.id && fetched.item.type == ExtrasType.TV) {
                selectedInfo = fetched
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            error = "Could not load this TMDB show's seasons. Select another candidate."
        } finally {
            searching = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(0.94f)) {
            Surface(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = maxHeight * 0.9f)
                    .testTag("extras-anime-metadata-match-dialog"),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text("Match anime episode metadata",
                            style = MaterialTheme.typography.titleLarge)
                        Text(
                            "AniList #${anime.item.id} stays the streaming ID. " +
                                "Select a TMDB show, its season and the TMDB episode corresponding to AniList Episode 1. " +
                                "This changes descriptions and artwork only.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it.take(120) },
                            label = { Text("Search TMDB TV titles") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = { searchNumber++ },
                            enabled = query.isNotBlank() && !searching,
                            modifier = Modifier.testTag("extras-anime-tmdb-search")
                        ) { Text("Find candidates") }
                    }
                    if (searching) item { CircularProgressIndicator() }
                    error?.let { message -> item {
                        Text(message, color = MaterialTheme.colorScheme.error)
                    } }
                    if (selected == null) {
                        items(candidates, key = { "match:${it.id}" }) { item ->
                            OutlinedButton(
                                onClick = { selected = item },
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("extras-anime-tmdb-candidate-${item.id}")
                            ) {
                                Text("${item.title} (${item.year}) · TMDB #${item.id}",
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        item {
                            Text(
                                "${selected?.title} — review its TMDB seasons below",
                                style = MaterialTheme.typography.titleSmall
                            )
                            TextButton(onClick = { selected = null }) {
                                Text("Choose another show")
                            }
                        }
                        selectedInfo?.let { tv ->
                            item {
                                val options = tv.seasons.filter { it.episodeCount > 0 }
                                if (options.isEmpty()) {
                                    Text("This TV entry has no episode-numbered seasons.")
                                } else {
                                    Text("TMDB season")
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(options, key = { it.number }) { option ->
                                            FilterChip(
                                                selected = season == option.number,
                                                onClick = { season = option.number; firstEpisode = "1" },
                                                label = {
                                                    Text("${option.name} · ${option.episodeCount} eps")
                                                },
                                                modifier = Modifier.testTag(
                                                    "extras-anime-tmdb-season-${option.number}")
                                            )
                                        }
                                    }
                                }
                            }
                            if (season != null) item {
                                val max = tv.seasons.firstOrNull { it.number == season }?.episodeCount ?: 0
                                OutlinedTextField(
                                    value = firstEpisode,
                                    onValueChange = { firstEpisode = it.filter(Char::isDigit).take(4) },
                                    label = { Text("TMDB episode matching AniList Episode 1 (1–$max)") },
                                    singleLine = true, modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "Do not assume season/cour numbers match. For a split cour, " +
                                        "AniList E1 may correspond to TMDB E13, not E1.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                val start = firstEpisode.toIntOrNull()
                                val valid = max > 0 && start != null && start in 1..max
                                OutlinedButton(
                                    onClick = {
                                        if (valid && start != null) {
                                            onConfirm(ExtrasAnimeTmdbMatch(
                                                anilistId = anime.item.id,
                                                tmdbTvId = tv.item.id,
                                                tmdbSeason = season!!,
                                                firstTmdbEpisode = start,
                                                tmdbTitle = tv.item.title.take(160)
                                            ))
                                        }
                                    },
                                    enabled = valid,
                                    modifier = Modifier.testTag("extras-anime-tmdb-confirm")
                                ) { Text("Use this episode guide (metadata only)") }
                            }
                        }
                    }
                    if (existing != null) item {
                        Text(
                            "Current guide: ${existing.tmdbTitle}, TMDB Season ${existing.tmdbSeason}, " +
                                "starting at Episode ${existing.firstTmdbEpisode}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onRemove) { Text("Remove metadata match") }
                    }
                    item {
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }
}
