package com.vaydelux.bousygram.presentation.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Arrangement
import com.vaydelux.bousygram.presentation.media.playableVideos
import com.vaydelux.bousygram.presentation.media.launch.rememberMediaLauncher
import com.vaydelux.bousygram.presentation.media.launch.LaunchOpeningIndicator
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val launcher = rememberMediaLauncher(
        screenKey = "search",
        onPlay = onPlaybackReady,
        onError = onStreamError
    )
    var privacyMode by remember { mutableStateOf(EnterprisePreferences.safeMode()) }
    var isVoiceListening by remember { mutableStateOf(false) }
    val searchInputFocusRequester = remember { FocusRequester() }
    val voiceButtonFocusRequester = remember { FocusRequester() }
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

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isVoiceListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognizedQuery = spokenMatches?.firstOrNull()?.trim()
            if (!recognizedQuery.isNullOrBlank()) {
                searchQuery = recognizedQuery
                executeSearch(recognizedQuery)
            }
        }
    }

    fun launchVoiceSearch() {
        val prompt = buildVoiceSearchPrompt(searchScope, viewModel.inChatTitle.ifBlank { inChatTitle })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Prefer the device recognizer on TVs that provide one; the system recognizer
            // remains responsible for its network fallback when offline recognition is absent.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            isVoiceListening = true
            voiceSearchLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            isVoiceListening = false
            Toast.makeText(context, "Voice search is not available on this device", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            isVoiceListening = false
            Toast.makeText(context, "Unable to activate voice search", Toast.LENGTH_SHORT).show()
        }
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
            SearchFocusTarget.VoiceButton -> runCatching { voiceButtonFocusRequester.requestFocus() }
            SearchFocusTarget.Grid -> {
                if (restoreGridIndex > 0) {
                    runCatching { gridState.scrollToItem(restoreGridIndex) }
                }
            }
        }
    }

    BousygramScaffold(modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        (keyEvent.key.nativeKeyCode == KeyEvent.KEYCODE_SEARCH ||
                         keyEvent.key.nativeKeyCode == KeyEvent.KEYCODE_VOICE_ASSIST)) {
                        launchVoiceSearch()
                        true
                    } else {
                        false
                    }
                }
        ) {
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
                            onSearch = { executeSearch() },
                            onVoiceSearch = { launchVoiceSearch() }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            VoiceSearchButton(
                                onClick = { launchVoiceSearch() },
                                isListening = isVoiceListening,
                                isCompact = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(voiceButtonFocusRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            onFocusStateChanged(SearchFocusTarget.VoiceButton, lastFocusedGridIndex)
                                            com.vaydelux.bousygram.core.tdlib.prefetch.FocusPrefetchManager.onFocusCleared()
                                        }
                                    }
                            )
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
                            onSearch = { executeSearch() },
                            onVoiceSearch = { launchVoiceSearch() }
                        )
                        VoiceSearchButton(
                            onClick = { launchVoiceSearch() },
                            isListening = isVoiceListening,
                            modifier = Modifier
                                .focusRequester(voiceButtonFocusRequester)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        onFocusStateChanged(SearchFocusTarget.VoiceButton, lastFocusedGridIndex)
                                        com.vaydelux.bousygram.core.tdlib.prefetch.FocusPrefetchManager.onFocusCleared()
                                    }
                                }
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
                        .weight(1f)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                    contentAlignment = Alignment.Center
                ) {
            when (val state = uiState) {
                is SearchUiState.Idle -> {
                    EmptyStateView(
                        title = "Ready to search",
                        message = "Use voice search, enter keywords, or pick a recent query to scan Telegram videos.",
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
                                        val queue = playableVideos(videos)
                                        launcher.launch(row.media, queue)
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
        BackHandler(enabled = launcher.isPending) {
            launcher.cancel()
        }
        LaunchOpeningIndicator(
            launcher = launcher,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}
}



@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit,
    onVoiceSearch: (() -> Unit)? = null
) {
    BousygramTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        trailingIcon = onVoiceSearch?.let { voiceAction ->
            {
                IconButton(
                    onClick = voiceAction,
                    modifier = Modifier.semantics {
                        contentDescription = "Voice search"
                        role = Role.Button
                    }
                ) {
                    VoiceMicIcon(
                        tint = MaterialTheme.colorScheme.primary,
                        size = 20.dp
                    )
                }
            }
        }
    )
}
