package com.vaydelux.bousygram.core.tdlib.server

/** Shared resilient single-byte-range parser used by both local HTTP implementations. */
internal object HttpRangeParser {
    data class Range(val start: Long, val endInclusive: Long, val isPartial: Boolean)

    sealed interface Result {
        data class Valid(val range: Range) : Result
        data object Unsatisfiable : Result
    }

    fun parse(header: String?, fileSize: Long): Result {
        if (fileSize <= 0L) return Result.Unsatisfiable
        if (header == null) return Result.Valid(Range(0L, fileSize - 1L, isPartial = false))
        val value = header.trim()
        if (!value.startsWith("bytes=", ignoreCase = true)) return Result.Unsatisfiable
        var spec = value.substringAfter('=', "").trim()
        // If client sends multiple comma-separated ranges, honor the first range per HTTP progressive specs
        if (spec.contains(',')) {
            spec = spec.substringBefore(',').trim()
        }
        if (spec.isEmpty()) return Result.Unsatisfiable
        val dash = spec.indexOf('-')
        if (dash < 0 || dash != spec.lastIndexOf('-')) return Result.Unsatisfiable
        val startText = spec.substring(0, dash).trim()
        val endText = spec.substring(dash + 1).trim()

        if (startText.isEmpty()) {
            if (endText.isEmpty() || endText.any { !it.isDigit() }) return Result.Unsatisfiable
            val suffixLength = endText.toLongOrNull() ?: return Result.Unsatisfiable
            if (suffixLength <= 0L) return Result.Unsatisfiable
            val start = (fileSize - suffixLength.coerceAtMost(fileSize)).coerceAtLeast(0L)
            return Result.Valid(Range(start, fileSize - 1L, isPartial = true))
        }

        if (startText.any { !it.isDigit() }) return Result.Unsatisfiable
        val start = startText.toLongOrNull() ?: return Result.Unsatisfiable
        if (start < 0L || start >= fileSize) return Result.Unsatisfiable
        if (endText.isEmpty()) {
            return Result.Valid(Range(start, fileSize - 1L, isPartial = true))
        }
        if (endText.any { !it.isDigit() }) return Result.Unsatisfiable
        val requestedEnd = endText.toLongOrNull() ?: return Result.Unsatisfiable
        if (requestedEnd < start) return Result.Unsatisfiable
        return Result.Valid(Range(start, requestedEnd.coerceAtMost(fileSize - 1L), isPartial = true))
    }
}
