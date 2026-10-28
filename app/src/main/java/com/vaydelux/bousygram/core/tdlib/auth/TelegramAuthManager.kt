package com.vaydelux.bousygram.core.tdlib.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.data.store.ApiCredentialsStore
import com.vaydelux.bousygram.data.store.TdlibDatabaseKeyStore
import com.vaydelux.bousygram.domain.model.AuthMode
import com.vaydelux.bousygram.domain.model.AuthStep
import com.vaydelux.bousygram.domain.model.AuthUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.drinkless.tdlib.TdApi
import org.drinkless.tdlib.Client
import java.io.File

class TelegramAuthManager(private val context: Context) {
    companion object {
        private const val TAG = "TelegramAuthManager"

        /**
         * Standard public Telegram application client credentials.
         * Used for zero-config onboarding on TV and mobile so users are never forced
         * to register developer apps on my.telegram.org just to log in.
         */
        const val DEFAULT_API_ID = 2040
        const val DEFAULT_API_HASH = "b18441a1ff607e10a989891a5462e627"
    }
    private val isTelevision: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private val _uiState = MutableStateFlow(
        AuthUiState(
            isLoading = true,
            preferredMode = if (isTelevision) AuthMode.QR else AuthMode.Phone
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState

    private val client: org.drinkless.tdlib.Client
        get() = TdLibClient.client
    private val credentialsStore = ApiCredentialsStore(context)
    private val databaseKeyStore = TdlibDatabaseKeyStore(context)
    private val authLock = Any()
    private var isPhoneLoginForced = !isTelevision
    @Volatile
    private var qrRequestInFlight = false
    @Volatile
    private var authSubmitInFlight = false
    @Volatile
    private var tdlibParametersSent = false
    @Volatile
    private var destroyInFlight = false
    @Volatile
    private var pendingPhone: String? = null

    private var updateHandlerRef: ((TdApi.Object?) -> Unit)? = null
    private var errorHandlerRef: ((Throwable?) -> Unit)? = null

    init {
        val updateHandler: (TdApi.Object?) -> Unit = ::handleUpdate
        val errorHandler: (Throwable?) -> Unit = ::handleError
        updateHandlerRef = updateHandler
        errorHandlerRef = errorHandler
        TdLibClient.addUpdateHandler(updateHandler)
        TdLibClient.addErrorHandler(errorHandler)
        if (!Client.isNativeAvailable()) {
            _uiState.value = AuthUiState(
                step = AuthStep.Unavailable,
                isLoading = false,
                preferredMode = if (isTelevision) AuthMode.QR else AuthMode.Phone,
                message = "Native TDLib is unavailable. Install a build containing libtdjni.so for this device ABI."
            )
        } else {
            client.send(TdApi.GetAuthorizationState()) { result ->
                when (result) {
                    is TdApi.AuthorizationState -> handleAuthState(result)
                    else -> handleResult(result)
                }
            }
        }
    }

    fun requestQrCodeLogin() {
        isPhoneLoginForced = false
        _uiState.update {
            it.copy(
                preferredMode = AuthMode.QR,
                step = AuthStep.ScanQrCode,
                isLoading = true,
                message = null
            )
        }
        client.send(TdApi.GetAuthorizationState()) { result ->
            when (result) {
                is TdApi.AuthorizationStateWaitPhoneNumber,
                is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                    requestQrCodeAuthentication()
                }
                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                    handleAuthState(result)
                }
                is TdApi.AuthorizationStateWaitCode,
                is TdApi.AuthorizationStateWaitPassword -> {
                    // User was midway through phone login and requested QR pairing:
                    // Reset pending unauthenticated session to return to clean QR pairing
                    resetPendingAuth()
                }
                is TdApi.AuthorizationStateReady -> {
                    handleAuthState(result)
                }
                is TdApi.AuthorizationState -> {
                    handleAuthState(result)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = "QR login is currently unavailable. Please continue with phone login."
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
                preferredMode = AuthMode.QR,
                isLoading = true,
                message = null
            )
        }
        client.send(TdApi.RequestQrCodeAuthentication(longArrayOf())) { result ->
            qrRequestInFlight = false
            if (result is TdApi.Error) {
                Log.e(TAG, "RequestQrCodeAuthentication error: ${result.code} ${result.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = AuthInputPolicy.friendlyMessage(result.message)
                    )
                }
            } else {
                // Query current auth state to ensure link is bound
                client.send(TdApi.GetAuthorizationState()) { stateResult ->
                    if (stateResult is TdApi.AuthorizationStateWaitOtherDeviceConfirmation) {
                        handleAuthState(stateResult)
                    }
                }
            }
        }
    }

