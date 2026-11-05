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
        val discardedMediaCount: Int,
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
        freshCatalogTimestamp(it.savedAtMs, android.os.SystemClock.elapsedRealtime(), freshnessWindowMs)
    }

    private fun rememberCatalog(folder: String, videos: List<MediaItem>, nextCursor: Long, discardedMediaCount: Int = 0) {
        // Never store a cursor for a truncated page. It would skip uncached
        // media when the user returns to a deep folder after pagination.
        if (videos.isEmpty()) return
        val key = snapshotKey(folder) ?: return
        if (!canCacheCatalogPage(videos.size, MAX_CACHED_CATALOG_ITEMS)) {
            // Remove an older shorter snapshot instead of retaining a misleading
            // cursor for a folder that has since grown beyond the bounded cache.
            synchronized(catalogSnapshotLock) { catalogSnapshots.remove(key) }
            return
        }
        synchronized(catalogSnapshotLock) {
            // Bounded metadata, never video bytes, stream URLs, or credentials.
            catalogSnapshots[key] = CatalogSnapshot(
                items = videos,
                nextFromMessageId = nextCursor,
                hasMore = nextCursor != 0L,
                discardedMediaCount = discardedMediaCount
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
                freshCatalogTimestamp(it.savedAtMs, android.os.SystemClock.elapsedRealtime(), freshnessWindowMs)
            }?.topics
        }
    }

    val uiState: StateFlow<MediaUiState> = _uiState
    val recentSearches: StateFlow<List<RecentSearchQuery>> = RecentSearchRepository.recentSearches
    private val pageSize = 30
    private val activeCatalogLimit: Int by lazy {
        val lowMemory = runCatching {
            val app = com.vaydelux.bousygram.app.BousygramApp.instance
            com.vaydelux.bousygram.core.media.PlayerBufferConfig.isLowRamDevice(app) ||
                !android.os.Process.is64Bit()
        }.getOrDefault(true)
        TelegramMediaWindow.itemLimit(lowMemory)
    }
    private var isInitialized = false
    private var hasLoadedVideoChats = false
    private var mediaRequestGeneration = 0L
    private var sourceRequestGeneration = 0L
    private var topicRequestGeneration = 0L
    private var topicLoadingTimeoutJob: Job? = null
    private var cancelForumTopicRequest: (() -> Unit)? = null

    private fun cancelForumTopicLoading() {
        topicLoadingTimeoutJob?.cancel()
        topicLoadingTimeoutJob = null
        cancelForumTopicRequest?.invoke()
        cancelForumTopicRequest = null
    }
    private var quickSearchGeneration = 0L
    private val browseRestorations = BoundedBrowsingHistory<String, BrowseRestoration>(MAX_BROWSE_RESTORATIONS)
    private val sourceFolderRestorations = java.util.concurrent.ConcurrentHashMap<ExplorerSection, BrowseRestoration>()
    private val topicFolderFocus = BoundedBrowsingHistory<Long, Int>(MAX_TOPIC_FOCUS)

    fun focusedExplorerTopic(chatId: Long?): Int? = chatId?.let(topicFolderFocus::get)

    fun rememberExplorerTopicFocus(chatId: Long?, topicId: Int) {
        if (chatId != null) topicFolderFocus[chatId] = topicId
    }

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
        val owner = AccountSession.current()
        val message = update.message
        val content = message.content
        if (content is TdApi.MessageVideo ||
            content is TdApi.MessageVideoNote ||
            content is TdApi.MessageDocument ||
            content is TdApi.MessageAnimation) {
            // Invalidate *every* source, not only the folder currently visible.
            // A new message in a different group must not leave a fresh-looking
            // but outdated snapshot when the user switches to that group.
            if (owner != null) invalidateCatalogForChat(message.chatId)
            if (owner != null && isMessageInSelectedChat(message.chatId)) {
                val generationAtRequest = mediaRequestGeneration
                repository.getMessage(message.chatId, message.id) { item, _ ->
                    if (AccountSession.isCurrent(owner) &&
                        generationAtRequest == mediaRequestGeneration &&
                        isMessageInSelectedChat(message.chatId)) {
                        item?.let { newItem ->
                            _uiState.update { current ->
                                val exists = current.items.any {
                                    it.chatId == newItem.chatId && it.messageId == newItem.messageId
                                }
                                if (exists) {
                                    // Updated thumbnail/title for an existing item must
                                    // retain its stable row and remote-focus key.
                                    current.copy(items = current.items.map {
                                        if (it.chatId == newItem.chatId &&
                                            it.messageId == newItem.messageId
                                        ) newItem else it
                                    })
                                } else {
                                    val selectedTopic = current.selectedTopicId
                                    val belongsToTopic = selectedTopic == null ||
                                        selectedTopic == 0 ||
                                        newItem.messageThreadId == selectedTopic.toLong() ||
                                        (selectedTopic == 1 && (newItem.messageThreadId == null ||
                                            newItem.messageThreadId == 0L))
                                    if (!belongsToTopic) current else {
                                        val update = TelegramMediaWindow.prependLatest(
                                            current = current.items,
                                            incoming = newItem,
                                            maxItems = activeCatalogLimit,
                                            discardedFromFront = current.discardedMediaCount,
                                            nextFromMessageId = current.nextFromMessageId,
                                            hasMore = current.hasMore
                                        )
                                        if (!update.visible) current else current.copy(
                                            items = update.items,
                                            nextFromMessageId = update.nextFromMessageId,
                                            hasMore = update.hasMore
                                        )
                                    }
                                }
                            }
                            // Index every authorized new message, but do not
                            // schedule thumbnails/enrichment for offscreen pages.
                            com.vaydelux.bousygram.data.database.VideoIndexRepository.indexMediaItemsAsync(listOf(newItem))
                            if (_uiState.value.items.any { it.chatId == newItem.chatId &&
                                    it.messageId == newItem.messageId
                                }
                            ) {
                                fetchThumbnails(listOf(newItem))
                                enrichMediaItemsAsync(listOf(newItem))
                            }
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
    private var quickSearchJob: Job? = null
    private var deferredThumbnailJob: Job? = null
    private val requestedThumbnailFileIds =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Int, Boolean>())
    private val thumbnailFailureTimesMs = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    // Account-scoped enrichment markers must not grow with every browsed page.
    private val enrichedMediaKeys = LinkedHashSet<String>()


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
                    ++sourceRequestGeneration
                    ++topicRequestGeneration
                    cancelForumTopicLoading()
                    chatUpdateJob?.cancel()
                    deferredChatSourceLoadJob?.cancel()
                    quickSearchJob?.cancel()
                    _uiState.value = MediaUiState()
                    _explorerPosition.value = ExplorerPosition()
                    browseRestorations.clear()
                    sourceFolderRestorations.clear()
                    topicFolderFocus.clear()
                    synchronized(catalogSnapshotLock) {
                        catalogSnapshots.clear()
                        topicSnapshots.clear()
                    }
                    hasLoadedVideoChats = false
                    requestedThumbnailFileIds.clear()
                    synchronized(enrichedMediaKeys) { enrichedMediaKeys.clear() }
                    initializedAccount = null
                    repository.shutdown() // Never reuse the previous Telegram account's self-chat ID.
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
            ++sourceRequestGeneration
            ++topicRequestGeneration
            cancelForumTopicLoading()
            chatUpdateJob?.cancel()
            deferredChatSourceLoadJob?.cancel()
            quickSearchJob?.cancel()
            deferredThumbnailJob?.cancel()
            _uiState.value = MediaUiState()
            _explorerPosition.value = ExplorerPosition()
            browseRestorations.clear()
            sourceFolderRestorations.clear()
            topicFolderFocus.clear()
            requestedThumbnailFileIds.clear()
            thumbnailFailureTimesMs.clear()
            synchronized(enrichedMediaKeys) { enrichedMediaKeys.clear() }
            synchronized(catalogSnapshotLock) {
                catalogSnapshots.clear()
                topicSnapshots.clear()
            }
            hasLoadedVideoChats = false
            isInitialized = false
            repository.shutdown() // Re-resolve Saved Messages for this account.
            initializedAccount = current
        }
        if (isInitialized) return
        isInitialized = true
        refresh()
    }

    fun selectExplorerSection(section: ExplorerSection) {
        cancelForumTopicLoading()
        _explorerPosition.value = ExplorerPosition(
            section = section,
            level = if (section == ExplorerSection.SAVED) ExplorerLevel.MEDIA else ExplorerLevel.SOURCES
        )
        if (section == ExplorerSection.SAVED) {
            val snapshot = freshCatalog("saved:topic:0")
            val savedItems = savedMessagesOnly(snapshot?.items.orEmpty(), repository.cachedSavedMessagesChatId)
            if (snapshot != null && savedItems.isNotEmpty()) {
                // Returning to Saved should not refetch the same page on every tab switch.
                ++mediaRequestGeneration
                _uiState.update { current ->
                    current.copy(
                        items = savedItems,
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
                        discardedMediaCount = snapshot.discardedMediaCount,
                        nextFromMessageId = snapshot.nextFromMessageId,
                        videoChats = current.videoChats.map { it.copy(isSelected = it.isSavedMessages) }
                    )
                }
            } else load() // A missing/unscoped Saved snapshot must be resolved from TDLib.
        } else loadVideoChatsIfNeeded()
    }

    fun enterExplorerSource(source: VideoChatItem) {
        if (source.isSavedMessages) {
            selectExplorerSection(ExplorerSection.SAVED)
            return
        }
        if (_uiState.value.selectedChatId != source.chatId) cancelForumTopicLoading()
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
                        // The previous forum's topic request may still be running.
                        // Never carry its loading flag into a different group.
                        isLoadingTopics = false,
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = false,
                        discardedMediaCount = 0,
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
                        discardedMediaCount = snapshot.discardedMediaCount,
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
        rememberExplorerTopicFocus(source.selectedChatId, topicId)
        cancelForumTopicLoading() // Do not compete with the selected topic's media request.
        _uiState.update { it.copy(isLoadingTopics = false) }
        _explorerPosition.value = _explorerPosition.value.copy(level = ExplorerLevel.MEDIA)
        selectTopic(topicId)
    }

    /** Allow browsing real group media even if GetForumTopics is unavailable or slow. */
    fun enterExplorerAllMedia() {
        if (_explorerPosition.value.level != ExplorerLevel.TOPICS) return
        val source = _uiState.value
        val chatId = source.selectedChatId ?: return
        if (source.isSavedMessagesSelected || source.videoChats.none { it.chatId == chatId && it.isForum }) return
        cancelForumTopicLoading()
        ++topicRequestGeneration // Invalidate any already-dispatched callbacks.
        _uiState.update { it.copy(isLoadingTopics = false) }
        _explorerPosition.value = _explorerPosition.value.copy(level = ExplorerLevel.MEDIA)
        loadTopic(null, force = true)
    }

    fun explorerBack(): Boolean {
        val position = _explorerPosition.value
        if (position.section == ExplorerSection.SAVED || position.level == ExplorerLevel.SOURCES) return false
        val source = _uiState.value.videoChats.firstOrNull { it.chatId == _uiState.value.selectedChatId }
        if (position.level == ExplorerLevel.TOPICS) {
            cancelForumTopicLoading()
            ++topicRequestGeneration
            _uiState.update { it.copy(isLoadingTopics = false) }
        }
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
        cancelForumTopicLoading()
        chatUpdateJob?.cancel()
        chatUpdateJob = null
        deferredChatSourceLoadJob?.cancel()
        deferredChatSourceLoadJob = null
        quickSearchJob?.cancel()
        quickSearchJob = null
        deferredThumbnailJob?.cancel()
        deferredThumbnailJob = null
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
            val viewingForumDirectory =
                _explorerPosition.value.level == ExplorerLevel.TOPICS &&
                selected.videoChats.any { it.chatId == selected.selectedChatId && it.isForum }
            if (viewingForumDirectory) refreshForumTopics(selected.selectedChatId)
            else loadChat(selected.selectedChatId, selected.selectedChatTitle)
            return
        }
        val owner = AccountSession.current() ?: return
        val requestGeneration = ++mediaRequestGeneration
        val savedChatId = repository.cachedSavedMessagesChatId
        val warmSaved = savedMessagesOnly(catalogSnapshot("saved:topic:0")?.items.orEmpty(), savedChatId)
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                selectedChatTitle = SAVED_MESSAGES_TITLE,
                selectedChatId = null,
                isSavedMessagesSelected = true,
                hasMore = true,
                discardedMediaCount = 0,
                isLoadingMore = false,
                items = savedMessagesOnly(
                    if (it.isSavedMessagesSelected && it.items.isNotEmpty()) it.items else warmSaved,
                    savedChatId
                ),
                nextFromMessageId = 0L
            )
        }
        viewModelScope.launch {
            // A null chatId means ALL recent videos in VideoIndexRepository. Never use
            // that fallback for Saved Messages, even briefly while GetMe is pending.
            val cached = savedChatId?.takeIf { it != 0L }?.let { resolvedId ->
                savedMessagesOnly(
                    com.vaydelux.bousygram.data.database.VideoIndexRepository.getIndexedVideosForChat(resolvedId, null),
                    resolvedId
                )
            }.orEmpty()
            if (cached.isNotEmpty() && AccountSession.isCurrent(owner) && requestGeneration == mediaRequestGeneration) {
                _uiState.update { current ->
                    if (current.items.isEmpty()) current.copy(items = cached) else current
                }
                enrichMediaItemsAsync(cached)
            }
        }
        repository.loadLatestMediaPagedProgressive(pageSize, null) { items, nextFromMessageId, error, isFinal ->
            if (!AccountSession.isCurrent(owner) || requestGeneration != mediaRequestGeneration) return@loadLatestMediaPagedProgressive
            val scopedItems = savedMessagesOnly(items, repository.cachedSavedMessagesChatId)
            _uiState.update { current ->
                val existing = savedMessagesOnly(current.items, repository.cachedSavedMessagesChatId)
                current.copy(
                    items = if (scopedItems.isEmpty() && existing.isNotEmpty() && (!isFinal || error != null)) {
                        existing
                    } else scopedItems,
                    isLoading = !isFinal,
                    error = error,
                    hasMore = isFinal && nextFromMessageId != 0L,
                    nextFromMessageId = if (isFinal) nextFromMessageId else 0L
                )
            }
            if (isFinal && scopedItems.isNotEmpty()) rememberCatalog("saved:topic:0", scopedItems, nextFromMessageId)
            if (scopedItems.isNotEmpty()) {
                com.vaydelux.bousygram.data.database.VideoIndexRepository.indexMediaItemsAsync(scopedItems)
                enrichMediaItemsAsync(scopedItems)
            }
            fetchThumbnails(scopedItems)
            scheduleChatSourceLoad()
        }
    }

    /** Saved Messages is an exact Telegram self-chat; null must never mean global search. */
    fun currentSearchChatId(): Long? {
        val selected = _uiState.value
        return quickSearchScope(
            selected.isSavedMessagesSelected,
            repository.cachedSavedMessagesChatId,
            selected.selectedChatId
        )
    }

    fun canResumeLastPlayedInCurrentSource(lastPlayedChatId: Long?): Boolean {
        val selected = _uiState.value
        val position = _explorerPosition.value
        // Switching to Channels/Groups only changes the directory route at first.
        // Never leave a Saved Messages Continue action visible in a different tab.
        if (position.level != ExplorerLevel.MEDIA ||
            (position.section == ExplorerSection.SAVED) != selected.isSavedMessagesSelected
        ) return false
        return canResumeInSource(
            selected.isSavedMessagesSelected,
            repository.cachedSavedMessagesChatId,
            selected.selectedChatId,
            lastPlayedChatId
        )
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
                discardedMediaCount = 0,
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

    fun loadForumTopics(chatId: Long) = requestForumTopics(chatId, force = false)

    /** A retry must bypass an empty or stale topic snapshot and actually query TDLib. */
    fun refreshForumTopics(chatId: Long) = requestForumTopics(chatId, force = true)

    private fun requestForumTopics(chatId: Long, force: Boolean) {
        if (!force && _uiState.value.isLoadingTopics && _uiState.value.selectedChatId == chatId) return
        if (_uiState.value.videoChats.none { it.chatId == chatId && it.isForum }) return
        val owner = AccountSession.current() ?: return
        val generation = mediaRequestGeneration
        cancelForumTopicLoading()
        val topicGeneration = ++topicRequestGeneration
        val cached = if (force) null else freshTopics(chatId)
        if (cached != null) {
            _uiState.update { current ->
                if (current.selectedChatId == chatId && !current.isSavedMessagesSelected) {
                    current.copy(availableTopics = cached, isLoadingTopics = false, error = null)
                } else current
            }
            return
        }
        fun stillCurrent(): Boolean =
            AccountSession.isCurrent(owner) &&
                generation == mediaRequestGeneration &&
                topicGeneration == topicRequestGeneration &&
                _uiState.value.selectedChatId == chatId &&
                !_uiState.value.isSavedMessagesSelected

        fun armTimeout() {
            topicLoadingTimeoutJob?.cancel()
            topicLoadingTimeoutJob = viewModelScope.launch {
                delay(FORUM_TOPIC_PAGE_TIMEOUT_MS)
                if (!stillCurrent()) return@launch
                // Do not display an endless skeleton when TDLib never calls back.
                cancelForumTopicLoading()
                ++topicRequestGeneration
                _uiState.update { current ->
                    if (current.selectedChatId == chatId && !current.isSavedMessagesSelected) {
                        current.copy(
                            isLoadingTopics = false,
                            error = "Telegram topics took too long to respond. Retry topics or browse all group media."
                        )
                    } else current
                }
            }
        }

        _uiState.update { current ->
            if (current.selectedChatId == chatId && !current.isSavedMessagesSelected) {
                current.copy(isLoadingTopics = true, error = null)
            } else current
        }
        armTimeout()
        cancelForumTopicRequest = repository.loadForumTopics(chatId) { topics, error, isFinal ->
            if (!stillCurrent()) return@loadForumTopics
            if (isFinal) {
                topicLoadingTimeoutJob?.cancel()
                topicLoadingTimeoutJob = null
                cancelForumTopicRequest = null
            } else armTimeout()
            _uiState.update { current ->
                if (current.selectedChatId != chatId || current.isSavedMessagesSelected) current
                else {
                    if (isFinal && error == null) {
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
                        isLoadingTopics = !isFinal,
                        error = error
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
            // Refreshing the all-media view must not restart an unrelated forum scan.
            current.videoChats.any { it.chatId == current.selectedChatId && it.isForum } ->
                loadTopic(null, force = true)
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
                discardedMediaCount = 0,
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
        val owner = AccountSession.current() ?: return
        if (!state.isSavedMessagesSelected && state.selectedChatId == null) return
        _uiState.update { it.copy(isLoadingMore = true) }
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
            val scopedItems = if (current.isSavedMessagesSelected) {
                savedMessagesOnly(items, repository.cachedSavedMessagesChatId)
            } else items.filter { it.chatId == current.selectedChatId }
            val existing = if (current.isSavedMessagesSelected) {
                savedMessagesOnly(current.items, repository.cachedSavedMessagesChatId)
            } else current.items.filter { it.chatId == current.selectedChatId }
            val bounded = TelegramMediaWindow.merge(existing, scopedItems, activeCatalogLimit)
            current.copy(
                items = bounded.items,
                discardedMediaCount = current.discardedMediaCount + bounded.removedFromFront,
                isLoadingMore = if (error != null) false else !isFinal,
                error = error,
                // An error is not end-of-list. Preserve cursor so Retry/scroll
                // can resume without skipping messages or hiding older pages.
                hasMore = if (error == null && isFinal) nextFromMessageId != 0L else current.hasMore,
                nextFromMessageId = resolvedPageCursor(current.nextFromMessageId, nextFromMessageId, isFinal, error)
            )
        }
        if (isFinal && error == null && _uiState.value.items.isNotEmpty()) {
            val current = _uiState.value
            val folderKey = if (current.isSavedMessagesSelected) "saved:topic:0"
            else "chat:" + current.selectedChatId + ":topic:" + (current.selectedTopicId ?: 0)
            rememberCatalog(folderKey, current.items, current.nextFromMessageId, current.discardedMediaCount)
        }
        if (items.isNotEmpty()) {
            com.vaydelux.bousygram.data.database.VideoIndexRepository.indexMediaItemsAsync(items)
            enrichMediaItemsAsync(items)
            fetchThumbnails(items)
        }
    }

    /**
     * Local-only title metadata parsing. Do not transmit private Telegram message
     * titles or filenames to TMDB automatically just because a BuildConfig API
     * key exists. Future Extras metadata access requires explicit user approval.
     */
    private fun enrichMediaItemsAsync(items: List<MediaItem>) {
        val owner = AccountSession.current() ?: return
        val pending = items.filter { item ->
            val key = "${owner.accountId}:${owner.generation}:${item.chatId}:${item.messageId}"
            synchronized(enrichedMediaKeys) {
                rememberBoundedIdentity(enrichedMediaKeys, key, MAX_ENRICHED_MEDIA_KEYS)
            }
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

    private fun fetchThumbnailIfNeeded(fileId: Int, owner: AccountSnapshot) {
        // Repeated partial search updates and failed/removed Telegram thumbnails must
        // not keep launching fresh GetFile/DownloadFile operations.
        val now = android.os.SystemClock.elapsedRealtime()
        if (!allowThumbnailRetry(thumbnailFailureTimesMs[fileId], now, THUMBNAIL_RETRY_DELAY_MS) ||
            !requestedThumbnailFileIds.add(fileId)
        ) return
        repository.fetchThumbnailPath(fileId) { path ->
            // Old account callbacks cannot clear the new account's in-flight tracking.
            if (!AccountSession.isCurrent(owner)) return@fetchThumbnailPath
            requestedThumbnailFileIds.remove(fileId)
            if (path.isNullOrBlank()) {
                thumbnailFailureTimesMs[fileId] = android.os.SystemClock.elapsedRealtime()
                // Failed-thumbnail cooldowns are transient. Old file IDs must not
                // accumulate indefinitely during sessions with thousands of cards.
                if (thumbnailFailureTimesMs.size > MAX_THUMBNAIL_FAILURES) {
                    val oldest = thumbnailFailureTimesMs.entries.minByOrNull { it.value }
                    if (oldest != null) thumbnailFailureTimesMs.remove(oldest.key, oldest.value)
                }
            } else {
                thumbnailFailureTimesMs.remove(fileId)
                updateThumbnailPathForFile(fileId, path)
            }
        }
    }

    private fun fetchThumbnails(items: List<MediaItem>) {
        val owner = AccountSession.current() ?: return
        if (com.vaydelux.bousygram.core.media.PlaybackSessionController.isVideoPlaying()) return
        // Load the likely visible cards first, then the rest after a short
        // pause. Non-focus touch devices also need offscreen thumbnail updates
        // when scrolling; do not permanently skip anything after the first row.
        val ids = items.asSequence()
            .filter { it.thumbnailPath.isNullOrBlank() }
            .mapNotNull { it.thumbnailFileId }
            .distinct()
            .toList()
        ids.take(MAX_INITIAL_THUMBNAIL_REQUESTS).forEach { fetchThumbnailIfNeeded(it, owner) }
        deferredThumbnailJob?.cancel()
        val remaining = ids.drop(MAX_INITIAL_THUMBNAIL_REQUESTS)
        if (remaining.isNotEmpty()) {
            deferredThumbnailJob = viewModelScope.launch {
                delay(THUMBNAIL_BACKGROUND_DELAY_MS)
                if (!AccountSession.isCurrent(owner) ||
                    com.vaydelux.bousygram.core.media.PlaybackSessionController.isVideoPlaying()
                ) return@launch
                // Keep TDLib thumbnail requests bounded even if callbacks stall.
                // Focused cards can still request their thumbnail independently.
                for (fileId in remaining) {
                    if (!AccountSession.isCurrent(owner) ||
                        com.vaydelux.bousygram.core.media.PlaybackSessionController.isVideoPlaying()
                    ) return@launch
                    var checks = 0
                    while (requestedThumbnailFileIds.size >= MAX_INFLIGHT_THUMBNAILS && checks++ < 20) {
                        delay(150L)
                    }
                    if (requestedThumbnailFileIds.size >= MAX_INFLIGHT_THUMBNAILS) return@launch
                    fetchThumbnailIfNeeded(fileId, owner)
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
        if (_uiState.value.isSidebarLoading || (!force && hasLoadedVideoChats)) return
        val owner = AccountSession.current() ?: return
        val sourceGeneration = ++sourceRequestGeneration
        val selectedId = if (_uiState.value.isSavedMessagesSelected) repository.cachedSavedMessagesChatId else _uiState.value.selectedChatId
        _uiState.update { it.copy(isSidebarLoading = true, sidebarError = null) }
        repository.loadVideoChats(10_000, selectedId) { chats, error, isFinal ->
            // Callback batches arrive on TDLib threads. Ignore outdated account requests.
            if (!AccountSession.isCurrent(owner) || sourceGeneration != sourceRequestGeneration) return@loadVideoChats
            hasLoadedVideoChats = isFinal && error == null
            _uiState.update { current ->
                current.copy(
                    // An early Saved-only snapshot must not clear channels already displayed
                    // during a manual refresh. A finished scan is authoritative.
                    videoChats = stableChatCatalog(current.videoChats, chats, retainMissing = !isFinal),
                    isSidebarLoading = !isFinal,
                    sidebarError = if (isFinal) error else null
                )
            }
        }
    }

    /** Resolves the saved last-played Telegram message even when it is outside the loaded page. */
    fun loadLastPlayed(onResult: (MediaItem?, String?) -> Unit) {
        val owner = AccountSession.current() ?: return
        val last = EnterprisePreferences.lastPlayed()
        if (last == null) {
            onResult(null, "There is no saved playback position yet")
            return
        }
        repository.getMessage(last.chatId, last.messageId) { item, error ->
            if (!AccountSession.isCurrent(owner)) return@getMessage
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
        val owner = AccountSession.current() ?: return
        val fileId = item.fileId
        _uiState.update { it.copy(preparingFileId = fileId) }
        viewModelScope.launch {
            val result = runCatching { repository.prepareStream(item).getOrThrow() }
            if (!AccountSession.isCurrent(owner)) return@launch
            val url = result.getOrNull()
            _uiState.update { it.copy(preparingFileId = null) }
            onResult(url, result.exceptionOrNull()?.message)
        }
    }

    fun deleteMediaItem(item: MediaItem, onResult: (String?) -> Unit) {
        val owner = AccountSession.current() ?: return
        _uiState.update { it.copy(error = null) }
        repository.deleteMessage(item.chatId, item.messageId) { error ->
            if (!AccountSession.isCurrent(owner)) return@deleteMessage
            if (error != null) {
                _uiState.update { current -> current.copy(error = error) }
                onResult(error)
            } else {
                invalidateCatalogForChat(item.chatId)
                _uiState.update { current ->
                    current.copy(items = current.items.filterNot {
                        it.chatId == item.chatId && it.messageId == item.messageId
                    })
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

    private fun isActiveQuickSearch(
        owner: AccountSnapshot,
        generation: Long,
        query: String,
        chatId: Long,
        saved: Boolean
    ): Boolean {
        val current = _uiState.value
        val currentSourceId = if (current.isSavedMessagesSelected) {
            repository.cachedSavedMessagesChatId
        } else current.selectedChatId
        return AccountSession.isCurrent(owner) &&
            generation == quickSearchGeneration &&
            current.quickSearchQuery == query &&
            current.isSavedMessagesSelected == saved &&
            currentSourceId == chatId
    }

    fun setQuickSearchQuery(query: String) {
        val clean = query.trim()
        val searchGeneration = ++quickSearchGeneration
        quickSearchJob?.cancel()
        _uiState.update { it.copy(quickSearchQuery = clean) }
        if (clean.isEmpty()) return

        val owner = AccountSession.current() ?: return
        val source = _uiState.value
        val isSaved = source.isSavedMessagesSelected
        // Null never means permission to search all other private chats.
        val chatId = quickSearchScope(isSaved, repository.cachedSavedMessagesChatId, source.selectedChatId)
            ?: return
        val title = source.selectedChatTitle
        fun current(): Boolean = isActiveQuickSearch(owner, searchGeneration, clean, chatId, isSaved)

        quickSearchJob = viewModelScope.launch {
            // Avoid a new TDLib request for every keystroke/voice partial.
            delay(300L)
            if (!current()) return@launch
            val cached = withContext(Dispatchers.IO) {
                VideoIndexRepository.searchIndexedVideos(clean, chatId, limit = 50)
            }
            if (!current()) return@launch
            if (cached.isNotEmpty()) {
                _uiState.update { ui ->
                    if (!current()) ui else {
                        val bounded = TelegramMediaWindow.merge(ui.items, cached, activeCatalogLimit)
                        ui.copy(
                            items = bounded.items,
                            discardedMediaCount = ui.discardedMediaCount + bounded.removedFromFront
                        )
                    }
                }
            }
            if (!current()) return@launch
            RecentSearchRepository.recordSearchQuery(
                query = clean,
                chatId = chatId,
                chatTitle = title,
                resultCount = _uiState.value.filteredAndSortedItems.size
            )
            if (!current()) return@launch

            repository.searchChatVideos(chatId, clean, 0L, limit = 40) { matches, _, _ ->
                if (!current()) return@searchChatVideos
                if (matches.isNotEmpty()) {
                    VideoIndexRepository.indexMediaItemsAsync(matches)
                    _uiState.update { ui ->
                        if (!current()) ui else {
                            val bounded = TelegramMediaWindow.merge(ui.items, matches, activeCatalogLimit)
                            ui.copy(
                                items = bounded.items,
                                discardedMediaCount = ui.discardedMediaCount + bounded.removedFromFront
                            )
                        }
                    }
                }
                if (current()) {
                    RecentSearchRepository.recordSearchQueryAsync(
                        query = clean,
                        chatId = chatId,
                        chatTitle = title,
                        resultCount = _uiState.value.filteredAndSortedItems.size
                    )
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
        ++quickSearchGeneration
        quickSearchJob?.cancel()
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
        val owner = AccountSession.current() ?: return
        item.fileId?.let { fileId ->
            MediaDurationResolver.cachedDurationMs(fileId)
                .takeIf { it > 0L && it != item.trustedDurationMs }
                ?.let { updateTrustedDuration(fileId, it) }
        }
        val thumbnailFileId = item.thumbnailFileId
        if (thumbnailFileId != null && item.thumbnailPath.isNullOrBlank()) {
            fetchThumbnailIfNeeded(thumbnailFileId, owner)
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
private const val MAX_INITIAL_THUMBNAIL_REQUESTS = 12
private const val THUMBNAIL_RETRY_DELAY_MS = 60_000L
private const val THUMBNAIL_BACKGROUND_DELAY_MS = 900L
private const val MEDIA_FIRST_PAGE_PRIORITY_POLL_MS = 150L
private const val MEDIA_FIRST_PAGE_PRIORITY_MAX_WAIT_MS = 3_000L

private const val MAX_CACHED_CATALOG_ITEMS = 120
private const val MAX_ENRICHED_MEDIA_KEYS = 768
private const val MAX_BROWSE_RESTORATIONS = 48
private const val MAX_TOPIC_FOCUS = 64
private const val MAX_THUMBNAIL_FAILURES = 256
private const val MAX_INFLIGHT_THUMBNAILS = 24
private const val FORUM_TOPIC_PAGE_TIMEOUT_MS = 9_000L
