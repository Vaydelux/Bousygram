package com.vaydelux.bousygram.core.tdlib.cache

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.vaydelux.bousygram.app.BousygramApp
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import org.drinkless.tdlib.TdApi
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object OfflineCacheManager {
    private const val TAG = "OfflineCacheManager"
    private const val PREFS_NAME = "tele_cache_prefs"
    private const val KEY_PINNED = "pinned_files"
    private const val KEY_ACCESS_PREFIX = "access_time_"
    
    // Default max size: 5 GB
    const val DEFAULT_MAX_CACHE_SIZE = 5L * 1024 * 1024 * 1024

    private val prefs by lazy {
        BousygramApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Thread-safe set of pinned files
    private val pinnedFiles = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    private val cleanupExecutor = Executors.newSingleThreadExecutor()

    init {
        val pinnedStrSet = prefs.getStringSet(KEY_PINNED, emptySet()) ?: emptySet()
        pinnedStrSet.forEach { idStr ->
            idStr.toIntOrNull()?.let { pinnedFiles.add(it) }
        }
    }

    fun isPinned(fileId: Int): Boolean {
        return pinnedFiles.contains(fileId)
    }

    fun pinFile(fileId: Int) {
        pinnedFiles.add(fileId)
        savePinned()
        
        // Trigger background download of the entire file with priority 32
        Log.i(TAG, "Pinning file $fileId for offline playback...")
        TdLibClient.client.send(
            TdApi.DownloadFile(fileId, 32, 0, 0, false),
            { }
        )
    }

    fun unpinFile(fileId: Int) {
        pinnedFiles.remove(fileId)
        savePinned()
        Log.i(TAG, "Unpinned file $fileId")
    }

    fun markFileAccessed(fileId: Int) {
        prefs.edit { putLong(KEY_ACCESS_PREFIX + fileId, System.currentTimeMillis()) }
        // Trigger cache check in background
        triggerCacheCleanupIfNeeded()
    }

    private fun savePinned() {
        val strSet = pinnedFiles.map { it.toString() }.toSet()
        prefs.edit { putStringSet(KEY_PINNED, strSet) }
    }

    private fun triggerCacheCleanupIfNeeded() {
        cleanupExecutor.execute {
            try {
                performCacheCleanup()
            } catch (e: Exception) {
                Log.e(TAG, "Error performing cache cleanup", e)
            }
        }
    }

    private fun performCacheCleanup() {
        // 1. Gather all files tracked in SharedPreferences that are not pinned
        val allPrefs = prefs.all
        val fileAccessTimes = mutableListOf<Pair<Int, Long>>()
        
        allPrefs.keys.forEach { key ->
            if (key.startsWith(KEY_ACCESS_PREFIX)) {
                val fileId = key.substringAfter(KEY_ACCESS_PREFIX).toIntOrNull()
                val accessTime = allPrefs[key] as? Long
                if (fileId != null && accessTime != null && !pinnedFiles.contains(fileId)) {
                    fileAccessTimes.add(Pair(fileId, accessTime))
                }
            }
        }

        // Sort by access time ascending (oldest first)
        fileAccessTimes.sortBy { it.second }

        // 2. Query TDLib to get size of each tracked file and check total downloaded size
        var totalSize = 0L
        val activeFiles = mutableListOf<TdApi.File>()
        
        val filesToCheck = fileAccessTimes.map { it.first } + pinnedFiles.toList()
        
        filesToCheck.forEach { fileId ->
            val file = getFileBlocking(fileId)
            if (file != null && !file.local.path.isNullOrEmpty()) {
                val localSize = file.local.downloadedSize.toLong()
                totalSize += localSize
                if (!pinnedFiles.contains(fileId)) {
                    activeFiles.add(file)
                }
            }
        }

        Log.d(TAG, "Current total cache size: ${totalSize / (1024 * 1024)} MB / ${DEFAULT_MAX_CACHE_SIZE / (1024 * 1024)} MB")

        // 3. If total downloaded size exceeds quota, evict oldest non-pinned files
        if (totalSize > DEFAULT_MAX_CACHE_SIZE) {
            // Sort activeFiles by access time (which we retrieved from fileAccessTimes)
            val accessTimeMap = fileAccessTimes.toMap()
            activeFiles.sortBy { accessTimeMap[it.id] ?: 0L }

            for (file in activeFiles) {
                if (totalSize <= DEFAULT_MAX_CACHE_SIZE) break
                val localSize = file.local.downloadedSize.toLong()
                if (localSize > 0) {
                    Log.i(TAG, "Cache size limit exceeded. Evicting fileId=${file.id} (size=${localSize / (1024 * 1024)} MB)...")
                    // Request TDLib to delete the file
                    TdLibClient.client.send(TdApi.DeleteFile(file.id), { })
                    // Remove access record
                    prefs.edit { remove(KEY_ACCESS_PREFIX + file.id) }
                    totalSize -= localSize
                }
            }
        }
    }

    private fun getFileBlocking(fileId: Int): TdApi.File? {
        var result: TdApi.File? = null
        val lock = Object()
        synchronized(lock) {
            TdLibClient.client.send(
                TdApi.GetFile(fileId),
                { res ->
                    synchronized(lock) {
                        result = res as? TdApi.File
                        lock.notifyAll()
                    }
                }
            )
            lock.wait(500)
        }
        return result
    }
}
