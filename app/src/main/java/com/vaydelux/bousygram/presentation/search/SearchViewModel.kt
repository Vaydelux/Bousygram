package com.vaydelux.bousygram.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaydelux.bousygram.data.repository.SavedMessagesRepository
import com.vaydelux.bousygram.data.repository.searchChatVideos
import com.vaydelux.bousygram.data.repository.searchGlobalVideos
import com.vaydelux.bousygram.domain.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
        val hasMore: Boolean
    ) : SearchUiState()
}

class SearchViewModel(
    private val repository: SavedMessagesRepository = SavedMessagesRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

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
        currentQuery = ""
        nextOffset = ""
        nextFromMessageId = 0L
        accumulated.clear()
        _uiState.value = SearchUiState.Idle
    }

    fun executeGlobalSearch(query: String) {
        currentQuery = query.trim()
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
            when (searchMode) {
                SearchMode.Global -> runGlobalSearch(isAppend = false)
                SearchMode.InChat -> runInChatSearch(isAppend = false)
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state !is SearchUiState.Success || !state.hasMore || isLoadingMore) return
        isLoadingMore = true
        viewModelScope.launch {
            when (searchMode) {
                SearchMode.Global -> runGlobalSearch(isAppend = true)
                SearchMode.InChat -> runInChatSearch(isAppend = true)
            }
            isLoadingMore = false
        }
    }

    private fun runGlobalSearch(isAppend: Boolean) {
        repository.searchGlobalVideos(currentQuery, nextOffset) { result, error ->
            if (error != null) {
                _uiState.value = SearchUiState.Error(error)
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
            nextOffset = data.nextOffset
            publishSuccess(data.nextOffset.isNotEmpty())
        }
    }

    private fun runInChatSearch(isAppend: Boolean) {
        val chatId = inChatId ?: run {
            _uiState.value = SearchUiState.Error("No chat selected")
            return
        }
        val fromId = if (isAppend) nextFromMessageId else 0L
        repository.searchChatVideos(chatId, currentQuery, fromId) { items, nextId, error ->
            if (error != null) {
                _uiState.value = SearchUiState.Error(error)
                return@searchChatVideos
            }
            val rows = items.map { SearchResultRow(it, inChatTitle) }
            if (isAppend) {
                accumulated.addAll(rows)
            } else {
                accumulated.clear()
                accumulated.addAll(rows)
            }
            nextFromMessageId = nextId
            publishSuccess(items.isNotEmpty() && nextId != 0L)
        }
    }

    private fun publishSuccess(hasMore: Boolean) {
        _uiState.value = SearchUiState.Success(
            query = currentQuery,
            results = accumulated.toList(),
            hasMore = hasMore
        )
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
