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
    val readSource: String
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
            "retries=${snapshot.retryCount}, " +
            "rebuffer=${snapshot.rebufferCount}, " +
            "decoder=${snapshot.decoderName}, droppedFrames=${snapshot.droppedFrames}, " +
            "bufferMode=${snapshot.bufferMode}, proxy=${snapshot.proxyStatus}, " +
            "activeChunks=${snapshot.activeChunkCount}, " +
            "chunkRetries=${snapshot.chunkRetryCount}, " +
            "cacheAhead=${snapshot.cacheAheadBytes} bytes, " +
            "lastOffset=${snapshot.lastStalledOffset}, " +
            "readSource=${snapshot.readSource}, " +
            "lastStreamError=${snapshot.lastStreamError}"
    }

    fun snapshot(): PlaybackDiagnosticsSnapshot =
        PlaybackDiagnosticsSnapshot(
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
            readSource = prefs.getString(KEY_READ_SOURCE, "unknown").orEmpty()
        )
}
