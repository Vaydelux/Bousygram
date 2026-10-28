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
    const val DEFAULT_CACHE_CEILING_BYTES = 5L * 1024L * 1024L * 1024L
    @Deprecated("Legacy display constant; runtime quota is calculated by CacheManager")
    const val DEFAULT_STORAGE_FRACTION = 0.20
    @Deprecated("Legacy display constant; runtime quota has no minimum floor")
    const val MIN_CACHE_LIMIT_BYTES = 256L * 1024L * 1024L
    @Deprecated("Legacy display constant; runtime ceiling is CacheManager.MAX_CACHE_LIMIT_BYTES")
    const val MAX_CACHE_LIMIT_BYTES = 5L * 1024L * 1024L * 1024L

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

        val beforeStats = storageStats(context, tdLibCacheDir)
        val result = CacheManager.pruneTrackedCacheNow()
        val stats = storageStats(context, tdLibCacheDir)
        val remainsOverLimit = stats.tdLibCacheBytes > cacheLimitBytes
        return TelegramCachePruneResult(
            deletedFiles = result.deletedFiles,
            freedBytes = result.freedBytes,
            remainingBytes = stats.tdLibCacheBytes,
            limitBytes = cacheLimitBytes,
            note = when {
                remainsOverLimit -> "Cache is still above the configured limit. Pinned, recent, active, and untracked data was preserved."
                result.deletedFiles > 0 -> "Pinned and actively playing media were preserved."
                beforeStats.tdLibCacheBytes > cacheLimitBytes -> "The cache is now within the configured limit."
                else -> "TDLib cache is within the configured limit."
            }
        )
    }
}

typealias TelegramCacheStorageStats = com.vaydelux.bousygram.core.cache.TelegramCacheStorageStats
typealias TelegramCachePruneResult = com.vaydelux.bousygram.core.cache.TelegramCachePruneResult
