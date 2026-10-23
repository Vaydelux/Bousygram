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

    fun saveMessagePosition(
        chatId: Long,
        messageId: Long,
        positionMs: Int,
        durationMs: Long = 0L,
        fileId: Int = 0,
        title: String = "",
        thumbnailPath: String? = null
    ) {
        if (chatId == 0L || messageId == 0L) return
        val isCompleted = durationMs > 0L && positionMs >= (durationMs * 0.90f).toLong()
        if (isCompleted || (durationMs > 0 && positionMs >= durationMs - END_THRESHOLD_MS)) {
            clearMessagePosition(chatId, messageId)
            com.vaydelux.bousygram.data.repository.ContinueWatchingRepository.markCompleted(chatId, messageId)
            return
        }
        prefs.edit { putInt(messageKey(chatId, messageId), positionMs) }
        com.vaydelux.bousygram.data.repository.ContinueWatchingRepository.saveProgress(
            chatId = chatId,
            messageId = messageId,
            fileId = fileId,
            title = title,
            thumbnailPath = thumbnailPath,
            positionMs = positionMs.toLong(),
            durationMs = durationMs
        )
    }

    fun getMessagePosition(chatId: Long, messageId: Long, durationMs: Long = 0L): Int {
        if (chatId == 0L || messageId == 0L) return 0
        val pos = prefs.getInt(messageKey(chatId, messageId), 0)
        if (pos <= 0) return 0
        if (durationMs > 0 && pos >= (durationMs * 0.90f).toLong()) {
            clearMessagePosition(chatId, messageId)
            return 0
        }
        return pos
    }

    fun clearMessagePosition(chatId: Long, messageId: Long) {
        if (chatId == 0L || messageId == 0L) return
        prefs.edit { remove(messageKey(chatId, messageId)) }
        com.vaydelux.bousygram.data.repository.ContinueWatchingRepository.markCompleted(chatId, messageId)
    }

    fun isWatched(chatId: Long, messageId: Long, durationMs: Long): Boolean =
        com.vaydelux.bousygram.data.repository.ContinueWatchingRepository.isWatched(chatId, messageId, durationMs)

    fun hasResume(chatId: Long, messageId: Long, fileId: Int?): Boolean =
        getMessagePosition(chatId, messageId) > 0 || (fileId != null && getPosition(fileId) > 0)

    private fun messageKey(chatId: Long, messageId: Long): String = "m:$chatId:$messageId"
}
