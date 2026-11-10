package com.vaydelux.bousygram.core.account

import android.content.Context
import com.vaydelux.bousygram.core.media.diagnostics.EngineVerdictStore
import com.vaydelux.bousygram.core.tv.WatchNextManager
import java.io.File

/** Exact account-specific stores, never the global TDLib database or another account's data. */
internal data class AccountPrivateDataPaths(
    val accountDirectory: File,
    val roomDatabaseName: String,
    val preferencesName: String,
    val playbackProgressPreferencesName: String
)

internal fun privateDataPaths(filesDir: File, accountId: Long): AccountPrivateDataPaths {
    require(accountId > 0L) { "Only verified Telegram account IDs may be purged" }
    return AccountPrivateDataPaths(
        accountDirectory = File(File(filesDir, "accounts"), accountId.toString()),
        roomDatabaseName = "bousygram_account_${accountId}.db",
        preferencesName = "account_${accountId}_enterprise",
        playbackProgressPreferencesName = privatePlaybackProgressPreferenceName(accountId)
    )
}

/** The legacy resume ledger is stored separately from the account's enterprise preferences. */
internal fun privatePlaybackProgressPreferenceName(accountId: Long): String {
    require(accountId > 0L) { "Only verified Telegram account IDs may select private progress" }
    return "playback_resume_positions_account_$accountId"
}

/** Private generated poster and seek-preview images live under cacheDir, not filesDir. */
internal fun privateAccountCacheDirectory(cacheRoot: File, accountId: Long): File {
    require(accountId > 0L) { "Only verified Telegram account IDs may be purged" }
    return File(File(cacheRoot, "accounts"), accountId.toString())
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
        // PlaybackProgressTracker writes positions to a separate account-scoped preferences
        // document. Clear and delete it AFTER session invalidation drained in-flight writes.
        val progressPrefs = names.playbackProgressPreferencesName
        check(context.getSharedPreferences(progressPrefs, Context.MODE_PRIVATE)
            .edit().clear().commit()) { "Unable to clear account playback positions" }
        // deleteSharedPreferences may return false when no resume file ever existed;
        // verify the actual file/backup state instead of treating an empty account as failure.
        context.deleteSharedPreferences(progressPrefs)
        val xml = File(File(context.applicationInfo.dataDir, "shared_prefs"), "$progressPrefs.xml")
        check(!xml.exists() && !File(xml.path + ".bak").exists()) {
            "Unable to erase account playback positions"
        }
        // The globally bounded recovery store contains account-scoped media keys.
        // Revoke only this account's records; another account may still be active later.
        EngineVerdictStore.clearAccount(context, accountId)
        // Launcher recommendations are outside app-private storage. Best-effort removal
        // is scoped to the revoked account; a TV provider may decline deletion.
        WatchNextManager.clearRevokedAccountRows(context, accountId)
        val db = context.getDatabasePath(names.roomDatabaseName)
        check(context.deleteDatabase(names.roomDatabaseName) || !db.exists()) {
            "Unable to erase account media index"
        }
        val dir = names.accountDirectory
        check(!dir.exists() || dir.deleteRecursively()) {
            "Unable to erase account watch history"
        }
        // MediaMetadataRetriever-generated private posters and scrub frames
        // are deliberately kept in per-account Android cache, which is NOT
        // covered by the filesDir account folder deletion above.
        val privateCache = privateAccountCacheDirectory(context.cacheDir, accountId)
        check(!privateCache.exists() || privateCache.deleteRecursively()) {
            "Unable to erase cached Telegram thumbnails and seek frames"
        }
    }
}
