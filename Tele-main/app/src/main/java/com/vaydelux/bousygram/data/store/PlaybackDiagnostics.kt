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
        val decoder = prefs.getString(KEY_DECODER_NAME, null).orEmpty().ifBlank { "Unknown" }
        val mode = prefs.getString(KEY_BUFFER_MODE, null).orEmpty().ifBlank { EnterprisePreferences.bufferMode().name }
        val error = prefs.getString(KEY_LAST_STREAM_ERROR, null).orEmpty().ifBlank { "None" }
        return "firstFrame=${prefs.getLong(KEY_FIRST_FRAME_MS, 0L)}ms, " +
            "retries=${prefs.getInt(KEY_RETRY_COUNT, 0)}, " +
            "rebuffer=${prefs.getInt(KEY_REBUFFER_COUNT, 0)}, " +
            "decoder=$decoder, droppedFrames=${prefs.getInt(KEY_DROPPED_FRAMES, 0)}, " +
            "bufferMode=$mode, proxy=${prefs.getString(KEY_PROXY_STATUS, "none")}, " +
            "activeChunks=${prefs.getInt(KEY_ACTIVE_CHUNK_COUNT, 0)}, " +
            "chunkRetries=${prefs.getInt(KEY_CHUNK_RETRY_COUNT, 0)}, " +
            "cacheAhead=${prefs.getLong(KEY_CACHE_AHEAD_BYTES, 0L)} bytes, " +
            "lastOffset=${prefs.getLong(KEY_LAST_STALLED_OFFSET, 0L)}, " +
            "readSource=${prefs.getString(KEY_READ_SOURCE, "unknown")}, " +
            "lastStreamError=$error"
    }
}
