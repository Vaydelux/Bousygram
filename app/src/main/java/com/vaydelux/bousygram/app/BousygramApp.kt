package com.vaydelux.bousygram.app

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.vaydelux.bousygram.core.tdlib.cache.TelegramCachePruner
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.core.tdlib.server.TelegramLogWrapper
import com.vaydelux.bousygram.core.tdlib.server.TelegramPreCacheEngine
import com.vaydelux.bousygram.core.tdlib.server.TelegramProxyManager
import com.vaydelux.bousygram.core.tdlib.utils.ThumbnailExtractor
import com.vaydelux.bousygram.presentation.extras.ExtrasCatalogApi
import com.vaydelux.bousygram.data.store.ManagedAppConfiguration
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.media.AccountMediaResources
import com.vaydelux.bousygram.core.media.SeekThumbnailManager
import com.vaydelux.bousygram.data.database.AppDatabase
import com.vaydelux.bousygram.data.database.RecentSearchRepository
import com.vaydelux.bousygram.data.repository.ContinueWatchingRepository
import java.io.File

class BousygramApp : Application(), DefaultLifecycleObserver {
    private val runtimeGate = TelegramRuntimeGate { initializeTelegramServices() }

    val telegramRuntimeStarted: Boolean get() = runtimeGate.isReady

    /** Called on first entry into Telegram mode, never during Extras-only startup. */
    fun ensureTelegramRuntimeStarted() = runtimeGate.ensureStarted()

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
        // The account invalidation handler and lifecycle observer must exist from launch,
        // even when Extras is selected. Telegram native/loopback initialization is deferred.
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private fun initializeTelegramServices() {
        // Important ordering: native binding before TDLib client/logging; update
        // and account protections are already registered by Application.onCreate.
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
        TelegramCachePruner.pruneCacheIfNecessary(this, File(filesDir, "tdlib_files"))
        // The process may already be foregrounded when Telegram is first opened.
        // Do not rely on a second process onStart event to resume proxy/prefetch.
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            refreshTelegramForegroundServices()
        }
    }

    private fun refreshTelegramForegroundServices() {
        com.vaydelux.bousygram.core.network.NetworkConnectivityObserver.refresh()
        if (ManagedAppConfiguration.allowBackgroundPrefetch()) {
            TelegramPreCacheEngine.resumeBackgroundCaching()
        } else {
            TelegramPreCacheEngine.pauseBackgroundCaching()
        }
        TelegramProxyManager.startProxy()
    }

    override fun onStart(owner: LifecycleOwner) {
        ManagedAppConfiguration.refresh(this)
        if (telegramRuntimeStarted) refreshTelegramForegroundServices()
    }

    override fun onStop(owner: LifecycleOwner) {
        // Preserve active Telegram sessions, sockets and playback across modes and PiP.
        // Extras-only processes have no Telegram jobs or files to prune.
        if (!telegramRuntimeStarted) return
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
                ExtrasCatalogApi.trimMemory(critical = true)
                if (telegramRuntimeStarted) {
                    TelegramPreCacheEngine.pauseBackgroundCaching()
                    ThumbnailExtractor.trimMemory(critical = true)
                    SeekThumbnailManager.trimMemory(critical = true)
                }
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ||
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                ExtrasCatalogApi.trimMemory(critical = false)
                if (telegramRuntimeStarted) {
                    TelegramPreCacheEngine.pauseBackgroundCaching()
                    ThumbnailExtractor.trimMemory(critical = false)
                    SeekThumbnailManager.trimMemory(critical = false)
                }
            }
        }
    }
}
