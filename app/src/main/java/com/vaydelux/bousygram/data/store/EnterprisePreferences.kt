package com.vaydelux.bousygram.data.store

import android.content.Context
import androidx.core.content.edit
import com.vaydelux.bousygram.app.BousygramApp
import org.json.JSONArray
import org.json.JSONObject

enum class SelectedPlayerEngine(val label: String) {
    AUTO("Auto (ExoPlayer + mpv fallback)"),
    ExoPlayer("Media3 ExoPlayer"),
    MPV("Universal libmpv (MKV, AVI, FLV, SSA/ASS)")
}

enum class BufferMode(val label: String) {
    FastStart("Fast Start"),
    Balanced("Balanced"),
    StableNetwork("Stable Network")
}

enum class CacheLimitOption(val label: String, val bytes: Long) {
    OneGb("1 GB", 1L * 1024L * 1024L * 1024L),
    TwoGb("2 GB", 2L * 1024L * 1024L * 1024L),
    FiveGb("5 GB", 5L * 1024L * 1024L * 1024L),
    TenGb("10 GB", 10L * 1024L * 1024L * 1024L)
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
    private const val KEY_SUBTITLE_LANGUAGE = "subtitle_language"
    private const val KEY_SPEED = "preferred_speed"
    private const val KEY_BUFFER_MODE = "buffer_mode"
    private const val KEY_CACHE_LIMIT = "cache_limit"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_VISUAL_THEME = "visual_theme"
    private const val KEY_PLAYER_ENGINE = "player_engine"
    private const val KEY_VIDEO_DEBUG_HUD = "video_debug_hud"
    private const val KEY_SURFACE_TYPE = "surface_type"
    private const val KEY_MPV_HWDEC_MODE = "mpv_hwdec_mode"


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

    fun visualThemeId(): String = prefs.getString(KEY_VISUAL_THEME, "midnight") ?: "midnight"
    fun setVisualThemeId(value: String) = prefs.edit { putString(KEY_VISUAL_THEME, value) }

    fun safeMode(): Boolean = prefs.getBoolean(KEY_SAFE_MODE, false)
    fun setSafeMode(value: Boolean) = prefs.edit { putBoolean(KEY_SAFE_MODE, value) }

    fun autoPlayNext(): Boolean = prefs.getBoolean(KEY_AUTO_PLAY_NEXT, true)
    fun setAutoPlayNext(value: Boolean) = prefs.edit { putBoolean(KEY_AUTO_PLAY_NEXT, value) }

    fun subtitlesDefault(): Boolean = prefs.getBoolean(KEY_SUBTITLES_DEFAULT, true)
    fun setSubtitlesDefault(value: Boolean) = prefs.edit { putBoolean(KEY_SUBTITLES_DEFAULT, value) }

    fun subtitleLanguage(): String = prefs.getString(KEY_SUBTITLE_LANGUAGE, "en") ?: "en"
    fun setSubtitleLanguage(value: String) = prefs.edit { putString(KEY_SUBTITLE_LANGUAGE, value) }

    fun preferredSpeed(): Float = prefs.getFloat(KEY_SPEED, 1.0f)
    fun setPreferredSpeed(value: Float) = prefs.edit { putFloat(KEY_SPEED, value) }

    fun selectedPlayerEngine(): SelectedPlayerEngine = runCatching {
        SelectedPlayerEngine.valueOf(
            prefs.getString(KEY_PLAYER_ENGINE, SelectedPlayerEngine.AUTO.name)
                ?: SelectedPlayerEngine.AUTO.name
        )
    }.getOrDefault(SelectedPlayerEngine.AUTO)

    fun setSelectedPlayerEngine(engine: SelectedPlayerEngine) = prefs.edit {
        putString(KEY_PLAYER_ENGINE, engine.name)
    }

    fun bufferMode(): BufferMode = runCatching {
        BufferMode.valueOf(prefs.getString(KEY_BUFFER_MODE, BufferMode.FastStart.name) ?: BufferMode.FastStart.name)
    }.getOrDefault(BufferMode.FastStart)

    fun setBufferMode(value: BufferMode) = prefs.edit { putString(KEY_BUFFER_MODE, value.name) }

