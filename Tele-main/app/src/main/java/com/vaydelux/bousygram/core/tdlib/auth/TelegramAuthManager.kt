package com.vaydelux.bousygram.core.tdlib.auth

import android.content.Context
import android.os.Build
import android.util.Log
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.data.store.ApiCredentialsStore
import com.vaydelux.bousygram.data.store.TdlibDatabaseKeyStore
import com.vaydelux.bousygram.domain.model.AuthStep
import com.vaydelux.bousygram.domain.model.AuthUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.drinkless.tdlib.TdApi
import java.io.File

class TelegramAuthManager(private val context: Context) {
    private companion object {
        const val TAG = "TelegramAuthManager"
    }
    private val _uiState = MutableStateFlow(AuthUiState(isLoading = true))
    val uiState: StateFlow<AuthUiState> = _uiState

    private val client: org.drinkless.tdlib.Client
        get() = TdLibClient.client
    private val credentialsStore = ApiCredentialsStore(context)
    private val databaseKeyStore = TdlibDatabaseKeyStore(context)
    private var isPhoneLoginForced = true
    @Volatile
    private var qrRequestInFlight = false

    private var updateHandlerRef: ((TdApi.Object?) -> Unit)? = null
    private var errorHandlerRef: ((Throwable?) -> Unit)? = null

    init {
        val updateHandler: (TdApi.Object?) -> Unit = ::handleUpdate
        val errorHandler: (Throwable?) -> Unit = ::handleError
        updateHandlerRef = updateHandler
        errorHandlerRef = errorHandler
        TdLibClient.addUpdateHandler(updateHandler)
        TdLibClient.addErrorHandler(errorHandler)
        client.send(TdApi.GetAuthorizationState()) { result ->
            when (result) {
                is TdApi.AuthorizationState -> handleAuthState(result)
                else -> handleResult(result)
            }
        }
    }

