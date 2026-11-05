package com.vaydelux.bousygram.presentation.media.vm

/**
 * Small synchronized access-order map for restoring TV focus after navigation.
 *
 * Activity-scoped MediaViewModel may survive hours of Telegram source switching.
 * Remember only the most recently visited folders; never keep an unbounded
 * ConcurrentHashMap entry for every chat/topic the user has opened.
 */
internal class BoundedBrowsingHistory<K : Any, V : Any>(private val capacity: Int) {
    init { require(capacity > 0) }

    private val entries = LinkedHashMap<K, V>(capacity.coerceAtMost(32), 0.75f, true)

    @Synchronized operator fun get(key: K): V? = entries[key]

    @Synchronized operator fun set(key: K, value: V) {
        entries[key] = value
        evictExcess()
    }

    @Synchronized fun compute(key: K, update: (K, V?) -> V?): V? {
        val value = update(key, entries[key])
        if (value == null) entries.remove(key) else entries[key] = value
        evictExcess()
        return value
    }

    @Synchronized fun clear() = entries.clear()

    @Synchronized fun trimToSize(keepEntries: Int) {
        require(keepEntries >= 0)
        while (entries.size > keepEntries) entries.remove(entries.keys.first())
    }

    @Synchronized fun size(): Int = entries.size

    private fun evictExcess() {
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }
}
