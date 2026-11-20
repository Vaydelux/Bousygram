package com.vaydelux.bousygram.presentation.extras

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.vaydelux.bousygram.R
import com.vaydelux.bousygram.core.media.PlayerBufferConfig
import com.vaydelux.bousygram.core.memory.MediaMemoryPressure
import com.vaydelux.bousygram.core.memory.MediaMemoryTrim
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramBrandMark
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramCenteredStatus
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIconButton
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.components.BousygramInlineStatus
import com.vaydelux.bousygram.presentation.components.BousygramSectionTab
import com.vaydelux.bousygram.presentation.components.BousygramStatusTone
import com.vaydelux.bousygram.presentation.components.PremiumGridBackdrop
import com.vaydelux.bousygram.presentation.components.TvFocusSurface
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors
import com.vaydelux.bousygram.presentation.theme.LocalBousygramStyleSpec
import com.vaydelux.bousygram.presentation.theme.tabular
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

@Composable
fun ExtrasCatalogScreen(onExit: () -> Unit) {
    val metrics = rememberAdaptiveMetrics()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // Some Android TV boxes advertise Leanback even when UiModeManager does not.
    // Keep all such devices on the native YouTube TV route, never the WebView.
    val supportsLeanback = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)
    }
    val isAndroidTv = metrics.isTv || supportsLeanback
    val layout = remember(metrics.widthDp, metrics.heightDp, metrics.fontScale, isAndroidTv) {
        ExtrasLayoutPolicy.resolve(
            metrics.widthDp.value, metrics.heightDp.value,
            isAndroidTv, metrics.fontScale
        )
    }
    val compactPhoneWindow = layout.compactHeader
    val lowMemoryDevice = remember(context) {
        PlayerBufferConfig.isLowRamDevice(context) || !android.os.Process.is64Bit()
    }
    val maxRetainedPosters = ExtrasPagingWindow.itemLimit(lowMemoryDevice)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val activeTabIndex = ExtrasPagingWindow.activeTabIndex(selectedTab)
    // The memory-pressure collector is long-lived; read the latest VALID tab
    // rather than retaining a stale or out-of-range saved selection.
    val currentTabForTrim by rememberUpdatedState(activeTabIndex)
    val type = ExtrasType.entries[activeTabIndex]
    var movies by remember { mutableStateOf(TabState()) }
    var tv by remember { mutableStateOf(TabState()) }
    var anime by remember { mutableStateOf(TabState()) }

    // A 32-bit/low-RAM TV should not keep three full catalog windows alive
    // throughout a long browsing session. Only the current tab retains
    // posters; dormant tabs keep query/filter settings and re-fetch on entry.
    LaunchedEffect(activeTabIndex, lowMemoryDevice) {
        if (lowMemoryDevice) {
            if (ExtrasPagingWindow.shouldReleaseDormantPosters(activeTabIndex, ExtrasType.MOVIES)) movies = movies.releaseDormantPosters()
            if (ExtrasPagingWindow.shouldReleaseDormantPosters(activeTabIndex, ExtrasType.TV)) tv = tv.releaseDormantPosters()
            if (ExtrasPagingWindow.shouldReleaseDormantPosters(activeTabIndex, ExtrasType.ANIME)) anime = anime.releaseDormantPosters()
        }
    }
    // Respond to actual Android memory trim callbacks even on high-memory
    // hardware. Never drop the currently displayed poster list or active player.
    LaunchedEffect(Unit) {
        MediaMemoryPressure.events.collect { severity ->
            if (severity == MediaMemoryTrim.MODERATE ||
                severity == MediaMemoryTrim.CRITICAL
            ) {
                // Trim only dormant catalogs. Switching tabs must not make a
                // later OS memory callback drop the now-visible poster grid.
                val activeTab = currentTabForTrim
                if (ExtrasPagingWindow.shouldReleaseDormantPosters(activeTab, ExtrasType.MOVIES)) movies = movies.releaseDormantPosters()
                if (ExtrasPagingWindow.shouldReleaseDormantPosters(activeTab, ExtrasType.TV)) tv = tv.releaseDormantPosters()
                if (ExtrasPagingWindow.shouldReleaseDormantPosters(activeTab, ExtrasType.ANIME)) anime = anime.releaseDormantPosters()
            }
        }
    }

    val state = when (type) {
        ExtrasType.MOVIES -> movies
        ExtrasType.TV -> tv
        ExtrasType.ANIME -> anime
    }
    // Only show autocomplete while the user is deliberately editing a query.
    // Tab switches keep independent queries but dismiss the former tab's popup.
    var showSuggestions by remember(type) { mutableStateOf(false) }
    var filtersExpanded by rememberSaveable(type.name) { mutableStateOf(false) }
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
        if (state.page >= 1 && state.results.isEmpty()) activeGrid.scrollToItem(0)
    }
    var detailItem by remember { mutableStateOf<ExtrasItem?>(null) }
    var activeEmbed by remember { mutableStateOf<ExtrasEmbedRequest?>(null) }
    val detailHistory = remember { mutableStateListOf<ExtrasItem>() }
    val selectedPoster = remember { mutableStateMapOf<ExtrasType, Int>() }
    val moviePosterFocus = remember { FocusRequester() }
    val tvPosterFocus = remember { FocusRequester() }
    val animePosterFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val filterFocus = remember { FocusRequester() }
    val firstPosterFocus = remember { FocusRequester() }
    val entryTabFocus = remember { FocusRequester() }
    // Set initial TV focus once; category switches retain user-controlled focus.
    LaunchedEffect(isAndroidTv) {
        if (isAndroidTv) {
            withFrameNanos { }
            runCatching { entryTabFocus.requestFocus() }
        }
    }
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
    LaunchedEffect(restorePosterFocus, detailItem, activeTabIndex, state.results.size) {
        if (restorePosterFocus <= restoredPosterFocus || detailItem != null) return@LaunchedEffect
        // A bounded page or provider refresh can evict the selected poster.
        // Restore the original card when present; otherwise focus the first
        // retained card instead of leaving a TV remote in a dead focus state.
        val target = ExtrasPosterReturnPolicy.resolve(selectedPoster[type], state.results)
            ?: return@LaunchedEffect
        activeGrid.scrollToItem(target.index)
        withFrameNanos { }
        runCatching {
            if (target.selectedPosterVisible) posterFocus.requestFocus()
            else firstPosterFocus.requestFocus()
        }
        restoredPosterFocus = restorePosterFocus
    }

    BackHandler(enabled = activeEmbed == null) {
        when {
            showCredits -> showCredits = false
            detailItem != null -> goBackFromDetails()
            showSuggestions && state.query.isNotBlank() && state.results.isNotEmpty() ->
                showSuggestions = false
            filtersExpanded -> filtersExpanded = false
            else -> onExit()
        }
    }

    if (showCredits) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCredits = false },
            shape = RoundedCornerShape(LocalBousygramStyleSpec.current.panelCorner),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                    Text("Extras web sources open in an isolated WebView; native playback is offered only for observed and validated accessible streams. Bousygram does not host those videos.")
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

    if (activeEmbed != null) {
        ExtrasMediaForgeScreen(
            request = activeEmbed!!,
            isAndroidTv = isAndroidTv,
            onBack = { activeEmbed = null }
        )
    } else {
    // Backdrop is draw-only, so give default Text a real themed foreground.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    PremiumGridBackdrop {
    Scaffold(
        containerColor = Color.Transparent,
        // Let the premium backdrop draw edge-to-edge, but keep native controls
        // out of the Android status/navigation bars and display cutout.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(
                Modifier.fillMaxWidth().statusBarsPadding().padding(
                    horizontal = layout.horizontalGutterDp.dp,
                    vertical = if (compactPhoneWindow) 2.dp else if (metrics.isTv || metrics.heightDp < 660.dp) 6.dp else 10.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (compactPhoneWindow) {
                        BousygramIconButton(
                            icon = BousygramIcons.Back,
                            contentDescription = "Back",
                            tooltip = "Back to previous screen",
                            onClick = {
                                if (detailItem != null) goBackFromDetails() else onExit()
                            }
                        )
                    } else {
                        BousygramButton(
                            text = "Back",
                            onClick = {
                                if (detailItem != null) goBackFromDetails() else onExit()
                            },
                            leadingIcon = BousygramIcons.Back,
                            tooltip = if (detailItem != null) "Back to catalog" else "Back to mode selection"
                        )
                    }
                    // On a 320dp phone, the logo plus two actions squeezed the
                    // title to almost zero width. Keep the actions reachable.
                    if (!compactPhoneWindow && metrics.effectiveWidthDp >= 380.dp) {
                        BousygramBrandMark(size = if (metrics.isTv) 36.dp else 30.dp, showHalo = true)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            if (detailItem == null) "Explore Extras" else "Title details",
                            style = if (metrics.isTv) MaterialTheme.typography.titleLarge
                                else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!compactPhoneWindow) {
                            Text(
                                if (detailItem == null) "Movies · TV shows · Anime"
                                    else "${detailItem?.type?.label} · ${detailItem?.type?.provider}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    if (compactPhoneWindow || metrics.widthDp < 430.dp) {
                        BousygramIconButton(
                            icon = BousygramIcons.Info,
                            contentDescription = "Info",
                            tooltip = "Sources & credits",
                            onClick = { showCredits = true }
                        )
                    } else {
                        BousygramButton(
                            text = "Credits",
                            leadingIcon = BousygramIcons.Info,
                            tooltip = "View TMDB & AniList credits",
                            onClick = { showCredits = true }
                        )
                    }
                }
                if (detailItem == null) {
                    ExtrasCatalogTabs(
                        activeTabIndex,
                        onSelect = { selectedTab = it },
                        entryFocusRequester = entryTabFocus
                    )
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
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
                        .testTag("extras-detail-error"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    BousygramCenteredStatus(
                        title = "Details unavailable",
                        message = detailError.orEmpty(),
                        tone = BousygramStatusTone.ERROR
                    )
                    Button(
                        onClick = { detailRetry += 1 },
                        modifier = Modifier.extrasFocusOutline().testTag("extras-detail-retry")
                    ) { Text("Retry") }
                }
            } else detail?.let { info ->
                ExtrasDetailContent(info, episodes, selectedSeason, seasonLoading, episodeError,
                    onSeason = { selectedSeason = it },
                    onPlayMovie = { activeEmbed = ExtrasEmbedRequest.movie(info.item.id, info.item.title) },
                    onPlayAnime = { episodeNumber ->
                        activeEmbed = ExtrasEmbedRequest.anime(info.item.id, episodeNumber, info.item.title)
                    },
                    onPlayEpisode = { episodeNumber ->
                        selectedSeason?.let { season ->
                            activeEmbed = ExtrasEmbedRequest.episode(info.item.id, season, episodeNumber, info.item.title)
                        }
                    },
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
                    shortViewport = ExtrasLayoutPolicy.compactDetailViewport(
                        isTv = isAndroidTv,
                        isShortWindow = metrics.isShortWindow,
                        effectiveHeightDp = metrics.effectiveHeightDp.value
                    ),
                    isAndroidTv = isAndroidTv)
            }
        } else if (type != ExtrasType.ANIME && !ExtrasCatalogApi.hasTmdbCredentials) {
            ExtrasTmdbPendingSetup(
                modifier = Modifier.fillMaxSize().padding(padding),
                onBrowseAnime = { selectedTab = ExtrasType.ANIME.ordinal }
            )
        } else {
            Column(Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = layout.horizontalGutterDp.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { text ->
                        val nextQuery = text.take(120)
                        showSuggestions = nextQuery.isNotBlank()
                        update(state.copy(query = nextQuery, page = 1, results = emptyList(),
                            loading = true, hasMore = true, error = null))
                    },
                    shape = RoundedCornerShape(LocalBousygramStyleSpec.current.inputCorner),
                    leadingIcon = {
                        BousygramIcon(
                            icon = BousygramIcons.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 18.dp
                        )
                    },
                    label = { Text("Search ${type.label} · ${type.provider}") },
                    placeholder = { Text("Title or keyword") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        showSuggestions = false
                        keyboardController?.hide()
                    }),
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            TextButton(
                                modifier = Modifier.extrasFocusOutline().testTag("extras-search-clear"),
                                onClick = {
                                    showSuggestions = false
                                    update(state.copy(
                                        query = "", page = 1, results = emptyList(),
                                        loading = true, hasMore = true, error = null
                                    ))
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    BousygramIcon(
                                        icon = BousygramIcons.Close,
                                        contentDescription = null,
                                        size = 15.dp
                                    )
                                    Text("Clear")
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                        .focusRequester(searchFocus)
                        .focusProperties {
                            if (!showSuggestions || state.query.isBlank()) down = filterFocus
                        }
                        .testTag("extras-search-field")
                        .extrasFocusOutline()
                        .onFocusChanged {
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
                Spacer(Modifier.height(layout.searchFilterGapDp.dp))
                ExtrasFilterDisclosure(
                    expanded = filtersExpanded,
                    active = state.filters != ExtrasFilters(),
                    onToggle = { filtersExpanded = !filtersExpanded },
                    minHeightDp = layout.minControlHeightDp,
                    modifier = Modifier.focusRequester(filterFocus).focusProperties {
                        up = searchFocus
                        if (!filtersExpanded && state.results.isNotEmpty()) down = firstPosterFocus
                    }
                )
                if (filtersExpanded) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().focusGroup().testTag("extras-filter-row"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(layout.filterChipGapDp.dp)
                    ) {
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
                        TextButton(
                            onClick = {
                                update(state.copy(filters = ExtrasFilters(), page = 1, results = emptyList(),
                                    loading = true, hasMore = true))
                            },
                            modifier = Modifier.extrasFocusOutline().heightIn(
                                min = layout.minControlHeightDp.dp
                            ).testTag("extras-reset-filters")
                        ) { Text("Reset filters") }
                    }
                    }
                }
                Spacer(Modifier.height(layout.filterResultsGapDp.dp))
                if (state.error != null) {
                    BousygramInlineStatus(
                        message = state.error.orEmpty(),
                        tone = BousygramStatusTone.ERROR,
                        modifier = Modifier.testTag("extras-catalog-error"),
                        trailingAction = {
                            TextButton(onClick = {
                                update(state.copy(loading = true, error = null))
                            }) { Text("Retry") }
                        }
                    )
                }
                if (state.page > 1 && state.results.isNotEmpty()) {
                    ExtrasPreviousPageAction {
                        showSuggestions = false
                        update(state.copy(
                            page = state.page - 1, results = emptyList(),
                            droppedResults = 0, loading = true, hasMore = true,
                            error = null
                        ))
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
                    ExtrasCatalogViewport {
                        ExtrasCatalogLoadingSkeleton(
                            isTv = isAndroidTv,
                            compact = metrics.isCompact,
                            shortViewport = metrics.heightDp < 650.dp
                        )
                    }
                } else if (state.results.isEmpty() && !state.loading && state.error == null) {
                    ExtrasCatalogViewport {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BousygramCenteredStatus(
                            title = "No matches on this page",
                            message = "Change your search or filters to find more titles.",
                            modifier = Modifier.testTag("extras-catalog-empty")
                        )
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
                    }
                } else {
                    val minWidth = layout.gridCardMinWidthDp.dp
                    ExtrasCatalogViewport {
                    LazyVerticalGrid(columns = GridCells.Adaptive(minWidth), state = activeGrid,
                        modifier = Modifier.fillMaxSize().focusGroup().testTag("extras-poster-grid"),
                        horizontalArrangement = Arrangement.spacedBy(layout.gridGapDp.dp),
                        verticalArrangement = Arrangement.spacedBy(layout.gridGapDp.dp + 2.dp)) {
                        itemsIndexed(
                            state.results, key = { _, item -> "${item.type}:${item.id}" }
                        ) { index, item ->
                            val focus = Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstPosterFocus)
                                    .focusProperties {
                                        if (!filtersExpanded) up = filterFocus
                                    } else Modifier)
                                .then(if (selectedPoster[type] == item.id) {
                                    Modifier.focusRequester(posterFocus)
                                } else Modifier)
                            ExtrasPosterCard(item,
                                modifier = focus,
                                onClick = { openFromResults(item) })
                        }
                        if (state.hasMore && state.results.isNotEmpty()) {
                            // Full-span paging footer: never masquerade as a
                            // poster or trap D-pad focus in a narrow grid cell.
                            item(key = "extras-load-more", span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ExtrasMoreTitlesAction(loading = state.loading) {
                                        if (!state.loading) update(state.copy(
                                            page = state.page + 1,
                                            loading = true,
                                            error = null
                                        ))
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
    }
    }
    }
}

/**
 * Separately testable focusable TV tabs, no API/key required to compose.
 * Test tags are intentionally stable for DPAD/instrumentation smoke checks.
 */
/**
 * All Extras catalog states reserve the remaining viewport after the search,
 * filters and error banner. Keep the poster grid above the fold on short TVs
 * and landscape phones, and never let loading/empty-state panels overgrow it.
 */
@Composable
internal fun ColumnScope.ExtrasCatalogViewport(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.weight(1f).fillMaxWidth()
            .testTag("extras-results-viewport")
    ) {
        content()
    }
}

/** Optional filters stay reachable without stealing a permanent row of posters. */
@Composable
internal fun ExtrasFilterDisclosure(
    expanded: Boolean,
    active: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    minHeightDp: Int = 56
) {
    val metrics = rememberAdaptiveMetrics()
    val compactWindow = metrics.isShortWindow || metrics.effectiveWidthDp < 480.dp
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = minHeightDp.dp)
            .focusGroup()
            .testTag("extras-filter-disclosure"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BousygramButton(
            text = if (expanded) "Hide filters" else "Filters",
            onClick = onToggle,
            selected = active || expanded,
            leadingIcon = BousygramIcons.Options,
            tooltip = if (expanded) "Hide sort and filter controls" else "Sort, genre, year, and rating filters",
            modifier = modifier.heightIn(min = minHeightDp.dp)
                .testTag("extras-filter-toggle")
        )
        if (!compactWindow) {
            Text(
                if (active) "Custom filters active" else "Sort, genre, year and more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun ExtrasCatalogTabs(
    selectedTab: Int,
    entryFocusRequester: FocusRequester? = null,
    onSelect: (Int) -> Unit
) {
    val metrics = rememberAdaptiveMetrics()
    // Three catalog destinations must remain visible at once on phone widths.
    // A lazy horizontal row could hide Anime until the user guessed to scroll.
    val compact = metrics.isCompact || metrics.isShortWindow ||
        (!metrics.isTv && metrics.effectiveHeightDp < 520.dp) ||
        metrics.effectiveWidthDp < 600.dp
    val iconOnlyCompact = !metrics.isTv &&
        metrics.fontScale >= 1.35f && metrics.effectiveWidthDp < 440.dp
    Row(
        modifier = Modifier.fillMaxWidth().focusGroup().testTag("extras-catalog-tabs"),
        horizontalArrangement = Arrangement.spacedBy(BousygramUiTokens.compactSpacing)
    ) {
        ExtrasType.entries.forEach { tab ->
            val tabIcon = when (tab) {
                ExtrasType.MOVIES -> BousygramIcons.Movies
                ExtrasType.TV -> BousygramIcons.TvShows
                ExtrasType.ANIME -> BousygramIcons.Anime
            }
            val tabDetail = when (tab) {
                ExtrasType.MOVIES -> "Explore movies via TMDB"
                ExtrasType.TV -> "Explore TV series via TMDB"
                ExtrasType.ANIME -> "Explore anime via AniList"
            }
            BousygramSectionTab(
                label = tab.label,
                active = selectedTab == tab.ordinal,
                isTv = metrics.isTv,
                compact = compact,
                accessibilityLabel = tab.label,
                icon = if (iconOnlyCompact || !compact || metrics.isTv) tabIcon else null,
                iconOnly = iconOnlyCompact,
                tooltip = tab.label,
                tooltipDetail = tabDetail,
                onClick = { onSelect(tab.ordinal) },
                modifier = (if (compact) Modifier.weight(1f)
                    else Modifier.widthIn(min = 136.dp, max = 196.dp))
                    .then(
                        if (tab.ordinal == selectedTab && entryFocusRequester != null) {
                            Modifier.focusRequester(entryFocusRequester)
                        } else Modifier
                    )
                    .testTag("extras-tab-${tab.name.lowercase()}")
            )
        }
    }
}

/**
 * A visible escape hatch when earlier pages leave the bounded results window.
 * This surface is independent of API credentials and is remote-accessible.
 */
/**
 * Certain Android TV firmware and Compose owners do not forward DPAD_CENTER
 * through Material3's default keyboard click path. Consume both halves of the
 * activation key and invoke the action once, on key up. Touch clicks still use
 * the normal clickable/Button handler.
 */
private fun Modifier.activateOnTvDpad(onActivate: () -> Unit): Modifier =
    this.onPreviewKeyEvent { event ->
        if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
            if (event.type == KeyEventType.KeyUp) onActivate()
            true
        } else false
    }

/** Full-span pagination action with an explicit, non-duplicating remote activation. */
@Composable
internal fun ExtrasMoreTitlesAction(loading: Boolean, onNext: () -> Unit) {
    Button(
        onClick = onNext,
        enabled = !loading,
        modifier = Modifier.widthIn(min = 196.dp, max = 320.dp)
            .heightIn(min = 56.dp)
            .extrasFocusOutline()
            .activateOnTvDpad(onNext)
            .testTag("extras-more-titles")
    ) {
        Text(if (loading) "Loading…" else "More titles")
    }
}

/** Remote-focusable single-page rewind without retaining earlier pages. */
@Composable
internal fun ExtrasPreviousPageAction(onPrevious: () -> Unit) {
    TextButton(
        modifier = Modifier.extrasFocusOutline().testTag("extras-previous-page").activateOnTvDpad(onPrevious),
        onClick = onPrevious
    ) { Text("Previous page") }
}

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
            modifier = Modifier.extrasFocusOutline().testTag("extras-start-over").activateOnTvDpad(onRestart),
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
            .focusGroup().testTag("extras-search-suggestions")
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
                    modifier = Modifier.extrasFocusOutline().testTag("extras-suggestions-dismiss"),
                    onClick = onDismiss
                ) { Text("Dismiss") }
            }
            LazyColumn(modifier = Modifier.heightIn(max = 154.dp).focusGroup()) {
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
                            .activateOnTvDpad { onOpen(item) }
                            .clickable { onOpen(item) }
                            .heightIn(min = 64.dp)
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
            BousygramCenteredStatus(
                title = "Movies and TV Shows are not configured yet",
                message = "TMDB access will be enabled in a later build. Anime search is available separately through AniList."
            )
            Button(
                modifier = Modifier.extrasFocusOutline().testTag("extras-go-anime").activateOnTvDpad(onBrowseAnime),
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
    // Keep the full cover visible. Catalog grid cards and detail covers share a
    // 2:3 frame so no provider artwork is cropped on phones or TVs.
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .testTag("extras-poster-artwork"),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Text(
                "Artwork unavailable",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(12.dp)
            )
        } else {
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHigh),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
    }
}

@Composable
internal fun ExtrasPosterCard(
    item: ExtrasItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val metrics = rememberAdaptiveMetrics()
    val halo = LocalBousygramHaloColors.current
    TvFocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
            .testTag("extras-poster-${item.type.name.lowercase()}-${item.id}")
    ) { focused ->
        Column {
            ExtrasPosterArtwork(
                url = item.poster,
                contentDescription = item.title,
                modifier = Modifier.fillMaxWidth()
                    .aspectRatio(ExtrasImagePolicy.POSTER_ASPECT_RATIO),
                detail = false
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    item.title,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = if (metrics.isTv) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.year.isNotBlank()) {
                        Text(
                            item.year,
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (item.rating.isNotBlank()) {
                        if (item.year.isNotBlank()) {
                            Text(
                                "·",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BousygramIcon(
                            icon = BousygramIcons.Star,
                            contentDescription = "Rating",
                            tint = halo.goldAccent,
                            size = 12.dp
                        )
                        Text(
                            item.rating,
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = halo.goldAccent,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
                Text(
                    item.subtitle.ifBlank { item.type.label },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ExtrasDropdown(label: String, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.heightIn(min = 48.dp)
                .extrasFocusOutline().testTag("extras-filter-dropdown")
        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp).widthIn(max = 340.dp)
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.extrasFocusOutline().heightIn(min = 48.dp),
                    onClick = {
                    expanded = false
                    onSelect(index)
                })
            }
        }
    }
}

/** Visible to the offline UI suite so first-entry TV focus and Play routing
 * can be qualified without TMDB, AniList, Telegram, or a network connection. */
@Composable
internal fun ExtrasDetailContent(
    info: ExtrasDetail,
    episodes: List<ExtrasEpisode>,
    selectedSeason: Int?,
    seasonLoading: Boolean,
    episodeError: String?,
    onSeason: (Int) -> Unit,
    onSeasonRetry: () -> Unit,
    onPlayMovie: () -> Unit,
    onPlayAnime: (Int) -> Unit,
    onPlayEpisode: (Int) -> Unit,
    onOpenItem: (ExtrasItem) -> Unit,
    modifier: Modifier,
    compact: Boolean,
    shortViewport: Boolean,
    isAndroidTv: Boolean
) {
    val context = LocalContext.current
    val metrics = rememberAdaptiveMetrics()
    val spec = remember(metrics.widthDp, metrics.heightDp, metrics.fontScale, isAndroidTv) {
        ExtrasDetailLayoutPolicy.resolve(
            widthDp = metrics.widthDp.value,
            heightDp = metrics.heightDp.value,
            isTv = isAndroidTv,
            fontScale = metrics.fontScale
        )
    }
    val detailList = rememberLazyListState()
    val primaryFocus = remember(info.item.type, info.item.id) { FocusRequester() }
    // Chunk per viewport without nesting an unbounded LazyVerticalGrid inside
    // a vertical scroller. Compose still virtualizes whole episode rows.
    val episodeRows = remember(episodes, spec.episodeColumns) {
        episodes.chunked(spec.episodeColumns)
    }

    // Navigating to a related title on touch devices must not inherit the
    // previous detail page's bottom-of-list scroll position. TV has its own
    // primary-action focus entry below, so only reset touch/phone here.
    LaunchedEffect(info.item.type, info.item.id, isAndroidTv) {
        if (!isAndroidTv) detailList.scrollToItem(0)
    }

    // Movies/anime enter directly on Play. TV titles enter on the first
    // season, now positioned above trailers/provider metadata. We scroll
    // the lazy item into composition before requesting focus.
    LaunchedEffect(info.item.type, info.item.id, isAndroidTv, info.seasons.isNotEmpty()) {
        if (!isAndroidTv) return@LaunchedEffect
        if (info.item.type == ExtrasType.TV && info.seasons.isEmpty()) return@LaunchedEffect
        val heroOffset = if (!info.backdrop.isNullOrBlank()) 1 else 0
        val primaryIndex = heroOffset + if (info.item.type == ExtrasType.TV) 2 else 1
        detailList.scrollToItem(primaryIndex)
        withFrameNanos { }
        runCatching { primaryFocus.requestFocus() }
    }
    // The phone screenshots exposed excessive blank space from a full-size
    // backdrop plus a duplicate 2:3 poster. Keep a single hero on compact
    // devices; tablets and TVs retain the poster alongside the synopsis.
    LazyColumn(
        state = detailList,
        modifier = modifier.fillMaxSize().testTag("extras-detail-scroll"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = spec.horizontalGutterDp.dp,
            vertical = if (spec.compactChrome) 8.dp else 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(spec.sectionGapDp.dp)
    ) {
        if (!info.backdrop.isNullOrBlank()) {
            item(key = "backdrop:${info.item.type}:${info.item.id}") {
                ExtrasDetailBackdrop(
                    info = info, compact = compact, shortViewport = shortViewport,
                    heightDp = spec.backdropHeightDp
                )
            }
        }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // React to the measured detail pane, not just phone-vs-TV.
                val stacked = !spec.sideBySideProfile || maxWidth < 520.dp
                val posterWidth = spec.posterWidthDp.dp
                // Width follows the responsive pane; height is always the
                // poster's full 2:3 aspect ratio, never an arbitrary crop.
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(spec.sectionGapDp.dp)) {
                        // The backdrop already identifies the title on phones.
                        // Avoid a second oversized poster that pushes Play and
                        // other primary actions several screens down.
                        if (info.backdrop.isNullOrBlank()) {
                            ExtrasPosterArtwork(
                                url = info.item.poster, contentDescription = info.item.title,
                                modifier = Modifier.width(posterWidth)
                                    .aspectRatio(ExtrasImagePolicy.POSTER_ASPECT_RATIO), detail = true
                            )
                        }
                        ExtrasDetailSummary(info, spec)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(if (isAndroidTv) 24.dp else 18.dp)) {
                        ExtrasPosterArtwork(
                            url = info.item.poster, contentDescription = info.item.title,
                            modifier = Modifier.width(posterWidth)
                                .aspectRatio(ExtrasImagePolicy.POSTER_ASPECT_RATIO), detail = true
                        )
                        Column(Modifier.weight(1f)) { ExtrasDetailSummary(info, spec) }
                    }
                }
            }
        }
        if (info.item.type == ExtrasType.MOVIES) {
            item {
                Button(
                    onClick = onPlayMovie,
                    shape = RoundedCornerShape(LocalBousygramStyleSpec.current.inputCorner),
                    modifier = Modifier
                        .then(if (isAndroidTv) Modifier.widthIn(min = 220.dp, max = 420.dp)
                            else Modifier.fillMaxWidth())
                        .heightIn(min = spec.controlHeightDp.dp)
                        .focusRequester(primaryFocus).extrasFocusOutline().testTag("extras-play-movie")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BousygramIcon(icon = BousygramIcons.Play, contentDescription = null, size = 18.dp)
                        Text("Play · Web source")
                    }
                }
            }
        }
        if (info.item.type == ExtrasType.ANIME) {
            item {
                var episodeNumber by remember(info.item.id) { mutableIntStateOf(1) }
                val total = info.episodeCount?.takeIf { it > 0 }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Anime episodes", style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { episodeNumber = (episodeNumber - 1).coerceAtLeast(1) },
                            enabled = episodeNumber > 1,
                            shape = RoundedCornerShape(LocalBousygramStyleSpec.current.tabCorner),
                            modifier = Modifier.extrasFocusOutline().testTag("extras-anime-previous-episode")
                        ) {
                            BousygramIcon(icon = BousygramIcons.Minus, contentDescription = "Previous episode", size = 16.dp)
                        }
                        Text(
                            "Episode $episodeNumber" + (total?.let { " / $it" } ?: ""),
                            style = MaterialTheme.typography.titleMedium.tabular()
                        )
                        OutlinedButton(
                            onClick = { episodeNumber = (episodeNumber + 1).coerceAtMost(total ?: 9999) },
                            enabled = episodeNumber < (total ?: 9999),
                            shape = RoundedCornerShape(LocalBousygramStyleSpec.current.tabCorner),
                            modifier = Modifier.extrasFocusOutline().testTag("extras-anime-next-episode")
                        ) {
                            BousygramIcon(icon = BousygramIcons.Plus, contentDescription = "Next episode", size = 16.dp)
                        }
                    }
                    Button(
                        onClick = { onPlayAnime(episodeNumber) },
                        shape = RoundedCornerShape(LocalBousygramStyleSpec.current.inputCorner),
                        modifier = Modifier
                            .then(if (isAndroidTv) Modifier.widthIn(min = 220.dp, max = 420.dp)
                                else Modifier.fillMaxWidth())
                            .heightIn(min = spec.controlHeightDp.dp)
                            .focusRequester(primaryFocus).extrasFocusOutline().testTag("extras-play-anime")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BousygramIcon(icon = BousygramIcons.Play, contentDescription = null, size = 18.dp)
                            Text("Play episode · Web source")
                        }
                    }
                    Text(
                        "AniList IDs identify individual anime entries. Source episode selection varies, and season defaults to 1.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (info.seasons.isNotEmpty()) {
            item {
                Text(
                    "Seasons & Episodes",
                    modifier = Modifier.testTag("extras-seasons-heading"),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().focusGroup().testTag("extras-seasons-row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 12.dp)
                ) {
                    items(info.seasons, key = { it.number }) { season ->
                        FilterChip(selected = selectedSeason == season.number,
                            onClick = { onSeason(season.number) },
                            modifier = Modifier
                                .then(if (season == info.seasons.first()) Modifier.focusRequester(primaryFocus)
                                    else Modifier)
                                .extrasFocusOutline()
                                .heightIn(min = spec.controlHeightDp.dp)
                                .testTag("extras-season-${season.number}"),
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
            if (seasonLoading) item {
                Row(
                    modifier = Modifier.testTag("extras-episodes-loading"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Loading episodes…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (episodeError != null) {
                item {
                    BousygramInlineStatus(
                        message = episodeError,
                        tone = BousygramStatusTone.ERROR,
                        modifier = Modifier.testTag("extras-episodes-error"),
                        trailingAction = {
                            OutlinedButton(onClick = onSeasonRetry) { Text("Retry episodes") }
                        }
                    )
                }
            }
            items(
                episodeRows,
                key = { row -> "season:${selectedSeason}:episode:${row.first().number}" }
            ) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    row.forEach { episode ->
                        ExtrasEpisodeTile(
                            episode = episode,
                            canPlay = selectedSeason != null && episode.number > 0,
                            onPlay = { onPlayEpisode(episode.number) },
                            isTv = isAndroidTv,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size < spec.episodeColumns) {
                        Spacer(Modifier.weight(1f))
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
                    modifier = Modifier.fillMaxWidth().extrasFocusOutline(),
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
                LazyRow(
                    modifier = Modifier.fillMaxWidth().testTag("extras-cast-row"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 12.dp)
                ) {
                    items(info.cast.take(24)) { person ->
                        Column(
                            modifier = Modifier.width(spec.castWidthDp.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AsyncImage(
                                model = person.portrait,
                                contentDescription = person.name,
                                modifier = Modifier
                                    .size(if (isAndroidTv) 88.dp else 76.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                person.name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                person.role,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        if (info.reviews.isNotEmpty()) {
            item { Text("Community Reviews", style = MaterialTheme.typography.titleMedium) }
            listItemsIndexed(
                info.reviews.take(8),
                key = { index, _ -> "${info.item.type}:${info.item.id}:review:$index" }
            ) { _, review ->
                var expanded by rememberSaveable(info.item.id, review.author, review.content) {
                    mutableStateOf(false)
                }
                var reviewOverflows by remember(info.item.id, review.author, review.content) {
                    mutableStateOf(false)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("extras-review-card"),
                    shape = RoundedCornerShape(LocalBousygramStyleSpec.current.cardCorner),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            review.author + if (review.score.isNotBlank()) " · ${review.score}" else "",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            review.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { result ->
                                if (!expanded) reviewOverflows = result.hasVisualOverflow
                            }
                        )
                        if (reviewOverflows) {
                            TextButton(
                                onClick = { expanded = !expanded },
                                modifier = Modifier.heightIn(min = spec.controlHeightDp.dp)
                                    .extrasFocusOutline()
                                    .testTag("extras-review-toggle")
                            ) {
                                Text(if (expanded) "Show less" else "Read full review")
                            }
                        }
                    }
                }
            }
        }
        if (info.related.isNotEmpty()) {
            item { Text("Related & recommended", style = MaterialTheme.typography.titleMedium) }
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().focusGroup().testTag("extras-related-row"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 12.dp)
                ) {
                    items(info.related, key = { "${it.type}:${it.id}" }) { item ->
                        Box(Modifier.width(if (compact) 128.dp else 160.dp)) {
                            ExtrasPosterCard(item) { onOpenItem(item) }
                        }
                    }
                }
            }
        }
        item {
            Text((if (info.item.type == ExtrasType.ANIME) "Anime metadata from AniList." else "This product uses the TMDB API but is not endorsed or certified by TMDB.") + " Catalog metadata does not provide streaming sources. Web embeds are third-party and may be unavailable.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Zoku's title / metadata / synopsis hierarchy, rebuilt with native
 * typography, bounded line length, and a remote-focusable full synopsis.
 * The title appears once (the image banner is intentionally title-free).
 */
@Composable
internal fun ExtrasDetailSummary(info: ExtrasDetail, spec: ExtrasDetailLayoutSpec) {
    val halo = LocalBousygramHaloColors.current
    var synopsisExpanded by rememberSaveable(info.item.type.name, info.item.id) {
        mutableStateOf(false)
    }
    val synopsis = info.description.ifBlank { "No description is available for this title." }
    var synopsisOverflows by remember(info.item.type, info.item.id, synopsis) {
        mutableStateOf(false)
    }
    Column(
        modifier = Modifier.widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(if (spec.compactChrome) 7.dp else 10.dp)
    ) {
        Text(
            "${info.item.type.label} · ${info.item.type.provider}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("extras-detail-category")
        )
        Text(
            info.item.title,
            style = if (spec.compactChrome) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("extras-detail-title")
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (info.item.year.isNotBlank()) {
                Text(
                    info.item.year,
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (info.item.rating.isNotBlank()) {
                if (info.item.year.isNotBlank()) {
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BousygramIcon(
                    icon = BousygramIcons.Star,
                    contentDescription = "Rating",
                    tint = halo.goldAccent,
                    size = 15.dp
                )
                Text(
                    info.item.rating,
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = halo.goldAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (info.genres.isNotEmpty()) {
            Text(
                info.genres.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (info.facts.isNotEmpty()) {
            Text(
                info.facts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "Overview",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            synopsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (synopsisExpanded) Int.MAX_VALUE else spec.synopsisCollapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!synopsisExpanded) synopsisOverflows = result.hasVisualOverflow
            },
            modifier = Modifier.testTag("extras-detail-overview")
        )
        if (synopsisOverflows) {
            TextButton(
                onClick = { synopsisExpanded = !synopsisExpanded },
                modifier = Modifier.heightIn(min = spec.controlHeightDp.dp)
                    .extrasFocusOutline()
                    .testTag("extras-description-toggle")
            ) {
                Text(if (synopsisExpanded) "Show less" else "Read full description")
            }
        }
    }
}
