package com.vaydelux.bousygram.core.media

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log

class StreamingSessionManager(context: Context) {
    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(PowerManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)

    private val wakeLock: PowerManager.WakeLock? = powerManager?.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "Bousygram:StreamingWakeLock"
    )?.apply {
        setReferenceCounted(false)
    }

    private val wifiLock: WifiManager.WifiLock? = wifiManager?.createWifiLock(
        WifiManager.WIFI_MODE_FULL_HIGH_PERF,
        "Bousygram:StreamingWifiLock"
    )?.apply {
        setReferenceCounted(false)
    }

    fun acquireLocks() {
        runIndependentStreamingLockActions(
            first = { if (wakeLock?.isHeld != true) wakeLock?.acquire(STREAMING_LOCK_TIMEOUT_MS) },
            second = { if (wifiLock?.isHeld != true) wifiLock?.acquire() },
            onFailure = { error -> Log.w(TAG, "Streaming lock acquisition failed (${error.javaClass.simpleName})") }
        )
    }

    fun releaseLocks() {
        runIndependentStreamingLockActions(
            first = { if (wifiLock?.isHeld == true) wifiLock.release() },
            second = { if (wakeLock?.isHeld == true) wakeLock.release() },
            onFailure = { error -> Log.w(TAG, "Streaming lock release failed (${error.javaClass.simpleName})") }
        )
    }

    private companion object {
        private const val TAG = "StreamingSessionManager"
        private const val STREAMING_LOCK_TIMEOUT_MS = 4L * 60L * 60L * 1000L
    }
}
