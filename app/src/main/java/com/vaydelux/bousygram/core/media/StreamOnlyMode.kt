package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.data.store.EnterprisePreferences

/**
 * Developer preview only: never enable finite TDLib foreground windows in a
 * production APK before on-device MP4/MKV/TS, seek and low-storage qualification.
 */
object StreamOnlyMode {
    fun isEnabled(): Boolean = BuildConfig.DEBUG &&
        (BuildConfig.BOUNDED_FOREGROUND_STREAMING_EXPERIMENT ||
            EnterprisePreferences.streamOnlyPreviewEnabled())
}
