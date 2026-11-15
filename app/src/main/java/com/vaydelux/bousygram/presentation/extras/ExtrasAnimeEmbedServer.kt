package com.vaydelux.bousygram.presentation.extras

/** An AniList media ID identifies a specific anime entry, not a TMDB show. */
internal enum class ExtrasAnimeEmbedServer(
    val label: String,
    private val template: String,
    private val queryParams: Map<String, String> = emptyMap(),
    val featured: Boolean = false
) {
    EMBED_HEAVEN("EmbedHeaven", ExtrasEmbedHeavenRoutes.ANIME_TEMPLATE, featured = true),
    // Zoryva documents a season- AND episode-specific route using AniList IDs.
    ZORYVA("Zoryva X", "https://zoryva.me/embed/anime/{anilistId}/{season}/{episode}", featured = true),
    VIDY("Vidy", "https://vidy.st/anime/{anilistId}/{episode}", mapOf("color" to "DC2626"), featured = true),
    XXIKU("XXIKU", "https://player.xxiku.com/embed/anime/{anilistId}/{episode}", featured = true),
    FILMU("Filmu", "https://embed.filmu.in/anime/{anilistId}/{season}/{episode}"),
    FMOV("FMOV", "https://fmov.my/embed/anime/{anilistId}/{season}/{episode}", featured = true),
    CINETARO("Cinetaro", "https://cinextream.cc/api/embed/anime/sub/{anilistId}/{episode}", mapOf("color" to "e91e63"), featured = true);


    /** Anime source choices must preserve the selected episode ID. */
    val selectable: Boolean get() = template.contains("{episode}")

    /** Non-default anime seasons must have an explicit season route. */
    fun supportsSeason(season: Int): Boolean = season > 0 && selectable &&
        (season == 1 || template.contains("{season}"))

    val description: String get() = when (this) {
        EMBED_HEAVEN -> "AniList episode route · unified movie/TV web player"
        ZORYVA -> "Documented AniList season/episode iframe · availability and rights unverified"
        VIDY, XXIKU -> "AniList episode route · external availability varies"
        else -> "External anime embed · availability unverified"
    }

    fun urlFor(anilistId: Int, season: Int = 1, episode: Int = 1): String {
        require(season > 0 && episode > 0) { "Anime selectors are one-based" }
        require(selectable) { "Anime source does not encode an episode ID" }
        require(supportsSeason(season)) { "This anime source cannot identify the selected season" }
        return ExtrasEmbedUrlTemplates.build(template, anilistId, season, episode, queryParams)
    }
}
