package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.vaydelux.bousygram.presentation.components.BrowseGridShimmerSkeleton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.PremiumGridBackdrop
import com.vaydelux.bousygram.presentation.theme.bousygramResponsiveTokens
import com.vaydelux.bousygram.presentation.components.BousygramPageHeading
import com.vaydelux.bousygram.presentation.components.EmptyStateView
import com.vaydelux.bousygram.presentation.components.LoadingOverlay
import com.vaydelux.bousygram.presentation.components.MediaPosterCard
import com.vaydelux.bousygram.presentation.media.launch.LaunchOpeningIndicator
import com.vaydelux.bousygram.presentation.media.launch.rememberMediaLauncher
import com.vaydelux.bousygram.presentation.media.vm.CloudFavoritesViewModel
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onPlay: (PlaybackRequest) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CloudFavoritesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Refresh cloud messages each time this screen opens, even if the VM is retained.
    LaunchedEffect(Unit) { viewModel.refresh() }
    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }
    var pendingDelete by remember { mutableStateOf<MediaItem?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val launcher = rememberMediaLauncher(
        screenKey = "favorites",
        onPlay = onPlay,
        onError = { scope.launch { snackbarHost.showSnackbar(it) } }
    )
    val safeMode = remember { EnterprisePreferences.safeMode() }
    val adaptive = rememberAdaptiveMetrics()
    val responsive = bousygramResponsiveTokens(adaptive)
    val horizontalPadding = responsive.pagePadding
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreKey by remember { mutableStateOf<String?>(null) }
    var previousKeys by remember { mutableStateOf(emptyList<String>()) }
    val restoreFocus = remember { FocusRequester() }
    val emptyFocus = remember { FocusRequester() }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val currentKeys = state.items.map { "${it.chatId}:${it.messageId}" }
    LaunchedEffect(currentKeys, state.isLoading) {
        if (state.isLoading) return@LaunchedEffect
        val oldFocus = focusedKey
        if (oldFocus != null && oldFocus !in currentKeys && currentKeys.isNotEmpty()) {
            val index = previousKeys.indexOf(oldFocus).coerceAtLeast(0).coerceAtMost(currentKeys.lastIndex)
            restoreKey = currentKeys[index]
        }
        if (oldFocus != null && currentKeys.isEmpty()) {
            focusedKey = null
            androidx.compose.runtime.withFrameNanos { }
            emptyFocus.requestFocus()
        }
        if (oldFocus != null && oldFocus in currentKeys && previousKeys.isEmpty()) restoreKey = oldFocus
        previousKeys = currentKeys
    }
    LaunchedEffect(restoreKey) {
        if (restoreKey != null) {
            val index = currentKeys.indexOf(restoreKey)
            if (index >= 0) gridState.scrollToItem(index)
            androidx.compose.runtime.withFrameNanos { }
            restoreFocus.requestFocus()
        }
    }



    PremiumGridBackdrop(modifier = modifier) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { insets ->
        Box(modifier = Modifier.fillMaxSize().padding(insets)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = if (adaptive.isCompact) 12.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BousygramPageHeading(
                    eyebrow = "Telegram media  /  Your collection",
                    title = "Favorites",
                    subtitle = if (state.isLoading) "Loading your saved videos…"
                        else if (state.items.isEmpty()) "No saved videos"
                        else "${state.loadedStart + 1}-${state.loadedStart + state.items.size} of ${state.totalCount} saved videos${if (state.isRefreshing) " - Refreshing" else ""}",
                    compact = adaptive.isCompact || adaptive.isShortWindow
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BousygramButton("← Back", onClick = { if (!launcher.cancel()) onBack() }, modifier = Modifier.focusRequester(emptyFocus))
                    BousygramButton("Search", onOpenSearch)
                    BousygramButton("Refresh", viewModel::refresh, enabled = !state.isLoading && !state.isRefreshing)
                    if (state.items.isNotEmpty()) {
                        BousygramButton(
                            text = "▶ Play loaded",
                            onClick = { state.items.firstOrNull { it.fileId != null }?.let { launcher.launch(it, state.items.filter { media -> media.fileId != null }) } },
                            selected = true,
                            enabled = !state.isLoading && !state.isRefreshing && !launcher.isPending
                        )
                    }
                }
            }

            if (state.unavailableCount > 0 && !state.isLoading && !adaptive.isShortWindow) {
                Text(
                    "${state.unavailableCount} saved item(s) could not be refreshed. Retry or remove them from the actions menu.",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            if (state.error != null && state.items.isNotEmpty()) {
                Text(state.error.orEmpty(), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading && state.items.isEmpty() -> BrowseGridShimmerSkeleton(
                        columns = GridCells.Adaptive(minSize = responsive.cardMinWidth),
                        modifier = Modifier.fillMaxSize(),
                        itemCount = if (adaptive.isCompact) 6 else 9
                    )
                    state.error != null && state.items.isEmpty() -> EmptyStateView(
                        title = "Couldn't load favorites",
                        message = state.error.orEmpty(),
                        actionText = "Try again",
                        onAction = viewModel::refresh,
                        modifier = Modifier.fillMaxSize()
                    )
                    state.items.isEmpty() -> EmptyStateView(
                        title = "No favorites yet",
                        message = "Add media from Home or Library. Your saved items are stored in a private Telegram Favorites channel.",
                        actionText = "Browse videos",
                        onAction = onBack,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = responsive.cardMinWidth),
                        horizontalArrangement = Arrangement.spacedBy(responsive.gridGap),
                        verticalArrangement = Arrangement.spacedBy(responsive.gridGap),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(state.items, key = { "${it.chatId}:${it.messageId}" }) { item ->
                            FavoriteGridItem(
                                item = item, safeMode = safeMode,
                                unavailable = "${item.chatId}:${item.messageId}" in state.unavailableKeys,
                                modifier = if (restoreKey == "${item.chatId}:${item.messageId}") Modifier.focusRequester(restoreFocus) else Modifier,
                                onFocus = { if (it) focusedKey = "${item.chatId}:${item.messageId}" },
                                onSelect = { selectedMedia = item }
                            )
                        }
                        if (state.hasMore) {
                            item(key = "favorites-load-more", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                BousygramButton("Load more favorites", onClick = viewModel::loadMore,
                                    modifier = Modifier.fillMaxWidth())
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
        selectedMedia?.let { item ->
            AlertDialog(
                onDismissRequest = { selectedMedia = null },
                title = { Text("Favorite video") },
                text = { Text(item.title + "\nSaved in your private Telegram channel.") },
                confirmButton = {
                    TextButton(onClick = {
                        selectedMedia = null
                        if (item.fileId == null) {
                            scope.launch {
                                snackbarHost.showSnackbar("Media unavailable. Refresh and retry.")
                            }
                        } else launcher.launch(item, state.items.filter { it.fileId != null })
                    }) { Text("Watch") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { selectedMedia = null }) { Text("Cancel") }
                        TextButton(onClick = { selectedMedia = null; pendingDelete = item }) {
                            Text("Delete", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
        pendingDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { if (!deleting) pendingDelete = null },
                title = { Text("Delete this saved favorite?") },
                text = {
                    Text("This permanently deletes the saved message from your private Bousygram Favorites Telegram channel. " +
                        "The original source message will NOT be deleted. If the source is gone, the saved copy may be unrecoverable.")
                },
                confirmButton = {
                    TextButton(enabled = !deleting, onClick = {
                        deleting = true
                        viewModel.removeFavorite(item) { result ->
                            deleting = false
                            if (result.isSuccess) {
                                pendingDelete = null
                                scope.launch { snackbarHost.showSnackbar("Removed from Favorites") }
                            } else scope.launch {
                                snackbarHost.showSnackbar(result.exceptionOrNull()?.message ?: "Could not delete favorite")
                            }
                        }
                    }) {
                        Text("Delete Favorite", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(enabled = !deleting, onClick = { pendingDelete = null }) {
                        Text("Keep Favorite")
                    }
                }
            )
        }

    }
}
}
}

@Composable
private fun FavoriteGridItem(
    item: MediaItem,
    safeMode: Boolean,
    unavailable: Boolean,
    modifier: Modifier = Modifier,
    onFocus: (Boolean) -> Unit,
    onSelect: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaPosterCard(
            media = item,
            subtitle = "Telegram",
            badge = if (unavailable) "RETRY REFRESH" else "FAVORITE",
            privacyMode = safeMode,
            cardWidth = null,
            showActions = false,
            onClick = onSelect,
            modifier = modifier.fillMaxWidth(),
            onFocusChanged = onFocus
        )

    }
}
