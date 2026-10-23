package com.vaydelux.bousygram.data.repository

import android.util.Log
import com.vaydelux.bousygram.domain.model.SAVED_MESSAGES_TITLE
import com.vaydelux.bousygram.domain.model.ChatSourceKind
import com.vaydelux.bousygram.domain.model.VideoChatItem
import org.drinkless.tdlib.TdApi
import java.util.concurrent.atomic.AtomicInteger

internal fun SavedMessagesRepository.loadVideoChatsInternal(
    limit: Int,
    selectedChatId: Long?,
    onResult: (List<VideoChatItem>, String?) -> Unit
) {
    resolveSavedMessagesChatInternal { savedChatId, savedError ->
        if (savedChatId == null) {
            onResult(emptyList(), savedError ?: "Saved Messages unavailable")
            return@resolveSavedMessagesChatInternal
        }

        // Load both Telegram's main and archived chat lists. TDLib can load fewer chats
        // than requested, so request subsequent batches until it reports that the list is
        // exhausted; only then read the currently known IDs.
        val listReferences = arrayOf(mutableListOf<Long>(), mutableListOf<Long>())
        val listErrors = arrayOfNulls<String>(2)
        val remainingLists = AtomicInteger(2)
        val maxVisibleChats = limit.coerceAtLeast(100)

        fun finishLists() {
            if (remainingLists.decrementAndGet() != 0) return

            val mergedIds = linkedMapOf<Long, Boolean>()
            // Preserve Telegram's main-list order, followed by archived chats. Saved Messages
            // is always inserted first and never duplicated if TDLib already returned it.
            mergedIds[savedChatId] = false
            listReferences[0].forEach { chatId -> if (!mergedIds.containsKey(chatId)) mergedIds[chatId] = false }
            listReferences[1].forEach { chatId -> if (!mergedIds.containsKey(chatId)) mergedIds[chatId] = true }
            val chatIds = mergedIds.keys.toList()
            if (chatIds.isEmpty()) {
                onResult(
                    listOf(VideoChatItem(savedChatId, SAVED_MESSAGES_TITLE, true, selectedChatId == null)),
                    listErrors.filterNotNull().firstOrNull()
                )
                return
            }

            Log.d(SavedMessagesRepository.TAG, "Found ${chatIds.size} Telegram chat sources")
            val lock = Any()
            val chatMap = mutableMapOf<Long, VideoChatItem>()
            fun finishChats() {
                val ordered = mutableListOf<VideoChatItem>()
                chatMap[savedChatId]?.let { ordered.add(it) }
                chatIds.forEach { chatId ->
                    if (chatId == savedChatId) return@forEach
                    chatMap[chatId]?.let { ordered.add(it) }
                }
                onResult(ordered, listErrors.filterNotNull().firstOrNull())
            }

            // Limit concurrent GetChat calls. Accounts can have thousands of chats; issuing
            // every request at once can saturate TDLib and delay media browsing.
            fun loadChatDetails(offset: Int) {
                val batch = chatIds.drop(offset).take(CHAT_PICKER_DETAIL_BATCH)
                if (batch.isEmpty()) {
                    finishChats()
                    return
                }
                val pendingBatch = AtomicInteger(batch.size)
                batch.forEach { chatId ->
                    client.send(TdApi.GetChat(chatId)) chatSend@{ chatResult ->
                        val chat = chatResult as? TdApi.Chat
                        val title = if (chatId == savedChatId) SAVED_MESSAGES_TITLE else chat?.title
                        if (chat != null && !title.isNullOrBlank()) {
                            val isSaved = chatId == savedChatId
                            val kind = if (isSaved) ChatSourceKind.SAVED_MESSAGES else when (val chatType = chat.type) {
                                is TdApi.ChatTypePrivate -> ChatSourceKind.PRIVATE
                                is TdApi.ChatTypeBasicGroup -> ChatSourceKind.GROUP
                                is TdApi.ChatTypeSupergroup -> if (chatType.isChannel) ChatSourceKind.CHANNEL else ChatSourceKind.GROUP
                                is TdApi.ChatTypeSecret -> ChatSourceKind.SECRET
                                else -> ChatSourceKind.OTHER
                            }
                            fun recordSource(isForum: Boolean) {
                                val source = VideoChatItem(
                                    chatId = chatId,
                                    title = title,
                                    isSavedMessages = isSaved,
                                    isSelected = if (isSaved) selectedChatId == null || chatId == selectedChatId else chatId == selectedChatId,
                                    kind = kind,
                                    isArchived = mergedIds[chatId] == true,
                                    isForum = isForum
                                )
                                synchronized(lock) { chatMap[chatId] = source }
                                if (pendingBatch.decrementAndGet() == 0) loadChatDetails(offset + batch.size)
                            }

                            val chatType = chat.type
                            if (chatType is TdApi.ChatTypeSupergroup) {
                                client.send(TdApi.GetSupergroup(chatType.supergroupId)) { sgResult ->
                                    val sg = sgResult as? TdApi.Supergroup
                                    recordSource(sg?.isForum == true)
                                }
                            } else {
                                recordSource(false)
                            }
                        } else {
                            if (pendingBatch.decrementAndGet() == 0) loadChatDetails(offset + batch.size)
                        }
                    }
                }
            }
            loadChatDetails(0)
        }

        fun loadChatList(listIndex: Int, chatList: TdApi.ChatList) {
            fun readLoadedChats() {
                client.send(TdApi.GetChats(chatList, maxVisibleChats)) { chatsResult ->
                    val chats = chatsResult as? TdApi.Chats
                    val ids = chats?.chatIds?.toList().orEmpty()
                    synchronized(listReferences[listIndex]) {
                        listReferences[listIndex].clear()
                        ids.forEach { id -> listReferences[listIndex].add(id) }
                    }
                    if (chatsResult is TdApi.Error) {
                        listErrors[listIndex] = chatsResult.message
                    }
                    finishLists()
                }
            }

            fun loadNextBatch() {
                client.send(TdApi.LoadChats(chatList, CHAT_PICKER_LOAD_BATCH)) { loadResult ->
                    when (loadResult) {
                        is TdApi.Ok -> loadNextBatch()
                        is TdApi.Error -> {
                            // 404 means the list is fully loaded. For other errors, still show
                            // the chats TDLib already has instead of losing the picker entirely.
                            if (loadResult.code != 404) listErrors[listIndex] = loadResult.message
                            readLoadedChats()
                        }
                        else -> readLoadedChats()
                    }
                }
            }

            loadNextBatch()
        }

        loadChatList(0, TdApi.ChatListMain())
        loadChatList(1, TdApi.ChatListArchive())
    }
}

