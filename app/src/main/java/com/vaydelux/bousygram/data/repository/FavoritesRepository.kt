package com.vaydelux.bousygram.data.repository

import android.content.Context
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.data.database.AppDatabase
import com.vaydelux.bousygram.data.database.FavoriteEntity
import com.vaydelux.bousygram.data.database.toMediaItem
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Single account-scoped source of truth. All database work is off the UI thread. */
@OptIn(ExperimentalCoroutinesApi::class)
object FavoritesRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Mutex()
    private data class Mutation(val owner: AccountSnapshot,
        val action: (AccountSnapshot, AppDatabase) -> Unit, val completion: CompletableJob)
    private val mutations = Channel<Mutation>(Channel.UNLIMITED)
    private lateinit var application: android.app.Application
    private val _keys = MutableStateFlow<Set<String>>(emptySet())
    val keys = _keys.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors = _errors.asSharedFlow()
    @Volatile private var publishedOwner: AccountSnapshot? = null

    fun init(appContext: Context) {
        if (::application.isInitialized) return
        application = appContext.applicationContext as android.app.Application
        scope.launch {
            for (request in mutations) {
                try {
                    if (AccountSession.isCurrent(request.owner)) {
                        val db = migratedDatabase(request.owner)
                        writes.withLock {
                            AccountSession.runIfCurrent(request.owner) { request.action(request.owner, db) }
                        }
                    }
                    request.completion.complete()
                } catch (cancelled: CancellationException) {
                    request.completion.cancel()
                    throw cancelled
                } catch (error: Exception) {
                    if (AccountSession.isCurrent(request.owner)) report(error)
                    request.completion.completeExceptionally(error)
                }
            }
        }
        AccountSession.registerInvalidationHandler {
            publishedOwner = null
            _keys.value = emptySet()
            _ready.value = false
        }
        scope.launch {
            AccountSession.state.collectLatest { owner ->
                publishedOwner = null
                _keys.value = emptySet()
                _ready.value = false
                if (owner != null) {
                    flow {
                        val db = migratedDatabase(owner)
                        emitAll(db.favoriteDao().observeKeys().distinctUntilChanged())
                    }.retryWhen { error, attempt ->
                        if (!AccountSession.isCurrent(owner)) false else {
                            if (attempt == 0L) report(error as? Exception ?: Exception(error))
                            delay(minOf(30_000L, 1_000L * (attempt + 1)))
                            true
                        }
                    }.collect { values ->
                        AccountSession.runIfCurrent(owner) {
                            publishedOwner = owner
                            _keys.value = values.toSet()
                            _ready.value = true
                        }
                    }
                }
            }
        }
    }

    fun currentKeys(): Set<String> =
        if (publishedOwner == AccountSession.current()) _keys.value else emptySet()

    private suspend fun migratedDatabase(owner: AccountSnapshot): AppDatabase = writes.withLock {
        var result: AppDatabase? = null
        check(AccountSession.runIfCurrent(owner) {
            val db = AppDatabase.getInstance(application, owner.accountId)
            val prefs = application.getSharedPreferences("account_${owner.accountId}_enterprise", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("favorites_room_v3", false)) {
                // Keep the legacy document for recovery. A committed marker makes migration one-shot.
                val legacy = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()
                db.runInTransaction {
                    legacy.forEach { key -> parseKey(key)?.let { (chat, message) ->
                        db.favoriteDao().insert(FavoriteEntity().apply { chatId = chat; messageId = message })
                        db.videoDao().getVideo(chat, message)?.let { copyMetadata(db, it.toMediaItem()) }
                    } }
                }
                check(prefs.edit().putBoolean("favorites_room_v3", true).commit()) {
                    "Could not finish favorites migration"
                }
            }
            result = db
        }) { "Account changed" }
        checkNotNull(result)
    }

    fun observePage(limit: Int, offset: Int = 0): Flow<List<FavoriteEntity>> = AccountSession.state.flatMapLatest { owner ->
        if (owner == null) flowOf(emptyList()) else flow {
            val db = migratedDatabase(owner)
            emitAll(db.favoriteDao().observePage(limit, offset).map { rows ->
                if (AccountSession.isCurrent(owner)) rows else emptyList()
            })
        }
    }.flowOn(Dispatchers.IO)

    fun setFavorite(item: MediaItem, enabled: Boolean) = mutate { owner, db ->
        val dao = db.favoriteDao()
        if (enabled) {
            dao.insert(FavoriteEntity().apply {
                chatId = item.chatId; messageId = item.messageId; addedAt = System.currentTimeMillis()
            })
            copyMetadata(db, item)
        } else dao.remove(item.chatId, item.messageId)
    }

    fun remove(chatId: Long, messageId: Long) = mutate { _, db -> db.favoriteDao().remove(chatId, messageId) }

    fun replaceKeys(values: Set<String>) = mutate { _, db ->
        db.runInTransaction {
            db.favoriteDao().clear()
            values.forEach { key -> parseKey(key)?.let { (chat, message) ->
                db.favoriteDao().insert(FavoriteEntity().apply {
                    chatId = chat; messageId = message; addedAt = System.currentTimeMillis()
                })
            } }
        }
    }

    private fun mutate(action: (AccountSnapshot, AppDatabase) -> Unit): Job {
        val owner = AccountSession.current() ?: return Job().apply { complete() }
        val completion = Job()
        check(mutations.trySend(Mutation(owner, action, completion)).isSuccess)
        return completion
    }

    private fun copyMetadata(db: AppDatabase, item: MediaItem) {
        db.favoriteDao().updateMetadata(
            item.chatId, item.messageId, item.title.ifBlank { "Saved media" }, item.fileId,
            item.messageThreadId, item.tmdbPosterPath ?: item.thumbnailPath,
            item.miniThumbnailBytes, item.durationSeconds, item.fileSizeBytes,
            item.type.name, item.fileName, item.mimeType, item.trustedDurationMs
        ) // UPDATE cannot resurrect a favorite removed during a lookup.
    }

    suspend fun updateMetadata(owner: AccountSnapshot, item: MediaItem) = withContext(Dispatchers.IO) {
        AccountSession.runIfCurrent(owner) { copyMetadata(AppDatabase.getInstance(application, owner.accountId), item) }
        Unit
    }

    suspend fun markUnavailable(owner: AccountSnapshot, chatId: Long, messageId: Long) = withContext(Dispatchers.IO) {
        AccountSession.runIfCurrent(owner) {
            AppDatabase.getInstance(application, owner.accountId).favoriteDao().markUnavailable(chatId, messageId)
        }
        Unit
    }

    private fun report(error: Exception) {
        android.util.Log.e("FavoritesRepository", "Favorites operation failed", error)
        _errors.tryEmit("Could not update favorites. Please try again.")
    }

    internal fun parseKey(key: String): Pair<Long, Long>? {
        val parts = key.split(':')
        if (parts.size != 2) return null
        val chat = parts[0].toLongOrNull()?.takeIf { it != 0L } ?: return null
        val message = parts[1].toLongOrNull()?.takeIf { it != 0L } ?: return null
        return chat to message
    }
}

fun FavoriteEntity.toFavoriteMedia() = MediaItem(
    chatId = chatId, messageId = messageId, date = 0,
    type = MediaType.entries.firstOrNull { it.name == mediaType } ?: MediaType.Video,
    title = title, fileId = fileId, messageThreadId = threadId,
    thumbnailPath = poster, miniThumbnailBytes = miniThumbnail,
    durationSeconds = durationSeconds, fileSizeBytes = fileSizeBytes,
    fileName = fileName, mimeType = mimeType, trustedDurationMs = trustedDurationMs
)
