package com.vaydelux.bousygram.core.media.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import com.vaydelux.bousygram.data.store.SelectedPlayerEngine

data class EngineVerdict(
    val recommendedEngine: SelectedPlayerEngine,
    val useTextureView: Boolean,
    val reason: String,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Pure policy for bounded, time-limited playback recovery hints. A verdict is a hint for
 * one particular Telegram media item, not proof that an entire container/codec works.
 */
internal object EngineVerdictPolicy {
    const val MAX_ENTRIES = 96
    const val MAX_REASON_LENGTH = 180
    const val TTL_MS = 7L * 24L * 60L * 60L * 1000L

    fun isFresh(timestampMs: Long, nowMs: Long): Boolean =
        timestampMs > 0L && timestampMs <= nowMs && nowMs - timestampMs < TTL_MS

    /** Fail closed on corrupt or unrecognized persisted preferences. */
    fun decodeEngine(raw: String?): SelectedPlayerEngine? = when (raw) {
        "ExoPlayer" -> SelectedPlayerEngine.ExoPlayer
        "MPV", "Mpv", "MpvPlayer" -> SelectedPlayerEngine.MPV
        "LIBVLC", "LIBVLC_EXPERIMENTAL" -> SelectedPlayerEngine.LIBVLC
        else -> null
    }

    /** Each saved verdict uses four preference keys, with _time as its index. */
    fun evictions(values: Map<String, *>, nowMs: Long): Set<String> {
        val times = values.entries.asSequence()
            .filter { it.key.endsWith("_time") }
            .map { it.key.removeSuffix("_time") to (it.value as? Long ?: 0L) }
            .toList()
        val stale = times.filterNot { isFresh(it.second, nowMs) }.map { it.first }
        val overflow = times.filter { isFresh(it.second, nowMs) }
            .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })
            .drop(MAX_ENTRIES)
            .map { it.first }
        return (stale + overflow).toSet()
    }
}

/**
 * A small cache of recovery hints. Keys are account-scoped media identities so a malformed
 * file cannot change the default player for every other MKV/TS file on the device.
 */
object EngineVerdictStore {
    private const val TAG = "EngineVerdictStore"
    private const val PREFS_NAME = "bousygram_engine_verdicts"
    private const val MAX_KEY_LENGTH = 180

    // Access-ordered for eviction. All accesses are guarded by the object's monitor.
    private val inMemoryCache = LinkedHashMap<String, EngineVerdict>(16, 0.75f, true)

    /** Separates an account's private hints from other signed-in accounts. */
    internal fun accountKeyPrefix(accountId: Long): String {
        require(accountId > 0L) { "Only verified accounts may be purged" }
        return "media_${accountId}_"
    }

    internal fun isAccountPreferenceKey(accountId: Long, preferenceKey: String): Boolean {
        val prefix = accountKeyPrefix(accountId)
        return preferenceKey.startsWith(prefix) && listOf(
            "_engine", "_texture", "_reason", "_time"
        ).any(preferenceKey::endsWith)
    }

    /**
     * Called after AccountSession invalidation when TDLib confirms an account's
     * authorization has been revoked. Never erase another account's hints.
     * Use commit() so failed cleanup blocks activation just like account DB cleanup.
     */
    @Synchronized
    fun clearAccount(context: Context, accountId: Long) {
        val prefix = accountKeyPrefix(accountId)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keys = prefs.all.keys.filter { isAccountPreferenceKey(accountId, it) }
        if (keys.isNotEmpty()) {
            val editor = prefs.edit()
            keys.forEach { editor.remove(it) }
            check(editor.commit()) { "Unable to erase account playback recovery hints" }
        }
        inMemoryCache.keys.removeAll { it.startsWith(prefix) }
    }

