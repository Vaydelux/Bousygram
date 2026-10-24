package com.vaydelux.bousygram.presentation.search

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramChip
import com.vaydelux.bousygram.presentation.components.BousygramScaffold
import com.vaydelux.bousygram.presentation.components.BousygramTextField
import com.vaydelux.bousygram.presentation.components.EmptyStateView
import com.vaydelux.bousygram.presentation.components.LoadingOverlay
import com.vaydelux.bousygram.presentation.components.MediaPosterCard
import com.vaydelux.bousygram.presentation.media.buildPlaybackRequest
import com.vaydelux.bousygram.presentation.media.playableVideos
import com.vaydelux.bousygram.presentation.navigation.SearchFocusTarget

enum class SearchFilterOption(val label: String) {
    ALL("All Results"),
    MOVIES("Long (> 20m)"),
    CLIPS("Clips (< 5m)"),
    LARGE("Large (> 300MB)")
}

@Composable
fun TvVideoSearchScreen(
    onPlayRequest: (MediaItem, List<MediaItem>, (String?, String?, String?) -> Unit) -> Unit,
    onPlaybackReady: (com.vaydelux.bousygram.presentation.media.PlaybackRequest) -> Unit,
    onStreamError: (String) -> Unit,
    modifier: Modifier = Modifier,
    inChatId: Long? = null,
    inChatTitle: String = "",
    restoreFocusTarget: SearchFocusTarget = SearchFocusTarget.SearchField,
    restoreGridIndex: Int = 0,
    onFocusStateChanged: (SearchFocusTarget, Int) -> Unit = { _, _ -> },
    viewModel: SearchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchScope by viewModel.scope.collectAsState()
    val adaptive = rememberAdaptiveMetrics()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var preparingKey by remember { mutableStateOf<String?>(null) }
    var privacyMode by remember { mutableStateOf(EnterprisePreferences.safeMode()) }
    val searchInputFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val focusManager = LocalFocusManager.current
    var lastFocusedGridIndex by rememberSaveable { mutableIntStateOf(restoreGridIndex) }
    var recentQueries by remember { mutableStateOf(EnterprisePreferences.recentQueries()) }
    var selectedFilter by remember { mutableStateOf(SearchFilterOption.ALL) }

    fun clearSearch() {
        searchQuery = ""
        viewModel.clearQuery()
    }

    fun executeSearch(query: String = searchQuery) {
        val clean = query.trim()
        if (clean.isBlank()) return
        EnterprisePreferences.rememberQuery(clean)
        recentQueries = EnterprisePreferences.recentQueries()
        viewModel.executeGlobalSearch(clean)
        focusManager.moveFocus(FocusDirection.Down)
    }

    LaunchedEffect(inChatId, inChatTitle) {
        if (inChatId != null) {
            viewModel.setInChatScope(inChatId, inChatTitle)
        } else {
            viewModel.setGlobalScope()
        }
    }

    LaunchedEffect(Unit) {
        when (restoreFocusTarget) {
            SearchFocusTarget.SearchField -> runCatching { searchInputFocusRequester.requestFocus() }
            SearchFocusTarget.Grid -> {
                if (restoreGridIndex > 0) {
                    runCatching { gridState.scrollToItem(restoreGridIndex) }
                }
            }
        }
    }

    BousygramScaffold(modifier) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (adaptive.isCompact) 16.dp else 42.dp)
            ) {
                Text(
                    text = if (privacyMode) "Search media" else if (searchScope == SearchMode.InChat) {
                        "Search in ${viewModel.inChatTitle.ifBlank { inChatTitle }}"
                    } else {
                        "Search Telegram videos"
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = if (adaptive.isCompact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge
                )
                Spacer(Modifier.height(14.dp))
                if (adaptive.isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = if (searchScope == SearchMode.InChat) "Keywords in current source" else "Global keywords",
                            modifier = Modifier
                                .focusRequester(searchInputFocusRequester)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        onFocusStateChanged(SearchFocusTarget.SearchField, lastFocusedGridIndex)
                                        com.vaydelux.bousygram.core.tdlib.prefetch.FocusPrefetchManager.onFocusCleared()
                                    }
                                },
                            onSearch = { executeSearch() }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            BousygramButton("Search", { executeSearch() }, modifier = Modifier.weight(1f))
                            BousygramButton("Clear", { clearSearch() }, modifier = Modifier.weight(1f))
                        }
                        if (searchScope == SearchMode.InChat) {
                            BousygramButton("Search all", {
                                viewModel.setGlobalScope()
                                executeSearch()
                            }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        SearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = if (searchScope == SearchMode.InChat) "Keywords in current source" else "Global keywords",
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchInputFocusRequester)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        onFocusStateChanged(SearchFocusTarget.SearchField, lastFocusedGridIndex)
                                        com.vaydelux.bousygram.core.tdlib.prefetch.FocusPrefetchManager.onFocusCleared()
                                    }
                                },
                            onSearch = { executeSearch() }
                        )
                        BousygramButton("Search", { executeSearch() })
                        BousygramButton("Clear", { clearSearch() })
                        if (searchScope == SearchMode.InChat) {
                            BousygramButton("Search all", {
                                viewModel.setGlobalScope()
                                executeSearch()
                            })
                        }
                    }
                }

                if (!privacyMode && recentQueries.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recentQueries.take(5)) { query ->
                        BousygramChip(query, selected = false, onClick = {
                            searchQuery = query
                            executeSearch(query)
                        })
                        }
                        item {
                            BousygramButton("Clear recent", {
                                EnterprisePreferences.clearRecentQueries()
                                recentQueries = emptyList()
                            })
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
            when (val state = uiState) {
                is SearchUiState.Idle -> {
                    EmptyStateView(
                        title = "Ready to search",
                        message = "Enter keywords or pick a recent query to scan Telegram videos.",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is SearchUiState.Loading -> LoadingOverlay("Searching Telegram...")
                is SearchUiState.Error -> {
                    EmptyStateView("Search failed", state.message, actionText = "Retry", onAction = { executeSearch() })
                }
                is SearchUiState.Success -> {
                    if (state.results.isEmpty()) {
                        EmptyStateView(
                            "No videos found",
                            if (privacyMode) "No matching videos were found." else "No results for \"${state.query}\".",
                            actionText = if (searchScope == SearchMode.InChat) "Search all" else null,
                            onAction = if (searchScope == SearchMode.InChat) {
                                {
                                    viewModel.setGlobalScope()
                                    executeSearch()
                                }
                            } else {
                                null
                            }
                        )
                    } else {
                        val filteredResults = remember(state.results, selectedFilter) {
                            when (selectedFilter) {
                                SearchFilterOption.ALL -> state.results
                                SearchFilterOption.MOVIES -> state.results.filter { it.media.durationSeconds >= 1200 }
                                SearchFilterOption.CLIPS -> state.results.filter { it.media.durationSeconds in 1..300 }
                                SearchFilterOption.LARGE -> state.results.filter { it.media.fileSizeBytes >= 300 * 1024 * 1024L }
                            }
                        }
                        val videos = filteredResults.map { it.media }
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                items(SearchFilterOption.entries) { filter ->
                                    BousygramChip(
                                        text = filter.label,
                                        selected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter }
                                    )
                                }
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(if (adaptive.isCompact) 150.dp else 220.dp),
                                state = gridState,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 48.dp),
                                modifier = Modifier.fillMaxSize().weight(1f).focusGroup()
                            ) {
                                itemsIndexed(
                                    filteredResults,
                                    key = { _, row -> "${row.media.chatId}:${row.media.messageId}" }
                                ) { index, row ->
                                    if (index >= filteredResults.size - 4 && state.hasMore) {
                                        LaunchedEffect(filteredResults.size) {
                                            viewModel.loadNextPage()
                                        }
                                    }
                                MediaPosterCard(
                                    media = row.media,
                                    subtitle = row.channelLabel,
                                    onClick = {
                                        val key = "${row.media.chatId}:${row.media.messageId}"
                                        preparingKey = key
                                        val queue = playableVideos(videos)
                                        onPlayRequest(row.media, queue) { url, subUrl, error ->
                                            preparingKey = null
                                            if (url.isNullOrBlank()) {
                                                onStreamError(error ?: "Could not prepare stream")
                                            } else {
                                                onPlaybackReady(
                                                    buildPlaybackRequest(row.media, url, queue, subUrl)
                                                )
                                            }
                                        }
                                    },
                                    cardWidth = null,
                                    privacyMode = privacyMode,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                lastFocusedGridIndex = index
                                                onFocusStateChanged(SearchFocusTarget.Grid, index)
                                                com.vaydelux.bousygram.core.tdlib.prefetch.FocusPrefetchManager.onCardFocused(row.media)
                                            }
                                        }
                                )
                            }
                            item {
                                Text(
                                    text = if (state.hasMore) "Loading more..." else "No more results",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
        if (preparingKey != null) {
            LoadingOverlay(
                text = "Preparing video...",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
}



@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit
) {
    BousygramTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}
