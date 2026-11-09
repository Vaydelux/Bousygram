package com.vaydelux.bousygram.core.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheTdLibGetFileErrorPolicyTest {
    @Test fun knownPermanentMissingFileErrorsCanDropStaleAccessRecords() {
        assertTrue(shouldDiscardCacheAccessRecordOnGetFileError(400, "File not found"))
        assertTrue(shouldDiscardCacheAccessRecordOnGetFileError(404, "FILE_NOT_FOUND"))
        assertTrue(shouldDiscardCacheAccessRecordOnGetFileError(400, "FILE_ID_INVALID"))
        assertTrue(shouldDiscardCacheAccessRecordOnGetFileError(400, " invalid file id "))
    }

    @Test fun transientErrorsMustNotOrphanCachedMedia() {
        for (code in listOf(401, 403, 408, 420, 429, 500, 502, 503, 504)) {
            assertFalse(shouldDiscardCacheAccessRecordOnGetFileError(code, "File not found"))
        }
        assertFalse(shouldDiscardCacheAccessRecordOnGetFileError(400, "Too Many Requests"))
        assertFalse(shouldDiscardCacheAccessRecordOnGetFileError(400, "Database is busy"))
        assertFalse(shouldDiscardCacheAccessRecordOnGetFileError(404, "Other resource unavailable"))
        assertFalse(shouldDiscardCacheAccessRecordOnGetFileError(500, "INTERNAL"))
    }

    @Test fun unknownOrMissingDescriptionsPreserveMetadataForRetry() {
        assertFalse(shouldDiscardCacheAccessRecordOnGetFileError(400, null))
        assertFalse(shouldDiscardCacheAccessRecordOnGetFileError(400, ""))
        assertFalse(shouldDiscardCacheAccessRecordOnGetFileError(400, "File not found for this account temporarily"))
        assertFalse(shouldDiscardCacheAccessRecordOnGetFileError(1, "FILE_ID_INVALID"))
    }
}
