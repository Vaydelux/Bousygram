package com.vaydelux.bousygram.presentation.extras

import android.content.Context
import org.json.JSONObject

/**
 * Tiny local public-ID mapping catalog. No provider credentials, signed URLs,
 * user playback data, account identifiers, or raw responses are persisted.
 *
 * Matching is deliberately explicit: two sources disagree about seasons,
 * split cours, specials and numbering. A fuzzy title match must NEVER rewrite
 * an AniList playback ID or claim episodes are the same without user approval.
 */
internal class ExtrasAnimeTmdbMatchStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "extras-anime-episode-metadata-map-v1", Context.MODE_PRIVATE
    )

    fun get(anilistId: Int): ExtrasAnimeTmdbMatch? {
        if (anilistId <= 0) return null
        val raw = prefs.getString("anime:$anilistId", null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            ExtrasAnimeTmdbMatch(
                anilistId = obj.getInt("anilist"),
                tmdbTvId = obj.getInt("tv"),
                tmdbSeason = obj.getInt("season"),
                firstTmdbEpisode = obj.getInt("firstEpisode"),
                tmdbTitle = obj.getString("title")
            ).takeIf { it.anilistId == anilistId }
        }.getOrNull()
    }

    fun save(match: ExtrasAnimeTmdbMatch): Boolean {
        val safe = JSONObject()
            .put("anilist", match.anilistId)
            .put("tv", match.tmdbTvId)
            .put("season", match.tmdbSeason)
            .put("firstEpisode", match.firstTmdbEpisode)
            .put("title", match.tmdbTitle)
        return prefs.edit().putString("anime:${match.anilistId}", safe.toString()).commit()
    }

    fun remove(anilistId: Int): Boolean =
        anilistId > 0 && prefs.edit().remove("anime:$anilistId").commit()
}
