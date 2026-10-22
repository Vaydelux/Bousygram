package com.vaydelux.bousygram.core.tdlib.server

import android.util.Log
import com.vaydelux.bousygram.core.observability.CrashReporter
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.data.store.PlaybackDiagnostics
import org.drinkless.tdlib.TdApi

object TelegramLogWrapper {
    private const val TAG = "TelegramLogWrapper"
    private val floodWaitRegex = Regex("""FLOOD_WAIT_(\d+)""")

    @Volatile
    private var configured = false

    fun configure() {
        if (configured) return
        configured = true
        TdLibClient.client.send(
            TdApi.SetLogVerbosityLevel(1),
            { response ->
                if (response is TdApi.Error) {
                    logError("SetLogVerbosityLevel", response)
                }
            }
        )
    }

    fun logError(queryName: String, error: TdApi.Error) {
        val message = error.message.orEmpty()
        val normalized = "$queryName failed: code=${error.code} message=$message"
        Log.w(TAG, normalized)
        PlaybackDiagnostics.recordStreamError(normalized)
        CrashReporter.setKey("tdlib_query", queryName)
        CrashReporter.setKey("tdlib_error_code", error.code)
        CrashReporter.log(normalized)
        floodWaitRegex.find(message)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { waitSeconds ->
            Log.w(TAG, "$queryName hit Telegram FLOOD_WAIT for ${waitSeconds}s")
            CrashReporter.setKey("tdlib_flood_wait_seconds", waitSeconds)
        }
    }
}
