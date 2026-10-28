package com.vaydelux.bousygram.domain.model

import android.graphics.Bitmap

enum class AuthStep {
    Loading,
    Unavailable,
    EnterApiKeys,
    ScanQrCode,
    EnterPhone,
    EnterCode,
    EnterPassword,
    Authorized
}

enum class AuthMode {
    QR,
    Phone
}

data class AuthUiState(
    val apiId: String = "",
    val apiHash: String = "",
    val phone: String = "",
    val code: String = "",
    val password: String = "",
    val step: AuthStep = AuthStep.Loading,
    val preferredMode: AuthMode = AuthMode.QR,
    val message: String? = null,
    val isLoading: Boolean = false,
    val qrCodeLink: String? = null,
    val qrCodeBitmap: Bitmap? = null,
    val connectionStatus: String? = null
)

