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
    private var mediaRequestGeneration = 0L

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
                val generationAtRequest = mediaRequestGeneration
                repository.getMessage(message.chatId, message.id) { item, _ ->
                    if (generationAtRequest == mediaRequestGeneration && isMessageInSelectedChat(message.chatId)) {
                        item?.let { newItem ->
                            _uiState.update { current ->
                                val exists = current.items.any {
                                    it.chatId == newItem.chatId && it.messageId == newItem.messageId
                                }
                                if (exists) current
                                else current.copy(items = listOf(newItem) + current.items)
                            }
                            fetchThumbnails(listOf(newItem))
                        }
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
    private var focusWarmupJob: Job? = null
    @Volatile private var focusWarmupGeneration = 0L

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
        focusWarmupGeneration++
        focusWarmupJob?.cancel()
        focusWarmupJob = null
        chatUpdateJob?.cancel()
        chatUpdateJob = null
        TdLibClient.removeNewMessageHandler(newMessageHandler)
        TdLibClient.removeDeleteMessageHandler(deleteMessageHandler)
        TdLibClient.removeUpdateHandler(chatUpdateHandler)
        repository.shutdown()
        super.onCleared()
    }

    fun refresh() {
        val selected = _uiState.value
        if (!selected.isSavedMessagesSelected && selected.selectedChatId != null) {
            loadChat(selected.selectedChatId, selected.selectedChatTitle)
            return
        }
        val requestGeneration = ++mediaRequestGeneration
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                selectedChatTitle = SAVED_MESSAGES_TITLE,
                selectedChatId = null,
                isSavedMessagesSelected = true,
                hasMore = true,
                isLoadingMore = false,
                items = emptyList(),
                nextFromMessageId = 0L
            )
        }
        repository.loadLatestMediaPaged(pageSize, null) { items, nextFromMessageId, error ->
            if (requestGeneration != mediaRequestGeneration) return@loadLatestMediaPaged
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
        val requestGeneration = ++mediaRequestGeneration
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                selectedChatTitle = title,
                selectedChatId = chatId,
                isSavedMessagesSelected = false,
                hasMore = true,
                isLoadingMore = false,
                items = emptyList(),
                nextFromMessageId = 0L
            )
        }
        loadVideoChats()
        repository.loadChatMediaPaged(chatId, pageSize, null) { items, nextFromMessageId, error ->
            if (requestGeneration != mediaRequestGeneration) return@loadChatMediaPaged
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
        val requestGeneration = mediaRequestGeneration
        if (state.isSavedMessagesSelected) {
            repository.loadLatestMediaPaged(pageSize, nextFromMessageId) { items, nextFromId, error ->
                val current = _uiState.value
                if (requestGeneration != mediaRequestGeneration || !current.isLoadingMore) return@loadLatestMediaPaged
                handleLoadMore(items, nextFromId, error)
            }
        } else {
            val chatId = state.selectedChatId ?: return
            repository.loadChatMediaPaged(chatId, pageSize, nextFromMessageId) { items, nextFromId, error ->
                val current = _uiState.value
                if (requestGeneration != mediaRequestGeneration || !current.isLoadingMore) return@loadChatMediaPaged
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

    /** Resolves the saved last-played Telegram message even when it is outside the loaded page. */
    fun loadLastPlayed(onResult: (MediaItem?, String?) -> Unit) {
        val last = EnterprisePreferences.lastPlayed()
        if (last == null) {
            onResult(null, "There is no saved playback position yet")
            return
        }
        repository.getMessage(last.chatId, last.messageId) { item, error ->
            if (item == null) {
                onResult(null, error ?: "The last-played Telegram message is no longer available")
            } else if (item.fileId == null) {
                onResult(null, "The last-played message no longer contains a playable media file")
            } else {
                onResult(item, null)
            }
        }
    }

    fun requestFastLink(item: MediaItem, onResult: (String?, String?) -> Unit) {
        val fileId = item.fileId
        _uiState.update { it.copy(preparingFileId = fileId) }
        viewModelScope.launch {
            val result = runCatching { repository.prepareStream(item).getOrThrow() }
            val url = result.getOrNull()
            _uiState.update { it.copy(preparingFileId = null) }
            onResult(url, result.exceptionOrNull()?.message)
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
        val warmupGeneration = ++focusWarmupGeneration
        item.fileId?.let { fileId ->
            MediaDurationResolver.cachedDurationMs(fileId)
                .takeIf { it > 0L && it != item.trustedDurationMs }
                ?.let { updateTrustedDuration(fileId, it) }
            if (EnterprisePreferences.playbackMode() != PlaybackMode.FastLink) {
                // Remote browsing often moves focus across many cards per second. Debounce
                // warm-up so we prefetch only after the user pauses on a candidate item.
                focusWarmupJob?.cancel()
                focusWarmupJob = viewModelScope.launch {
                    delay(FOCUS_PREFETCH_DEBOUNCE_MS)
                    if (warmupGeneration != focusWarmupGeneration ||
                        EnterprisePreferences.playbackMode() == PlaybackMode.FastLink
                    ) return@launch
                    val size = withContext(Dispatchers.IO) {
                        item.fileSizeBytes.takeIf { it > 0L }
                            ?: LocalStreamingServer.resolveFileSize(fileId)
                    }
                    // The size lookup may block briefly; check again so stale focus never
                    // starts prefetch after the user has already moved elsewhere.
                    if (warmupGeneration != focusWarmupGeneration) return@launch
                    if (size > 0L) {
                        withContext(Dispatchers.IO) {
                            if (warmupGeneration == focusWarmupGeneration) {
                                LocalStreamingServer.warmUp(fileId, size)
                            }
                        }
                    }
                }
            } else {
                focusWarmupJob?.cancel()
                focusWarmupJob = null
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

    private companion object {
        const val FOCUS_PREFETCH_DEBOUNCE_MS = 450L
    }
}
