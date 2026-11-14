package com.vaydelux.bousygram.ui.adaptive

/**
 * Layout decisions in density-independent pixels. Keep device classification separate
 * from orientation: a short landscape phone is not an Android TV.
 */
enum class NavigationMode { BOTTOM_BAR, RAIL, TV_RAIL }
enum class WindowClass { COMPACT, MEDIUM, EXPANDED }
enum class BackgroundPattern { NONE, FINE_GRID, DOTS, SOFT_GRADIENT }

data class AdaptiveUiPolicy(
    val windowClass: WindowClass,
    val navigationMode: NavigationMode,
    val contentGutterDp: Int,
    val mediaColumns: Int,
    val compactHeader: Boolean,
    val searchHistoryInitiallyExpanded: Boolean,
    val allowDecorativePatterns: Boolean,
)

object AdaptiveUi {
    fun resolve(
        widthDp: Int,
        heightDp: Int,
        isTelevision: Boolean,
        isHighContrast: Boolean = false,
        reduceDecorations: Boolean = false,
    ): AdaptiveUiPolicy {
        require(widthDp > 0 && heightDp > 0) { "Window dimensions must be positive" }
        val size = when {
            widthDp < 600 -> WindowClass.COMPACT
            widthDp < 840 -> WindowClass.MEDIUM
            else -> WindowClass.EXPANDED
        }
        val short = heightDp < 480
        val nav = when {
            isTelevision -> NavigationMode.TV_RAIL
            // Width-only classification misidentifies short landscape phones as tablets.
            // Keep their full width for the content instead of inserting a rail.
            short -> NavigationMode.BOTTOM_BAR
            size == WindowClass.EXPANDED -> NavigationMode.RAIL
            size == WindowClass.MEDIUM && !short && widthDp >= 720 -> NavigationMode.RAIL
            else -> NavigationMode.BOTTOM_BAR
        }
        val gutter = when {
            isTelevision -> 32
            size == WindowClass.COMPACT -> 16
            size == WindowClass.MEDIUM -> 24
            else -> 32
        }
        // Target a readable card width while avoiding a huge number of columns on TV.
        val usableWidth = (widthDp - 2 * gutter - if (nav != NavigationMode.BOTTOM_BAR) 88 else 0)
            .coerceAtLeast(1)
        val minCardWidth = if (isTelevision) 220 else 160
        val columns = ((usableWidth + 12) / (minCardWidth + 12)).coerceIn(1, if (isTelevision) 6 else 5)
        return AdaptiveUiPolicy(
            windowClass = size,
            navigationMode = nav,
            contentGutterDp = gutter,
            mediaColumns = columns,
            compactHeader = short && !isTelevision,
            searchHistoryInitiallyExpanded = false,
            allowDecorativePatterns = !isHighContrast && !reduceDecorations,
        )
    }

    fun effectivePattern(
        selected: BackgroundPattern,
        isHighContrast: Boolean,
        reduceDecorations: Boolean,
    ): BackgroundPattern = if (isHighContrast || reduceDecorations) BackgroundPattern.NONE else selected
}

/** Distinguishes unloaded content from a confirmed empty source. */
sealed interface MediaContentState {
    data object Idle : MediaContentState
    data object Loading : MediaContentState
    data class Loaded(val count: Int) : MediaContentState {
        init { require(count >= 0) }
    }
    data class Failed(val message: String) : MediaContentState
}

fun MediaContentState.isConfirmedEmpty(): Boolean =
    this is MediaContentState.Loaded && count == 0
