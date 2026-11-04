package com.vaydelux.bousygram.presentation.extras

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
        require(maximumItems > 0)
        // Preserve stable per-provider IDs, and the earlier position of a title
        // when a backend repeats it on adjacent pages.
        val unique = LinkedHashMap<Pair<ExtrasType, Int>, ExtrasItem>()
        for (item in existing) unique[item.type to item.id] = item
        for (item in incoming) unique[item.type to item.id] = item
        val overflow = (unique.size - maximumItems).coerceAtLeast(0)
        return Merge(unique.values.drop(overflow), overflow)
    }
}
