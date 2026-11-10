package com.vaydelux.bousygram.app

import android.app.Application
import android.app.ActivityManager
import android.os.Process
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.vaydelux.bousygram.core.cache.TvStorageBudget
import coil.memory.MemoryCache
import com.vaydelux.bousygram.core.memory.decodedPosterCacheHeapFraction
import com.vaydelux.bousygram.core.memory.MediaMemoryPressure
import com.vaydelux.bousygram.core.cache.CacheManager
import com.vaydelux.bousygram.core.cache.StoragePressureMonitor
import com.vaydelux.bousygram.core.memory.MediaMemoryTrim
import com.vaydelux.bousygram.core.memory.mediaMemoryTrim
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
import com.vaydelux.bousygram.core.tdlib.utils.PlaybackProgressTracker
import com.vaydelux.bousygram.presentation.extras.ExtrasCatalogApi
import com.vaydelux.bousygram.data.store.ManagedAppConfiguration
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.media.AccountMediaResources
import com.vaydelux.bousygram.core.media.SeekThumbnailManager
import com.vaydelux.bousygram.data.database.AppDatabase
import com.vaydelux.bousygram.data.database.RecentSearchRepository
import com.vaydelux.bousygram.data.repository.ContinueWatchingRepository
import java.io.File

class BousygramApp : Application(), DefaultLifecycleObserver, ImageLoaderFactory {
    @Volatile private var posterImageLoader: ImageLoader? = null

    /** Coil 2.x uses this factory lazily. No image decoder/network work at startup. */
    override fun newImageLoader(): ImageLoader {
        val lowMemory = (getSystemService(ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true ||
            !Process.is64Bit()
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(decodedPosterCacheHeapFraction(lowMemory))
                    .build()
            }
            // Coil's default disk allocation is too large for 8 GB TV storage.
            // Reuse the standard image_cache directory so old poster files
            // are eligible for the same bounded LRU eviction on upgrade.
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(TvStorageBudget.POSTER_DISK_CACHE_BYTES)
                    .build()
            }
            .build()
            .also { posterImageLoader = it }
    }
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
            // Remove account-specific thumbnails from Coil's decoded memory
            // cache; do not instantiate a loader solely for logout.
            close { posterImageLoader?.memoryCache?.clear() }
            close { RecentSearchRepository.invalidateAccountResources() }
            close { ContinueWatchingRepository.invalidateAccountResources() }
            // Drain pending SharedPreferences apply() writes and clear only the
            // cached revoked account before TelegramAuthManager purges its files.
            // Failure must block activation of another verified Telegram identity.
            close { PlaybackProgressTracker.invalidateAccountResources() }
            close { AppDatabase.closeAccountDatabases() }
            failure?.let { throw IllegalStateException("Account resources could not be fully closed", it) }
        }
        ManagedAppConfiguration.refresh(this)
        // Recycle obsolete generated PNGs from all account-scoped cache dirs
        // without doing a synchronous disk scan on cold Android TV startup.
        ThumbnailExtractor.pruneDiskCacheAsync(this)
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
        com.vaydelux.bousygram.core.tdlib.cache.TelegramNativeFileGc.startPeriodicGc(intervalMinutes = 10)
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
        if (ManagedAppConfiguration.allowBackgroundPrefetch() && !StoragePressureMonitor.isLow()) {
            TelegramPreCacheEngine.resumeBackgroundCaching()
        } else {
            TelegramPreCacheEngine.pauseBackgroundCaching()
        }
        TelegramProxyManager.startProxy()
    }

    override fun onStart(owner: LifecycleOwner) {
        ManagedAppConfiguration.refresh(this)
        if (telegramRuntimeStarted) {
            if (StoragePressureMonitor.isLow()) {
                com.vaydelux.bousygram.core.tdlib.prefetch.FocusPrefetchManager.cancelAll()
                TelegramPreCacheEngine.pauseBackgroundCaching()
                CacheManager.triggerCacheCleanupIfNeeded()
            }
            refreshTelegramForegroundServices()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        // Preserve active Telegram sessions, sockets and playback across modes and PiP.
        // Extras-only processes have no Telegram jobs or files to prune.
        if (!telegramRuntimeStarted) return
        // One coalesced protected sweep is enough. The prior stop path
        // scheduled two independent TDLib scans for the same files.
        CacheManager.triggerCacheCleanupIfNeeded()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val severity = mediaMemoryTrim(level)
        if (severity == MediaMemoryTrim.NONE) return
        val critical = severity == MediaMemoryTrim.CRITICAL
        MediaMemoryPressure.report(severity)
        ExtrasCatalogApi.trimMemory(critical)
        if (critical) posterImageLoader?.memoryCache?.clear()
        if (telegramRuntimeStarted) {
            // Never tear down active decoded media or a foreground video here.
            // Pause speculative work and release only reclaimable thumbnails.
            TelegramPreCacheEngine.pauseBackgroundCaching()
            ThumbnailExtractor.trimMemory(critical)
            SeekThumbnailManager.trimMemory(critical)
        }
    }
}
