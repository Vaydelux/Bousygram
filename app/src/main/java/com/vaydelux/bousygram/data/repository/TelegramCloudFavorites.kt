package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.domain.model.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Telegram is the only source of truth for NEW Favorites.
 *
 * No Room table, DataStore journal, WorkManager, or media-byte caching is used.
 * The bounded in-memory queue is intentionally best-effort: Android process death
 * can discard unsent operations. Success is reported ONLY on TDLib send confirmation.
 */
object TelegramCloudFavorites {
    const val CHANNEL_TITLE = "Bousygram Favorites"
    private const val CHANNEL_MARKER = "Bousygram-managed private favorites channel (v1)"
    private const val PAGE_SIZE = 60
    private const val QUEUE_CAPACITY = 32

    data class Page(val channelId: Long, val items: List<MediaItem>, val nextBeforeId: Long?, val hasMore: Boolean)
    data class Receipt(val sourceChatId: Long, val sourceMessageId: Long, val savedMessageId: Long, val channelId: Long)

    private data class PendingSave(
        val owner: AccountSnapshot,
        val media: MediaItem,
        val key: String,
        val completion: (Result<Receipt>) -> Unit
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channelLock = Mutex()
    private val saves = Channel<PendingSave>(QUEUE_CAPACITY)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val initialized = AtomicBoolean(false)
    @Volatile private var cachedChannel: Pair<AccountSnapshot, Long>? = null
    private val _savingKeys = MutableStateFlow<Set<String>>(emptySet())
    val savingKeys = _savingKeys.asStateFlow()
    private val _recentKeys = MutableStateFlow<Set<String>>(emptySet())
    val recentKeys = _recentKeys.asStateFlow()

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        AccountSession.registerInvalidationHandler {
            cachedChannel = null
            inFlight.clear()
            _savingKeys.value = emptySet()
            _recentKeys.value = emptySet()
            // Any requests still in the queue belong to an obsolete account.
            while (saves.tryReceive().isSuccess) { }
            // In-flight TDLib requests are cancelled by TdLibClient account invalidation.
        }
        scope.launch {
            for (save in saves) {
                try {
                    if (!AccountSession.isCurrent(save.owner)) continue
                    val receipt = saveNow(save.owner, save.media)
                    if (AccountSession.isCurrent(save.owner)) {
                        _recentKeys.value = _recentKeys.value + mediaKey(save.media)
                        complete(save, Result.success(receipt))
                    }
                } catch (cancelled: CancellationException) {
                    // Session invalidation must never leak callbacks to the next user.
                    if (AccountSession.isCurrent(save.owner)) complete(save, Result.failure(cancelled))
                } catch (error: Exception) {
                    if (AccountSession.isCurrent(save.owner)) complete(save, Result.failure(error))
                } finally {
                    inFlight.remove(save.key)
                    _savingKeys.value = inFlight.map { it.substringAfter(':').substringAfter(':') }.toSet()
                }
            }
        }
    }

    fun enqueueSave(media: MediaItem, onComplete: (Result<Receipt>) -> Unit): Boolean {
        init()
        val owner = AccountSession.current()
        if (owner == null || media.chatId == 0L || media.messageId == 0L) {
            scope.launch(Dispatchers.Main.immediate) {
                onComplete(Result.failure(IllegalStateException("Please sign in to Telegram to save this media.")))
            }
            return false
        }
        val key = "${owner.generation}:${owner.accountId}:${media.chatId}:${media.messageId}"
        if (!inFlight.add(key)) return false
        _savingKeys.value = inFlight.map { it.substringAfter(':').substringAfter(':') }.toSet()
        if (!saves.trySend(PendingSave(owner, media, key, onComplete)).isSuccess) {
            inFlight.remove(key)
            _savingKeys.value = inFlight.map { it.substringAfter(':').substringAfter(':') }.toSet()
            scope.launch(Dispatchers.Main.immediate) {
                onComplete(Result.failure(IllegalStateException("Favorites is busy. Please retry.")))
            }
            return false
        }
        return true
    }

    private suspend fun complete(save: PendingSave, result: Result<Receipt>) {
        withContext(Dispatchers.Main.immediate) {
            if (AccountSession.isCurrent(save.owner)) save.completion(result)
        }
    }

    suspend fun refresh(beforeMessageId: Long = 0L): Page {
        val owner = checkNotNull(AccountSession.current()) { "No verified Telegram account" }
        val channel = ensureChannel(owner)
        val limit = PAGE_SIZE + if (beforeMessageId != 0L) 1 else 0
        val history = TdApi.GetChatHistory().apply {
            chatId = channel
            fromMessageId = beforeMessageId
            offset = 0
            this.limit = limit
            onlyLocal = false
        }
        val result = expect<TdApi.Messages>(owner, history)
        val messages = result.messages.orEmpty().filterNotNull()
            .filter { beforeMessageId == 0L || it.id < beforeMessageId }
        val mapper = SavedMessagesRepository()
        val media = messages.mapNotNull { mapper.mapMessageToMediaInternal(it) }
        val next = messages.lastOrNull()?.id
        return Page(
            channelId = channel,
            items = media,
            nextBeforeId = next,
            hasMore = messages.size >= PAGE_SIZE && next != null
        )
    }

    suspend fun deleteFavorite(item: MediaItem) {
        val owner = checkNotNull(AccountSession.current()) { "No verified Telegram account" }
        val channel = ensureChannel(owner)
        require(item.chatId == channel && item.messageId != 0L) {
            "Only messages saved in your Favorites channel can be removed here."
        }
        val delete = TdApi.DeleteMessages().apply {
            chatId = channel
            messageIds = longArrayOf(item.messageId)
            revoke = true
        }
        expect<TdApi.Ok>(owner, delete)
    }