    fun switchToPhoneLogin() {
        isPhoneLoginForced = true
        _uiState.update {
            it.copy(
                step = AuthStep.EnterPhone,
                preferredMode = AuthMode.Phone,
                qrCodeLink = null,
                isLoading = true,
                message = null
            )
        }
        client.send(TdApi.GetAuthorizationState()) { result ->
            when (result) {
                is TdApi.AuthorizationStateWaitOtherDeviceConfirmation,
                is TdApi.AuthorizationStateWaitCode,
                is TdApi.AuthorizationStateWaitPassword -> {
                    resetPendingAuth()
                }
                is TdApi.AuthorizationStateWaitPhoneNumber -> {
                    _uiState.update { it.copy(step = AuthStep.EnterPhone, isLoading = false) }
                }
                else -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private fun removedPreviewAuthentication() {
        if (!org.drinkless.tdlib.Client.isNativeAvailable()) {
            _uiState.update { it.copy(isLoading = true, message = "Authorizing preview session…") }
            _uiState.update { it.copy(isLoading = false, message = "Native TDLib is unavailable.") }
        }
    }

    fun resetPendingAuth() {
        if (destroyInFlight) return
        destroyInFlight = true
        tdlibParametersSent = false
        Log.d(TAG, "Resetting pending unauthenticated auth session to return to clean phone login")
        _uiState.update {
            it.copy(isLoading = true, message = "Preparing phone login…", qrCodeLink = null, code = "", password = "")
        }
        client.send(TdApi.Destroy()) { result ->
            if (result is TdApi.Error) {
                destroyInFlight = false
                Log.w(TAG, "Destroy error: ${result.message}")
                _uiState.update { it.copy(isLoading = false, message = AuthInputPolicy.friendlyMessage(result.message)) }
            }
        }
    }

    fun submitPhone(phone: String) {
        val trimmed = AuthInputPolicy.normalizePhone(phone)
        val validation = AuthInputPolicy.phoneError(phone)
        if (validation != null) {
            _uiState.update { it.copy(message = validation, isLoading = false) }
            return
        }
        _uiState.update { it.copy(phone = trimmed, isLoading = true, message = null) }
        client.send(TdApi.GetAuthorizationState()) { state ->
            if (state is TdApi.AuthorizationStateWaitOtherDeviceConfirmation) {
                isPhoneLoginForced = true
                pendingPhone = trimmed
                resetPendingAuth()
            } else {
                pendingPhone = trimmed
                flushPendingPhone()
            }
        }
    }

    fun submitCode(code: String) {
        val trimmed = code.trim()
        val validation = AuthInputPolicy.codeError(trimmed)
        if (validation != null) {
            _uiState.update { it.copy(message = validation, isLoading = false) }
            return
        }
        submitAction(TdApi.CheckAuthenticationCode(trimmed))
    }

    fun submitPassword(password: String) {
        if (password.isBlank()) {
            _uiState.update { it.copy(message = "Enter your 2FA password", isLoading = false) }
            return
        }
        submitAction(TdApi.CheckAuthenticationPassword(password))
    }

    private fun submitAction(request: TdApi.Function<*>): Boolean {
        val started = synchronized(authLock) {
            if (authSubmitInFlight) {
                false
            } else {
                authSubmitInFlight = true
                true
            }
        }
        if (!started) {
            _uiState.update {
                it.copy(message = "Still contacting Telegram. Wait a moment, then try again.")
            }
            return false
        }
        _uiState.update { it.copy(isLoading = true, message = null) }
        client.send(request) { result ->
            synchronized(authLock) { authSubmitInFlight = false }
            handleResult(result)
            flushPendingPhone()
        }
        return true
    }

    private fun flushPendingPhone() {
        val queued = pendingPhone ?: return
        pendingPhone = null
        if (!submitAction(TdApi.SetAuthenticationPhoneNumber(queued, null))) {
            pendingPhone = queued
        }
    }

    fun submitApiKeys(apiId: String, apiHash: String) {
        val cleanId = apiId.trim()
        val cleanHash = apiHash.trim()
        val validation = AuthInputPolicy.apiCredentialsError(cleanId, cleanHash)
        if (validation != null) {
            _uiState.update { it.copy(message = validation, isLoading = false) }
            return
        }
        try {
            credentialsStore.save(cleanId, cleanHash)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to protect Telegram API credentials", e)
            _uiState.update {
                it.copy(message = "Secure credential storage is unavailable on this device", isLoading = false)
            }
            return
        }
        tdlibParametersSent = false
        _uiState.update { it.copy(apiId = cleanId, apiHash = cleanHash, isLoading = true, message = null) }
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
            is TdApi.ConnectionStateWaitingForNetwork -> {
                com.vaydelux.bousygram.core.network.NetworkConnectivityObserver.refresh()
                "Waiting for network connection…"
            }
            else -> null
        }
        _uiState.update { it.copy(connectionStatus = statusText) }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState?) {
        Log.d(TAG, "Auth state: ${state?.javaClass?.simpleName}")
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> setTdlibParameters()
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                if (pendingPhone != null) {
                    flushPendingPhone()
                } else if (isPhoneLoginForced) {
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
                _uiState.update { it.copy(step = AuthStep.EnterCode, isLoading = false, password = "") }
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                _uiState.update { it.copy(step = AuthStep.EnterPassword, isLoading = false, code = "") }
            }
            is TdApi.AuthorizationStateReady -> {
                pendingPhone = null
                synchronized(authLock) { authSubmitInFlight = false }
                _uiState.update {
                    it.copy(step = AuthStep.Authorized, isLoading = false, code = "", password = "", message = null)
                }
                com.vaydelux.bousygram.core.network.NetworkConnectivityObserver.refresh()
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                _uiState.update { it.copy(message = "Logging out…", isLoading = true) }
            }
            is TdApi.AuthorizationStateClosing -> {
                _uiState.update { it.copy(message = "Closing session…", isLoading = true) }
            }
            is TdApi.AuthorizationStateClosed -> {
                Log.d(TAG, "AuthorizationStateClosed received; re-initializing client")
                destroyInFlight = false
                tdlibParametersSent = false
                qrRequestInFlight = false
                synchronized(authLock) { authSubmitInFlight = false }
                _uiState.update { it.copy(message = "Reconnecting…", isLoading = true, qrCodeLink = null) }
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
            if (result.message.contains("parameter", ignoreCase = true)) {
                tdlibParametersSent = false
            }
            _uiState.update { state ->
                state.copy(message = AuthInputPolicy.friendlyMessage(result.message), isLoading = false)
            }
        }
    }

    private fun handleError(t: Throwable?) {
        Log.e(TAG, "TDLib error", t)
        qrRequestInFlight = false
        synchronized(authLock) { authSubmitInFlight = false }
        _uiState.update { state ->
            state.copy(message = t?.message ?: "TDLib error", isLoading = false)
        }
    }

    fun resolveApiCredentials(): Pair<Int, String> {
        val storedApiId = credentialsStore.getApiId()?.toIntOrNull()?.takeIf { it != 0 }
        val storedApiHash = credentialsStore.getApiHash()?.takeIf { it.isNotBlank() }
        val buildApiId = BuildConfig.TELEGRAM_API_ID.toIntOrNull()?.takeIf { it != 0 }
        val buildApiHash = BuildConfig.TELEGRAM_API_HASH.takeIf {
            it.isNotBlank() && it != "TELEGRAM_API_HASH_DEFAULT_VALUE"
        }

        val apiId = storedApiId ?: buildApiId ?: DEFAULT_API_ID
        val apiHash = storedApiHash ?: buildApiHash ?: DEFAULT_API_HASH
        return Pair(apiId, apiHash)
    }

    private fun setTdlibParameters() {
        val (apiId, apiHash) = resolveApiCredentials()
        if (apiId == 0 || apiHash.isBlank()) {
            val storedApiId = credentialsStore.getApiId()
            val storedApiHash = credentialsStore.getApiHash()
            tdlibParametersSent = false
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
        if (tdlibParametersSent) return
        tdlibParametersSent = true
        val databaseDirectory = File(context.filesDir, "tdlib")
        val databaseDir = databaseDirectory.absolutePath
        val filesDir = File(context.filesDir, "tdlib_files").absolutePath
        val databaseEncryptionKey = try {
            databaseKeyStore.getOrCreateKey(databaseDirectory)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to unlock the protected TDLib database key", e)
            tdlibParametersSent = false
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
            deviceModel(),
            Build.VERSION.RELEASE ?: "Android",
            BuildConfig.VERSION_NAME
        )
        client.send(parameters) { result ->
            if (result is TdApi.Error) {
                tdlibParametersSent = false
            }
            handleResult(result)
        }
    }

    private fun deviceModel(): String {
        val television = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return if (television) {
            "Android TV"
        } else {
            Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
        }
    }

    fun logout() {
        Log.i(TAG, "Requesting Telegram session logout")
        client.send(TdApi.LogOut()) { result ->
            if (result is TdApi.Error) {
                Log.w(TAG, "LogOut error: ${result.message}")
            }
        }
    }

    fun cleanup() {
        updateHandlerRef?.let { TdLibClient.removeUpdateHandler(it) }
        errorHandlerRef?.let { TdLibClient.removeErrorHandler(it) }
        updateHandlerRef = null
        errorHandlerRef = null
    }
}
