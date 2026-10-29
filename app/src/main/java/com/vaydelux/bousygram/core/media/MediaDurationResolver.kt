package com.vaydelux.bousygram.core.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.core.content.edit
import com.vaydelux.bousygram.app.BousygramApp
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Resolves full movie length from local Telegram cache or stream metadata.
 * ExoPlayer often reports ~2 minutes during progressive streaming; this provides the real duration.
 */
object MediaDurationResolver {
    private const val TAG = "MediaDurationResolver"
    private const val PREFS_NAME = "trusted_media_durations"
    private const val KEY_PREFIX = "duration_ms_"
    private const val SMALL_STREAM_MAX_BYTES = 150L * 1024L * 1024L
    private const val SHORT_MOVIE_THRESHOLD_MS = 10L * 60L * 1000L
    private val cacheMs = ConcurrentHashMap<Pair<Long, Int>, Long>()
    private fun prefs(owner: AccountSnapshot) = BousygramApp.instance.getSharedPreferences(
        "${PREFS_NAME}_${owner.accountId}", Context.MODE_PRIVATE
    )

    fun invalidateAccountResources() = cacheMs.clear()

    enum class DurationSource {
        None,
        TdLib,
        LocalFile,
        Stream,
        Player,
        Cache
    }

    data class DurationEstimate(
        val durationMs: Long,
        val source: DurationSource,
        val trusted: Boolean
    ) {
        val hasDuration: Boolean = durationMs > 0L
    }

    fun cachedDurationMs(fileId: Int): Long {
        val owner = AccountSession.current() ?: return 0L
        return cachedDurationMs(fileId, owner)
    }

    private fun cachedDurationMs(fileId: Int, owner: AccountSnapshot): Long {
        if (!AccountSession.isCurrent(owner)) return 0L
        val key = owner.accountId to fileId
        cacheMs[key]?.let { return it }
        val stored = prefs(owner).getLong(durationKey(fileId), 0L)
        if (stored > 0L && AccountSession.isCurrent(owner)) {
            cacheMs[key] = stored
        }
        return stored.takeIf { AccountSession.isCurrent(owner) } ?: 0L
    }

    fun rememberDuration(fileId: Int, durationMs: Long) {
        rememberTrustedDuration(fileId, durationMs, DurationSource.LocalFile)
    }

    fun rememberTdLibDuration(fileId: Int, durationMs: Long) {
        rememberTrustedDuration(fileId, durationMs, DurationSource.TdLib)
    }

    fun rememberTrustedDuration(fileId: Int, durationMs: Long, source: DurationSource) {
        val owner = AccountSession.current() ?: return
        rememberTrustedDuration(fileId, durationMs, source, owner)
    }

    private fun rememberTrustedDuration(fileId: Int, durationMs: Long, source: DurationSource, owner: AccountSnapshot) {
        if (!AccountSession.isCurrent(owner)) return
        if (durationMs <= 0L) return
        val value = max(cachedDurationMs(fileId, owner), durationMs)
        cacheMs[owner.accountId to fileId] = value
        prefs(owner).edit { putLong(durationKey(fileId), value) }
    }

    /**
     * @param hintSeconds Duration from TDLib message (0 for document files).
     */
    fun resolveDurationEstimate(
        fileId: Int,
        totalSize: Long,
        streamUrl: String?,
        hintSeconds: Int = 0
    ): DurationEstimate {
        val owner = AccountSession.current() ?: return DurationEstimate(0L, DurationSource.None, false)
        val hintMs = hintSeconds.takeIf { it > 0 }?.times(1000L) ?: 0L
        if (hintMs > 0L) {
            rememberTrustedDuration(fileId, hintMs, DurationSource.TdLib, owner)
        }
        val cached = cachedDurationMs(fileId, owner)
        if (cached > 0L) {
            return DurationEstimate(cached, DurationSource.Cache, trusted = true)
        }

        val fromLocal = LocalStreamingServer.probeDurationFromLocalFile(fileId, totalSize)
        if (!AccountSession.isCurrent(owner)) return DurationEstimate(0L, DurationSource.None, false)
        if (fromLocal > 0L) {
            rememberTrustedDuration(fileId, fromLocal, DurationSource.LocalFile, owner)
            return DurationEstimate(fromLocal, DurationSource.LocalFile, trusted = true)
        }

        if (!streamUrl.isNullOrBlank()) {
            val fromStream = probeFromRetriever(streamUrl)
            if (!AccountSession.isCurrent(owner)) return DurationEstimate(0L, DurationSource.None, false)
            if (fromStream > 0L && isStreamDurationTrusted(fromStream, totalSize, hintMs)) {
                rememberTrustedDuration(fileId, fromStream, DurationSource.Stream, owner)
                return DurationEstimate(fromStream, DurationSource.Stream, trusted = true)
            }
            if (fromStream > 0L) {
                return DurationEstimate(fromStream, DurationSource.Stream, trusted = false)
            }
        }

        return DurationEstimate(0L, DurationSource.None, trusted = false)
    }

    fun resolveDurationMs(
        fileId: Int,
        totalSize: Long,
        streamUrl: String?,
        hintSeconds: Int = 0
    ): Long {
        val estimate = resolveDurationEstimate(fileId, totalSize, streamUrl, hintSeconds)
        return estimate.durationMs.takeIf { estimate.trusted } ?: 0L
    }

    fun playerDurationIfTrusted(
        fileId: Int,
        durationMs: Long,
        totalSize: Long,
        hintSeconds: Int = 0
    ): Long {
        if (durationMs <= 0L) return cachedDurationMs(fileId)
        val hintMs = hintSeconds.takeIf { it > 0 }?.times(1000L) ?: 0L
        val trusted = when {
            hintMs > 0L -> durationMs >= hintMs - 5_000L
            totalSize in 1..SMALL_STREAM_MAX_BYTES -> durationMs > 0L
            durationMs < SHORT_MOVIE_THRESHOLD_MS -> false
            else -> false
        }
        if (trusted) {
            rememberTrustedDuration(fileId, durationMs, DurationSource.Player)
        }
        return cachedDurationMs(fileId)
    }

    private fun isStreamDurationTrusted(durationMs: Long, totalSize: Long, hintMs: Long): Boolean {
        if (durationMs <= 0L) return false
        if (hintMs > 0L) return durationMs >= hintMs - 5_000L
        if (totalSize <= 0L) return false
        if (totalSize > SMALL_STREAM_MAX_BYTES) return false
        return durationMs > 0L
    }

    private fun durationKey(fileId: Int): String {
        return "$KEY_PREFIX$fileId"
    }

    private fun probeFromRetriever(source: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source, HashMap())
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Could not read stream duration (${e.javaClass.simpleName})")
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }
}
