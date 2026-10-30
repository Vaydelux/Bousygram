package com.vaydelux.bousygram.presentation.media.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaydelux.bousygram.core.media.MediaDurationResolver
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.repository.SavedMessagesRepository
import com.vaydelux.bousygram.data.repository.searchChatVideos
import com.vaydelux.bousygram.presentation.media.stableChatCatalog
import com.vaydelux.bousygram.data.repository.searchGlobalVideos
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.VideoChatItem
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.domain.model.MediaUiState
import com.vaydelux.bousygram.domain.model.SAVED_MESSAGES_TITLE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

import com.vaydelux.bousygram.data.database.RecentSearchQuery
import com.vaydelux.bousygram.data.database.RecentSearchRepository
import com.vaydelux.bousygram.data.database.VideoIndexRepository

data class BrowseRestoration(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
    val focusedMediaKey: String? = null,
    val sectionKey: String? = null
)

/** Persistent explorer position owned by the existing activity-scoped MediaViewModel. */
enum class ExplorerSection { SAVED, CHANNELS, GROUPS }
enum class ExplorerLevel { SOURCES, TOPICS, MEDIA }

data class ExplorerPosition(
    val section: ExplorerSection = ExplorerSection.SAVED,
    val level: ExplorerLevel = ExplorerLevel.MEDIA
)

