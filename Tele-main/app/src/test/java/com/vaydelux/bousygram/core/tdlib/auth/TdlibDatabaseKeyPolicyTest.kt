package com.vaydelux.bousygram.core.tdlib.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class TdlibDatabaseKeyPolicyTest {
    @Test
    fun newEmptyDatabaseGetsAnEncryptedKey() {
        assertEquals(
            TdlibDatabaseKeyPolicy.Decision.CREATE_ENCRYPTED_KEY,
            TdlibDatabaseKeyPolicy.decide(null, hasEncryptedKey = false, hasExistingDatabaseData = false)
        )
    }

    @Test
    fun oldUnmarkedDatabaseKeepsLegacyKeyMode() {
        assertEquals(
            TdlibDatabaseKeyPolicy.Decision.USE_LEGACY_EMPTY_KEY,
            TdlibDatabaseKeyPolicy.decide(null, hasEncryptedKey = false, hasExistingDatabaseData = true)
        )
    }

    @Test
    fun existingStoredKeyRemainsUsableWithoutOldModeMarker() {
        assertEquals(
            TdlibDatabaseKeyPolicy.Decision.USE_STORED_ENCRYPTED_KEY,
            TdlibDatabaseKeyPolicy.decide(null, hasEncryptedKey = true, hasExistingDatabaseData = true)
        )
    }

    @Test
    fun missingKeyForEncryptedDatabaseFailsClosed() {
        assertEquals(
            TdlibDatabaseKeyPolicy.Decision.FAIL_CLOSED,
            TdlibDatabaseKeyPolicy.decide(
                TdlibDatabaseKeyPolicy.MODE_ENCRYPTED_V1,
                hasEncryptedKey = false,
                hasExistingDatabaseData = true
            )
        )
    }

    @Test
    fun inconsistentLegacyModeAndEncryptedKeyFailsClosed() {
        assertEquals(
            TdlibDatabaseKeyPolicy.Decision.FAIL_CLOSED,
            TdlibDatabaseKeyPolicy.decide(
                TdlibDatabaseKeyPolicy.MODE_LEGACY_EMPTY,
                hasEncryptedKey = true,
                hasExistingDatabaseData = true
            )
        )
    }
    @Test
    fun matchingRedundantModeMarkersAgree() {
        assertEquals(
            TdlibDatabaseKeyPolicy.MODE_ENCRYPTED_V1,
            TdlibDatabaseKeyPolicy.resolveMode(
                TdlibDatabaseKeyPolicy.MODE_ENCRYPTED_V1,
                TdlibDatabaseKeyPolicy.MODE_ENCRYPTED_V1
            )
        )
    }

    @Test
    fun mismatchedModeMarkersAreNotSilentlyResolved() {
        assertEquals(
            TdlibDatabaseKeyPolicy.MODE_INCONSISTENT,
            TdlibDatabaseKeyPolicy.resolveMode(
                TdlibDatabaseKeyPolicy.MODE_ENCRYPTED_V1,
                TdlibDatabaseKeyPolicy.MODE_LEGACY_EMPTY
            )
        )
    }

}
