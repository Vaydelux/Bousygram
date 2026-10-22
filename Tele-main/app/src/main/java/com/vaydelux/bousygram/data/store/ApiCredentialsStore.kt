package com.vaydelux.bousygram.data.store

import android.content.Context
import androidx.core.content.edit

/** Stores Telegram API credentials encrypted with a key held by Android Keystore. */
class ApiCredentialsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getApiId(): String? = readAndMigrate(KEY_API_ID)

    @Synchronized
    fun getApiHash(): String? = readAndMigrate(KEY_API_HASH)

    @Synchronized
    fun save(apiId: String, apiHash: String) {
        val normalizedId = apiId.trim()
        val normalizedHash = apiHash.trim()
        prefs.edit {
            putString(KEY_API_ID, encryptValue(normalizedId))
            putString(KEY_API_HASH, encryptValue(normalizedHash))
        }
    }

    private fun readAndMigrate(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        if (stored.startsWith(ENCRYPTED_PREFIX)) {
            return runCatching {
                AndroidKeystoreCipher.decrypt(CREDENTIALS_KEY_ALIAS, stored.removePrefix(ENCRYPTED_PREFIX))
            }.getOrNull()
        }

        // One-time migration for installations that stored plain text. Only return the legacy
        // value after its encrypted replacement is durably committed. If protected storage is
        // unavailable, remove the plaintext entry and require the user to re-enter the value
        // rather than silently keeping a credential in clear text.
        val encrypted = runCatching { encryptValue(stored) }.getOrNull()
        if (encrypted != null && prefs.edit().putString(key, encrypted).commit()) {
            return stored
        }
        prefs.edit().remove(key).commit()
        return null
    }

    private fun encryptValue(value: String): String =
        ENCRYPTED_PREFIX + AndroidKeystoreCipher.encrypt(CREDENTIALS_KEY_ALIAS, value)

    companion object {
        private const val PREFS_NAME = "telegram_api_credentials"
        private const val KEY_API_ID = "api_id"
        private const val KEY_API_HASH = "api_hash"
        private const val ENCRYPTED_PREFIX = "enc:v1:"
        private const val CREDENTIALS_KEY_ALIAS = "bousygram.telegram.credentials.v1"
    }
}
