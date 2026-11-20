package com.vaydelux.bousygram.data.repository

import android.annotation.SuppressLint
import android.content.Context
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.domain.model.MediaItem
import java.io.IOException
import org.json.JSONObject

/** Per-account user corrections. They never change Telegram file or message IDs. */
object TelegramMetadataCorrections {
    private const val MAX_RECORDS = 300
    private val safeImagePath = Regex("""^/[A-Za-z0-9_-]{8,100}\.(?:jpg|png|webp)$""")
    private val lock = Any()

    data class Correction(
        val skip: Boolean = false, val tmdbId: Int = 0, val isSeries: Boolean = false,
        val title: String = "", val year: Int? = null, val overview: String? = null,
        val rating: Float? = null, val posterPath: String? = null,
        val backdropPath: String? = null
    )

    private fun key(item: MediaItem): String? =
        if (item.chatId == 0L || item.messageId <= 0L) null
        else "m_" + item.chatId + "_" + item.messageId

    private fun store(context: Context, owner: AccountSnapshot) =
        context.applicationContext.getSharedPreferences(
            "telegram_metadata_corrections_" + owner.accountId, Context.MODE_PRIVATE
        )

    private fun normalize(value: Correction): Correction? {
        if (value.skip) return Correction(skip = true)
        if (value.tmdbId <= 0 || value.title.isBlank() || value.title.length > 160 ||
            value.year != null && value.year !in 1900..2099) return null
        return value.copy(
            title = value.title.trim(),
            overview = value.overview?.take(1200)?.takeIf(String::isNotBlank),
            rating = value.rating?.takeIf { it.isFinite() && it in 0f..10f },
            posterPath = value.posterPath?.takeIf(safeImagePath::matches),
            backdropPath = value.backdropPath?.takeIf(safeImagePath::matches)
        )
    }

    private fun decode(raw: String): Correction {
        if (raw.length > 3500) throw IOException("Saved correction exceeds limit.")
        try {
            val obj = JSONObject(raw)
            if (obj.optInt("v") != 1) throw IOException("Unsupported metadata correction.")
            val value = Correction(
                skip = obj.optBoolean("skip"), tmdbId = obj.optInt("id"),
                isSeries = obj.optBoolean("tv"), title = obj.optString("title"),
                year = obj.optInt("year", 0).takeIf { it in 1900..2099 },
                overview = obj.optString("overview").takeIf(String::isNotBlank),
                rating = obj.optDouble("rating", -1.0).toFloat()
                    .takeIf { it.isFinite() && it in 0f..10f },
                posterPath = obj.optString("poster").takeIf(String::isNotBlank),
                backdropPath = obj.optString("backdrop").takeIf(String::isNotBlank)
            )
            return normalize(value) ?: throw IOException("Invalid stored metadata.")
        } catch (e: IOException) { throw e }
        catch (e: Exception) { throw IOException("Stored metadata is unreadable.", e) }
    }

    fun read(context: Context, item: MediaItem): Correction? {
        val owner = AccountSession.current() ?: return null
        val id = key(item) ?: return null
        return synchronized(lock) {
            if (!AccountSession.isCurrent(owner)) return@synchronized null
            val value = store(context, owner).getString(id, null) ?: return@synchronized null
            decode(value)
        }
    }

    private fun encode(value: Correction) = JSONObject().apply {
        put("v", 1); put("skip", value.skip); put("id", value.tmdbId)
        put("tv", value.isSeries); put("title", value.title)
        value.year?.let { put("year", it) }
        value.overview?.let { put("overview", it) }
        value.rating?.let { put("rating", it) }
        value.posterPath?.let { put("poster", it) }
        value.backdropPath?.let { put("backdrop", it) }
    }.toString()

    // A correction is an explicit user action, not background telemetry.
    // Callers use Dispatchers.IO; commit() must confirm persistence before
    // the UI claims the selection was saved. apply() cannot report failure.
    @SuppressLint("ApplySharedPref")
    private fun write(context: Context, item: MediaItem, value: Correction?): Boolean {
        val owner = AccountSession.current() ?: return false
        val id = key(item) ?: return false
        return synchronized(lock) {
            val prefs = store(context, owner)
            val previous = prefs.getString(id, null)
            // Do not overwrite corrupted or future-version data silently.
            if (previous != null) decode(previous)
            if (value != null && previous == null &&
                prefs.all.keys.count { it.startsWith("m_") } >= MAX_RECORDS
            ) throw IOException("Correction storage is full. Clear an existing correction.")
            var committed = false
            val current = AccountSession.runIfCurrent(owner) {
                committed = if (value == null) prefs.edit().remove(id).commit()
                else prefs.edit().putString(id, encode(value)).commit()
            }
            current && committed
        }
    }

    fun choose(context: Context, item: MediaItem, candidate: TmdbCorrectionCandidate,
        metadata: TmdbMetadata? = null): Boolean {
        val selected = normalize(Correction(
            tmdbId = candidate.id, isSeries = candidate.isSeries,
            title = metadata?.title?.takeIf(String::isNotBlank) ?: candidate.title,
            year = metadata?.year ?: candidate.year,
            overview = metadata?.overview ?: candidate.overview,
            rating = metadata?.rating ?: candidate.rating,
            posterPath = metadata?.posterPath ?: candidate.posterPath,
            backdropPath = metadata?.backdropPath
        )) ?: return false
        return write(context, item, selected)
    }

    fun skip(context: Context, item: MediaItem): Boolean =
        write(context, item, Correction(skip = true))

    fun clear(context: Context, item: MediaItem): Boolean = write(context, item, null)

    /** Metadata is decoration, not the source of playback or authorization. */
    fun apply(context: Context, item: MediaItem): MediaItem {
        val selected = read(context, item) ?: return item
        if (selected.skip) return item.copy(
            tmdbTitle = null, tmdbOverview = null, tmdbRating = null,
            tmdbPosterPath = null, tmdbBackdropPath = null, episodeTitle = null
        )
        val imagesEnabled = EnterprisePreferences.tmdbExternalDiscoveryEnabled()
        return item.copy(
            tmdbTitle = selected.title, tmdbOverview = selected.overview,
            tmdbRating = selected.rating, releaseYear = selected.year ?: item.releaseYear,
            tmdbPosterPath = selected.posterPath?.takeIf { imagesEnabled }
                ?.let { "https://image.tmdb.org/t/p/w500" + it },
            tmdbBackdropPath = selected.backdropPath?.takeIf { imagesEnabled }
                ?.let { "https://image.tmdb.org/t/p/w1280" + it }
        )
    }
}