    @Deprecated("Cache limit is now automatically managed based on a fraction of free storage")
    fun cacheLimitOption(): CacheLimitOption = CacheLimitOption.TwoGb

    @Deprecated("Cache limit setting has been removed in favor of automatic cache management")
    fun setCacheLimitOption(value: CacheLimitOption) {
        // No-op: automatic cache management is active
    }

    fun cacheLimitBytes(): Long = ManagedAppConfiguration.cacheLimitBytes()
        ?: com.vaydelux.bousygram.core.tdlib.cache.TelegramCachePruner.calculateDynamicCacheLimit()

    fun setLastError(message: String) = prefs.edit { putString(KEY_LAST_ERROR, message.take(400)) }
    fun lastError(): String = prefs.getString(KEY_LAST_ERROR, "None").orEmpty()

    fun videoDebugHudEnabled(): Boolean = prefs.getBoolean(KEY_VIDEO_DEBUG_HUD, false)
    fun setVideoDebugHudEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_VIDEO_DEBUG_HUD, enabled) }

    fun surfaceTypePreference(): String = prefs.getString(KEY_SURFACE_TYPE, "auto") ?: "auto"
    fun setSurfaceTypePreference(type: String) = prefs.edit { putString(KEY_SURFACE_TYPE, type) }

    fun mpvHwDecMode(): String = prefs.getString(KEY_MPV_HWDEC_MODE, "mediacodec-copy") ?: "mediacodec-copy"
    fun setMpvHwDecMode(mode: String) = prefs.edit { putString(KEY_MPV_HWDEC_MODE, mode) }


    fun diagnosticsText(cacheSizeBytes: Long, appVersion: String): String = buildString {
        appendLine("Bousygram diagnostics")
        appendLine("Version: $appVersion")
        appendLine("Buffer mode: ${bufferMode().label}")
        appendLine("Playback: TDLib local")
        appendLine("Preferred speed: ${preferredSpeed()}x")
        appendLine("Auto-play next: ${autoPlayNext()}")
        appendLine("Subtitles default: ${subtitlesDefault()}")
        appendLine("Privacy mode: ${safeMode()}")
        appendLine("Visual theme: ${visualThemeId()}")
        appendLine("Hide watched: ${hideWatched()}")
        appendLine("Favorites: ${favoriteKeys().size}")
        appendLine("Pinned chats: ${pinnedChatIds().size}")
        appendLine("Cache size: ${cacheSizeBytes / (1024 * 1024)} MB")
        appendLine("Cache limit: ${cacheLimitBytes() / (1024 * 1024)} MB (automatic)")
        appendLine("Full-video background cache: Disabled")
        appendLine("Last error: ${lastError()}")
        appendLine("Playback: ${PlaybackDiagnostics.summary()}")
        appendLine("Playback telemetry: ${com.vaydelux.bousygram.core.observability.PlaybackTelemetryRecorder.summary()}")
    }

    fun exportSettingsJson(): String = JSONObject()
        .put("favorites", JSONArray(favoriteKeys().toList()))
        .put("pinnedChats", JSONArray(pinnedChatIds().map { it.toString() }))
        .put("recentQueries", JSONArray(recentQueries()))
        .put("lastPlayed", prefs.getString(KEY_LAST_PLAYED, ""))
        .put("continueOnly", hideWatched())
        .put("safeMode", safeMode())
        .put("visualTheme", visualThemeId())
        .put("autoPlayNext", autoPlayNext())
        .put("subtitlesDefault", subtitlesDefault())
        .put("preferredSpeed", preferredSpeed().toDouble())
        .put("bufferMode", bufferMode().name)
        .put("cacheLimit", cacheLimitOption().name)
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
        val cacheLimit = runCatching {
            CacheLimitOption.valueOf(obj.optString("cacheLimit", CacheLimitOption.TwoGb.name))
        }.getOrDefault(CacheLimitOption.TwoGb)
        val visualTheme = obj.optString("visualTheme", visualThemeId())
            .takeIf { it in setOf("midnight", "aurora", "ocean", "sunset") }
            ?: visualThemeId()
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
            putString(KEY_CACHE_LIMIT, cacheLimit.name)
            putString(KEY_VISUAL_THEME, visualTheme)
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
