package com.vaydelux.bousygram.core.tdlib.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.vaydelux.bousygram.app.BousygramApp
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
    private const val FULL_CACHE_PRIORITY = 1

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activePrimeFileIds = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    private val activeFullCacheFileIds = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())

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

    fun cacheFullVideoOnWifi(context: Context, fileId: Int, totalSize: Long) {
        if (fileId <= 0 || totalSize <= 0L || !activeFullCacheFileIds.add(fileId)) return
        scope.launch {
            try {
                if (!isUnmeteredHomeNetwork(context)) {
                    Log.d(TAG, "Skipping full-video cache on metered/non-home network fileId=$fileId")
                    return@launch
                }
                TelegramChunkScheduler.prefetchRange(
                    fileId = fileId,
                    offset = 0L,
                    length = totalSize,
                    purpose = TelegramChunkPurpose.BackgroundFull,
                    priority = FULL_CACHE_PRIORITY
                ).join()
            } finally {
                activeFullCacheFileIds.remove(fileId)
            }
        }
    }

    fun cacheState(fileId: Int): PlaybackCacheState =
        TelegramChunkScheduler.cacheState(fileId)

    private fun positionToOffset(totalSize: Long, positionMs: Long, durationMs: Long): Long =
        if (durationMs > 0L) {
            ((positionMs.coerceAtLeast(0L) * totalSize) / durationMs).coerceIn(0L, totalSize - 1)
        } else {
            min((positionMs.coerceAtLeast(0L) / 1000L) * 512_000L, totalSize - 1)
        }

    private fun isUnmeteredHomeNetwork(context: Context): Boolean {
        val appContext = context.applicationContext ?: BousygramApp.instance
        val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        val isHomeTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return isHomeTransport &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
