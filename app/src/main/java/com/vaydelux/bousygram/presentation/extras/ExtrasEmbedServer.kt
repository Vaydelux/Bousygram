package com.vaydelux.bousygram.presentation.extras

/**
 * Explicit, documented third-party embed providers. TMDB supplies title IDs
 * and metadata, never the video or an entitlement to watch it.
 *
 * A provider being listed does NOT mean its media are reachable, licensed,
 * playable, or supported by the native media preflight.
 */
internal enum class ExtrasEmbedServer(
    val label: String,
    val description: String
) {
    CINESRC("CineSrc", "Original external embed"),
    XXIKU("XXIKU", "Server chooser and automatic retry inside the embed"),
    EMBED_HEAVEN("EmbedHeaven", "Alternate external embedded player");

    fun urlFor(tmdbId: Int, season: Int? = null, episode: Int? = null): String {
        require(tmdbId > 0) { "TMDB ID must be positive" }
        require((season == null) == (episode == null)) {
            "A TV selection requires both season and episode"
        }
        if (season != null) require(season >= 0 && episode!! > 0) {
            "Invalid season/episode"
        }
        return if (season == null) {
            when (this) {
                CINESRC -> "https://cinesrc.st/embed/movie/$tmdbId"
                XXIKU -> "https://player.xxiku.com/embed/movie/$tmdbId?best=1&auto=1&style=compact"
                EMBED_HEAVEN -> "https://player.embedheaven.st/movie/$tmdbId?autoplay=0"
            }
        } else {
            when (this) {
                CINESRC -> "https://cinesrc.st/embed/tv/$tmdbId?s=$season&e=$episode"
                XXIKU -> "https://player.xxiku.com/embed/tv/$tmdbId/$season/$episode?best=1&auto=1&style=compact"
                EMBED_HEAVEN -> "https://player.embedheaven.st/tv/$tmdbId/$season/$episode?autoplay=0"
            }
        }
    }
}
