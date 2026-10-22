package com.vaydelux.bousygram.core.tdlib.cache

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.vaydelux.bousygram.app.BousygramApp
import com.vaydelux.bousygram.core.media.PlaybackSessionController
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler
import com.vaydelux.bousygram.core.tdlib.server.TelegramPreCacheEngine
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import org.drinkless.tdlib.TdApi
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Tracks offline-pinned files and performs conservative LRU eviction via TDLib. */
object OfflineCacheManager {
    private const val TAG = "OfflineCacheManager"
    private const val PREFS_NAME = "tele_cache_prefs"
    private const val KEY_PINNED = "pinned_files"
    private const val KEY_ACCESS_PREFIX = "access_time_"
    private const val TD_API_TIMEOUT_MS = 4_000L
    private const val MAX_TRACKED_FILES_PER_SWEEP = 256
    private const val MAX_PINNED_FILES_TO_INSPECT = 64
    private const val RECENT_ACCESS_PROTECTION_MS = 2 * 60 * 1000L

    // Kept for callers that display the historical default; runtime cleanup uses user preferences.
    const val DEFAULT_MAX_CACHE_SIZE = 5L * 1024 * 1024 * 1024

    private val prefs by lazy {
        BousygramApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val pinnedFiles = Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Int, Boolean>())
    private val cleanupExecutor = Executors.newSingleThreadExecutor()
    private val cleanupLock = Any()

    init {
        val pinnedStrSet = prefs.getStringSet(KEY_PINNED, emptySet()) ?: emptySet()
        pinnedStrSet.forEach { it.toIntOrNull()?.let(pinnedFiles::add) }
    }

    fun isPinned(fileId: Int): Boolean = pinnedFiles.contains(fileId)

    @Synchronized
    fun pinFile(fileId: Int) {
        if (fileId <= 0) return
        pinnedFiles.add(fileId)
        savePinned()
        Log.i(TAG, "Pinning file $fileId for offline playback")
        TdLibClient.client.send(TdApi.DownloadFile(fileId, 32, 0, 0, false)) { result ->
            if (result is TdApi.Error) {
                Log.w(TAG, "Offline download failed fileId=$fileId code=${result.code} message=${result.message}")
            }
        }
    }

    @Synchronized
    fun unpinFile(fileId: Int) {
        pinnedFiles.remove(fileId)
        savePinned()
        Log.i(TAG, "Unpinned file $fileId")
        triggerCacheCleanupIfNeeded()
    }

    fun markFileAccessed(fileId: Int) {
        if (fileId <= 0) return
        prefs.edit { putLong(KEY_ACCESS_PREFIX + fileId, System.currentTimeMillis()) }
        triggerCacheCleanupIfNeeded()
    }

    private fun savePinned() {
        prefs.edit { putStringSet(KEY_PINNED, pinnedFiles.map { it.toString() }.toSet()) }
    }

    private fun triggerCacheCleanupIfNeeded() {
        runCatching {
            cleanupExecutor.execute {
                runCatching { synchronized(cleanupLock) { performCacheCleanup() } }
                    .onFailure { Log.e(TAG, "Cache cleanup failed", it) }
            }
        }.onFailure { Log.w(TAG, "Cache cleanup could not be scheduled", it) }
    }

    /** Runs LRU cleanup on the caller's background thread and reports confirmed TDLib deletions. */
    fun pruneTrackedCacheNow(): TrackedCacheCleanupResult = synchronized(cleanupLock) {
        performCacheCleanup()
    }

