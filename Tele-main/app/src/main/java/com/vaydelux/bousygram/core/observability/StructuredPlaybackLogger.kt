package com.vaydelux.bousygram.core.observability

import android.util.Log
import com.vaydelux.bousygram.data.store.PlaybackDiagnostics

data class PlaybackLogContext(
    val fileId: Int = 0,
    val messageId: Long = 0L,
    val chatId: Long = 0L,
    val offset: Long = 0L,
    val decoderName: String = "Unknown",
    val cacheState: String = "Unknown",
    val proxyStatus: String = "Unknown",
    val activeChunks: Int = 0
)

object StructuredPlaybackLogger {
    private const val TAG = "BousygramPlayback"

    fun warn(event: String, message: String, context: PlaybackLogContext = PlaybackLogContext()) {
        val line = format(event, message, context)
        Log.w(TAG, line)
        PlaybackDiagnostics.recordStreamError(line)
        CrashReporter.log(line)
        tagCrashContext(context)
    }

    fun error(event: String, error: Throwable, context: PlaybackLogContext = PlaybackLogContext()) {
        val line = format(event, error.message.orEmpty(), context)
        Log.e(TAG, line, error)
        PlaybackDiagnostics.recordStreamError(line)
        tagCrashContext(context)
        CrashReporter.recordException(error)
    }

    private fun tagCrashContext(context: PlaybackLogContext) {
        CrashReporter.setKey("file_id", context.fileId)
        CrashReporter.setKey("message_id", context.messageId)
        CrashReporter.setKey("chat_id", context.chatId)
        CrashReporter.setKey("chunk_offset", context.offset)
        CrashReporter.setKey("decoder", context.decoderName)
        CrashReporter.setKey("cache_state", context.cacheState)
        CrashReporter.setKey("proxy_status", context.proxyStatus)
        CrashReporter.setKey("active_chunks", context.activeChunks)
    }

    private fun format(event: String, message: String, context: PlaybackLogContext): String =
        "event=$event fileId=${context.fileId} chatId=${context.chatId} messageId=${context.messageId} " +
            "offset=${context.offset} cache=${context.cacheState} proxy=${context.proxyStatus} " +
            "activeChunks=${context.activeChunks} decoder=${context.decoderName} message=${message.take(300)}"
}
