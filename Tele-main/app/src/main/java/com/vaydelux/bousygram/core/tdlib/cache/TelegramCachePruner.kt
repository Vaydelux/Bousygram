package com.vaydelux.bousygram.core.tdlib.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

object TelegramCachePruner {
    private const val TAG = "TelegramCachePruner"
    private const val CACHE_CEILING_BYTES = 2L * 1024L * 1024L * 1024L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun pruneCacheIfNecessary(context: Context, tdLibCacheDir: File) {
        val appContext = context.applicationContext
        scope.launch {
            val root = tdLibCacheDir.canonicalFile
            if (!root.exists() || !root.isDirectory) return@launch

            val files = root
                .walkTopDown()
                .filter { it.isFile }
                .filterNot { shouldSkip(it) }
                .toList()
            var totalSize = files.sumOf { it.length() }
            if (totalSize <= CACHE_CEILING_BYTES) return@launch

            files
                .sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.absolutePath })
                .forEach { file ->
                    if (totalSize <= CACHE_CEILING_BYTES) return@forEach
                    val length = file.length()
                    if (file.delete()) {
                        totalSize -= length
                        Log.i(
                            TAG,
                            "Deleted TDLib cache file=${file.safeRelativeTo(root)} size=$length " +
                                "remaining=$totalSize app=${appContext.packageName}"
                        )
                    } else {
                        Log.w(TAG, "Could not delete TDLib cache file=${file.safeRelativeTo(root)}")
                    }
                }
        }
    }

    private fun shouldSkip(file: File): Boolean {
        val name = file.name
        return name.endsWith(".xml", ignoreCase = true) ||
            name.contains("td.bin", ignoreCase = true)
    }

    private fun File.safeRelativeTo(root: File): String =
        runCatching { relativeTo(root).path }.getOrElse { absolutePath }
}
