package com.vaydelux.bousygram.core.media

import android.os.Handler
import android.os.Looper
import com.vaydelux.bousygram.core.cache.CacheManager
import com.vaydelux.bousygram.core.tdlib.cache.TelegramNativeFileGc
import com.vaydelux.bousygram.core.tdlib.prefetch.FocusPrefetchManager
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.core.tdlib.server.StreamAccessRegistry
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler
import com.vaydelux.bousygram.core.tdlib.server.TelegramPreCacheEngine
import com.vaydelux.bousygram.core.tdlib.server.TelegramProxyManager
import com.vaydelux.bousygram.core.tdlib.utils.PlaybackProgressTracker
import com.vaydelux.bousygram.core.tdlib.utils.ThumbnailExtractor
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** Synchronous privacy barrier, called before a new account is published. */
object AccountMediaResources {
    fun invalidateAccountResources() {
        var failure: Throwable? = null
        fun invalidate(action: () -> Unit) { runCatching(action).onFailure { failure = failure ?: it } }
        invalidate { StreamAccessRegistry.revokeAll() }
        invalidate { LocalStreamingServer.invalidateAccountResources() }
        invalidate { TelegramProxyManager.stopProxy() }
        invalidate { FocusPrefetchManager.cancelAll() }
        invalidate { TelegramPreCacheEngine.stopAllCaching() }
        invalidate { PlaybackSessionController.stopAll() }
        invalidate { TelegramChunkScheduler.invalidateAccountResources() }
        invalidate { TelegramNativeFileGc.stopPeriodicGc() }
        invalidate { CacheManager.invalidateAccountResources() }
        invalidate { MediaDurationResolver.invalidateAccountResources() }
        invalidate { SeekThumbnailManager.invalidateAccountResources() }
        invalidate { ThumbnailExtractor.invalidateAccountResources() }
        invalidate { PlaybackProgressTracker.invalidateAccountResources() }
        failure?.let { throw IllegalStateException("Private media resources could not be fully closed", it) }
    }

    /** Media3 requires its owner thread. Failure keeps account activation blocked. */
    fun onPlayerThread(action: () -> Unit) {
        val main = Looper.getMainLooper()
        if (Looper.myLooper() == main) { action(); return }
        val task = FutureTask { action() }
        check(Handler(main).post(task)) { "The playback owner is unavailable" }
        task.get(5, TimeUnit.SECONDS)
    }
}
