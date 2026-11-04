package com.vaydelux.bousygram.presentation.extras

import android.text.Html
import android.util.LruCache
import com.vaydelux.bousygram.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Extras is public catalog metadata only. Never use Telegram sessions or media IDs here. */
enum class ExtrasType(val label: String, val provider: String) {
    MOVIES("Movies", "TMDB"),
    TV("TV Shows", "TMDB"),
    ANIME("Anime", "AniList")
}

enum class ExtrasSort(val label: String, val tmdbValue: String, val anilistValue: String) {
    POPULAR("Popular", "popularity.desc", "POPULARITY_DESC"),
    TOP_RATED("Top rated", "vote_average.desc", "SCORE_DESC"),
    NEWEST("Newest", "primary_release_date.desc", "START_DATE_DESC"),
    TRENDING("Trending", "popularity.desc", "TRENDING_DESC")
}

data class ExtrasFilters(
    val sort: ExtrasSort = ExtrasSort.POPULAR,
    val genre: String = "All",
    val year: Int? = null,
    val minimumRating: Int = 0
)

data class ExtrasItem(
    val id: Int,
    val type: ExtrasType,
    val title: String,
    val year: String = "",
    val description: String = "",
    val poster: String? = null,
    val rating: String = "",
    val subtitle: String = ""
)

data class ExtrasPage(val items: List<ExtrasItem>, val hasNext: Boolean, val page: Int)

data class ExtrasPerson(val name: String, val role: String, val portrait: String?)
data class ExtrasReview(val author: String, val content: String, val score: String = "")
data class ExtrasSeason(val number: Int, val name: String, val episodeCount: Int)
data class ExtrasEpisode(val number: Int, val title: String, val overview: String, val still: String?)
data class ExtrasDetail(
    val item: ExtrasItem,
    val description: String,
    val genres: List<String>,
    val facts: List<String>,
    val cast: List<ExtrasPerson>,
    val reviews: List<ExtrasReview>,
    val seasons: List<ExtrasSeason>,
    val episodes: List<ExtrasEpisode> = emptyList(),
    val related: List<ExtrasItem> = emptyList(),
    val backdrop: String? = null,
    val youtubeId: String? = null
)

/** Pure routing policy. Provider IDs must never be guessed or interchanged. */
object ExtrasProviderPolicy {
    fun searchPath(type: ExtrasType, hasQuery: Boolean): String = when (type) {
        ExtrasType.MOVIES -> if (hasQuery) "/search/movie" else "/discover/movie"
        ExtrasType.TV -> if (hasQuery) "/search/tv" else "/discover/tv"
        ExtrasType.ANIME -> "/graphql"
    }

    fun youtubeId(id: String?): String? =
        id?.takeIf { it.length in 6..24 && it.matches(Regex("[A-Za-z0-9_-]+")) }

    fun genres(type: ExtrasType): List<Pair<String, String>> = when (type) {
        ExtrasType.MOVIES -> listOf("All" to "", "Action" to "28", "Animation" to "16",
            "Comedy" to "35", "Drama" to "18", "Documentary" to "99",
            "Horror" to "27", "Romance" to "10749", "Sci-Fi" to "878", "Thriller" to "53")
        ExtrasType.TV -> listOf("All" to "", "Action & Adventure" to "10759",
            "Animation" to "16", "Comedy" to "35", "Drama" to "18",
            "Documentary" to "99", "Mystery" to "9648", "Reality" to "10764",
            "Sci-Fi & Fantasy" to "10765")
        ExtrasType.ANIME -> listOf("All" to "", "Action" to "Action", "Adventure" to "Adventure",
            "Comedy" to "Comedy", "Drama" to "Drama", "Fantasy" to "Fantasy",
            "Mystery" to "Mystery", "Romance" to "Romance", "Sci-Fi" to "Sci-Fi",
            "Slice of Life" to "Slice of Life", "Sports" to "Sports", "Supernatural" to "Supernatural")
    }
}

