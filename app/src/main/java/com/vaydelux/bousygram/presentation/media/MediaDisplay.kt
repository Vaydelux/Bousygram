package com.vaydelux.bousygram.presentation.media

import com.vaydelux.bousygram.data.repository.formatFileSize
import com.vaydelux.bousygram.domain.model.MediaItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MediaDisplayLabels(
    val title: String,
    val subtitle: String,
    val durationLabel: String,
    val fileSizeLabel: String?
)

fun mediaDisplayLabels(
    item: MediaItem,
    sourceLabel: String,
    durationCalculating: Boolean = false
): MediaDisplayLabels {
    val fileName = item.fileName?.trim()?.takeIf { it.isNotBlank() }
    val rawTitle = item.title.trim()
    val cleanTitle = rawTitle.takeIf { it.isNotBlank() && !isGenericMediaTitle(it) }
    val fallbackTitle = fallbackMediaTitle(item)
    val title = when {
        fileName != null && (cleanTitle == null || cleanTitle == fileName) -> fileName
        cleanTitle != null -> cleanTitle
        fileName != null -> fileName
        else -> fallbackTitle
    }
    val secondaryParts = buildList {
        if (fileName != null && fileName != title) add(fileName)
        sourceLabel.takeIf { it.isNotBlank() }?.let(::add)
    }
    return MediaDisplayLabels(
        title = title,
        subtitle = secondaryParts.joinToString(" • ").ifBlank { sourceLabel },
        durationLabel = durationLabel(item, durationCalculating),
        fileSizeLabel = item.fileSizeBytes.takeIf { it > 0L }?.let(::formatFileSize)
    )
}

fun durationLabel(item: MediaItem, calculating: Boolean = false): String {
    val trustedMs = item.trustedDurationMs.takeIf { it > 0L }
        ?: item.durationSeconds.takeIf { it > 0 }?.times(1000L)
    return when {
        trustedMs != null -> formatDurationMs(trustedMs)
        calculating -> "Calculating"
        else -> "Unknown"
    }
}

fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun fallbackMediaTitle(item: MediaItem): String {
    val date = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        .format(Date(item.date.toLong() * 1000L))
    return "${item.type.name} • $date"
}

private fun isGenericMediaTitle(value: String): Boolean {
    val normalized = value.trim().lowercase(Locale.US)
    return normalized == "video" ||
        normalized == "photo" ||
        normalized == "audio" ||
        normalized == "file" ||
        normalized == "animation" ||
        normalized == "video note" ||
        normalized.startsWith("video - ") ||
        normalized.startsWith("audio - ") ||
        normalized.startsWith("file - ") ||
        normalized.startsWith("animation - ") ||
        normalized.startsWith("video note - ")
}
