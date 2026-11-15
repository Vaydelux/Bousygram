package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.data.store.EnterprisePreferences

/**
 * Developer preview only: never enable finite TDLib foreground windows in a
 * production APK before on-device MP4/MKV/TS, seek and low-storage qualification.
 */
object StreamOnlyMode {
    fun isEnabled(): Boolean {
        if (!BuildConfig.DEBUG) return false
        if (BuildConfig.BOUNDED_FOREGROUND_STREAMING_EXPERIMENT) return true
        // Local JVM and streaming scheduler tests run without an Android
        // Application instance. A missing preference context is not permission
        // to enable experimental native downloading.
        return runCatching { EnterprisePreferences.streamOnlyPreviewEnabled() }
            .getOrDefault(false)
    }
}
