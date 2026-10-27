package com.vaydelux.bousygram.data.repository

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.core.media.SeriesMetadataParser
import com.vaydelux.bousygram.domain.model.MediaItem
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Metadata record retrieved from TMDB and stored in disk cache.
 */
data class TmdbMetadata(
    val matched: Boolean,
    val tmdbId: Int = 0,
    val title: String = "",
    val year: Int? = null,
    val overview: String? = null,
    val rating: Float? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val localPosterPath: String? = null,
    val localBackdropPath: String? = null,
    val episodeTitle: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Handles matching video titles against TMDB, caching JSON metadata and downloaded images on disk.
 * Adheres to zero-blocking principle: all network calls occur asynchronously on Dispatchers.IO.
 */
object TmdbRepository {

    private const val TAG = "TmdbRepository"
    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
    private const val TMDB_IMAGE_BASE_POSTER = "https://image.tmdb.org/t/p/w500"
    private const val TMDB_IMAGE_BASE_BACKDROP = "https://image.tmdb.org/t/p/w1280"

    private const val CONNECT_TIMEOUT_MS = 6_000
    private const val READ_TIMEOUT_MS = 10_000

    // In-memory cache to avoid disk reads during fast TV list scrolling
    private val memoryCache = LruCache<String, TmdbMetadata>(200)

    // Positive match cache TTL: 30 days
    const val POSITIVE_CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
    // Negative match cache TTL: 7 days
    const val NEGATIVE_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * Matches a single MediaItem against TMDB, returning an enriched copy if matched,
     * or the original item with extracted season/episode/year if not.
     * Never blocks the UI thread.
     */
    suspend fun enrich(item: MediaItem, context: Context? = null): MediaItem = withContext(Dispatchers.IO) {
        // If already enriched with TMDB poster or backdrop, return immediately
        if (!item.tmdbPosterPath.isNullOrBlank() || !item.tmdbTitle.isNullOrBlank()) {
            return@withContext item
        }

        val parsed = SeriesMetadataParser.parse(
            title = item.title,
            fileName = item.fileName,
            caption = null
        )

        val queryTitle = parsed.cleanTitle?.trim() ?: item.title.trim()
        if (queryTitle.isBlank()) {
            return@withContext item.copy(
                releaseYear = parsed.year ?: item.releaseYear,
                seasonNumber = parsed.seasonNumber ?: item.seasonNumber,
                episodeNumber = parsed.episodeNumber ?: item.episodeNumber
            )
        }

        val isSeries = parsed.isSeries || item.seasonNumber != null || item.episodeNumber != null
        val seasonNum = parsed.seasonNumber ?: item.seasonNumber
        val episodeNum = parsed.episodeNumber ?: item.episodeNumber

        val meta = resolveMetadata(
            title = queryTitle,
            year = parsed.year ?: item.releaseYear,
            isSeries = isSeries,
            seasonNumber = seasonNum,
            episodeNumber = episodeNum,
            context = context
        )

        if (meta != null && meta.matched) {
            val localPoster = meta.localPosterPath
                ?: meta.posterPath?.let { downloadImageToDisk(it, isPoster = true, context = context) }
            val localBackdrop = meta.localBackdropPath
                ?: meta.backdropPath?.let { downloadImageToDisk(it, isPoster = false, context = context) }

            item.copy(
                releaseYear = meta.year ?: parsed.year ?: item.releaseYear,
                seasonNumber = seasonNum,
                episodeNumber = episodeNum,
                tmdbTitle = meta.title,
                tmdbOverview = meta.overview,
                tmdbRating = meta.rating,
                tmdbPosterPath = localPoster ?: meta.posterPath?.let { "$TMDB_IMAGE_BASE_POSTER$it" },
                tmdbBackdropPath = localBackdrop ?: meta.backdropPath?.let { "$TMDB_IMAGE_BASE_BACKDROP$it" },
                seriesTitle = if (isSeries) meta.title else item.seriesTitle,
                episodeTitle = meta.episodeTitle
            )
        } else {
            item.copy(
                releaseYear = parsed.year ?: item.releaseYear,
                seasonNumber = seasonNum,
                episodeNumber = episodeNum
            )
        }
    }

    /**
     * Enriches a list of MediaItems in the background.
     */
    suspend fun enrichList(items: List<MediaItem>, context: Context? = null): List<MediaItem> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext items
        items.map { item -> enrich(item, context) }
    }

    /**
     * Resolves TMDB metadata for a title + year, checking memory cache, disk cache,
     * and querying TMDB if not cached.
     */
    suspend fun resolveMetadata(
        title: String,
        year: Int?,
        isSeries: Boolean,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        context: Context? = null
    ): TmdbMetadata? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.TMDB_API_KEY
        if (apiKey.isBlank() || apiKey == "TMDB_API_KEY_DEFAULT_VALUE") {
            return@withContext null
        }

        val cacheKey = generateCacheKey(title, year, isSeries, seasonNumber, episodeNumber)

        // 1. Check in-memory cache
        val memCached = memoryCache.get(cacheKey)
        if (memCached != null) {
            return@withContext memCached
        }

        // 2. Check disk cache
        val diskCached = readDiskCache(cacheKey, context)
        if (diskCached != null) {
            val ttl = if (diskCached.matched) POSITIVE_CACHE_TTL_MS else NEGATIVE_CACHE_TTL_MS
            if (System.currentTimeMillis() - diskCached.timestamp <= ttl) {
                memoryCache.put(cacheKey, diskCached)
                return@withContext diskCached
            }
        }

        // 3. Query TMDB API
        val result = queryTmdb(title, year, isSeries, seasonNumber, episodeNumber, apiKey)
        val metadataToStore = if (result != null) {
            // Download images to disk cache
            val localPoster = result.posterPath?.let { downloadImageToDisk(it, isPoster = true, context = context) }
            val localBackdrop = result.backdropPath?.let { downloadImageToDisk(it, isPoster = false, context = context) }
            result.copy(
                localPosterPath = localPoster,
                localBackdropPath = localBackdrop
            )
        } else {
            TmdbMetadata(matched = false, title = title, year = year)
        }

        // 4. Save to disk cache and memory cache
        writeDiskCache(cacheKey, metadataToStore, context)
        memoryCache.put(cacheKey, metadataToStore)

        return@withContext metadataToStore
    }

    /**
     * Queries TMDB v3 API for movie or TV show, calling search and details endpoints.
     */
    private fun queryTmdb(
        title: String,
        year: Int?,
        isSeries: Boolean,
        seasonNumber: Int?,
        episodeNumber: Int?,
        apiKey: String
    ): TmdbMetadata? {
        try {
            if (isSeries) {
                // 1. Search TV with year
                val tvWithYear = searchAndDetailTv(title, year, seasonNumber, episodeNumber, apiKey)
                if (tvWithYear != null) return tvWithYear

                // 2. Search TV without year if year was provided
                if (year != null) {
                    val tvWithoutYear = searchAndDetailTv(title, null, seasonNumber, episodeNumber, apiKey)
                    if (tvWithoutYear != null) return tvWithoutYear
                }

                // 3. Fallback to Movie search in case it was a movie named like a series
                return searchAndDetailMovie(title, year, apiKey) ?: searchAndDetailMovie(title, null, apiKey)
            } else {
                // 1. Search Movie with year
                val movieWithYear = searchAndDetailMovie(title, year, apiKey)
                if (movieWithYear != null) return movieWithYear

                // 2. Search Movie without year if year was provided
                if (year != null) {
                    val movieWithoutYear = searchAndDetailMovie(title, null, apiKey)
                    if (movieWithoutYear != null) return movieWithoutYear
                }

                // 3. Fallback to TV search in case it was a TV show without SxxExx
                return searchAndDetailTv(title, year, seasonNumber, episodeNumber, apiKey)
                    ?: searchAndDetailTv(title, null, seasonNumber, episodeNumber, apiKey)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query TMDB for '$title': ${e.message}")
            return null
        }
    }

    private fun searchAndDetailMovie(title: String, year: Int?, apiKey: String): TmdbMetadata? {
        val encodedQuery = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
        val searchUrl = buildString {
            append("$TMDB_BASE_URL/search/movie?")
            append("api_key=").append(apiKey)
            append("&query=").append(encodedQuery)
            append("&include_adult=false")
            if (year != null && year in 1900..2099) {
                append("&year=").append(year)
            }
        }

        val searchJson = executeGet(searchUrl) ?: return null
        val root = JSONObject(searchJson)
        val results = root.optJSONArray("results") ?: return null
        if (results.length() == 0) return null

        val top = results.getJSONObject(0)
        val id = top.optInt("id", 0)
        if (id <= 0) return parseMovieResponse(searchJson)

        // Query movie details endpoint for complete metadata
        val detailsUrl = "$TMDB_BASE_URL/movie/$id?api_key=$apiKey"
        val detailsJson = executeGet(detailsUrl)
        return if (detailsJson != null) {
            parseMovieDetailsResponse(detailsJson)
        } else {
            parseMovieResponse(searchJson)
        }
    }

    private fun searchAndDetailTv(
        title: String,
        year: Int?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        apiKey: String
    ): TmdbMetadata? {
        val encodedQuery = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
        val searchUrl = buildString {
            append("$TMDB_BASE_URL/search/tv?")
            append("api_key=").append(apiKey)
            append("&query=").append(encodedQuery)
            append("&include_adult=false")
            if (year != null && year in 1900..2099) {
                append("&first_air_date_year=").append(year)
            }
        }

        val searchJson = executeGet(searchUrl) ?: return null
        val root = JSONObject(searchJson)
        val results = root.optJSONArray("results") ?: return null
        if (results.length() == 0) return null

        val top = results.getJSONObject(0)
        val id = top.optInt("id", 0)
        if (id <= 0) return parseTvResponse(searchJson)

        // Query TV show details endpoint
        val detailsUrl = "$TMDB_BASE_URL/tv/$id?api_key=$apiKey"
        val detailsJson = executeGet(detailsUrl)
        val showMeta = if (detailsJson != null) {
            parseTvDetailsResponse(detailsJson)
        } else {
            parseTvResponse(searchJson)
        } ?: return null

        // If season and episode are specified, query episode details endpoint for episode title
        if (seasonNumber != null && episodeNumber != null) {
            val epUrl = "$TMDB_BASE_URL/tv/$id/season/$seasonNumber/episode/$episodeNumber?api_key=$apiKey"
            val epJson = executeGet(epUrl)
            if (epJson != null) {
                val epObj = JSONObject(epJson)
                val epTitle = epObj.optString("name", "").takeIf { it.isNotBlank() }
                val epOverview = epObj.optString("overview", "").takeIf { it.isNotBlank() }
                return showMeta.copy(
                    episodeTitle = epTitle,
                    overview = epOverview ?: showMeta.overview
                )
            }
        }

        return showMeta
    }

    private fun parseMovieDetailsResponse(jsonStr: String): TmdbMetadata? {
        val top = JSONObject(jsonStr)
        val id = top.optInt("id", 0)
        val title = top.optString("title", "").ifBlank { top.optString("original_title", "") }
        val overview = top.optString("overview", "").takeIf { it.isNotBlank() }
        val posterPath = top.optString("poster_path", "").takeIf { it.isNotBlank() && it != "null" }
        val backdropPath = top.optString("backdrop_path", "").takeIf { it.isNotBlank() && it != "null" }
        val voteAverage = top.optDouble("vote_average", 0.0).toFloat().takeIf { it > 0f }
        val releaseDate = top.optString("release_date", "")
        val year = releaseDate.take(4).toIntOrNull()

        return TmdbMetadata(
            matched = true,
            tmdbId = id,
            title = title,
            year = year,
            overview = overview,
            rating = voteAverage,
            posterPath = posterPath,
            backdropPath = backdropPath
        )
    }

    private fun parseTvDetailsResponse(jsonStr: String): TmdbMetadata? {
        val top = JSONObject(jsonStr)
        val id = top.optInt("id", 0)
        val title = top.optString("name", "").ifBlank { top.optString("original_name", "") }
        val overview = top.optString("overview", "").takeIf { it.isNotBlank() }
        val posterPath = top.optString("poster_path", "").takeIf { it.isNotBlank() && it != "null" }
        val backdropPath = top.optString("backdrop_path", "").takeIf { it.isNotBlank() && it != "null" }
        val voteAverage = top.optDouble("vote_average", 0.0).toFloat().takeIf { it > 0f }
        val firstAirDate = top.optString("first_air_date", "")
        val year = firstAirDate.take(4).toIntOrNull()

        return TmdbMetadata(
            matched = true,
            tmdbId = id,
            title = title,
            year = year,
            overview = overview,
            rating = voteAverage,
            posterPath = posterPath,
            backdropPath = backdropPath
        )
    }

    private fun parseMovieResponse(jsonStr: String): TmdbMetadata? {
        val root = JSONObject(jsonStr)
        val results = root.optJSONArray("results") ?: return null
        if (results.length() == 0) return null

        val top = results.getJSONObject(0)
        val id = top.optInt("id", 0)
        val title = top.optString("title", "").ifBlank { top.optString("original_title", "") }
        val overview = top.optString("overview", "").takeIf { it.isNotBlank() }
        val posterPath = top.optString("poster_path", "").takeIf { it.isNotBlank() && it != "null" }
        val backdropPath = top.optString("backdrop_path", "").takeIf { it.isNotBlank() && it != "null" }
        val voteAverage = top.optDouble("vote_average", 0.0).toFloat().takeIf { it > 0f }
        val releaseDate = top.optString("release_date", "")
        val year = releaseDate.take(4).toIntOrNull()

        return TmdbMetadata(
            matched = true,
            tmdbId = id,
            title = title,
            year = year,
            overview = overview,
            rating = voteAverage,
            posterPath = posterPath,
            backdropPath = backdropPath
        )
    }

    private fun parseTvResponse(jsonStr: String): TmdbMetadata? {
        val root = JSONObject(jsonStr)
        val results = root.optJSONArray("results") ?: return null
        if (results.length() == 0) return null

        val top = results.getJSONObject(0)
        val id = top.optInt("id", 0)
        val title = top.optString("name", "").ifBlank { top.optString("original_name", "") }
        val overview = top.optString("overview", "").takeIf { it.isNotBlank() }
        val posterPath = top.optString("poster_path", "").takeIf { it.isNotBlank() && it != "null" }
        val backdropPath = top.optString("backdrop_path", "").takeIf { it.isNotBlank() && it != "null" }
        val voteAverage = top.optDouble("vote_average", 0.0).toFloat().takeIf { it > 0f }
        val firstAirDate = top.optString("first_air_date", "")
        val year = firstAirDate.take(4).toIntOrNull()

        return TmdbMetadata(
            matched = true,
            tmdbId = id,
            title = title,
            year = year,
            overview = overview,
            rating = voteAverage,
            posterPath = posterPath,
            backdropPath = backdropPath
        )
    }

    /**
     * Performs a standard GET request with timeouts and returns response body string.
     */
    private fun executeGet(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Bousygram/1.0 (Android TV)")
            }
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } else {
                Log.d(TAG, "TMDB HTTP $responseCode for $urlString")
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "TMDB network error: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private var testCacheDir: File? = null

    fun setTestCacheDir(dir: File?) {
        testCacheDir = dir
    }

    fun downloadImageToDisk(tmdbPath: String, isPoster: Boolean, context: Context?): String? {
        if (tmdbPath.isBlank()) return null
        val cleanPath = tmdbPath.removePrefix("/")
        val base = testCacheDir ?: context?.cacheDir ?: File(System.getProperty("java.io.tmpdir"), "tmdb_test")
        val cacheDir = File(base, "tmdb_images").apply { if (!exists()) mkdirs() }
        val prefix = if (isPoster) "p_" else "b_"
        val safeFileName = prefix + cleanPath.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val targetFile = File(cacheDir, safeFileName)

        if (targetFile.exists() && targetFile.length() > 0L) {
            return targetFile.absolutePath
        }

        val baseUrl = if (isPoster) TMDB_IMAGE_BASE_POSTER else TMDB_IMAGE_BASE_BACKDROP
        val imageUrl = "$baseUrl/$cleanPath"
        val tempFile = File(cacheDir, "$safeFileName.tmp")

        var connection: HttpURLConnection? = null
        return try {
            val url = URL(imageUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "Bousygram/1.0 (Android TV)")
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0L) {
                    if (targetFile.exists()) targetFile.delete()
                    if (tempFile.renameTo(targetFile)) {
                        targetFile.absolutePath
                    } else {
                        tempFile.absolutePath
                    }
                } else {
                    tempFile.delete()
                    null
                }
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            tempFile.delete()
            Log.d(TAG, "Failed downloading image $imageUrl: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    // Disk Cache Implementation (JSON metadata with TTL)

    private fun getMetadataCacheDir(context: Context?): File {
        val base = testCacheDir ?: context?.cacheDir ?: File(System.getProperty("java.io.tmpdir"), "tmdb_test")
        val dir = File(base, "tmdb_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun readDiskCache(cacheKey: String, context: Context?): TmdbMetadata? {
        val file = File(getMetadataCacheDir(context), "$cacheKey.json")
        if (!file.exists()) return null
        return try {
            val text = file.readText(StandardCharsets.UTF_8)
            val json = JSONObject(text)
            val matched = json.optBoolean("matched", false)
            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
            val ttl = if (matched) POSITIVE_CACHE_TTL_MS else NEGATIVE_CACHE_TTL_MS
            if (System.currentTimeMillis() - timestamp > ttl) {
                file.delete()
                return null
            }
            if (!matched) {
                TmdbMetadata(
                    matched = false,
                    title = json.optString("title", ""),
                    year = json.optInt("year", 0).takeIf { it > 0 },
                    timestamp = timestamp
                )
            } else {
                TmdbMetadata(
                    matched = true,
                    tmdbId = json.optInt("tmdbId", 0),
                    title = json.optString("title", ""),
                    year = json.optInt("year", 0).takeIf { it > 0 },
                    overview = json.optString("overview", "").takeIf { it.isNotBlank() },
                    rating = json.optDouble("rating", 0.0).toFloat().takeIf { it > 0f },
                    posterPath = json.optString("posterPath", "").takeIf { it.isNotBlank() },
                    backdropPath = json.optString("backdropPath", "").takeIf { it.isNotBlank() },
                    localPosterPath = json.optString("localPosterPath", "").takeIf {
                        it.isNotBlank() && File(it).exists()
                    },
                    localBackdropPath = json.optString("localBackdropPath", "").takeIf {
                        it.isNotBlank() && File(it).exists()
                    },
                    episodeTitle = json.optString("episodeTitle", "").takeIf { it.isNotBlank() },
                    timestamp = timestamp
                )
            }
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    fun writeDiskCache(cacheKey: String, metadata: TmdbMetadata, context: Context? = null) {
        val file = File(getMetadataCacheDir(context), "$cacheKey.json")
        val tempFile = File(getMetadataCacheDir(context), "$cacheKey.json.tmp")
        try {
            val json = JSONObject().apply {
                put("matched", metadata.matched)
                put("tmdbId", metadata.tmdbId)
                put("title", metadata.title)
                metadata.year?.let { put("year", it) }
                metadata.overview?.let { put("overview", it) }
                metadata.rating?.let { put("rating", it.toDouble()) }
                metadata.posterPath?.let { put("posterPath", it) }
                metadata.backdropPath?.let { put("backdropPath", it) }
                metadata.localPosterPath?.let { put("localPosterPath", it) }
                metadata.localBackdropPath?.let { put("localBackdropPath", it) }
                metadata.episodeTitle?.let { put("episodeTitle", it) }
                put("timestamp", metadata.timestamp)
            }
            tempFile.writeText(json.toString(), StandardCharsets.UTF_8)
            if (file.exists()) file.delete()
            tempFile.renameTo(file)
        } catch (e: Exception) {
            tempFile.delete()
            Log.w(TAG, "Error writing TMDB disk cache: ${e.message}")
        }
    }

    fun generateCacheKey(
        title: String,
        year: Int?,
        isSeries: Boolean,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): String {
        val normalized = "${title.trim().lowercase()}_${year ?: ""}_${if (isSeries) "tv" else "movie"}${if (seasonNumber != null) "_s$seasonNumber" else ""}${if (episodeNumber != null) "_e$episodeNumber" else ""}"
        return md5(normalized)
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun clearMemoryCache() {
        memoryCache.evictAll()
    }
}
