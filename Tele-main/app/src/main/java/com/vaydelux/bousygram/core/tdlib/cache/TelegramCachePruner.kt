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
                limitBytes = cacheLimitBytes
            )
        }

        val files = root
            .walkTopDown()
            .filter { it.isFile }
            .filterNot { shouldSkip(it) }
            .toList()
        var totalSize = files.sumOf { it.length() }
        var deletedFiles = 0
        var freedBytes = 0L

        if (totalSize > cacheLimitBytes) {
            files
                .sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.absolutePath })
                .forEach { file ->
                    if (totalSize <= cacheLimitBytes) return@forEach
                    val length = file.length()
                    if (file.delete()) {
                        deletedFiles++
                        freedBytes += length
                        totalSize -= length
                        Log.i(
                            TAG,
                            "Deleted TDLib cache file=${file.safeRelativeTo(root)} size=$length " +
                                "remaining=$totalSize app=${context.applicationContext.packageName}"
                        )
                    } else {
                        Log.w(TAG, "Could not delete TDLib cache file=${file.safeRelativeTo(root)}")
                    }
                }
        }

        return TelegramCachePruneResult(
            deletedFiles = deletedFiles,
            freedBytes = freedBytes,
            remainingBytes = totalSize,
            limitBytes = cacheLimitBytes
        )
    }

    private fun shouldSkip(file: File): Boolean {
        val name = file.name
        return name.endsWith(".xml", ignoreCase = true) ||
            name.contains("td.bin", ignoreCase = true)
    }

    private fun directorySizeOrZero(file: File): Long =
        runCatching { directorySize(file) }.getOrDefault(0L)

    private fun directorySize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { directorySize(it) } ?: 0L
    }

    private fun File.safeRelativeTo(root: File): String =
        runCatching { relativeTo(root).path }.getOrElse { absolutePath }
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
    val limitBytes: Long
)
