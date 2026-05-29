package com.vaydelux.bousygram.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vaydelux.bousygram.core.tdlib.cache.TelegramCachePruner
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.core.tdlib.server.TelegramLogWrapper
import com.vaydelux.bousygram.core.tdlib.server.TelegramProxyManager
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
        TelegramProxyManager.startProxy()
    }

    override fun onStop(owner: LifecycleOwner) {
        TelegramProxyManager.stopProxy()
        TelegramCachePruner.pruneCacheIfNecessary(
            context = this,
            tdLibCacheDir = File(filesDir, "tdlib_files")
        )
    }
}
