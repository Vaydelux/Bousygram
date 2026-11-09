package com.vaydelux.bousygram.presentation.extras

/**
 * Canonical EmbedHeaven routes provided for Extras. Metadata IDs and channel
 * slugs are different identity types and must never be interchanged.
 */
internal object ExtrasEmbedHeavenRoutes {
    private const val BASE = "https://player.embedheaven.st"
    const val MOVIE_TEMPLATE = "$BASE/movie/{tmdbId}"
    const val TV_TEMPLATE = "$BASE/tv/{tmdbId}/{season}/{episode}"
    const val ANIME_TEMPLATE = "$BASE/anime/{anilistId}/{episode}"
    private const val LEGACY_MOVIE_TEMPLATE = "$BASE/embed?id={tmdbId}&type=movie"
    private val channelPattern = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

    fun movie(tmdbId: Int): String =
        ExtrasEmbedUrlTemplates.build(MOVIE_TEMPLATE, tmdbId, null, null)

    fun tv(tmdbId: Int, season: Int, episode: Int): String =
        ExtrasEmbedUrlTemplates.build(TV_TEMPLATE, tmdbId, season, episode)

    fun anime(anilistId: Int, episode: Int): String {
        require(episode > 0) { "Anime episode must be positive" }
        return ExtrasEmbedUrlTemplates.build(ANIME_TEMPLATE, anilistId, 1, episode)
    }

    fun isValidChannelSlug(slug: String): Boolean =
        slug.length in 1..80 && channelPattern.matches(slug)

    fun live(slug: String): String {
        require(isValidChannelSlug(slug)) { "Invalid live channel slug" }
        return "$BASE/live/$slug"
    }

    fun legacyMovie(tmdbId: Int): String =
        ExtrasEmbedUrlTemplates.build(LEGACY_MOVIE_TEMPLATE, tmdbId, null, null)
}
