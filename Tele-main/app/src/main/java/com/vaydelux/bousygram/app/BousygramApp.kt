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
        lateinit var instance: BousygramApp
            private set
    }

    override fun onCreate() {
        super<Application>.onCreate()
        instance = this
        System.loadLibrary("tdjni")
        TelegramLogWrapper.configure()
        LocalStreamingServer.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        TelegramPreCacheEngine.resumeBackgroundCaching()
        TelegramProxyManager.startProxy()
    }

    override fun onStop(owner: LifecycleOwner) {
        TelegramProxyManager.stopProxy()
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
