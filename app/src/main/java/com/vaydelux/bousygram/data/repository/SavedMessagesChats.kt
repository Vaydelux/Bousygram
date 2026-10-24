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

        // Load both Telegram's main and archived chat lists incrementally.
        // TDLib chat database stores known chats locally; read them first, then load more
        // in small incremental batches and emit updates progressively.
        val listReferences = arrayOf(mutableListOf<Long>(), mutableListOf<Long>())
        val listErrors = arrayOfNulls<String>(2)
        val maxVisibleChats = limit.coerceAtLeast(100)
        val lock = Any()
        val chatMap = mutableMapOf<Long, VideoChatItem>()
        val requestedChatIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

        fun emitCurrentChats(error: String? = null) {
            val mergedIds = linkedMapOf<Long, Boolean>()
            mergedIds[savedChatId] = false
            synchronized(listReferences[0]) {
                listReferences[0].forEach { if (!mergedIds.containsKey(it)) mergedIds[it] = false }
            }
            synchronized(listReferences[1]) {
                listReferences[1].forEach { if (!mergedIds.containsKey(it)) mergedIds[it] = true }
            }
            val ordered = mutableListOf<VideoChatItem>()
            synchronized(lock) {
                chatMap[savedChatId]?.let { ordered.add(it) } ?: run {
                    ordered.add(VideoChatItem(savedChatId, SAVED_MESSAGES_TITLE, true, selectedChatId == null || selectedChatId == savedChatId))
                }
                mergedIds.keys.forEach { chatId ->
                    if (chatId != savedChatId) {
                        chatMap[chatId]?.let { ordered.add(it) }
                    }
                }
            }
            onResult(ordered, error ?: listErrors.filterNotNull().firstOrNull())
        }

        // Emit initial Saved Messages item right away
        emitCurrentChats()

        fun loadMissingDetails(chatIds: List<Long>, onDone: () -> Unit) {
            val missing = chatIds.filter { !requestedChatIds.contains(it) }
            if (missing.isEmpty()) {
                onDone()
                return
            }
            missing.forEach { requestedChatIds.add(it) }

            val pending = AtomicInteger(missing.size)
            missing.forEach { chatId ->
                client.send(TdApi.GetChat(chatId)) { chatResult ->
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
                            val isArchived = synchronized(listReferences[1]) { listReferences[1].contains(chatId) }
                            val source = VideoChatItem(
                                chatId = chatId,
                                title = title,
                                isSavedMessages = isSaved,
                                isSelected = if (isSaved) selectedChatId == null || chatId == selectedChatId else chatId == selectedChatId,
                                kind = kind,
                                isArchived = isArchived,
                                isForum = isForum
                            )
                            synchronized(lock) { chatMap[chatId] = source }
                            if (pending.decrementAndGet() == 0) onDone()
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
                        if (pending.decrementAndGet() == 0) onDone()
                    }
                }
            }
        }

        fun loadChatList(listIndex: Int, chatList: TdApi.ChatList) {
            fun syncAndLoadNext(shouldContinue: Boolean) {
                client.send(TdApi.GetChats(chatList, maxVisibleChats)) { chatsResult ->
                    val chats = chatsResult as? TdApi.Chats
                    val ids = chats?.chatIds?.toList().orEmpty()
                    synchronized(listReferences[listIndex]) {
                        listReferences[listIndex].clear()
                        listReferences[listIndex].addAll(ids)
                    }
                    if (chatsResult is TdApi.Error) {
                        listErrors[listIndex] = chatsResult.message
                    }

                    loadMissingDetails(ids) {
                        emitCurrentChats()
                    }

                    if (shouldContinue && ids.size < maxVisibleChats) {
                        client.send(TdApi.LoadChats(chatList, CHAT_PICKER_LOAD_BATCH)) { loadResult ->
                            when (loadResult) {
                                is TdApi.Ok -> syncAndLoadNext(shouldContinue = true)
                                is TdApi.Error -> {
                                    if (loadResult.code != 404) listErrors[listIndex] = loadResult.message
                                    syncAndLoadNext(shouldContinue = false)
                                }
                                else -> syncAndLoadNext(shouldContinue = false)
                            }
                        }
                    }
                }
            }

            syncAndLoadNext(shouldContinue = true)
        }

        loadChatList(0, TdApi.ChatListMain())
        loadChatList(1, TdApi.ChatListArchive())
    }
}

private const val CHAT_PICKER_LOAD_BATCH = 50
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

