package com.vaydelux.bousygram.app

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vaydelux.bousygram.core.tdlib.cache.TelegramCachePruner
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.core.tdlib.server.TelegramLogWrapper
import com.vaydelux.bousygram.core.tdlib.server.TelegramPreCacheEngine
import com.vaydelux.bousygram.core.tdlib.server.TelegramProxyManager
import com.vaydelux.bousygram.core.tdlib.utils.ThumbnailExtractor
import com.vaydelux.bousygram.data.store.ManagedAppConfiguration
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.media.AccountMediaResources
import com.vaydelux.bousygram.data.database.AppDatabase
import com.vaydelux.bousygram.data.database.RecentSearchRepository
import com.vaydelux.bousygram.data.repository.ContinueWatchingRepository
import java.io.File

class BousygramApp : Application(), DefaultLifecycleObserver {
    companion object {
        init {
            System.setProperty("io.netty.transport.noNative", "true")
        }

        lateinit var instance: BousygramApp
            private set
    }

    override fun onCreate() {
        System.setProperty("io.netty.transport.noNative", "true")
        super<Application>.onCreate()
        instance = this
        // Install the process-lifetime privacy barrier before TDLib, playback or auth starts.
        // Logout and authorization loss synchronously revoke resources BEFORE another
        // Telegram identity can be activated. Never register this per Activity/login.
        AccountSession.registerInvalidationHandler {
            var failure: Throwable? = null
            fun close(action: () -> Unit) {
                runCatching(action).onFailure { failure = failure ?: it }
            }
            close { AccountMediaResources.invalidateAccountResources() }
            close { RecentSearchRepository.invalidateAccountResources() }
            close { ContinueWatchingRepository.invalidateAccountResources() }
            close { AppDatabase.closeAccountDatabases() }
            failure?.let { throw IllegalStateException("Account resources could not be fully closed", it) }
        }
        ManagedAppConfiguration.refresh(this)
        try {
            System.loadLibrary("tdjni")
        } catch (e: Throwable) {
            android.util.Log.w("BousygramApp", "libtdjni.so not present for current ABI; using TdLibFallbackEngine")
        }
        com.vaydelux.bousygram.data.database.VideoIndexRepository.init(this)
        com.vaydelux.bousygram.core.network.NetworkConnectivityObserver.init(this)
        com.vaydelux.bousygram.core.tdlib.cache.TelegramNativeFileGc.enableStorageOptimizer()
        com.vaydelux.bousygram.core.tdlib.cache.TelegramNativeFileGc.startPeriodicGc(intervalMinutes = 30)
        TelegramLogWrapper.configure()
        LocalStreamingServer.start()
        TelegramProxyManager.startProxy()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        TelegramCachePruner.pruneCacheIfNecessary(this, File(filesDir, "tdlib_files"))
    }

    override fun onStart(owner: LifecycleOwner) {
        ManagedAppConfiguration.refresh(this)
        com.vaydelux.bousygram.core.network.NetworkConnectivityObserver.refresh()
        if (ManagedAppConfiguration.allowBackgroundPrefetch()) {
            TelegramPreCacheEngine.resumeBackgroundCaching()
        } else {
            TelegramPreCacheEngine.pauseBackgroundCaching()
        }
        TelegramProxyManager.startProxy()
    }

    override fun onStop(owner: LifecycleOwner) {
        // The loopback proxy remains active for the process lifetime so PiP, background audio,
        // and MediaSession playback continue unhindered without tearing down sockets.
        com.vaydelux.bousygram.core.tdlib.cache.TelegramNativeFileGc.optimizeStorage()
        TelegramCachePruner.pruneCacheIfNecessary(
            context = this,
            tdLibCacheDir = File(filesDir, "tdlib_files")
        )
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                TelegramPreCacheEngine.pauseBackgroundCaching()
                ThumbnailExtractor.trimMemory(critical = true)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ||
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                TelegramPreCacheEngine.pauseBackgroundCaching()
                ThumbnailExtractor.trimMemory(critical = false)
            }
        }
    }
}
