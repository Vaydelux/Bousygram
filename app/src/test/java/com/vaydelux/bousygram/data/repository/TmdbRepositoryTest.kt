package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TmdbRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        cacheDir = tempFolder.newFolder("app_cache")
        TmdbRepository.setTestCacheDir(cacheDir)
        TmdbRepository.clearMemoryCache()
    }

    @After
    fun tearDown() {
        TmdbRepository.setTestCacheDir(null)
        TmdbRepository.clearMemoryCache()
    }

    @Test
    fun generatesDeterministicAndCaseInsensitiveCacheKeys() {
        val key1 = TmdbRepository.generateCacheKey("Inception", 2010, false)
        val key2 = TmdbRepository.generateCacheKey("inception", 2010, false)
        val key3 = TmdbRepository.generateCacheKey("Inception", 2011, false)
        val keyTv = TmdbRepository.generateCacheKey("Inception", 2010, true)

        assertEquals("Same title/year/type should produce identical hash", key1, key2)
        assertFalse("Different year should produce different hash", key1 == key3)
        assertFalse("Series vs movie should produce different hash", key1 == keyTv)
    }

    @Test
    fun writesAndReadsMetadataFromDiskCache() {
        val key = TmdbRepository.generateCacheKey("Inception", 2010, false)
        val metadata = TmdbMetadata(
            matched = true,
            tmdbId = 27205,
            title = "Inception",
            year = 2010,
            overview = "A thief who steals corporate secrets through the use of dream-sharing technology.",
            rating = 8.4f,
            posterPath = "/edv5CZvWj09upOsy2Y6IwDhK8bt.jpg",
            backdropPath = "/8ZTVqvKDQ8emSGUEMjsS4yHAwrp.jpg",
            localPosterPath = null,
            localBackdropPath = null
        )

        TmdbRepository.writeDiskCache(key, metadata)

        val retrieved = TmdbRepository.readDiskCache(key, null)
        assertNotNull(retrieved)
        assertTrue(retrieved!!.matched)
        assertEquals(27205, retrieved.tmdbId)
        assertEquals("Inception", retrieved.title)
        assertEquals(2010, retrieved.year)
        assertEquals(8.4f, retrieved.rating!!, 0.01f)
        assertEquals("/edv5CZvWj09upOsy2Y6IwDhK8bt.jpg", retrieved.posterPath)
        assertEquals("/8ZTVqvKDQ8emSGUEMjsS4yHAwrp.jpg", retrieved.backdropPath)
        assertEquals("A thief who steals corporate secrets through the use of dream-sharing technology.", retrieved.overview)
    }

    @Test
    fun writesAndReadsNegativeMatchFromDiskCache() {
        val key = TmdbRepository.generateCacheKey("RandomFamilyVideo", null, false)
        val negativeMeta = TmdbMetadata(
            matched = false,
            title = "RandomFamilyVideo",
            year = null
        )

        TmdbRepository.writeDiskCache(key, negativeMeta)

        val retrieved = TmdbRepository.readDiskCache(key, null)
        assertNotNull(retrieved)
        assertFalse(retrieved!!.matched)
        assertEquals("RandomFamilyVideo", retrieved.title)
        assertNull(retrieved.posterPath)
    }

    @Test
    fun enrichReturnsEarlyWhenAlreadyEnriched() = runBlocking {
        val alreadyEnriched = MediaItem(
            chatId = 100L,
            messageId = 1L,
            date = 1000,
            type = MediaType.Video,
            title = "Inception",
            fileId = 10,
            tmdbTitle = "Inception",
            tmdbPosterPath = "/path/to/local_poster.jpg"
        )

        val result = TmdbRepository.enrich(alreadyEnriched)
        assertEquals("/path/to/local_poster.jpg", result.tmdbPosterPath)
        assertEquals("Inception", result.tmdbTitle)
    }

    @Test
    fun enrichParsesYearAndPreservesTelegramFallbackWhenNoApiKey() = runBlocking {
        val rawItem = MediaItem(
            chatId = 100L,
            messageId = 2L,
            date = 1000,
            type = MediaType.Video,
            title = "The.Matrix.1999.1080p.mkv",
            fileId = 20,
            thumbnailPath = "/tg_cache/thumb_20.jpg",
            miniThumbnailBytes = byteArrayOf(1, 2, 3),
            fileName = "The.Matrix.1999.1080p.mkv"
        )

        val result = TmdbRepository.enrich(rawItem)

        // Year parsed from filename
        assertEquals(1999, result.releaseYear)
        // Fallback to Telegram thumbnail remains intact
        assertEquals("/tg_cache/thumb_20.jpg", result.thumbnailPath)
        assertNotNull(result.miniThumbnailBytes)
    }

    @Test
    fun enrichParsesSeasonAndEpisodeWhenNoApiKey() = runBlocking {
        val rawItem = MediaItem(
            chatId = 100L,
            messageId = 3L,
            date = 1000,
            type = MediaType.Video,
            title = "Breaking.Bad.S03E07.One.Minute.720p.mkv",
            fileId = 30,
            fileName = "Breaking.Bad.S03E07.One.Minute.720p.mkv"
        )

        val result = TmdbRepository.enrich(rawItem)

        assertEquals(3, result.seasonNumber)
        assertEquals(7, result.episodeNumber)
    }
}
