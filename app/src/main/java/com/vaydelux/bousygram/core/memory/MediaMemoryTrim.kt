package com.vaydelux.bousygram.core.memory

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow

/** Android ComponentCallbacks2 levels are non-linear *categories*, not a scale.
 * UI_HIDDEN (20) is not more severe than RUNNING_CRITICAL (15).
 */
enum class MediaMemoryTrim { NONE, MODERATE, CRITICAL }

internal fun mediaMemoryTrim(level: Int): MediaMemoryTrim = when {
    level >= 80 || level == 15 -> MediaMemoryTrim.CRITICAL // COMPLETE / RUNNING_CRITICAL
    level == 5 || level == 10 || level == 40 || level == 60 ->
        MediaMemoryTrim.MODERATE // RUNNING_MODERATE/LOW, BACKGROUND, MODERATE
    else -> MediaMemoryTrim.NONE // UI_HIDDEN=20; preserve useful warm thumbnails
}

/** No activity or ViewModel retained. Background/foreground state is not logged. */
object MediaMemoryPressure {
    private val changes = MutableSharedFlow<MediaMemoryTrim>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = changes.asSharedFlow()
    fun report(severity: MediaMemoryTrim) {
        if (severity != MediaMemoryTrim.NONE) changes.tryEmit(severity)
    }
}

internal data class OffscreenCatalogTrim(
    val snapshots: Int,
    val topics: Int,
    val browsingPositions: Int,
    val focusedTopics: Int
)

/** Keep current on-screen media untouched; trim only dormant metadata. */
internal fun offscreenCatalogBudget(severity: MediaMemoryTrim): OffscreenCatalogTrim = when (severity) {
    MediaMemoryTrim.NONE -> OffscreenCatalogTrim(8, 8, 48, 64)
    MediaMemoryTrim.MODERATE -> OffscreenCatalogTrim(2, 2, 12, 16)
    MediaMemoryTrim.CRITICAL -> OffscreenCatalogTrim(0, 0, 1, 1)
}