    suspend fun ensureChannelForCurrentAccount(): Long {
        val owner = checkNotNull(AccountSession.current()) { "No verified Telegram account" }
        return ensureChannel(owner)
    }

    private suspend fun ensureChannel(owner: AccountSnapshot): Long = channelLock.withLock {
        cachedChannel?.takeIf { it.first == owner }?.second?.let { return@withLock it }
        val localResult = request(owner, TdApi.SearchChats().apply {
            query = CHANNEL_TITLE
            limit = 100
        })
        val serverResult = request(owner, TdApi.SearchChatsOnServer().apply {
            query = CHANNEL_TITLE
            limit = 100
        })
        val candidates = ((localResult as? TdApi.Chats)?.chatIds.orEmpty().toList() +
            (serverResult as? TdApi.Chats)?.chatIds.orEmpty().toList()).distinct()
        for (id in candidates) {
            if (isOwnedFavoritesChannel(owner, id)) {
                cachedChannel = owner to id
                return@withLock id
            }
        }
        // Never create a duplicate if we could not check the server. For
        // example, an offline account with an archived channel must retry.
        if (serverResult !is TdApi.Chats) {
            throw IllegalStateException("Could not verify your Favorites channel on Telegram. Check your connection and retry.")
        }
        val created = expect<TdApi.Chat>(owner, TdApi.CreateNewSupergroupChat().apply {
            title = CHANNEL_TITLE
            isForum = false
            isChannel = true
            description = CHANNEL_MARKER
        })
        // A successful CreateNewSupergroupChat response already proves ownership.
        // Full-info propagation can lag immediately after creation; do not
        // report a failed operation and risk creating a duplicate on retry.
        check((created.type as? TdApi.ChatTypeSupergroup)?.isChannel == true) {
            "Telegram did not create a private channel."
        }
        cachedChannel = owner to created.id
        created.id
    }

    private suspend fun isOwnedFavoritesChannel(owner: AccountSnapshot, chatId: Long): Boolean {
        val chat = request(owner, TdApi.GetChat(chatId)) as? TdApi.Chat ?: return false
        if (chat.title != CHANNEL_TITLE) return false
        val type = chat.type as? TdApi.ChatTypeSupergroup ?: return false
        if (!type.isChannel) return false
        val supergroup = request(owner, TdApi.GetSupergroup(type.supergroupId)) as? TdApi.Supergroup
            ?: return false
        if (supergroup.status?.javaClass?.simpleName != "ChatMemberStatusCreator") return false
        val details = request(owner, TdApi.GetSupergroupFullInfo(type.supergroupId))
            as? TdApi.SupergroupFullInfo ?: return false
        return details.description == CHANNEL_MARKER
    }

    private suspend fun saveNow(owner: AccountSnapshot, media: MediaItem): Receipt {
        val channelId = ensureChannel(owner)
        check(channelId != media.chatId) { "This message is already in your Favorites channel." }
        val props = expect<TdApi.MessageProperties>(
            owner, TdApi.GetMessageProperties(media.chatId, media.messageId)
        )
        check(props.canBeForwarded || props.canBeCopied) {
            "Telegram protects this message from forwarding or copying."
        }
        val events = Channel<TdApi.Object>(Channel.BUFFERED)
        val listener: (TdApi.Object?) -> Unit = { update ->
            if (update is TdApi.UpdateMessageSendSucceeded &&
                update.message?.chatId == channelId) {
                events.trySend(update)
            } else if (update is TdApi.UpdateMessageSendFailed &&
                update.message?.chatId == channelId) {
                events.trySend(update)
            }
        }
        TdLibClient.addUpdateHandler(listener)
        try {
            val forwarded = expect<TdApi.Messages>(owner, TdApi.ForwardMessages().apply {
                chatId = channelId
                fromChatId = media.chatId
                messageIds = longArrayOf(media.messageId)
                // Prefer an ordinary forward for provenance; fall back to a copy
                // only if Telegram explicitly allows copying but not forwarding.
                sendCopy = !props.canBeForwarded
                removeCaption = false
            })
            val pending = forwarded.messages.orEmpty().filterNotNull().firstOrNull()
                ?: error("Telegram did not return a forwarded message.")
            val confirmed = if (pending.sendingState == null) pending else withTimeout(60_000L) {
                var result: TdApi.Message? = null
                while (result == null) {
                    when (val update = events.receive()) {
                        is TdApi.UpdateMessageSendSucceeded ->
                            if (update.oldMessageId == pending.id) result = update.message
                        is TdApi.UpdateMessageSendFailed ->
                            if (update.oldMessageId == pending.id)
                                error("Telegram could not send the favorite. Please try again.")
                    }
                }
                result
            }
            return Receipt(media.chatId, media.messageId, confirmed.id, channelId)
        } finally {
            TdLibClient.removeUpdateHandler(listener)
            events.close()
        }
    }

    private suspend fun request(owner: AccountSnapshot, query: TdApi.Function<*>): TdApi.Object? =
        TdLibClient.awaitAccountResponse(query, owner, timeoutMs = 20_000L)

    private suspend inline fun <reified T : TdApi.Object> expect(
        owner: AccountSnapshot, query: TdApi.Function<*>
    ): T {
        val value = request(owner, query)
        if (value is TdApi.Error) error(value.message ?: "Telegram rejected the operation.")
        return value as? T ?: error("Unexpected Telegram response for ${query.javaClass.simpleName}.")
    }

    private fun mediaKey(media: MediaItem): String = "${media.chatId}:${media.messageId}"
}
