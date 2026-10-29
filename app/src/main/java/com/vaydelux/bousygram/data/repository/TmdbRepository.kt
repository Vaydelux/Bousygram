package com.vaydelux.bousygram.data.repository

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.media.SeriesMetadataParser
import com.vaydelux.bousygram.domain.model.MediaItem
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CancellationException
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
    private const val MAX_METADATA_BYTES = 1_048_576
    private const val MAX_IMAGE_BYTES = 8_388_608

    private data class CacheScope(val account: AccountSnapshot, val root: File)

    private fun cacheScope(context: Context?): CacheScope? {
        val account = AccountSession.current() ?: return null
        val base = testCacheDir ?: context?.cacheDir ?: return null
        return CacheScope(account, File(base, "accounts/${account.accountId}"))
    }

    private fun ensureCurrent(account: AccountSnapshot) {
        if (!AccountSession.isCurrent(account)) throw CancellationException("Account session ended")
    }

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
        val account = AccountSession.current() ?: return@withContext item
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
        ensureCurrent(account)

        if (meta != null && meta.matched) {
            val localPoster = meta.localPosterPath
                ?: meta.posterPath?.let { downloadImageToDisk(it, isPoster = true, context = context) }
            val localBackdrop = meta.localBackdropPath
                ?: meta.backdropPath?.let { downloadImageToDisk(it, isPoster = false, context = context) }
            ensureCurrent(account)

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
        val scope = cacheScope(context) ?: return@withContext null
        val apiKey = BuildConfig.TMDB_API_KEY
        if (apiKey.isBlank() || apiKey == "TMDB_API_KEY_DEFAULT_VALUE") {
            return@withContext null
        }

        val cacheKey = generateCacheKey(title, year, isSeries, seasonNumber, episodeNumber)
        val memoryKey = "${scope.account.accountId}:$cacheKey"

        // 1. Check in-memory cache
        val memCached = memoryCache.get(memoryKey)
        if (memCached != null && isFresh(memCached)) {
            ensureCurrent(scope.account)
            return@withContext memCached
        }
        memoryCache.remove(memoryKey)

        // 2. Check disk cache
        val diskCached = readDiskCache(cacheKey, scope)
        if (diskCached != null) {
            val ttl = if (diskCached.matched) POSITIVE_CACHE_TTL_MS else NEGATIVE_CACHE_TTL_MS
            if (System.currentTimeMillis() - diskCached.timestamp <= ttl) {
                ensureCurrent(scope.account)
                memoryCache.put(memoryKey, diskCached)
                return@withContext diskCached
            }
        }

        // 3. Query TMDB API
        val result = queryTmdb(title, year, isSeries, seasonNumber, episodeNumber, apiKey)
        ensureCurrent(scope.account)
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
        ensureCurrent(scope.account)
        writeDiskCache(cacheKey, metadataToStore, scope)
        memoryCache.put(memoryKey, metadataToStore)

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
            return if (isSeries) {
                searchAndDetailTv(title, year, seasonNumber, episodeNumber, apiKey)
            } else {
                searchAndDetailMovie(title, year, apiKey)
            }
        } catch (e: Exception) {
            Log.w(TAG, "TMDB metadata lookup failed (${e.javaClass.simpleName})")
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
        val top = selectMatchingResult(searchJson, title, year, isSeries = false) ?: return null
        val id = top.optInt("id", 0)

        // Query movie details endpoint for complete metadata
        val detailsUrl = "$TMDB_BASE_URL/movie/$id?api_key=$apiKey"
        val detailsJson = executeGet(detailsUrl)
        return if (detailsJson != null) {
            parseMovieDetailsResponse(detailsJson)
        } else {
            parseMovieDetailsResponse(top.toString())
        }
    }

    /** Ambiguous remakes and unrelated first results must retain Telegram's own metadata. */
    internal fun selectMatchingResult(json: String, title: String, year: Int?, isSeries: Boolean): JSONObject? {
        if (json.length > MAX_METADATA_BYTES) return null
        val results = JSONObject(json).optJSONArray("results") ?: return null
        val titleKey = normalizeTitle(title)
        if (titleKey.isBlank()) return null
        val titleField = if (isSeries) "name" else "title"
        val originalField = if (isSeries) "original_name" else "original_title"
        val dateField = if (isSeries) "first_air_date" else "release_date"
        val matches = (0 until results.length()).mapNotNull { index ->
            val candidate = results.optJSONObject(index) ?: return@mapNotNull null
            if (candidate.optInt("id", 0) <= 0) return@mapNotNull null
            val sameTitle = listOf(titleField, originalField).any {
                normalizeTitle(candidate.optString(it)) == titleKey
            }
            val sameYear = year == null || candidate.optString(dateField).take(4).toIntOrNull() == year
            candidate.takeIf { sameTitle && sameYear }
        }.distinctBy { it.optInt("id") }
        return matches.singleOrNull()
    }

    private fun normalizeTitle(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .replace("[^\\p{L}\\p{N}]+".toRegex(), " ")
        .trim()

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
        val top = selectMatchingResult(searchJson, title, year, isSeries = true) ?: return null
        val id = top.optInt("id", 0)

        // Query TV show details endpoint
        val detailsUrl = "$TMDB_BASE_URL/tv/$id?api_key=$apiKey"
        val detailsJson = executeGet(detailsUrl)
        val showMeta = if (detailsJson != null) {
            parseTvDetailsResponse(detailsJson)
        } else {
            parseTvDetailsResponse(top.toString())
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
                connection.inputStream.use { String(readBounded(it, MAX_METADATA_BYTES), StandardCharsets.UTF_8) }
            } else {
                Log.d(TAG, "TMDB returned HTTP $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "TMDB network error (${e.javaClass.simpleName})")
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
        val scope = cacheScope(context) ?: return null
        val cleanPath = tmdbPath.removePrefix("/")
        if (!cleanPath.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9]+".toRegex())) return null
        val cacheDir = File(scope.root, "tmdb_images").apply { if (!exists()) mkdirs() }
        val prefix = if (isPoster) "p_" else "b_"
        val safeFileName = prefix + cleanPath.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val targetFile = File(cacheDir, safeFileName)

        if (targetFile.exists() && targetFile.length() > 0L) {
            ensureCurrent(scope.account)
            return targetFile.absolutePath
        }

        val baseUrl = if (isPoster) TMDB_IMAGE_BASE_POSTER else TMDB_IMAGE_BASE_BACKDROP
        val imageUrl = "$baseUrl/$cleanPath"
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
                val bytes = connection.inputStream.use { readBounded(it, MAX_IMAGE_BYTES) }
                if (bytes.isEmpty()) return null
                ensureCurrent(scope.account)
                writeAtomically(targetFile, bytes)
                ensureCurrent(scope.account)
                targetFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d(TAG, "TMDB image download failed (${e.javaClass.simpleName})")
            null
        } finally {
            connection?.disconnect()
        }
    }

    // Disk Cache Implementation (JSON metadata with TTL)

    private fun getMetadataCacheDir(scope: CacheScope): File {
        val dir = File(scope.root, "tmdb_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun readDiskCache(cacheKey: String, context: Context?): TmdbMetadata? {
        val scope = cacheScope(context) ?: return null
        return readDiskCache(cacheKey, scope)
    }

    private fun readDiskCache(cacheKey: String, scope: CacheScope): TmdbMetadata? {
        if (!cacheKey.matches("[a-f0-9]{32}".toRegex())) return null
        ensureCurrent(scope.account)
        val file = File(getMetadataCacheDir(scope), "$cacheKey.json")
        if (!file.exists()) return null
        return try {
            val text = file.inputStream().use { String(readBounded(it, MAX_METADATA_BYTES), StandardCharsets.UTF_8) }
            val json = JSONObject(text)
            val matched = json.optBoolean("matched", false)
            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
            val ttl = if (matched) POSITIVE_CACHE_TTL_MS else NEGATIVE_CACHE_TTL_MS
            if (timestamp <= 0 || timestamp > System.currentTimeMillis() || System.currentTimeMillis() - timestamp > ttl) {
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
        val scope = cacheScope(context) ?: return
        writeDiskCache(cacheKey, metadata, scope)
    }

    private fun writeDiskCache(cacheKey: String, metadata: TmdbMetadata, scope: CacheScope) {
        if (!cacheKey.matches("[a-f0-9]{32}".toRegex())) return
        ensureCurrent(scope.account)
        val file = File(getMetadataCacheDir(scope), "$cacheKey.json")
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
            val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
            require(bytes.size <= MAX_METADATA_BYTES) { "Metadata exceeds cache limit" }
            ensureCurrent(scope.account)
            writeAtomically(file, bytes)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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

    private fun isFresh(metadata: TmdbMetadata): Boolean {
        val now = System.currentTimeMillis()
        val ttl = if (metadata.matched) POSITIVE_CACHE_TTL_MS else NEGATIVE_CACHE_TTL_MS
        return metadata.timestamp > 0 && metadata.timestamp <= now && now - metadata.timestamp <= ttl
    }

    internal fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) throw IOException("No progress reading metadata response")
            if (count > maxBytes - output.size()) throw IOException("Metadata response exceeds limit")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temporary = File.createTempFile("${target.name}-", ".tmp", target.parentFile)
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }
}
