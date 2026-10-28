package com.vaydelux.bousygram.data.store

import android.content.Context
import android.os.Bundle
import androidx.annotation.VisibleForTesting

/** Reads Android Enterprise managed configuration without requiring a backend. */
object ManagedAppConfiguration {
    private const val ALLOW_CELLULAR = "allow_cellular_streaming"
    private const val CACHE_LIMIT_MB = "cache_limit_mb"
    private const val ALLOW_EXTERNAL = "allow_external_players"
    private const val DEFAULT_QUALITY = "default_media_quality"
    private const val ALLOW_PREFETCH = "allow_background_prefetch"

    @Volatile
    private var restrictions: Bundle = Bundle()

    fun refresh(context: Context) {
        restrictions = if (android.os.Build.VERSION.SDK_INT >= 21) {
            context.getSystemService(Context.RESTRICTIONS_SERVICE)
                ?.let { service ->
                    val manager = service as android.content.RestrictionsManager
                    manager.applicationRestrictions ?: Bundle()
                } ?: Bundle()
        } else {
            Bundle()
        }
    }

    fun allowCellularStreaming(): Boolean = restrictions.getBoolean(ALLOW_CELLULAR, true)
    fun allowExternalPlayers(): Boolean = restrictions.getBoolean(ALLOW_EXTERNAL, false)
    fun allowBackgroundPrefetch(): Boolean = restrictions.getBoolean(ALLOW_PREFETCH, false)
    fun defaultMediaQuality(): String = restrictions.getString(DEFAULT_QUALITY, "auto")
        ?.takeIf { it in setOf("auto", "low", "medium", "high") } ?: "auto"

    fun cacheLimitBytes(): Long? = restrictions.getInt(CACHE_LIMIT_MB, 0)
        .takeIf { it > 0 }
        ?.toLong()
        ?.times(1024L * 1024L)

    @VisibleForTesting
    fun setRestrictionsForTesting(values: Bundle) {
        restrictions = Bundle(values)
    }
}
