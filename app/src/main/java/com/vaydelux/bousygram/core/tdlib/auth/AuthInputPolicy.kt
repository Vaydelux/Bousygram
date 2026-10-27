package com.vaydelux.bousygram.core.tdlib.auth

internal object AuthInputPolicy {
    private val PHONE_PATTERN = Regex("^\\+[1-9][0-9]{8,14}$")
    private val CODE_PATTERN = Regex("^[0-9]{5,6}$")
    private val API_HASH_PATTERN = Regex("^[a-fA-F0-9]{32}$")
    private val FLOOD_WAIT = Regex("FLOOD_WAIT_(\\d+)")

    // Known dummy or test phone sequences that must not bypass authentication
    private val DUMMY_PHONE_PATTERNS = listOf(
        Regex("^\\+123456789[0-9]*$"),
        Regex("^\\+0+$"),
        Regex("^\\+1{7,}$"),
        Regex("^\\+9{7,}$"),
        Regex("^\\+0000.*$")
    )

    fun normalizePhone(phone: String): String {
        val stripped = phone.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace(".", "")
        return if (stripped.startsWith("00") && stripped.length > 2) {
            "+" + stripped.drop(2)
        } else {
            stripped
        }
    }

    fun isObviousDummyPhone(phone: String): Boolean {
        val normalized = normalizePhone(phone)
        // Telegram test DC prefixes (+99966...) are valid official test numbers
        if (normalized.startsWith("+99966")) return false
        return DUMMY_PHONE_PATTERNS.any { it.matches(normalized) } ||
            normalized.drop(1).all { it == normalized[1] } // e.g. +2222222222
    }

    fun phoneError(phone: String): String? {
        val normalized = normalizePhone(phone)
        return when {
            normalized.isBlank() -> "Enter your phone number"
            isObviousDummyPhone(normalized) -> "That phone number is not valid. Include the country code, for example +15551234567"
            normalized.matches(PHONE_PATTERN) -> null
            else -> "Use an international number, for example +15551234567"
        }
    }

    fun codeError(code: String): String? {
        val trimmed = code.trim()
        return when {
            trimmed.isBlank() -> "Enter the code from Telegram"
            trimmed.matches(CODE_PATTERN) -> null
            else -> "Enter the 5-digit verification code from Telegram"
        }
    }

    fun apiCredentialsError(apiId: String, apiHash: String): String? {
        val id = apiId.trim()
        val hash = apiHash.trim()
        val parsedId = id.toIntOrNull()
        return when {
            id.isBlank() || parsedId == null || parsedId <= 0 -> "Enter a valid API ID"
            !hash.matches(API_HASH_PATTERN) -> "Enter a 32-character API hash"
            else -> null
        }
    }

    fun friendlyMessage(raw: String): String {
        val normalized = raw.uppercase()
        val floodSeconds = FLOOD_WAIT.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (floodSeconds != null) {
            return if (floodSeconds > 0) {
                "Too many attempts. Wait $floodSeconds seconds, then try again."
            } else {
                "Too many attempts. Wait a moment, then try again."
            }
        }
        return when {
            "PHONE_NUMBER_INVALID" in normalized ->
                "That phone number is not valid. Include the country code, for example +15551234567."
            "PHONE_NUMBER_BANNED" in normalized ->
                "Telegram has banned this phone number from signing in."
            "PHONE_CODE_INVALID" in normalized ->
                "That login code is not correct. Check Telegram and try again."
            "PHONE_CODE_EXPIRED" in normalized ->
                "That login code expired. Request a new code and try again."
            "PASSWORD_HASH_INVALID" in normalized ->
                "That 2FA password is not correct."
            "API_ID_INVALID" in normalized || "API_ID_PUBLISHED_FLOOD" in normalized ->
                "Those Telegram API credentials were rejected. Check the API ID and hash from my.telegram.org."
            "FLOOD_WAIT" in normalized ->
                "Too many attempts. Wait a moment, then try again."
            else -> raw
        }
    }
}
