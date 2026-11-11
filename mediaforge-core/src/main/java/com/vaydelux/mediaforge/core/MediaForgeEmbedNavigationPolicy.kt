package com.vaydelux.mediaforge.core

import java.net.URI

/**
 * A pure, SDK-owned navigation gate for user-selected external embed pages.
 *
 * This constrains WebView *main-frame* navigation, not CDN/media subresources.
 * WebView does not expose socket-level DNS pinning or every network redirect
 * through its resource-interception callback. Do not treat this as an SSRF
 * defense for arbitrary browser traffic.
 */
object MediaForgeEmbedNavigationPolicy {
    // Some providers put the title in the path; others keep a fixed path and
    // pass the title/episode in the query. Both forms must preserve identity.
    private val identityParameters = setOf(
        "id", "tmdb", "tmdb_id", "tmdbid", "anilist", "anilist_id",
        "anilistid", "video_id", "imdb", "imdb_id", "type",
        "s", "e", "season", "episode"
    )

    fun allows(selectedEmbed: URI, requestedUrl: String): Boolean {
        val original = MediaForgeUrlPolicy.safeHttpsUrl(selectedEmbed.toString())
            ?: return false
        val target = MediaForgeUrlPolicy.safeHttpsUrl(requestedUrl)
            ?: return false
        if (!target.host.equals(original.host, ignoreCase = true)) return false

        // Do not permit an unrecognized /embed/{id} template to navigate to
        // another ID merely because both paths start with /embed/. Compare raw
        // paths so encoded separators/canonicalization cannot change identity.
        if (target.rawPath != original.rawPath) return false

        // A fixed path such as /embed/movie?tmdb=550 or
        // /embed/tv?tmdb=550&season=1&episode=2 has the SAME title guarantee
        // as /embed/movie/550. Non-identity switches (server, subtitles,
        // startAt, etc.) remain free to change on the selected host/path.
        val selected = protectedParameters(original, identityParameters) ?: return false
        val requested = protectedParameters(target, identityParameters) ?: return false
        if (selected != requested) return false

        // Preserve existing stricter semantics for the original legacy route.
        if (original.rawPath == "/embed" && selected.isNotEmpty() &&
            (selected["id"].isNullOrBlank() || selected["type"].isNullOrBlank())
        ) return false

        return true
    }

    /**
     * Reject duplicate identity keys, including encoded and case variants.
     * Preserve exact selected values rather than normalizing signed queries.
     * Semicolon separation is recognized because some frameworks treat it as '&'.
     */
    private fun protectedParameters(uri: URI, keys: Set<String>): Map<String, String>? {
        val parameters = LinkedHashMap<String, String>()
        for (field in uri.rawQuery.orEmpty().split('&', ';')) {
            if (field.isEmpty()) continue
            val rawKey = field.substringBefore('=')
            val key = runCatching { java.net.URLDecoder.decode(rawKey, "UTF-8") }
                .getOrNull()?.lowercase(java.util.Locale.ROOT) ?: return null
            if (key !in keys) continue
            if (parameters.containsKey(key)) return null
            parameters[key] = field.substringAfter('=', "")
        }
        return parameters
    }
}
