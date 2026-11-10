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

        // Preserve the title ID + type for legacy query-based /embed pages.
        // A repeated parameter must not be treated as a missing parameter.
        if (originalPath == "/embed") {
            val originalId = singleParam(original, "id")
            val originalType = singleParam(original, "type")
            if (originalId != null || originalType != null) {
                if (originalId.isNullOrEmpty() || originalType.isNullOrEmpty()) return false
                if (originalId != singleParam(target, "id") ||
                    originalType != singleParam(target, "type")
                ) return false
            }
        }

        // Title-bound routes can change server selection, not the episode
        // which the host user selected. Duplicates are rejected explicitly.
        if (titleBound) {
            for (key in listOf("s", "e", "season", "episode")) {
                val originalValue = singleParam(original, key)
                if (originalValue != null && originalValue != singleParam(target, key))
                    return false
                if (originalValue == null && occurrences(original, key) > 1)
                    return false
            }
        }
        return true
    }

    private fun occurrences(uri: URI, key: String): Int =
        uri.rawQuery.orEmpty().split('&').count { it.substringBefore('=') == key }

    private fun singleParam(uri: URI, key: String): String? {
        if (occurrences(uri, key) != 1) return null
        return uri.rawQuery.orEmpty().split('&')
            .first { it.substringBefore('=') == key }
            .substringAfter('=', "")
    }
}
