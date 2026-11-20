package com.vaydelux.bousygram.presentation.extras

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a dedicated test preferences name; never touches production library. */
@RunWith(AndroidJUnit4::class)
class ExtrasPersonalLibraryPersistenceInstrumentedTest {
    private val name = "extras_personal_library_instrumented_test_v1"

    @Test fun savedDataPersistsAcrossNewInstancesWithoutTelegram() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val first = ExtrasPersonalLibrary(context, name)
            val movie = ExtrasItem(42, ExtrasType.MOVIES, "Movie fixture", year = "2026")
            val show = ExtrasItem(42, ExtrasType.TV, "Show fixture", year = "2026")
            first.toggleSaved(movie)
            first.toggleSaved(show)
            first.markOpened(show)

            val reloaded = ExtrasPersonalLibrary(context, name).read()
            assertEquals(2, reloaded.saved.size)
            assertEquals(1, reloaded.recent.size)
            assertTrue(ExtrasPersonalLibraryPolicy.containsSaved(reloaded, "MOVIES", 42))
            assertTrue(ExtrasPersonalLibraryPolicy.containsSaved(reloaded, "TV", 42))

            val finalSnapshot = ExtrasPersonalLibrary(context, name).clearRecent()
            assertTrue(finalSnapshot.recent.isEmpty())
            assertEquals(2, ExtrasPersonalLibrary(context, name).read().saved.size)
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun corruptLibraryIsNotSilentlyWipedOnAnAttemptedSave() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val raw = "{not-valid-json"
            prefs.edit().putString("library_json", raw).commit()
            val library = ExtrasPersonalLibrary(context, name)
            val readResult = runCatching { library.read() }
            assertTrue(readResult.exceptionOrNull() is IOException)
            val writeResult = runCatching {
                library.toggleSaved(ExtrasItem(4, ExtrasType.ANIME, "Anime fixture"))
            }
            assertTrue(writeResult.exceptionOrNull() is IOException)
            assertEquals(raw, prefs.getString("library_json", null))
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
