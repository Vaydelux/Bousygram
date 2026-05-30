package com.vaydelux.bousygram.core.tdlib.server

import android.content.Context
import android.util.Log
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
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
    private const val TAIL_PRIME_BYTES = 2L * 1024L * 1024L
    private const val CACHE_AHEAD_BYTES = 32L * 1024L * 1024L
    private const val MIN_CACHE_AHEAD_SHIFT_BYTES = 8L * 1024L * 1024L
    private const val PRIME_PRIORITY = 32
    private const val CACHE_AHEAD_PRIORITY = 8

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activePrimeJobs = ConcurrentHashMap<Int, MutableList<Job>>()
    private val activeCacheAheadJobs = ConcurrentHashMap<Int, CacheAheadJob>()

    private data class CacheAheadJob(
        val offset: Long,
        val endExclusive: Long,
        val job: Job
    )

    @Suppress("UNUSED_PARAMETER")
    fun primeVideoCache(tdLibClient: Client, fileId: Int, totalSize: Long = 0L) {
        if (fileId <= 0) return
        val trackedJobs = Collections.synchronizedList(mutableListOf<Job>())
        if (activePrimeJobs.putIfAbsent(fileId, trackedJobs) != null) return
        val outerJob = scope.launch {
            try {
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
                if (size > 0L) {
                    val tailLength = min(TAIL_PRIME_BYTES, size)
                    val tailOffset = (size - tailLength).coerceAtLeast(0L)
                    if (tailOffset > 0L) {
                        val tailJob = TelegramChunkScheduler.prefetchRange(
                            fileId = fileId,
                            offset = tailOffset,
                            length = tailLength,
                            purpose = TelegramChunkPurpose.CacheAhead,
                            priority = CACHE_AHEAD_PRIORITY
                        )
                        trackedJobs += tailJob
                    }
                }
                Log.d(TAG, "Primed Telegram video cache fileId=$fileId size=$size")
            } finally {
                activePrimeJobs.remove(fileId, trackedJobs)
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
        if (fileId <= 0 || totalSize <= 0L) return
        val offset = positionToOffset(totalSize, positionMs, durationMs)
        val length = min(maxBytes.coerceIn(1L, CACHE_AHEAD_BYTES * 2), totalSize - offset)
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

    internal fun isCachingForTests(fileId: Int): Boolean =
        activePrimeJobs[fileId]?.any { it.isActive } == true ||
            activeCacheAheadJobs[fileId]?.job?.isActive == true

    private fun cancelTelegramDownload(fileId: Int) {
        TdLibClient.client.send(
            TdApi.CancelDownloadFile(fileId, false)
        ) { response ->
            if (response is TdApi.Error) {
                TelegramLogWrapper.logError("CancelDownloadFile", response)
                Log.w(TAG, "CancelDownloadFile failed fileId=$fileId code=${response.code} message=${response.message}")
            } else {
                Log.d(TAG, "Stopped active Telegram download fileId=$fileId")
            }
        }
    }

    private fun positionToOffset(totalSize: Long, positionMs: Long, durationMs: Long): Long =
        if (durationMs > 0L) {
            ((positionMs.coerceAtLeast(0L) * totalSize) / durationMs).coerceIn(0L, totalSize - 1)
        } else {
            min((positionMs.coerceAtLeast(0L) / 1000L) * 512_000L, totalSize - 1)
        }

}
