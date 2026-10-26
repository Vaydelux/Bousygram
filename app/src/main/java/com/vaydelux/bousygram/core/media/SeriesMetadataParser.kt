package com.vaydelux.bousygram.core.media

data class ParsedSeriesInfo(
    val seriesTitle: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val year: Int? = null,
    val isSeries: Boolean = (seasonNumber != null || episodeNumber != null)
) {
    val cleanTitle: String? get() = seriesTitle
}

object SeriesMetadataParser {

    private val S_E_REGEX = Regex("""(?i)\bS(\d{1,3})\s*[._-]?\s*E(\d{1,4})\b""")
    private val X_REGEX = Regex("""(?i)\b(\d{1,2})x(\d{1,4})\b""")
    private val SEASON_EPISODE_TEXT_REGEX = Regex("""(?i)\b(?:Season|Series)[.\s_-]*(\d{1,3})[.\s_-]*(?:Episode|Ep)\.?[.\s_-]*(\d{1,4})\b""")
    private val EPISODE_ONLY_REGEX = Regex("""(?i)\b(?:Episode|Ep)\.?[.\s_-]*(\d{1,4})\b""")
    private val SEASON_ONLY_REGEX = Regex("""(?i)\b(?:Season|Series)[.\s_-]*(\d{1,3})\b""")

    private val ANIME_EPISODE_REGEX = Regex("""(?i)^(?:\[[^]]+\]\s*)?(.+?)\s*-\s*(\d{1,4})(?:\s*\[[^]]+\]|\s*\([^)]+\))*$""")
    private val PART_REGEX = Regex("""(?i)\b(?:Part|CD|Disk|Disc)\s*(\d{1,2})\b""")

    // Year matching: 4 digits in (1900..2099)
    private val YEAR_PAREN_REGEX = Regex("""[\(\[]\s*(19\d{2}|20\d{2})\s*[\)\]]""")
    private val YEAR_BOUNDED_REGEX = Regex("""(?:^|[._\s\-\(\[])(19\d{2}|20\d{2})(?:[._\s\-\)\]]|$)""")
    private val EXPLICIT_YEAR_CAPTION_REGEX = Regex("""(?i)\b(?:Year|Release Date|Released)\s*[:=]\s*(19\d{2}|20\d{2})\b""")

    // Common junk tokens to trim from raw series/movie titles
    private val JUNK_SUFFIXES = Regex("""(?i)[. _-]*(?:720p|1080p|1080i|2160p|4k|8k|uhd|fhd|hd|sd|hdtv|web-?dl|webrip|bluray|x264|x265|hevc|aac|ac3|dts|remux).*$""")
    private val BRACKET_TAGS_REGEX = Regex("""\[[^\]]*\]|\{[^\}]*\}""")
    private val TRAILING_RELEASE_GROUP_REGEX = Regex("""-[A-Za-z0-9_]+$""")

    private val FILE_EXTENSIONS = listOf(
        ".mkv", ".mp4", ".avi", ".mov", ".webm", ".m4v", ".flv", ".ts", ".m2ts", ".vob", ".wmv"
    )

