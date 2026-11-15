package com.vaydelux.bousygram.core.tdlib.cache

import android.content.Context
import android.util.Log
import com.vaydelux.bousygram.core.cache.CacheManager
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Backward compatibility facade delegating to unified [CacheManager].
 */
object TelegramCachePruner {
    private const val TAG = "TelegramCachePruner"
    @Deprecated("Legacy display constant; runtime quota is calculated by CacheManager")
    const val DEFAULT_CACHE_CEILING_BYTES = CacheManager.MAX_CACHE_LIMIT_BYTES
    @Deprecated("Legacy display constant; runtime quota is calculated by CacheManager")
    const val DEFAULT_STORAGE_FRACTION = 0.20
    @Deprecated("Legacy display constant; runtime quota has no minimum floor")
    const val MIN_CACHE_LIMIT_BYTES = 256L * 1024L * 1024L
    @Deprecated("Legacy display constant; runtime ceiling is CacheManager.MAX_CACHE_LIMIT_BYTES")
    const val MAX_CACHE_LIMIT_BYTES = CacheManager.MAX_CACHE_LIMIT_BYTES

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun calculateDynamicCacheLimit(context: Context? = null): Long =
        CacheManager.calculateDynamicQuota(context)

    fun pruneCacheIfNecessary(context: Context, tdLibCacheDir: File) {
        val appContext = context.applicationContext
        scope.launch {
            val result = pruneCacheNow(context, tdLibCacheDir)
            if (result.deletedFiles > 0) {
                Log.i(
                    TAG,
                    "Pruned TDLib cache deleted=${result.deletedFiles} freed=${result.freedBytes} " +
                        "remaining=${result.remainingBytes} app=${appContext.packageName}"
                )
            }
        }
    }

    fun storageStats(context: Context, tdLibCacheDir: File): TelegramCacheStorageStats {
        val stats = CacheManager.storageStats(context, tdLibCacheDir)
        return TelegramCacheStorageStats(
            appCacheBytes = stats.appCacheBytes,
            appFilesBytes = stats.appFilesBytes,
            tdLibCacheBytes = stats.tdLibCacheBytes,
            cacheLimitBytes = stats.cacheLimitBytes
        )
    }

    fun pruneCacheNow(context: Context, tdLibCacheDir: File): TelegramCachePruneResult {
        val cacheLimitBytes = CacheManager.calculateDynamicQuota(context)
        val root = runCatching { tdLibCacheDir.canonicalFile }.getOrElse { tdLibCacheDir }
        if (!root.exists() || !root.isDirectory) {
            val stats = storageStats(context, tdLibCacheDir)
            return TelegramCachePruneResult(
                deletedFiles = 0,
                freedBytes = 0L,
                remainingBytes = stats.tdLibCacheBytes,
                limitBytes = cacheLimitBytes,
                note = "The TDLib cache directory is not available."
            )
        }

        // One post-prune directory snapshot is enough for the Settings result.
        // The cleanup engine already measures cache pressure before deletions;
        // avoid a redundant pre-prune storageStats() traversal.
        val startedAt = System.nanoTime()
        val result = CacheManager.pruneTrackedCacheNow()
        val stats = storageStats(context, tdLibCacheDir)
        val remainsOverLimit = stats.tdLibCacheBytes > cacheLimitBytes
        val elapsedMs = ((System.nanoTime() - startedAt).coerceAtLeast(0L)) / 1_000_000L
        Log.i(TAG, "Safe cache prune durationMs=$elapsedMs deletedFiles=${result.deletedFiles} " +
            "freedBytes=${result.freedBytes} remainingBytes=${stats.tdLibCacheBytes} " +
            "quotaBytes=$cacheLimitBytes")
        return TelegramCachePruneResult(
            deletedFiles = result.deletedFiles,
            freedBytes = result.freedBytes,
            remainingBytes = stats.tdLibCacheBytes,
            limitBytes = cacheLimitBytes,
            note = when {
                remainsOverLimit -> "The TDLib media folder is still above its target. " +
                    "Untracked, offline-pinned, active, or recently used bytes may remain; " +
                    "streaming downloads can temporarily exceed the target."
                result.deletedFiles > 0 -> "Safe TDLib media cleanup completed. " +
                    "Offline pins and current playback were preserved; resume history is unchanged."
                else -> "The TDLib media folder is within its target. " +
                    "No stored resume history or explicit offline pins were removed."
            }
        )
    }
}

typealias TelegramCacheStorageStats = com.vaydelux.bousygram.core.cache.TelegramCacheStorageStats
typealias TelegramCachePruneResult = com.vaydelux.bousygram.core.cache.TelegramCachePruneResult
