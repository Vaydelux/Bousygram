package com.vaydelux.bousygram.data.database

import android.content.Context
import android.util.Log
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecentSearchQuery(
    val query: String,
    val normalizedQuery: String = query.trim().lowercase(),
    val chatId: Long? = null,
    val chatTitle: String? = null,
    val resultCount: Int = 0,
    val timestampMs: Long = System.currentTimeMillis()
)

object RecentSearchRepository {
    private const val TAG = "RecentSearchRepository"
    private const val PREFS_NAME = "recent_search_room_state"
    private const val KEY_USER_CLEARED = "user_cleared_recent_searches"
    const val MAX_RECENT_SEARCHES = 12

    private val defaultSeedQueries = listOf(
        RecentSearchQuery(query = "Sintel", chatTitle = "Saved Messages", resultCount = 2),
        RecentSearchQuery(query = "Tears of Steel", chatTitle = "Saved Messages", resultCount = 2),
        RecentSearchQuery(query = "1080p", chatTitle = "All Channels", resultCount = 8),
        RecentSearchQuery(query = "4K", chatTitle = "Saved Messages", resultCount = 2),
        RecentSearchQuery(query = "Big Buck Bunny", chatTitle = "Saved Messages", resultCount = 1)
    )

    @Volatile
    private var database: AppDatabase? = null
    @Volatile
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var roomObserveJob: Job? = null

