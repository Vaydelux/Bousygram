package com.vaydelux.bousygram.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbQueryPolicyTest {
    @Test fun acceptsCleanMovieAndTvTitles() {
        assertEquals("Movie Name", TmdbQueryPolicy.cleanedTitle(" Movie    Name ", 2025))
        assertEquals("It", TmdbQueryPolicy.cleanedTitle("It", 2017))
        assertEquals("1917", TmdbQueryPolicy.cleanedTitle("1917", 2019))
        assertEquals("Severance", TmdbQueryPolicy.cleanedTitle("Severance", null))
    }

    @Test fun refusesRawPathsCredentialsAndCameraFiles() {
        for (value in listOf(
            "https://example.com/private",
            "C:\\secret\\file.mp4",
            "user@example.com",
            "Movie.Name.2025.mkv",
            "IMG_202601150001.jpg",
            "VID_202601201141.mp4",
            "ab9f414f08caddcd8c223ab911e324bb",
            "0000000000001",
            "ok\napi_key=secret",
            " "
        )) assertNull("Unexpected TMDB query for $value", TmdbQueryPolicy.cleanedTitle(value, null))
    }

    @Test fun networkLookupRequiresExplicitConsent() {
        assertNull(TmdbQueryPolicy.forNetwork(false, "The Matrix", 1999))
        assertEquals("The Matrix", TmdbQueryPolicy.forNetwork(true, "The Matrix", 1999))
        assertNull(TmdbQueryPolicy.forNetwork(true, "VID_20261120", null))
    }

    @Test fun boundsMetadataQueries() {
        assertNull(TmdbQueryPolicy.cleanedTitle("X".repeat(121), null))
        assertNull(TmdbQueryPolicy.cleanedTitle("Example", 1899))
        assertNull(TmdbQueryPolicy.cleanedTitle("Example", 2100))
        assertEquals("Example", TmdbQueryPolicy.cleanedTitle("Example", 2026))
    }
}
