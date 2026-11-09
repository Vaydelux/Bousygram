package com.vaydelux.bousygram.core.tdlib.server

import android.content.Context
import android.util.Log
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.core.cache.CacheManager
import com.vaydelux.bousygram.core.cache.StoragePressureMonitor
import com.vaydelux.bousygram.core.cache.StoragePressurePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

object TelegramPreCacheEngine {
    private const val TAG = "TelegramPreCacheEngine"
    private const val HEAD_PRIME_BYTES = 2L * 1024L * 1024L
    // 32 KiB is enough to see a header, but generally insufficient to call a
    // next episode 'primed' on slower Telegram links. Require a useful runway.
    internal fun requiredPrimedHeadBytes(headLength: Long): Int =
        min(headLength.coerceAtLeast(1L), 256L * 1024L).toInt()
    private const val CACHE_AHEAD_BYTES = 32L * 1024L * 1024L
    private const val MIN_CACHE_AHEAD_SHIFT_BYTES = 8L * 1024L * 1024L
    private const val PRIME_PRIORITY = 12
    private const val CACHE_AHEAD_PRIORITY = 8

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activePrimeJobs = ConcurrentHashMap<Int, MutableList<Job>>()
    private val activeCacheAheadJobs = ConcurrentHashMap<Int, CacheAheadJob>()

    private data class CacheAheadJob(
        val offset: Long,
        val endExclusive: Long,
        val job: Job
    )

    /**
     * Prime bytes before a player requests them. A caller may observe the result:
     * a launched prefetch is not considered successful until TDLib actually exposes
     * the initial bytes. Head-only priming is best for the next queued episode.
     */
    @Suppress("UNUSED_PARAMETER")
    fun primeVideoCache(
        tdLibClient: Client,
        fileId: Int,
        totalSize: Long = 0L,
        includeTail: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (fileId <= 0 || TelegramChunkScheduler.isPlaybackBusy(fileId) ||
            StoragePressureMonitor.isLow()) {
            onComplete?.invoke(false)
            return
        }
        val trackedJobs = Collections.synchronizedList(mutableListOf<Job>())
        if (activePrimeJobs.putIfAbsent(fileId, trackedJobs) != null) {
            onComplete?.invoke(false)
            return
        }
        val outerJob = scope.launch {
            var headReady = false
            try {
                if (TelegramChunkScheduler.isPlaybackBusy(fileId)) return@launch
                val size = totalSize.takeIf { it > 0L } ?: LocalStreamingServer.resolveFileSize(fileId)
                TelegramChunkScheduler.markState(fileId, PlaybackCacheState.Priming)
                val headLength = if (size > 0L) min(HEAD_PRIME_BYTES, size) else HEAD_PRIME_BYTES
                val headJob = TelegramChunkScheduler.prefetchRange(
                    fileId = fileId,
                    offset = 0L,
                    length = headLength,
                    purpose = TelegramChunkPurpose.Priming,
                    priority = PRIME_PRIORITY
                )
                trackedJobs += headJob
                headJob.join()
                // A completed Job only means the request has ended, not that TDLib
                // delivered usable bytes. Verify the actual downloaded prefix.
                headReady = TelegramChunkScheduler.awaitPrefixReady(
                    fileId = fileId,
                    offset = 0L,
                    minNeededBytes = requiredPrimedHeadBytes(headLength),
                    timeoutMs = 2_500L
                )
                if (headReady && includeTail && size > 4L * 1024L * 1024L &&
                    !TelegramChunkScheduler.isPlaybackBusy(fileId)
                ) {
                    val tailLength = min(HEAD_PRIME_BYTES, size)
                    val tailOffset = size - tailLength
                    val tailJob = TelegramChunkScheduler.prefetchRange(
                        fileId = fileId,
                        offset = tailOffset,
                        length = tailLength,
                        purpose = TelegramChunkPurpose.Priming,
                        priority = PRIME_PRIORITY - 1
                    )
                    trackedJobs += tailJob
                    tailJob.join()
                }
                Log.d(TAG, "Telegram prime completed fileId=$fileId headReady=$headReady tail=$includeTail")
            } finally {
                activePrimeJobs.remove(fileId, trackedJobs)
                runCatching { onComplete?.invoke(headReady) }
                    .onFailure { Log.w(TAG, "Prefetch completion callback failed", it) }
            }
        }
        trackedJobs += outerJob
    }

