package com.vaydelux.bousygram.core.tdlib.client

import android.util.Log
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.Executors

/**
 * Fail-closed bridge used only when the native TDLib runtime cannot be loaded.
 *
 * This object deliberately does not emulate Telegram. It never creates an account,
 * accepts authentication input, invents chats, or reports an unperformed mutation
 * as successful. Production functionality requires the native TDLib library.
 */
object TdLibFallbackEngine {
    private const val TAG = "TdLibFallbackEngine"
    const val NATIVE_UNAVAILABLE_CODE = 503
    const val NATIVE_UNAVAILABLE_MESSAGE = "TDLIB_NATIVE_UNAVAILABLE"

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Bousygram-TdLibUnavailable").apply { isDaemon = true }
    }

    private fun unavailable(): TdApi.Error = TdApi.Error(
        NATIVE_UNAVAILABLE_CODE,
        NATIVE_UNAVAILABLE_MESSAGE
    )

    @JvmStatic
    fun onClientCreated(updateHandler: Client.ResultHandler?) {
        if (updateHandler == null) return
        executor.execute {
            try {
                updateHandler.onResult(TdApi.UpdateConnectionState(TdApi.ConnectionStateWaitingForNetwork()))
            } catch (t: Throwable) {
                Log.w(TAG, "Error dispatching native-unavailable update", t)
            }
        }
    }

    @JvmStatic
    fun execute(query: TdApi.Function<*>): TdApi.Object {
        // These are required by the Java client/bootstrap diagnostics and do not
        // claim that Telegram account or data operations succeeded.
        return when (query) {
            is TdApi.SetLogVerbosityLevel -> TdApi.Ok()
            is TdApi.GetOption -> TdApi.OptionValueString("native-unavailable")
            else -> unavailable()
        }
    }

    @JvmStatic
    fun send(
        query: TdApi.Function<*>,
        resultHandler: Client.ResultHandler?,
        exceptionHandler: Client.ExceptionHandler?,
        updateHandler: Client.ResultHandler?
    ) {
        executor.execute {
            try {
                resultHandler?.onResult(unavailable())
            } catch (t: Throwable) {
                Log.w(TAG, "Error reporting native-unavailable result for ${query.javaClass.simpleName}", t)
                try {
                    exceptionHandler?.onException(t)
                } catch (_: Throwable) {
                }
            }
        }
    }
}
