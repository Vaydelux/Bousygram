package com.vaydelux.bousygram.presentation.extras

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.vaydelux.bousygram.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private data class TabState(
    val query: String = "",
    val filters: ExtrasFilters = ExtrasFilters(),
    val results: List<ExtrasItem> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null
)

@Composable
fun ExtrasCatalogScreen(onExit: () -> Unit) {
    val metrics = rememberAdaptiveMetrics()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val type = ExtrasType.entries[selectedTab.coerceIn(0, ExtrasType.entries.lastIndex)]
    var movies by remember { mutableStateOf(TabState()) }
    var tv by remember { mutableStateOf(TabState()) }
    var anime by remember { mutableStateOf(TabState()) }
    val state = when (type) {
        ExtrasType.MOVIES -> movies
        ExtrasType.TV -> tv
        ExtrasType.ANIME -> anime
    }
    fun update(new: TabState) {
        when (type) {
            ExtrasType.MOVIES -> movies = new
            ExtrasType.TV -> tv = new
            ExtrasType.ANIME -> anime = new
        }
    }
    // Keep independent scroll state across tab switches and details navigation.
    val movieGrid = rememberLazyGridState()
    val tvGrid = rememberLazyGridState()
    val animeGrid = rememberLazyGridState()
    val activeGrid = when (type) {
        ExtrasType.MOVIES -> movieGrid
        ExtrasType.TV -> tvGrid
        ExtrasType.ANIME -> animeGrid
    }
    var detailItem by remember { mutableStateOf<ExtrasItem?>(null) }
    val detailHistory = remember { mutableStateListOf<ExtrasItem>() }
    val selectedPoster = remember { mutableStateMapOf<ExtrasType, Int>() }
    val moviePosterFocus = remember { FocusRequester() }
    val tvPosterFocus = remember { FocusRequester() }
    val animePosterFocus = remember { FocusRequester() }
    val posterFocus = when (type) {
        ExtrasType.MOVIES -> moviePosterFocus
        ExtrasType.TV -> tvPosterFocus
        ExtrasType.ANIME -> animePosterFocus
    }
    var restorePosterFocus by remember { mutableIntStateOf(0) }
    var restoredPosterFocus by remember { mutableIntStateOf(0) }
    var showCredits by remember { mutableStateOf(false) }
    var detailRetry by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<ExtrasDetail?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var episodes by remember { mutableStateOf<List<ExtrasEpisode>>(emptyList()) }
    var episodeError by remember { mutableStateOf<String?>(null) }
    var seasonLoading by remember { mutableStateOf(false) }

    fun openFromResults(item: ExtrasItem) {
        selectedPoster[type] = item.id
        detailHistory.clear()
        detailItem = item
    }
    fun goBackFromDetails() {
        detailItem = if (detailHistory.isNotEmpty()) detailHistory.removeAt(detailHistory.lastIndex) else null
        detail = null
        selectedSeason = null
        episodes = emptyList()
        if (detailItem == null) restorePosterFocus += 1
    }

    // Restore the poster *and* grid position after leaving the details route.
    LaunchedEffect(restorePosterFocus, detailItem, selectedTab, state.results.size) {
        if (restorePosterFocus <= restoredPosterFocus || detailItem != null) return@LaunchedEffect
        val id = selectedPoster[type] ?: return@LaunchedEffect
        val index = state.results.indexOfFirst { it.id == id }
        if (index < 0) return@LaunchedEffect
        activeGrid.scrollToItem(index)
        withFrameNanos { }
        runCatching { posterFocus.requestFocus() }
        restoredPosterFocus = restorePosterFocus
    }

    BackHandler {
        when {
            showCredits -> showCredits = false
            detailItem != null -> goBackFromDetails()
            else -> onExit()
        }
    }

    if (showCredits) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCredits = false },
            title = { Text("Extras · Sources & Credits") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(
                        painter = painterResource(R.drawable.ic_tmdb_official),
                        contentDescription = "TMDB logo",
                        modifier = Modifier.width(72.dp).height(52.dp)
                    )
                    Text("Movies and TV Shows: The Movie Database (TMDB).")
                    Text("This product uses the TMDB API but is not endorsed or certified by TMDB.")
                    Text("Anime: AniList. Titles, artwork, community ratings and linked episodes come from AniList.")
                    Text("Trailers and official episode links open in external apps. Bousygram does not host or stream those videos.")
                    Text("TMDB: themoviedb.org · AniList: anilist.co",
                        style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showCredits = false }) { Text("Close") }
            }
        )
    }

    // Debounce queries, cancel obsolete network coroutines, and isolate per-tab results.
    LaunchedEffect(type, state.query, state.filters, state.page, state.loading) {
        if (!state.loading) return@LaunchedEffect
        if (state.query.isNotBlank()) delay(350L)
        val request = state
        try {
            val nextPage = ExtrasCatalogApi.browse(type, request.query, request.filters,
                request.page.coerceAtLeast(1))
            update(request.copy(
                results = if (request.page <= 1) nextPage.items else
                    (request.results + nextPage.items).distinctBy { it.type to it.id },
                page = nextPage.page,
                hasMore = nextPage.hasNext,
                loading = false,
                error = null
            ))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            update(request.copy(loading = false, error = e.message ?: "Catalog unavailable"))
        }
    }
    LaunchedEffect(type) {
        if (state.page == 0 && !state.loading) update(state.copy(page = 1, loading = true))
    }
    LaunchedEffect(detailItem, detailRetry) {
        val selected = detailItem ?: return@LaunchedEffect
        val retry = detailRetry
        detail = null
        detailError = null
        detailLoading = true
        episodes = emptyList()
        selectedSeason = null
        try {
            detail = ExtrasCatalogApi.details(selected)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            detailError = e.message ?: "Unable to load details"
        } finally {
            if (detailItem == selected && detailRetry == retry) detailLoading = false
        }
    }
    LaunchedEffect(detail?.item?.id, selectedSeason) {
        val current = detail ?: return@LaunchedEffect
        val season = selectedSeason ?: return@LaunchedEffect
        if (current.item.type != ExtrasType.TV) return@LaunchedEffect
        episodes = emptyList()
        episodeError = null
        seasonLoading = true
        try {
            episodes = ExtrasCatalogApi.season(current.item.id, season)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            episodeError = e.message ?: "Unable to load episodes"
        } finally {
            if (selectedSeason == season && detail?.item?.id == current.item.id) {
                seasonLoading = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = if (metrics.isCompact) 12.dp else 26.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        if (detailItem != null) goBackFromDetails() else onExit()
                    }) { Text("← Back") }
                    Column(Modifier.weight(1f)) {
                        Text(if (detailItem == null) "Explore Extras" else detailItem?.title.orEmpty(),
                            style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Catalog metadata · TMDB / AniList",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { showCredits = true }) { Text("Credits") }
                }
                if (detailItem == null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExtrasType.entries.forEachIndexed { index, tab ->
                            FilterChip(selected = selectedTab == index, onClick = { selectedTab = index },
                                label = { Text(tab.label) })
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (detailItem != null) {
            if (detailLoading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (detailError != null) {
                Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text(detailError.orEmpty())
                    Button(onClick = { detailRetry += 1 }) { Text("Retry") }
                }
            } else detail?.let { info ->
                ExtrasDetailContent(info, episodes, selectedSeason, seasonLoading, episodeError,
                    onSeason = { selectedSeason = it },
                    onOpenItem = { related ->
                        detailItem?.let {
                            if (detailHistory.size >= 20) detailHistory.removeAt(0)
                            detailHistory.add(it)
                        }
                        detailItem = related
                    },
                    modifier = Modifier.padding(padding),
                    compact = metrics.isCompact,
                    isAndroidTv = metrics.isTv)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = if (metrics.isCompact) 12.dp else 26.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { text ->
                        update(state.copy(query = text.take(120), page = 1, results = emptyList(),
                            loading = true, hasMore = true, error = null))
                    },
                    label = { Text("Search ${type.label} · ${type.provider}") },
                    placeholder = { Text("Title or keyword") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Autocomplete-style suggestions are explicitly sourced from the selected provider.
                if (state.query.isNotBlank() && state.results.isNotEmpty()) {
                    Surface(
                        tonalElevation = 4.dp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
                    ) {
                        LazyColumn {
                            items(state.results.take(5), key = { "${it.type}:${it.id}" }) { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { openFromResults(item) }
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    AsyncImage(model = item.poster, contentDescription = null,
                                        modifier = Modifier.size(width = 38.dp, height = 57.dp),
                                        contentScale = ContentScale.Crop)
                                    Column {
                                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${item.year} · ${item.type.provider}",
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        ExtrasDropdown("Sort: ${state.filters.sort.label}",
                            ExtrasSort.entries.map { it.label }) { index ->
                            update(state.copy(filters = state.filters.copy(sort = ExtrasSort.entries[index]),
                                page = 1, results = emptyList(), loading = true, hasMore = true))
                        }
                    }
                    item {
                        val genres = ExtrasProviderPolicy.genres(type)
                        ExtrasDropdown("Genre: ${state.filters.genre}", genres.map { it.first }) { index ->
                            update(state.copy(filters = state.filters.copy(genre = genres[index].first),
                                page = 1, results = emptyList(), loading = true, hasMore = true))
                        }
                    }
                    item {
                        val years = listOf("Any year") + (java.time.Year.now().value downTo 1980).map(Int::toString)
                        ExtrasDropdown("Year: ${state.filters.year ?: "Any"}", years) { index ->
                            update(state.copy(filters = state.filters.copy(year = years[index].toIntOrNull()),
                                page = 1, results = emptyList(), loading = true, hasMore = true))
                        }
                    }
                    item {
                        val options = listOf(0, 5, 7, 8, 9)
                        ExtrasDropdown("Rating: ${if (state.filters.minimumRating == 0) "Any" else "${state.filters.minimumRating}+"}",
                            options.map { if (it == 0) "Any rating" else "$it+" }) { index ->
                            update(state.copy(filters = state.filters.copy(minimumRating = options[index]),
                                page = 1, results = emptyList(), loading = true, hasMore = true))
                        }
                    }
                    item {
                        TextButton(onClick = {
                            update(state.copy(filters = ExtrasFilters(), page = 1, results = emptyList(),
                                loading = true, hasMore = true))
                        }) { Text("Reset filters") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (state.error != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.error, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            update(state.copy(loading = true, error = null))
                        }) { Text("Retry") }
                    }
                }
                if (state.results.isEmpty() && state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (state.results.isEmpty() && !state.loading && state.error == null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No matches on this page. Change your search or filters.")
                        if (state.hasMore && state.page > 0) {
                            TextButton(onClick = {
                                update(state.copy(page = state.page + 1, loading = true, error = null))
                            }) { Text("Check next page") }
                        }
                    }
                } else {
                    val minWidth = when {
                        metrics.isTv -> 160.dp
                        metrics.isCompact -> 132.dp
                        else -> 155.dp
                    }
                    LazyVerticalGrid(columns = GridCells.Adaptive(minWidth), state = activeGrid, modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(state.results, key = { "${it.type}:${it.id}" }) { item ->
                            ExtrasPosterCard(item,
                                modifier = if (selectedPoster[type] == item.id) {
                                    Modifier.focusRequester(posterFocus)
                                } else Modifier,
                                onClick = { openFromResults(item) })
                        }
                        if (state.hasMore && state.results.isNotEmpty()) {
                            item {
                                Button(onClick = {
                                    if (!state.loading) update(state.copy(page = state.page + 1, loading = true, error = null))
                                }, enabled = !state.loading) {
                                    Text(if (state.loading) "Loading…" else "More titles")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtrasPosterCard(
    item: ExtrasItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Card(onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        AsyncImage(model = item.poster, contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(205.dp))
        Column(Modifier.padding(9.dp)) {
            Text(item.title, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(listOf(item.year, item.rating.takeIf(String::isNotEmpty)?.let { "★ $it" })
                .filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExtrasDropdown(label: String, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(label, maxLines = 1) }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    expanded = false
                    onSelect(index)
                })
            }
        }
    }
}

@Composable
private fun ExtrasDetailContent(
    info: ExtrasDetail,
    episodes: List<ExtrasEpisode>,
    selectedSeason: Int?,
    seasonLoading: Boolean,
    episodeError: String?,
    onSeason: (Int) -> Unit,
    onOpenItem: (ExtrasItem) -> Unit,
    modifier: Modifier,
    compact: Boolean,
    isAndroidTv: Boolean
) {
    val context = LocalContext.current
    LazyColumn(modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = if (compact) 14.dp else 28.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(model = info.item.poster, contentDescription = info.item.title,
                        modifier = Modifier.width(155.dp).height(225.dp),
                        contentScale = ContentScale.Crop)
                    DetailText(info)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    AsyncImage(model = info.item.poster, contentDescription = info.item.title,
                        modifier = Modifier.width(185.dp).height(270.dp),
                        contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f)) { DetailText(info) }
                }
            }
        }
        info.youtubeId?.let { youtube ->
            item {
                ExtrasYouTubeTrailer(
                    videoId = youtube,
                    isAndroidTv = isAndroidTv,
                    compact = compact
                )
            }
        }
        if (info.legalStreamingLinks.isNotEmpty()) {
            item { Text("Officially listed episode links", style = MaterialTheme.typography.titleMedium) }
            items(info.legalStreamingLinks, key = { it.url }) { link ->
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                        runCatching { context.startActivity(intent) }
                            .onFailure { android.widget.Toast.makeText(
                                context, "No compatible browser installed", android.widget.Toast.LENGTH_SHORT
                            ).show() }
                    }
                ) { Text("${link.site}: ${link.title}", maxLines = 2) }
            }
        }
        if (info.cast.isNotEmpty()) {
            item { Text(if (info.item.type == ExtrasType.ANIME) "Characters, voice actors & staff" else "Cast",
                style = MaterialTheme.typography.titleMedium) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(info.cast) { person ->
                        Column(Modifier.width(110.dp)) {
                            AsyncImage(model = person.portrait, contentDescription = person.name,
                                modifier = Modifier.fillMaxWidth().height(130.dp),
                                contentScale = ContentScale.Crop)
                            Text(person.name, style = MaterialTheme.typography.labelMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(person.role, style = MaterialTheme.typography.labelSmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (info.seasons.isNotEmpty()) {
            item { Text("Seasons & Episodes", style = MaterialTheme.typography.titleMedium) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(info.seasons, key = { it.number }) { season ->
                        FilterChip(selected = selectedSeason == season.number,
                            onClick = { onSeason(season.number) },
                            label = { Text("${season.name} (${season.episodeCount})") })
                    }
                }
            }
            if (seasonLoading) item { CircularProgressIndicator() }
            if (episodeError != null) item { Text(episodeError, color = MaterialTheme.colorScheme.error) }
            items(episodes, key = { it.number }) { episode ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = episode.still, contentDescription = null,
                        modifier = Modifier.size(width = 110.dp, height = 70.dp),
                        contentScale = ContentScale.Crop)
                    Column {
                        Text("${episode.number}. ${episode.title}", fontWeight = FontWeight.SemiBold)
                        Text(episode.overview, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else if (info.item.type == ExtrasType.ANIME) {
            item {
                Text("AniList supplies episode counts, airing information and related series; it does not provide a complete per-episode guide for every anime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (info.reviews.isNotEmpty()) {
            item { Text("Community Reviews", style = MaterialTheme.typography.titleMedium) }
            items(info.reviews) { review ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(review.author + if (review.score.isNotBlank()) " · ${review.score}" else "",
                        fontWeight = FontWeight.SemiBold)
                    Text(review.content, style = MaterialTheme.typography.bodyMedium, maxLines = 8,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (info.related.isNotEmpty()) {
            item { Text("Related & recommended", style = MaterialTheme.typography.titleMedium) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(info.related, key = { "${it.type}:${it.id}" }) { item ->
                        Box(Modifier.width(148.dp)) { ExtrasPosterCard(item) { onOpenItem(item) } }
                    }
                }
            }
        }
        item {
            Text((if (info.item.type == ExtrasType.ANIME) "Anime metadata from AniList." else "This product uses the TMDB API but is not endorsed or certified by TMDB.") + " Titles, scores and episode availability depend on the provider. No video streaming is supplied.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailText(info: ExtrasDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(info.item.title, style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text(listOfNotNull(info.item.year.takeIf(String::isNotBlank),
            info.item.rating.takeIf(String::isNotBlank)?.let { "★ $it" })
            .joinToString(" · "), color = MaterialTheme.colorScheme.primary)
        if (info.genres.isNotEmpty()) Text(info.genres.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium)
        if (info.facts.isNotEmpty()) Text(info.facts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(info.description.ifBlank { "No description is available for this title." },
            style = MaterialTheme.typography.bodyMedium)
    }
}
