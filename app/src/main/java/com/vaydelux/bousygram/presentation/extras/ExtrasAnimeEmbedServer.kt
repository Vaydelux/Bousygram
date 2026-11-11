package com.vaydelux.bousygram.presentation.extras

/** An AniList media ID identifies a specific anime entry, not a TMDB show. */
internal enum class ExtrasAnimeEmbedServer(
    val label: String,
    private val template: String,
    private val queryParams: Map<String, String> = emptyMap(),
    val featured: Boolean = false
) {
    EMBED_HEAVEN("EmbedHeaven", ExtrasEmbedHeavenRoutes.ANIME_TEMPLATE, featured = true),
    VIDY("Vidy", "https://vidy.st/anime/{anilistId}/{episode}", mapOf("color" to "DC2626"), featured = true),
    XXIKU("XXIKU", "https://player.xxiku.com/embed/anime/{anilistId}/{episode}", featured = true),
    FILMU("Filmu", "https://embed.filmu.in/anime/{anilistId}/{season}/{episode}"),
    CINEZO("Cinezo", "https://player.cinezo.live/embed/anime/{anilistId}", mapOf("autoplay" to "true", "poster" to "true", "chromecast" to "false", "servericon" to "true", "setting" to "true", "pip" to "false", "download" to "false", "font" to "Roboto", "fontcolor" to "6f63ff", "fontsize" to "20", "opacity" to "0.5", "primarycolor" to "e8b86d", "secondarycolor" to "0a0a12", "iconcolor" to "ffffff"), featured = true),
    FMOV("FMOV", "https://fmov.my/embed/anime/{anilistId}/{season}/{episode}", featured = true),
    CINETARO("Cinetaro", "https://cinextream.cc/api/embed/anime/sub/{anilistId}/{episode}", mapOf("color" to "e91e63"), featured = true);


    /**
     * An anime episode picker must never silently discard its episode ID.
     * Cinezo currently exposes only a title-level URL; leave the provider
     * registered but do not offer it as an episode-specific choice.
     */
    val selectable: Boolean get() = template.contains("{episode}")

    val description: String get() = when (this) {
        EMBED_HEAVEN -> "AniList episode route · unified movie/TV web player"
        VIDY, XXIKU -> "AniList episode route · external availability varies"
        else -> "External anime embed · availability unverified"
    }

    fun urlFor(anilistId: Int, season: Int = 1, episode: Int = 1): String {
        require(season > 0 && episode > 0) { "Anime selectors are one-based" }
        require(selectable) { "Anime source does not encode an episode ID" }
        require(season == 1 || template.contains("{season}")) {
            "This anime source cannot identify a non-default season"
        }
        return ExtrasEmbedUrlTemplates.build(template, anilistId, season, episode, queryParams)
    }
}
