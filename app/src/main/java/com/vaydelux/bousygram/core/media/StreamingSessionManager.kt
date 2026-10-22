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
        runCatching {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(STREAMING_LOCK_TIMEOUT_MS)
            }
            if (wifiLock?.isHeld != true) {
                wifiLock?.acquire()
            }
        }.onFailure { error ->
            Log.w(TAG, "Could not acquire streaming locks: ${error.message}")
        }
    }

    fun releaseLocks() {
        runCatching {
            if (wifiLock?.isHeld == true) {
                wifiLock.release()
            }
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        }.onFailure { error ->
            Log.w(TAG, "Could not release streaming locks: ${error.message}")
        }
    }

    private companion object {
        private const val TAG = "StreamingSessionManager"
        private const val STREAMING_LOCK_TIMEOUT_MS = 4L * 60L * 60L * 1000L
    }
}
