package com.vaydelux.bousygram.data.database

import android.content.Context
import android.util.Log
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class RecentSearchQuery(
    val query: String,
    val normalizedQuery: String = query.trim().lowercase(),
    val chatId: Long? = null,
    val chatTitle: String? = null,
    val resultCount: Int = 0,
    val timestampMs: Long = System.currentTimeMillis()
)

/** History belongs to the verified account's Room database. Legacy global history is retained unused. */
object RecentSearchRepository {
    private const val TAG = "RecentSearchRepository"
    const val MAX_RECENT_SEARCHES = 12
    @Volatile private var appContext: Context? = null
    @Volatile private var testDatabase: AppDatabase? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutations = Mutex()
    private var roomObserveJob: Job? = null
    private val _recentSearches = MutableStateFlow<List<RecentSearchQuery>>(emptyList())
    val recentSearches: StateFlow<List<RecentSearchQuery>> = _recentSearches.asStateFlow()

    fun init(context: Context) { appContext = context.applicationContext }
    fun setDatabaseForTest(testDb: AppDatabase?) { testDatabase = testDb }

    private fun database(owner: AccountSnapshot): AppDatabase? =
        if (!AccountSession.isCurrent(owner)) null
        else testDatabase ?: appContext?.let { AppDatabase.getInstance(it, owner.accountId) }

    suspend fun activateAccount(owner: AccountSnapshot) = withContext(Dispatchers.IO) {
        val db = database(owner) ?: return@withContext
        roomObserveJob?.cancel()
        val initial = db.recentSearchDao().getRecentSearches(MAX_RECENT_SEARCHES).map { it.toDomain() }
        AccountSession.runIfCurrent(owner) { _recentSearches.value = initial }
        roomObserveJob = scope.launch {
            try {
                db.recentSearchDao().observeRecentSearches(MAX_RECENT_SEARCHES).collect { rows ->
                    AccountSession.runIfCurrent(owner) { _recentSearches.value = rows.map { it.toDomain() } }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (AccountSession.isCurrent(owner)) Log.w(TAG, "Could not observe account search history", error)
            }
        }
    }

    fun invalidateAccountResources() {
        roomObserveJob?.cancel()
        roomObserveJob = null
        _recentSearches.value = emptyList()
    }

    fun observeRecentSearches(limit: Int = MAX_RECENT_SEARCHES): Flow<List<RecentSearchQuery>> =
        recentSearches.map { it.take(limit.coerceIn(0, MAX_RECENT_SEARCHES)) }

    suspend fun getRecentSearches(chatId: Long? = null, limit: Int = MAX_RECENT_SEARCHES): List<RecentSearchQuery> {
        val owner = AccountSession.current() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val dao = database(owner)?.recentSearchDao() ?: return@withContext emptyList()
            val count = limit.coerceIn(0, MAX_RECENT_SEARCHES)
            val rows = if (chatId == null) dao.getRecentSearches(count) else dao.getRecentSearchesForChat(chatId, count)
            if (AccountSession.isCurrent(owner)) rows.map { it.toDomain() } else emptyList()
        }
    }

    suspend fun recordSearchQuery(query: String, chatId: Long? = null, chatTitle: String? = null, resultCount: Int = 0) {
        val owner = AccountSession.current() ?: return
        record(owner, query, chatId, chatTitle, resultCount)
    }

    private suspend fun record(owner: AccountSnapshot, query: String, chatId: Long?, chatTitle: String?, resultCount: Int) =
        withContext(Dispatchers.IO) {
            val clean = query.trim().take(512)
            if (clean.isBlank()) return@withContext
            mutations.withLock {
                val db = database(owner) ?: return@withLock
                val entry = RecentSearchQuery(clean, clean.lowercase(), chatId, chatTitle?.take(512),
                    resultCount.coerceAtLeast(0), System.currentTimeMillis())
                AccountSession.runIfCurrent(owner) {
                    db.runInTransaction {
                        db.recentSearchDao().insertRecentSearch(entry.toEntity())
                        db.recentSearchDao().trimToMaxCount(MAX_RECENT_SEARCHES)
                    }
                    _recentSearches.value = db.recentSearchDao().getRecentSearches(MAX_RECENT_SEARCHES).map { it.toDomain() }
                }
            }
        }

    fun recordSearchQueryAsync(query: String, chatId: Long? = null, chatTitle: String? = null, resultCount: Int = 0) {
        val owner = AccountSession.current() ?: return
        scope.launch { record(owner, query, chatId, chatTitle, resultCount) }
    }

    suspend fun removeSearchQuery(query: String) {
        val owner = AccountSession.current() ?: return
        remove(owner, query)
    }

    private suspend fun remove(owner: AccountSnapshot, query: String) = withContext(Dispatchers.IO) {
        mutations.withLock {
            val db = database(owner) ?: return@withLock
            AccountSession.runIfCurrent(owner) {
                db.recentSearchDao().deleteRecentSearch(query.trim().lowercase())
                _recentSearches.value = db.recentSearchDao().getRecentSearches(MAX_RECENT_SEARCHES).map { it.toDomain() }
            }
        }
    }

    fun removeSearchQueryAsync(query: String) {
        val owner = AccountSession.current() ?: return
        scope.launch { remove(owner, query) }
    }

    suspend fun clearRecentSearches() {
        val owner = AccountSession.current() ?: return
        clear(owner)
    }

    private suspend fun clear(owner: AccountSnapshot) = withContext(Dispatchers.IO) {
        mutations.withLock {
            val db = database(owner) ?: return@withLock
            AccountSession.runIfCurrent(owner) {
                db.recentSearchDao().clearAllRecentSearches()
                _recentSearches.value = emptyList()
            }
        }
    }

    fun clearRecentSearchesAsync() {
        val owner = AccountSession.current() ?: return
        scope.launch { clear(owner) }
    }
}

fun RecentSearchEntity.toDomain(): RecentSearchQuery = RecentSearchQuery(
    query, normalizedQuery, chatId, chatTitle, resultCount, timestampMs
)
fun RecentSearchQuery.toEntity(): RecentSearchEntity = RecentSearchEntity(
    normalizedQuery, query, chatId, chatTitle, resultCount, timestampMs
)
