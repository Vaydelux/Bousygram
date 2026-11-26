package com.vaydelux.mediaforge.core

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Strict bounded UTF-8 SRT -> WebVTT conversion; no media transcoding or disk writes. */
object SrtWebVtt {
    const val MAX_INPUT_BYTES = 512 * 1024
    private const val MAX_CUES = 5000
    private const val MAX_LINE_CHARS = 4096
    private val timing = Regex("""^(\d{2,3}):([0-5]\d):([0-5]\d),(\d{3})[ \t]+-->[ \t]+(\d{2,3}):([0-5]\d):([0-5]\d),(\d{3})$""")
    private val ordinal = Regex("[0-9]{1,9}")

    fun convert(input: ByteArray): ByteArray? {
        if (input.isEmpty() || input.size > MAX_INPUT_BYTES) return null
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(input)).toString()
        } catch (_: Exception) {
            return null
        }
        val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.any { it.isISOControl() && it != '\n' && it != '\t' }) return null
        val lines = normalized.split('\n')
        if (lines.any { it.length > MAX_LINE_CHARS }) return null
        val output = StringBuilder("WEBVTT\n\n")
        var i = 0
        var cues = 0
        var previousStart = -1L
        while (i < lines.size) {
            while (i < lines.size && lines[i].isBlank()) i++
            if (i == lines.size) break
            if (ordinal.matches(lines[i].trim())) i++
            if (i == lines.size) return null
            val match = timing.matchEntire(lines[i++].trim()) ?: return null
            val start = timestamp(match, 1)
            val end = timestamp(match, 5)
            if (start < previousStart || end <= start || ++cues > MAX_CUES) return null
            previousStart = start
            output.append(format(start)).append(" --> ").append(format(end)).append('\n')
            var textLines = 0
            while (i < lines.size && lines[i].isNotBlank()) {
                // Treat subtitle text as text, not arbitrary WebVTT markup.
                val line = lines[i++]
                if ("-->" in line) return null
                output.append(line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")).append('\n')
                textLines++
                if (output.length > MAX_INPUT_BYTES * 3) return null
            }
            if (textLines == 0) return null
            output.append('\n')
        }
        if (cues == 0) return null
        return output.toString().toByteArray(Charsets.UTF_8).takeIf { it.size <= MAX_INPUT_BYTES * 3 }
    }

    private fun timestamp(match: MatchResult, offset: Int): Long =
        (((match.groupValues[offset].toLong() * 60 +
            match.groupValues[offset + 1].toLong()) * 60 +
            match.groupValues[offset + 2].toLong()) * 1000 +
            match.groupValues[offset + 3].toLong())

    private fun format(ms: Long): String = String.format(
        Locale.ROOT, "%02d:%02d:%02d.%03d",
        ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000
    )
}
