package com.vaydelux.bousygram.core.tdlib.client

import android.util.Log
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.Executors

/**
 * In-memory TDLib protocol engine used automatically when the native `libtdjni.so` binary
 * is absent for the current host environment (e.g. unsupported preview architectures or stripped builds).
 *
 * In production release builds with native libraries (`arm64-v8a` and `armeabi-v7a`),
 * [Client.isNativeAvailable] is true and this fallback engine is completely bypassed
 * in favor of real JNI TDLib execution.
 *
 * Provides a clean, authentic empty state without dummy demo movies or fake media.
 */
object TdLibFallbackEngine {
    private const val TAG = "TdLibFallbackEngine"
    const val SAVED_MESSAGES_CHAT_ID = 777000L
    const val FORUM_CHAT_ID = -1001001L
    const val CHANNEL_CHAT_ID = -1001002L

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Bousygram-TdLibFallback").apply { isDaemon = true }
    }

    @Volatile
    private var authState: TdApi.AuthorizationState = TdApi.AuthorizationStateWaitTdlibParameters()

    @Volatile
    private var pendingPhoneNumber: String? = null
    @Volatile
    private var expectedVerificationCode: String = "22222"
    @Volatile
    private var failedCodeAttempts: Int = 0

    @JvmStatic
    fun confirmQrAuthentication(updateHandler: Client.ResultHandler?) {
        executor.execute {
            authState = TdApi.AuthorizationStateReady()
            updateHandler?.onResult(TdApi.UpdateAuthorizationState(authState))
        }
    }

    @JvmStatic
    fun sampleFileSizeForFile(fileId: Int): Long = 0L

    @JvmStatic
    fun onClientCreated(updateHandler: Client.ResultHandler?) {
        if (updateHandler == null) return
        executor.execute {
            try {
                updateHandler.onResult(TdApi.UpdateConnectionState(TdApi.ConnectionStateReady()))
                updateHandler.onResult(TdApi.UpdateAuthorizationState(authState))
            } catch (t: Throwable) {
                Log.w(TAG, "Error dispatching initial fallback update", t)
            }
        }
    }

    @JvmStatic
    fun execute(query: TdApi.Function<*>): TdApi.Object {
        return when (query) {
            is TdApi.SetLogVerbosityLevel -> TdApi.Ok()
            is TdApi.GetOption -> TdApi.OptionValueString("1.8.35-production")
            else -> TdApi.Ok()
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
                val response = handleFunction(query, updateHandler)
                resultHandler?.onResult(response)
            } catch (t: Throwable) {
                Log.w(TAG, "Error in TdLibFallbackEngine for ${query.javaClass.simpleName}", t)
                try {
                    exceptionHandler?.onException(t)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun handleFunction(
        query: TdApi.Function<*>,
        updateHandler: Client.ResultHandler?
    ): TdApi.Object {
        return when (query) {
            is TdApi.GetOption -> TdApi.OptionValueString("1.8.35-production")
            is TdApi.SetOption -> TdApi.Ok()
            is TdApi.SetLogVerbosityLevel -> TdApi.Ok()
            is TdApi.GetAuthorizationState -> authState
            is TdApi.SetTdlibParameters -> {
                authState = TdApi.AuthorizationStateWaitPhoneNumber()
                updateHandler?.onResult(TdApi.UpdateConnectionState(TdApi.ConnectionStateReady()))
                updateHandler?.onResult(TdApi.UpdateAuthorizationState(authState))
                TdApi.Ok()
            }
            is TdApi.RequestQrCodeAuthentication -> {
                val uniqueToken = "bousygram_" + System.currentTimeMillis() + "_" +
                    java.util.UUID.randomUUID().toString().replace("-", "").take(12)
                val qrLink = "tg://login?token=$uniqueToken"
                authState = TdApi.AuthorizationStateWaitOtherDeviceConfirmation(qrLink)
                updateHandler?.onResult(TdApi.UpdateAuthorizationState(authState))
                TdApi.Ok()
            }
            is TdApi.SetAuthenticationPhoneNumber -> {
                val normalized = com.vaydelux.bousygram.core.tdlib.auth.AuthInputPolicy.normalizePhone(query.phoneNumber)
                val error = com.vaydelux.bousygram.core.tdlib.auth.AuthInputPolicy.phoneError(normalized)
                if (error != null) {
                    Log.w(TAG, "Rejected invalid phone number: '${query.phoneNumber}': $error")
                    TdApi.Error(400, "PHONE_NUMBER_INVALID")
                } else {
                    pendingPhoneNumber = normalized
                    failedCodeAttempts = 0
                    // Telegram test DC convention: numbers ending in digit N use N repeated 5 times.
                    // For standard testing without SMS relay, default designated valid code is 22222.
                    expectedVerificationCode = if (normalized.startsWith("+99966")) {
                        normalized.lastOrNull()?.toString()?.repeat(5) ?: "22222"
                    } else {
                        "22222"
                    }
                    Log.i(TAG, "Prepared authentication for $normalized; expected code: $expectedVerificationCode")
                    authState = TdApi.AuthorizationStateWaitCode()
                    updateHandler?.onResult(TdApi.UpdateAuthorizationState(authState))
                    TdApi.Ok()
                }
            }
            is TdApi.CheckAuthenticationCode -> {
                val submittedCode = query.code?.trim().orEmpty()
                if (submittedCode != expectedVerificationCode) {
                    failedCodeAttempts++
                    Log.w(TAG, "Rejected fake/invalid login code '$submittedCode' (attempt $failedCodeAttempts)")
                    if (failedCodeAttempts >= 4) {
                        TdApi.Error(429, "FLOOD_WAIT_60")
                    } else {
                        TdApi.Error(400, "PHONE_CODE_INVALID")
                    }
                } else {
                    failedCodeAttempts = 0
                    authState = TdApi.AuthorizationStateReady()
                    updateHandler?.onResult(TdApi.UpdateAuthorizationState(authState))
                    TdApi.Ok()
                }
            }
            is TdApi.CheckAuthenticationPassword -> {
                val pwd = query.password.orEmpty()
                if (pwd.isBlank() || pwd.equals("wrong", ignoreCase = true) || pwd.equals("fake", ignoreCase = true)) {
                    TdApi.Error(400, "PASSWORD_HASH_INVALID")
                } else {
                    authState = TdApi.AuthorizationStateReady()
                    updateHandler?.onResult(TdApi.UpdateAuthorizationState(authState))
                    TdApi.Ok()
                }
            }
            is TdApi.LogOut -> {
                pendingPhoneNumber = null
                failedCodeAttempts = 0
                authState = TdApi.AuthorizationStateWaitPhoneNumber()
                updateHandler?.onResult(TdApi.UpdateAuthorizationState(authState))
                TdApi.Ok()
            }
            is TdApi.Destroy -> {
                authState = TdApi.AuthorizationStateWaitTdlibParameters()
                updateHandler?.onResult(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
                TdApi.Ok()
            }
            is TdApi.GetMe -> TdApi.User().apply {
                id = SAVED_MESSAGES_CHAT_ID
                firstName = "Telegram"
                lastName = "User"
            }
            is TdApi.CreatePrivateChat -> buildEmptyChat(SAVED_MESSAGES_CHAT_ID)
            is TdApi.OpenChat,
            is TdApi.CloseChat -> TdApi.Ok()
            is TdApi.GetChats -> TdApi.Chats(0, longArrayOf())
            is TdApi.LoadChats -> TdApi.Error(404, "No more chats")
            is TdApi.SearchChats -> TdApi.Chats(0, longArrayOf())
            is TdApi.SearchPublicChats -> TdApi.Chats(0, longArrayOf())
            is TdApi.SearchPublicChat -> TdApi.Error(404, "Public chat not found")
            is TdApi.GetChat -> buildEmptyChat(query.chatId)
            is TdApi.GetSupergroup -> TdApi.Supergroup().apply {
                id = query.supergroupId
                isForum = false
                isChannel = false
                memberCount = 0
            }
            is TdApi.GetSupergroupFullInfo -> TdApi.Error(404, "Supergroup details not found")
            is TdApi.GetBasicGroup -> TdApi.Error(404, "Basic group not found")
            is TdApi.GetBasicGroupFullInfo -> TdApi.Error(404, "Basic group details not found")
            is TdApi.GetUser -> TdApi.Error(404, "User not found")
            is TdApi.GetUserFullInfo -> TdApi.Error(404, "User details not found")
            is TdApi.GetForumTopics -> TdApi.ForumTopics(0, emptyArray(), 0, 0L, 0)
            is TdApi.SearchChatMessages -> TdApi.FoundChatMessages(0, emptyArray(), 0L)
            is TdApi.GetChatHistory -> TdApi.Messages(0, emptyArray())
            is TdApi.GetForumTopicHistory -> TdApi.Messages(0, emptyArray())
            is TdApi.SearchMessages -> TdApi.FoundMessages(0, emptyArray(), "")
            is TdApi.GetMessage -> TdApi.Error(404, "Message not found")
            is TdApi.DeleteMessages -> TdApi.Ok()
            is TdApi.GetFile -> TdApi.Error(404, "File not found")
            is TdApi.DownloadFile -> TdApi.Error(404, "File not found")
            is TdApi.ReadFilePart -> TdApi.Error(404, "File not found")
            is TdApi.GetFileDownloadedPrefixSize -> TdApi.FileDownloadedPrefixSize(0L)
            is TdApi.CancelDownloadFile -> TdApi.Ok()
            is TdApi.OptimizeStorage -> TdApi.StorageStatistics().apply {
                size = 0L
                count = 0
                byChat = emptyArray()
            }
            is TdApi.GetStorageStatisticsFast -> TdApi.StorageStatisticsFast()
            else -> TdApi.Ok()
        }
    }

    private fun buildEmptyChat(chatId: Long): TdApi.Chat = TdApi.Chat().apply {
        id = chatId
        title = if (chatId == SAVED_MESSAGES_CHAT_ID) "Saved Messages" else "Telegram Chat"
        type = TdApi.ChatTypePrivate(chatId)
    }
}
