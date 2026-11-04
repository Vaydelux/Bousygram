package com.vaydelux.bousygram.presentation.extras

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasWatchProvidersTest {
    @Test fun regionOptionsHonorDeviceLocaleWithoutUnboundedRegionData() {
        assertEquals("PH", ExtrasWatchProviders.regionChoices("ph").first())
        assertEquals("ZA", ExtrasWatchProviders.regionChoices("za").first())
        assertEquals("PH", ExtrasWatchProviders.regionChoices("").first())
        assertTrue(ExtrasWatchProviders.regionChoices("US").size <= 16)
        assertEquals(ExtrasWatchProviders.regionChoices("US").size,
            ExtrasWatchProviders.regionChoices("US").distinct().size)
    }

    @Test fun justWatchListingsKeepRegionAndMonetizationTypesSeparate() {
        val obj = JSONObject(
            """
            {
              "PH": {
                "link": "https://www.themoviedb.org/movie/123-demo/watch?locale=PH",
                "flatrate": [
                  {"provider_name":"Provider One","logo_path":"/good.png"},
                  {"provider_name":"Provider One","logo_path":"/good.png"}
                ],
                "rent": [{"provider_name":"Provider Two","logo_path":"/other.png"}],
                "free": [{"provider_name":"Public Video","logo_path":"//bad.com/x.png"}]
              },
              "US": {
                "link": "https://malicious.invalid/watch",
                "buy": [{"provider_name":"Store","logo_path":"/store.png"}]
              }
            }
            """.trimIndent()
        )
        val parsed = ExtrasWatchProviders.parse(obj, "PH")
        assertEquals("PH", parsed.first().region)
        assertEquals(3, parsed.first().offers.size)
        assertEquals("Subscription", parsed.first().offers[0].method)
        assertEquals("Rent", parsed.first().offers[2].method)
        assertTrue(parsed.first().offers[0].logo!!.startsWith("https://image.tmdb.org/t/p/w92/"))
        assertNull(parsed.first().offers.first { it.providerName == "Public Video" }.logo)
        assertTrue(parsed.first().tmdbLink!!.contains("/movie/123-demo/watch"))
        assertEquals("US", parsed[1].region)
        assertNull(parsed[1].tmdbLink)
    }

    @Test fun linksMustPointToOfficialTmdbTitlePages() {
        assertEquals("https://www.themoviedb.org/tv/45/watch",
            ExtrasWatchProviders.safeTmdbWatchLink("https://www.themoviedb.org/tv/45/watch"))
        assertNull(ExtrasWatchProviders.safeTmdbWatchLink("https://evil.example.org/movie/1/watch"))
        assertNull(ExtrasWatchProviders.safeTmdbWatchLink("http://www.themoviedb.org/tv/45"))
        assertNull(ExtrasWatchProviders.safeTmdbWatchLink("https://www.themoviedb.org/person/45"))
        assertNull(ExtrasWatchProviders.safeTmdbWatchLink("javascript:alert(1)"))
    }

    @Test fun tmdbAppendUrlKeepsSlashLiteralButEncodesArbitraryValues() {
        val append = ExtrasProviderPolicy.encodeTmdbQueryValue(
            "append_to_response", "credits,videos,watch/providers"
        )
        assertTrue(append.contains("watch/providers"))
        assertFalse(append.contains("watch%2Fproviders"))
        assertTrue(ExtrasProviderPolicy.encodeTmdbQueryValue("query", "a/b").contains("%2F"))
    }

    @Test fun missingProviderDataGracefullyReturnsEmpty() {
        assertTrue(ExtrasWatchProviders.parse(null, "PH").isEmpty())
        assertTrue(ExtrasWatchProviders.parse(JSONObject("{}"), "US").isEmpty())
        assertTrue(ExtrasWatchProviders.parse(JSONObject("{\"US\":{\"flatrate\":[]}}"), "US").isEmpty())
    }
}
