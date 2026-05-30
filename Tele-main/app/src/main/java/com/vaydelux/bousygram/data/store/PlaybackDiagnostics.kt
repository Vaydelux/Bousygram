package com.vaydelux.bousygram.data.store

import android.content.Context
import androidx.core.content.edit
import com.vaydelux.bousygram.app.BousygramApp

internal data class StreamMetrics(
    val firstFrameMs: Long,
    val retryCount: Int,
    val rebufferCount: Int,
    val decoderName: String,
    val droppedFrames: Int,
    val bufferMode: BufferMode
)

internal data class PlaybackDiagnosticsSnapshot(
    val sessionId: String,
    val activeFileId: Int,
    val firstFrameMs: Long,
    val retryCount: Int,
    val rebufferCount: Int,
    val decoderName: String,
    val droppedFrames: Int,
    val bufferMode: String,
    val lastStreamError: String,
    val lastStalledOffset: Long,
    val chunkRetryCount: Int,
    val cacheAheadBytes: Long,
    val activeChunkCount: Int,
    val proxyStatus: String,
    val readSource: String,
    val stallReason: String,
    val cacheStopReason: String,
    val currentOffset: Long
)

internal object PlaybackDiagnostics {
    private const val PREFS_NAME = "playback_diagnostics"
    private const val KEY_FIRST_FRAME_MS = "first_frame_ms"
    private const val KEY_RETRY_COUNT = "retry_count"
    private const val KEY_REBUFFER_COUNT = "rebuffer_count"
    private const val KEY_DECODER_NAME = "decoder_name"
    private const val KEY_DROPPED_FRAMES = "dropped_frames"
    private const val KEY_BUFFER_MODE = "buffer_mode"
    private const val KEY_LAST_STREAM_ERROR = "last_stream_error"
    private const val KEY_LAST_STALLED_OFFSET = "last_stalled_offset"
    private const val KEY_CHUNK_RETRY_COUNT = "chunk_retry_count"
    private const val KEY_CACHE_AHEAD_BYTES = "cache_ahead_bytes"
    private const val KEY_ACTIVE_CHUNK_COUNT = "active_chunk_count"
    private const val KEY_PROXY_STATUS = "proxy_status"
    private const val KEY_READ_SOURCE = "read_source"
    private const val KEY_SESSION_ID = "session_id"
    private const val KEY_ACTIVE_FILE_ID = "active_file_id"
    private const val KEY_STALL_REASON = "stall_reason"
    private const val KEY_CACHE_STOP_REASON = "cache_stop_reason"
    private const val KEY_CURRENT_OFFSET = "current_offset"

