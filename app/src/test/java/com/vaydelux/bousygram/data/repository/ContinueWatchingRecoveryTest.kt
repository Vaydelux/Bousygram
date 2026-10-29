package com.vaydelux.bousygram.data.repository

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingRecoveryTest {
    @Test fun interruptedWritePreservesBackupOnlyWithoutInventingAnEmptyLedger() {
        val dir = Files.createTempDirectory("bousygram-progress-backup-").toFile()
        try {
            val primary = java.io.File(dir, "continue_watching.json")
            val backup = java.io.File(dir, "continue_watching.json.bak")
            val bytes = """{"accountId":100,"entries":["partial"]}""".toByteArray()
            backup.writeBytes(bytes)
            val retained = retainUnreadableProgress(primary, timestampMs = 123L)
            assertTrue(retained.exists())
            assertArrayEquals(bytes, retained.readBytes())
            assertTrue(backup.exists())
            assertFalse(primary.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun duplicateRecoveryAttemptsNeverOverwriteFirstCopy() {
        val dir = Files.createTempDirectory("bousygram-progress-duplicates-").toFile()
        try {
            val primary = java.io.File(dir, "continue_watching.json")
            primary.writeText("first")
            val first = retainUnreadableProgress(primary, timestampMs = 99L)
            primary.writeText("second")
            val second = retainUnreadableProgress(primary, timestampMs = 99L)
            assertTrue(first != second)
            assertTrue(first.readText() == "first")
            assertTrue(second.readText() == "second")
        } finally { dir.deleteRecursively() }
    }

    @Test fun missingPrimaryAndBackupCannotPretendToBeRecovered() {
        val dir = Files.createTempDirectory("bousygram-progress-missing-").toFile()
        try {
            val primary = java.io.File(dir, "continue_watching.json")
            assertTrue(runCatching { retainUnreadableProgress(primary) }.isFailure)
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally { dir.deleteRecursively() }
    }
}
