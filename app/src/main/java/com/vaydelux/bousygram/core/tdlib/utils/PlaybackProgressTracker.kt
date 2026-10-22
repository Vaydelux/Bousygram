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

    fun saveMessagePosition(chatId: Long, messageId: Long, positionMs: Int, durationMs: Long = 0L) {
        if (chatId == 0L || messageId == 0L) return
        if (durationMs > 0 && positionMs >= durationMs - END_THRESHOLD_MS) {
            clearMessagePosition(chatId, messageId)
            return
        }
        prefs.edit { putInt(messageKey(chatId, messageId), positionMs) }
    }

    fun getMessagePosition(chatId: Long, messageId: Long, durationMs: Long = 0L): Int {
        if (chatId == 0L || messageId == 0L) return 0
        val pos = prefs.getInt(messageKey(chatId, messageId), 0)
        if (pos <= 0) return 0
        if (durationMs > 0 && pos >= durationMs - END_THRESHOLD_MS) {
            clearMessagePosition(chatId, messageId)
            return 0
        }
        return pos
    }

    fun clearMessagePosition(chatId: Long, messageId: Long) {
        if (chatId == 0L || messageId == 0L) return
        prefs.edit { remove(messageKey(chatId, messageId)) }
    }

    fun hasResume(chatId: Long, messageId: Long, fileId: Int?): Boolean =
        getMessagePosition(chatId, messageId) > 0 || (fileId != null && getPosition(fileId) > 0)

    private fun messageKey(chatId: Long, messageId: Long): String = "m:$chatId:$messageId"
}
