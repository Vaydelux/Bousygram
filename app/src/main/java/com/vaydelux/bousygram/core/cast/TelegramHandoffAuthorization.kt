package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.drinkless.tdlib.TdApi

/** A remote handoff is an export operation, not ordinary in-account playback.
 * Resolve only against the captured receiver account; never adopt a remote
 * file ID or the account that happens to be active after an asynchronous call. */
internal object TelegramHandoffAuthorization {
    fun permits(request: TelegramTvHandoff, messageChatId: Long, messageId: Long,
                resolvedChatId: Long, canBeSaved: Boolean, protectedChat: Boolean,
                secretChat: Boolean, selfDestructing: Boolean, fileId: Int?): Boolean =
        request.isValid() && request.chatId == messageChatId && request.chatId == resolvedChatId &&
            request.messageId == messageId && canBeSaved && !protectedChat && !secretChat &&
            !selfDestructing && fileId != null && fileId > 0

    suspend fun resolve(owner: AccountSnapshot, request: TelegramTvHandoff): Int? {
        val scope = currentCoroutineContext()
        fun current(): Boolean {
            scope.ensureActive()
            return AccountSession.isCurrent(owner)
        }
        if (!request.isValid() || !current()) return null
        val message = TdLibClient.awaitAccountResponse(
            TdApi.GetMessage(request.chatId, request.messageId), expectedOwner = owner, timeoutMs = 5_000L
        ) as? TdApi.Message ?: return null
        if (!current()) return null
        val chat = TdLibClient.awaitAccountResponse(TdApi.GetChat(request.chatId),
            expectedOwner = owner, timeoutMs = 5_000L) as? TdApi.Chat ?: return null
        if (!current()) return null
        val fileId = when (val content = message.content) {
            is TdApi.MessageVideo -> content.video?.video?.id
            is TdApi.MessageVideoNote -> content.videoNote?.video?.id
            is TdApi.MessageAnimation -> content.animation?.animation?.id
            is TdApi.MessageDocument -> content.document?.document?.id
            else -> null
        }
        if (!permits(request, message.chatId, message.id, chat.id, message.canBeSaved,
                chat.hasProtectedContent, chat.type is TdApi.ChatTypeSecret, message.selfDestructType != null,
                fileId)) return null
        val id = checkNotNull(fileId)
        val file = TdLibClient.awaitAccountResponse(TdApi.GetFile(id),
            expectedOwner = owner, timeoutMs = 5_000L) as? TdApi.File ?: return null
        return id.takeIf { current() && file.id == id }
    }
}
