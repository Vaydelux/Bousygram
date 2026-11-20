package com.vaydelux.bousygram.data.repository

/**
 * Only cleaned, plausible catalog titles are eligible for an outbound TMDB
 * search. Raw paths, e-mail addresses, camera filenames and private identifiers
 * must never be sent automatically to a third-party metadata provider.
 *
 * This is intentionally conservative; users can still play the original
 * Telegram video when identification is ambiguous.
 */
internal object TmdbQueryPolicy {
    private val whitespace = Regex("""\s+""")
    private val extensions = Regex("""(?i)\.(?:mkv|mp4|avi|mov|webm|m4v|ts|m2ts|wmv)$""")
    private val cameraNames = Regex("""(?i)^(?:img|vid|dsc|pxl|screen[_ -]?record(?:ing)?)[_ -]?\d{4,}.*$""")
    private val longIdentifier = Regex("""(?i)^[0-9a-f]{24,}$""")

    fun cleanedTitle(title: String, year: Int?): String? {
        if (title.any { it.isISOControl() }) return null
        val clean = title.trim().replace(whitespace, " ")
        if (clean.length !in 2..120) return null
        if (year != null && year !in 1900..2099) return null
        if (clean.contains('@') || clean.contains('/') || clean.contains('\\')) return null
        if (clean.contains("://") || extensions.containsMatchIn(clean)) return null
        if (cameraNames.matches(clean) || longIdentifier.matches(clean)) return null
        if (clean.all { !it.isLetter() && !it.isDigit() }) return null
        // Numbers alone may be real film titles (for example, "1917"), but
        // arbitrary long numeric IDs are not safe as catalog searches.
        if (clean.all(Char::isDigit)) {
            val value = clean.toIntOrNull()
            if (clean.length != 4 || value == null || value !in 1900..2099) return null
        }
        return clean
    }
}
