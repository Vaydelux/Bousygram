package com.vaydelux.bousygram.presentation.extras

internal data class ExtrasDiscoveryRailModel(val key: String, val label: String, val items: List<ExtrasItem>)
internal data class ExtrasDiscoveryModel(val featured: Boolean, val rails: List<ExtrasDiscoveryRailModel>) {
    val prefixCount: Int get() = (if (featured) 1 else 0) + rails.size
    fun posterIndex(index: Int): Int = index.coerceAtLeast(0) + prefixCount
}

/** Only existing bounded data; no extra network feeds, guessed ratings or progress. */
internal object ExtrasDiscoveryPolicy {
    const val RAIL_LIMIT = 8
    fun resolve(type: ExtrasType, results: List<ExtrasItem>, library: ExtrasLibrarySnapshot?,
        unfiltered: Boolean, roomy: Boolean): ExtrasDiscoveryModel {
        if (!unfiltered || !roomy || results.isEmpty()) return ExtrasDiscoveryModel(false, emptyList())
        val rails = buildList {
            fun addLocal(key: String, label: String, entries: List<ExtrasLibraryEntry>) {
                val matching = entries.asSequence().filter { it.type == type.name }
                    .distinctBy { it.id }.take(RAIL_LIMIT).map { it.toExtrasItem() }.toList()
                if (matching.isNotEmpty()) add(ExtrasDiscoveryRailModel(key, label, matching))
            }
            addLocal("saved", "Saved titles", library?.saved.orEmpty())
            addLocal("recent", "Recently explored", library?.recent.orEmpty())
            val rated = results.mapNotNull { item ->
                item.rating.toDoubleOrNull()?.takeIf { it.isFinite() && it in 8.0..10.0 }?.let { item to it }
            }.sortedByDescending { it.second }.map { it.first }.distinctBy { it.id }.take(RAIL_LIMIT)
            if (rated.size >= 2) add(ExtrasDiscoveryRailModel("rated", "Highly rated in these results", rated))
        }
        return ExtrasDiscoveryModel(true, rails)
    }
}
