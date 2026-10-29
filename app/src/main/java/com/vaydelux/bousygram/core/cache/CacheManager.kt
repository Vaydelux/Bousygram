package com.vaydelux.bousygram.core.cache

import android.content.Context
import android.content.SharedPreferences
import android.os.StatFs
import android.util.Log
import androidx.core.content.edit
import com.vaydelux.bousygram.app.BousygramApp
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.media.PlaybackSessionController
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.core.tdlib.prefetch.FocusPrefetchManager
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler
import com.vaydelux.bousygram.core.tdlib.server.TelegramPreCacheEngine
import com.vaydelux.bousygram.core.tdlib.utils.PlaybackProgressTracker
import com.vaydelux.bousygram.data.database.AppDatabase
import com.vaydelux.bousygram.data.repository.ContinueWatchingRepository
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * Unified CacheManager consolidating offline file tracking, dynamic disk quota management,
 * sliding read-ahead windowing, and safe LRU eviction for TDLib media.
 *
 * Tailored for budget Android TV devices with constrained flash storage (e.g., 8 GB eMMC).
 */
object CacheManager {
    private const val TAG = "CacheManager"
    private const val PREFS_NAME = "tele_cache_prefs"
    private const val KEY_PINNED = "pinned_files"
    private const val KEY_ACCESS_PREFIX = "access_time_"

    // Sizing constants
    const val MAX_CACHE_LIMIT_BYTES = 2L * 1024L * 1024L * 1024L // Eviction target ceiling; not a TDLib write cap.
    @Deprecated("The active quota has no minimum floor")
    const val MIN_CACHE_LIMIT_BYTES = 256L * 1024L * 1024L // Legacy compatibility only.
    const val DEFAULT_STORAGE_FRACTION = 0.15 // Fraction of available internal app-data storage.
    const val SLIDING_WINDOW_BYTES = 32L * 1024L * 1024L // 32 MB forward read-ahead window
    const val HEAD_PRELOAD_BYTES = 8L * 1024L * 1024L // 8 MB head preload for next episode

    private const val TD_API_TIMEOUT_MS = 4_000L
    private const val MAX_TRACKED_FILES_PER_SWEEP = 256
    private const val MAX_PINNED_FILES_TO_INSPECT = 64
    private const val RECENT_ACCESS_PROTECTION_MS = 2 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cleanupExecutor = Executors.newSingleThreadExecutor()
    private val cleanupLock = Any()

    private val prefs: SharedPreferences?
        get() = runCatching {
            val account = AccountSession.current() ?: return@runCatching null
            BousygramApp.instance.getSharedPreferences("${PREFS_NAME}_${account.accountId}", Context.MODE_PRIVATE)
        }.getOrNull()

    private val pinnedFiles = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    private val preloadedNextEpisodes = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    private val isEvictionScheduled = AtomicBoolean(false)
    private var pinnedAccountId: Long? = null

    @Synchronized
    private fun ensurePinnedLoaded() {
        val accountId = AccountSession.current()?.accountId
        if (pinnedAccountId != accountId) {
            pinnedFiles.clear()
            pinnedAccountId = accountId
        }
        if (accountId != null && pinnedFiles.isEmpty()) {
            val pinnedStrSet = prefs?.getStringSet(KEY_PINNED, emptySet()) ?: emptySet()
            pinnedStrSet.forEach { it.toIntOrNull()?.let(pinnedFiles::add) }
        }
    }

    @Synchronized
    fun invalidateAccountResources() {
        if (pinnedFiles.isNotEmpty()) {
            val client = TdLibClient.client
            pinnedFiles.toList().forEach { id -> runCatching { client.send(TdApi.CancelDownloadFile(id, false)) { } } }
        }
        pinnedFiles.clear()
        pinnedAccountId = null
        preloadedNextEpisodes.clear()
    }

    // --- Dynamic Quota Calculation ---

    /**
     * Calculates the temporary-cache target from free space and an optional managed ceiling.
     * The target never exceeds the free-space fraction; low storage is allowed to yield zero.
     */
    fun calculateDynamicQuota(context: Context? = null): Long {
        val ctx = context ?: BousygramApp.instance
        // TDLib media is stored under filesDir/tdlib_files. Stat the same app-data
        // filesystem rather than assuming cacheDir is the storage target.
        val availableBytes = StatFs(ctx.filesDir.absolutePath).availableBytes
        return calculateQuota(availableBytes, EnterprisePreferences.cacheLimitBytes())
    }

