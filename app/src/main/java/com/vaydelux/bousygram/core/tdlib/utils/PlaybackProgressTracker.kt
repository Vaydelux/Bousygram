package com.vaydelux.bousygram.core.tdlib.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.vaydelux.bousygram.app.BousygramApp
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.data.repository.ContinueWatchingRepository
import com.vaydelux.bousygram.core.tdlib.cache.OfflineCacheManager

/**
 * Legacy resume positions are intentionally not read from the old, global preferences file:
 * that file has no authenticated owner and could disclose Account A's playback to Account B.
 * The old file remains untouched until an explicit, verifiable migration strategy exists.
 */
object PlaybackProgressTracker {
    private const val PREF_NAME = "playback_resume_positions"
    private const val END_THRESHOLD_MS = 5_000

    private val lock = Any()
    private var cached: Pair<AccountSnapshot, SharedPreferences>? = null

    /** Drop access to the previous account immediately on auth transitions. */
    fun invalidateAccountResources() = synchronized(lock) {
        cached = null
    }

    internal fun accountPreferenceName(accountId: Long): String {
        require(accountId > 0L)
        return "${PREF_NAME}_account_$accountId"
    }

    private fun preferencesFor(owner: AccountSnapshot): SharedPreferences = synchronized(lock) {
        val previous = cached
        if (previous?.first == owner) {
            previous.second
        } else {
            BousygramApp.instance.getSharedPreferences(
                accountPreferenceName(owner.accountId), Context.MODE_PRIVATE
            ).also { cached = owner to it }
        }
    }

    /**
     * The account-generation check and final preference operation are serialized with
     * logout. A delayed callback cannot write using a newly authenticated identity.
     */
    private fun <T> withCurrentAccount(default: T, block: (SharedPreferences, AccountSnapshot) -> T): T {
        val owner = AccountSession.current() ?: return default
        var result = default
        AccountSession.runIfCurrent(owner) {
            result = block(preferencesFor(owner), owner)
        }
        return result
    }

    fun savePosition(fileId: Int, positionMs: Int, durationMs: Long = 0L) {
        if (fileId <= 0) return
        withCurrentAccount(Unit) { prefs, _ ->
            if (durationMs > 0L && positionMs >= durationMs - END_THRESHOLD_MS) {
                prefs.edit { remove(fileId.toString()) }
            } else {
                prefs.edit { putInt(fileId.toString(), positionMs.coerceAtLeast(0)) }
            }
        }
    }

    fun getPosition(fileId: Int, durationMs: Long = 0L): Int {
        if (fileId <= 0) return 0
        return withCurrentAccount(0) { prefs, _ ->
            val pos = prefs.getInt(fileId.toString(), 0)
            if (pos <= 0) 0
            else if (durationMs > 0L && pos >= durationMs - END_THRESHOLD_MS) {
                prefs.edit { remove(fileId.toString()) }
                0
            } else pos
        }
    }

    fun clearPosition(fileId: Int) {
        if (fileId <= 0) return
        withCurrentAccount(Unit) { prefs, _ -> prefs.edit { remove(fileId.toString()) } }
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
        withCurrentAccount(Unit) { prefs, owner ->
            val isCompleted = durationMs > 0L && positionMs >= (durationMs * 0.90f).toLong()
            if (isCompleted || (durationMs > 0L && positionMs >= durationMs - END_THRESHOLD_MS)) {
                prefs.edit { remove(messageKey(chatId, messageId)) }
                ContinueWatchingRepository.markCompleted(chatId, messageId, owner)
            } else {
                prefs.edit { putInt(messageKey(chatId, messageId), positionMs.coerceAtLeast(0)) }
                ContinueWatchingRepository.saveProgress(
                    chatId = chatId,
                    messageId = messageId,
                    fileId = fileId,
                    title = title,
                    thumbnailPath = thumbnailPath,
                    positionMs = positionMs.coerceAtLeast(0).toLong(),
                    durationMs = durationMs,
                    owner = owner
                )
            }
        }
    }

    fun getMessagePosition(chatId: Long, messageId: Long, durationMs: Long = 0L): Int {
        if (chatId == 0L || messageId == 0L) return 0
        return withCurrentAccount(0) { prefs, _ ->
            val key = messageKey(chatId, messageId)
            val pos = prefs.getInt(key, 0)
            if (pos <= 0) 0
            else if (durationMs > 0L && pos >= (durationMs * 0.90f).toLong()) {
                prefs.edit { remove(key) }
                0
            } else pos
        }
    }

    fun clearMessagePosition(chatId: Long, messageId: Long) {
        if (chatId == 0L || messageId == 0L) return
        withCurrentAccount(Unit) { prefs, owner ->
            prefs.edit { remove(messageKey(chatId, messageId)) }
            ContinueWatchingRepository.markCompleted(chatId, messageId, owner)
            OfflineCacheManager.triggerCacheCleanupIfNeeded()
        }
    }

    fun isWatched(chatId: Long, messageId: Long, durationMs: Long): Boolean =
        ContinueWatchingRepository.isWatched(chatId, messageId, durationMs)

    fun isWatched(fileId: Int): Boolean = ContinueWatchingRepository.isWatched(fileId)

    fun hasResume(chatId: Long, messageId: Long, fileId: Int?): Boolean =
        withCurrentAccount(false) { prefs, _ ->
            (chatId != 0L && messageId != 0L && prefs.getInt(messageKey(chatId, messageId), 0) > 0) ||
                (fileId != null && fileId > 0 && prefs.getInt(fileId.toString(), 0) > 0)
        }

    private fun messageKey(chatId: Long, messageId: Long): String = "m:$chatId:$messageId"
}
