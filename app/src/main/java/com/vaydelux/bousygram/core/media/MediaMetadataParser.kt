package com.vaydelux.bousygram.core.media

import java.util.Locale

/**
 * Parsed media metadata extracted from filenames, titles, and Telegram captions.
 */
data class ParsedMediaMetadata(
    /** Cleaned canonical title stripped of junk tags, extensions, and resolution markers. */
    val title: String,
    /** Extracted 4-digit release year (1900..2099) if detected. */
    val year: Int? = null,
    /** Season number if TV series. */
    val seasonNumber: Int? = null,
    /** Episode number if TV series. */
    val episodeNumber: Int? = null,
    /** Whether this item is recognized as a TV show or episodic series. */
    val isSeries: Boolean = false
)

/**
 * Robust zero-allocation / regex parser for movie and TV series filenames and captions.
 * Supports standard scene names, multiline Telegram captions, anime brackets, and various separators.
 */
object MediaMetadataParser {

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

    // Common junk tokens, quality, codec, audio, source markers
    private val JUNK_TOKENS_REGEX = Regex(
        """(?i)\b(?:720p|1080p|1080i|2160p|4k|8k|uhd|fhd|hd|sd|""" +
        """x264|x265|h264|h265|hevc|avc|xvid|divx|10bit|8bit|hdr|hdr10|dv|dolby\s*vision|""" +
        """aac|ac3|eac3|dts|dts-hd|truehd|atmos|mp3|flac|ddp5\.1|5\.1ch|7\.1ch|2\.0ch|""" +
        """bluray|blu-ray|bdrip|brrip|webrip|web-dl|web|hdtv|dvdrip|remux|telesync|camrip|proper|repack)\b"""
    )

    private val JUNK_SUFFIX_FROM_MATCH = Regex(
        """(?i)[. _-]*(?:720p|1080p|1080i|2160p|4k|8k|uhd|fhd|hd|""" +
        """x264|x265|hevc|aac|ac3|dts|bluray|web-?dl|webrip|hdtv|remux).*$"""
    )

    // Release group tags like [YTS], [TGx], [RARBG], -SPARKS, etc.
    private val BRACKET_TAGS_REGEX = Regex("""\[[^\]]*\]|\{[^\}]*\}""")
    private val TRAILING_RELEASE_GROUP_REGEX = Regex("""-[A-Za-z0-9_]+$""")

    // Video file extensions
    private val FILE_EXTENSIONS = listOf(
        ".mkv", ".mp4", ".avi", ".mov", ".webm", ".m4v", ".flv", ".ts", ".m2ts", ".vob", ".wmv"
    )

    /**
     * Parses title, year, season, and episode from caption and/or filename.
     */
    fun parse(title: String?, caption: String? = null, fileName: String? = null): ParsedMediaMetadata {
        val rawCandidate = caption?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() }
            ?: fileName.orEmpty()

        if (rawCandidate.isBlank()) {
            return ParsedMediaMetadata(title = "")
        }

        // 1. Extract potential season/episode
        var seasonNum: Int? = null
        var episodeNum: Int? = null
        var isSeries = false

        // Check fileName first, then caption/title for SxxExx
        val textCandidates = listOfNotNull(fileName, caption, title)
        for (candidate in textCandidates) {
            val seriesInfo = SeriesMetadataParser.parse(candidate, null)
            if (seriesInfo.seasonNumber != null || seriesInfo.episodeNumber != null) {
                seasonNum = seriesInfo.seasonNumber
                episodeNum = seriesInfo.episodeNumber
                isSeries = true
                break
            }
        }

        // 2. Extract Year
        var parsedYear: Int? = null
        for (candidate in textCandidates) {
            // Check explicit caption tag first: "Year: 2023"
            EXPLICIT_YEAR_CAPTION_REGEX.find(candidate)?.let { match ->
                parsedYear = match.groupValues[1].toIntOrNull()
            }
            if (parsedYear != null) break

            // Check (YYYY) or [YYYY]
            YEAR_PAREN_REGEX.find(candidate)?.let { match ->
                val yr = match.groupValues[1].toIntOrNull()
                if (yr != null && yr in 1900..2099) {
                    parsedYear = yr
                }
            }
            if (parsedYear != null) break
        }

        // If not found in parens, check bounded year (avoiding numbers in resolution or season)
        if (parsedYear == null) {
            for (candidate in textCandidates) {
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

        // 3. Extract Clean Title
        val cleanTitle = extractCleanTitle(
            caption = caption,
            title = title,
            fileName = fileName,
            year = parsedYear,
            hasSeason = seasonNum != null || episodeNum != null
        )

        return ParsedMediaMetadata(
            title = cleanTitle,
            year = parsedYear,
            seasonNumber = seasonNum,
            episodeNumber = episodeNum,
            isSeries = isSeries
        )
    }

    /**
     * Extracts and cleans the movie or show title.
     */
    private fun extractCleanTitle(
        caption: String?,
        title: String?,
        fileName: String?,
        year: Int?,
        hasSeason: Boolean
    ): String {
        // If caption contains multiline text, first line often contains the actual title
        val primarySource = if (!caption.isNullOrBlank()) {
            val firstLine = caption.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() && !isMetadataLabelLine(it) }
            firstLine ?: caption
        } else if (!title.isNullOrBlank()) {
            title
        } else {
            fileName.orEmpty()
        }

        var text = stripExtensions(primarySource)

        // Remove bracketed release group tags like [YTS.MX], [Erai-raws], etc.
        text = text.replace(BRACKET_TAGS_REGEX, " ")

        // If there's an SxxExx or 1x02 marker, cut everything from it onwards
        S_E_REGEX.find(text)?.let {
            text = text.substring(0, it.range.first)
        }
        X_REGEX.find(text)?.let {
            text = text.substring(0, it.range.first)
        }
        SEASON_EPISODE_TEXT_REGEX.find(text)?.let {
            text = text.substring(0, it.range.first)
        }
        if (hasSeason) {
            SEASON_ONLY_REGEX.find(text)?.let {
                text = text.substring(0, it.range.first)
            }
            EPISODE_ONLY_REGEX.find(text)?.let {
                text = text.substring(0, it.range.first)
            }
        }

        // If there's an anime-style "- 05" episode pattern, cut from the dash
        Regex("""\s*-\s*\d{1,4}(?:\s.*)?$""").find(text)?.let {
            text = text.substring(0, it.range.first)
        }

        // If there's a year, cut everything from the year onwards (for filenames like Inception.2010.1080p)
        if (year != null) {
            val yearIdx = text.indexOf(year.toString())
            if (yearIdx > 0) {
                text = text.substring(0, yearIdx)
            }
        }

        // Remove junk suffix tokens (1080p, x264, etc.)
        text = text.replace(JUNK_SUFFIX_FROM_MATCH, "")
        text = text.replace(JUNK_TOKENS_REGEX, " ")
        text = text.replace(TRAILING_RELEASE_GROUP_REGEX, "")

        // Normalize separators: dots, underscores, dashes to spaces
        text = text.replace('.', ' ')
            .replace('_', ' ')
            .replace('-', ' ')

        // Normalize extra whitespace
        text = text.split("\\s+".toRegex()).joinToString(" ").trim()
        text = text.trim('-', ':', '.', '_', ',', ' ', '(', ')', '[', ']', '{', '}')

        // Fallback if empty
        if (text.isBlank()) {
            text = stripExtensions(fileName ?: title ?: "Video")
                .replace('.', ' ')
                .replace('_', ' ')
                .trim()
        }

        return text
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
        val lower = line.lowercase(Locale.US)
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
