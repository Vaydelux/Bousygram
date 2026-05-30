package com.vaydelux.bousygram.core.tdlib.server

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

object TelegramPreCacheEngine {
    private const val TAG = "TelegramPreCacheEngine"
    private const val HEAD_PRIME_BYTES = 6L * 1024L * 1024L
    private const val TAIL_PRIME_BYTES = 4L * 1024L * 1024L
    private const val CACHE_AHEAD_BYTES = 24L * 1024L * 1024L
    private const val PRIME_PRIORITY = 16
    private const val CACHE_AHEAD_PRIORITY = 8

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activePrimeFileIds = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())

    @Suppress("UNUSED_PARAMETER")
    fun primeVideoCache(tdLibClient: Client, fileId: Int, totalSize: Long = 0L) {
        if (fileId <= 0 || !activePrimeFileIds.add(fileId)) return
        scope.launch {
            try {
                val size = totalSize.takeIf { it > 0L } ?: LocalStreamingServer.resolveFileSize(fileId)
                TelegramChunkScheduler.markState(fileId, PlaybackCacheState.Priming)
                val headLength = if (size > 0L) min(HEAD_PRIME_BYTES, size) else HEAD_PRIME_BYTES
                val jobs = mutableListOf(
                    TelegramChunkScheduler.prefetchRange(
                        fileId = fileId,
                        offset = 0L,
                        length = headLength,
                        purpose = TelegramChunkPurpose.Priming,
                        priority = PRIME_PRIORITY
                    )
                )
                if (size > 0L) {
                    val tailLength = min(TAIL_PRIME_BYTES, size)
                    val tailOffset = (size - tailLength).coerceAtLeast(0L)
                    if (tailOffset > 0L) {
                        jobs += TelegramChunkScheduler.prefetchRange(
                            fileId = fileId,
                            offset = tailOffset,
                            length = tailLength,
                            purpose = TelegramChunkPurpose.Priming,
                            priority = PRIME_PRIORITY
                        )
                    }
                }
                jobs.joinAll()
                Log.d(TAG, "Primed Telegram video cache fileId=$fileId size=$size")
            } finally {
                activePrimeFileIds.remove(fileId)
            }
        }
    }

    fun cacheAhead(fileId: Int, totalSize: Long, positionMs: Long, durationMs: Long) {
        if (fileId <= 0 || totalSize <= 0L) return
        val offset = positionToOffset(totalSize, positionMs, durationMs)
        val length = min(CACHE_AHEAD_BYTES, totalSize - offset)
        if (length <= 0L) return
        TelegramChunkScheduler.prefetchRange(
            fileId = fileId,
            offset = offset,
            length = length,
            purpose = TelegramChunkPurpose.CacheAhead,
            priority = CACHE_AHEAD_PRIORITY
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun cacheFullVideoOnWifi(context: Context, fileId: Int, totalSize: Long) {
        Log.d(TAG, "Full-video background caching disabled; using active stream cache-ahead only fileId=$fileId")
    }

    fun pauseBackgroundCaching() {
        TelegramChunkScheduler.pauseBackgroundWork()
    }

    fun resumeBackgroundCaching() {
        TelegramChunkScheduler.resumeBackgroundWork()
    }

    internal fun cacheState(fileId: Int): PlaybackCacheState =
        TelegramChunkScheduler.cacheState(fileId)

    private fun positionToOffset(totalSize: Long, positionMs: Long, durationMs: Long): Long =
        if (durationMs > 0L) {
            ((positionMs.coerceAtLeast(0L) * totalSize) / durationMs).coerceIn(0L, totalSize - 1)
        } else {
            min((positionMs.coerceAtLeast(0L) / 1000L) * 512_000L, totalSize - 1)
        }

}