/** Small, bounded HTTP/JSON reader; short-lived cache limits memory on 32-bit TVs. */
object ExtrasCatalogApi {
    private const val TMDB = "https://api.themoviedb.org/3"
    private const val ANILIST = "https://graphql.anilist.co"
    private const val MAX_BYTES = 2_000_000
    private data class Cached(val content: String, val until: Long)
    private val responseCache = object : LruCache<String, Cached>(4096) {
        override fun sizeOf(key: String, value: Cached): Int =
            ((value.content.length * 2L) / 1024L).coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
    @Volatile private var anilistBackoffUntil = 0L

    suspend fun browse(
        type: ExtrasType, query: String, filters: ExtrasFilters, page: Int
    ): ExtrasPage = withContext(Dispatchers.IO) {
        if (type == ExtrasType.ANIME) browseAnime(query, filters, page)
        else browseTmdb(type, query, filters, page)
    }

    suspend fun details(item: ExtrasItem): ExtrasDetail = withContext(Dispatchers.IO) {
        if (item.type == ExtrasType.ANIME) animeDetails(item.id)
        else tmdbDetails(item)
    }

    suspend fun season(showId: Int, number: Int): List<ExtrasEpisode> = withContext(Dispatchers.IO) {
        require(showId > 0 && number >= 0)
        val body = tmdbGet("/tv/$showId/season/$number", emptyMap())
        arrayObjects(body.optJSONArray("episodes")).map {
            ExtrasEpisode(it.optInt("episode_number"), it.optString("name", "Episode"),
                it.optString("overview"), tmdbImage(it.optString("still_path"), "w342"))
        }
    }

    private fun browseTmdb(type: ExtrasType, query: String, f: ExtrasFilters, page: Int): ExtrasPage {
        val searching = query.isNotBlank()
        val genreId = ExtrasProviderPolicy.genres(type).firstOrNull { it.first == f.genre }?.second.orEmpty()
        val params = mutableMapOf(
            "page" to page.coerceIn(1, 500).toString(),
            "language" to "en-US",
            "include_adult" to "false"
        )
        if (searching) {
            params["query"] = query.trim().take(120)
            f.year?.let { params[if (type == ExtrasType.TV) "first_air_date_year" else "year"] = it.toString() }
        } else {
            params["sort_by"] = when {
                f.sort == ExtrasSort.NEWEST && type == ExtrasType.TV -> "first_air_date.desc"
                else -> f.sort.tmdbValue
            }
            if (f.sort == ExtrasSort.TOP_RATED) params["vote_count.gte"] = "100"
            if (genreId.isNotBlank()) params["with_genres"] = genreId
            f.year?.let { params[if (type == ExtrasType.TV) "first_air_date_year" else "primary_release_year"] = it.toString() }
            if (f.minimumRating > 0) params["vote_average.gte"] = f.minimumRating.toString()
        }
        val body = tmdbGet(ExtrasProviderPolicy.searchPath(type, searching), params)
        val results = arrayObjects(body.optJSONArray("results"))
            .mapNotNull { tmdbItem(type, it) }
            // TMDB /search does not accept discover-only filters; apply those locally.
            .filter { !searching || (
                (genreId.isBlank() || containsGenreId(it.id, body.optJSONArray("results"), genreId)) &&
                (f.minimumRating == 0 || (it.rating.toDoubleOrNull() ?: 0.0) >= f.minimumRating)
            ) }
        return ExtrasPage(results, page < body.optInt("total_pages", 1).coerceAtMost(500), page)
    }

    private fun containsGenreId(id: Int, results: JSONArray?, genreId: String): Boolean {
        if (results == null) return false
        for (i in 0 until results.length()) {
            val entry = results.optJSONObject(i) ?: continue
            if (entry.optInt("id") != id) continue
            val genres = entry.optJSONArray("genre_ids") ?: return false
            for (j in 0 until genres.length()) if (genres.optString(j) == genreId) return true
        }
        return false
    }

    private fun tmdbItem(type: ExtrasType, obj: JSONObject): ExtrasItem? {
        val id = obj.optInt("id")
        if (id <= 0) return null
        val tv = type == ExtrasType.TV
        val title = obj.optString(if (tv) "name" else "title")
            .ifBlank { obj.optString(if (tv) "original_name" else "original_title") }
        if (title.isBlank()) return null
        val date = obj.optString(if (tv) "first_air_date" else "release_date")
        val score = obj.optDouble("vote_average", 0.0)
        return ExtrasItem(id, type, title, date.take(4), obj.optString("overview"),
            tmdbImage(obj.optString("poster_path"), "w342"),
            if (score > 0) String.format(java.util.Locale.US, "%.1f", score) else "",
            if (tv) "TV series" else "Movie")
    }

    private fun tmdbDetails(item: ExtrasItem): ExtrasDetail {
        val tv = item.type == ExtrasType.TV
        val namespace = if (tv) "tv" else "movie"
        val obj = tmdbGet("/$namespace/${item.id}", mapOf(
            "language" to "en-US", "append_to_response" to "credits,videos,reviews,recommendations"
        ))
        val current = tmdbItem(item.type, obj) ?: item
        val cast = arrayObjects(obj.optJSONObject("credits")?.optJSONArray("cast")).take(16).map {
            ExtrasPerson(it.optString("name"), it.optString("character"),
                tmdbImage(it.optString("profile_path"), "w185"))
        }
        val reviews = arrayObjects(obj.optJSONObject("reviews")?.optJSONArray("results")).take(6).map {
            ExtrasReview(it.optString("author", "Anonymous"), it.optString("content").take(900))
        }
        val seasons = if (tv) arrayObjects(obj.optJSONArray("seasons")).map {
            ExtrasSeason(it.optInt("season_number"), it.optString("name", "Season"),
                it.optInt("episode_count"))
        } else emptyList()
        val trailer = arrayObjects(obj.optJSONObject("videos")?.optJSONArray("results"))
            .firstOrNull { it.optString("site").equals("YouTube", true) &&
                it.optString("type").equals("Trailer", true) }
            ?: arrayObjects(obj.optJSONObject("videos")?.optJSONArray("results"))
                .firstOrNull { it.optString("site").equals("YouTube", true) }
        val facts = buildList {
            val date = obj.optString(if (tv) "first_air_date" else "release_date")
            if (date.isNotBlank()) add("Released $date")
            if (tv) {
                add("${obj.optInt("number_of_seasons")} seasons")
                add("${obj.optInt("number_of_episodes")} episodes")
                arrayObjects(obj.optJSONArray("networks")).firstOrNull()?.optString("name")
                    ?.takeIf { it.isNotBlank() }?.let { add(it) }
            } else {
                obj.optInt("runtime").takeIf { it > 0 }?.let { add("$it min") }
            }
            obj.optString("status").takeIf { it.isNotBlank() }?.let { add(it) }
            obj.optInt("vote_count").takeIf { it > 0 }?.let { add("$it ratings") }
        }
        return ExtrasDetail(
            item = current,
            description = obj.optString("overview"),
            genres = arrayObjects(obj.optJSONArray("genres")).map { it.optString("name") }.filter(String::isNotBlank),
            facts = facts,
            cast = cast,
            reviews = reviews,
            seasons = seasons,
            related = arrayObjects(obj.optJSONObject("recommendations")?.optJSONArray("results"))
                .mapNotNull { tmdbItem(item.type, it) }.take(12),
            backdrop = tmdbImage(obj.optString("backdrop_path"), "w780"),
            youtubeId = ExtrasProviderPolicy.youtubeId(trailer?.optString("key"))
        )
    }

    private fun browseAnime(query: String, f: ExtrasFilters, page: Int): ExtrasPage {
        val variables = JSONObject()
            .put("page", page.coerceIn(1, 200))
            .put("sort", JSONArray().put(f.sort.anilistValue))
        if (query.isNotBlank()) variables.put("search", query.trim().take(120))
        if (f.genre != "All") variables.put("genre", f.genre)
        f.year?.let { variables.put("year", "$it%") }
        if (f.minimumRating > 0) variables.put("score", f.minimumRating * 10 - 1)
        val gql = """
            query (${'$'}page: Int!, ${'$'}search: String, ${'$'}genre: String, ${'$'}year: String,
                   ${'$'}score: Int, ${'$'}sort: [MediaSort]) {
              Page(page: ${'$'}page, perPage: 20) {
                pageInfo { hasNextPage }
                media(type: ANIME, isAdult: false, search: ${'$'}search, genre: ${'$'}genre,
                      startDate_like: ${'$'}year, averageScore_greater: ${'$'}score, sort: ${'$'}sort) {
                  id title { english romaji native }
                  coverImage { large } seasonYear startDate { year }
                  averageScore format episodes description(asHtml: false)
                }
              }
            }
        """.trimIndent()
        val result = animePost(gql, variables).optJSONObject("data")?.optJSONObject("Page")
            ?: throw IOException("AniList returned no catalog data")
        val items = arrayObjects(result.optJSONArray("media")).mapNotNull(::animeItem)
        return ExtrasPage(items, result.optJSONObject("pageInfo")?.optBoolean("hasNextPage") == true, page)
    }

    private fun animeItem(obj: JSONObject): ExtrasItem? {
        val id = obj.optInt("id")
        if (id <= 0) return null
        val titles = obj.optJSONObject("title")
        val title = titles?.optString("english").orEmpty().ifBlank { titles?.optString("romaji").orEmpty() }
        if (title.isBlank()) return null
        val score = obj.optInt("averageScore")
        return ExtrasItem(id, ExtrasType.ANIME, title,
            (obj.optInt("seasonYear").takeIf { it > 1900 }
                ?: obj.optJSONObject("startDate")?.optInt("year")?.takeIf { it > 1900 })
                ?.toString().orEmpty(),
            plain(obj.optString("description")),
            secureImage(obj.optJSONObject("coverImage")?.optString("large")),
            if (score > 0) "$score/100" else "",
            listOf(obj.optString("format"), obj.optInt("episodes").takeIf { it > 0 }?.let { "$it eps" })
                .filterNotNull().filter(String::isNotBlank).joinToString(" · "))
    }

    private fun animeDetails(id: Int): ExtrasDetail {
        require(id > 0)
        val gql = """
            query (${'$'}id: Int!) {
              Media(id: ${'
                id title { english romaji native }
                description(asHtml: false)
                coverImage { large } bannerImage
                averageScore popularity episodes duration format status season seasonYear isAdult
                startDate { year month day }
                genres
                trailer { id site }
                nextAiringEpisode { episode airingAt }
                characters(page: 1, perPage: 16, sort: [ROLE, RELEVANCE]) {
                  edges { node { id name { full } image { medium } } role }
                }
                staff(page: 1, perPage: 12) { edges { node { name { full } } role } }
                studios(isMain: true) { nodes { name } }
                reviews(page: 1, perPage: 6) {
                  nodes { id summary score user { name } }
                }
                relations { nodes { id type title { english romaji } coverImage { large }
                    averageScore seasonYear format } }
              }
            }
        """.trimIndent()
        val obj = animePost(gql, JSONObject().put("id", id))
            .optJSONObject("data")?.optJSONObject("Media")
            ?: throw IOException("AniList could not load this anime")
        val item = animeItem(obj) ?: throw IOException("Anime entry is incomplete")
        if (obj.optBoolean("isAdult", false)) throw IOException("This catalog entry is unavailable")
        val cast = arrayObjects(obj.optJSONObject("characters")?.optJSONArray("edges")).take(12).mapNotNull {
            val char = it.optJSONObject("node") ?: return@mapNotNull null
            ExtrasPerson(char.optJSONObject("name")?.optString("full").orEmpty(),
                it.optString("role"), secureImage(char.optJSONObject("image")?.optString("medium")))
        }
        val staff = arrayObjects(obj.optJSONObject("staff")?.optJSONArray("edges")).take(8).mapNotNull {
            val person = it.optJSONObject("node") ?: return@mapNotNull null
            ExtrasPerson(person.optJSONObject("name")?.optString("full").orEmpty(),
                it.optString("role"), null)
        }
        val reviewList = arrayObjects(obj.optJSONObject("reviews")?.optJSONArray("nodes")).take(6).map {
            ExtrasReview(it.optJSONObject("user")?.optString("name").orEmpty().ifBlank { "AniList user" },
                plain(it.optString("summary")).take(900),
                it.optInt("score").takeIf { score -> score > 0 }?.let { score -> "$score/100" }.orEmpty())
        }
        val facts = buildList {
            listOf("format", "status", "season").forEach { field ->
                obj.optString(field).takeIf { it.isNotBlank() && it != "null" }?.let { add(it.replace('_', ' ')) }
            }
            obj.optInt("episodes").takeIf { it > 0 }?.let { add("$it episodes") }
            obj.optInt("duration").takeIf { it > 0 }?.let { add("$it min/episode") }
            obj.optInt("popularity").takeIf { it > 0 }?.let { add("$it members") }
            obj.optJSONObject("nextAiringEpisode")?.optInt("episode")?.takeIf { it > 0 }
                ?.let { add("Next airing: episode $it") }
            arrayObjects(obj.optJSONObject("studios")?.optJSONArray("nodes")).firstOrNull()
                ?.optString("name")?.takeIf(String::isNotBlank)?.let { add("Studio: $it") }
        }
        val relations = arrayObjects(obj.optJSONObject("relations")?.optJSONArray("nodes"))
            .filter { it.optString("type") == "ANIME" }.mapNotNull(::animeItem).take(12)
        val trailer = obj.optJSONObject("trailer")?.takeIf { it.optString("site").equals("youtube", true) }
        return ExtrasDetail(
            item = item,
            description = plain(obj.optString("description")),
            genres = buildList {
                val arr = obj.optJSONArray("genres")
                if (arr != null) for (i in 0 until arr.length()) arr.optString(i).takeIf(String::isNotBlank)?.let(::add)
            },
            facts = facts,
            cast = cast + staff,
            reviews = reviewList,
            seasons = emptyList(), // AniList has episode counts / relations, not TMDB-style season episode lists.
            related = relations,
            backdrop = secureImage(obj.optString("bannerImage")),
            youtubeId = ExtrasProviderPolicy.youtubeId(trailer?.optString("id"))
        )
    }

    private fun plain(value: String): String =
        Html.fromHtml(value.take(20_000), Html.FROM_HTML_MODE_COMPACT).toString().trim()

    private fun secureImage(value: String?): String? {
        if (value.isNullOrBlank() || !value.startsWith("https://")) return null
        val host = runCatching { URL(value).host.lowercase(java.util.Locale.ROOT) }.getOrNull() ?: return null
        return value.takeIf { host == "anilist.co" || host.endsWith(".anilist.co") ||
            host == "anili.st" || host.endsWith(".anili.st") }
    }

    private fun tmdbImage(path: String?, size: String): String? {
        if (path.isNullOrBlank() || !path.startsWith("/") || path.startsWith("//") ||
            !path.matches(Regex("/[A-Za-z0-9_./-]+"))) return null
        return "https://image.tmdb.org/t/p/$size$path"
    }

    private fun tmdbGet(path: String, params: Map<String, String>): JSONObject {
        val key = BuildConfig.TMDB_API_KEY.trim()
        if (key.isEmpty() || key == "TMDB_API_KEY_DEFAULT_VALUE") {
            throw IOException("TMDB API key is not configured. Add TMDB_API_KEY to local.properties.")
        }
        val query = (params + ("api_key" to key)).entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }
        // Credentials are not recorded in error messages or logs.
        return requestJson("$TMDB$path?$query", null, ttlMs = 5 * 60_000L)
    }

