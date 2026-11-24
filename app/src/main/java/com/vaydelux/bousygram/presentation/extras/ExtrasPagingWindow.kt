package com.vaydelux.bousygram.presentation.extras

import com.vaydelux.bousygram.core.memory.mergeBoundedOrderedWindow

/** Pure tab state: independent queries/filters with independently bounded results. */
internal data class TabState(
    val query: String = "",
    val filters: ExtrasFilters = ExtrasFilters(),
    val results: List<ExtrasItem> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    /** Number of earlier unique posters discarded to keep paging state bounded. */
    val droppedResults: Int = 0,
    val loading: Boolean = false,
    val error: String? = null
)

/** Reclaim dormant poster metadata without losing the tab's filters or query.
 * Page zero rehydrates when the tab becomes active; a stale page cursor must
 * never render an empty grid permanently after a memory trim.
 */
internal fun TabState.releaseDormantPosters(): TabState {
    // OS trim events can arrive repeatedly while the tab stays inactive.
    // Preserve identity and avoid allocating a new snapshot if already empty.
    if (results.isEmpty() && page == 0 && hasMore &&
        droppedResults == 0 && !loading && error == null
    ) return this
    return copy(results = emptyList(), page = 0, hasMore = true,
        droppedResults = 0, loading = false, error = null)
}

/**
 * A bounded in-memory viewport for infinite-style catalog paging.
 *
 * Pages continue to be fetched in order, but only recent unique posters stay
 * strongly referenced by Compose state. Older pages can be revisited by the
 * explicit Start from first page action, rather than silently re-fetched.
 *
 * This is a *state budget*, not a claim about measured Java heap or GPU PSS.
 * Coil manages its own bounded image caches independently of this list.
 */
internal object ExtrasPagingWindow {
    const val LOW_RAM_MAX_ITEMS = 120
    const val STANDARD_MAX_ITEMS = 240

    /** Saved tab selections may outlive tabs added/removed in a newer build. */
    fun activeTabIndex(saved: Int): Int =
        saved.coerceIn(0, ExtrasType.entries.lastIndex)

    /** Match the visible tab before releasing any dormant catalog window. */
    fun shouldReleaseDormantPosters(activeTab: Int, candidate: ExtrasType): Boolean =
        activeTabIndex(activeTab) != candidate.ordinal

    data class Merge(
        val items: List<ExtrasItem>,
        val removedFromFront: Int
    )

    fun itemLimit(lowMemory: Boolean): Int =
        if (lowMemory) LOW_RAM_MAX_ITEMS else STANDARD_MAX_ITEMS

    fun merge(
        existing: List<ExtrasItem>,
        incoming: List<ExtrasItem>,
        maximumItems: Int
    ): Merge {
        val bounded = mergeBoundedOrderedWindow(existing, incoming, maximumItems) {
            it.type to it.id
        }
        return Merge(bounded.items, bounded.removedFromFront)
    }
}

/**
 * Return from details after the visible bounded poster window may have changed.
 * A missing selection falls back to a visible card; an empty/loading result
 * defers focus until the grid is composed again.
 */
internal data class ExtrasPosterReturnTarget(
    val index: Int,
    val selectedPosterVisible: Boolean
)

internal object ExtrasPosterReturnPolicy {
    fun resolve(selectedId: Int?, results: List<ExtrasItem>): ExtrasPosterReturnTarget? {
        if (results.isEmpty()) return null
        val selectedIndex = if (selectedId == null) -1
            else results.indexOfFirst { it.id == selectedId }
        return if (selectedIndex >= 0) {
            ExtrasPosterReturnTarget(selectedIndex, selectedPosterVisible = true)
        } else {
            ExtrasPosterReturnTarget(index = 0, selectedPosterVisible = false)
        }
    }
}
