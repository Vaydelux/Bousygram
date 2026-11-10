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
    private val fixedTitleRoute = Regex(
        "^/embed/(movie|tv|anime)/[0-9]+(?:/[0-9]+)*$"
    )

    fun allows(selectedEmbed: URI, requestedUrl: String): Boolean {
        val original = MediaForgeUrlPolicy.safeHttpsUrl(selectedEmbed.toString())
            ?: return false
        val target = MediaForgeUrlPolicy.safeHttpsUrl(requestedUrl)
            ?: return false
        if (!target.host.equals(original.host, ignoreCase = true)) return false

        val originalPath = original.path.orEmpty()
        val targetPath = target.path.orEmpty()
        val titleBound = fixedTitleRoute.matches(originalPath)

        val pathAllowed = targetPath == originalPath ||
            (!titleBound && originalPath.startsWith("/embed/") &&
                targetPath.startsWith("/embed/"))
        if (!pathAllowed) return false

        // Only the server selector may change. Compare the *full* identity
        // parameter set, including absence: a newly introduced season/episode
        // must not silently switch the title selected by the host application.
        if (originalPath == "/embed") {
            val identity = setOf("id", "type")
            val selected = protectedParameters(original, identity) ?: return false
            val requested = protectedParameters(target, identity) ?: return false
            if (selected != requested) return false
            if (selected.isNotEmpty() &&
                (selected["id"].isNullOrBlank() || selected["type"].isNullOrBlank())
            ) return false
        }

        if (titleBound) {
            val episode = setOf("s", "e", "season", "episode")
            val selected = protectedParameters(original, episode) ?: return false
            val requested = protectedParameters(target, episode) ?: return false
            if (selected != requested) return false
        }
        return true
    }

    /**
     * Reject duplicate identity keys, including encoded and case variants.
     * Preserve the exact selected values (rather than normalizing signed
     * provider queries). Semicolon separation is recognized because some
     * server-side frameworks treat it like '&'.
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
