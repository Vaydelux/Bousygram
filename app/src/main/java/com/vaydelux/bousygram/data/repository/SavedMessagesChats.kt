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
    onResult: (List<VideoChatItem>, String?, Boolean) -> Unit
) {
    resolveSavedMessagesChatInternal { savedChatId, savedError ->
        if (savedChatId == null) {
            onResult(emptyList(), savedError ?: "Saved Messages unavailable", true)
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
        val remainingChatLists = AtomicInteger(2)
        val publicationLock = Any()

        // TDLib invokes callbacks on native threads. Serialize partial snapshots and the
        // terminal update so the UI never gets an older, incomplete list after completion.
        fun emitCurrentChats(error: String? = null, isFinal: Boolean = false) = synchronized(publicationLock) {
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
            onResult(ordered, error ?: listErrors.filterNotNull().firstOrNull(), isFinal)
        }

        fun finishChatList() {
            if (remainingChatLists.decrementAndGet() == 0) {
                emitCurrentChats(isFinal = true)
            }
        }

        // Emit initial Saved Messages item right away
        emitCurrentChats()

        fun loadMissingDetails(chatIds: List<Long>, onDone: () -> Unit) {
            // A 10k-chat Telegram account must not dispatch thousands of GetChat
            // requests at once or wait for all responses before showing channels.
            val missing = chatIds.filter { requestedChatIds.add(it) }
            fun loadBatch(start: Int) {
                if (start >= missing.size) {
                    onDone()
                    return
                }
                val end = minOf(start + CHAT_PICKER_DETAIL_BATCH, missing.size)
                val pending = AtomicInteger(end - start)
                fun completeOne() {
                    if (pending.decrementAndGet() == 0) {
                        emitCurrentChats() // Show every finished batch immediately.
                        loadBatch(end)
                    }
                }
                missing.subList(start, end).forEach { chatId ->
                    client.send(TdApi.GetChat(chatId)) { chatResult ->
                        val chat = chatResult as? TdApi.Chat
                        val title = if (chatId == savedChatId) SAVED_MESSAGES_TITLE else chat?.title
                        if (chat == null || title.isNullOrBlank()) {
                            completeOne()
                            return@send
                        }
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
                            completeOne()
                        }
                        val chatType = chat.type
                        if (chatType is TdApi.ChatTypeSupergroup) {
                            client.send(TdApi.GetSupergroup(chatType.supergroupId)) { sgResult ->
                                recordSource((sgResult as? TdApi.Supergroup)?.isForum == true)
                            }
                        } else recordSource(false)
                    }
                }
            }
            loadBatch(0)
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

                    // Finish this metadata batch BEFORE requesting more chat IDs.
                    // Otherwise a large account accumulates thousands of GetChat /
                    // GetSupergroup requests and can publish a terminal snapshot
                    // while older detail callbacks are still running.
                    loadMissingDetails(ids) {
                        emitCurrentChats()
                        if (shouldContinue && chatsResult is TdApi.Chats && ids.size < maxVisibleChats) {
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
                        } else {
                            finishChatList()
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
private const val CHAT_PICKER_DETAIL_BATCH = 40

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

