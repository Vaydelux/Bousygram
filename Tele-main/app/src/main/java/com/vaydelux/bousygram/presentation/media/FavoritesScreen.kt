package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.EmptyStateView
import com.vaydelux.bousygram.presentation.components.LoadingOverlay
import com.vaydelux.bousygram.presentation.components.MediaPosterCard
import com.vaydelux.bousygram.presentation.media.vm.FavoritesViewModel
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onPlay: (PlaybackRequest) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val safeMode = remember { EnterprisePreferences.safeMode() }

    LaunchedEffect(viewModel) { viewModel.refresh() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0D0D0F),
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Favorites", color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
                    Text(
                        text = if (state.isLoading) "Loading your saved videos…" else "${state.items.size} saved ${if (state.items.size == 1) "video" else "videos"}",
                        color = Color.White.copy(alpha = 0.62f),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                }
                BousygramButton("Home", onBack)
                BousygramButton("Search", onOpenSearch)
                BousygramButton("Refresh", viewModel::refresh)
            }

            if (state.unavailableCount > 0 && !state.isLoading) {
                Text(
                    "${state.unavailableCount} saved item(s) are no longer available in Telegram and are not shown.",
                    color = Color(0xFFE4B66A),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
            }
            state.error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading && state.items.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.items.isEmpty() -> EmptyStateView(
                        title = "No favorites yet",
                        message = "Favorite a video from Home or your Telegram library. Your saved items will appear here across sources.",
                        actionText = "Browse videos",
                        onAction = onBack,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 284.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(state.items, key = { "${it.chatId}:${it.messageId}" }) { item ->
                            FavoriteGridItem(
                                item = item,
                                safeMode = safeMode,
                                onPlay = {
                                    viewModel.requestPlay(item) { request, error ->
                                        if (request != null) onPlay(request)
                                        else scope.launch { snackbarHost.showSnackbar(error ?: "Could not prepare this favorite") }
                                    }
                                },
                                onRemove = { viewModel.removeFavorite(item) }
                            )
                        }
                    }
                }
            }
        }
        if (state.isLoading && state.items.isNotEmpty()) {
            LoadingOverlay(
                text = "Refreshing favorites…",
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f))
            )
        }
    }
}

@Composable
private fun FavoriteGridItem(
    item: MediaItem,
    safeMode: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaPosterCard(
            media = item,
            subtitle = "Telegram",
            badge = "FAVORITE",
            privacyMode = safeMode,
            cardWidth = null,
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            BousygramButton("Remove favorite", onRemove)
        }
    }
}
