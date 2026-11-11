package com.vaydelux.bousygram.presentation.extras

import java.net.URI
import java.net.URLEncoder

/**
 * Preconfigured third-party URL shapes; a listed server is NOT a claim of
 * authorized content, availability, compatibility, safety or live health.
 * Metadata IDs are never interchangeable between TMDB and AniList.
 */
internal object ExtrasEmbedUrlTemplates {
    fun build(template: String, id: Int, season: Int?, episode: Int?,
              params: Map<String, String> = emptyMap(), startAtSec: Int? = null): String {
        require(id > 0) { "Catalog ID must be positive" }
        require((season == null) == (episode == null)) { "Both season and episode must be supplied" }
        if (season != null) require(season >= 0 && episode!! > 0) { "Invalid season/episode" }
        require(template.startsWith("https://")) { "An absolute HTTPS template is required" }
        val rendered = template
            .replace("{tmdbId}", id.toString())
            .replace("{anilistId}", id.toString())
            .replace("{id}", id.toString())
            .replace("{season}", (season ?: 1).toString())
            .replace("{episode}", (episode ?: 1).toString())
        require(!Regex("\\{[A-Za-z]+\\}").containsMatchIn(rendered)) { "Unresolved embed template" }
        val uri = URI(rendered)
        require(uri.scheme == "https" && !uri.host.isNullOrEmpty() &&
            uri.userInfo == null && uri.fragment == null) { "Unsafe embed template" }
        val query = buildMap {
            putAll(params)
            if (startAtSec != null && startAtSec > 0) put("startAt", startAtSec.toString())
        }.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
        return if (query.isEmpty()) rendered else rendered + (if (uri.rawQuery == null) "?" else "&") + query
    }
}

