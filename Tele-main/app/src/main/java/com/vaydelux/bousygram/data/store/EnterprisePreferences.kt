package com.vaydelux.bousygram.data.store

import android.content.Context
import androidx.core.content.edit
import com.vaydelux.bousygram.app.BousygramApp
import org.json.JSONArray
import org.json.JSONObject

enum class BufferMode(val label: String) {
    FastStart("Fast Start"),
    Balanced("Balanced"),
    StableNetwork("Stable Network")
}

object EnterprisePreferences {
    private const val PREFS_NAME = "enterprise_ui_prefs"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_PINNED_CHATS = "pinned_chats"
    private const val KEY_RECENT_QUERIES = "recent_queries"
    private const val KEY_LAST_PLAYED = "last_played"
    private const val KEY_HIDE_WATCHED = "hide_watched"
    private const val KEY_SAFE_MODE = "safe_mode"
    private const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
    private const val KEY_SUBTITLES_DEFAULT = "subtitles_default"
    private const val KEY_SPEED = "preferred_speed"
    private const val KEY_BUFFER_MODE = "buffer_mode"
    private const val KEY_LAST_ERROR = "last_error"

    private val prefs by lazy {
        BousygramApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun favoriteKeys(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
    fun isFavorite(chatId: Long, messageId: Long): Boolean = mediaKey(chatId, messageId) in favoriteKeys()
    fun toggleFavorite(chatId: Long, messageId: Long): Boolean {
        val key = mediaKey(chatId, messageId)
        val next = favoriteKeys().toMutableSet()
        val enabled = if (next.contains(key)) {
            next.remove(key)
            false
        } else {
            next.add(key)
            true
        }
        prefs.edit { putStringSet(KEY_FAVORITES, next) }
        return enabled
    }

    fun pinnedChatIds(): Set<Long> =
        prefs.getStringSet(KEY_PINNED_CHATS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun isPinnedChat(chatId: Long?): Boolean = chatId != null && chatId in pinnedChatIds()
    fun togglePinnedChat(chatId: Long?): Boolean {
        if (chatId == null) return false
        val next = pinnedChatIds().map { it.toString() }.toMutableSet()
        val key = chatId.toString()
        val enabled = if (next.contains(key)) {
            next.remove(key)
            false
        } else {
            next.add(key)
            true
        }
        prefs.edit { putStringSet(KEY_PINNED_CHATS, next) }
        return enabled
    }

    fun recentQueries(): List<String> =
        prefs.getStringSet(KEY_RECENT_QUERIES, emptySet()).orEmpty()
            .sortedBy { it.substringBefore('|').toLongOrNull() ?: 0L }
            .map { it.substringAfter('|') }
            .takeLast(6)
            .reversed()

    fun rememberQuery(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val existing = recentQueries().filterNot { it.equals(clean, ignoreCase = true) }
        val stamped = (listOf(clean) + existing).take(6).mapIndexed { index, value ->
            "${System.currentTimeMillis() - index}|$value"
        }.toSet()
        prefs.edit { putStringSet(KEY_RECENT_QUERIES, stamped) }
    }

    fun clearRecentQueries() {
        prefs.edit { remove(KEY_RECENT_QUERIES) }
    }

    fun setLastPlayed(chatId: Long, messageId: Long, fileId: Int, title: String) {
        prefs.edit {
            putString(KEY_LAST_PLAYED, listOf(chatId, messageId, fileId, title.replace("|", " ")).joinToString("|"))
        }
    }

    fun lastPlayed(): LastPlayed? {
        val parts = prefs.getString(KEY_LAST_PLAYED, null)?.split("|") ?: return null
        if (parts.size < 4) return null
        return LastPlayed(
            chatId = parts[0].toLongOrNull() ?: return null,
            messageId = parts[1].toLongOrNull() ?: return null,
            fileId = parts[2].toIntOrNull() ?: return null,
            title = parts.drop(3).joinToString("|")
        )
    }

    fun hideWatched(): Boolean = prefs.getBoolean(KEY_HIDE_WATCHED, false)
    fun setHideWatched(value: Boolean) = prefs.edit { putBoolean(KEY_HIDE_WATCHED, value) }

    fun safeMode(): Boolean = prefs.getBoolean(KEY_SAFE_MODE, false)
    fun setSafeMode(value: Boolean) = prefs.edit { putBoolean(KEY_SAFE_MODE, value) }

    fun autoPlayNext(): Boolean = prefs.getBoolean(KEY_AUTO_PLAY_NEXT, true)
    fun setAutoPlayNext(value: Boolean) = prefs.edit { putBoolean(KEY_AUTO_PLAY_NEXT, value) }

    fun subtitlesDefault(): Boolean = prefs.getBoolean(KEY_SUBTITLES_DEFAULT, true)
    fun setSubtitlesDefault(value: Boolean) = prefs.edit { putBoolean(KEY_SUBTITLES_DEFAULT, value) }

    fun preferredSpeed(): Float = prefs.getFloat(KEY_SPEED, 1.0f)
    fun setPreferredSpeed(value: Float) = prefs.edit { putFloat(KEY_SPEED, value) }

    fun bufferMode(): BufferMode = runCatching {
        BufferMode.valueOf(prefs.getString(KEY_BUFFER_MODE, BufferMode.FastStart.name) ?: BufferMode.FastStart.name)
    }.getOrDefault(BufferMode.FastStart)

    fun setBufferMode(value: BufferMode) = prefs.edit { putString(KEY_BUFFER_MODE, value.name) }

    fun setLastError(message: String) = prefs.edit { putString(KEY_LAST_ERROR, message.take(400)) }
    fun lastError(): String = prefs.getString(KEY_LAST_ERROR, "None").orEmpty()

    fun diagnosticsText(cacheSizeBytes: Long, appVersion: String): String = buildString {
        appendLine("Bousygram diagnostics")
        appendLine("Version: $appVersion")
        appendLine("Buffer mode: ${bufferMode().label}")
        appendLine("Preferred speed: ${preferredSpeed()}x")
        appendLine("Auto-play next: ${autoPlayNext()}")
        appendLine("Subtitles default: ${subtitlesDefault()}")
        appendLine("Safe mode: ${safeMode()}")
        appendLine("Hide watched: ${hideWatched()}")
        appendLine("Favorites: ${favoriteKeys().size}")
        appendLine("Pinned chats: ${pinnedChatIds().size}")
        appendLine("Cache size: ${cacheSizeBytes / (1024 * 1024)} MB")
        appendLine("Last error: ${lastError()}")
        appendLine("Playback: ${PlaybackDiagnostics.summary()}")
    }

    fun exportSettingsJson(): String = JSONObject()
        .put("favorites", JSONArray(favoriteKeys().toList()))
        .put("pinnedChats", JSONArray(pinnedChatIds().map { it.toString() }))
        .put("recentQueries", JSONArray(recentQueries()))
        .put("lastPlayed", prefs.getString(KEY_LAST_PLAYED, ""))
        .put("continueOnly", hideWatched())
        .put("safeMode", safeMode())
        .put("autoPlayNext", autoPlayNext())
        .put("subtitlesDefault", subtitlesDefault())
        .put("preferredSpeed", preferredSpeed().toDouble())
        .put("bufferMode", bufferMode().name)
        .toString(2)

    fun importSettingsJson(json: String): Result<Unit> = runCatching {
        val obj = JSONObject(json)
        val recent = obj.optStringArray("recentQueries")
            .take(6)
            .mapIndexed { index, query -> "${System.currentTimeMillis() - index}|$query" }
            .toSet()
        val mode = runCatching {
            BufferMode.valueOf(obj.optString("bufferMode", BufferMode.FastStart.name))
        }.getOrDefault(BufferMode.FastStart)
        prefs.edit {
            putStringSet(KEY_FAVORITES, obj.optStringArray("favorites").toSet())
            putStringSet(KEY_PINNED_CHATS, obj.optStringArray("pinnedChats").toSet())
            putStringSet(KEY_RECENT_QUERIES, recent)
            obj.optString("lastPlayed").takeIf { it.isNotBlank() }?.let {
                putString(KEY_LAST_PLAYED, it)
            }
            putBoolean(KEY_HIDE_WATCHED, obj.optBoolean("continueOnly", hideWatched()))
            putBoolean(KEY_SAFE_MODE, obj.optBoolean("safeMode", safeMode()))
            putBoolean(KEY_AUTO_PLAY_NEXT, obj.optBoolean("autoPlayNext", autoPlayNext()))
            putBoolean(KEY_SUBTITLES_DEFAULT, obj.optBoolean("subtitlesDefault", subtitlesDefault()))
            putFloat(KEY_SPEED, obj.optDouble("preferredSpeed", preferredSpeed().toDouble()).toFloat())
            putString(KEY_BUFFER_MODE, mode.name)
        }
    }.onFailure { error ->
        setLastError(error.message ?: "Settings import failed")
    }

    private fun mediaKey(chatId: Long, messageId: Long): String = "$chatId:$messageId"

    private fun JSONObject.optStringArray(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}

data class LastPlayed(
    val chatId: Long,
    val messageId: Long,
    val fileId: Int,
    val title: String
)
