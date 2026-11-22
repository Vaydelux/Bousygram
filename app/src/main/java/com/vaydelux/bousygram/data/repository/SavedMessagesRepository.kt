package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.domain.model.MediaItem
import org.drinkless.tdlib.TdApi
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class SavedMessagesRepository {
    companion object {
        const val TAG = "SavedMessagesRepo"
    }

    internal val owner = AccountSession.current()
    internal val client = TdLibClient.accountClient(owner)
    internal val catalogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile internal var cachedSavedMessagesChatId: Long? = null

    fun shutdown() {
        catalogScope.coroutineContext.cancelChildren()
        cachedSavedMessagesChatId = null
    }

    internal suspend fun requestCatalog(query: TdApi.Function<*>, timeoutMs: Long = 8_000L): TdApi.Object? {
        val account = owner ?: return TdApi.Error(401, "Sign in to Telegram to load media.")
        return try {
            withTimeoutOrNull(timeoutMs) { TdLibClient.awaitAccountResponse(query, account) }
        } catch (cancelled: CancellationException) {
            if (!currentCoroutineContext().isActive || !AccountSession.isCurrent(account)) throw cancelled
            TdApi.Error(503, "Telegram connection changed. Try again.")
        } catch (_: Exception) {
            TdApi.Error(500, "Couldn't load data from Telegram. Try again.")
        }
    }

    internal fun sendCatalogRequest(
        query: TdApi.Function<*>,
        timeoutMs: Long = 8_000L,
        onResult: (TdApi.Object?) -> Unit
    ): Job =
        catalogScope.launch {
            // After login, TDLib can transiently reject SearchChatMessages
            // and GetForumTopics while indexes/dialogs are synchronizing.
            // Retry once inside this account-scoped request, rather than
            // surfacing an error that requires repeated manual Retry taps.
            val attempts = if (query is TdApi.SearchChatMessages ||
                query is TdApi.GetForumTopics) 2 else 1
            val result = retryTransientTelegramCatalogRequest(query, attempts) {
                requestCatalog(it, timeoutMs)
            } ?: TdApi.Error(408, "Telegram took too long to respond. Try again.")
            if (owner == null || AccountSession.isCurrent(owner)) onResult(result)
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

    /** Returns a cancellation handle so stale topic pages cannot outlive navigation. */
    fun loadForumTopics(
        chatId: Long,
        query: String = "",
        onUpdate: (List<com.vaydelux.bousygram.domain.model.ForumTopicChannel>, String?, Boolean) -> Unit
    ): () -> Unit = loadForumTopicsInternal(chatId, query, onUpdate)

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
