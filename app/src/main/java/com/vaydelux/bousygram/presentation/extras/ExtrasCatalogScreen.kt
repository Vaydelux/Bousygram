package com.vaydelux.bousygram.presentation.extras

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.testTag
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
import coil.request.ImageRequest
import coil.size.Precision
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay

private data class TabState(
    val query: String = "",
    val filters: ExtrasFilters = ExtrasFilters(),
    val results: List<ExtrasItem> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    /** Number of earlier unique posters discarded to keep paging state bounded. */
    val droppedResults: Int = 0,
    val loading: Boolean = false,
    val error: String? = null
)

@Composable
fun ExtrasCatalogScreen(onExit: () -> Unit) {
    val metrics = rememberAdaptiveMetrics()
    val context = LocalContext.current
    // Some Android TV boxes advertise Leanback even when UiModeManager does not.
    // Keep all such devices on the native YouTube TV route, never the WebView.
    val supportsLeanback = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)
    }
    val isAndroidTv = metrics.isTv || supportsLeanback
    val lowMemoryDevice = remember(context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        manager?.isLowRamDevice == true || !android.os.Process.is64Bit()
    }
    val maxRetainedPosters = ExtrasPagingWindow.itemLimit(lowMemoryDevice)
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
    // Only show autocomplete while the user is deliberately editing a query.
    // Tab switches keep independent queries but dismiss the former tab's popup.
    var showSuggestions by remember(type) { mutableStateOf(false) }
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
    // A new query/filter starts at the first poster, without destroying the
    // independent scroll position of the other two tabs.
    LaunchedEffect(movies.query, movies.filters) { movieGrid.scrollToItem(0) }
    LaunchedEffect(tv.query, tv.filters) { tvGrid.scrollToItem(0) }
    LaunchedEffect(anime.query, anime.filters) { animeGrid.scrollToItem(0) }
    // Rewinding the bounded window must take the actual grid back to its start.
    // Ordinary page trimming relies on stable item keys to preserve the visible
    // poster anchor; it must not jump back to the first loaded item.
    LaunchedEffect(type, state.page, state.results.isEmpty(), state.droppedResults) {
        if (state.page == 1 && state.results.isEmpty()) activeGrid.scrollToItem(0)
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
    var seasonRetry by remember { mutableIntStateOf(0) }

    fun openFromResults(item: ExtrasItem) {
        showSuggestions = false
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
            showSuggestions && state.query.isNotBlank() && state.results.isNotEmpty() ->
                showSuggestions = false
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
                    Text("Regional watch availability data supplied by JustWatch via TMDB.")
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
        if (type != ExtrasType.ANIME && !ExtrasCatalogApi.hasTmdbCredentials) {
            // Do not even enter requestJson when the user has deferred TMDB setup.
            // Keep Anime fully independent and usable without a credential.
            update(state.copy(loading = false, error = null, hasMore = false))
            return@LaunchedEffect
        }
        if (state.query.isNotBlank()) delay(350L)
        val request = state
        try {
            val nextPage = ExtrasCatalogApi.browse(type, request.query, request.filters,
                request.page.coerceAtLeast(1))
            // HttpURLConnection may return despite a canceled coroutine; never publish stale results.
            currentCoroutineContext().ensureActive()
            val freshStart = request.page <= 1
            val bounded = ExtrasPagingWindow.merge(
                existing = if (freshStart) emptyList() else request.results,
                incoming = nextPage.items,
                maximumItems = maxRetainedPosters
            )
            update(request.copy(
                results = bounded.items,
                droppedResults = if (freshStart) 0 else
                    request.droppedResults + bounded.removedFromFront,
                page = nextPage.page,
                hasMore = nextPage.hasNext,
                loading = false,
                error = null
            ))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
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
            val loaded = ExtrasCatalogApi.details(selected)
            currentCoroutineContext().ensureActive()
            detail = loaded
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            detailError = e.message ?: "Unable to load details"
        } finally {
            if (detailItem == selected && detailRetry == retry) detailLoading = false
        }
    }
    LaunchedEffect(detail?.item?.id, selectedSeason, seasonRetry) {
        val current = detail ?: return@LaunchedEffect
        val season = selectedSeason ?: return@LaunchedEffect
        if (current.item.type != ExtrasType.TV) return@LaunchedEffect
        episodes = emptyList()
        episodeError = null
        seasonLoading = true
        try {
            val loaded = ExtrasCatalogApi.season(current.item.id, season)
            currentCoroutineContext().ensureActive()
            episodes = loaded
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
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
                    // Keep every tab reachable on smaller phones and large font scales.
                    ExtrasCatalogTabs(selectedTab, onSelect = { selectedTab = it })
                }
            }
        }
    ) { padding ->
        if (detailItem != null) {
            if (detailLoading) {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    ExtrasDetailLoadingSkeleton(metrics.isCompact)
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
                    onSeasonRetry = { seasonRetry += 1 },
                    onOpenItem = { related ->
                        detailItem?.let {
                            if (detailHistory.size >= 20) detailHistory.removeAt(0)
                            detailHistory.add(it)
                        }
                        detailItem = related
                    },
                    modifier = Modifier.padding(padding),
                    compact = metrics.isCompact,
                    isAndroidTv = isAndroidTv)
            }
        } else if (type != ExtrasType.ANIME && !ExtrasCatalogApi.hasTmdbCredentials) {
            ExtrasTmdbPendingSetup(
                modifier = Modifier.fillMaxSize().padding(padding),
                onBrowseAnime = { selectedTab = ExtrasType.ANIME.ordinal }
            )
        } else {
            Column(Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = if (metrics.isCompact) 12.dp else 26.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { text ->
                        val nextQuery = text.take(120)
                        showSuggestions = nextQuery.isNotBlank()
                        update(state.copy(query = nextQuery, page = 1, results = emptyList(),
                            loading = true, hasMore = true, error = null))
                    },
                    label = { Text("Search ${type.label} · ${type.provider}") },
                    placeholder = { Text("Title or keyword") },
                    singleLine = true,
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            TextButton(
                                modifier = Modifier.testTag("extras-search-clear"),
                                onClick = {
                                    showSuggestions = false
                                    update(state.copy(
                                        query = "", page = 1, results = emptyList(),
                                        loading = true, hasMore = true, error = null
                                    ))
                                }
                            ) { Text("Clear") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().onFocusChanged {
                        if (it.hasFocus && state.query.isNotBlank()) showSuggestions = true
                    }
                )
                if (showSuggestions && state.query.isNotBlank() && state.results.isNotEmpty()) {
                    ExtrasSearchSuggestions(
                        type = type,
                        suggestions = state.results.take(5),
                        onOpen = { chosen -> openFromResults(chosen) },
                        onDismiss = { showSuggestions = false }
                    )
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
                    if (type != ExtrasType.ANIME) {
                        item {
                            val languages = ExtrasProviderPolicy.tmdbLanguages()
                            val selected = languages.firstOrNull {
                                it.second == state.filters.tmdbOriginalLanguage
                            }?.first ?: "Any language"
                            ExtrasDropdown("Original language: $selected",
                                languages.map { it.first }) { index ->
                                update(state.copy(
                                    filters = state.filters.copy(
                                        tmdbOriginalLanguage = languages[index].second
                                    ),
                                    page = 1, results = emptyList(), loading = true,
                                    hasMore = true, error = null
                                ))
                            }
                        }
                    }
                    if (type == ExtrasType.ANIME) {
                        item {
                            val formats = ExtrasProviderPolicy.animeFormats()
                            val selected = formats.firstOrNull {
                                it.second == state.filters.animeFormat
                            }?.first ?: "Any format"
                            ExtrasDropdown("Format: $selected", formats.map { it.first }) { index ->
                                update(state.copy(
                                    filters = state.filters.copy(animeFormat = formats[index].second),
                                    page = 1, results = emptyList(), loading = true,
                                    hasMore = true, error = null
                                ))
                            }
                        }
                        item {
                            val statuses = ExtrasProviderPolicy.animeStatuses()
                            val selected = statuses.firstOrNull {
                                it.second == state.filters.animeStatus
                            }?.first ?: "Any status"
                            ExtrasDropdown("Status: $selected", statuses.map { it.first }) { index ->
                                update(state.copy(
                                    filters = state.filters.copy(animeStatus = statuses[index].second),
                                    page = 1, results = emptyList(), loading = true,
                                    hasMore = true, error = null
                                ))
                            }
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
                if (state.droppedResults > 0 && state.results.isNotEmpty()) {
                    ExtrasPagingWindowNotice(
                        retainedCount = state.results.size,
                        onRestart = {
                            showSuggestions = false
                            update(state.copy(
                                results = emptyList(), droppedResults = 0,
                                page = 1, loading = true, hasMore = true,
                                error = null
                            ))
                        }
                    )
                }
                if (state.results.isEmpty() && state.loading) {
                    ExtrasCatalogLoadingSkeleton(isAndroidTv, metrics.isCompact)
                } else if (state.results.isEmpty() && !state.loading && state.error == null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No matches on this page. Change your search or filters.")
                        TextButton(onClick = {
                            showSuggestions = false
                            update(state.copy(
                                query = "", filters = ExtrasFilters(), page = 1,
                                results = emptyList(), loading = true, hasMore = true, error = null
                            ))
                        }) { Text("Clear search and filters") }
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

/**
 * Separately testable focusable TV tabs, no API/key required to compose.
 * Test tags are intentionally stable for DPAD/instrumentation smoke checks.
 */
@Composable
internal fun ExtrasCatalogTabs(selectedTab: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ExtrasType.entries.toList(), key = { it.name }) { tab ->
            FilterChip(
                selected = selectedTab == tab.ordinal,
                onClick = { onSelect(tab.ordinal) },
                label = { Text(tab.label) },
                modifier = Modifier.heightIn(min = 44.dp)
                    .testTag("extras-tab-${tab.name.lowercase()}")
            )
        }
    }
}

/**
 * A visible escape hatch when earlier pages leave the bounded results window.
 * This surface is independent of API credentials and is remote-accessible.
 */
@Composable
internal fun ExtrasPagingWindowNotice(retainedCount: Int, onRestart: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("extras-paging-window-notice"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Showing the latest $retainedCount titles. Earlier pages were released to conserve memory.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            modifier = Modifier.testTag("extras-start-over"),
            onClick = onRestart
        ) { Text("From start") }
    }
}

/**
 * Autocomplete is only visible while actively searching. D-pad focus and close
 * actions are exposed for offline Compose instrumentation without API access.
 */
@Composable
internal fun ExtrasSearchSuggestions(
    type: ExtrasType,
    suggestions: List<ExtrasItem>,
    onOpen: (ExtrasItem) -> Unit,
    onDismiss: () -> Unit
) {
    // Defense in depth: never offer a TMDB item in AniList autocomplete
    // (or vice versa), even if a stale upstream result is accidentally passed.
    val visible = suggestions.filter { it.type == type }.take(5)
    if (visible.isEmpty()) return
    Surface(
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 210.dp)
            .testTag("extras-search-suggestions")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Suggestions · ${type.provider}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    modifier = Modifier.testTag("extras-suggestions-dismiss"),
                    onClick = onDismiss
                ) { Text("Dismiss") }
            }
            LazyColumn(modifier = Modifier.heightIn(max = 154.dp)) {
                items(visible, key = { "${it.type}:${it.id}" }) { item ->
                    var focused by remember(item.type, item.id) { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .testTag("extras-suggestion-${item.type.name.lowercase()}-${item.id}")
                            .onFocusChanged { focused = it.isFocused }
                            .then(
                                if (focused) Modifier.border(
                                    2.dp, MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(8.dp)
                                ) else Modifier
                            )
                            .clickable { onOpen(item) }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExtrasPosterArtwork(
                            url = item.poster,
                            contentDescription = null,
                            modifier = Modifier.size(width = 38.dp, height = 57.dp),
                            detail = false,
                            suggestion = true
                        )
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
}

/**
 * Displayed while TMDB credentials are intentionally deferred at build time.
 * Does not expose secrets or imply a network failure. This remains D-pad
 * navigable and makes the independent AniList catalog one action away.
 */
@Composable
internal fun ExtrasTmdbPendingSetup(
    modifier: Modifier = Modifier,
    onBrowseAnime: () -> Unit
) {
    Box(modifier.testTag("extras-tmdb-pending"), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "Movies and TV Shows are not configured yet",
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                "TMDB access will be enabled in a later build. Anime search is available separately through AniList.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                modifier = Modifier.testTag("extras-go-anime"),
                onClick = onBrowseAnime
            ) { Text("Browse Anime") }
        }
    }
}

/** Explicitly capped Coil decoder requests instead of full-size source bitmaps. */
@Composable
private fun ExtrasPosterArtwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier,
    detail: Boolean,
    suggestion: Boolean = false
) {
    val context = LocalContext.current
    val lowMemory = remember(context) {
        val memory = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        memory?.isLowRamDevice == true || !android.os.Process.is64Bit()
    }
    val target = ExtrasImagePolicy.poster(lowMemory, detail, suggestion)
    val imageRequest = remember(context, url, target) {
        ImageRequest.Builder(context)
            .data(url)
            .size(target.width, target.height)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}

@Composable
internal fun ExtrasPosterCard(
    item: ExtrasItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Card(onClick = onClick,
        modifier = modifier.fillMaxWidth()
            .testTag("extras-poster-${item.type.name.lowercase()}-${item.id}")
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        ExtrasPosterArtwork(
            url = item.poster,
            contentDescription = item.title,
            modifier = Modifier.fillMaxWidth().height(205.dp),
            detail = false
        )
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
    onSeasonRetry: () -> Unit,
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
                    ExtrasPosterArtwork(
                        url = info.item.poster, contentDescription = info.item.title,
                        modifier = Modifier.width(155.dp).height(225.dp), detail = true
                    )
                    DetailText(info)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    ExtrasPosterArtwork(
                        url = info.item.poster, contentDescription = info.item.title,
                        modifier = Modifier.width(185.dp).height(270.dp), detail = true
                    )
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
        if (info.item.type != ExtrasType.ANIME) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Where to watch", style = MaterialTheme.typography.titleMedium)
                    if (info.watchCountries.isEmpty()) {
                        Text("No regional streaming availability is listed for this title.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val regions = info.watchCountries
                        var selectedRegion by remember(info.item.id, info.item.type) {
                            mutableStateOf(regions.first().region)
                        }
                        val region = regions.firstOrNull { it.region == selectedRegion }
                            ?: regions.first()
                        ExtrasDropdown("Region: ${region.region}",
                            regions.map { it.region }) { index ->
                            selectedRegion = regions[index].region
                        }
                        region.offers.groupBy { it.method }.forEach { (method, offers) ->
                            Text(method, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge)
                            offers.forEach { offer ->
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AsyncImage(
                                        model = offer.logo, contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Text(offer.providerName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        region.tmdbLink?.let { url ->
                            OutlinedButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            .addCategory(Intent.CATEGORY_BROWSABLE)
                                    )
                                }.onFailure {
                                    android.widget.Toast.makeText(
                                        context, "No compatible browser installed",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }) { Text("See official availability on TMDB") }
                        }
                    }
                    Text(
                        "Availability data from JustWatch via TMDB. Providers and access vary by region; listings are not direct playback links.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
            if (selectedSeason == null) {
                item {
                    Text("Select a season to load its episodes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (seasonLoading) item { CircularProgressIndicator() }
            if (episodeError != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(episodeError, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = onSeasonRetry) { Text("Retry episodes") }
                    }
                }
            }
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
