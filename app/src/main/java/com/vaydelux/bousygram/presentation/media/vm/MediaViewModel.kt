package com.vaydelux.bousygram.presentation.media.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaydelux.bousygram.core.media.MediaDurationResolver
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.repository.SavedMessagesRepository
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaUiState
import com.vaydelux.bousygram.domain.model.SAVED_MESSAGES_TITLE
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class MediaViewModel(
    private val repository: SavedMessagesRepository = SavedMessagesRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState
    private val pageSize = 30
    private var isInitialized = false
    private var hasLoadedVideoChats = false
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
    private var deferredChatSourceLoadJob: Job? = null
    private val requestedThumbnailFileIds =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Int, Boolean>())

    private val chatUpdateHandler: (TdApi.Object?) -> Unit = { update ->
        when (update) {
            is TdApi.UpdateNewChat,
            is TdApi.UpdateChatAddedToList,
            is TdApi.UpdateChatRemovedFromList,
            is TdApi.UpdateChatPosition -> {
                chatUpdateJob?.cancel()
                chatUpdateJob = viewModelScope.launch {
                    delay(500)
                    loadVideoChats(force = true)
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
        chatUpdateJob?.cancel()
        chatUpdateJob = null
        deferredChatSourceLoadJob?.cancel()
        deferredChatSourceLoadJob = null
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
        repository.loadLatestMediaPagedProgressive(pageSize, null) { items, nextFromMessageId, error, isFinal ->
            if (requestGeneration != mediaRequestGeneration) return@loadLatestMediaPagedProgressive
            _uiState.update { current ->
                current.copy(
                    items = items,
                    isLoading = !isFinal,
                    error = error,
                    hasMore = isFinal && nextFromMessageId != 0L,
                    nextFromMessageId = if (isFinal) nextFromMessageId else 0L
                )
            }
            fetchThumbnails(items)
            scheduleChatSourceLoad()
        }
    }

    fun loadIfNeeded() {
        if (_uiState.value.isLoading) return
        if (_uiState.value.items.isEmpty()) {
            refresh()
        }
    }

    fun load() {
        _uiState.update { current ->
            current.copy(
                isSavedMessagesSelected = true,
                selectedChatId = null,
                selectedChatTitle = SAVED_MESSAGES_TITLE,
                availableTopics = emptyList(),
                selectedTopicId = null,
                isLoadingTopics = false,
                videoChats = current.videoChats.map { it.copy(isSelected = it.isSavedMessages) }
            )
        }
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
                availableTopics = emptyList(),
                selectedTopicId = null,
                isLoadingTopics = false,
                hasMore = true,
                isLoadingMore = false,
                items = emptyList(),
                nextFromMessageId = 0L,
                videoChats = it.videoChats.map { source -> source.copy(isSelected = !source.isSavedMessages && source.chatId == chatId) }
            )
        }
        loadForumTopics(chatId)
        repository.loadChatMediaPagedProgressive(chatId, pageSize, null) { items, nextFromMessageId, error, isFinal ->
            if (requestGeneration != mediaRequestGeneration) return@loadChatMediaPagedProgressive
            _uiState.update { current ->
                current.copy(
                    items = items,
                    isLoading = !isFinal,
                    error = error,
                    hasMore = isFinal && nextFromMessageId != 0L,
                    nextFromMessageId = if (isFinal) nextFromMessageId else 0L
                )
            }
            fetchThumbnails(items)
            scheduleChatSourceLoad()
        }
    }

    fun loadForumTopics(chatId: Long) {
        _uiState.update { it.copy(isLoadingTopics = true) }
        repository.loadForumTopics(chatId) { topics, _ ->
            _uiState.update { it.copy(availableTopics = topics, isLoadingTopics = false) }
        }
    }

    fun selectTopic(topicId: Int?) {
        val state = _uiState.value
        val chatId = state.selectedChatId ?: return
        if (state.selectedTopicId == topicId) return
        val requestGeneration = ++mediaRequestGeneration
        _uiState.update {
            it.copy(
                selectedTopicId = topicId,
                isLoading = true,
                error = null,
                hasMore = true,
                isLoadingMore = false,
                items = emptyList(),
                nextFromMessageId = 0L
            )
        }
        repository.loadChatMediaPagedProgressive(chatId, pageSize, null, topicId = topicId) { items, nextFromMessageId, error, isFinal ->
            if (requestGeneration != mediaRequestGeneration) return@loadChatMediaPagedProgressive
            _uiState.update { current ->
                current.copy(
                    items = items,
                    isLoading = !isFinal,
                    error = error,
                    hasMore = isFinal && nextFromMessageId != 0L,
                    nextFromMessageId = if (isFinal) nextFromMessageId else 0L
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
            repository.loadLatestMediaPagedProgressive(pageSize, nextFromMessageId) { items, nextFromId, error, isFinal ->
                val current = _uiState.value
                if (requestGeneration != mediaRequestGeneration || !current.isLoadingMore) return@loadLatestMediaPagedProgressive
                handleLoadMore(items, nextFromId, error, isFinal)
            }
        } else {
            val chatId = state.selectedChatId ?: return
            repository.loadChatMediaPagedProgressive(chatId, pageSize, nextFromMessageId, topicId = state.selectedTopicId) { items, nextFromId, error, isFinal ->
                val current = _uiState.value
                if (requestGeneration != mediaRequestGeneration || !current.isLoadingMore) return@loadChatMediaPagedProgressive
                handleLoadMore(items, nextFromId, error, isFinal)
            }
        }
    }

    private fun handleLoadMore(
        items: List<MediaItem>,
        nextFromMessageId: Long,
        error: String?,
        isFinal: Boolean
    ) {
        _uiState.update { current ->
            val merged = (current.items + items)
                .distinctBy { "${it.chatId}:${it.messageId}" }
            current.copy(
                items = merged,
                isLoadingMore = !isFinal,
                error = error,
                hasMore = if (isFinal) nextFromMessageId != 0L else current.hasMore,
                nextFromMessageId = if (isFinal) nextFromMessageId else current.nextFromMessageId
            )
        }
        if (items.isNotEmpty()) fetchThumbnails(items)
    }

    private fun fetchThumbnails(items: List<MediaItem>) {
        items.asSequence()
            .filter { it.thumbnailPath.isNullOrBlank() }
            .forEach { item ->
                val thumbnailFileId = item.thumbnailFileId ?: return@forEach
                // Progressive filter results can include the same media item more than once.
                // De-duplicate requests in the ViewModel while the repository applies a global
                // concurrency limit across all screens.
                if (!requestedThumbnailFileIds.add(thumbnailFileId)) return@forEach
                repository.fetchThumbnailPath(thumbnailFileId) { path ->
                    if (!path.isNullOrBlank()) {
                        updateThumbnailPathForFile(thumbnailFileId, path)
                    } else {
                        requestedThumbnailFileIds.remove(thumbnailFileId)
                    }
                }
            }
    }

    /**
     * Source-picker metadata is useful, but loading thousands of chats can compete with media
     * search in TDLib. Let the first media queries start and settle briefly before the source
     * catalog begins; it still loads all main/archive chat types afterwards.
     */
    private fun scheduleChatSourceLoad() {
        if (hasLoadedVideoChats || _uiState.value.isSidebarLoading || deferredChatSourceLoadJob?.isActive == true) return
        deferredChatSourceLoadJob = viewModelScope.launch {
            delay(CHAT_SOURCE_LOAD_DELAY_MS)
            // Keep media discovery ahead of the much broader chat-catalog scan. On large
            // accounts, LoadChats/GetChat across every source can generate far more TDLib work
            // than the first visible page needs. Do not wait forever if the current source has
            // no indexed videos (the repository may be running its bounded history fallback).
            var priorityWaitRemaining = MEDIA_FIRST_PAGE_PRIORITY_MAX_WAIT_MS
            while (_uiState.value.isLoading && priorityWaitRemaining > 0L) {
                val waitSlice = minOf(MEDIA_FIRST_PAGE_PRIORITY_POLL_MS, priorityWaitRemaining)
                delay(waitSlice)
                priorityWaitRemaining -= waitSlice
            }
            deferredChatSourceLoadJob = null
            loadVideoChatsIfNeeded()
        }
    }

    fun loadVideoChatsIfNeeded() {
        if (_uiState.value.isSidebarLoading || hasLoadedVideoChats) return
        loadVideoChats()
    }

    fun refreshVideoChats() {
        loadVideoChats(force = true)
    }

    private fun loadVideoChats(force: Boolean = false) {
        if (!force && (hasLoadedVideoChats || _uiState.value.isSidebarLoading)) return
        val selectedId = if (_uiState.value.isSavedMessagesSelected) repository.cachedSavedMessagesChatId else _uiState.value.selectedChatId
        _uiState.update { it.copy(isSidebarLoading = true, sidebarError = null) }
        repository.loadVideoChats(10_000, selectedId) { chats, error ->
            hasLoadedVideoChats = true
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

    fun preparePlayback(item: MediaItem, onResult: (String?, String?) -> Unit) {
        val fileId = item.fileId
        _uiState.update { it.copy(preparingFileId = fileId) }
        viewModelScope.launch {
            val result = runCatching { repository.prepareStream(item).getOrThrow() }
            val url = result.getOrNull()
            _uiState.update { it.copy(preparingFileId = null) }
            onResult(url, result.exceptionOrNull()?.message)
        }
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

    fun setCategoryTab(tab: com.vaydelux.bousygram.domain.model.MediaCategoryTab) {
        _uiState.update { it.copy(selectedCategoryTab = tab, selectedSeriesTitle = null) }
    }

    fun selectSeries(seriesTitle: String?) {
        _uiState.update { it.copy(selectedSeriesTitle = seriesTitle) }
    }

    fun onItemFocused(item: MediaItem) {
        // Browsing must remain metadata-first. Do not download the first 2 MiB of every video
        // when a TV remote merely moves focus across a card. ExoPlayer/local streaming primes
        // the selected video only after the user opens it; here we may request just its thumbnail.
        item.fileId?.let { fileId ->
            MediaDurationResolver.cachedDurationMs(fileId)
                .takeIf { it > 0L && it != item.trustedDurationMs }
                ?.let { updateTrustedDuration(fileId, it) }
        }
        val thumbnailFileId = item.thumbnailFileId
        if (thumbnailFileId != null && item.thumbnailPath.isNullOrBlank() && requestedThumbnailFileIds.add(thumbnailFileId)) {
            repository.fetchThumbnailPath(thumbnailFileId) { path ->
                if (!path.isNullOrBlank()) updateThumbnailPathForFile(thumbnailFileId, path)
                else requestedThumbnailFileIds.remove(thumbnailFileId)
            }
        }
    }

    private fun updateThumbnailPathForFile(thumbnailFileId: Int, path: String) {
        _uiState.update { current ->
            current.copy(
                items = current.items.map { item ->
                    if (item.thumbnailFileId == thumbnailFileId) item.copy(thumbnailPath = path) else item
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

private const val CHAT_SOURCE_LOAD_DELAY_MS = 300L
private const val MEDIA_FIRST_PAGE_PRIORITY_POLL_MS = 150L
private const val MEDIA_FIRST_PAGE_PRIORITY_MAX_WAIT_MS = 3_000L
