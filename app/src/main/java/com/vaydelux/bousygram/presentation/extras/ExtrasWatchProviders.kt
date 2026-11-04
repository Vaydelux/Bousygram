package com.vaydelux.bousygram.presentation.extras

import org.json.JSONObject
import java.net.URI
import java.util.Locale

/** TMDB/JustWatch provider listings are metadata, not playable streams or deep links. */
data class ExtrasWatchOffer(
    val providerName: String,
    val method: String,
    val logo: String?
)

data class ExtrasWatchAvailability(
    val region: String,
    val tmdbLink: String?,
    val offers: List<ExtrasWatchOffer>
)

internal object ExtrasWatchProviders {
    private val commonRegions = listOf(
        "PH", "US", "GB", "JP", "KR", "SG", "AU", "CA",
        "IN", "FR", "DE", "ES", "IT", "BR", "MX"
    )

    /** Keep parsing bounded on 32-bit devices while honoring the device's country first. */
    fun regionChoices(deviceCountry: String): List<String> {
        val first = deviceCountry.uppercase(Locale.ROOT)
            .takeIf { it.matches(Regex("[A-Z]{2}")) }
        return (listOfNotNull(first) + commonRegions).distinct().take(16)
    }

    fun safeTmdbWatchLink(raw: String?): String? {
        val safe = ExtrasProviderPolicy.safeExternalHttps(raw) ?: return null
        val uri = runCatching { URI(safe) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val path = uri.path ?: return null
        if (host !in setOf("www.themoviedb.org", "themoviedb.org")) return null
        if (!path.startsWith("/movie/") && !path.startsWith("/tv/")) return null
        return safe
    }

    fun parse(results: JSONObject?, deviceCountry: String): List<ExtrasWatchAvailability> {
        if (results == null) return emptyList()
        val buckets = listOf(
            "flatrate" to "Subscription",
            "free" to "Free",
            "ads" to "Free with ads",
            "rent" to "Rent",
            "buy" to "Buy"
        )
        return regionChoices(deviceCountry).mapNotNull { region ->
            val source = results.optJSONObject(region) ?: return@mapNotNull null
            val offers = buckets.flatMap { (field, method) ->
                val entries = source.optJSONArray(field) ?: return@flatMap emptyList()
                (0 until minOf(entries.length(), 24)).mapNotNull { index ->
                    val entry = entries.optJSONObject(index) ?: return@mapNotNull null
                    val name = entry.optString("provider_name").take(80)
                    if (name.isBlank()) return@mapNotNull null
                    val path = entry.optString("logo_path")
                    val logo = path.takeIf {
                        it.startsWith("/") && !it.startsWith("//") &&
                            it.matches(Regex("/[A-Za-z0-9_./-]+"))
                    }?.let { "https://image.tmdb.org/t/p/w92$it" }
                    ExtrasWatchOffer(name, method, logo)
                }
            }.distinctBy { it.method to it.providerName }.take(20)
            if (offers.isEmpty()) return@mapNotNull null
            ExtrasWatchAvailability(
                region = region,
                tmdbLink = safeTmdbWatchLink(source.optString("link")),
                offers = offers
            )
        }
    }
}
