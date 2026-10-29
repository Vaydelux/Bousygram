package com.vaydelux.bousygram.presentation.auth.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vaydelux.bousygram.core.tdlib.auth.AuthInputPolicy
import com.vaydelux.bousygram.core.tdlib.auth.TelegramAuthManager
import com.vaydelux.bousygram.core.tdlib.utils.QrCodeGenerator
import com.vaydelux.bousygram.domain.model.AuthStep
import com.vaydelux.bousygram.domain.model.AuthUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val authManager = TelegramAuthManager(application)
    private val _uiState = MutableStateFlow(AuthUiState(isLoading = true))
    val uiState: StateFlow<AuthUiState> = _uiState
    private var lastManagerMessage: String? = null

    init {
        viewModelScope.launch {
            authManager.uiState.collect { state ->
                val qrBitmap = qrBitmapFor(state.qrCodeLink, _uiState.value.qrCodeLink, _uiState.value.qrCodeBitmap)
                val messageChanged = state.message != lastManagerMessage
                lastManagerMessage = state.message
                _uiState.update { current ->
                    val showingKeys = state.step == AuthStep.EnterApiKeys && current.step != AuthStep.EnterApiKeys
                    current.copy(
                        step = state.step,
                        apiId = adoptedField(current.apiId, state.apiId, showingKeys),
                        apiHash = adoptedField(current.apiHash, state.apiHash, showingKeys),
                        phone = adoptedPhone(current.phone, state.phone),
                        code = if (state.step == AuthStep.EnterCode && current.step == AuthStep.EnterCode) current.code else "",
                        password = if (state.step == AuthStep.EnterPassword && current.step == AuthStep.EnterPassword) {
                            current.password
                        } else {
                            ""
                        },
                        preferredMode = state.preferredMode,
                        message = if (messageChanged) state.message else current.message,
                        isLoading = state.isLoading,
                        qrCodeLink = state.qrCodeLink,
                        qrCodeBitmap = qrBitmap,
                        connectionStatus = state.connectionStatus
                    )
                }
            }
        }
    }

    fun selectMode(mode: com.vaydelux.bousygram.domain.model.AuthMode) {
        if (mode == com.vaydelux.bousygram.domain.model.AuthMode.QR) {
            requestQrCodeLogin()
        } else {
            switchToPhoneLogin()
        }
    }

    fun logout() {
        authManager.logout()
    }

    fun retryInitialization() = authManager.retryInitialization()

    fun switchToPhoneLogin() {
        authManager.switchToPhoneLogin()
    }

    fun requestQrCodeLogin() {
        authManager.requestQrCodeLogin()
    }

    fun onPhoneChange(value: String) = updateField { it.copy(phone = value.take(24)) }
    fun onApiIdChange(value: String) = updateField { it.copy(apiId = value.filter(Char::isDigit).take(12)) }
    fun onApiHashChange(value: String) = updateField {
        it.copy(apiHash = value.filter(::isHashCharacter).take(32))
    }
    fun onCodeChange(value: String) = updateField { it.copy(code = value.filter(Char::isDigit).take(8)) }
    fun onPasswordChange(value: String) = updateField { it.copy(password = value.take(256)) }

    private fun updateField(transform: (AuthUiState) -> AuthUiState) {
        _uiState.update { transform(it).copy(message = null) }
    }

    fun submitPhone() {
        val phone = _uiState.value.phone
        val validation = AuthInputPolicy.phoneError(phone)
        if (validation != null) {
            _uiState.update { it.copy(message = validation) }
            return
        }
        _uiState.update { it.copy(message = null, phone = AuthInputPolicy.normalizePhone(phone)) }
        authManager.submitPhone(phone)
    }

    fun submitApiKeys() {
        val apiId = _uiState.value.apiId.trim()
        val apiHash = _uiState.value.apiHash.trim()
        val validation = AuthInputPolicy.apiCredentialsError(apiId, apiHash)
        if (validation != null) {
            _uiState.update { it.copy(message = validation) }
            return
        }
        _uiState.update { it.copy(message = null, apiId = apiId, apiHash = apiHash) }
        authManager.submitApiKeys(apiId, apiHash)
    }

    fun submitCode() {
        val code = _uiState.value.code.trim()
        val validation = AuthInputPolicy.codeError(code)
        if (validation != null) {
            _uiState.update { it.copy(message = validation) }
            return
        }
        _uiState.update { it.copy(message = null, code = code) }
        authManager.submitCode(code)
    }

    fun submitPassword() {
        val password = _uiState.value.password
        if (password.isBlank()) {
            _uiState.update { it.copy(message = "Enter your 2FA password") }
            return
        }
        _uiState.update { it.copy(message = null) }
        authManager.submitPassword(password)
    }

    override fun onCleared() {
        authManager.cleanup()
        super.onCleared()
    }

    private suspend fun qrBitmapFor(
        link: String?,
        currentLink: String?,
        currentBitmap: android.graphics.Bitmap?
    ): android.graphics.Bitmap? {
        if (link.isNullOrBlank()) return null
        if (link == currentLink && currentBitmap != null) return currentBitmap
        return withContext(Dispatchers.Default) {
            QrCodeGenerator.generate(link, 400, 400)
        }
    }

    private fun adoptedField(current: String, incoming: String, replace: Boolean): String {
        return when {
            current.isBlank() -> incoming
            replace && incoming.isNotBlank() -> incoming
            else -> current
        }
    }

    private fun adoptedPhone(current: String, incoming: String): String {
        if (incoming.isBlank()) return current
        val normalizedCurrent = AuthInputPolicy.normalizePhone(current)
        return when {
            current.isBlank() -> incoming
            incoming == normalizedCurrent -> incoming
            else -> current
        }
    }

    private fun isHashCharacter(character: Char): Boolean {
        return character.isDigit() || character in 'a'..'f' || character in 'A'..'F'
    }
}
