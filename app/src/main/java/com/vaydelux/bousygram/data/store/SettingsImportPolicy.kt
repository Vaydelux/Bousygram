package com.vaydelux.bousygram.data.store

import org.json.JSONArray
import org.json.JSONObject

/** Fully validated before touching either preferences file. Null means absent, not erase. */
internal data class SettingsImport(
    val favorites: Set<String>? = null, val pinnedChats: Set<String>? = null,
    val recentQueries: List<String>? = null, val lastPlayed: String? = null,
    val hideWatched: Boolean? = null, val safeMode: Boolean? = null,
    val autoPlayNext: Boolean? = null, val subtitlesDefault: Boolean? = null,
    val speed: Float? = null, val bufferMode: BufferMode? = null, val visualTheme: String? = null
)

internal object SettingsImportPolicy {
    private val privateFields = setOf("favorites", "pinnedChats", "recentQueries", "lastPlayed")
    fun parse(json: String, accountId: Long?): SettingsImport {
        require(json.length <= 65_536) { "Settings document exceeds 64 KiB" }
        val obj = JSONObject(json)
        if (obj.has("version")) require(obj.getInt("version") == 1) { "Unsupported settings version" }
        if (privateFields.any(obj::has)) {
            require(accountId != null && obj.has("accountId") && obj.getLong("accountId") == accountId) {
                "Private settings require the same verified Telegram account; legacy unowned data was not imported"
            }
        }
        fun strings(name: String, max: Int): List<String>? {
            if (!obj.has(name)) return null
            val array = obj.get(name)
            require(array is JSONArray && array.length() <= max) { "Invalid $name list" }
            return List(array.length()) { index ->
                val value = array.get(index)
                require(value is String && value.length <= 1_024) { "Invalid $name entry" }
                value
            }
        }
        fun bool(name: String): Boolean? {
            if (!obj.has(name)) return null
            val value = obj.get(name)
            require(value is Boolean) { "Invalid $name value" }
            return value
        }
        val favorites = strings("favorites", 1_000)?.onEach { key ->
            val parts = key.split(':')
            require(parts.size == 2 && parts[0].toLongOrNull()?.let { it != 0L } == true &&
                parts[1].toLongOrNull()?.let { it != 0L } == true) { "Invalid favorite identity" }
        }?.toSet()
        val pinned = strings("pinnedChats", 1_000)?.onEach {
            require(it.toLongOrNull()?.let { id -> id != 0L } == true) { "Invalid pinned chat identity" }
        }?.toSet()
        val recent = strings("recentQueries", 12)?.onEach {
            require(it.isNotBlank() && it.length <= 512) { "Invalid search query" }
        }
        val last = if (obj.has("lastPlayed")) obj.getString("lastPlayed").also {
            require(it.length <= 2_048) { "Invalid last-played entry" }
            if (it.isNotEmpty()) {
                val parts = it.split('|')
                require(parts.size >= 4 && parts[0].toLongOrNull()?.let { id -> id != 0L } == true &&
                    parts[1].toLongOrNull()?.let { id -> id != 0L } == true &&
                    parts[2].toIntOrNull()?.let { id -> id > 0 } == true) { "Invalid last-played identity" }
            }
        } else null
        val speed = if (obj.has("preferredSpeed")) obj.getDouble("preferredSpeed").toFloat().also {
            require(it.isFinite() && it in 0.25f..3f) { "Playback speed must be between 0.25 and 3" }
        } else null
        val mode = if (obj.has("bufferMode")) BufferMode.valueOf(obj.getString("bufferMode")) else null
        val theme = if (obj.has("visualTheme")) obj.getString("visualTheme").also {
            require(it in setOf("midnight", "aurora", "ocean", "sunset")) { "Unknown visual theme" }
        } else null
        return SettingsImport(favorites, pinned, recent, last, bool("continueOnly"), bool("safeMode"),
            bool("autoPlayNext"), bool("subtitlesDefault"), speed, mode, theme)
    }
}