    private val prefs by lazy {
        BousygramApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun recordPlayerMetrics(metrics: StreamMetrics) {
        prefs.edit {
            putLong(KEY_FIRST_FRAME_MS, metrics.firstFrameMs)
            putInt(KEY_RETRY_COUNT, metrics.retryCount)
            putInt(KEY_REBUFFER_COUNT, metrics.rebufferCount)
            putString(KEY_DECODER_NAME, metrics.decoderName.take(120))
            putInt(KEY_DROPPED_FRAMES, metrics.droppedFrames)
            putString(KEY_BUFFER_MODE, metrics.bufferMode.name)
        }
    }

    fun recordStreamError(message: String) {
        prefs.edit { putString(KEY_LAST_STREAM_ERROR, message.take(400)) }
    }

    fun recordSession(
        sessionId: String,
        activeFileId: Int,
        currentOffset: Long,
        retryCount: Int,
        stallReason: String,
        cacheStopReason: String
    ) {
        prefs.edit {
            putString(KEY_SESSION_ID, sessionId.take(80))
            putInt(KEY_ACTIVE_FILE_ID, activeFileId)
            putLong(KEY_CURRENT_OFFSET, currentOffset)
            putInt(KEY_RETRY_COUNT, retryCount)
            putString(KEY_STALL_REASON, stallReason.take(80))
            putString(KEY_CACHE_STOP_REASON, cacheStopReason.take(80))
        }
    }

    fun recordProxyState(
        lastStalledOffset: Long,
        chunkRetryCount: Int,
        cacheAheadBytes: Long,
        activeChunkCount: Int,
        proxyStatus: String,
        readSource: String
    ) {
        prefs.edit {
            putLong(KEY_LAST_STALLED_OFFSET, lastStalledOffset)
            putInt(KEY_CHUNK_RETRY_COUNT, chunkRetryCount)
            putLong(KEY_CACHE_AHEAD_BYTES, cacheAheadBytes)
            putInt(KEY_ACTIVE_CHUNK_COUNT, activeChunkCount)
            putString(KEY_PROXY_STATUS, proxyStatus.take(80))
            putString(KEY_READ_SOURCE, readSource.take(80))
        }
    }

    fun summary(): String {
        val snapshot = snapshot()
        return "firstFrame=${snapshot.firstFrameMs}ms, " +
            "session=${snapshot.sessionId}, fileId=${snapshot.activeFileId}, " +
            "retries=${snapshot.retryCount}, " +
            "rebuffer=${snapshot.rebufferCount}, " +
            "decoder=${snapshot.decoderName}, droppedFrames=${snapshot.droppedFrames}, " +
            "bufferMode=${snapshot.bufferMode}, proxy=${snapshot.proxyStatus}, " +
            "activeChunks=${snapshot.activeChunkCount}, " +
            "chunkRetries=${snapshot.chunkRetryCount}, " +
            "cacheAhead=${snapshot.cacheAheadBytes} bytes, " +
            "currentOffset=${snapshot.currentOffset}, lastOffset=${snapshot.lastStalledOffset}, " +
            "stallReason=${snapshot.stallReason}, cacheStop=${snapshot.cacheStopReason}, " +
            "readSource=${snapshot.readSource}, " +
            "lastStreamError=${snapshot.lastStreamError}"
    }

    fun sessionReport(): String {
        val snapshot = snapshot()
        return """
            Bousygram playback session report
            sessionId=${snapshot.sessionId}
            activeFileId=${snapshot.activeFileId}
            currentOffset=${snapshot.currentOffset}
            firstFrameMs=${snapshot.firstFrameMs}
            retryCount=${snapshot.retryCount}
            rebufferCount=${snapshot.rebufferCount}
            droppedFrames=${snapshot.droppedFrames}
            decoder=${snapshot.decoderName}
            bufferMode=${snapshot.bufferMode}
            proxyStatus=${snapshot.proxyStatus}
            activeChunks=${snapshot.activeChunkCount}
            chunkRetryCount=${snapshot.chunkRetryCount}
            cacheAheadBytes=${snapshot.cacheAheadBytes}
            lastStalledOffset=${snapshot.lastStalledOffset}
            stallReason=${snapshot.stallReason}
            cacheStopReason=${snapshot.cacheStopReason}
            readSource=${snapshot.readSource}
            lastStreamError=${snapshot.lastStreamError}
        """.trimIndent()
    }

    fun snapshot(): PlaybackDiagnosticsSnapshot =
        PlaybackDiagnosticsSnapshot(
            sessionId = prefs.getString(KEY_SESSION_ID, null).orEmpty().ifBlank { "none" },
            activeFileId = prefs.getInt(KEY_ACTIVE_FILE_ID, 0),
            firstFrameMs = prefs.getLong(KEY_FIRST_FRAME_MS, 0L),
            retryCount = prefs.getInt(KEY_RETRY_COUNT, 0),
            rebufferCount = prefs.getInt(KEY_REBUFFER_COUNT, 0),
            decoderName = prefs.getString(KEY_DECODER_NAME, null).orEmpty().ifBlank { "Unknown" },
            droppedFrames = prefs.getInt(KEY_DROPPED_FRAMES, 0),
            bufferMode = prefs.getString(KEY_BUFFER_MODE, null).orEmpty().ifBlank {
                EnterprisePreferences.bufferMode().name
            },
            lastStreamError = prefs.getString(KEY_LAST_STREAM_ERROR, null).orEmpty().ifBlank { "None" },
            lastStalledOffset = prefs.getLong(KEY_LAST_STALLED_OFFSET, 0L),
            chunkRetryCount = prefs.getInt(KEY_CHUNK_RETRY_COUNT, 0),
            cacheAheadBytes = prefs.getLong(KEY_CACHE_AHEAD_BYTES, 0L),
            activeChunkCount = prefs.getInt(KEY_ACTIVE_CHUNK_COUNT, 0),
            proxyStatus = prefs.getString(KEY_PROXY_STATUS, "none").orEmpty(),
            readSource = prefs.getString(KEY_READ_SOURCE, "unknown").orEmpty(),
            stallReason = prefs.getString(KEY_STALL_REASON, "none").orEmpty(),
            cacheStopReason = prefs.getString(KEY_CACHE_STOP_REASON, "none").orEmpty(),
            currentOffset = prefs.getLong(KEY_CURRENT_OFFSET, 0L)
        )
}
