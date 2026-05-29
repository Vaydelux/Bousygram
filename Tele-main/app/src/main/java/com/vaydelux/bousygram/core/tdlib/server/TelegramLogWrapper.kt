package com.vaydelux.bousygram.core.tdlib.server

import android.util.Log
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
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
        Log.w(TAG, "$queryName failed: code=${error.code} message=$message")
        floodWaitRegex.find(message)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { waitSeconds ->
            Log.w(TAG, "$queryName hit Telegram FLOOD_WAIT for ${waitSeconds}s")
        }
    }
}
