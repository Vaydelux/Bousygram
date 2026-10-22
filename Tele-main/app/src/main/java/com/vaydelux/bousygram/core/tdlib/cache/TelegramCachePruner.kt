package com.vaydelux.bousygram.core.tdlib.cache

import android.content.Context
import android.util.Log
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

object TelegramCachePruner {
    private const val TAG = "TelegramCachePruner"
    const val DEFAULT_CACHE_CEILING_BYTES = 2L * 1024L * 1024L * 1024L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        val appCacheBytes = directorySize(context.cacheDir)
        val tdLibBytes = directorySizeOrZero(tdLibCacheDir)
        val appFilesBytes = (directorySize(context.filesDir) - tdLibBytes).coerceAtLeast(0L)
        return TelegramCacheStorageStats(
            appCacheBytes = appCacheBytes,
            appFilesBytes = appFilesBytes,
            tdLibCacheBytes = tdLibBytes,
            cacheLimitBytes = EnterprisePreferences.cacheLimitBytes()
        )
    }

    fun pruneCacheNow(context: Context, tdLibCacheDir: File): TelegramCachePruneResult {
        val cacheLimitBytes = EnterprisePreferences.cacheLimitBytes()
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

        // Only evict tracked TDLib file IDs through TDLib itself. Never delete raw files under
        // TDLib's managed directory; active sessions and offline-pinned IDs are protected.
        val beforeStats = storageStats(context, tdLibCacheDir)
        val result = OfflineCacheManager.pruneTrackedCacheNow()
        val stats = storageStats(context, tdLibCacheDir)
        val remainsOverLimit = stats.tdLibCacheBytes > cacheLimitBytes
        return TelegramCachePruneResult(
            deletedFiles = result.deletedFiles,
            freedBytes = result.freedBytes,
            remainingBytes = stats.tdLibCacheBytes,
            limitBytes = cacheLimitBytes,
            note = when {
                remainsOverLimit -> "Cache is still above the configured limit. Only known tracked files can be safely evicted; pinned, recent, active, and untracked data was preserved."
                result.deletedFiles > 0 -> "Pinned and actively playing media were preserved."
                beforeStats.tdLibCacheBytes > cacheLimitBytes -> "The cache is now within the configured limit."
                else -> "TDLib cache is within the configured limit."
            }
        )
    }

    private fun directorySizeOrZero(file: File): Long =
        runCatching { directorySize(file) }.getOrDefault(0L)

    private fun directorySize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { directorySize(it) } ?: 0L
    }


}

data class TelegramCacheStorageStats(
    val appCacheBytes: Long,
    val appFilesBytes: Long,
    val tdLibCacheBytes: Long,
    val cacheLimitBytes: Long
) {
    val totalBytes: Long = appCacheBytes + appFilesBytes + tdLibCacheBytes
}

data class TelegramCachePruneResult(
    val deletedFiles: Int,
    val freedBytes: Long,
    val remainingBytes: Long,
    val limitBytes: Long,
    val note: String = ""
)
