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
 * Cloud-only, account-scoped Favorites browser. History is refreshed on each entry.
 * Only ONE page of posters is held by the ViewModel on low-memory TVs/phones.
 * Small numeric page cursors are kept in memory; no Favorites database is written.
 */
class CloudFavoritesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState

    private var loadJob: Job? = null
    private val cursors = mutableListOf(0L)
    private var pageIndex = 0
    private var nextBeforeId: Long? = null
    private var hasMore = false
    private var generation = 0L
    private var observedAccount = AccountSession.current()
    private val pageSize = 60

    init {
        viewModelScope.launch {
            AccountSession.state.collectLatest { owner ->
                // Initial emission is not an account switch. Do not cancel the
                // screen-entry cloud refresh that is already in progress.
                if (owner == observedAccount) return@collectLatest
                observedAccount = owner
                ++generation
                loadJob?.cancel()
                cursors.clear()
                cursors += 0L
                pageIndex = 0
                nextBeforeId = null
                hasMore = false
                _uiState.value = FavoritesUiState(isLoading = owner != null)
            }
        }
    }

    fun refresh() {
        cursors.clear()
        cursors += 0L
        nextBeforeId = null
        hasMore = false
        loadPage(0, 0L)
    }

    fun loadMore() {
        if (!hasMore || loadJob?.isActive == true) return
        val cursor = nextBeforeId ?: return
        val nextIndex = pageIndex + 1
        if (cursors.size <= nextIndex) cursors += cursor else cursors[nextIndex] = cursor
        loadPage(nextIndex, cursor)
    }

    fun loadPrevious() {
        if (pageIndex <= 0 || loadJob?.isActive == true) return
        val target = pageIndex - 1
        loadPage(target, cursors[target])
    }

    private fun loadPage(index: Int, cursor: Long) {
        loadJob?.cancel()
        val owner = AccountSession.current() ?: return
        val token = ++generation
        _uiState.value = _uiState.value.copy(isLoading = true, isRefreshing = true, error = null)
        loadJob = viewModelScope.launch {
            try {
                val page = TelegramCloudFavorites.refresh(cursor)
                if (token != generation || !AccountSession.isCurrent(owner)) return@launch
                pageIndex = index
                nextBeforeId = page.nextBeforeId
                hasMore = page.hasMore && page.nextBeforeId != cursor
                _uiState.value = FavoritesUiState(
                    items = page.items,
                    isLoading = false,
                    isRefreshing = false,
                    loadedStart = index * pageSize,
                    totalCount = index * pageSize + page.items.size,
                    hasMore = hasMore,
                    hasPrevious = index > 0
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token == generation && AccountSession.isCurrent(owner)) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error.message ?: "Could not refresh Telegram Favorites"
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