    fun cacheAhead(
        fileId: Int,
        totalSize: Long,
        positionMs: Long,
        durationMs: Long,
        maxBytes: Long = CACHE_AHEAD_BYTES
    ) {
        if (fileId <= 0 || totalSize <= 0L || StoragePressureMonitor.isLow()) return
        // Playback has an open-ended high-priority window; avoid competing cache requests.
        if (TelegramChunkScheduler.isPlaybackBusy(fileId)) return
        val offset = positionToOffset(totalSize, positionMs, durationMs)
        val effectiveMax = if (com.vaydelux.bousygram.core.network.NetworkConnectivityObserver.isCellular) {
            4L * 1024L * 1024L
        } else {
            maxBytes
        }
        val bounded = StoragePressurePolicy.boundedReadAheadBytes(
            effectiveMax, StoragePressureMonitor.freeBytes()
        )
        if (bounded <= 0L) return
        val length = min(bounded, totalSize - offset)
        if (length <= 0L) return

        val endExclusive = offset + length
        val active = activeCacheAheadJobs[fileId]
        if (active != null) {
            if (active.job.isActive &&
                offset >= active.offset &&
                offset + MIN_CACHE_AHEAD_SHIFT_BYTES < active.endExclusive
            ) {
                return
            }
            active.job.cancel()
            activeCacheAheadJobs.remove(fileId, active)
        }

        val job = TelegramChunkScheduler.prefetchRange(
            fileId = fileId,
            offset = offset,
            length = length,
            purpose = TelegramChunkPurpose.CacheAhead,
            priority = CACHE_AHEAD_PRIORITY
        )
        activeCacheAheadJobs[fileId] = CacheAheadJob(offset, endExclusive, job)
        job.invokeOnCompletion {
            val current = activeCacheAheadJobs[fileId]
            if (current?.job == job) {
                activeCacheAheadJobs.remove(fileId, current)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun cacheFullVideoOnWifi(context: Context, fileId: Int, totalSize: Long) {
        Log.d(TAG, "Full-video background caching disabled; using active stream cache-ahead only fileId=$fileId")
    }

    fun onSeek(fileId: Int, newOffset: Long, windowBytes: Long = CACHE_AHEAD_BYTES) {
        if (fileId <= 0) return
        val active = activeCacheAheadJobs[fileId]
        if (active != null) {
            val validStart = (newOffset - 2L * 1024L * 1024L).coerceAtLeast(0L)
            val validEnd = newOffset + windowBytes
            if (active.endExclusive < validStart || active.offset > validEnd) {
                active.job.cancel()
                activeCacheAheadJobs.remove(fileId, active)
            }
        }
        TelegramChunkScheduler.cancelOutOfWindowChunks(fileId, newOffset, windowBytes)
    }

    fun stopCaching(fileId: Int, cancelTdLibDownload: Boolean = true) {
        if (fileId <= 0) return
        activePrimeJobs.remove(fileId)?.forEach { it.cancel() }
        activeCacheAheadJobs.remove(fileId)?.job?.cancel()
        TelegramChunkScheduler.markState(fileId, PlaybackCacheState.Preparing)
        if (cancelTdLibDownload) cancelTelegramDownload(fileId)
    }

    fun stopAllCaching(cancelTdLibDownload: Boolean = true) {
        val fileIds = (activePrimeJobs.keys + activeCacheAheadJobs.keys).toSet()
        fileIds.forEach { stopCaching(it, cancelTdLibDownload) }
    }

    fun pauseBackgroundCaching() {
        TelegramChunkScheduler.pauseBackgroundWork()
    }

    fun resumeBackgroundCaching() {
        TelegramChunkScheduler.resumeBackgroundWork()
    }

    internal fun cacheState(fileId: Int): PlaybackCacheState =
        TelegramChunkScheduler.cacheState(fileId)

    fun isCaching(fileId: Int): Boolean =
        activePrimeJobs[fileId]?.any { it.isActive } == true ||
            activeCacheAheadJobs[fileId]?.job?.isActive == true

    internal fun isCachingForTests(fileId: Int): Boolean = isCaching(fileId)

    private fun cancelTelegramDownload(fileId: Int) {
        try {
            TdLibClient.client.send(
                TdApi.CancelDownloadFile(fileId, false)
            ) { response ->
                if (response is TdApi.Error) {
                    TelegramLogWrapper.logError("CancelDownloadFile", response)
                    Log.w(TAG, "CancelDownloadFile failed fileId=$fileId code=${response.code} message=${response.message}")
                } else {
                    Log.d(TAG, "Stopped active Telegram download fileId=$fileId")
                    // The player has released its file; a protected worker can
                    // now remove eligible watched/old bytes without touching
                    // any active playback leases or pinned offline downloads.
                    CacheManager.onStoppedDownloadCancelled(fileId)
                    CacheManager.triggerCacheCleanupIfNeeded()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not issue CancelDownloadFile for fileId=$fileId: ${t.message}")
        }
    }

    private fun positionToOffset(totalSize: Long, positionMs: Long, durationMs: Long): Long =
        if (durationMs > 0L) {
            ((positionMs.coerceAtLeast(0L) * totalSize) / durationMs).coerceIn(0L, totalSize - 1)
        } else {
            min((positionMs.coerceAtLeast(0L) / 1000L) * 512_000L, totalSize - 1)
        }

}
