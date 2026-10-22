package com.vaydelux.bousygram.data.store

import android.content.Context
import androidx.core.content.edit
import android.util.Base64
import com.vaydelux.bousygram.core.tdlib.auth.TdlibDatabaseKeyPolicy
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom

/**
 * Provides a persistent encryption key for new TDLib databases. Existing databases without a
 * recorded key retain their legacy empty-key configuration. A mode marker prevents a missing or
 * damaged encrypted key from being mistaken for an old unencrypted database on a later launch.
 */
internal class TdlibDatabaseKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getOrCreateKey(databaseDirectory: File): ByteArray {
        val encryptedKey = prefs.getString(KEY_ENCRYPTED_DATABASE_KEY, null)?.takeIf { it.isNotBlank() }
        val preferenceMode = prefs.getString(KEY_DATABASE_KEY_MODE, null)
        val fileMode = readModeMarker()
        val storedMode = TdlibDatabaseKeyPolicy.resolveMode(preferenceMode, fileMode)
        val hasDatabaseData = encryptedKey == null && storedMode == null && containsExistingData(databaseDirectory)

        return when (TdlibDatabaseKeyPolicy.decide(storedMode, encryptedKey != null, hasDatabaseData)) {
            TdlibDatabaseKeyPolicy.Decision.USE_STORED_ENCRYPTED_KEY -> {
                val encoded = AndroidKeystoreCipher.decrypt(DATABASE_KEY_ALIAS, encryptedKey!!)
                val key = Base64.decode(encoded, Base64.NO_WRAP).also {
                    check(it.size == KEY_BYTES) { "Stored TDLib database key has an invalid length" }
                }
                // This also writes the independent mode marker for earlier v1 installations.
                // If preferences were later cleared while database files remain, the marker keeps
                // the app from guessing that an encrypted database is a legacy empty-key database.
                persistMode(TdlibDatabaseKeyPolicy.MODE_ENCRYPTED_V1)
                key
            }
            TdlibDatabaseKeyPolicy.Decision.USE_LEGACY_EMPTY_KEY -> {
                persistMode(TdlibDatabaseKeyPolicy.MODE_LEGACY_EMPTY)
                byteArrayOf()
            }
            TdlibDatabaseKeyPolicy.Decision.CREATE_ENCRYPTED_KEY -> {
                val bytes = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val encrypted = AndroidKeystoreCipher.encrypt(DATABASE_KEY_ALIAS, encoded)
                // Commit mode and key together before giving the key to TDLib. If persistence
                // fails, do not initialize a database that cannot be reopened after process death.
                val editor = prefs.edit()
                    .putString(KEY_ENCRYPTED_DATABASE_KEY, encrypted)
                    .putString(KEY_DATABASE_KEY_MODE, TdlibDatabaseKeyPolicy.MODE_ENCRYPTED_V1)
                check(editor.commit()) { "Unable to persist protected TDLib database key" }
                persistMode(TdlibDatabaseKeyPolicy.MODE_ENCRYPTED_V1)
                bytes
            }
            TdlibDatabaseKeyPolicy.Decision.FAIL_CLOSED -> throw IllegalStateException(
                "TDLib database-key state is inconsistent. The local database was not changed; " +
                    "restore the protected key or explicitly reset local Telegram data to recover."
            )
        }
    }

    private fun persistMode(mode: String) {
        check(prefs.edit().putString(KEY_DATABASE_KEY_MODE, mode).commit()) {
            "Unable to persist TDLib database-key mode in preferences"
        }
        if (modeMarkerFile.exists() && runCatching { modeMarkerFile.readText() == mode }.getOrDefault(false)) {
            return
        }

        val parent = modeMarkerFile.parentFile
            ?: throw IllegalStateException("TDLib database-key marker directory is unavailable")
        check(parent.exists() || parent.mkdirs()) { "Unable to create TDLib security directory" }
        val temporaryFile = File(parent, "${modeMarkerFile.name}.tmp")
        try {
            FileOutputStream(temporaryFile).use { stream ->
                stream.write(mode.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            if (modeMarkerFile.exists()) {
                check(modeMarkerFile.delete()) { "Unable to replace TDLib database-key marker" }
            }
            check(temporaryFile.renameTo(modeMarkerFile)) { "Unable to persist TDLib database-key marker" }
        } finally {
            runCatching { temporaryFile.delete() }
        }
    }

    private fun readModeMarker(): String? {
        if (!modeMarkerFile.exists()) return null
        // An unreadable or empty existing marker is corruption; do not treat it as absent.
        return modeMarkerFile.readText().trim().ifEmpty { "invalid-mode-marker" }
    }

    private fun containsExistingData(directory: File): Boolean {
        if (!directory.exists()) return false
        return runCatching {
            directory.walkTopDown().any { it.isFile && it.length() > 0L }
        }.getOrDefault(true)
    }

    private val modeMarkerFile: File = File(
        context.applicationContext.noBackupFilesDir,
        "tdlib-database-key-mode"
    )

    private companion object {
        const val PREFS_NAME = "tdlib_database_security"
        const val KEY_ENCRYPTED_DATABASE_KEY = "encrypted_database_key_v1"
        const val KEY_DATABASE_KEY_MODE = "database_key_mode"
        const val DATABASE_KEY_ALIAS = "bousygram.tdlib.database-key.v1"
        const val KEY_BYTES = 32
    }
}
