package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.core.account.AccountSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Proves that logout cannot finish draining a progress ledger while an AtomicFile
 * writer is still active. No Android filesystem, network, or timing sleeps required.
 */
class AccountProgressLedgerConcurrencyTest {
    @Test fun invalidationWaitsForActiveWriteAndRejectsOldAccountPublication() = runBlocking {
        val owner = AccountSnapshot(101L, 1L)
        val current = AtomicBoolean(true)
        val writeStarted = CompletableDeferred<Unit>()
        val finishWrite = CompletableDeferred<Unit>()
        var publishedAfterLogout = false
        val persistence = object : ProgressPersistence {
            override suspend fun read(accountId: Long): List<ContinueWatchingEntry> = emptyList()
            override suspend fun write(accountId: Long, entries: List<ContinueWatchingEntry>) {
                writeStarted.complete(Unit)
                finishWrite.await()
            }
        }
        val ledger = AccountProgressLedger(persistence, { current.get() }) { _, _ ->
            if (!current.get()) publishedAfterLogout = true
        }

        val mutation = launch(start = CoroutineStart.UNDISPATCHED) {
            ledger.mutate(owner) { rows ->
                rows["1:2"] = ContinueWatchingEntry(1L, 2L, 3, "Fixture", positionMs = 9000L, durationMs = 120000L)
            }
        }
        writeStarted.await()
        current.set(false) // AccountSession has already invalidated identity.
        val drained = CompletableDeferred<Unit>()
        val invalidate = launch(start = CoroutineStart.UNDISPATCHED) {
            ledger.invalidateAndDrain()
            drained.complete(Unit)
        }
        assertFalse("Logout must wait for outstanding persistence", drained.isCompleted)
        finishWrite.complete(Unit)
        mutation.join()
        invalidate.join()
        assertTrue(drained.isCompleted)
        assertFalse("Old account must never publish progress after logout", publishedAfterLogout)
        assertTrue(ledger.snapshot(owner).isEmpty())
    }
}
