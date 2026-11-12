package com.vaydelux.bousygram.data.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.data.repository.FavoritesRepository
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritesInstrumentedTest {
    @Test fun largeCollectionsReadBoundedPagesAndLateMetadataCannotResurrectRemovedRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.favoriteDao()
            for (count in listOf(0, 1, 100, 1_000, 10_000)) {
                db.runInTransaction {
                    dao.clear()
                    repeat(count) { index -> dao.insert(FavoriteEntity().apply {
                        chatId = -100L; messageId = index + 1L; addedAt = index.toLong()
                        title = "A long saved title with duplicate filenames: video.mkv"
                    }) }
                }
                assertEquals(minOf(count, 60), dao.observePage(60, 0).first().size)
                assertEquals(count, dao.observeKeys().first().size)
                if (count >= 100) {
                    assertEquals(60, dao.observePage(60, 40).first().size)
                    assertEquals(count - 40L, dao.observePage(60, 40).first().first().messageId)
                }
            }
            dao.markUnavailable(-100L, 10_000L)
            assertTrue(dao.find(-100L, 10_000L).unavailable)
            dao.remove(-100L, 10_000L)
            dao.remove(-100L, 10_000L)
            dao.updateMetadata(-100L, 10_000L, "Late result", 42, null, null, null, 60, 123, "Video", null, null, 0)
            assertNull(dao.find(-100L, 10_000L))
        } finally { db.close() }
    }

    @Test fun rapidAccountInvalidationDrainsLiveFavoritesQueriesBeforeRoomCloses() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val account = 8_800_000_004L
        val prefs = context.getSharedPreferences("account_${account}_enterprise", 0)
        AccountSession.invalidate()
        context.deleteDatabase("bousygram_account_$account.db")
        prefs.edit().clear().commit()
        try {
            repeat(8) { index ->
                checkNotNull(AccountSession.activate(account, AccountSession.generation()))
                val media = MediaItem(-100L, index + 1L, 0, MediaType.Video, "Saved episode", 42)
                FavoritesRepository.setFavorite(media, true).join()
                withTimeout(10_000) {
                    FavoritesRepository.keys.first { "-100:${index + 1}" in it }
                }
                // Every logout closes Room synchronously. A lingering Flow query
                // must never run against the closed connection pool afterward.
                AccountSession.invalidate()
                assertTrue(FavoritesRepository.keys.value.isEmpty())
            }
        } finally {
            AccountSession.invalidate()
            context.deleteDatabase("bousygram_account_$account.db")
            prefs.edit().clear().commit()
        }
    }

    @Test fun legacyMigrationIsOneShotAndFavoritesAreIsolatedAcrossAccounts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = 8_800_000_001L
        val second = 8_800_000_002L
        fun prefs(account: Long) = context.getSharedPreferences("account_${account}_enterprise", 0)
        AccountSession.invalidate()
        listOf(first, second).forEach { id ->
            context.deleteDatabase("bousygram_account_$id.db")
            prefs(id).edit().clear().commit()
        }
        try {
            prefs(first).edit().putStringSet("favorites", setOf("-100:5", "invalid", "0:7")).commit()
            val owner = checkNotNull(AccountSession.activate(first, AccountSession.generation()))
            withTimeout(10_000) { FavoritesRepository.keys.first { it == setOf("-100:5") } }
            assertTrue(prefs(first).getBoolean("favorites_room_v3", false))
            val item = MediaItem(-100, 5, 0, MediaType.Video, "Saved episode", 42)
            FavoritesRepository.setFavorite(item, true).join()
            assertEquals("Saved episode", FavoritesRepository.observePage(60).first().single().title)
            // Calls made in quick succession must commit in invocation order.
            FavoritesRepository.setFavorite(item, true)
            FavoritesRepository.remove(-100, 5).join()
            FavoritesRepository.remove(-100, 5).join()
            withTimeout(10_000) { FavoritesRepository.keys.first { it.isEmpty() } }
            AccountSession.invalidate()
            checkNotNull(AccountSession.activate(second, AccountSession.generation()))
            assertTrue(FavoritesRepository.observePage(60).first().isEmpty())
            // An old network completion must not recreate the previous account's record.
            FavoritesRepository.updateMetadata(owner, item)
            AccountSession.invalidate()
            checkNotNull(AccountSession.activate(first, AccountSession.generation()))
            assertTrue(FavoritesRepository.observePage(60).first().isEmpty())
        } finally {
            AccountSession.invalidate()
            listOf(first, second).forEach { id ->
                context.deleteDatabase("bousygram_account_$id.db")
                prefs(id).edit().clear().commit()
            }
        }
    }
}
