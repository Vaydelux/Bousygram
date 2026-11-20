package com.vaydelux.bousygram.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaydelux.bousygram.data.database.RecentSearchQuery
import com.vaydelux.bousygram.data.database.RecentSearchRepository
import com.vaydelux.bousygram.data.repository.SavedMessagesRepository
import com.vaydelux.bousygram.data.repository.searchChatVideos
import com.vaydelux.bousygram.data.repository.searchGlobalVideos
import com.vaydelux.bousygram.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchMode {
    Global,
    InChat
}

data class SearchResultRow(
    val media: MediaItem,
    val channelLabel: String
)

sealed class SearchUiState {
    data object Idle : SearchUiState()
    data object Loading : SearchUiState()
    data class Error(val message: String) : SearchUiState()
    data class Success(
        val query: String,
        val results: List<SearchResultRow>,
        val hasMore: Boolean,
        val error: String? = null,
        val canRetryPage: Boolean = false
    ) : SearchUiState()
}

class SearchViewModel(
    private val repository: SavedMessagesRepository = SavedMessagesRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    val recentSearches: StateFlow<List<RecentSearchQuery>> = RecentSearchRepository.recentSearches

    var searchMode: SearchMode = SearchMode.Global
        private set
    var inChatId: Long? = null
        private set
    var inChatTitle: String = ""
        private set
    private val _scope = MutableStateFlow(SearchMode.Global)
    val scope: StateFlow<SearchMode> = _scope

    private var currentQuery: String = ""
    private var nextOffset: String = ""
    private var nextFromMessageId: Long = 0L
    private var isLoadingMore = false
    private var searchGeneration = 0L
    private val accumulated = mutableListOf<SearchResultRow>()
    private var chatTitles = mutableMapOf<Long, String>()

    fun setInChatScope(chatId: Long, chatTitle: String) {
        searchMode = SearchMode.InChat
        inChatId = chatId
        inChatTitle = chatTitle
        _scope.value = SearchMode.InChat
    }

    fun setGlobalScope() {
        searchMode = SearchMode.Global
        inChatId = null
        inChatTitle = ""
        _scope.value = SearchMode.Global
    }

    fun clearQuery() {
        ++searchGeneration
        isLoadingMore = false
        currentQuery = ""
        nextOffset = ""
        nextFromMessageId = 0L
        accumulated.clear()
        _uiState.value = SearchUiState.Idle
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            RecentSearchRepository.removeSearchQuery(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            RecentSearchRepository.clearRecentSearches()
        }
    }

    fun executeGlobalSearch(query: String) {
        val requestedQuery = query.trim()
        val generation = ++searchGeneration
        isLoadingMore = false
        currentQuery = requestedQuery
        nextOffset = ""
        nextFromMessageId = 0L
        accumulated.clear()
        chatTitles.clear()
        if (currentQuery.isBlank()) {
            _uiState.value = SearchUiState.Idle
            return
        }
        _uiState.value = SearchUiState.Loading
        viewModelScope.launch {
            val scopedChatId = if (searchMode == SearchMode.InChat) inChatId else null
            val scopedChatTitle = if (searchMode == SearchMode.InChat) inChatTitle else ""
            RecentSearchRepository.recordSearchQuery(
                query = requestedQuery,
                chatId = scopedChatId,
                chatTitle = scopedChatTitle,
                resultCount = 0
            )
            val cachedItems = withContext(Dispatchers.IO) {
                com.vaydelux.bousygram.data.database.VideoIndexRepository.searchIndexedVideos(
                    requestedQuery, scopedChatId, limit = 50
                )
            }
            if (generation != searchGeneration) return@launch
            if (cachedItems.isNotEmpty() && _uiState.value is SearchUiState.Loading) {
                val cachedRows = cachedItems.map {
                    SearchResultRow(
                        media = it,
                        channelLabel = if (searchMode == SearchMode.InChat) inChatTitle else (chatTitles[it.chatId] ?: "Telegram")
                    )
                }
                accumulated.addAll(cachedRows)
                _uiState.value = SearchUiState.Success(
                    query = currentQuery,
                    results = cachedRows,
                    hasMore = false
                )
            }

            when (searchMode) {
                SearchMode.Global -> runGlobalSearch(isAppend = false, generation = generation)
                SearchMode.InChat -> runInChatSearch(isAppend = false, generation = generation)
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state !is SearchUiState.Success || (!state.hasMore && !state.canRetryPage) || isLoadingMore) return
        isLoadingMore = true
        when (searchMode) {
            SearchMode.Global -> runGlobalSearch(isAppend = true, generation = searchGeneration)
            SearchMode.InChat -> runInChatSearch(isAppend = true, generation = searchGeneration)
        }
    }

    private fun runGlobalSearch(isAppend: Boolean, generation: Long) {
        repository.searchGlobalVideos(currentQuery, nextOffset) { result, error ->
            if (generation != searchGeneration) return@searchGlobalVideos
            isLoadingMore = false
            if (error != null) {
                publishSearchError(error, isAppend)
                return@searchGlobalVideos
            }
            val data = result ?: return@searchGlobalVideos
            chatTitles.putAll(data.chatTitles)
            val rows = data.items.map { item ->
                SearchResultRow(
                    media = item,
                    channelLabel = chatTitles[item.chatId] ?: "Telegram"
                )
            }
            if (isAppend) {
                accumulated.addAll(rows)
            } else {
                accumulated.clear()
                accumulated.addAll(rows)
            }
            if (data.items.isNotEmpty()) {
                com.vaydelux.bousygram.data.database.VideoIndexRepository.indexMediaItemsAsync(data.items)
            }
            nextOffset = data.nextOffset
            publishSuccess(data.nextOffset.isNotEmpty())
        }
    }

    private fun runInChatSearch(isAppend: Boolean, generation: Long) {
        val chatId = inChatId ?: run {
            isLoadingMore = false
            _uiState.value = SearchUiState.Error("No chat selected")
            return
        }
        val fromId = if (isAppend) nextFromMessageId else 0L
        repository.searchChatVideos(chatId, currentQuery, fromId) { items, nextId, error ->
            if (generation != searchGeneration) return@searchChatVideos
            isLoadingMore = false
            if (error != null) {
                publishSearchError(error, isAppend)
                return@searchChatVideos
            }
            val rows = items.map { SearchResultRow(it, inChatTitle) }
            if (isAppend) {
                accumulated.addAll(rows)
            } else {
                accumulated.clear()
                accumulated.addAll(rows)
            }
            if (items.isNotEmpty()) {
                com.vaydelux.bousygram.data.database.VideoIndexRepository.indexMediaItemsAsync(items)
            }
            nextFromMessageId = nextId
            publishSuccess(items.isNotEmpty() && nextId != 0L)
        }
    }

    private fun publishSearchError(error: String, isAppend: Boolean) {
        val current = _uiState.value
        _uiState.value = if (current is SearchUiState.Success && current.results.isNotEmpty()) {
            current.copy(hasMore = false, error = error, canRetryPage = isAppend)
        } else {
            SearchUiState.Error(error)
        }
    }

    private fun publishSuccess(hasMore: Boolean) {
        val resultsList = accumulated.toList()
        if (currentQuery.isNotBlank()) {
            val scopedChatId = if (searchMode == SearchMode.InChat) inChatId else null
            val scopedChatTitle = if (searchMode == SearchMode.InChat) inChatTitle else ""
            RecentSearchRepository.recordSearchQueryAsync(
                query = currentQuery,
                chatId = scopedChatId,
                chatTitle = scopedChatTitle,
                resultCount = resultsList.size
            )
        }
        _uiState.value = SearchUiState.Success(
            query = currentQuery,
            results = resultsList,
            hasMore = hasMore
        )
    }

    override fun onCleared() {
        repository.shutdown()
        super.onCleared()
    }

    fun requestPlay(
        item: MediaItem,
        queueItems: List<MediaItem>,
        onResult: (String?, String?, String?) -> Unit
    ) {
        viewModelScope.launch {
            val streamResult = runCatching { repository.prepareStream(item).getOrThrow() }
            val url = streamResult.getOrNull()
            if (url.isNullOrBlank()) {
                onResult(null, null, streamResult.exceptionOrNull()?.message ?: "Could not prepare stream")
                return@launch
            }
            onResult(url, null, null)
        }
    }
}
