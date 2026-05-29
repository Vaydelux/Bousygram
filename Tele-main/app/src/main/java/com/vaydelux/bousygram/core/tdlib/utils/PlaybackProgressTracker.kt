package com.vaydelux.bousygram.core.tdlib.utils

import android.content.Context
import androidx.core.content.edit
import com.vaydelux.bousygram.app.BousygramApp

object PlaybackProgressTracker {
    private const val PREF_NAME = "playback_resume_positions"
    private const val END_THRESHOLD_MS = 5_000

    private val prefs by lazy {
        BousygramApp.instance.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun savePosition(fileId: Int, positionMs: Int, durationMs: Long = 0L) {
        if (fileId <= 0) return
        if (durationMs > 0 && positionMs >= durationMs - END_THRESHOLD_MS) {
            clearPosition(fileId)
            return
        }
        prefs.edit { putInt(fileId.toString(), positionMs) }
    }

    fun getPosition(fileId: Int, durationMs: Long = 0L): Int {
        if (fileId <= 0) return 0
        val pos = prefs.getInt(fileId.toString(), 0)
        if (pos <= 0) return 0
        if (durationMs > 0 && pos >= durationMs - END_THRESHOLD_MS) {
            clearPosition(fileId)
            return 0
        }
        return pos
    }

    fun clearPosition(fileId: Int) {
        if (fileId <= 0) return
        prefs.edit { remove(fileId.toString()) }
    }
}
