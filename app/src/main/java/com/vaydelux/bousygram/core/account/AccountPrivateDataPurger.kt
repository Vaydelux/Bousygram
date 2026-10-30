package com.vaydelux.bousygram.core.account

import android.content.Context
import java.io.File

/** Exact account-specific stores, never the global TDLib database or another account's data. */
internal data class AccountPrivateDataPaths(
    val accountDirectory: File,
    val roomDatabaseName: String,
    val preferencesName: String
)

internal fun privateDataPaths(filesDir: File, accountId: Long): AccountPrivateDataPaths {
    require(accountId > 0L) { "Only verified Telegram account IDs may be purged" }
    return AccountPrivateDataPaths(
        accountDirectory = File(File(filesDir, "accounts"), accountId.toString()),
        roomDatabaseName = "bousygram_account_${accountId}.db",
        preferencesName = "account_${accountId}_enterprise"
    )
}

/**
 * Called only after TDLib explicitly confirms authorization loss and AccountSession.invalidate
 * has synchronously closed players, cache tasks, and Room handles.
 *
 * TDLib owns its global native database/cache and must clean it using TdApi.Destroy, never
 * by deleting files from under the running JNI client.
 */
internal object AccountPrivateDataPurger {
    fun purge(context: Context, accountId: Long) {
        val names = privateDataPaths(context.filesDir, accountId)
        check(context.getSharedPreferences(names.preferencesName, Context.MODE_PRIVATE)
            .edit().clear().commit()) { "Unable to erase account preferences" }
        val db = context.getDatabasePath(names.roomDatabaseName)
        check(context.deleteDatabase(names.roomDatabaseName) || !db.exists()) {
            "Unable to erase account media index"
        }
        val dir = names.accountDirectory
        check(!dir.exists() || dir.deleteRecursively()) {
            "Unable to erase account watch history"
        }
    }
}
