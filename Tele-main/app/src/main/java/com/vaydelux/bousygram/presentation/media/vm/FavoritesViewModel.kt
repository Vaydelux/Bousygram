package com.vaydelux.bousygram.presentation.media.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaydelux.bousygram.core.tdlib.cache.OfflineCacheManager
import com.vaydelux.bousygram.data.repository.SavedMessagesRepository
import com.vaydelux.bousygram.data.repository.findSidecarSubtitle
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.media.PlaybackRequest
import com.vaydelux.bousygram.presentation.media.buildPlaybackRequest
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class FavoritesUiState(
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val unavailableCount: Int = 0
)

/** Loads favorites by their saved chat/message identity, independent of the current source page. */
class FavoritesViewModel(
    private val repository: SavedMessagesRepository = SavedMessagesRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState
    private var generation = 0L
    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        val requestGeneration = ++generation
        val keys = EnterprisePreferences.favoriteKeys().toList().sorted()
        if (keys.isEmpty()) {
            loadJob = null
            _uiState.value = FavoritesUiState()
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, unavailableCount = 0)
        loadJob = viewModelScope.launch {
            val loaded = mutableListOf<MediaItem>()
            var unavailable = 0
            try {
                keys.chunked(MAX_PARALLEL_LOOKUPS).forEach { batch ->
                    val results = coroutineScope { batch.map { key -> async { loadFavorite(key) } }.awaitAll() }
                    results.forEach { (item, _) ->
                        if (item?.fileId != null) loaded += item else unavailable++
                    }
                    if (requestGeneration != generation) return@launch
                }
                if (requestGeneration == generation) {
                    _uiState.value = FavoritesUiState(
                        items = loaded.distinctBy { "${it.chatId}:${it.messageId}" }
                            .sortedBy { it.title.lowercase() },
                        isLoading = false,
                        error = null,
                        unavailableCount = unavailable
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestGeneration == generation) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Could not load favorites"
                    )
                }
            } finally {
                if (requestGeneration == generation) loadJob = null
            }
        }
    }

    fun removeFavorite(item: MediaItem) {
        EnterprisePreferences.toggleFavorite(item.chatId, item.messageId)
        _uiState.value = _uiState.value.copy(items = _uiState.value.items.filterNot {
            it.chatId == item.chatId && it.messageId == item.messageId
        })
        // Reload the remaining keys so a refresh already in flight cannot reinsert this item.
        refresh()
    }

    fun requestPlay(item: MediaItem, onResult: (PlaybackRequest?, String?) -> Unit) {
        viewModelScope.launch {
            val streamResult = runCatching { repository.prepareStream(item).getOrThrow() }
            val url = streamResult.getOrNull()
            if (url.isNullOrBlank()) {
                onResult(null, streamResult.exceptionOrNull()?.message ?: "Could not prepare stream")
                return@launch
            }
            val fileId = item.fileId ?: run {
                onResult(null, "This favorite no longer has a playable file")
                return@launch
            }
            EnterprisePreferences.setLastPlayed(item.chatId, item.messageId, fileId, item.title)
            OfflineCacheManager.markFileAccessed(fileId)
            repository.findSidecarSubtitle(item.chatId, item.title) { subtitleFileId, _ ->
                val subtitleUrl = if (EnterprisePreferences.subtitlesDefault()) {
                    subtitleFileId?.let { LocalStreamingServer.getSubtitleUrl(it, item.title) }
                } else null
                viewModelScope.launch {
                    val queue = _uiState.value.items
                    onResult(buildPlaybackRequest(item, url, queue, subtitleUrl), null)
                }
            }
        }
    }

    private suspend fun loadFavorite(key: String): Pair<MediaItem?, String?> {
        val split = key.split(':')
        val chatId = split.getOrNull(0)?.toLongOrNull() ?: return null to "Invalid favorite key"
        val messageId = split.getOrNull(1)?.toLongOrNull() ?: return null to "Invalid favorite key"
        return try {
            withTimeoutOrNull(8_000L) {
                suspendCancellableCoroutine<Pair<MediaItem?, String?>> { continuation ->
                    repository.getMessage(chatId, messageId) { item, error ->
                        if (continuation.isActive) continuation.resume(item to error)
                    }
                }
            } ?: (null to "Timed out while loading a favorite")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            null to (error.message ?: "Could not load favorite")
        }
    }

    override fun onCleared() {
        generation++
        loadJob?.cancel()
        loadJob = null
        repository.shutdown()
        super.onCleared()
    }

    private companion object {
        const val MAX_PARALLEL_LOOKUPS = 8
    }
}