    private val _recentSearches = MutableStateFlow<List<RecentSearchQuery>>(defaultSeedQueries)
    val recentSearches: StateFlow<List<RecentSearchQuery>> = _recentSearches.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        if (database == null) {
            database = AppDatabase.getInstance(context)
            startObservingRoom()
            scope.launch {
                migrateOrSeedIfNeeded(context.applicationContext)
                refreshSnapshot()
            }
        }
    }

    fun setDatabaseForTest(testDb: AppDatabase?) {
        database = testDb
        roomObserveJob?.cancel()
        if (testDb != null) {
            startObservingRoom()
        }
    }

    fun observeRecentSearches(limit: Int = MAX_RECENT_SEARCHES): Flow<List<RecentSearchQuery>> {
        val db = database
        return if (db != null) {
            db.recentSearchDao()
                .observeRecentSearches(limit)
                .map { list -> list.map { it.toDomain() } }
                .catch { e ->
                    Log.w(TAG, "Error observing recent searches from Room: ${e.message}")
                    emit(_recentSearches.value.take(limit))
                }
        } else {
            _recentSearches.map { it.take(limit) }
        }
    }

    private fun startObservingRoom() {
        val db = database ?: return
        roomObserveJob?.cancel()
        roomObserveJob = scope.launch {
            runCatching {
                db.recentSearchDao()
                    .observeRecentSearches(MAX_RECENT_SEARCHES)
                    .collect { entities ->
                        if (entities.isNotEmpty() || isUserCleared()) {
                            _recentSearches.value = entities.map { it.toDomain() }
                        }
                    }
            }.onFailure { e ->
                Log.w(TAG, "Room Flow observation failed: ${e.message}")
            }
        }
    }

    private fun isUserCleared(): Boolean {
        val ctx = appContext ?: return false
        return runCatching {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_USER_CLEARED, false)
        }.getOrDefault(false)
    }

    private fun setUserCleared(cleared: Boolean) {
        val ctx = appContext ?: return
        runCatching {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_USER_CLEARED, cleared)
                .apply()
        }
    }

    private suspend fun migrateOrSeedIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        runCatching {
            val dao = db.recentSearchDao()
            if (dao.getRecentSearchCount() == 0) {
                val legacyQueries = runCatching { EnterprisePreferences.recentQueries() }.getOrDefault(emptyList())
                val now = System.currentTimeMillis()
                if (legacyQueries.isNotEmpty()) {
                    val entities = legacyQueries.mapIndexedNotNull { index, raw ->
                        val clean = raw.trim()
                        if (clean.isBlank()) null
                        else RecentSearchEntity(
                            clean.lowercase(),
                            clean,
                            null,
                            null,
                            0,
                            now - (index * 1000L)
                        )
                    }
                    if (entities.isNotEmpty()) {
                        dao.insertRecentSearches(entities)
                        dao.trimToMaxCount(MAX_RECENT_SEARCHES)
                        return@runCatching
                    }
                }
                val clearedByUser = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(KEY_USER_CLEARED, false)
                if (!clearedByUser) {
                    val seedEntities = defaultSeedQueries.mapIndexed { index, item ->
                        item.copy(timestampMs = now - (index * 1000L)).toEntity()
                    }
                    dao.insertRecentSearches(seedEntities)
                    dao.trimToMaxCount(MAX_RECENT_SEARCHES)
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "Could not migrate or seed recent search queries: ${e.message}")
        }
    }

    suspend fun getRecentSearches(
        chatId: Long? = null,
        limit: Int = MAX_RECENT_SEARCHES
    ): List<RecentSearchQuery> = withContext(Dispatchers.IO) {
        val db = database
        if (db == null) {
            return@withContext _recentSearches.value
                .filter { chatId == null || it.chatId == null || it.chatId == chatId }
                .take(limit)
        }
        runCatching {
            val dao = db.recentSearchDao()
            val entities = if (chatId != null) {
                dao.getRecentSearchesForChat(chatId, limit)
            } else {
                dao.getRecentSearches(limit)
            }
            entities.map { it.toDomain() }
        }.getOrElse { e ->
            Log.w(TAG, "Error loading recent searches from Room: ${e.message}")
            _recentSearches.value.take(limit)
        }
    }

    suspend fun recordSearchQuery(
        query: String,
        chatId: Long? = null,
        chatTitle: String? = null,
        resultCount: Int = 0
    ) = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext
        setUserCleared(false)
        val normalized = clean.lowercase()
        val now = System.currentTimeMillis()
        val existing = _recentSearches.value.firstOrNull { it.normalizedQuery == normalized }
        val resolvedCount = if (resultCount > 0) resultCount else (existing?.resultCount ?: 0)
        val entry = RecentSearchQuery(
            query = clean,
            normalizedQuery = normalized,
            chatId = chatId ?: existing?.chatId,
            chatTitle = chatTitle?.takeIf { it.isNotBlank() } ?: existing?.chatTitle,
            resultCount = resolvedCount.coerceAtLeast(0),
            timestampMs = now
        )

        // Update in-memory StateFlow immediately for responsive UI
        val updated = (listOf(entry) + _recentSearches.value.filterNot { it.normalizedQuery == normalized })
            .take(MAX_RECENT_SEARCHES)
        _recentSearches.value = updated

        runCatching { EnterprisePreferences.rememberQuery(clean) }

        val db = database ?: return@withContext
        runCatching {
            val dao = db.recentSearchDao()
            dao.insertRecentSearch(entry.toEntity())
            dao.trimToMaxCount(MAX_RECENT_SEARCHES)
            _recentSearches.value = dao.getRecentSearches(MAX_RECENT_SEARCHES).map { it.toDomain() }
        }.onFailure { e ->
            Log.w(TAG, "Failed to persist recent search query to Room: ${e.message}")
        }
    }

    fun recordSearchQueryAsync(
        query: String,
        chatId: Long? = null,
        chatTitle: String? = null,
        resultCount: Int = 0
    ) {
        scope.launch {
            recordSearchQuery(query, chatId, chatTitle, resultCount)
        }
    }

    suspend fun removeSearchQuery(query: String) = withContext(Dispatchers.IO) {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return@withContext
        val remaining = _recentSearches.value.filterNot { it.normalizedQuery == normalized }
        if (remaining.isEmpty()) setUserCleared(true)
        _recentSearches.value = remaining
        val db = database ?: return@withContext
        runCatching {
            val dao = db.recentSearchDao()
            dao.deleteRecentSearch(normalized)
            _recentSearches.value = dao.getRecentSearches(MAX_RECENT_SEARCHES).map { it.toDomain() }
        }.onFailure { e ->
            Log.w(TAG, "Failed to delete recent search query from Room: ${e.message}")
        }
    }

    fun removeSearchQueryAsync(query: String) {
        scope.launch {
            removeSearchQuery(query)
        }
    }

    suspend fun clearRecentSearches() = withContext(Dispatchers.IO) {
        setUserCleared(true)
        _recentSearches.value = emptyList()
        runCatching { EnterprisePreferences.clearRecentQueries() }
        val db = database ?: return@withContext
        runCatching {
            db.recentSearchDao().clearAllRecentSearches()
        }.onFailure { e ->
            Log.w(TAG, "Failed to clear recent searches from Room: ${e.message}")
        }
    }

    fun clearRecentSearchesAsync() {
        scope.launch {
            clearRecentSearches()
        }
    }

    private suspend fun refreshSnapshot() = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        runCatching {
            val list = db.recentSearchDao().getRecentSearches(MAX_RECENT_SEARCHES).map { it.toDomain() }
            _recentSearches.value = list
        }
    }
}

fun RecentSearchEntity.toDomain(): RecentSearchQuery = RecentSearchQuery(
    query = query,
    normalizedQuery = normalizedQuery,
    chatId = chatId,
    chatTitle = chatTitle,
    resultCount = resultCount,
    timestampMs = timestampMs
)

fun RecentSearchQuery.toEntity(): RecentSearchEntity = RecentSearchEntity(
    normalizedQuery,
    query,
    chatId,
    chatTitle,
    resultCount,
    timestampMs
)