/** Movie and TV selectors consume TMDB IDs only. */
internal enum class ExtrasEmbedServer(
    val label: String,
    val description: String,
    private val movieTemplate: String,
    private val tvTemplate: String,
    private val queryParams: Map<String, String> = emptyMap(),
    val featured: Boolean = false,
    val selectable: Boolean = true
) {
    CINESRC("CineSrc", "Original external embed", "https://cinesrc.st/embed/movie/{tmdbId}", "https://cinesrc.st/embed/tv/{tmdbId}?s={season}&e={episode}"),
    XXIKU("XXIKU", "External movie/TV embed; availability unverified", "https://player.xxiku.com/embed/movie/{tmdbId}", "https://player.xxiku.com/embed/tv/{tmdbId}/{season}/{episode}"),
    EMBED_HEAVEN("EmbedHeaven", "External movie/TV embed; availability unverified", ExtrasEmbedHeavenRoutes.MOVIE_TEMPLATE, ExtrasEmbedHeavenRoutes.TV_TEMPLATE),
    SERVER_111MOVIES("111Movies", "External embed; playback and licensing unverified", "https://111movies.net/movie/{id}", "https://111movies.net/tv/{id}/{season}/{episode}"),
    SERVER_1EMBED("1Embed", "External embed; playback and licensing unverified", "https://1embed.cc/embed/movie/{id}", "https://1embed.cc/embed/tv/{id}/{season}/{episode}"),
    SERVER_2EMBED("2Embed", "External embed; playback and licensing unverified", "https://www.2embed.cc/embed/{id}", "https://www.2embed.cc/embedtv/{id}?s={season}&e={episode}"),
    AUTOEMBED("AutoEmbed", "External embed; playback and licensing unverified", "https://player.autoembed.app/embed/movie/{id}", "https://player.autoembed.app/embed/tv/{id}/{season}/{episode}"),
    CINEMAOS("CinemaOS", "External embed; playback and licensing unverified", "https://cinemaos.tech/player/{tmdbId}", "https://cinemaos.tech/player/{tmdbId}/{season}/{episode}"),
    ICEFY("Icefy", "External embed; playback and licensing unverified", "https://embed.icefy.top/movie/{id}", "https://embed.icefy.top/tv/{id}/{season}/{episode}"),
    MAPPLE("MappleTV", "External embed; playback and licensing unverified", "https://mappletv.uk/watch/movie/{tmdbId}", "https://mappletv.uk/watch/tv/{tmdbId}-{season}-{episode}"),
    NONTONGO("NontonGo", "External embed; playback and licensing unverified", "https://nontongo.win/embed/movie/{id}", "https://nontongo.win/embed/tv/{id}/{season}/{episode}"),
    NXSHA("Nxsha", "External embed; playback and licensing unverified", "https://web.nxsha.app/embed/movie/{id}", "https://web.nxsha.app/embed/tv/{id}/{season}/{episode}"),
    PEACHIFY("Peachify", "External embed; playback and licensing unverified", "https://peachify.top/embed/movie/{id}", "https://peachify.top/embed/tv/{id}/{season}/{episode}"),
    PRIMESRC("PrimeSrc", "External embed; playback and licensing unverified", "https://primesrc.me/embed/movie?tmdb={tmdbId}", "https://primesrc.me/embed/tv?tmdb={tmdbId}&season={season}&episode={episode}"),
    SMASHYSTREAM("SmashyStream", "External embed; playback and licensing unverified", "https://player.smashystream.com/movie/{id}", "https://player.smashystream.com/tv/{id}?s={season}&e={episode}"),
    STREAMVAULT("StreamVault", "External embed; playback and licensing unverified", "https://streamvaultsrc.click/embed/movie/{id}", "https://streamvaultsrc.click/embed/tv/{id}/{season}/{episode}"),
    SUPEREMBED("SuperEmbed", "External embed; playback and licensing unverified", "https://multiembed.mov/?video_id={id}&tmdb=1", "https://multiembed.mov/?video_id={id}&tmdb=1&s={season}&e={episode}"),
    SUPEREMBED_VIP("SuperEmbed VIP", "External embed; playback and licensing unverified", "", "", selectable = false),
    THISISCINEMA("ThisIsCinema", "External embed; playback and licensing unverified", "https://thisiscinema.pages.dev/embed/movie/{id}", "https://thisiscinema.pages.dev/embed/tv/{id}/{season}/{episode}"),
    VIDAPI("VidAPI", "External embed; playback and licensing unverified", "https://vidapi.xyz/embed/movie/{id}", "https://vidapi.xyz/embed/tv/{id}/{season}/{episode}"),
    VIDFAST("VidFast", "External embed; playback and licensing unverified", "https://vidfast.pro/movie/{id}", "https://vidfast.pro/tv/{id}/{season}/{episode}", queryParams = mapOf("autoPlay" to "true")),
    VIDFLIX("VidFlix", "External embed; playback and licensing unverified", "https://vidflix.club/movie/{tmdbId}", "https://vidflix.club/tv/{tmdbId}/{season}/{episode}"),
    VIDJOY("VidJoy", "External embed; playback and licensing unverified", "https://vidjoy.pro/embed/movie/{tmdbId}", "https://vidjoy.pro/embed/tv/{tmdbId}/{season}/{episode}"),
    VIDKING("VidKing", "External embed; playback and licensing unverified", "https://www.vidking.net/embed/movie/{id}", "https://www.vidking.net/embed/tv/{id}/{season}/{episode}"),
    VIDLINK("VidLink", "External embed; playback and licensing unverified", "https://vidlink.pro/movie/{tmdbId}", "https://vidlink.pro/tv/{tmdbId}/{season}/{episode}"),
    VIDPLAYS("VidPlays", "External embed; playback and licensing unverified", "https://vidplays.fun/embed/movie/{tmdbId}", "https://vidplays.fun/embed/tv/{tmdbId}/{season}/{episode}"),
    VIDROCK("VidRock", "External embed; playback and licensing unverified", "https://vidrock.ru/movie/{id}", "https://vidrock.ru/tv/{id}/{season}/{episode}"),
    VIDSRC("VidSrc", "External embed; playback and licensing unverified", "https://vidsrc.to/embed/movie/{id}", "https://vidsrc.to/embed/tv/{id}/{season}/{episode}"),
    VIDSRC_XYZ("VidSrc Pro", "External embed; playback and licensing unverified", "https://vidsrc.xyz/embed/movie/{tmdbId}", "https://vidsrc.xyz/embed/tv/{tmdbId}/{season}/{episode}"),
    VIDSRC_EMBED("VidSrc Embed", "External embed; playback and licensing unverified", "https://vidsrc-embed.ru/embed/movie?tmdb={tmdbId}", "https://vidsrc-embed.ru/embed/tv?tmdb={tmdbId}&season={season}&episode={episode}"),
    VIDSYNC("VidSync", "External embed; playback and licensing unverified", "https://vidsync.xyz/embed/movie/{id}", "https://vidsync.xyz/embed/tv/{id}/{season}/{episode}"),
    VIDUP("VidUp", "External embed; playback and licensing unverified", "https://vidup.to/embed/{id}", "https://vidup.to/embed/tv/{id}/{season}/{episode}"),
    VIDZEE("VidZee", "External embed; playback and licensing unverified", "https://player.vidzee.wtf/embed/movie/{tmdbId}", "https://player.vidzee.wtf/embed/tv/{tmdbId}/{season}/{episode}"),
    VIDZEN("VidZen", "External embed; playback and licensing unverified", "https://vidzen.fun/movie/{tmdbId}", "https://vidzen.fun/tv/{tmdbId}/{season}/{episode}"),
    VIDCORE("VidCore", "External embed; playback and licensing unverified", "https://vidcore.net/embed/movie/{id}", "https://vidcore.net/embed/tv/{id}/{season}/{episode}"),
    VIXSRC("VixSrc", "External embed; playback and licensing unverified", "https://vixsrc.to/movie/{id}", "https://vixsrc.to/tv/{id}/{season}/{episode}"),
    VSEMBED("VSEmbed", "External embed; playback and licensing unverified", "https://vsembed.ru/embed/movie?tmdb={tmdbId}", "https://vsembed.ru/embed/tv?tmdb={tmdbId}&season={season}&episode={episode}"),
    WFS("WatchFreeStreams", "External embed; playback and licensing unverified", "https://embed.wfs.lol/embed/movie/{tmdbId}", "https://embed.wfs.lol/embed/tv/{tmdbId}/{season}/{episode}"),
    EMBED_SU("Embed SU (High Quality)", "External embed; playback and licensing unverified", "https://embed.filmu.in/movie/{tmdbId}", "https://embed.filmu.in/tv/{tmdbId}/{season}/{episode}"),
    OVIDADS("OVIDADS", "External embed; playback and licensing unverified", "https://0vidads.top/embed/movie/{tmdbId}", "https://0vidads.top/embed/tv/{tmdbId}/{season}/{episode}", queryParams = mapOf("branding" to "bousy", "server" to "orion", "subtitle" to "English", "color" to "FF0000", "loading" to "2", "back" to "true"), featured = true),
    ATLANTIC("Atlantic", "External embed; playback and licensing unverified", "https://atlantic.st/embed/movie/{tmdbId}", "https://atlantic.st/embed/tv/{tmdbId}/{season}/{episode}", featured = true),
    CINETARO("Cinetaro", "External embed; playback and licensing unverified", "https://cinextream.cc/api/embed/movie/{tmdbId}", "https://cinextream.cc/api/embed/tv/{tmdbId}/{season}/{episode}", queryParams = mapOf("color" to "e91e63"), featured = true),
    CINEZO("Cinezo", "External embed; playback and licensing unverified", "https://player.cinezo.live/embed/movie/{tmdbId}", "https://player.cinezo.live/embed/tv/{tmdbId}/{season}/{episode}", queryParams = mapOf("autoplay" to "true", "poster" to "true", "chromecast" to "false", "servericon" to "true", "setting" to "true", "pip" to "false", "download" to "false", "font" to "Roboto", "fontcolor" to "6f63ff", "fontsize" to "20", "opacity" to "0.5", "primarycolor" to "e8b86d", "secondarycolor" to "0a0a12", "iconcolor" to "ffffff"), featured = true),
    FMOV("FMOV", "External embed; playback and licensing unverified", "https://fmov.my/embed/movie/{tmdbId}", "https://fmov.my/embed/tv/{tmdbId}/{season}/{episode}", featured = true),
    FRAMEXTV("FrameXTV", "External embed; playback and licensing unverified", "https://framextv.xyz/embed/{tmdbId}", "https://framextv.xyz/embed/{tmdbId}/{season}/{episode}", queryParams = mapOf("autoplay" to "1", "muted" to "0"), featured = true),
    STELLAR("Stellar", "External embed; playback and licensing unverified", "https://stellar.rip/en/watch/embed/movie/{tmdbId}", "https://stellar.rip/en/watch/embed/tv/{tmdbId}-{season}-{episode}", queryParams = mapOf("theme" to "9B8CFF", "title" to "true", "poster" to "true", "autoPlay" to "false", "startAt" to "0", "nextButton" to "true", "autoNext" to "false"), featured = true),
    VELARIUM("Velarium", "External embed; playback and licensing unverified", "https://velarium.cc/en/watch/movie/{tmdbId}", "https://velarium.cc/en/watch/tv/{tmdbId}-{season}-{episode}", queryParams = mapOf("autoPlay" to "false", "theme" to "A8E6CF", "title" to "true", "poster" to "true", "startAt" to "0", "nextButton" to "true", "autoNext" to "false", "autoSkipIntro" to "false", "autoSkipOutro" to "false"), featured = true),
    VIDUKI_1("Viduki (Multi Server)", "External embed; playback and licensing unverified", "https://viduki.net/1/movie/{id}", "https://viduki.net/1/tv/{id}/{season}/{episode}", queryParams = mapOf("color" to "DC2626"), featured = true),
    VIDUKI_2("Viduki (Multi Language)", "External embed; playback and licensing unverified", "https://viduki.net/2/movie/{id}", "https://viduki.net/2/tv/{id}/{season}/{episode}", queryParams = mapOf("color" to "DC2626"), featured = true),
    VIDUKI_3("Viduki (Multi Embeds)", "External embed; playback and licensing unverified", "https://viduki.net/3/movie/{id}", "https://viduki.net/3/tv/{id}/{season}/{episode}", queryParams = mapOf("color" to "DC2626"), featured = true),
    VIDUKI_4("Viduki (Premium)", "External embed; playback and licensing unverified", "https://viduki.net/4/movie/{id}", "https://viduki.net/4/tv/{id}/{season}/{episode}", queryParams = mapOf("color" to "DC2626"), featured = true),
    VIDY("Vidy", "External embed; playback and licensing unverified", "https://vidy.st/movie/{tmdbId}", "https://vidy.st/tv/{tmdbId}/{season}/{episode}", queryParams = mapOf("color" to "DC2626"), featured = true);

    fun urlFor(tmdbId: Int, season: Int? = null, episode: Int? = null,
               startAtSec: Int? = null): String {
        require(selectable) { "This embed needs a configured absolute HTTPS host" }
        val template = if (season == null) movieTemplate else tvTemplate
        val resume = if (this in setOf(VIDFAST, PEACHIFY, PRIMESRC)) startAtSec else null
        return ExtrasEmbedUrlTemplates.build(template, tmdbId, season, episode, queryParams, resume)
    }
}