class MediaViewModel(
    private val repository: SavedMessagesRepository = SavedMessagesRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(MediaUiState())
    private val _explorerPosition = MutableStateFlow(ExplorerPosition())
    val explorerPosition: StateFlow<ExplorerPosition> = _explorerPosition
    private var initializedAccount: AccountSnapshot? = null

    /** Bounded, account-generation-scoped metadata only. TDLib manages media bytes. */
    private data class CatalogSnapshot(
        val items: List<MediaItem>,
        val nextFromMessageId: Long,
        val hasMore: Boolean,
        val savedAtMs: Long = android.os.SystemClock.elapsedRealtime()
    )
    private val catalogSnapshots = LinkedHashMap<String, CatalogSnapshot>(12, 0.75f, true)
    private val catalogSnapshotLock = Any()
    private data class TopicSnapshot(
        val topics: List<com.vaydelux.bousygram.domain.model.ForumTopicChannel>,
        val savedAtMs: Long
    )
    private val topicSnapshots = LinkedHashMap<String, TopicSnapshot>()
    private val freshnessWindowMs = 120_000L

    private fun snapshotKey(folder: String): String? = AccountSession.current()?.let { owner ->
        owner.accountId.toString() + ":" + owner.generation + ":" + folder
    }

    private fun catalogSnapshot(folder: String): CatalogSnapshot? {
        val key = snapshotKey(folder) ?: return null
        return synchronized(catalogSnapshotLock) { catalogSnapshots[key] }
    }

    private fun freshCatalog(folder: String): CatalogSnapshot? = catalogSnapshot(folder)?.takeIf {
        android.os.SystemClock.elapsedRealtime() - it.savedAtMs in 0..freshnessWindowMs
    }

    private fun rememberCatalog(folder: String, videos: List<MediaItem>, nextCursor: Long) {
        if (videos.isEmpty()) return
        val key = snapshotKey(folder) ?: return
        synchronized(catalogSnapshotLock) {
            // Bounded first-page metadata, never video bytes, stream URLs, or credentials.
            catalogSnapshots[key] = CatalogSnapshot(
                items = videos.take(120),
                nextFromMessageId = nextCursor,
                hasMore = nextCursor != 0L
            )
            while (catalogSnapshots.size > 8) {
                catalogSnapshots.remove(catalogSnapshots.keys.first())
            }
        }
    }

    private fun invalidateCatalogForChat(chatId: Long) {
        synchronized(catalogSnapshotLock) {
            val sourceFragment = ":chat:" + chatId + ":"
            catalogSnapshots.keys.removeAll { it.contains(sourceFragment) }
            topicSnapshots.keys.removeAll { it.contains(sourceFragment) }
            if (repository.cachedSavedMessagesChatId == chatId) {
                catalogSnapshots.keys.removeAll { it.endsWith(":saved:topic:0") }
            }
        }
    }

    private fun freshTopics(chatId: Long): List<com.vaydelux.bousygram.domain.model.ForumTopicChannel>? {
        val key = snapshotKey("chat:" + chatId + ":topics") ?: return null
        return synchronized(catalogSnapshotLock) {
            topicSnapshots[key]?.takeIf {
                android.os.SystemClock.elapsedRealtime() - it.savedAtMs in 0..freshnessWindowMs
            }?.topics
        }
    }

    val uiState: StateFlow<MediaUiState> = _uiState
    val recentSearches: StateFlow<List<RecentSearchQuery>> = RecentSearchRepository.recentSearches
    private val pageSize = 30
    private var isInitialized = false
    private var hasLoadedVideoChats = false
    private var mediaRequestGeneration = 0L
    private val browseRestorations = java.util.concurrent.ConcurrentHashMap<String, BrowseRestoration>()
    private val sourceFolderRestorations = java.util.concurrent.ConcurrentHashMap<ExplorerSection, BrowseRestoration>()

    fun sourceFolderRestoration(section: ExplorerSection): BrowseRestoration =
        sourceFolderRestorations[section] ?: BrowseRestoration()

    fun rememberSourceFolderFocus(section: ExplorerSection, chatId: Long) {
        sourceFolderRestorations.compute(section) { _, current ->
            (current ?: BrowseRestoration()).copy(focusedMediaKey = chatId.toString())
        }
    }

    fun rememberSourceFolderViewport(section: ExplorerSection, index: Int, offset: Int) {
        sourceFolderRestorations.compute(section) { _, current ->
            (current ?: BrowseRestoration()).copy(
                firstVisibleItemIndex = index.coerceAtLeast(0),
                firstVisibleItemScrollOffset = offset.coerceAtLeast(0)
            )
        }
    }

    fun currentBrowseStateKey(): String {
        val state = _uiState.value
        val source = if (state.isSavedMessagesSelected) "saved" else "chat:${state.selectedChatId ?: 0L}"
        val route = _explorerPosition.value
        return "${route.section}:${route.level}:$source:topic:${state.selectedTopicId ?: 0}"
    }

    fun browseRestoration(key: String = currentBrowseStateKey()): BrowseRestoration =
        browseRestorations[key] ?: BrowseRestoration()

    fun saveBrowseViewport(key: String, firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        browseRestorations.compute(key) { _, current ->
            (current ?: BrowseRestoration()).copy(
                firstVisibleItemIndex = firstVisibleItemIndex.coerceAtLeast(0),
                firstVisibleItemScrollOffset = firstVisibleItemScrollOffset.coerceAtLeast(0)
            )
        }
    }

    fun recordBrowseFocus(item: MediaItem, sectionKey: String? = null) {
        val stateKey = currentBrowseStateKey()
        val mediaKey = "${item.chatId}:${item.messageId}"
        browseRestorations.compute(stateKey) { _, current ->
            (current ?: BrowseRestoration()).copy(
                focusedMediaKey = mediaKey,
                sectionKey = sectionKey ?: current?.sectionKey
            )
        }
    }


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
                            invalidateCatalogForChat(message.chatId)
                            _uiState.update { current ->
                                val exists = current.items.any {
                                    it.chatId == newItem.chatId && it.messageId == newItem.messageId
                                }
                                if (exists) current
                                else current.copy(items = listOf(newItem) + current.items)
                            }
                            fetchThumbnails(listOf(newItem))
                            com.vaydelux.bousygram.data.database.VideoIndexRepository.indexMediaItemsAsync(listOf(newItem))
                            enrichMediaItemsAsync(listOf(newItem))
                        }
                    }
                }
            }
        }
    }

    private val deleteMessageHandler: (TdApi.UpdateDeleteMessages) -> Unit = { update ->
        if (update.isPermanent) {
            invalidateCatalogForChat(update.chatId)
        }
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
            is TdApi.UpdateMessageContent -> {
                invalidateCatalogForChat(update.chatId)
            }
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
        viewModelScope.launch {
            AccountSession.state.collect { owner ->
                if (owner == null && initializedAccount != null) {
                    // The Activity-scoped ViewModel outlives the auth screen.
                    // Clear media, source directories and focus when an account logs out.
                    ++mediaRequestGeneration
                    _uiState.value = MediaUiState()
                    _explorerPosition.value = ExplorerPosition()
                    browseRestorations.clear()
                    sourceFolderRestorations.clear()
                    synchronized(catalogSnapshotLock) {
                        catalogSnapshots.clear()
                        topicSnapshots.clear()
                    }
                    hasLoadedVideoChats = false
                    requestedThumbnailFileIds.clear()
                    enrichedMediaKeys.clear()
                    initializedAccount = null
                    isInitialized = false
                }
            }
        }
        TdLibClient.addNewMessageHandler(newMessageHandler)
        TdLibClient.addDeleteMessageHandler(deleteMessageHandler)
        TdLibClient.addUpdateHandler(chatUpdateHandler)
    }

    fun initialize() {
        val current = AccountSession.current()
        if (current != initializedAccount) {
            // The Activity ViewModel outlives the auth route. Never publish another
            // Telegram identity's cached catalog, UI focus or selected folder.
            ++mediaRequestGeneration
            _uiState.value = MediaUiState()
            _explorerPosition.value = ExplorerPosition()
            browseRestorations.clear()
            sourceFolderRestorations.clear()
            synchronized(catalogSnapshotLock) {
                catalogSnapshots.clear()
                topicSnapshots.clear()
            }
            hasLoadedVideoChats = false
            isInitialized = false
            initializedAccount = current
        }
        if (isInitialized) return
        isInitialized = true
        refresh()
    }

    fun selectExplorerSection(section: ExplorerSection) {
        _explorerPosition.value = ExplorerPosition(
            section = section,
            level = if (section == ExplorerSection.SAVED) ExplorerLevel.MEDIA else ExplorerLevel.SOURCES
        )
        if (section == ExplorerSection.SAVED) {
            val snapshot = freshCatalog("saved:topic:0")
            if (snapshot != null) {
                // Returning to Saved should not refetch the same page on every tab switch.
                ++mediaRequestGeneration
                _uiState.update { current ->
                    current.copy(
                        items = snapshot.items,
                        selectedChatId = null,
                        selectedChatTitle = SAVED_MESSAGES_TITLE,
                        isSavedMessagesSelected = true,
                        selectedTopicId = null,
                        availableTopics = emptyList(),
                        quickSearchQuery = "",
                        isLoading = false,
                        isLoadingMore = false,
                        error = null,
                        hasMore = snapshot.hasMore,
                        nextFromMessageId = snapshot.nextFromMessageId,
                        videoChats = current.videoChats.map { it.copy(isSelected = it.isSavedMessages) }
                    )
                }
            } else if (!_uiState.value.isSavedMessagesSelected) load()
            else loadIfNeeded()
        } else loadVideoChatsIfNeeded()
    }

    fun enterExplorerSource(source: VideoChatItem) {
        if (source.isSavedMessages) {
            selectExplorerSection(ExplorerSection.SAVED)
            return
        }
        _explorerPosition.value = _explorerPosition.value.copy(
            level = if (source.isForum) ExplorerLevel.TOPICS else ExplorerLevel.MEDIA
        )
        val currentState = _uiState.value
        if (currentState.selectedChatId != source.chatId || currentState.isSavedMessagesSelected) {
            if (source.isForum) {
                // A forum source is a directory first. Do not fetch *all* media for
                // every topic before the user selects the real Telegram topic.
                if (AccountSession.current() == null) return
                ++mediaRequestGeneration
                _uiState.update { current ->
                    current.copy(
                        items = emptyList(),
                        selectedChatId = source.chatId,
                        selectedChatTitle = source.title,
                        isSavedMessagesSelected = false,
                        selectedTopicId = null,
                        availableTopics = emptyList(),
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = false,
                        nextFromMessageId = 0L,
                        quickSearchQuery = "",
                        error = null,
                        videoChats = current.videoChats.map {
                            it.copy(isSelected = !it.isSavedMessages && it.chatId == source.chatId)
                        }
                    )
                }
                loadForumTopics(source.chatId)
            } else {
                val snapshot = freshCatalog("chat:" + source.chatId + ":topic:0")
                if (snapshot == null) loadChat(source.chatId, source.title)
                else {
                ++mediaRequestGeneration
                _uiState.update { current ->
                    current.copy(
                        items = snapshot.items,
                        selectedChatId = source.chatId,
                        selectedChatTitle = source.title,
                        isSavedMessagesSelected = false,
                        selectedTopicId = null,
                        availableTopics = emptyList(),
                        quickSearchQuery = "",
                        isLoading = false,
                        isLoadingMore = false,
                        error = null,
                        hasMore = snapshot.hasMore,
                        nextFromMessageId = snapshot.nextFromMessageId,
                        videoChats = current.videoChats.map { it.copy(isSelected = !it.isSavedMessages && it.chatId == source.chatId) }
                    )
                }
                }
            }
        } else if (source.isForum && _uiState.value.availableTopics.isEmpty()) {
            loadForumTopics(source.chatId)
        }
    }

    fun enterExplorerTopic(topicId: Int) {
        if (_explorerPosition.value.level != ExplorerLevel.TOPICS) return
        val source = _uiState.value
        if (source.selectedChatId == null || source.availableTopics.none { it.id == topicId }) return
        _explorerPosition.value = _explorerPosition.value.copy(level = ExplorerLevel.MEDIA)
        selectTopic(topicId)
    }

    fun explorerBack(): Boolean {
        val position = _explorerPosition.value
        if (position.section == ExplorerSection.SAVED || position.level == ExplorerLevel.SOURCES) return false
        val source = _uiState.value.videoChats.firstOrNull { it.chatId == _uiState.value.selectedChatId }
        _explorerPosition.value = position.copy(
            level = if (position.level == ExplorerLevel.MEDIA && source?.isForum == true) {
                ExplorerLevel.TOPICS
            } else {
                ExplorerLevel.SOURCES
            }
        )
        return true
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
        com.vaydelux.bousygram.core.tdlib.prefetch.FocusPrefetchManager.onFocusCleared()
        super.onCleared()
    }

    fun refresh() {
        val selected = _uiState.value
        if (!selected.isSavedMessagesSelected && selected.selectedChatId != null) {
            loadChat(selected.selectedChatId, selected.selectedChatTitle)
            return
        }
        val owner = AccountSession.current() ?: return
        val requestGeneration = ++mediaRequestGeneration
        val warmSaved = catalogSnapshot("saved:topic:0")
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                selectedChatTitle = SAVED_MESSAGES_TITLE,
                selectedChatId = null,
                isSavedMessagesSelected = true,
                hasMore = true,
                isLoadingMore = false,
                items = if (it.isSavedMessagesSelected && it.items.isNotEmpty()) it.items else warmSaved?.items ?: emptyList(),
                nextFromMessageId = 0L
            )
        }
        viewModelScope.launch {
            val cached = com.vaydelux.bousygram.data.database.VideoIndexRepository.getIndexedVideosForChat(
                repository.cachedSavedMessagesChatId,
                null
            )
            if (cached.isNotEmpty() && AccountSession.isCurrent(owner) && requestGeneration == mediaRequestGeneration) {
                _uiState.update { current ->
                    if (current.items.isEmpty()) current.copy(items = cached) else current
                }
                enrichMediaItemsAsync(cached)
            }
        }
        repository.loadLatestMediaPagedProgressive(pageSize, null) { items, nextFromMessageId, error, isFinal ->
            if (!AccountSession.isCurrent(owner) || requestGeneration != mediaRequestGeneration) return@loadLatestMediaPagedProgressive
            _uiState.update { current ->
                current.copy(
                    items = if (items.isEmpty() && current.items.isNotEmpty() && (!isFinal || error != null)) {
                        current.items
                    } else items,
                    isLoading = !isFinal,
                    error = error,
                    hasMore = isFinal && nextFromMessageId != 0L,
                    nextFromMessageId = if (isFinal) nextFromMessageId else 0L
                )
            }
            if (isFinal && items.isNotEmpty()) rememberCatalog("saved:topic:0", items, nextFromMessageId)
            if (items.isNotEmpty()) {
                com.vaydelux.bousygram.data.database.VideoIndexRepository.indexMediaItemsAsync(items)
                enrichMediaItemsAsync(items)
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
        val owner = AccountSession.current() ?: return
        val requestGeneration = ++mediaRequestGeneration
        val folderKey = "chat:" + chatId + ":topic:0"
        val warmChat = catalogSnapshot(folderKey)
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
                items = if (!it.isSavedMessagesSelected && it.selectedChatId == chatId && it.items.isNotEmpty()) it.items else warmChat?.items ?: emptyList(),
                nextFromMessageId = 0L,
                videoChats = it.videoChats.map { source -> source.copy(isSelected = !source.isSavedMessages && source.chatId == chatId) }
            )
        }
        viewModelScope.launch {
            val cached = com.vaydelux.bousygram.data.database.VideoIndexRepository.getIndexedVideosForChat(
                chatId,
                null
            )
            if (cached.isNotEmpty() && AccountSession.isCurrent(owner) && requestGeneration == mediaRequestGeneration) {
                _uiState.update { current ->
                    if (current.items.isEmpty()) current.copy(items = cached) else current
                }
                enrichMediaItemsAsync(cached)
            }
        }
        // Only forum groups expose topics. Channels and ordinary groups should
        // not send unnecessary forum queries or show irrelevant topic errors.
        if (_uiState.value.videoChats.any { it.chatId == chatId && it.isForum }) {
            loadForumTopics(chatId)
        }
        repository.loadChatMediaPagedProgressive(chatId, pageSize, null) { items, nextFromMessageId, error, isFinal ->
            if (!AccountSession.isCurrent(owner) || requestGeneration != mediaRequestGeneration) return@loadChatMediaPagedProgressive
            _uiState.update { current ->
                current.copy(
                    items = if (items.isEmpty() && current.items.isNotEmpty() && (!isFinal || error != null)) {
                        current.items
                    } else items,
                    isLoading = !isFinal,
                    error = error,
                    hasMore = isFinal && nextFromMessageId != 0L,
                    nextFromMessageId = if (isFinal) nextFromMessageId else 0L
                )
            }
            if (isFinal && items.isNotEmpty()) rememberCatalog(folderKey, items, nextFromMessageId)
            if (items.isNotEmpty()) {
                com.vaydelux.bousygram.data.database.VideoIndexRepository.indexMediaItemsAsync(items)
                enrichMediaItemsAsync(items)
            }
            fetchThumbnails(items)
            scheduleChatSourceLoad()
        }
    }

    fun loadForumTopics(chatId: Long) {
        if (_uiState.value.videoChats.none { it.chatId == chatId && it.isForum }) return
        val owner = AccountSession.current() ?: return
        val generation = mediaRequestGeneration
        val cached = freshTopics(chatId)
        if (cached != null) {
            _uiState.update { current ->
                if (current.selectedChatId == chatId && !current.isSavedMessagesSelected) {
                    current.copy(availableTopics = cached, isLoadingTopics = false)
                } else current
            }
            return
        }
        _uiState.update { it.copy(isLoadingTopics = true) }
        repository.loadForumTopics(chatId) { topics, error ->
            if (!AccountSession.isCurrent(owner) || generation != mediaRequestGeneration) return@loadForumTopics
            _uiState.update { current ->
                if (current.selectedChatId != chatId || current.isSavedMessagesSelected) current
                else {
                    if (error == null) {
                        snapshotKey("chat:" + chatId + ":topics")?.let { key ->
                            synchronized(catalogSnapshotLock) {
                                topicSnapshots[key] = TopicSnapshot(
                                    topics = topics,
                                    savedAtMs = android.os.SystemClock.elapsedRealtime()
                                )
                                while (topicSnapshots.size > 8) {
                                    topicSnapshots.remove(topicSnapshots.keys.first())
                                }
                            }
                        }
                    }
                    current.copy(
                        availableTopics = if (error != null && current.availableTopics.isNotEmpty()) current.availableTopics else topics,
                        isLoadingTopics = false,
                        error = error ?: current.error
                    )
                }
            }
        }
    }

    fun selectTopic(topicId: Int?) = loadTopic(topicId, force = false)

    fun refreshCurrentMedia() {
        val current = _uiState.value
        when {
            current.isSavedMessagesSelected -> refresh()
            current.selectedChatId == null -> refresh()
            current.selectedTopicId != null -> loadTopic(current.selectedTopicId, force = true)
            else -> loadChat(current.selectedChatId, current.selectedChatTitle)
        }
    }

    private fun loadTopic(topicId: Int?, force: Boolean) {
        val state = _uiState.value
        val chatId = state.selectedChatId ?: return
        if (!force && state.selectedTopicId == topicId) return
        val owner = AccountSession.current() ?: return
        val requestGeneration = ++mediaRequestGeneration
        val folderKey = "chat:" + chatId + ":topic:" + (topicId ?: 0)
        val warmTopic = catalogSnapshot(folderKey)
        _uiState.update {
            it.copy(
                selectedTopicId = topicId,
                isLoading = true,
                error = null,
                hasMore = true,
                isLoadingMore = false,
                items = warmTopic?.items ?: emptyList(),
                nextFromMessageId = 0L
            )
        }
        viewModelScope.launch {
            val cached = com.vaydelux.bousygram.data.database.VideoIndexRepository.getIndexedVideosForChat(
                chatId,
                topicId?.toLong()
            )
            if (cached.isNotEmpty() && AccountSession.isCurrent(owner) && requestGeneration == mediaRequestGeneration) {
                _uiState.update { current ->
                    if (current.items.isEmpty()) current.copy(items = cached) else current
                }
            }
        }
        repository.loadChatMediaPagedProgressive(chatId, pageSize, null, topicId = topicId) { items, nextFromMessageId, error, isFinal ->
            if (!AccountSession.isCurrent(owner) || requestGeneration != mediaRequestGeneration) return@loadChatMediaPagedProgressive
            _uiState.update { current ->
                current.copy(
                    items = if (items.isEmpty() && current.items.isNotEmpty() && (!isFinal || error != null)) {
                        current.items
                    } else items,
                    isLoading = !isFinal,
                    error = error,
                    hasMore = isFinal && nextFromMessageId != 0L,
                    nextFromMessageId = if (isFinal) nextFromMessageId else 0L
                )
            }
            if (isFinal && items.isNotEmpty()) rememberCatalog(folderKey, items, nextFromMessageId)
            if (items.isNotEmpty()) {
                com.vaydelux.bousygram.data.database.VideoIndexRepository.indexMediaItemsAsync(items)
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
        val owner = AccountSession.current() ?: return
        val requestGeneration = mediaRequestGeneration
        if (state.isSavedMessagesSelected) {
            repository.loadLatestMediaPagedProgressive(pageSize, nextFromMessageId) { items, nextFromId, error, isFinal ->
                val current = _uiState.value
                if (!AccountSession.isCurrent(owner) || requestGeneration != mediaRequestGeneration || !current.isLoadingMore) return@loadLatestMediaPagedProgressive
                handleLoadMore(items, nextFromId, error, isFinal)
            }
        } else {
            val chatId = state.selectedChatId ?: return
            repository.loadChatMediaPagedProgressive(chatId, pageSize, nextFromMessageId, topicId = state.selectedTopicId) { items, nextFromId, error, isFinal ->
                val current = _uiState.value
                if (!AccountSession.isCurrent(owner) || requestGeneration != mediaRequestGeneration || !current.isLoadingMore) return@loadChatMediaPagedProgressive
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
        if (isFinal && _uiState.value.items.isNotEmpty()) {
            val current = _uiState.value
            val folderKey = if (current.isSavedMessagesSelected) "saved:topic:0"
            else "chat:" + current.selectedChatId + ":topic:" + (current.selectedTopicId ?: 0)
            rememberCatalog(folderKey, current.items, current.nextFromMessageId)
        }
        if (items.isNotEmpty()) {
            com.vaydelux.bousygram.data.database.VideoIndexRepository.indexMediaItemsAsync(items)
            enrichMediaItemsAsync(items)
            fetchThumbnails(items)
        }
    }

    private val enrichedMediaKeys =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /**
     * Local-only title metadata parsing. Do not transmit private Telegram message
     * titles or filenames to TMDB automatically just because a BuildConfig API
     * key exists. Future Extras metadata access requires explicit user approval.
     */
    private fun enrichMediaItemsAsync(items: List<MediaItem>) {
        val owner = AccountSession.current() ?: return
        val pending = items.filter { item ->
            enrichedMediaKeys.add("${owner.accountId}:${owner.generation}:${item.chatId}:${item.messageId}")
        }
        if (pending.isEmpty()) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            pending.chunked(32).forEach { batch ->
                if (!AccountSession.isCurrent(owner)) return@launch
                val parsed = batch.associate { item ->
                    val info = com.vaydelux.bousygram.core.media.SeriesMetadataParser.parse(
                        title = item.title,
                        fileName = item.fileName
                    )
                    val enriched = item.copy(
                        releaseYear = item.releaseYear ?: info.year,
                        seasonNumber = item.seasonNumber ?: info.seasonNumber,
                        episodeNumber = item.episodeNumber ?: info.episodeNumber,
                        seriesTitle = item.seriesTitle ?: info.seriesTitle.takeIf { info.isSeries }
                    )
                    ("${item.chatId}:${item.messageId}") to enriched
                }
                _uiState.update { current ->
                    if (!AccountSession.isCurrent(owner)) return@update current
                    current.copy(
                        items = current.items.map { item ->
                            parsed["${item.chatId}:${item.messageId}"] ?: item
                        }
                    )
                }
            }
        }
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
        val owner = AccountSession.current() ?: run {
            _uiState.update { it.copy(isSidebarLoading = false) }
            return
        }
        repository.loadVideoChats(10_000, selectedId) { chats, error ->
            if (!AccountSession.isCurrent(owner)) return@loadVideoChats
            hasLoadedVideoChats = error == null
            _uiState.update { current ->
                current.copy(
                    videoChats = stableChatCatalog(current.videoChats, chats),
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

    // Playback launch is managed via PlaybackLaunchCoordinator.
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

    fun setQuickSearchQuery(query: String) {
        val clean = query.trim()
        _uiState.update { it.copy(quickSearchQuery = clean) }
        if (clean.isNotEmpty()) {
            val currentState = _uiState.value
            val scopedChatId = if (currentState.isSavedMessagesSelected) null else currentState.selectedChatId
            val scopedChatTitle = currentState.selectedChatTitle
            viewModelScope.launch {
                val cachedMatches = withContext(Dispatchers.IO) {
                    VideoIndexRepository.searchIndexedVideos(clean, scopedChatId, limit = 50)
                }
                if (cachedMatches.isNotEmpty()) {
                    _uiState.update { current ->
                        val merged = (current.items + cachedMatches)
                            .distinctBy { "${it.chatId}:${it.messageId}" }
                        current.copy(items = merged)
                    }
                }
                val initialMatchCount = _uiState.value.filteredAndSortedItems.size
                RecentSearchRepository.recordSearchQuery(
                    query = clean,
                    chatId = scopedChatId,
                    chatTitle = scopedChatTitle,
                    resultCount = initialMatchCount
                )
            }
            // Also re-execute TDLib search for this query and merge any new matches
            if (scopedChatId != null && scopedChatId != 0L) {
                repository.searchChatVideos(scopedChatId, clean, 0L, limit = 40) { foundItems, _, _ ->
                    if (foundItems.isNotEmpty()) {
                        VideoIndexRepository.indexMediaItemsAsync(foundItems)
                        _uiState.update { current ->
                            val merged = (current.items + foundItems)
                                .distinctBy { "${it.chatId}:${it.messageId}" }
                            current.copy(items = merged)
                        }
                    }
                    val updatedCount = _uiState.value.filteredAndSortedItems.size
                    if (updatedCount > 0) {
                        RecentSearchRepository.recordSearchQueryAsync(
                            query = clean,
                            chatId = scopedChatId,
                            chatTitle = scopedChatTitle,
                            resultCount = updatedCount
                        )
                    }
                }
            } else {
                repository.searchGlobalVideos(clean, "", limit = 40) { globalResult, _ ->
                    val foundItems = globalResult?.items.orEmpty()
                    if (foundItems.isNotEmpty()) {
                        VideoIndexRepository.indexMediaItemsAsync(foundItems)
                        _uiState.update { current ->
                            val merged = (current.items + foundItems)
                                .distinctBy { "${it.chatId}:${it.messageId}" }
                            current.copy(items = merged)
                        }
                    }
                    val updatedCount = _uiState.value.filteredAndSortedItems.size
                    if (updatedCount > 0) {
                        RecentSearchRepository.recordSearchQueryAsync(
                            query = clean,
                            chatId = null,
                            chatTitle = scopedChatTitle,
                            resultCount = updatedCount
                        )
                    }
                }
            }
        }
    }

    fun executeRecentSearch(query: String) {
        setQuickSearchQuery(query)
    }

    fun toggleQuickSearchQuery(query: String) {
        val clean = query.trim()
        val current = _uiState.value.quickSearchQuery
        if (current.equals(clean, ignoreCase = true)) {
            clearQuickSearchQuery()
        } else {
            setQuickSearchQuery(clean)
        }
    }

    fun clearQuickSearchQuery() {
        _uiState.update { it.copy(quickSearchQuery = "") }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            RecentSearchRepository.removeSearchQuery(query)
            if (_uiState.value.quickSearchQuery.equals(query.trim(), ignoreCase = true)) {
                clearQuickSearchQuery()
            }
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            RecentSearchRepository.clearRecentSearches()
            clearQuickSearchQuery()
        }
    }

    fun setCategoryTab(tab: com.vaydelux.bousygram.domain.model.MediaCategoryTab) {
        val presentationTab = if (tab == com.vaydelux.bousygram.domain.model.MediaCategoryTab.SERIES) {
            com.vaydelux.bousygram.domain.model.MediaCategoryTab.ALL
        } else {
            tab
        }
        _uiState.update { it.copy(selectedCategoryTab = presentationTab, selectedSeriesTitle = null) }
    }

    /** Kept for source/API compatibility; series drill-down is intentionally disabled. */
    fun selectSeries(seriesTitle: String?) {
        _uiState.update { it.copy(selectedSeriesTitle = null) }
    }

    fun onItemFocused(item: MediaItem) {
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
        // Do not download the first 2 MiB of every video on TV D-pad focus; only resolve metadata/thumbnails.
        // Prefetch on focus: >400 ms debounce, 2MB head (and MP4 tail), low priority, max 2 concurrent, never while playing
        com.vaydelux.bousygram.core.tdlib.prefetch.FocusPrefetchManager.onCardFocused(item)
    }

    fun onItemFocusLost(item: MediaItem) {
        com.vaydelux.bousygram.core.tdlib.prefetch.FocusPrefetchManager.onCardFocusLost(item)
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
