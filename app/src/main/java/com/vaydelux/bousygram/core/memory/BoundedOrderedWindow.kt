package com.vaydelux.bousygram.core.memory

/** First-seen order, latest payload, and only the retained window holds payload references. */
internal data class BoundedWindow<T>(val items: List<T>, val removedFromFront: Int)

internal fun <T, K> mergeBoundedOrderedWindow(
    existing: Iterable<T>,
    incoming: Iterable<T>,
    maximumItems: Int,
    keyOf: (T) -> K
): BoundedWindow<T> {
    require(maximumItems > 0)
    val retained = LinkedHashMap<K, T>()
    // Only identities, not media objects, survive eviction within this merge.
    val evicted = HashSet<K>()
    fun append(item: T) {
        val key = keyOf(item)
        if (retained.containsKey(key)) {
            retained[key] = item
            return
        }
        if (key in evicted) return
        retained[key] = item
        if (retained.size > maximumItems) {
            val iterator = retained.entries.iterator()
            val oldest = iterator.next().key
            iterator.remove()
            evicted.add(oldest)
        }
    }
    existing.forEach(::append)
    incoming.forEach(::append)
    return BoundedWindow(retained.values.toList(), evicted.size)
}
