package com.vaydelux.bousygram.presentation.extras

/**
 * Episode navigation is scoped to the selected catalog identity.
 * AniList IDs are NEVER replaced by TMDB IDs for playback.
 * A separate verified mapping may supply episode descriptions and images.
 */
internal data class ExtrasAnimeTmdbMatch(
    val anilistId: Int,
    val tmdbTvId: Int,
    val tmdbSeason: Int,
    /** TMDB episode corresponding to AniList episode 1. May be >1 for split cours. */
    val firstTmdbEpisode: Int,
    val tmdbTitle: String
) {
    init {
        require(anilistId > 0 && tmdbTvId > 0)
        require(tmdbSeason >= 0 && firstTmdbEpisode > 0)
        require(tmdbTitle.isNotBlank() && tmdbTitle.length <= 160)
    }

    fun tmdbEpisode(anilistEpisode: Int): Int {
        require(anilistEpisode > 0)
        return firstTmdbEpisode + anilistEpisode - 1
    }

    fun anilistEpisode(tmdbEpisode: Int): Int? =
        (tmdbEpisode - firstTmdbEpisode + 1).takeIf { it > 0 }
}

internal data class ExtrasEpisodeGuide(
    val catalogType: ExtrasType,
    val titleId: Int,
    val seasons: List<ExtrasSeason>,
    val animeEpisodeCount: Int? = null,
    val animeTmdbMatch: ExtrasAnimeTmdbMatch? = null
) {
    init {
        require(titleId > 0)
        require(animeTmdbMatch == null ||
            (catalogType == ExtrasType.ANIME && animeTmdbMatch.anilistId == titleId))
    }

    /** Only episodes whose existence is supported by the current catalog are navigable. */
    fun count(season: Int): Int? = when (catalogType) {
        ExtrasType.MOVIES -> null
        ExtrasType.ANIME -> if (season == 1) animeEpisodeCount?.takeIf { it > 0 } else null
        ExtrasType.TV -> seasons.firstOrNull { it.number == season }
            ?.episodeCount?.takeIf { it > 0 }
    }

    fun contains(season: Int, episode: Int): Boolean =
        episode > 0 && (count(season)?.let { episode <= it } == true)

    fun adjacent(season: Int, episode: Int, step: Int): Pair<Int, Int>? {
        if (step !in listOf(-1, 1) || !contains(season, episode)) return null
        val count = count(season) ?: return null
        if (step == 1 && episode < count) return season to episode + 1
        if (step == -1 && episode > 1) return season to episode - 1
        if (catalogType != ExtrasType.TV) return null
        val candidate = seasons
            .filter { it.number > 0 && it.episodeCount > 0 }
            .sortedBy { it.number }
        val index = candidate.indexOfFirst { it.number == season }
        if (index < 0) return null
        val neighbor = candidate.getOrNull(index + step) ?: return null
        return neighbor.number to (if (step > 0) 1 else neighbor.episodeCount)
    }

    /** Bound the selection list in memory even for extremely long-running series. */
    fun page(season: Int, start: Int, pageSize: Int = 48): IntRange? {
        val count = count(season) ?: return null
        val from = start.coerceIn(1, count)
        val to = (from.toLong() + pageSize.coerceIn(1, 60) - 1)
            .coerceAtMost(count.toLong()).toInt()
        return from..to
    }
}

internal fun ExtrasEmbedRequest.forEpisode(
    nextSeason: Int,
    nextEpisode: Int
): ExtrasEmbedRequest {
    require(nextSeason >= 0 && nextEpisode > 0)
    if (anilistId > 0) {
        // A new AniList season/cour usually has a different media ID.
        // Never point the current AniList ID at an unrelated season.
        require(nextSeason == (season ?: 1)) {
            "Anime across seasons requires a separately matched AniList title"
        }
        val source = animeServer
        return copy(
            season = nextSeason,
            episode = nextEpisode,
            title = title.substringBefore(" · ") + " · E$nextEpisode",
            url = source.urlFor(anilistId, nextSeason, nextEpisode)
        )
    }
    require(tmdbId > 0 && season != null) { "Episode navigation is only available for series" }
    val source = server
    return copy(
        season = nextSeason,
        episode = nextEpisode,
        title = title.substringBefore(" · ") + " · S$nextSeason E$nextEpisode",
        url = source.urlFor(tmdbId, nextSeason, nextEpisode),
        legacyEmbedHeaven = false
    )
}
