package com.vaydelux.bousygram.core.media.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import com.vaydelux.bousygram.data.store.SelectedPlayerEngine

data class EngineVerdict(
    val recommendedEngine: SelectedPlayerEngine,
    val useTextureView: Boolean,
    val reason: String,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Persistent store for engine & surface verdicts per device, codec, container, and resolution.
 * Prevents repeating 3-4s black-screen stalls when playing known problematic media formats.
 */
object EngineVerdictStore {
    private const val TAG = "EngineVerdictStore"
    private const val PREFS_NAME = "bousygram_engine_verdicts"

    private val inMemoryCache = mutableMapOf<String, EngineVerdict>()

    fun buildKey(
        mimeType: String?,
        container: String?,
        profile: Int = 0,
        is10Bit: Boolean = false,
        deviceModel: String = Build.MODEL
    ): String {
        val mime = mimeType?.lowercase()?.trim() ?: "unknown_mime"
        val cont = container?.lowercase()?.trim() ?: "unknown_container"
        val bit = if (is10Bit) "10bit" else "8bit"
        return "${deviceModel}_${cont}_${mime}_p${profile}_$bit"
    }

    @Synchronized
    fun getVerdict(context: Context, key: String): EngineVerdict? {
        inMemoryCache[key]?.let { return it }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val engineStr = prefs.getString("${key}_engine", null) ?: return null
        val useTexture = prefs.getBoolean("${key}_texture", false)
        val reason = prefs.getString("${key}_reason", "Persisted verdict") ?: "Persisted verdict"
        val time = prefs.getLong("${key}_time", 0L)

        val engine = when (engineStr) {
            "MPV", "Mpv", "MpvPlayer" -> SelectedPlayerEngine.MPV
            else -> SelectedPlayerEngine.ExoPlayer
        }

        val verdict = EngineVerdict(engine, useTexture, reason, time)
        inMemoryCache[key] = verdict
        return verdict
    }

    @Synchronized
    fun recordVerdict(context: Context, key: String, verdict: EngineVerdict) {
        inMemoryCache[key] = verdict
        Log.i(TAG, "Recording verdict for $key: engine=${verdict.recommendedEngine}, texture=${verdict.useTextureView}, reason=${verdict.reason}")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("${key}_engine", verdict.recommendedEngine.name)
            .putBoolean("${key}_texture", verdict.useTextureView)
            .putString("${key}_reason", verdict.reason)
            .putLong("${key}_time", verdict.timestampMs)
            .apply()
    }

    @Synchronized
    fun clearAll(context: Context) {
        inMemoryCache.clear()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