    fun parse(title: String?, fileName: String? = null, caption: String? = null): ParsedSeriesInfo {
        val candidates = listOfNotNull(caption?.takeIf { it.isNotBlank() }, fileName?.takeIf { it.isNotBlank() }, title?.takeIf { it.isNotBlank() })
        if (candidates.isEmpty()) {
            return ParsedSeriesInfo(null, null, null, null, false)
        }

        // 1. Extract Season / Episode
        var seasonNum: Int? = null
        var episodeNum: Int? = null
        var rawSeriesTitle: String? = null

        for (candidate in candidates) {
            val parsed = parseCandidate(candidate)
            if (parsed != null && (parsed.seasonNumber != null || parsed.episodeNumber != null)) {
                seasonNum = parsed.seasonNumber
                episodeNum = parsed.episodeNumber
                rawSeriesTitle = parsed.seriesTitle
                break
            }
        }

        // 2. Extract Year
        var parsedYear: Int? = null
        for (candidate in candidates) {
            EXPLICIT_YEAR_CAPTION_REGEX.find(candidate)?.let { match ->
                parsedYear = match.groupValues[1].toIntOrNull()
            }
            if (parsedYear != null) break

            YEAR_PAREN_REGEX.find(candidate)?.let { match ->
                val yr = match.groupValues[1].toIntOrNull()
                if (yr != null && yr in 1900..2099) {
                    parsedYear = yr
                }
            }
            if (parsedYear != null) break
        }

        if (parsedYear == null) {
            for (candidate in candidates) {
                val clean = stripExtensions(candidate)
                YEAR_BOUNDED_REGEX.findAll(clean).forEach { match ->
                    val yr = match.groupValues[1].toIntOrNull()
                    if (yr != null && yr in 1900..2099 && yr != 1080 && yr != 2160) {
                        parsedYear = yr
                        return@forEach
                    }
                }
                if (parsedYear != null) break
            }
        }

        // 3. Clean Title
        val isSeries = seasonNum != null || episodeNum != null
        val finalTitle = if (rawSeriesTitle != null) {
            cleanTitle(rawSeriesTitle, parsedYear)
        } else {
            // Movie or title without S/E
            val primary = candidates.firstOrNull { !isMetadataLabelLine(it) } ?: candidates.first()
            extractCleanTitle(primary, parsedYear)
        }

        return ParsedSeriesInfo(
            seriesTitle = finalTitle,
            seasonNumber = seasonNum,
            episodeNumber = episodeNum,
            year = parsedYear,
            isSeries = isSeries
        )
    }

    private fun parseCandidate(text: String): ParsedSeriesInfo? {
        val cleanText = stripExtensions(text)

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

        // Try Anime style: [ReleaseGroup] Show Name - 05 [1080p]
        ANIME_EPISODE_REGEX.find(cleanText.trim())?.let { match ->
            val rawTitle = match.groupValues[1]
            val episode = match.groupValues[2].toIntOrNull()
            if (episode != null) {
                val title = cleanTitle(rawTitle)
                return ParsedSeriesInfo(title, seasonNumber = 1, episodeNumber = episode)
            }
        }

        // Try Multi-Part: Movie Part 1 or Movie CD2
        PART_REGEX.find(cleanText)?.let { match ->
            val part = match.groupValues[1].toIntOrNull()
            val rawTitle = cleanText.substring(0, match.range.first)
            val title = cleanTitle(rawTitle)
            return ParsedSeriesInfo(title, seasonNumber = 1, episodeNumber = part)
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

    private fun extractCleanTitle(rawText: String, year: Int?): String? {
        val firstLine = rawText.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !isMetadataLabelLine(it) } ?: rawText

        var t = stripExtensions(firstLine)
        t = t.replace(BRACKET_TAGS_REGEX, " ")

        if (year != null) {
            val yearIdx = t.indexOf(year.toString())
            if (yearIdx > 0) {
                t = t.substring(0, yearIdx)
            }
        }

        return cleanTitle(t, year)
    }

    private fun cleanTitle(raw: String, year: Int? = null): String? {
        var t = raw.replace(BRACKET_TAGS_REGEX, " ")
        t = t.replace(JUNK_SUFFIXES, "")
        t = t.replace(TRAILING_RELEASE_GROUP_REGEX, "")

        if (year != null) {
            val yearIdx = t.indexOf(year.toString())
            if (yearIdx > 0) {
                t = t.substring(0, yearIdx)
            }
        }

        t = t.replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .trim()
            .trim('-', ':', '.', '_', ',', ' ', '(', ')', '[', ']', '{', '}')
            .trim()

        return t.ifBlank { null }
    }

    private fun stripExtensions(name: String): String {
        var result = name
        for (ext in FILE_EXTENSIONS) {
            if (result.endsWith(ext, ignoreCase = true)) {
                result = result.substring(0, result.length - ext.length)
                break
            }
        }
        return result
    }

    private fun isMetadataLabelLine(line: String): Boolean {
        val lower = line.lowercase(java.util.Locale.US)
        return lower.startsWith("imdb:") ||
            lower.startsWith("genre:") ||
            lower.startsWith("genres:") ||
            lower.startsWith("rating:") ||
            lower.startsWith("stars:") ||
            lower.startsWith("cast:") ||
            lower.startsWith("director:") ||
            lower.startsWith("audio:") ||
            lower.startsWith("quality:") ||
            lower.startsWith("size:") ||
            lower.startsWith("storyline:") ||
            lower.startsWith("plot:") ||
            lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("#")
    }
}
