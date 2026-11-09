package com.vaydelux.bousygram.core.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePressurePolicyTest {
    @Test fun freeSpaceAt500MiBIsNormalButOneByteLowerIsNot() {
        assertEquals(StoragePressurePolicy.Level.NORMAL,
            StoragePressurePolicy.level(500L * TvStorageBudget.MIB))
        assertEquals(StoragePressurePolicy.Level.LOW,
            StoragePressurePolicy.level(500L * TvStorageBudget.MIB - 1L))
        assertEquals(StoragePressurePolicy.Level.CRITICAL,
            StoragePressurePolicy.level(250L * TvStorageBudget.MIB - 1L))
        assertEquals(StoragePressurePolicy.Level.CRITICAL, StoragePressurePolicy.level(null))
    }

    @Test fun unknownOrLowSpaceNeverStartsOptionalDownloads() {
        assertFalse(StoragePressurePolicy.allowSpeculativeDownload(null))
        assertFalse(StoragePressurePolicy.allowSpeculativeDownload(0L))
        assertFalse(StoragePressurePolicy.allowSpeculativeDownload(400L * TvStorageBudget.MIB))
        assertTrue(StoragePressurePolicy.allowSpeculativeDownload(900L * TvStorageBudget.MIB))
    }

    @Test fun boundedReadAheadNeverExceeds32MiBAndKeepsForegroundIndependent() {
        assertEquals(0L, StoragePressurePolicy.boundedReadAheadBytes(48L * TvStorageBudget.MIB, null))
        assertEquals(0L, StoragePressurePolicy.boundedReadAheadBytes(48L * TvStorageBudget.MIB,
            200L * TvStorageBudget.MIB))
        assertEquals(32L * TvStorageBudget.MIB,
            StoragePressurePolicy.boundedReadAheadBytes(48L * TvStorageBudget.MIB,
                900L * TvStorageBudget.MIB))
        assertEquals(8L * TvStorageBudget.MIB,
            StoragePressurePolicy.boundedReadAheadBytes(8L * TvStorageBudget.MIB,
                900L * TvStorageBudget.MIB))
    }
}
