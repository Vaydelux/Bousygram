package com.vaydelux.bousygram.presentation.extras

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * A bounded, local-only Extras personal library. No Telegram, credentials,
 * video bytes, source URLs, server state, tracking or backend dependencies.
 *
 * Invalid/unsupported stored data is NOT silently replaced by an empty list:
 * this protects the user's library from accidental destruction after an
 * interrupted write or a future schema change.
 */
internal class ExtrasPersonalLibrary(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "extras_personal_library_v1", Context.MODE_PRIVATE
    )
    private val mutex = Mutex()

    suspend fun read(): ExtrasLibrarySnapshot = withContext(Dispatchers.IO) {
        mutex.withLock { decode(prefs.getString(DATA_KEY, null)) }
    }

    suspend fun toggleSaved(item: ExtrasItem): ExtrasLibrarySnapshot =
        update { ExtrasPersonalLibraryPolicy.toggleSaved(it, item.asLibraryEntry()) }

    suspend fun removeSaved(item: ExtrasItem): ExtrasLibrarySnapshot =
        update { ExtrasPersonalLibraryPolicy.removeSaved(it, item.type.name, item.id) }

    suspend fun markOpened(item: ExtrasItem): ExtrasLibrarySnapshot =
        update { ExtrasPersonalLibraryPolicy.markOpened(it, item.asLibraryEntry()) }

    suspend fun clearRecent(): ExtrasLibrarySnapshot =
        update(ExtrasPersonalLibraryPolicy::clearRecent)

    private suspend fun update(
        transform: (ExtrasLibrarySnapshot) -> ExtrasLibrarySnapshot
    ): ExtrasLibrarySnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = decode(prefs.getString(DATA_KEY, null))
            val next = ExtrasPersonalLibraryPolicy.sanitize(transform(current))
            if (current != next && !prefs.edit().putString(DATA_KEY, encode(next)).commit()) {
                throw IOException("Unable to save the Extras library on this device.")
            }
            next
        }
    }

    private fun ExtrasItem.asLibraryEntry() = ExtrasLibraryEntry(
        type = type.name,
        id = id,
        title = title,
        year = year,
        description = description,
        poster = poster,
        rating = rating,
        subtitle = subtitle,
        updatedAtMillis = System.currentTimeMillis()
    )

    private fun encode(snapshot: ExtrasLibrarySnapshot): String {
        fun entryJson(entry: ExtrasLibraryEntry): JSONObject = JSONObject().apply {
            put("type", entry.type)
            put("id", entry.id)
            put("title", entry.title)
            put("year", entry.year)
            put("description", entry.description)
            put("poster", entry.poster ?: JSONObject.NULL)
            put("rating", entry.rating)
            put("subtitle", entry.subtitle)
            put("updatedAtMillis", entry.updatedAtMillis)
        }
        fun items(list: List<ExtrasLibraryEntry>): JSONArray = JSONArray().apply {
            list.forEach { put(entryJson(it)) }
        }
        return JSONObject().apply {
            put("version", 1)
            put("saved", items(snapshot.saved))
            put("recent", items(snapshot.recent))
        }.toString().also {
            if (it.length > ExtrasPersonalLibraryPolicy.MAX_DOCUMENT_CHARS) {
                throw IOException("The Extras library exceeds its storage limit.")
            }
        }
    }

    private fun decode(raw: String?): ExtrasLibrarySnapshot {
        if (raw == null) return ExtrasLibrarySnapshot()
        if (raw.length > ExtrasPersonalLibraryPolicy.MAX_DOCUMENT_CHARS) {
            throw IOException("Stored Extras library is too large; existing data was preserved.")
        }
        try {
            val document = JSONObject(raw)
            if (document.optInt("version", -1) != 1) {
                throw IOException("This Extras library version is not supported; existing data was preserved.")
            }
            fun readRecords(key: String, maximum: Int): List<ExtrasLibraryEntry> {
                val array = document.optJSONArray(key)
                    ?: throw IOException("Stored Extras library is incomplete.")
                if (array.length() > maximum) {
                    throw IOException("Stored Extras library exceeds its entry limit.")
                }
                return (0 until array.length()).map { index ->
                    val obj = array.getJSONObject(index)
                    val entry = ExtrasLibraryEntry(
                        type = obj.getString("type"),
                        id = obj.getInt("id"),
                        title = obj.getString("title"),
                        year = obj.optString("year", ""),
                        description = obj.optString("description", ""),
                        poster = obj.optString("poster").takeUnless { it.isBlank() || it == "null" },
                        rating = obj.optString("rating", ""),
                        subtitle = obj.optString("subtitle", ""),
                        updatedAtMillis = obj.optLong("updatedAtMillis", 0)
                    )
                    ExtrasPersonalLibraryPolicy.normalize(entry)
                        ?: throw IOException("Stored Extras library has an invalid title.")
                }
            }
            return ExtrasPersonalLibraryPolicy.sanitize(ExtrasLibrarySnapshot(
                saved = readRecords("saved", ExtrasPersonalLibraryPolicy.MAX_SAVED),
                recent = readRecords("recent", ExtrasPersonalLibraryPolicy.MAX_RECENT)
            ))
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("The saved Extras library could not be read; existing data was preserved.", e)
        }
    }

    companion object {
        private const val DATA_KEY = "library_json"
    }
}

internal fun ExtrasLibraryEntry.toExtrasItem(): ExtrasItem = ExtrasItem(
    id = id,
    type = ExtrasType.valueOf(type),
    title = title,
    year = year,
    description = description,
    poster = poster,
    rating = rating,
    subtitle = subtitle
)
