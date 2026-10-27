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
        com.vaydelux.bousygram.core.network.NetworkConnectivityObserver.refresh()
        TelegramPreCacheEngine.resumeBackgroundCaching()
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