    fun requestQrCodeLogin() {
        isPhoneLoginForced = false
        _uiState.update { it.copy(isLoading = true, message = null, qrCodeLink = null) }
        client.send(TdApi.GetAuthorizationState()) { result ->
            when (result) {
                is TdApi.AuthorizationStateWaitPhoneNumber -> requestQrCodeAuthentication()
                is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> handleAuthState(result)
                is TdApi.AuthorizationStateWaitTdlibParameters -> handleAuthState(result)
                is TdApi.AuthorizationState -> {
                    handleAuthState(result)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = "QR login is not available at this step. Continue with the current sign-in form."
                        )
                    }
                }
                else -> handleResult(result)
            }
        }
    }

    private fun requestQrCodeAuthentication() {
        if (qrRequestInFlight) return
        qrRequestInFlight = true
        _uiState.update {
            it.copy(
                step = AuthStep.ScanQrCode,
                isLoading = true,
                message = null,
                qrCodeLink = null
            )
        }
        client.send(TdApi.RequestQrCodeAuthentication(longArrayOf())) { result ->
            qrRequestInFlight = false
            handleResult(result)
        }
    }

    fun switchToPhoneLogin() {
        isPhoneLoginForced = true
        _uiState.update { it.copy(step = AuthStep.EnterPhone, qrCodeLink = null, isLoading = true, message = null) }
        client.send(TdApi.GetAuthorizationState()) { result ->
            if (result is TdApi.AuthorizationStateWaitOtherDeviceConfirmation) {
                resetPendingAuth()
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun resetPendingAuth() {
        Log.d(TAG, "Resetting pending unauthenticated auth session to return to clean phone login")
        _uiState.update { it.copy(isLoading = true, message = "Preparing phone login…") }
        client.send(TdApi.Destroy()) { result ->
            if (result is TdApi.Error) {
                Log.w(TAG, "Destroy error: ${result.message}")
                _uiState.update { it.copy(isLoading = false, message = result.message) }
            }
        }
    }

    fun submitPhone(phone: String) {
        val trimmed = phone.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(message = "Enter your phone number", isLoading = false) }
            return
        }
        _uiState.update { it.copy(isLoading = true, message = null) }
        client.send(TdApi.GetAuthorizationState()) { state ->
            if (state is TdApi.AuthorizationStateWaitOtherDeviceConfirmation) {
                isPhoneLoginForced = true
                resetPendingAuth()
                _uiState.update {
                    it.copy(
                        message = "Resetting QR session. Please tap submit again in a moment.",
                        isLoading = false
                    )
                }
            } else {
                submitAction(TdApi.SetAuthenticationPhoneNumber(trimmed, null))
            }
        }
    }

    fun submitCode(code: String) = submitAction(TdApi.CheckAuthenticationCode(code))
    fun submitPassword(password: String) = submitAction(TdApi.CheckAuthenticationPassword(password))

    private fun submitAction(request: TdApi.Function<*>) {
        _uiState.update { it.copy(isLoading = true, message = null) }
        client.send(request) { result -> handleResult(result) }
    }

    fun submitApiKeys(apiId: String, apiHash: String) {
        try {
            credentialsStore.save(apiId, apiHash)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to protect Telegram API credentials", e)
            _uiState.update {
                it.copy(message = "Secure credential storage is unavailable on this device", isLoading = false)
            }
            return
        }
        _uiState.update { it.copy(apiId = apiId, apiHash = apiHash, isLoading = true, message = null) }
        client.send(TdApi.GetAuthorizationState()) { result ->
            when (result) {
                is TdApi.AuthorizationState -> handleAuthState(result)
                else -> handleResult(result)
            }
        }
    }

    private fun handleUpdate(update: TdApi.Object?) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> handleAuthState(update.authorizationState)
            is TdApi.UpdateConnectionState -> handleConnectionState(update.state)
        }
    }

    private fun handleConnectionState(state: TdApi.ConnectionState?) {
        Log.d(TAG, "Connection state: ${state?.javaClass?.simpleName}")
        val statusText = when (state) {
            is TdApi.ConnectionStateConnecting -> "Connecting to Telegram secure server…"
            is TdApi.ConnectionStateConnectingToProxy -> "Connecting via proxy…"
            is TdApi.ConnectionStateReady -> "Connected to Telegram secure server"
            is TdApi.ConnectionStateUpdating -> "Updating secure databases…"
            is TdApi.ConnectionStateWaitingForNetwork -> "Waiting for network connection…"
            else -> null
        }
        _uiState.update { it.copy(connectionStatus = statusText) }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState?) {
        Log.d(TAG, "Auth state: ${state?.javaClass?.simpleName}")
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> setTdlibParameters()
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                if (isPhoneLoginForced) {
                    _uiState.update { it.copy(step = AuthStep.EnterPhone, isLoading = false, message = null) }
                } else {
                    requestQrCodeAuthentication()
                }
            }
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                qrRequestInFlight = false
                val link = state.link
                if (isPhoneLoginForced) {
                    resetPendingAuth()
                } else {
                    _uiState.update {
                        it.copy(
                            step = AuthStep.ScanQrCode,
                            qrCodeLink = link,
                            message = null,
                            isLoading = false
                        )
                    }
                }
            }
            is TdApi.AuthorizationStateWaitCode -> {
                _uiState.update { it.copy(step = AuthStep.EnterCode, isLoading = false) }
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                _uiState.update { it.copy(step = AuthStep.EnterPassword, isLoading = false) }
            }
            is TdApi.AuthorizationStateReady -> {
                _uiState.update { it.copy(step = AuthStep.Authorized, isLoading = false) }
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                _uiState.update { it.copy(message = "Logging out…", isLoading = true) }
            }
            is TdApi.AuthorizationStateClosing -> {
                _uiState.update { it.copy(message = "Closing session…", isLoading = true) }
            }
            is TdApi.AuthorizationStateClosed -> {
                Log.d(TAG, "AuthorizationStateClosed received; re-initializing client")
                _uiState.update { it.copy(message = "Reconnecting…", isLoading = true) }
                val newClient = TdLibClient.restartClient()
                newClient.send(TdApi.GetAuthorizationState()) { res ->
                    when (res) {
                        is TdApi.AuthorizationState -> handleAuthState(res)
                        else -> handleResult(res)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun handleResult(result: TdApi.Object?) {
        if (result is TdApi.Error) {
            Log.e(TAG, "Auth error: ${result.code} ${result.message}")
            _uiState.update { state ->
                state.copy(message = result.message, isLoading = false)
            }
        }
    }

    private fun handleError(t: Throwable?) {
        Log.e(TAG, "TDLib error", t)
        _uiState.update { state ->
            state.copy(message = t?.message ?: "TDLib error", isLoading = false)
        }
    }

    private fun setTdlibParameters() {
        val storedApiId = credentialsStore.getApiId()
        val storedApiHash = credentialsStore.getApiHash()
        val buildApiId = BuildConfig.TELEGRAM_API_ID.toIntOrNull() ?: 0
        val buildApiHash = BuildConfig.TELEGRAM_API_HASH
        val apiId = when {
            !storedApiId.isNullOrBlank() -> storedApiId.toIntOrNull() ?: 0
            else -> buildApiId
        }
        val apiHash = if (!storedApiHash.isNullOrBlank()) storedApiHash else buildApiHash
        if (apiId == 0 || apiHash.isBlank()) {
            _uiState.update {
                it.copy(
                    step = AuthStep.EnterApiKeys,
                    apiId = storedApiId.orEmpty(),
                    apiHash = storedApiHash.orEmpty(),
                    message = "Enter your Telegram API ID and Hash",
                    isLoading = false
                )
            }
            return
        }
        val databaseDirectory = File(context.filesDir, "tdlib")
        val databaseDir = databaseDirectory.absolutePath
        val filesDir = File(context.filesDir, "tdlib_files").absolutePath
        val databaseEncryptionKey = try {
            databaseKeyStore.getOrCreateKey(databaseDirectory)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to unlock the protected TDLib database key", e)
            _uiState.update {
                it.copy(
                    message = "Secure Telegram database storage could not be unlocked. Your existing local data was not changed.",
                    isLoading = false
                )
            }
            return
        }

        val parameters = TdApi.SetTdlibParameters(
            false,
            databaseDir,
            filesDir,
            databaseEncryptionKey,
            true,
            true,
            true,
            false,
            apiId,
            apiHash,
            "en",
            "Android TV",
            Build.VERSION.RELEASE ?: "Android",
            BuildConfig.VERSION_NAME
        )
        client.send(parameters) { result ->
            handleResult(result)
        }
    }

    fun cleanup() {
        updateHandlerRef?.let { TdLibClient.removeUpdateHandler(it) }
        errorHandlerRef?.let { TdLibClient.removeErrorHandler(it) }
        updateHandlerRef = null
        errorHandlerRef = null
    }
}
