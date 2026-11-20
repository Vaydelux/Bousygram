package com.vaydelux.bousygram.presentation.extras

/**
 * Extras has its own local library: TMDB movie ID 42, TMDB TV ID 42 and
 * AniList anime ID 42 must never collide with each other or Telegram IDs.
 * This policy is Android-free so its correctness can be tested on the JVM.
 */
internal data class ExtrasLibraryEntry(
    val type: String,
    val id: Int,
    val title: String,
    val year: String = "",
    val description: String = "",
    val poster: String? = null,
    val rating: String = "",
    val subtitle: String = "",
    val updatedAtMillis: Long = 0L
) {
    val key: String get() = "$type:$id"
}

internal data class ExtrasLibrarySnapshot(
    val saved: List<ExtrasLibraryEntry> = emptyList(),
    val recent: List<ExtrasLibraryEntry> = emptyList()
)

internal object ExtrasPersonalLibraryPolicy {
    const val MAX_SAVED = 150
    const val MAX_RECENT = 24
    const val MAX_DOCUMENT_CHARS = 260_000
    private val allowedTypes = setOf("MOVIES", "TV", "ANIME")

    fun normalize(entry: ExtrasLibraryEntry): ExtrasLibraryEntry? {
        if (entry.type !in allowedTypes || entry.id <= 0 || entry.title.isBlank()) return null
        return entry.copy(
            title = entry.title.trim().replace("\n", " ").take(160),
            year = entry.year.trim().take(16),
            description = entry.description.trim().take(400),
            poster = entry.poster?.trim()?.takeIf { it.startsWith("https://") && it.length <= 512 },
            rating = entry.rating.trim().take(32),
            subtitle = entry.subtitle.trim().take(80),
            updatedAtMillis = entry.updatedAtMillis.coerceAtLeast(0)
        )
    }

    private fun distinctCapped(input: List<ExtrasLibraryEntry>, limit: Int): List<ExtrasLibraryEntry> {
        val seen = HashSet<String>()
        return input.asSequence().mapNotNull(::normalize)
            .filter { seen.add(it.key) }.take(limit).toList()
    }

    fun sanitize(snapshot: ExtrasLibrarySnapshot): ExtrasLibrarySnapshot = ExtrasLibrarySnapshot(
        saved = distinctCapped(snapshot.saved, MAX_SAVED),
        recent = distinctCapped(snapshot.recent, MAX_RECENT)
    )

    fun containsSaved(snapshot: ExtrasLibrarySnapshot, type: String, id: Int): Boolean =
        snapshot.saved.any { it.type == type && it.id == id }

    fun toggleSaved(
        snapshot: ExtrasLibrarySnapshot,
        item: ExtrasLibraryEntry
    ): ExtrasLibrarySnapshot {
        val entry = normalize(item) ?: return snapshot
        val old = snapshot.saved
        // A saved title is explicitly user-owned. Never evict older saves
        // merely because a bounded storage limit has been reached.
        val updated = when {
            old.any { it.key == entry.key } -> old.filterNot { it.key == entry.key }
            old.size >= MAX_SAVED -> return snapshot
            else -> listOf(entry) + old
        }
        return sanitize(snapshot.copy(saved = updated))
    }

    fun markOpened(
        snapshot: ExtrasLibrarySnapshot,
        item: ExtrasLibraryEntry
    ): ExtrasLibrarySnapshot {
        val entry = normalize(item) ?: return snapshot
        return sanitize(snapshot.copy(
            recent = listOf(entry) + snapshot.recent.filterNot { it.key == entry.key }
        ))
    }

    fun removeSaved(snapshot: ExtrasLibrarySnapshot, type: String, id: Int): ExtrasLibrarySnapshot =
        snapshot.copy(saved = snapshot.saved.filterNot { it.type == type && it.id == id })

    fun clearRecent(snapshot: ExtrasLibrarySnapshot): ExtrasLibrarySnapshot =
        snapshot.copy(recent = emptyList())
}
