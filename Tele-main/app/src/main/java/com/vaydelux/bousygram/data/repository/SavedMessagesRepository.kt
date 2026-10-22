package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.VideoChatItem
import org.drinkless.tdlib.TdApi
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import com.vaydelux.bousygram.core.tdlib.fastlink.FastLinkManager
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.store.PlaybackMode
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

internal const val BOT_USERNAME = "StreamVaultProBot"
internal const val HANDLER_TIMEOUT_SECONDS = 30L

class SavedMessagesRepository {
    companion object {
        const val TAG = "SavedMessagesRepo"
    }

    internal val client = TdLibClient.client
    internal val handlerScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    internal var cachedMeId: Long? = null
    internal var cachedSavedMessagesChatId: Long? = null

    fun shutdown() {
        handlerScheduler.shutdown()
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

    fun loadVideoChats(limit: Int, selectedChatId: Long?, onResult: (List<VideoChatItem>, String?) -> Unit) =
        loadVideoChatsInternal(limit, selectedChatId, onResult)

    fun fetchThumbnailPath(fileId: Int, onResult: (String?) -> Unit) =
        fetchThumbnailPathInternal(fileId, onResult)

    /** Prepare a stream and wait for asynchronous Fast-Link responses before returning. */
    suspend fun prepareStream(item: MediaItem): Result<String> = withTimeout(45_000L) {
        val fileId = item.fileId ?: return@withTimeout Result.failure(IllegalArgumentException("No file is associated with this video"))
        if (EnterprisePreferences.playbackMode() == PlaybackMode.FastLink) {
            FastLinkManager.requestFastLink(item.chatId, item.messageId)
        } else {
            withContext(Dispatchers.IO) {
                val prepared = LocalStreamingServer.prepareStream(
                    fileId = fileId,
                    declaredSize = item.fileSizeBytes,
                    title = item.title,
                    mimeType = item.mimeType,
                    fileName = item.fileName
                )
                if (prepared == null) Result.failure(IllegalStateException("File is not ready to stream yet"))
                else Result.success(prepared.url)
            }
        }
    }

    fun requestFastLink(item: MediaItem, onResult: (String?, String?) -> Unit) =
        requestFastLinkInternal(item, onResult)

    fun deleteMessage(chatId: Long, messageId: Long, onResult: (String?) -> Unit) =
        deleteMessageInternal(chatId, messageId, onResult)
}