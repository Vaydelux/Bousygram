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
import java.util.concurrent.atomic.AtomicReference
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
    const val MAX_CACHE_LIMIT_BYTES = TvStorageBudget.TELEGRAM_MEDIA_CACHE_BYTES // 160 MiB, within the 200 MiB media-cache allocation.
    @Deprecated("The active quota has no minimum floor")
    const val MIN_CACHE_LIMIT_BYTES = 0L // No floor on low-storage Android TVs.
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
    private val preloadAttemptsMs = ConcurrentHashMap<Int, Long>()
    private const val PRELOAD_RETRY_COOLDOWN_MS = 30_000L
    private val isEvictionScheduled = AtomicBoolean(false)
    private data class AccessCleanupMarker(val accountId: Long, val elapsedMs: Long)
    private val lastAccessCleanupMarker = AtomicReference<AccessCleanupMarker?>(null)
    private var pinnedAccountId: Long? = null
    // Access timestamps are sorted oldest first. Rotate a bounded TDLib
    // lookup page across sweeps so the first 256 entries cannot starve newer
    // tracked files when some old entries are protected or unresolved.
    private var inspectionCursorAccountId: Long? = null
    private var inspectionCursor = 0

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
        lastAccessCleanupMarker.set(null)
        preloadedNextEpisodes.clear()
        preloadAttemptsMs.clear()
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
        if (fileId <= 0) return
        val account = AccountSession.current() ?: return
        // Keep accurate recency for every access; only expensive directory
        // scans/GetFile requests are rate-limited, never the tracking write.
        prefs?.edit { putLong(KEY_ACCESS_PREFIX + fileId, System.currentTimeMillis()) }
        val now = System.nanoTime() / 1_000_000L
        val freeBytes = StoragePressureMonitor.freeBytes()
        // A failed StatFs read does not prove critical storage pressure.
        val level = if (freeBytes != null) StoragePressurePolicy.level(freeBytes)
            else StoragePressurePolicy.Level.NORMAL
        while (true) {
            val previous = lastAccessCleanupMarker.get()
            val scheduledMs = previous?.takeIf { it.accountId == account.accountId }?.elapsedMs
            if (!shouldRequestCacheCleanupForAccess(scheduledMs, now, level)) return
            if (lastAccessCleanupMarker.compareAndSet(
                    previous, AccessCleanupMarker(account.accountId, now)
                )
            ) {
                triggerCacheCleanupIfNeeded()
                return
            }
        }
    }

    // --- Sliding Read-Ahead Window & Seek ---

    /**
     * A time-based seek is NOT a known byte offset for variable-bitrate MKV/TS.
     * The actual Media3 DataSpec or mpv HTTP Range request owns the byte-accurate
     * window reposition, which TelegramChunkScheduler.handleSeek handles.
     *
     * Only cancel obsolete low-priority speculative work here. In particular do
     * not start a competing download at timeMs * fileSize / durationMs: on VBR
     * content it may evict the exact range the decoder is waiting to read.
     */
    @Suppress("UNUSED_PARAMETER")
    fun onSeek(fileId: Int, newPositionMs: Long, totalSize: Long, durationMs: Long) {
        if (fileId <= 0) return
        TelegramChunkScheduler.cancelPrefetch(fileId)
    }

    // --- Next episode: healthy-buffer-only, verified head preload ---

    /**
     * Never compete with a starving foreground stream for the next video.
     * A prefetch request is useful only after 80% progress with >=15s of real
     * buffered media (or all remaining media if fewer than 15s remain).
     */
    internal fun shouldPreloadNextEpisode(
        positionMs: Long,
        durationMs: Long,
        bufferedPositionMs: Long,
        isBuffering: Boolean,
        playWhenReady: Boolean,
        currentFileSizeBytes: Long = 0L,
        measuredThroughputBytesPerSec: Long = 0L
    ): Boolean {
        if (isBuffering || !playWhenReady || durationMs <= 0L ||
            positionMs < 0L || positionMs >= durationMs
        ) return false
        if (positionMs.toDouble() / durationMs.toDouble() < 0.80) return false
        val requiredLead = min(15_000L, durationMs - positionMs)
        if (bufferedPositionMs - positionMs < requiredLead) return false
        // Do not steal network capacity from a bitrate-limited foreground video.
        // Downloaded-byte throughput is measured through TDLib updates, not disk
        // cache reads. Unknown rates preserve the existing buffer-runway policy.
        if (currentFileSizeBytes > 0L) {
            // Without a fresh network sample, speculative downloading is only
            // safe if the entire remaining movie is already buffered.
            if (measuredThroughputBytesPerSec <= 0L) return bufferedPositionMs >= durationMs
            val mediaBytesPerSecond = currentFileSizeBytes.toDouble() * 1000.0 / durationMs
            if (measuredThroughputBytesPerSec.toDouble() < mediaBytesPerSecond * 1.35) return false
        }
        return true
    }

    fun onPlaybackProgress(
        currentFileId: Int,
        currentPositionMs: Long,
        durationMs: Long,
        nextItemFileId: Int?,
        nextItemTotalSize: Long = 0L,
        currentBufferedPositionMs: Long = 0L,
        isBuffering: Boolean = false,
        playWhenReady: Boolean = true,
        currentFileSizeBytes: Long = 0L,
        measuredThroughputBytesPerSec: Long = 0L
    ) {
        if (currentFileId <= 0 || nextItemFileId == null ||
            nextItemFileId <= 0 || nextItemFileId == currentFileId
        ) return
        if (StoragePressureMonitor.isLow()) return
        if (shouldPreloadNextEpisode(
                currentPositionMs, durationMs, currentBufferedPositionMs,
                isBuffering, playWhenReady, currentFileSizeBytes, measuredThroughputBytesPerSec
            )
        ) preloadNextEpisode(nextItemFileId, nextItemTotalSize)
    }

    fun preloadNextEpisode(nextFileId: Int, totalSize: Long = 0L) {
        // This is optional next-episode work, never a foreground playback read.
        // On small TVs reserve free flash for the video already being watched.
        if (StoragePressureMonitor.isLow()) return
        val owner = AccountSession.current() ?: return
        if (nextFileId <= 0 || preloadedNextEpisodes.contains(nextFileId)) return
        val now = System.nanoTime() / 1_000_000L
        var shouldLaunch = false
        preloadAttemptsMs.compute(nextFileId) { _, previous ->
            if (previous == null || now - previous >= PRELOAD_RETRY_COOLDOWN_MS) {
                shouldLaunch = true
                now
            } else previous
        }
        if (!shouldLaunch) return
        Log.d(TAG, "Preloading next video head fileId=$nextFileId")
        TelegramPreCacheEngine.primeVideoCache(
            tdLibClient = TdLibClient.client,
            fileId = nextFileId,
            totalSize = totalSize,
            includeTail = false,
            onComplete = { headReady ->
                // Do not let a callback from a logged-out account mark another
                // account's file id as successfully primed.
                if (headReady && AccountSession.isCurrent(owner)) {
                    preloadedNextEpisodes.add(nextFileId)
                } else {
                    Log.d(TAG, "Next-video head prefetch was not ready fileId=$nextFileId; retry permitted after cooldown")
                }
            }
        )
    }

    fun clearPreloadTracking() {
        preloadedNextEpisodes.clear()
        preloadAttemptsMs.clear()
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
        val appCacheBytes = directoryUsageBytes(context.cacheDir)
        val tdLibBytes = directoryUsageBytes(tdLibCacheDir)
        // Counting filesDir and subtracting TDLib's size walks the entire
        // TDLib tree TWICE. Exclude it while traversing filesDir instead.
        val appFilesBytes = directoryUsageBytes(context.filesDir, tdLibCacheDir)
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
        if (inspectionCursorAccountId != account.accountId) {
            inspectionCursorAccountId = account.accountId
            inspectionCursor = 0
        }
        val unpinnedIds = accessed.map { it.first }
            .filterNot { pinnedFiles.contains(it) }
        val page = selectCacheInspectionPage(unpinnedIds, inspectionCursor, accessCapacity)
        inspectionCursor = page.nextCursor
        val fileIds = (page.ids + pinnedSnapshot).distinct()
        val fileById = getFilesBlocking(fileIds, account)
        if (!AccountSession.isCurrent(account)) return TrackedCacheCleanupResult(0, 0L, 0L, cacheLimit, false)
        val files = fileIds.mapNotNull { id -> fileById[id]?.let { id to it } }
        var totalSize = files.sumOf { (_, file) -> file.local.downloadedSize.coerceAtLeast(0L) }
        // The inspected 256-file window is not the whole TDLib cache. Use a
        // disk snapshot of the configured TDLib media directory for quota
        // pressure, while still querying/deleting only the bounded page.
        // An unavailable/empty directory must never hide known tracked bytes.
        val diskBytes = directorySizeOrZero(File(BousygramApp.instance.filesDir, "tdlib_files"))
        val measuredCachePressureBytes = cacheQuotaPressureBytes(diskBytes, totalSize)
        val initiallyOverLimit = needsCacheEviction(measuredCachePressureBytes, cacheLimit)
        if (!initiallyOverLimit) {
            // The cache is already within its (possibly low-storage) target.
            // Proactively deleting freshly watched media causes avoidable
            // re-downloads, flash writes and playback startup latency.
            return TrackedCacheCleanupResult(
                0, 0L, totalSize, cacheLimit, initiallyOverLimit = false
            )
        }

        var deletedFiles = 0
        var freedBytes = 0L
        val accessTime = accessed.toMap()
        val now = System.currentTimeMillis()

        val continueWatchingIds = runCatching {
            ContinueWatchingRepository.getContinueWatchingFileIds()
        }.getOrDefault(emptySet())
        val lastPlayedId = EnterprisePreferences.lastPlayed()?.fileId
        // An unfinished last-played movie is protected for resume. A completed
        // movie no longer needs a storage exemption after the player releases it.
        val protectedLastPlayedId = protectedLastPlayedId(
            lastPlayedId, lastPlayedId?.let { isWatched(it) } == true
        )

        val safeCandidates = files
            .filter { (id, file) ->
                !pinnedFiles.contains(id) &&
                    // Episode queue membership grants stream access, but is
                    // not a reason to retain an otherwise evictable full file.
                    // Protect only the active/resolving video; the safeguards
                    // below still protect downloads, leases and prefetch.
                    !PlaybackSessionController.isCacheProtectedPlaybackFile(id) &&
                    !continueWatchingIds.contains(id) &&
                    id != protectedLastPlayedId &&
                    !TelegramPreCacheEngine.isCaching(id) &&
                    TelegramChunkScheduler.activeLeaseCount(id) == 0 &&
                    TelegramChunkScheduler.activeChunkCount(id) == 0 &&
                    !file.local.isDownloadingActive &&
                    !FocusPrefetchManager.isPrefetching(id) &&
                    !file.local.path.isNullOrBlank() && file.local.downloadedSize > 0L
            }

        // Apply the same recent-use grace to watched and non-watched media.
        // Previously the watched pass ignored the grace entirely, deleting
        // just-finished episodes while playback could soon resume or replay.
        val oldEnoughCandidates = safeCandidates.filter { (id, _) ->
            canEvictCacheByAccessAge(accessTime[id], now, RECENT_ACCESS_PROTECTION_MS)
        }
        val plannedDeletions = mutableListOf<Pair<Int, Long>>()
        var plannedTotal = measuredCachePressureBytes

        // Phase 1: Prefer watched/completed media only while above quota.
        val watchedCandidates = oldEnoughCandidates.filter { (id, _) -> isWatched(id) }
        for ((fileId, file) in watchedCandidates) {
            if (plannedTotal <= cacheLimit) break
            val fileSize = file.local.downloadedSize.coerceAtLeast(0L)
            if (fileSize > 0L) {
                plannedDeletions += fileId to fileSize
                plannedTotal = (plannedTotal - fileSize).coerceAtLeast(0L)
                Log.i(TAG, "Queued watched media for automatic eviction fileId=$fileId sizeMiB=${fileSize / (1024 * 1024)}")
            }
        }

        // Phase 2: If still above dynamic quota, evict oldest accessed files
        if (plannedTotal > cacheLimit) {
            val plannedIds = plannedDeletions.mapTo(mutableSetOf()) { it.first }
            val remainingCandidates = oldEnoughCandidates
                .filterNot { (id, _) -> id in plannedIds }
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
            return TrackedCacheCleanupResult(0, 0L, totalSize, cacheLimit, initiallyOverLimit = initiallyOverLimit)
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
            initiallyOverLimit = initiallyOverLimit
        )
    }

    /** Completed last-played media can be recycled after its session closes. */
    internal fun protectedLastPlayedId(lastPlayedId: Int?, completed: Boolean): Int? =
        if (completed) null else lastPlayedId?.takeIf { it > 0 }

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
        if (fileIds.isEmpty() || !AccountSession.isCurrent(account)) return emptyMap()
        val client = TdLibClient.client
        val results = ConcurrentHashMap<Int, TdApi.File>()
        // One 4s budget across the entire sweep. The old implementation sent
        // up to 256 requests concurrently, competing with active video reads.
        val startedNs = System.nanoTime()
        for (batch in cacheTdLibRequestBatches(fileIds)) {
            if (!AccountSession.isCurrent(account)) break
            val remainingNs = cacheTdLibRemainingNanos(startedNs, System.nanoTime(), TD_API_TIMEOUT_MS)
            if (remainingNs <= 0L) break
            val latch = CountDownLatch(batch.size)
            val accepting = AtomicBoolean(true)
            var completed = false
            try {
                batch.forEach { fileId ->
                    try {
                        if (!AccountSession.isCurrent(account)) {
                            latch.countDown()
                            return@forEach
                        }
                        client.send(TdApi.GetFile(fileId)) { response ->
                            try {
                                if (accepting.get() && AccountSession.isCurrent(account)) {
                                    when (response) {
                                        is TdApi.File -> results[fileId] = response
                                        is TdApi.Error -> {
                                            // Only a confirmed permanently missing TDLib
                                            // file is safe to remove from access tracking.
                                            // Temporary 429/500/503, authorization or
                                            // migration errors must not orphan cached media.
                                            if (shouldDiscardCacheAccessRecordOnGetFileError(
                                                    response.code, response.message
                                                )
                                            ) {
                                                prefs?.edit { remove(KEY_ACCESS_PREFIX + fileId) }
                                                Log.d(TAG, "Dropping permanently invalid cache fileId=$fileId")
                                            } else {
                                                Log.w(TAG, "Retaining tracked cache file after transient/unclassified GetFile error code=${response.code}")
                                            }
                                        }
                                    }
                                }
                            } finally {
                                latch.countDown()
                            }
                        }
                    } catch (error: Exception) {
                        Log.w(TAG, "Could not request TDLib file status fileId=$fileId", error)
                        latch.countDown()
                    }
                }
                completed = try {
                    // Include batch submission time in the single shared deadline.
                    val waitNs = cacheTdLibRemainingNanos(
                        startedNs, System.nanoTime(), TD_API_TIMEOUT_MS
                    )
                    if (waitNs > 0L) latch.await(waitNs, TimeUnit.NANOSECONDS)
                    else false
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    false
                }
            } finally {
                // Ignore late callbacks after a timed-out batch/account swap.
                accepting.set(false)
            }
            if (!completed) {
                Log.w(TAG, "TDLib cache status batch timed out; deferring remaining files")
                break
            }
        }
        return results.toMap()
    }

    /**
     * Keep the same live current-media and lease guards immediately before
     * each DeleteFile request, since playback can switch while TDLib GetFile
     * responses are being collected.
     */
    private fun isTrackedFileDeletionProtected(fileId: Int): Boolean =
        pinnedFiles.contains(fileId) ||
            PlaybackSessionController.isCacheProtectedPlaybackFile(fileId) ||
            TelegramPreCacheEngine.isCaching(fileId) ||
            TelegramChunkScheduler.activeChunkCount(fileId) > 0 ||
            TelegramChunkScheduler.activeLeaseCount(fileId) > 0 ||
            FocusPrefetchManager.isPrefetching(fileId)

    private fun deleteFilesBlocking(fileIds: List<Int>, account: AccountSnapshot): Set<Int> {
        if (fileIds.isEmpty() || !AccountSession.isCurrent(account)) return emptySet()
        val client = TdLibClient.client
        val eligibleFileIds = fileIds.filterNot(::isTrackedFileDeletionProtected)
        if (eligibleFileIds.isEmpty()) return emptySet()
        val confirmed = ConcurrentHashMap.newKeySet<Int>()
        val startedNs = System.nanoTime()
        // Limit native deletion requests too. Recheck every file immediately
        // before issuance: the queued episode may now be the playing video.
        for (batch in cacheTdLibRequestBatches(eligibleFileIds)) {
            if (!AccountSession.isCurrent(account)) break
            val remainingNs = cacheTdLibRemainingNanos(startedNs, System.nanoTime(), TD_API_TIMEOUT_MS)
            if (remainingNs <= 0L) break
            val latch = CountDownLatch(batch.size)
            val accepting = AtomicBoolean(true)
            var completed = false
            try {
                batch.forEach { fileId ->
                    try {
                        if (!AccountSession.isCurrent(account) ||
                            isTrackedFileDeletionProtected(fileId)
                        ) {
                            latch.countDown()
                            return@forEach
                        }
                        client.send(TdApi.DeleteFile(fileId)) { response ->
                            try {
                                if (accepting.get() && AccountSession.isCurrent(account)) {
                                    if (response is TdApi.Ok) {
                                        confirmed.add(fileId)
                                        prefs?.edit { remove(KEY_ACCESS_PREFIX + fileId) }
                                    } else if (response is TdApi.Error) {
                                        Log.w(TAG, "TDLib refused cache eviction fileId=$fileId code=${response.code} message=${response.message}")
                                    }
                                }
                            } finally {
                                latch.countDown()
                            }
                        }
                    } catch (error: Exception) {
                        Log.w(TAG, "Could not request TDLib cache eviction fileId=$fileId", error)
                        latch.countDown()
                    }
                }
                completed = try {
                    // Include batch submission time in the single shared deadline.
                    val waitNs = cacheTdLibRemainingNanos(
                        startedNs, System.nanoTime(), TD_API_TIMEOUT_MS
                    )
                    if (waitNs > 0L) latch.await(waitNs, TimeUnit.NANOSECONDS)
                    else false
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    false
                }
            } finally {
                accepting.set(false)
            }
            if (!completed) {
                Log.w(TAG, "TDLib cache deletion batch timed out; deferring remaining files")
                break
            }
        }
        return confirmed.toSet()
    }

    private fun directorySizeOrZero(file: File): Long =
        runCatching { directoryUsageBytes(file) }.getOrDefault(0L)
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
    /** Cache-like measured locations, unlike persistent app files or databases. */
    val measuredCacheBytes: Long get() = appCacheBytes + tdLibCacheBytes
    /** Measured cacheDir + filesDir only; excludes APK, databases and shared prefs. */
    val totalBytes: Long get() = appCacheBytes + appFilesBytes + tdLibCacheBytes
}

data class TelegramCachePruneResult(
    val deletedFiles: Int,
    val freedBytes: Long,
    val remainingBytes: Long,
    val limitBytes: Long,
    val note: String
)
