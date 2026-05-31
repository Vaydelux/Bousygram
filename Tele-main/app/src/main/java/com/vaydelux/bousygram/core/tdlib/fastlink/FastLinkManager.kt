package com.vaydelux.bousygram.core.tdlib.fastlink

import android.util.Log
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume

object FastLinkManager {
    private const val TAG = "FastLinkManager"
    private const val DEFAULT_TIMEOUT_MS = 30_000L
    private val urlRegex = Regex("""https?://[^\s]+""")
    private val pendingRequests = ConcurrentHashMap<String, Boolean>()
    private val disposalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun requestFastLink(
        chatId: Long,
        messageId: Long,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Result<String> {
        val botUsername = EnterprisePreferences.fastLinkBotUsername()
            .trim()
            .trimStart('@')
        if (botUsername.isBlank()) {
            return Result.failure(IllegalStateException("Fast-link bot username is not configured"))
        }
        val requestKey = "$chatId:$messageId"
        if (pendingRequests.putIfAbsent(requestKey, true) != null) {
            return Result.failure(IllegalStateException("Fast-link request is already running"))
        }

        return try {
            withTimeoutOrNull(timeoutMs) {
                val botChat = sendTd(TdApi.SearchPublicChat(botUsername)) as? TdApi.Chat
                    ?: return@withTimeoutOrNull Result.failure<String>(
                        IllegalStateException("Could not find bot @$botUsername")
                    )
                val baselineMessageId = latestMessageId(botChat.id)
                val forwarded = sendTd(
                    TdApi.ForwardMessages(
                        botChat.id,
                        null,
                        chatId,
                        longArrayOf(messageId),
                        null,
                        true,
                        true
                    )
                ) as? TdApi.Messages
                val forwardedMessageId = forwarded?.messages?.firstOrNull()?.id ?: 0L
                waitForBotReply(
                    botChatId = botChat.id,
                    baselineMessageId = baselineMessageId,
                    forwardedMessageId = forwardedMessageId
                )
            } ?: Result.failure(IllegalStateException("Fast-link bot did not reply in time"))
        } catch (error: Throwable) {
            Log.w(TAG, "Fast-link request failed", error)
            Result.failure(error)
        } finally {
            pendingRequests.remove(requestKey)
        }
    }

    fun revokeFastLink(url: String?) {
        val revokeUrl = revokeUrlFor(url) ?: return
        disposalScope.launch {
            runCatching {
                val connection = (URL(revokeUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 3_000
                    readTimeout = 3_000
                }
                try {
                    connection.responseCode
                } finally {
                    connection.disconnect()
                }
            }.onFailure { error ->
                Log.d(TAG, "Fast-link revoke failed: ${error.message}")
            }
        }
    }

    private suspend fun waitForBotReply(
        botChatId: Long,
        baselineMessageId: Long,
        forwardedMessageId: Long
    ): Result<String> =
        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            lateinit var updateHandler: (TdApi.Object?) -> Unit
            val timeoutThread = Thread {
                try {
                    TimeUnit.SECONDS.sleep(30)
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        TdLibClient.removeUpdateHandler(updateHandler)
                        continuation.resume(Result.failure(IllegalStateException("Fast-link bot reply timed out")))
                    }
                } catch (_: InterruptedException) {
                }
            }

            fun finish(result: Result<String>) {
                if (completed.compareAndSet(false, true) && continuation.isActive) {
                    TdLibClient.removeUpdateHandler(updateHandler)
                    timeoutThread.interrupt()
                    continuation.resume(result)
                }
            }

            updateHandler = updateHandler@{ update ->
                val message = (update as? TdApi.UpdateNewMessage)?.message ?: return@updateHandler
                if (message.chatId != botChatId || message.id <= baselineMessageId) return@updateHandler
                val text = (message.content as? TdApi.MessageText)?.text?.text ?: return@updateHandler
                val isReply = (message.replyTo as? TdApi.MessageReplyToMessage)?.messageId == forwardedMessageId
                val link = extractUrl(text)
                if (link != null && (isReply || text.contains("http", ignoreCase = true))) {
                    finish(Result.success(link))
                }
            }
            TdLibClient.addUpdateHandler(updateHandler)
            continuation.invokeOnCancellation {
                TdLibClient.removeUpdateHandler(updateHandler)
                timeoutThread.interrupt()
            }
            timeoutThread.isDaemon = true
            timeoutThread.start()
        }

    private suspend fun latestMessageId(chatId: Long): Long {
        val history = sendTd(TdApi.GetChatHistory(chatId, 0, 0, 1, false)) as? TdApi.Messages
        return history?.messages?.firstOrNull()?.id ?: 0L
    }

    private fun extractUrl(text: String): String? {
        val fastLine = text.lineSequence().firstOrNull { it.contains("Download Link:", ignoreCase = true) }
        if (fastLine != null) {
            urlRegex.find(fastLine)?.value?.trimEnd('.', ',', ')', ']', '>')?.let { return it }
        }
        return urlRegex.find(text)?.value?.trimEnd('.', ',', ')', ']', '>')
    }

    private fun revokeUrlFor(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val streamMarker = "/stream/"
        val markerIndex = url.indexOf(streamMarker)
        if (markerIndex < 0) return null
        val base = url.substring(0, markerIndex)
        val token = url.substring(markerIndex + streamMarker.length)
            .substringBefore('?')
            .substringBefore('#')
            .trim()
        if (token.isBlank()) return null
        return "$base/revoke/$token"
    }

    private suspend fun sendTd(function: TdApi.Function<*>): TdApi.Object =
        suspendCancellableCoroutine { continuation ->
            TdLibClient.client.send(function) { response ->
                if (continuation.isActive) {
                    continuation.resume(response)
                }
            }
        }
}
