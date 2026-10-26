package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.VideoChatItem
import org.drinkless.tdlib.TdApi
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class SavedMessagesRepository {
    companion object {
        const val TAG = "SavedMessagesRepo"
    }

    internal val client = TdLibClient.client
    internal var cachedMeId: Long? = null
    internal var cachedSavedMessagesChatId: Long? = null

    fun shutdown() {
        cachedMeId = null
        cachedSavedMessagesChatId = null
    }

    fun loadLatestMedia(limit: Int, onResult: (List<MediaItem>, String?) -> Unit) =
        loadLatestMediaInternal(limit, onResult)

    fun loadLatestMediaPaged(
        limit: Int,
        fromMessageId: Long?,
        onResult: (List<MediaItem>, Long, String?) -> Unit
    ) = loadLatestMediaPagedInternal(limit, fromMessageId, onResult)

    /** Emits partial TDLib search results as each media filter returns, then one final page. */
    fun loadLatestMediaPagedProgressive(
        limit: Int,
        fromMessageId: Long?,
        onUpdate: (List<MediaItem>, Long, String?, Boolean) -> Unit
    ) = loadLatestMediaPagedProgressiveInternal(limit, fromMessageId, onUpdate)

    fun getMessage(chatId: Long, messageId: Long, onResult: (MediaItem?, String?) -> Unit) {
        client.send(TdApi.GetMessage(chatId, messageId)) { result ->
            when (result) {
                is TdApi.Message -> {
                    val item = mapMessageToMediaInternal(result)
                    onResult(item, null)
                }
                is TdApi.Error -> onResult(null, result.message)
                else -> onResult(null, "Failed to load message")
            }
        }
    }

    fun loadChatMedia(chatId: Long, limit: Int, onResult: (List<MediaItem>, String?) -> Unit) =
        loadChatMediaInternal(chatId, limit, onResult)

    fun loadChatMediaPaged(
        chatId: Long,
        limit: Int,
        fromMessageId: Long?,
        onResult: (List<MediaItem>, Long, String?) -> Unit
    ) = loadChatMediaPagedInternal(chatId, limit, fromMessageId, onResult)

    /** Emits partial TDLib search results as each media filter returns, then one final page. */
    fun loadChatMediaPagedProgressive(
        chatId: Long,
        limit: Int,
        fromMessageId: Long?,
        topicId: Int? = null,
        onUpdate: (List<MediaItem>, Long, String?, Boolean) -> Unit
    ) = loadChatMediaPagedProgressiveInternal(chatId, limit, fromMessageId, topicId, onUpdate)

    fun loadVideoChats(limit: Int, selectedChatId: Long?, onResult: (List<VideoChatItem>, String?) -> Unit) =
        loadVideoChatsInternal(limit, selectedChatId, onResult)

    fun loadForumTopics(chatId: Long, query: String = "", onResult: (List<com.vaydelux.bousygram.domain.model.ForumTopicChannel>, String?) -> Unit) =
        loadForumTopicsInternal(chatId, query, onResult)

    fun fetchThumbnailPath(fileId: Int, onResult: (String?) -> Unit) =
        fetchThumbnailPathInternal(fileId, onResult)

    /** Prepare a local TDLib stream. Playback never forwards the message to a bot. */
    suspend fun prepareStream(
        item: MediaItem,
        deadlineAtMs: Long = System.currentTimeMillis() + 4_000L
    ): Result<String> = withContext(Dispatchers.IO) {
        val fileId = item.fileId ?: return@withContext Result.failure(IllegalArgumentException("No file is associated with this video"))
        val prepared = LocalStreamingServer.prepareStream(
            fileId = fileId,
            declaredSize = item.fileSizeBytes,
            title = item.title,
            mimeType = item.mimeType,
            fileName = item.fileName,
            chatId = item.chatId,
            messageId = item.messageId,
            deadlineAtMs = deadlineAtMs
        )
        if (prepared == null) Result.failure(IllegalStateException("File is not ready to stream yet"))
        else Result.success(prepared.url)
    }

    fun preparePlayback(item: MediaItem, onResult: (String?, String?) -> Unit) =
        prepareLocalStreamInternal(item, onResult)

    fun deleteMessage(chatId: Long, messageId: Long, onResult: (String?) -> Unit) =
        deleteMessageInternal(chatId, messageId, onResult)
}