    internal fun calculateQuota(availableBytes: Long, managedCeilingBytes: Long?): Long {
        val storageBudget = (availableBytes.coerceAtLeast(0L) * DEFAULT_STORAGE_FRACTION).toLong()
        val ceiling = min(MAX_CACHE_LIMIT_BYTES, managedCeilingBytes?.coerceAtLeast(0L) ?: MAX_CACHE_LIMIT_BYTES)
        return min(storageBudget, ceiling)
    }

    // --- Offline Pinning ---

    fun isPinned(fileId: Int): Boolean {
        ensurePinnedLoaded()
        return pinnedFiles.contains(fileId)
    }

    @Synchronized
    fun pinFile(fileId: Int) {
        if (fileId <= 0) return
        val account = AccountSession.current() ?: return
        ensurePinnedLoaded()
        pinnedFiles.add(fileId)
        savePinned()
        Log.i(TAG, "Pinning file $fileId for offline playback")
        TdLibClient.client.send(TdApi.DownloadFile(fileId, 32, 0, 0, false)) { result ->
            if (!AccountSession.isCurrent(account)) return@send
            if (result is TdApi.Error) {
                Log.w(TAG, "Offline download failed fileId=$fileId code=${result.code} message=${result.message}")
            }
        }
    }

    @Synchronized
    fun unpinFile(fileId: Int) {
        ensurePinnedLoaded()
        pinnedFiles.remove(fileId)
        savePinned()
        Log.i(TAG, "Unpinned file $fileId")
        triggerCacheCleanupIfNeeded()
    }

    private fun savePinned() {
        prefs?.edit { putStringSet(KEY_PINNED, pinnedFiles.map { it.toString() }.toSet()) }
    }

    // --- Access Tracking ---

    fun markFileAccessed(fileId: Int) {
        if (fileId <= 0 || AccountSession.current() == null) return
        prefs?.edit { putLong(KEY_ACCESS_PREFIX + fileId, System.currentTimeMillis()) }
        triggerCacheCleanupIfNeeded()
    }

    // --- Sliding Read-Ahead Window & Seek ---

    /**
     * Called when a seek occurs. Cancels out-of-window requests outside the 32 MB window.
     */
    fun onSeek(fileId: Int, newPositionMs: Long, totalSize: Long, durationMs: Long) {
        if (fileId <= 0) return
        val offset = if (durationMs > 0L && totalSize > 0L) {
            ((newPositionMs.coerceAtLeast(0L) * totalSize) / durationMs).coerceIn(0L, totalSize - 1)
        } else {
            min((newPositionMs.coerceAtLeast(0L) / 1000L) * 512_000L, (totalSize - 1).coerceAtLeast(0L))
        }
        TelegramPreCacheEngine.onSeek(fileId, offset, SLIDING_WINDOW_BYTES)
        LocalStreamingServer.prefetchAtPosition(fileId, totalSize, newPositionMs, durationMs)
    }

    // --- Next-Episode 80% Head Preload ---

    /**
     * Preloads the head (first 8 MB) of the next queued item once playback reaches >= 80% of current item.
     */
    fun onPlaybackProgress(
        currentFileId: Int,
        currentPositionMs: Long,
        durationMs: Long,
        nextItemFileId: Int?,
        nextItemTotalSize: Long = 0L
    ) {
        if (nextItemFileId == null || nextItemFileId <= 0) return
        if (durationMs > 0L && currentPositionMs >= (durationMs * 0.80)) {
            preloadNextEpisode(nextItemFileId, nextItemTotalSize)
        }
    }

    fun preloadNextEpisode(nextFileId: Int, totalSize: Long = 0L) {
        if (nextFileId <= 0 || AccountSession.current() == null) return
        if (preloadedNextEpisodes.add(nextFileId)) {
            Log.i(TAG, "Preloading head of next episode fileId=$nextFileId (8 MB burst)")
            TelegramPreCacheEngine.primeVideoCache(
                tdLibClient = TdLibClient.client,
                fileId = nextFileId,
                totalSize = totalSize
            )
        }
    }

