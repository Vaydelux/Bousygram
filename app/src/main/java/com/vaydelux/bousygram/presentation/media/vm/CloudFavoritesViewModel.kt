package com.vaydelux.bousygram.presentation.media.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.data.repository.TelegramCloudFavorites
import com.vaydelux.bousygram.domain.model.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The private Telegram channel owns the collection. Every screen entry calls
 * refresh() instead of showing a persisted Favorites Room snapshot.
 */
class CloudFavoritesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState

    private var loadJob: Job? = null
    private var nextBeforeId: Long? = null
    private var hasMore = false
    private var generation = 0L

    init {
        viewModelScope.launch {
            AccountSession.state.collectLatest {
                ++generation
                loadJob?.cancel()
                nextBeforeId = null
                hasMore = false
                _uiState.value = FavoritesUiState(isLoading = it != null)
            }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        val owner = AccountSession.current() ?: return
        val token = ++generation
        _uiState.value = FavoritesUiState(isLoading = true, isRefreshing = true)
        loadJob = viewModelScope.launch {
            try {
                val page = TelegramCloudFavorites.refresh()
                if (token != generation || !AccountSession.isCurrent(owner)) return@launch
                nextBeforeId = page.nextBeforeId
                hasMore = page.hasMore
                _uiState.value = FavoritesUiState(
                    items = page.items,
                    isLoading = false,
                    isRefreshing = false,
                    totalCount = page.items.size,
                    hasMore = page.hasMore
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token == generation && AccountSession.isCurrent(owner)) {
                    _uiState.value = FavoritesUiState(
                        isLoading = false,
                        error = error.message ?: "Could not refresh Telegram Favorites"
                    )
                }
            }
        }
    }

    fun loadMore() {
        if (!hasMore || loadJob?.isActive == true) return
        val cursor = nextBeforeId ?: return
        val owner = AccountSession.current() ?: return
        val token = generation
        loadJob = viewModelScope.launch {
            try {
                val page = TelegramCloudFavorites.refresh(cursor)
                if (token != generation || !AccountSession.isCurrent(owner)) return@launch
                val appended = (_uiState.value.items + page.items)
                    .distinctBy { "${it.chatId}:${it.messageId}" }
                nextBeforeId = page.nextBeforeId
                hasMore = page.hasMore && page.nextBeforeId != cursor
                _uiState.value = _uiState.value.copy(
                    items = appended,
                    hasMore = hasMore,
                    totalCount = appended.size,
                    error = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token == generation && AccountSession.isCurrent(owner)) {
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Could not load more Favorites"
                    )
                }
            }
        }
    }

    fun removeFavorite(item: MediaItem, onComplete: (Result<Unit>) -> Unit) {
        val owner = AccountSession.current() ?: return
        viewModelScope.launch {
            val outcome = try {
                TelegramCloudFavorites.deleteFavorite(item)
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            if (AccountSession.isCurrent(owner)) {
                onComplete(outcome)
                if (outcome.isSuccess) refresh()
            }
        }
    }

    override fun onCleared() {
        ++generation
        loadJob?.cancel()
        super.onCleared()
    }
}
