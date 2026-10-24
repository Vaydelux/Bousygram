package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerBufferConfigTest {

    @Test
    fun exoPlayerStandardBufferConstantsMatchUserSpec() {
        assertEquals(30_000, PlayerBufferConfig.EXO_DEFAULT_MIN_BUFFER_MS)
        assertEquals(120_000, PlayerBufferConfig.EXO_DEFAULT_MAX_BUFFER_MS)
        assertEquals(2_500, PlayerBufferConfig.EXO_DEFAULT_BUFFER_FOR_PLAYBACK_MS)
        assertEquals(5_000, PlayerBufferConfig.EXO_DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
        assertEquals(30_000, PlayerBufferConfig.EXO_DEFAULT_BACK_BUFFER_MS)
        assertTrue(PlayerBufferConfig.EXO_RETAIN_BACK_BUFFER_FROM_KEYFRAME)

        // Verify aliases
        assertEquals(30_000, PlayerBufferConfig.EXO_MIN_BUFFER_MS)
        assertEquals(120_000, PlayerBufferConfig.EXO_MAX_BUFFER_MS)
        assertEquals(2_500, PlayerBufferConfig.EXO_BUFFER_FOR_PLAYBACK_MS)
        assertEquals(5_000, PlayerBufferConfig.EXO_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
        assertEquals(30_000, PlayerBufferConfig.EXO_BACK_BUFFER_MS)
    }

    @Test
    fun exoPlayerLowRamBufferConstantsAreTunedForLowEndHardware() {
        assertEquals(15_000, PlayerBufferConfig.EXO_LOW_RAM_MIN_BUFFER_MS)
        assertEquals(45_000, PlayerBufferConfig.EXO_LOW_RAM_MAX_BUFFER_MS)
        assertEquals(2_500, PlayerBufferConfig.EXO_LOW_RAM_BUFFER_FOR_PLAYBACK_MS)
        assertEquals(5_000, PlayerBufferConfig.EXO_LOW_RAM_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
        assertEquals(10_000, PlayerBufferConfig.EXO_LOW_RAM_BACK_BUFFER_MS)
    }

    @Test
    fun mpvBufferConstantsMatchUserSpec() {
        assertEquals("yes", PlayerBufferConfig.MPV_CACHE_ENABLED)
        assertEquals(150L * 1024L * 1024L, PlayerBufferConfig.MPV_TV_DEMUXER_MAX_BYTES)
        assertEquals(64L * 1024L * 1024L, PlayerBufferConfig.MPV_PHONE_DEMUXER_MAX_BYTES)
        assertEquals(32L * 1024L * 1024L, PlayerBufferConfig.MPV_LOW_RAM_DEMUXER_MAX_BYTES)
        assertEquals(20L * 1024L * 1024L, PlayerBufferConfig.MPV_CELLULAR_DEMUXER_MAX_BYTES)

        assertEquals(60, PlayerBufferConfig.MPV_DEFAULT_READAHEAD_SECS)
        assertEquals(30, PlayerBufferConfig.MPV_LOW_RAM_READAHEAD_SECS)

        assertEquals(30L * 1024L * 1024L, PlayerBufferConfig.MPV_TV_MAX_BACK_BYTES)
        assertEquals(15L * 1024L * 1024L, PlayerBufferConfig.MPV_PHONE_MAX_BACK_BYTES)
        assertEquals(5L * 1024L * 1024L, PlayerBufferConfig.MPV_LOW_RAM_MAX_BACK_BYTES)
    }

    @Test
    fun deviceMemoryClassIdentifiesLowRamAnd32BitSticks() {
        // Flagged as low RAM device by system
        assertTrue(PlayerBufferConfig.isLowRam(isLowRamDevice = true, memoryClass = 256))
        assertTrue(PlayerBufferConfig.isLowRam(isLowRamDevice = true, memoryClass = 512))

        // 32-bit TV sticks with low memory class (<= 192MB)
        assertTrue(PlayerBufferConfig.isLowRam(isLowRamDevice = false, memoryClass = 128))
        assertTrue(PlayerBufferConfig.isLowRam(isLowRamDevice = false, memoryClass = 192))

        // Standard phone or 4K TV (> 192MB)
        assertFalse(PlayerBufferConfig.isLowRam(isLowRamDevice = false, memoryClass = 256))
        assertFalse(PlayerBufferConfig.isLowRam(isLowRamDevice = false, memoryClass = 384))
        assertFalse(PlayerBufferConfig.isLowRam(isLowRamDevice = false, memoryClass = 512))
    }

    @Test
    fun resolveExoConfigDistinguishesNormalAndLowRamTiers() {
        val normal = PlayerBufferConfig.resolveExoConfig(isLowRam = false)
        assertFalse(normal.isLowRam)
        assertEquals(30_000, normal.minBufferMs)
        assertEquals(120_000, normal.maxBufferMs)
        assertEquals(2_500, normal.bufferForPlaybackMs)
        assertEquals(5_000, normal.bufferForPlaybackAfterRebufferMs)
        assertEquals(30_000, normal.backBufferMs)
        assertTrue(normal.retainBackBufferFromKeyframe)
        assertTrue(normal.description.startsWith("exo:normal"))

        val lowRam = PlayerBufferConfig.resolveExoConfig(isLowRam = true)
        assertTrue(lowRam.isLowRam)
        assertEquals(15_000, lowRam.minBufferMs)
        assertEquals(45_000, lowRam.maxBufferMs)
        assertEquals(2_500, lowRam.bufferForPlaybackMs)
        assertEquals(5_000, lowRam.bufferForPlaybackAfterRebufferMs)
        assertEquals(10_000, lowRam.backBufferMs)
        assertTrue(lowRam.retainBackBufferFromKeyframe)
        assertTrue(lowRam.description.startsWith("exo:low_ram"))
    }

    @Test
    fun resolveMpvConfigSelectsSensibleDemuxerCacheForEachFormFactor() {
        // 1. Android TV (normal RAM)
        val tvConfig = PlayerBufferConfig.resolveMpvConfig(isLowRam = false, isTv = true, isCellular = false)
        assertEquals("yes", tvConfig.cacheEnabled)
        assertEquals(150L * 1024L * 1024L, tvConfig.demuxerMaxBytes)
        assertEquals(60, tvConfig.demuxerReadaheadSecs)
        assertEquals(30L * 1024L * 1024L, tvConfig.demuxerMaxBackBytes)
        assertFalse(tvConfig.isLowRam)
        assertTrue(tvConfig.description.startsWith("mpv:tv"))
        assertTrue(tvConfig.description.contains("max=150MB"))
        assertTrue(tvConfig.description.contains("readahead=60s"))

        // 2. Phone (normal RAM)
        val phoneConfig = PlayerBufferConfig.resolveMpvConfig(isLowRam = false, isTv = false, isCellular = false)
        assertEquals("yes", phoneConfig.cacheEnabled)
        assertEquals(64L * 1024L * 1024L, phoneConfig.demuxerMaxBytes)
        assertEquals(60, phoneConfig.demuxerReadaheadSecs)
        assertEquals(15L * 1024L * 1024L, phoneConfig.demuxerMaxBackBytes)
        assertFalse(phoneConfig.isLowRam)
        assertTrue(phoneConfig.description.startsWith("mpv:phone"))
        assertTrue(phoneConfig.description.contains("max=64MB"))

        // 3. Low-RAM 32-bit Stick
        val lowRamStickConfig = PlayerBufferConfig.resolveMpvConfig(isLowRam = true, isTv = true, isCellular = false)
        assertEquals("yes", lowRamStickConfig.cacheEnabled)
        assertEquals(32L * 1024L * 1024L, lowRamStickConfig.demuxerMaxBytes)
        assertEquals(30, lowRamStickConfig.demuxerReadaheadSecs)
        assertEquals(5L * 1024L * 1024L, lowRamStickConfig.demuxerMaxBackBytes)
        assertTrue(lowRamStickConfig.isLowRam)
        assertTrue(lowRamStickConfig.description.startsWith("mpv:low_ram"))
        assertTrue(lowRamStickConfig.description.contains("max=32MB"))
        assertTrue(lowRamStickConfig.description.contains("readahead=30s"))

        // 4. Cellular Network (constrained bandwidth)
        val cellularConfig = PlayerBufferConfig.resolveMpvConfig(isLowRam = false, isTv = false, isCellular = true)
        assertEquals("yes", cellularConfig.cacheEnabled)
        assertEquals(20L * 1024L * 1024L, cellularConfig.demuxerMaxBytes)
        assertEquals(30, cellularConfig.demuxerReadaheadSecs)
        assertEquals(5L * 1024L * 1024L, cellularConfig.demuxerMaxBackBytes)
        assertTrue(cellularConfig.description.startsWith("mpv:cellular"))
    }

    @Test
    fun buildLoadControlConstructsConfiguredDefaultLoadControl() {
        val normalConfig = PlayerBufferConfig.resolveExoConfig(isLowRam = false)
        val normalLoadControl = PlayerBufferConfig.buildLoadControl(normalConfig)
        assertNotNull(normalLoadControl)

        val lowRamConfig = PlayerBufferConfig.resolveExoConfig(isLowRam = true)
        val lowRamLoadControl = PlayerBufferConfig.buildLoadControl(lowRamConfig)
        assertNotNull(lowRamLoadControl)
    }

    @Test
    fun telegramProxyPlayerFactoryProducesLoadControlWithBufferConfig() {
        val customConfig = PlayerBufferConfig.ExoBufferConfig(
            minBufferMs = 35_000,
            maxBufferMs = 100_000,
            bufferForPlaybackMs = 3_000,
            bufferForPlaybackAfterRebufferMs = 6_000,
            backBufferMs = 25_000,
            retainBackBufferFromKeyframe = true,
            isLowRam = false,
            description = "exo:test_custom"
        )
        val loadControl = TelegramProxyPlayerFactory.customLoadControl(bufferConfig = customConfig)
        assertNotNull(loadControl)
    }
}