    private fun animePost(query: String, variables: JSONObject): JSONObject {
        val now = System.currentTimeMillis()
        if (now < anilistBackoffUntil) {
            throw IOException("AniList is rate limiting requests. Retry after the cooldown.")
        }
        return requestJson(ANILIST, JSONObject().put("query", query).put("variables", variables).toString(),
            ttlMs = 5 * 60_000L).also {
            val errors = it.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                throw IOException("AniList rejected the request. Try another filter or retry later.")
            }
        }
    }

    private fun requestJson(url: String, body: String?, ttlMs: Long): JSONObject {
        val cacheKey = if (body == null) url else "$url::$body"
        val now = System.currentTimeMillis()
        synchronized(responseCache) {
            responseCache.get(cacheKey)?.takeIf { it.until > now }?.let {
                return JSONObject(it.content)
            }
        }
        val conn = (URL(url).openConnection() as HttpURLConnection)
        try {
            conn.connectTimeout = 6_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Bousygram/Extras (catalog metadata)")
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = conn.responseCode
            if (status == 429) {
                if (body != null) {
                    val retrySeconds = conn.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(1, 120) ?: 60L
                    anilistBackoffUntil = System.currentTimeMillis() + retrySeconds * 1000L
                }
                throw IOException("Catalog request limit reached. Please retry shortly.")
            }
            if (status !in 200..299) {
                throw IOException(when (status) {
                    401, 403 -> "Catalog provider access denied. Check API access and configuration."
                    404 -> "This title is no longer available."
                    else -> "Catalog request failed (HTTP $status)."
                })
            }
            val data = conn.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > MAX_BYTES) throw IOException("Catalog response exceeded size limit")
                    output.write(buffer, 0, read)
                }
                output.toString(StandardCharsets.UTF_8.name())
            }
            val json = JSONObject(data)
            synchronized(responseCache) {
                responseCache.put(cacheKey, Cached(data, now + ttlMs))
            }
            return json
        } finally {
            conn.disconnect()
        }
    }

    private fun encode(input: String) = URLEncoder.encode(input, StandardCharsets.UTF_8.name())

    private fun arrayObjects(arr: JSONArray?): List<JSONObject> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull(arr::optJSONObject)
    }
}
}id, type: ANIME, isAdult: false) {
                id title { english romaji native }
                description(asHtml: false)
                coverImage { large } bannerImage
                averageScore popularity episodes duration format status season seasonYear isAdult
                startDate { year month day }
                genres
                trailer { id site }
                nextAiringEpisode { episode airingAt }
                characters(page: 1, perPage: 16, sort: [ROLE, RELEVANCE]) {
                  edges { node { id name { full } image { medium } } role }
                }
                staff(page: 1, perPage: 12) { edges { node { name { full } } role } }
                studios(isMain: true) { nodes { name } }
                reviews(page: 1, perPage: 6) {
                  nodes { id summary score user { name } }
                }
                relations { nodes { id type title { english romaji } coverImage { large }
                    averageScore seasonYear format } }
              }
            }
        """.trimIndent()
        val obj = animePost(gql, JSONObject().put("id", id))
            .optJSONObject("data")?.optJSONObject("Media")
            ?: throw IOException("AniList could not load this anime")
        val item = animeItem(obj) ?: throw IOException("Anime entry is incomplete")
        if (obj.optBoolean("isAdult", false)) throw IOException("This catalog entry is unavailable")
        val cast = arrayObjects(obj.optJSONObject("characters")?.optJSONArray("edges")).take(12).mapNotNull {
            val char = it.optJSONObject("node") ?: return@mapNotNull null
            ExtrasPerson(char.optJSONObject("name")?.optString("full").orEmpty(),
                it.optString("role"), secureImage(char.optJSONObject("image")?.optString("medium")))
        }
        val staff = arrayObjects(obj.optJSONObject("staff")?.optJSONArray("edges")).take(8).mapNotNull {
            val person = it.optJSONObject("node") ?: return@mapNotNull null
            ExtrasPerson(person.optJSONObject("name")?.optString("full").orEmpty(),
                it.optString("role"), null)
        }
        val reviewList = arrayObjects(obj.optJSONObject("reviews")?.optJSONArray("nodes")).take(6).map {
            ExtrasReview(it.optJSONObject("user")?.optString("name").orEmpty().ifBlank { "AniList user" },
                plain(it.optString("summary")).take(900),
                it.optInt("score").takeIf { score -> score > 0 }?.let { score -> "$score/100" }.orEmpty())
        }
        val facts = buildList {
            listOf("format", "status", "season").forEach { field ->
                obj.optString(field).takeIf { it.isNotBlank() && it != "null" }?.let { add(it.replace('_', ' ')) }
            }
            obj.optInt("episodes").takeIf { it > 0 }?.let { add("$it episodes") }
            obj.optInt("duration").takeIf { it > 0 }?.let { add("$it min/episode") }
            obj.optInt("popularity").takeIf { it > 0 }?.let { add("$it members") }
            obj.optJSONObject("nextAiringEpisode")?.optInt("episode")?.takeIf { it > 0 }
                ?.let { add("Next airing: episode $it") }
            arrayObjects(obj.optJSONObject("studios")?.optJSONArray("nodes")).firstOrNull()
                ?.optString("name")?.takeIf(String::isNotBlank)?.let { add("Studio: $it") }
        }
        val relations = arrayObjects(obj.optJSONObject("relations")?.optJSONArray("nodes"))
            .filter { it.optString("type") == "ANIME" }.mapNotNull(::animeItem).take(12)
        val trailer = obj.optJSONObject("trailer")?.takeIf { it.optString("site").equals("youtube", true) }
        return ExtrasDetail(
            item = item,
            description = plain(obj.optString("description")),
            genres = buildList {
                val arr = obj.optJSONArray("genres")
                if (arr != null) for (i in 0 until arr.length()) arr.optString(i).takeIf(String::isNotBlank)?.let(::add)
            },
            facts = facts,
            cast = cast + staff,
            reviews = reviewList,
            seasons = emptyList(), // AniList has episode counts / relations, not TMDB-style season episode lists.
            related = relations,
            backdrop = secureImage(obj.optString("bannerImage")),
            youtubeId = ExtrasProviderPolicy.youtubeId(trailer?.optString("id"))
        )
    }

    private fun plain(value: String): String =
        Html.fromHtml(value.take(20_000), Html.FROM_HTML_MODE_COMPACT).toString().trim()

    private fun secureImage(value: String?): String? {
        if (value.isNullOrBlank() || !value.startsWith("https://")) return null
        val host = runCatching { URL(value).host.lowercase(java.util.Locale.ROOT) }.getOrNull() ?: return null
        return value.takeIf { host == "anilist.co" || host.endsWith(".anilist.co") ||
            host == "anili.st" || host.endsWith(".anili.st") }
    }

    private fun tmdbImage(path: String?, size: String): String? {
        if (path.isNullOrBlank() || !path.startsWith("/") || path.startsWith("//") ||
            !path.matches(Regex("/[A-Za-z0-9_./-]+"))) return null
        return "https://image.tmdb.org/t/p/$size$path"
    }

    private fun tmdbGet(path: String, params: Map<String, String>): JSONObject {
        val key = BuildConfig.TMDB_API_KEY.trim()
        if (key.isEmpty() || key == "TMDB_API_KEY_DEFAULT_VALUE") {
            throw IOException("TMDB API key is not configured. Add TMDB_API_KEY to local.properties.")
        }
        val query = (params + ("api_key" to key)).entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }
        // Credentials are not recorded in error messages or logs.
        return requestJson("$TMDB$path?$query", null, ttlMs = 5 * 60_000L)
    }

    private fun animePost(query: String, variables: JSONObject): JSONObject {
        val now = System.currentTimeMillis()
        if (now < anilistBackoffUntil) {
            throw IOException("AniList is rate limiting requests. Retry after the cooldown.")
        }
        return requestJson(ANILIST, JSONObject().put("query", query).put("variables", variables).toString(),
            ttlMs = 5 * 60_000L).also {
            val errors = it.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                throw IOException("AniList rejected the request. Try another filter or retry later.")
            }
        }
    }

    private fun requestJson(url: String, body: String?, ttlMs: Long): JSONObject {
        val cacheKey = if (body == null) url else "$url::$body"
        val now = System.currentTimeMillis()
        synchronized(responseCache) {
            responseCache.get(cacheKey)?.takeIf { it.until > now }?.let {
                return JSONObject(it.content)
            }
        }
        val conn = (URL(url).openConnection() as HttpURLConnection)
        try {
            conn.connectTimeout = 6_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Bousygram/Extras (catalog metadata)")
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = conn.responseCode
            if (status == 429) {
                if (body != null) {
                    val retrySeconds = conn.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(1, 120) ?: 60L
                    anilistBackoffUntil = System.currentTimeMillis() + retrySeconds * 1000L
                }
                throw IOException("Catalog request limit reached. Please retry shortly.")
            }
            if (status !in 200..299) {
                throw IOException(when (status) {
                    401, 403 -> "Catalog provider access denied. Check API access and configuration."
                    404 -> "This title is no longer available."
                    else -> "Catalog request failed (HTTP $status)."
                })
            }
            val data = conn.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > MAX_BYTES) throw IOException("Catalog response exceeded size limit")
                    output.write(buffer, 0, read)
                }
                output.toString(StandardCharsets.UTF_8.name())
            }
            val json = JSONObject(data)
            synchronized(responseCache) {
                responseCache.put(cacheKey, Cached(data, now + ttlMs))
            }
            return json
        } finally {
            conn.disconnect()
        }
    }

    private fun encode(input: String) = URLEncoder.encode(input, StandardCharsets.UTF_8.name())

    private fun arrayObjects(arr: JSONArray?): List<JSONObject> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull(arr::optJSONObject)
    }
}
