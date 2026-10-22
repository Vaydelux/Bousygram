package com.vaydelux.bousygram.core.tdlib.auth

/** Pure policy for choosing a TDLib database-key path without ever guessing after key loss. */
internal object TdlibDatabaseKeyPolicy {
    const val MODE_ENCRYPTED_V1 = "encrypted-v1"
    const val MODE_LEGACY_EMPTY = "legacy-empty"
    const val MODE_INCONSISTENT = "inconsistent-mode-markers"

    enum class Decision {
        USE_STORED_ENCRYPTED_KEY,
        USE_LEGACY_EMPTY_KEY,
        CREATE_ENCRYPTED_KEY,
        FAIL_CLOSED
    }

    /** Resolve redundant markers; disagreement is treated as corruption, not a reason to guess. */
    fun resolveMode(preferenceMode: String?, fileMode: String?): String? = when {
        preferenceMode == null -> fileMode
        fileMode == null -> preferenceMode
        preferenceMode == fileMode -> preferenceMode
        else -> MODE_INCONSISTENT
    }

    fun decide(
        storedMode: String?,
        hasEncryptedKey: Boolean,
        hasExistingDatabaseData: Boolean
    ): Decision = when {
        hasEncryptedKey && (storedMode == null || storedMode == MODE_ENCRYPTED_V1) ->
            Decision.USE_STORED_ENCRYPTED_KEY
        hasEncryptedKey -> Decision.FAIL_CLOSED
        storedMode == MODE_ENCRYPTED_V1 -> Decision.FAIL_CLOSED
        storedMode == MODE_LEGACY_EMPTY -> Decision.USE_LEGACY_EMPTY_KEY
        storedMode != null -> Decision.FAIL_CLOSED
        hasExistingDatabaseData -> Decision.USE_LEGACY_EMPTY_KEY
        else -> Decision.CREATE_ENCRYPTED_KEY
    }
}