private const val CHAT_PICKER_LOAD_BATCH = 500
private const val CHAT_PICKER_DETAIL_BATCH = 50

internal fun SavedMessagesRepository.resolveSavedMessagesChatInternal(
    onResult: (Long?, String?) -> Unit
) {
    val cachedChatId = cachedSavedMessagesChatId
    if (cachedChatId != null) {
        onResult(cachedChatId, null)
        return
    }

    fun createChat(userId: Long) {
        client.send(TdApi.CreatePrivateChat(userId, false)) { chatResult ->
            when (chatResult) {
                is TdApi.Chat -> {
                    cachedSavedMessagesChatId = chatResult.id
                    client.send(TdApi.OpenChat(chatResult.id)) { }
                    onResult(chatResult.id, null)
                }
                is TdApi.Error -> onResult(null, chatResult.message)
                else -> onResult(null, "Failed to open Saved Messages")
            }
        }
    }

    val cachedUserId = cachedMeId
    if (cachedUserId != null) {
        createChat(cachedUserId)
        return
    }

    client.send(TdApi.GetMe()) { meResult ->
        when (meResult) {
            is TdApi.User -> {
                cachedMeId = meResult.id
                createChat(meResult.id)
            }
            is TdApi.Error -> onResult(null, meResult.message)
            else -> onResult(null, "Failed to load user")
        }
    }
}

internal fun SavedMessagesRepository.loadSavedMessagesChatInternal(
    userId: Long,
    limit: Int,
    onResult: (List<com.vaydelux.bousygram.domain.model.MediaItem>, String?) -> Unit
) {
    client.send(TdApi.CreatePrivateChat(userId, false)) { chatResult ->
        when (chatResult) {
            is TdApi.Chat -> {
                client.send(TdApi.OpenChat(chatResult.id)) {
                    searchMediaInternal(chatResult.id, limit, null, onResult)
                }
            }
            is TdApi.Error -> onResult(emptyList(), chatResult.message)
            else -> onResult(emptyList(), "Failed to load Saved Messages chat")
        }
    }
}