    fun clearPreloadTracking() {
        preloadedNextEpisodes.clear()
    }

    // --- Eviction & Cleanup ---

    fun triggerCacheCleanupIfNeeded() {
        val account = AccountSession.current() ?: return
        if (isEvictionScheduled.compareAndSet(false, true)) {
            cleanupExecutor.execute {
                try {
                    synchronized(cleanupLock) {
                        if (AccountSession.isCurrent(account)) performCacheCleanup()
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Cache cleanup error", t)
                } finally {
                    isEvictionScheduled.set(false)
                }
            }
        }
    }

    fun pruneTrackedCacheNow(): TrackedCacheCleanupResult = synchronized(cleanupLock) {
        performCacheCleanup()
    }

    fun storageStats(context: Context, tdLibCacheDir: File): TelegramCacheStorageStats {
        val appCacheBytes = directorySize(context.cacheDir)
        val tdLibBytes = directorySizeOrZero(tdLibCacheDir)
        val appFilesBytes = (directorySize(context.filesDir) - tdLibBytes).coerceAtLeast(0L)
        return TelegramCacheStorageStats(
            appCacheBytes = appCacheBytes,
            appFilesBytes = appFilesBytes,
            tdLibCacheBytes = tdLibBytes,
            cacheLimitBytes = calculateDynamicQuota(context)
        )
    }

    private fun performCacheCleanup(requestedLimitBytes: Long? = null): TrackedCacheCleanupResult {
        val account = AccountSession.current()
            ?: return TrackedCacheCleanupResult(0, 0L, 0L, 0L, false)
        ensurePinnedLoaded()
        val cacheLimit = min(calculateDynamicQuota(), requestedLimitBytes?.coerceAtLeast(0L) ?: Long.MAX_VALUE)
        val allPrefs = prefs?.all ?: emptyMap()
        val accessed = allPrefs.entries.mapNotNull { (key, value) ->
            if (!key.startsWith(KEY_ACCESS_PREFIX)) return@mapNotNull null
            val id = key.removePrefix(KEY_ACCESS_PREFIX).toIntOrNull() ?: return@mapNotNull null
            val time = value as? Long ?: return@mapNotNull null
            id to time
        }.distinctBy { it.first }.sortedBy { it.second }

        val pinnedSnapshot = pinnedFiles.toList().take(MAX_PINNED_FILES_TO_INSPECT)
        val accessCapacity = (MAX_TRACKED_FILES_PER_SWEEP - pinnedSnapshot.size).coerceAtLeast(0)
        val oldAccessIds = accessed.asSequence()
            .map { it.first }
            .filterNot { pinnedFiles.contains(it) }
            .take(accessCapacity)
            .toList()
        val fileIds = (oldAccessIds + pinnedSnapshot).distinct()
        val fileById = getFilesBlocking(fileIds, account)
        if (!AccountSession.isCurrent(account)) return TrackedCacheCleanupResult(0, 0L, 0L, cacheLimit, false)
        val files = fileIds.mapNotNull { id -> fileById[id]?.let { id to it } }
        var totalSize = files.sumOf { (_, file) -> file.local.downloadedSize.coerceAtLeast(0L) }
        val initialTrackedBytes = totalSize

        var deletedFiles = 0
        var freedBytes = 0L
        val accessTime = accessed.toMap()
        val now = System.currentTimeMillis()

        val continueWatchingIds = runCatching {
            ContinueWatchingRepository.getContinueWatchingFileIds()
        }.getOrDefault(emptySet())
        val lastPlayedId = EnterprisePreferences.lastPlayed()?.fileId

        val safeCandidates = files
            .filter { (id, file) ->
                !pinnedFiles.contains(id) &&
                    !PlaybackSessionController.isFileActive(id) &&
                    !continueWatchingIds.contains(id) &&
                    id != lastPlayedId &&
                    !TelegramPreCacheEngine.isCaching(id) &&
                    TelegramChunkScheduler.activeLeaseCount(id) == 0 &&
                    TelegramChunkScheduler.activeChunkCount(id) == 0 &&
                    !file.local.isDownloadingActive &&
                    !FocusPrefetchManager.isPrefetching(id) &&
                    !file.local.path.isNullOrBlank() && file.local.downloadedSize > 0L
            }

        val plannedDeletions = mutableListOf<Pair<Int, Long>>()
        var plannedTotal = totalSize

        // Phase 1: Evict watched/completed media first
        val watchedCandidates = safeCandidates.filter { (id, _) -> isWatched(id) }
        for ((fileId, file) in watchedCandidates) {
            val fileSize = file.local.downloadedSize.coerceAtLeast(0L)
            if (fileSize > 0L) {
                plannedDeletions += fileId to fileSize
                plannedTotal = (plannedTotal - fileSize).coerceAtLeast(0L)
                Log.i(TAG, "Queued watched media for automatic eviction fileId=$fileId sizeMiB=${fileSize / (1024 * 1024)}")
            }
        }

        // Phase 2: If still above dynamic quota, evict oldest accessed files
        if (plannedTotal > cacheLimit) {
            val remainingCandidates = safeCandidates
                .filterNot { (id, _) -> plannedDeletions.any { it.first == id } }
                .filter { (id, _) ->
                    accessTime[id]?.let { now - it >= RECENT_ACCESS_PROTECTION_MS } ?: true
                }
                .sortedBy { (id, _) -> accessTime[id] ?: 0L }

            for ((fileId, file) in remainingCandidates) {
                if (plannedTotal <= cacheLimit) break
                val fileSize = file.local.downloadedSize.coerceAtLeast(0L)
                if (fileSize <= 0L) continue
                plannedDeletions += fileId to fileSize
                plannedTotal = (plannedTotal - fileSize).coerceAtLeast(0L)
            }
        }

        if (plannedDeletions.isEmpty()) {
            return TrackedCacheCleanupResult(0, 0L, totalSize, cacheLimit, initiallyOverLimit = initialTrackedBytes > cacheLimit)
        }

        val confirmedDeletions = deleteFilesBlocking(plannedDeletions.map { it.first }, account)
        val plannedSizes = plannedDeletions.toMap()
        confirmedDeletions.forEach { fileId ->
            val size = plannedSizes[fileId] ?: 0L
            if (size > 0L) {
                totalSize = (totalSize - size).coerceAtLeast(0L)
                freedBytes += size
                deletedFiles++
                Log.i(TAG, "Evicted TDLib cache fileId=$fileId sizeMiB=${size / (1024 * 1024)}")
            }
        }

        return TrackedCacheCleanupResult(
            deletedFiles = deletedFiles,
            freedBytes = freedBytes,
            remainingTrackedBytes = totalSize,
            limitBytes = cacheLimit,
            initiallyOverLimit = initialTrackedBytes > cacheLimit
        )
    }

    fun isWatched(fileId: Int): Boolean {
        val account = AccountSession.current() ?: return false
        if (PlaybackProgressTracker.isWatched(fileId)) return true
        return runCatching {
            val entity = AppDatabase.getInstance(BousygramApp.instance, account.accountId).videoDao().getVideoByFileId(fileId)
            if (!AccountSession.isCurrent(account)) return@runCatching false
            if (entity != null) {
                val durationMs = entity.duration * 1000L
                PlaybackProgressTracker.isWatched(entity.chatId, entity.messageId, durationMs)
            } else false
        }.getOrDefault(false)
    }

    // Native OptimizeStorage cannot exclude individual pinned files or active leases.
    // Keep this compatibility entry point, but use our protected tracked-file eviction.

    fun optimizeStorageNative(
        targetSizeBytes: Long = calculateDynamicQuota(),
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        val account = AccountSession.current() ?: run {
            onComplete?.invoke(false, "Sign in before cleaning cached media")
            return
        }
        cleanupExecutor.execute {
            runCatching {
                synchronized(cleanupLock) {
                    check(AccountSession.isCurrent(account)) { "The active account changed" }
                    performCacheCleanup(targetSizeBytes)
                }
            }.onSuccess { result ->
                if (AccountSession.isCurrent(account)) onComplete?.invoke(
                    true,
                    "Protected cache cleanup removed ${result.deletedFiles} files (${result.freedBytes} bytes)"
                )
            }.onFailure {
                if (AccountSession.isCurrent(account)) onComplete?.invoke(false, "Cache cleanup could not complete")
            }
        }
    }

    // --- Internal Blocking Helpers ---

    private fun getFilesBlocking(fileIds: List<Int>, account: AccountSnapshot): Map<Int, TdApi.File> {
        if (fileIds.isEmpty()) return emptyMap()
        if (!AccountSession.isCurrent(account)) return emptyMap()
        val client = TdLibClient.client
        val results = ConcurrentHashMap<Int, TdApi.File>()
        val latch = CountDownLatch(fileIds.size)
        fileIds.forEach { fileId ->
            try {
                if (!AccountSession.isCurrent(account)) { latch.countDown(); return@forEach }
                client.send(TdApi.GetFile(fileId)) { response ->
                    if (!AccountSession.isCurrent(account)) { latch.countDown(); return@send }
                    when (response) {
                        is TdApi.File -> results[fileId] = response
                        is TdApi.Error -> {
                            prefs?.edit { remove(KEY_ACCESS_PREFIX + fileId) }
                            Log.d(TAG, "Dropping stale cache tracking fileId=$fileId (${response.code})")
                        }
                    }
                    latch.countDown()
                }
            } catch (error: Exception) {
                Log.w(TAG, "Could not request TDLib file status fileId=$fileId", error)
                latch.countDown()
            }
        }
        latch.await(TD_API_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return results.toMap()
    }

    private fun deleteFilesBlocking(fileIds: List<Int>, account: AccountSnapshot): Set<Int> {
        if (fileIds.isEmpty()) return emptySet()
        if (!AccountSession.isCurrent(account)) return emptySet()
        val client = TdLibClient.client
        val eligibleFileIds = fileIds.filterNot { fileId ->
            pinnedFiles.contains(fileId) || PlaybackSessionController.isFileActive(fileId) ||
                TelegramPreCacheEngine.isCaching(fileId) || TelegramChunkScheduler.activeChunkCount(fileId) > 0 ||
                TelegramChunkScheduler.activeLeaseCount(fileId) > 0 ||
                FocusPrefetchManager.isPrefetching(fileId)
        }
        if (eligibleFileIds.isEmpty()) return emptySet()
        val confirmed = ConcurrentHashMap.newKeySet<Int>()
        val latch = CountDownLatch(eligibleFileIds.size)
        eligibleFileIds.forEach { fileId ->
            try {
                if (!AccountSession.isCurrent(account)) { latch.countDown(); return@forEach }
                client.send(TdApi.DeleteFile(fileId)) { response ->
                    if (!AccountSession.isCurrent(account)) { latch.countDown(); return@send }
                    if (response is TdApi.Ok) {
                        confirmed.add(fileId)
                        prefs?.edit { remove(KEY_ACCESS_PREFIX + fileId) }
                    } else if (response is TdApi.Error) {
                        Log.w(TAG, "TDLib refused cache eviction fileId=$fileId code=${response.code} message=${response.message}")
                    }
                    latch.countDown()
                }
            } catch (error: Exception) {
                Log.w(TAG, "Could not request TDLib cache eviction fileId=$fileId", error)
                latch.countDown()
            }
        }
        latch.await(TD_API_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return confirmed.toSet()
    }

    private fun directorySizeOrZero(file: File): Long =
        runCatching { directorySize(file) }.getOrDefault(0L)

    private fun directorySize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { directorySize(it) } ?: 0L
    }
}

data class TrackedCacheCleanupResult(
    val deletedFiles: Int,
    val freedBytes: Long,
    val remainingTrackedBytes: Long,
    val limitBytes: Long,
    val initiallyOverLimit: Boolean
)

data class TelegramCacheStorageStats(
    val appCacheBytes: Long,
    val appFilesBytes: Long,
    val tdLibCacheBytes: Long,
    val cacheLimitBytes: Long
) {
    val totalBytes: Long get() = appCacheBytes + appFilesBytes + tdLibCacheBytes
}

data class TelegramCachePruneResult(
    val deletedFiles: Int,
    val freedBytes: Long,
    val remainingBytes: Long,
    val limitBytes: Long,
    val note: String
)
