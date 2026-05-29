package com.vaydelux.bousygram.data.repository

import android.util.Log
import com.vaydelux.bousygram.domain.model.SAVED_MESSAGES_TITLE
import com.vaydelux.bousygram.domain.model.MediaType
import com.vaydelux.bousygram.domain.model.VideoChatItem
import org.drinkless.tdlib.TdApi
import java.util.concurrent.atomic.AtomicInteger

internal fun SavedMessagesRepository.loadVideoChatsInternal(
    limit: Int,
    selectedChatId: Long?,
    onResult: (List<VideoChatItem>, String?) -> Unit
) {
    resolveSavedMessagesChatInternal { savedChatId, error ->
        if (savedChatId == null) {
            onResult(emptyList(), error ?: "Saved Messages unavailable")
            return@resolveSavedMessagesChatInternal
        }
        
        // Asynchronously load the chats list from Telegram servers to synchronize TDLib's local cache
        client.send(TdApi.LoadChats(TdApi.ChatListMain(), 100)) { loadResult ->
            // Query the populated local cache using GetChats
            client.send(TdApi.GetChats(TdApi.ChatListMain(), 100)) chatsSend@{ chatsResult ->
                val chats = chatsResult as? TdApi.Chats
                val chatIds = chats?.chatIds?.toList().orEmpty()
                if (chatIds.isEmpty()) {
                    onResult(emptyList(), null)
                    return@chatsSend
                }
                Log.d(SavedMessagesRepository.TAG, "Found ${chatIds.size} chats")
                val pending = AtomicInteger(chatIds.size)
                val lock = Any()
                val chatMap = mutableMapOf<Long, VideoChatItem>()
                fun finish() {
                    val ordered = mutableListOf<VideoChatItem>()
                    chatMap[savedChatId]?.let { ordered.add(it) }
                    chatIds.forEach { chatId ->
                        if (chatId == savedChatId) return@forEach
                        chatMap[chatId]?.let { ordered.add(it) }
                    }
                    onResult(ordered, null)
                }
                chatIds.forEach { chatId ->
                    client.send(TdApi.GetChat(chatId)) chatSend@{ chatResult ->
                        val chat = chatResult as? TdApi.Chat
                        val title = if (chatId == savedChatId) SAVED_MESSAGES_TITLE else chat?.title
                        if (title == null) {
                            if (pending.decrementAndGet() == 0) finish()
                            return@chatSend
                        }

                        // Determine chat type to decide sidebar inclusion strategy
                        val chatType = chat?.type
                        val isChannel = chatType is TdApi.ChatTypeSupergroup && chatType.isChannel
                        val isGroup = chatType is TdApi.ChatTypeBasicGroup ||
                            (chatType is TdApi.ChatTypeSupergroup && !chatType.isChannel)
                        val isSavedMessages = chatId == savedChatId

                        when {
                            isSavedMessages -> {
                                // Always include Saved Messages
                                synchronized(lock) {
                                    chatMap[chatId] = VideoChatItem(
                                        chatId = chatId,
                                        title = title,
                                        isSavedMessages = true,
                                        isSelected = selectedChatId == null || chatId == selectedChatId
                                    )
                                }
                                if (pending.decrementAndGet() == 0) finish()
                            }
                            isGroup -> {
                                // Always show groups — fetch live from server when clicked
                                synchronized(lock) {
                                    chatMap[chatId] = VideoChatItem(
                                        chatId = chatId,
                                        title = title,
                                        isSavedMessages = false,
                                        isSelected = chatId == selectedChatId
                                    )
                                }
                                if (pending.decrementAndGet() == 0) finish()
                            }
                            isChannel -> {
                                // For channels: check if they have video content
                                hasVideoInChatInternal(chatId) { hasVideo ->
                                    if (hasVideo) {
                                        synchronized(lock) {
                                            chatMap[chatId] = VideoChatItem(
                                                chatId = chatId,
                                                title = title,
                                                isSavedMessages = false,
                                                isSelected = chatId == selectedChatId
                                            )
                                        }
                                    }
                                    if (pending.decrementAndGet() == 0) finish()
                                }
                            }
                            else -> {
                                if (pending.decrementAndGet() == 0) finish()
                            }
                        }
                    }
                }
            }
        }
    }
}

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

private fun SavedMessagesRepository.hasVideoInChatInternal(chatId: Long, onResult: (Boolean) -> Unit) {
    searchWithFilterInternal(chatId, 1, null, TdApi.SearchMessagesFilterPhotoAndVideo()) { items, _ ->
        if (items.any { it.type == MediaType.Video }) {
            onResult(true)
        } else {
            searchWithFilterInternal(chatId, 15, null, TdApi.SearchMessagesFilterDocument()) { docItems, _ ->
                if (docItems.any { it.type == MediaType.Video }) {
                    onResult(true)
                } else {
                    // Final fallback for chats where TDLib search index hasn't been populated yet.
                    client.send(TdApi.GetChatHistory(chatId, 0, 0, 100, false)) { result ->
                        when (result) {
                            is TdApi.Messages -> {
                                val hasVideo = result.messages
                                    ?.asSequence()
                                    ?.mapNotNull { mapMessageToMediaInternal(it) }
                                    ?.any { it.type == MediaType.Video }
                                    ?: false
                                onResult(hasVideo)
                            }
                            else -> onResult(false)
                        }
                    }
                }
            }
        }
    }
}