    private fun performCacheCleanup(): TrackedCacheCleanupResult {
        val cacheLimit = EnterprisePreferences.cacheLimitBytes().coerceAtLeast(0L)
        val allPrefs = prefs.all
        val accessed = allPrefs.entries.mapNotNull { (key, value) ->
            if (!key.startsWith(KEY_ACCESS_PREFIX)) return@mapNotNull null
            val id = key.removePrefix(KEY_ACCESS_PREFIX).toIntOrNull() ?: return@mapNotNull null
            val time = value as? Long ?: return@mapNotNull null
            id to time
        }.distinctBy { it.first }.sortedBy { it.second }

        // Bound each sweep so a large history cannot cause one sequential TDLib timeout per file.
        // Older accesses are prioritized for eviction; an unqueried/pinned file is never a candidate.
        val pinnedSnapshot = pinnedFiles.toList().take(MAX_PINNED_FILES_TO_INSPECT)
        val accessCapacity = (MAX_TRACKED_FILES_PER_SWEEP - pinnedSnapshot.size).coerceAtLeast(0)
        val oldAccessIds = accessed.asSequence()
            .map { it.first }
            .filterNot { pinnedFiles.contains(it) }
            .take(accessCapacity)
            .toList()
        val fileIds = (oldAccessIds + pinnedSnapshot).distinct()
        val fileById = getFilesBlocking(fileIds)
        val files = fileIds.mapNotNull { id -> fileById[id]?.let { id to it } }
        var totalSize = files.sumOf { (_, file) -> file.local.downloadedSize.coerceAtLeast(0L) }
        val initialTrackedBytes = totalSize
        if (totalSize <= cacheLimit) {
            return TrackedCacheCleanupResult(0, 0L, totalSize, cacheLimit, initiallyOverLimit = false)
        }

        var deletedFiles = 0
        var freedBytes = 0L
        val accessTime = accessed.toMap()
        val now = System.currentTimeMillis()
        val candidates = files
            .filter { (id, file) ->
                !pinnedFiles.contains(id) &&
                    !PlaybackSessionController.isFileActive(id) &&
                    !TelegramPreCacheEngine.isCaching(id) &&
                    TelegramChunkScheduler.activeChunkCount(id) == 0 &&
                    !file.local.isDownloadingActive &&
                    (accessTime[id]?.let { now - it >= RECENT_ACCESS_PROTECTION_MS } ?: true) &&
                    !file.local.path.isNullOrBlank() && file.local.downloadedSize > 0L
            }
            .sortedBy { (id, _) -> accessTime[id] ?: 0L }

        // Choose the oldest safe files until the expected deleted size meets the limit. Requests
        // are issued together and confirmed asynchronously to prevent N * timeout blocking.
        val plannedDeletions = mutableListOf<Pair<Int, Long>>()
        var plannedTotal = totalSize
        for ((fileId, file) in candidates) {
            if (plannedTotal <= cacheLimit) break
            if (pinnedFiles.contains(fileId) || PlaybackSessionController.isFileActive(fileId) ||
                TelegramPreCacheEngine.isCaching(fileId) || TelegramChunkScheduler.activeChunkCount(fileId) > 0 ||
                file.local.isDownloadingActive ||
                accessTime[fileId]?.let { System.currentTimeMillis() - it < RECENT_ACCESS_PROTECTION_MS } == true
            ) continue
            val fileSize = file.local.downloadedSize.coerceAtLeast(0L)
            if (fileSize <= 0L) continue
            plannedDeletions += fileId to fileSize
            plannedTotal = (plannedTotal - fileSize).coerceAtLeast(0L)
        }

        val confirmedDeletions = deleteFilesBlocking(plannedDeletions.map { it.first })
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
        if (totalSize > cacheLimit) {
            Log.w(TAG, "Tracked cache remains above limit; pinned, recently used, active, untracked, or uncached IDs are not safe eviction candidates sizeMiB=${totalSize / (1024 * 1024)} limitMiB=${cacheLimit / (1024 * 1024)}")
        }
        return TrackedCacheCleanupResult(
            deletedFiles = deletedFiles,
            freedBytes = freedBytes,
            remainingTrackedBytes = totalSize,
            limitBytes = cacheLimit,
            initiallyOverLimit = initialTrackedBytes > cacheLimit
        )
    }

    private fun getFilesBlocking(fileIds: List<Int>): Map<Int, TdApi.File> {
        if (fileIds.isEmpty()) return emptyMap()
        val results = java.util.concurrent.ConcurrentHashMap<Int, TdApi.File>()
        val latch = CountDownLatch(fileIds.size)
        fileIds.forEach { fileId ->
            try {
                TdLibClient.client.send(TdApi.GetFile(fileId)) { response ->
                    when (response) {
                        is TdApi.File -> results[fileId] = response
                        is TdApi.Error -> {
                            // Stale file IDs would otherwise occupy every bounded LRU sweep forever.
                            prefs.edit { remove(KEY_ACCESS_PREFIX + fileId) }
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
        if (!latch.await(TD_API_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "Timed out resolving ${fileIds.size} tracked TDLib file statuses; missing statuses will not be evicted")
        }
        return results.toMap()
    }

    private fun deleteFilesBlocking(fileIds: List<Int>): Set<Int> {
        if (fileIds.isEmpty()) return emptySet()
        val eligibleFileIds = fileIds.filterNot { fileId ->
            pinnedFiles.contains(fileId) || PlaybackSessionController.isFileActive(fileId) ||
                TelegramPreCacheEngine.isCaching(fileId) || TelegramChunkScheduler.activeChunkCount(fileId) > 0
        }
        if (eligibleFileIds.isEmpty()) return emptySet()
        val confirmed = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
        val latch = CountDownLatch(eligibleFileIds.size)
        eligibleFileIds.forEach { fileId ->
            try {
                TdLibClient.client.send(TdApi.DeleteFile(fileId)) { response ->
                    if (response is TdApi.Ok) {
                        confirmed.add(fileId)
                        // This also handles a TDLib callback that arrives after the sweep timeout.
                        prefs.edit { remove(KEY_ACCESS_PREFIX + fileId) }
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
        if (!latch.await(TD_API_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "Timed out confirming ${eligibleFileIds.size} cache deletion requests; only confirmed files count as freed")
        }
        return confirmed.toSet()
    }

}


data class TrackedCacheCleanupResult(
    val deletedFiles: Int,
    val freedBytes: Long,
    val remainingTrackedBytes: Long,
    val limitBytes: Long,
    val initiallyOverLimit: Boolean
)
