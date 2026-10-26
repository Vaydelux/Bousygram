package com.vaydelux.bousygram.core.tdlib.cache

import android.util.Log
import com.vaydelux.bousygram.core.cache.CacheManager
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/**
 * Native File Garbage Collection for TDLib cached media files.
 *
 * TDLib internally manages its structured metadata in SQLite databases (encrypted via Keystore
 * in SetTdlibParameters). Media payload files (videos, documents) are downloaded onto disk
 * and managed by TDLib's LRU file cache. OptimizeStorage triggers TDLib's native file-level GC
 * without touching the SQLite databases or binary logs.
 */
object TelegramNativeFileGc {
    private const val TAG = "TelegramNativeFileGc"
    const val DEFAULT_MAX_CACHE_SIZE_BYTES = 2L * 1024L * 1024L * 1024L // 2 GB
    private const val IMMUNITY_DELAY_SECONDS = 3600 // 1 hour immunity for active/recent media

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var periodicJob: Job? = null

    fun enableStorageOptimizer() {
        try {
            TdLibClient.client.send(
                TdApi.SetOption("use_storage_optimizer", TdApi.OptionValueBoolean(true))
            ) { result ->
                if (result is TdApi.Ok) {
                    Log.i(TAG, "TDLib storage optimizer option enabled successfully")
                } else if (result is TdApi.Error) {
                    Log.w(TAG, "Failed to enable TDLib storage optimizer: ${result.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling TDLib storage optimizer", e)
        }
    }

    fun startPeriodicGc(intervalMinutes: Long = 30) {
        if (periodicJob?.isActive == true) return
        periodicJob = scope.launch {
            while (isActive) {
                delay(intervalMinutes * 60 * 1000L)
                optimizeStorage()
            }
        }
        Log.i(TAG, "Periodic native file GC ticker scheduled every $intervalMinutes minutes")
    }

    fun stopPeriodicGc() {
        periodicJob?.cancel()
        periodicJob = null
    }

    fun optimizeStorage(
        targetSizeBytes: Long = CacheManager.calculateDynamicQuota(),
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        CacheManager.optimizeStorageNative(targetSizeBytes, onComplete)
    }
}

/**
 * Backward compatibility alias for TelegramNativeFileGc.
 */
typealias TelegramStorageOptimizer = TelegramNativeFileGc
