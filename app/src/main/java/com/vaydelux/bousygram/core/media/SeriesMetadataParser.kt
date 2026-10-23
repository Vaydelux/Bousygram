package com.vaydelux.bousygram.core.media

data class ParsedSeriesInfo(
    val seriesTitle: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?
)

object SeriesMetadataParser {

    private val S_E_REGEX = Regex("""(?i)\bS(\d{1,3})\s*[._-]?\s*E(\d{1,4})\b""")
    private val X_REGEX = Regex("""(?i)\b(\d{1,2})x(\d{1,4})\b""")
    private val SEASON_EPISODE_TEXT_REGEX = Regex("""(?i)\b(?:Season|Series)\s*(\d{1,3})\s*[-._]?\s*(?:Episode|Ep)\.?\s*(\d{1,4})\b""")
    private val EPISODE_ONLY_REGEX = Regex("""(?i)\b(?:Episode|Ep)\.?\s*(\d{1,4})\b""")
    private val SEASON_ONLY_REGEX = Regex("""(?i)\b(?:Season|Series)\s*(\d{1,3})\b""")

    // Common junk tokens to trim from raw series titles
    private val JUNK_SUFFIXES = Regex("""(?i)[. _-]*(?:720p|1080p|2160p|4k|uhd|hdtv|web-?dl|webrip|bluray|x264|x265|hevc|aac|dts|remux).*$""")

    fun parse(title: String?, fileName: String?): ParsedSeriesInfo {
        val candidates = listOfNotNull(fileName, title)
        for (candidate in candidates) {
            val parsed = parseCandidate(candidate)
            if (parsed != null && (parsed.seasonNumber != null || parsed.episodeNumber != null)) {
                return parsed
            }
        }
        return ParsedSeriesInfo(null, null, null)
    }

    private fun parseCandidate(text: String): ParsedSeriesInfo? {
        val cleanText = text.replace(".mkv", "", ignoreCase = true)
            .replace(".mp4", "", ignoreCase = true)
            .replace(".avi", "", ignoreCase = true)
            .replace(".mov", "", ignoreCase = true)
            .replace(".webm", "", ignoreCase = true)

        // Try SxxExx
        S_E_REGEX.find(cleanText)?.let { match ->
            val season = match.groupValues[1].toIntOrNull()
            val episode = match.groupValues[2].toIntOrNull()
            val rawTitle = cleanText.substring(0, match.range.first)
            val title = cleanTitle(rawTitle)
            return ParsedSeriesInfo(title, season, episode)
        }

        // Try 1x05
        X_REGEX.find(cleanText)?.let { match ->
            val season = match.groupValues[1].toIntOrNull()
            val episode = match.groupValues[2].toIntOrNull()
            val rawTitle = cleanText.substring(0, match.range.first)
            val title = cleanTitle(rawTitle)
            return ParsedSeriesInfo(title, season, episode)
        }

        // Try Season X Episode Y
        SEASON_EPISODE_TEXT_REGEX.find(cleanText)?.let { match ->
            val season = match.groupValues[1].toIntOrNull()
            val episode = match.groupValues[2].toIntOrNull()
            val rawTitle = cleanText.substring(0, match.range.first)
            val title = cleanTitle(rawTitle)
            return ParsedSeriesInfo(title, season, episode)
        }

        // Try Episode X
        EPISODE_ONLY_REGEX.find(cleanText)?.let { match ->
            val episode = match.groupValues[1].toIntOrNull()
            val rawTitle = cleanText.substring(0, match.range.first)
            val title = cleanTitle(rawTitle)
            return ParsedSeriesInfo(title, seasonNumber = 1, episodeNumber = episode)
        }

        // Try Season X
        SEASON_ONLY_REGEX.find(cleanText)?.let { match ->
            val season = match.groupValues[1].toIntOrNull()
            val rawTitle = cleanText.substring(0, match.range.first)
            val title = cleanTitle(rawTitle)
            return ParsedSeriesInfo(title, seasonNumber = season, episodeNumber = null)
        }

        return null
    }

    private fun cleanTitle(raw: String): String? {
        var t = raw.replace(JUNK_SUFFIXES, "")
        t = t.replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .trim()
            .trim('-', ':', '.', '_')
            .trim()
        return t.ifBlank { null }
    }
}
