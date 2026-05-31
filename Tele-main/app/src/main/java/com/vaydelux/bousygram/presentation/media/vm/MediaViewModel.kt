package com.vaydelux.bousygram.presentation.media.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaydelux.bousygram.core.media.MediaDurationResolver
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.store.PlaybackMode
import com.vaydelux.bousygram.data.repository.SavedMessagesRepository
import com.vaydelux.bousygram.data.repository.findSidecarSubtitle
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaUiState
import com.vaydelux.bousygram.domain.model.SAVED_MESSAGES_TITLE
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

class MediaViewModel(
    private val repository: SavedMessagesRepository = SavedMessagesRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState
    private val pageSize = 30
    private var isInitialized = false

    private fun isMessageInSelectedChat(chatId: Long): Boolean {
        val state = _uiState.value
        return if (state.isSavedMessagesSelected) {
            repository.cachedSavedMessagesChatId != null && chatId == repository.cachedSavedMessagesChatId
        } else {
            chatId == state.selectedChatId
        }
    }

    private val newMessageHandler: (TdApi.UpdateNewMessage) -> Unit = { update ->
        val message = update.message
        val content = message.content
        if (content is TdApi.MessageVideo ||
            content is TdApi.MessageVideoNote ||
            content is TdApi.MessageDocument ||
            content is TdApi.MessageAnimation) {
            if (isMessageInSelectedChat(message.chatId)) {
                repository.getMessage(message.chatId, message.id) { item, _ ->
                    item?.let { newItem ->
                        _uiState.update { current ->
                            val exists = current.items.any { it.messageId == newItem.messageId }
                            if (exists) current
                            else current.copy(items = listOf(newItem) + current.items)
                        }
                        fetchThumbnails(listOf(newItem))
                    }
                }
            }
        }
    }

    private val deleteMessageHandler: (TdApi.UpdateDeleteMessages) -> Unit = { update ->
        if (update.isPermanent && isMessageInSelectedChat(update.chatId)) {
            _uiState.update { current ->
                current.copy(items = current.items.filterNot { update.messageIds.contains(it.messageId) })
            }
        }
    }

    private var chatUpdateJob: Job? = null

    private val chatUpdateHandler: (TdApi.Object?) -> Unit = { update ->
        when (update) {
            is TdApi.UpdateNewChat,
            is TdApi.UpdateChatAddedToList,
            is TdApi.UpdateChatRemovedFromList,
            is TdApi.UpdateChatPosition -> {
                chatUpdateJob?.cancel()
                chatUpdateJob = viewModelScope.launch {
                    delay(500)
                    loadVideoChats()
                }
            }
        }
    }

    init {
        TdLibClient.addNewMessageHandler(newMessageHandler)
        TdLibClient.addDeleteMessageHandler(deleteMessageHandler)
        TdLibClient.addUpdateHandler(chatUpdateHandler)
    }

    fun initialize() {
        if (isInitialized) return
        isInitialized = true
        refresh()
    }

    override fun onCleared() {
        TdLibClient.removeNewMessageHandler(newMessageHandler)
        TdLibClient.removeDeleteMessageHandler(deleteMessageHandler)
        TdLibClient.removeUpdateHandler(chatUpdateHandler)
        repository.shutdown()
        super.onCleared()
    }

    fun refresh() {
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                selectedChatTitle = SAVED_MESSAGES_TITLE,
                selectedChatId = null,
                isSavedMessagesSelected = true,
                hasMore = true,
                items = emptyList(),
                nextFromMessageId = 0L
            )
        }
        repository.loadLatestMediaPaged(pageSize, null) { items, nextFromMessageId, error ->
            _uiState.update { current ->
                current.copy(
                    items = items,
                    isLoading = false,
                    error = error,
                    hasMore = nextFromMessageId != 0L,
                    nextFromMessageId = nextFromMessageId
                )
            }
            fetchThumbnails(items)
            loadVideoChats()
        }
    }

    fun loadIfNeeded() {
        if (_uiState.value.isLoading) return
        if (_uiState.value.items.isEmpty()) {
            refresh()
        }
    }

    fun load() {
        _uiState.update {
            it.copy(
                isSavedMessagesSelected = true,
                selectedChatId = null,
                selectedChatTitle = SAVED_MESSAGES_TITLE
            )
        }
        loadVideoChats()
        refresh()
    }

    fun loadChat(chatId: Long, title: String) {
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                selectedChatTitle = title,
                selectedChatId = chatId,
                isSavedMessagesSelected = false,
                hasMore = true,
                items = emptyList(),
                nextFromMessageId = 0L
            )
        }
        loadVideoChats()
        repository.loadChatMediaPaged(chatId, pageSize, null) { items, nextFromMessageId, error ->
            _uiState.update { current ->
                current.copy(
                    items = items,
                    isLoading = false,
                    error = error,
                    hasMore = nextFromMessageId != 0L,
                    nextFromMessageId = nextFromMessageId
                )
            }
            fetchThumbnails(items)
        }
    }

    fun loadMoreIfNeeded() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore || state.items.isEmpty()) return
        val nextFromMessageId = state.nextFromMessageId
        if (nextFromMessageId == 0L) {
            _uiState.update { it.copy(hasMore = false, isLoadingMore = false) }
            return
        }
        _uiState.update { it.copy(isLoadingMore = true) }
        if (state.isSavedMessagesSelected) {
            repository.loadLatestMediaPaged(pageSize, nextFromMessageId) { items, nextFromId, error ->
                val current = _uiState.value
                if (!current.isLoadingMore) return@loadLatestMediaPaged
                handleLoadMore(items, nextFromId, error)
            }
        } else {
            val chatId = state.selectedChatId ?: return
            repository.loadChatMediaPaged(chatId, pageSize, nextFromMessageId) { items, nextFromId, error ->
                val current = _uiState.value
                if (!current.isLoadingMore) return@loadChatMediaPaged
                handleLoadMore(items, nextFromId, error)
            }
        }
    }

    private fun handleLoadMore(items: List<MediaItem>, nextFromMessageId: Long, error: String?) {
        _uiState.update { current ->
            val merged = (current.items + items).distinctBy { it.messageId }
            current.copy(
                items = merged,
                isLoadingMore = false,
                error = error,
                hasMore = nextFromMessageId != 0L,
                nextFromMessageId = nextFromMessageId
            )
        }
        if (items.isNotEmpty()) {
            fetchThumbnails(items)
        }
    }

    private fun fetchThumbnails(items: List<MediaItem>) {
        items
            .asSequence()
            .filter { it.thumbnailPath.isNullOrBlank() }
            .forEach { item ->
                val thumbnailFileId = item.thumbnailFileId ?: return@forEach
                repository.fetchThumbnailPath(thumbnailFileId) { path ->
                    if (!path.isNullOrBlank()) {
                        updateThumbnailPath(item.messageId, path)
                    }
                }
            }
    }

    fun loadVideoChatsIfNeeded() {
        if (_uiState.value.isSidebarLoading) return
        loadVideoChats()
    }

    fun refreshVideoChats() {
        loadVideoChats()
    }

    private fun loadVideoChats() {
        val selectedId = if (_uiState.value.isSavedMessagesSelected) repository.cachedSavedMessagesChatId else _uiState.value.selectedChatId
        _uiState.update { it.copy(isSidebarLoading = true, sidebarError = null) }
        repository.loadVideoChats(15, selectedId) { chats, error ->
            _uiState.update { current ->
                current.copy(
                    videoChats = chats,
                    isSidebarLoading = false,
                    sidebarError = error
                )
            }
        }
    }

    fun requestFastLink(item: MediaItem, onResult: (String?, String?) -> Unit) {
        val fileId = item.fileId
        _uiState.update { it.copy(preparingFileId = fileId) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                var output: Pair<String?, String?> = null to "Could not prepare stream"
                repository.requestFastLink(item) { url, error ->
                    output = url to error
                }
                output
            }
            val url = result.first
            _uiState.update { it.copy(preparingFileId = null) }
            onResult(url, result.second)
            if (!url.isNullOrBlank() && fileId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    val estimate = MediaDurationResolver.resolveDurationEstimate(
                        fileId = fileId,
                        totalSize = item.fileSizeBytes,
                        streamUrl = url,
                        hintSeconds = item.durationSeconds
                    )
                    if (estimate.trusted) {
                        updateTrustedDuration(fileId, estimate.durationMs)
                    }
                }
            }
        }
    }

    fun findSidecarSubtitle(item: MediaItem, onResult: (Int?, String?) -> Unit) {
        repository.findSidecarSubtitle(item.chatId, item.title, onResult)
    }

    fun deleteMediaItem(item: MediaItem, onResult: (String?) -> Unit) {
        _uiState.update { it.copy(error = null) }
        repository.deleteMessage(item.chatId, item.messageId) { error ->
            if (error != null) {
                _uiState.update { current -> current.copy(error = error) }
                onResult(error)
            } else {
                _uiState.update { current ->
                    current.copy(items = current.items.filterNot { it.messageId == item.messageId })
                }
                onResult(null)
            }
        }
    }

    fun setSortOrder(order: com.vaydelux.bousygram.domain.model.SortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
    }

    fun setFilter(year: Int?, month: Int?) {
        _uiState.update { it.copy(filterYear = year, filterMonth = month) }
    }

    fun onItemFocused(item: MediaItem) {
        item.fileId?.let { fileId ->
            MediaDurationResolver.cachedDurationMs(fileId)
                .takeIf { it > 0L && it != item.trustedDurationMs }
                ?.let { updateTrustedDuration(fileId, it) }
            if (EnterprisePreferences.playbackMode() != PlaybackMode.FastLink) {
                viewModelScope.launch(Dispatchers.IO) {
                    val size = item.fileSizeBytes.takeIf { it > 0L }
                        ?: LocalStreamingServer.resolveFileSize(fileId)
                    if (size > 0L) {
                        LocalStreamingServer.warmUp(fileId, size)
                    }
                }
            }
        }
        val fileId = item.thumbnailFileId
        if (fileId != null && item.thumbnailPath.isNullOrBlank()) {
            repository.fetchThumbnailPath(fileId) { path ->
                if (!path.isNullOrBlank()) {
                    updateThumbnailPath(item.messageId, path)
                }
            }
        }
    }

    private fun updateThumbnailPath(messageId: Long, path: String) {
        _uiState.update { current ->
            current.copy(
                items = current.items.map { item ->
                    if (item.messageId == messageId) item.copy(thumbnailPath = path) else item
                }
            )
        }
    }

    private fun updateTrustedDuration(fileId: Int, durationMs: Long) {
        if (durationMs <= 0L) return
        _uiState.update { current ->
            current.copy(
                items = current.items.map { item ->
                    if (item.fileId == fileId) item.copy(trustedDurationMs = durationMs) else item
                }
            )
        }
    }
}
