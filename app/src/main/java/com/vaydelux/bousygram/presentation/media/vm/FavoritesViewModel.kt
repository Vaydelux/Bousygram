package com.vaydelux.bousygram.presentation.media.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaydelux.bousygram.core.tdlib.cache.OfflineCacheManager
import com.vaydelux.bousygram.data.repository.SavedMessagesRepository
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.media.PlaybackRequest
import com.vaydelux.bousygram.presentation.media.buildPlaybackRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import com.vaydelux.bousygram.data.repository.toFavoriteMedia
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class FavoritesUiState(
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val unavailableCount: Int = 0,
    val unavailableKeys: Set<String> = emptySet(),
    val hasMore: Boolean = false,
    val totalCount: Int = 0,
    val hasPrevious: Boolean = false,
    val loadedStart: Int = 0
)

/** Cached first, paged favorites. Network lookups never gate initial content. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FavoritesViewModel(
    private val repository: SavedMessagesRepository = SavedMessagesRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState
    private data class Window(val limit: Int = 60, val offset: Int = 0)
    private val window = MutableStateFlow(Window())
    private val reload = MutableStateFlow(0)
    private var loadJob: Job? = null
    private val checkedKeys = mutableSetOf<String>()
    private var latestRows = emptyList<com.vaydelux.bousygram.data.database.FavoriteEntity>()
    private var generation = 0L

    init {
        viewModelScope.launch {
            com.vaydelux.bousygram.core.account.AccountSession.state.collectLatest { owner ->
                generation++
                loadJob?.cancel()
                checkedKeys.clear()
                latestRows = emptyList()
                window.value = Window()
                _uiState.value = FavoritesUiState(isLoading = owner != null)
                if (owner == null) return@collectLatest
                combine(window, com.vaydelux.bousygram.data.repository.FavoritesRepository.keys, reload) { page, keys, _ -> page to keys.size }
                    .flatMapLatest { (page, total) ->
                        com.vaydelux.bousygram.data.repository.FavoritesRepository.observePage(page.limit, page.offset)
                            .map { Triple(it, total, page) }
                            .catch { error ->
                                if (com.vaydelux.bousygram.core.account.AccountSession.isCurrent(owner))
                                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message ?: "Could not load favorites")
                            }
                    }.collect { (rows, total, page) ->
                        if (!com.vaydelux.bousygram.core.account.AccountSession.isCurrent(owner)) return@collect
                        if (page.offset > 0 && page.offset >= total) {
                            window.value = page.copy(offset = ((total - 1).coerceAtLeast(0) / 60) * 60)
                            return@collect
                        }
                        latestRows = rows
                        val unavailable = rows.filter { it.unavailable }.map { "${it.chatId}:${it.messageId}" }.toSet()
                        _uiState.value = _uiState.value.copy(
                            items = rows.map { it.toFavoriteMedia() }, isLoading = false,
                            unavailableCount = unavailable.size, unavailableKeys = unavailable,
                            hasMore = total > page.offset + rows.size, totalCount = maxOf(total, page.offset + rows.size),
                            hasPrevious = page.offset > 0, loadedStart = page.offset
                        )
                        refreshUnchecked()
                    }
            }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = null
        generation++
        checkedKeys.clear()
        _uiState.value = _uiState.value.copy(error = null, isLoading = latestRows.isEmpty())
        reload.value++
        refreshUnchecked()
    }

    fun loadMore() {
        if (!_uiState.value.hasMore) return
        val page = window.value
        window.value = if (page.limit < 240) page.copy(limit = page.limit + 60)
            else page.copy(offset = page.offset + 60)
    }
    fun loadPrevious() {
        val page = window.value
        window.value = page.copy(offset = (page.offset - 60).coerceAtLeast(0))
    }

    private fun refreshUnchecked() {
        if (loadJob?.isActive == true) return
        val owner = com.vaydelux.bousygram.core.account.AccountSession.current() ?: return
        val pending = latestRows.filter { "${it.chatId}:${it.messageId}" !in checkedKeys }
        if (pending.isEmpty()) return
        val requestGeneration = generation
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            var completed = false
            try {
                pending.chunked(MAX_PARALLEL_LOOKUPS).forEach { batch ->
                    coroutineScope {
                        batch.map { row -> async {
                            val key = "${row.chatId}:${row.messageId}"
                            val (item, _) = loadFavorite(key)
                            if (!com.vaydelux.bousygram.core.account.AccountSession.isCurrent(owner) || generation != requestGeneration) return@async
                            if (item?.fileId != null) com.vaydelux.bousygram.data.repository.FavoritesRepository.updateMetadata(owner, item)
                            else com.vaydelux.bousygram.data.repository.FavoritesRepository.markUnavailable(owner, row.chatId, row.messageId)
                            checkedKeys += key
                        } }.awaitAll()
                    }
                }
                completed = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation == requestGeneration) _uiState.value = _uiState.value.copy(error = "Some saved media could not be refreshed. Try again.")
            } finally {
                if (generation == requestGeneration) {
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                    loadJob = null
                }
            }
            // A page may have arrived while a previous batch was still resolving.
            if (completed && generation == requestGeneration) refreshUnchecked()
        }
    }

    fun removeFavorite(item: MediaItem) {
        com.vaydelux.bousygram.data.repository.FavoritesRepository.remove(item.chatId, item.messageId)
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
            val queue = _uiState.value.items
            onResult(buildPlaybackRequest(item, url, queue, null), null)
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
        const val MAX_PARALLEL_LOOKUPS = 4
    }
}