    /** Older device/format key remains available to callers that explicitly use it. */
    fun buildKey(
        mimeType: String?,
        container: String?,
        profile: Int = 0,
        is10Bit: Boolean = false,
        deviceModel: String = Build.MODEL
    ): String {
        val mime = mimeType?.lowercase()?.trim() ?: "unknown_mime"
        val cont = container?.lowercase()?.trim() ?: "unknown_container"
        val bit = if (is10Bit) "10bit" else "8bit"
        return "${deviceModel}_${cont}_${mime}_p${profile}_$bit"
    }

    /** Telegram chat ids may be negative; message ids and verified account ids are positive. */
    fun buildMediaKey(accountId: Long?, chatId: Long, messageId: Long): String? =
        if (accountId == null || accountId <= 0L || chatId == 0L || messageId <= 0L) null
        else "media_${accountId}_${chatId}_${messageId}"

    @Synchronized
    fun getVerdict(context: Context, key: String): EngineVerdict? {
        if (key.isBlank() || key.length > MAX_KEY_LENGTH) return null
        val now = System.currentTimeMillis()
        inMemoryCache[key]?.let {
            if (EngineVerdictPolicy.isFresh(it.timestampMs, now)) return it
            inMemoryCache.remove(key)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Inspect the raw preferences to tolerate type mismatches caused by older installations.
        val values = prefs.all
        val engine = EngineVerdictPolicy.decodeEngine(values["${key}_engine"] as? String)
        val time = values["${key}_time"] as? Long ?: 0L
        if (engine == null || !EngineVerdictPolicy.isFresh(time, now)) {
            if (values.containsKey("${key}_engine") || values.containsKey("${key}_time")) {
                removeEntry(prefs, key)
            }
            return null
        }

        val verdict = EngineVerdict(
            recommendedEngine = engine,
            useTextureView = values["${key}_texture"] as? Boolean ?: false,
            reason = (values["${key}_reason"] as? String).orEmpty()
                .take(EngineVerdictPolicy.MAX_REASON_LENGTH),
            timestampMs = time
        )
        remember(key, verdict)
        return verdict
    }

    @Synchronized
    fun recordVerdict(context: Context, key: String, verdict: EngineVerdict) {
        if (key.isBlank() || key.length > MAX_KEY_LENGTH ||
            verdict.recommendedEngine == SelectedPlayerEngine.AUTO ||
            !EngineVerdictPolicy.isFresh(verdict.timestampMs, System.currentTimeMillis())
        ) return

        val sanitized = verdict.copy(reason = verdict.reason.take(EngineVerdictPolicy.MAX_REASON_LENGTH))
        remember(key, sanitized)
        // Do not log media identity or Telegram identifiers.
        Log.i(TAG, "Recording recovery hint for ${sanitized.recommendedEngine}")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val snapshot = prefs.all.toMutableMap()
        snapshot["${key}_time"] = sanitized.timestampMs
        val evictions = EngineVerdictPolicy.evictions(snapshot, System.currentTimeMillis())
        val editor = prefs.edit()
            .putString("${key}_engine", sanitized.recommendedEngine.name)
            .putBoolean("${key}_texture", sanitized.useTextureView)
            .putString("${key}_reason", sanitized.reason)
            .putLong("${key}_time", sanitized.timestampMs)
        evictions.forEach { old ->
            removeFields(editor, old)
            inMemoryCache.remove(old)
        }
        editor.apply()
    }

    private fun remember(key: String, verdict: EngineVerdict) {
        inMemoryCache[key] = verdict
        while (inMemoryCache.size > EngineVerdictPolicy.MAX_ENTRIES) {
            inMemoryCache.remove(inMemoryCache.keys.first())
        }
    }

    private fun removeEntry(prefs: android.content.SharedPreferences, key: String) {
        inMemoryCache.remove(key)
        removeFields(prefs.edit(), key).apply()
    }

    private fun removeFields(
        editor: android.content.SharedPreferences.Editor,
        key: String
    ): android.content.SharedPreferences.Editor = editor
        .remove("${key}_engine")
        .remove("${key}_texture")
        .remove("${key}_reason")
        .remove("${key}_time")

    @Synchronized
    fun clearAll(context: Context) {
        inMemoryCache.clear()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
