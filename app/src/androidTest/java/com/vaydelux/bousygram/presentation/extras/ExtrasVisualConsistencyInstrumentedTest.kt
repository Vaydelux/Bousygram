package com.vaydelux.bousygram.presentation.extras

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaydelux.bousygram.presentation.components.BousygramPageHeading
import com.vaydelux.bousygram.presentation.components.PremiumGridBackdrop
import com.vaydelux.bousygram.presentation.components.writeUiEvidence
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Offline Extras visual QA: theme parity, three visible catalog tabs, portrait
 * artwork with its top and bottom intact, prominent poster focus and pointer.
 * No TMDB token, AniList access or Telegram session is required.
 */
@RunWith(AndroidJUnit4::class)
class ExtrasVisualConsistencyInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun catalogTabsFitInCompactWidthAndArtworkKeepsItsPortraitRatio() {
        val art = makePosterFixture("extras-qa-poster.png")
        compose.setContent {
            BousygramTheme(preset = BousygramThemePreset.DARK) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    ExtrasCatalogTabs(selectedTab = 0, onSelect = {})
                    ExtrasPosterCard(
                        item = ExtrasItem(501, ExtrasType.MOVIES, "A full-length poster",
                            "2026", poster = art, rating = "8.4", subtitle = "Movie"),
                        modifier = Modifier.fillMaxWidth()
                    ) {}
                }
            }
        }
        compose.onNodeWithTag("extras-tab-movies").assertIsDisplayed()
        compose.onNodeWithTag("extras-tab-tv").assertIsDisplayed()
        compose.onNodeWithTag("extras-tab-anime").assertIsDisplayed()
        val bounds = compose.onAllNodesWithTag("extras-poster-artwork")[0]
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Poster should have measurable dimensions", bounds.height > 0f)
        assertEquals(ExtrasImagePolicy.POSTER_ASPECT_RATIO,
            bounds.width / bounds.height, 0.025f)
    }

    @Test fun captureCatalogAcrossThemesAndFocusedPoster() {
        val art = makePosterFixture("extras-qa-poster-theme.png")
        val theme = mutableStateOf(BousygramThemePreset.DARK)
        var selected by mutableIntStateOf(0)
        compose.setContent {
            BousygramTheme(preset = theme.value) {
                PremiumGridBackdrop {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        BousygramPageHeading(
                            "DISCOVER", "Extras", "Movies · TV Shows · Anime", compact = true
                        )
                        ExtrasCatalogTabs(selectedTab = selected) { selected = it }
                        Text("Popular titles", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExtrasPosterCard(
                                item = ExtrasItem(501, ExtrasType.MOVIES, "The Complete Story",
                                    "2026", poster = art, rating = "8.4",
                                    subtitle = "Movie"),
                                modifier = Modifier.weight(1f)
                            ) {}
                            ExtrasPosterCard(
                                item = ExtrasItem(502, ExtrasType.ANIME, "Animated Feature",
                                    "2025", poster = art, rating = "91/100",
                                    subtitle = "Movie · Anime"),
                                modifier = Modifier.weight(1f)
                            ) {}
                        }
                    }
                }
            }
        }
        writeUiEvidence(compose, "extras-catalog-dark")
        compose.runOnIdle { theme.value = BousygramThemePreset.LIGHT }
        writeUiEvidence(compose, "extras-catalog-light")
        compose.runOnIdle { theme.value = BousygramThemePreset.HIGH_CONTRAST }
        val poster = compose.onNodeWithTag("extras-poster-movies-501")
        poster.performSemanticsAction(SemanticsActions.RequestFocus)
        writeUiEvidence(compose, "extras-catalog-focus")
    }

    @Test fun captureMetadataBackdropHeroWithBoundedArtwork() {
        val backdrop = makeBackdropFixture("extras-qa-backdrop.png")
        compose.setContent {
            BousygramTheme(preset = BousygramThemePreset.DARK) {
                PremiumGridBackdrop {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        ExtrasDetailBackdrop(
                            info = ExtrasDetail(
                                item = ExtrasItem(77, ExtrasType.TV,
                                    "The New Series", "2026", rating = "8.7"),
                                description = "A provider-supplied series synopsis",
                                genres = listOf("Drama", "Sci-Fi"),
                                facts = listOf("2026", "1 season"),
                                cast = emptyList(), reviews = emptyList(),
                                seasons = emptyList(), backdrop = backdrop
                            ),
                            compact = true,
                            shortViewport = false
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("extras-detail-backdrop").assertIsDisplayed()
        writeUiEvidence(compose, "extras-detail-hero")
    }

    @Test fun captureConventionalWebCursorAgainstThemeSurface() {
        compose.setContent {
            BousygramTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    Text(
                        "Web player · cursor mode",
                        modifier = Modifier.padding(22.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    ExtrasVirtualCursor(
                        target = null,
                        widthPx = 1080,
                        heightPx = 1920,
                        xPx = 340,
                        yPx = 480,
                        onDelta = { _, _ -> },
                        onConfirm = {},
                        isActive = true,
                        tag = "extras-qa-pointer"
                    )
                }
            }
        }
        compose.onNodeWithTag("extras-cursor-pointer").assertIsDisplayed()
        writeUiEvidence(compose, "extras-webview-pointer")
    }

    /** Abstract local cinematic banner; avoids network and copyright fixtures. */
    private fun makeBackdropFixture(name: String): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val painter = Paint(Paint.ANTI_ALIAS_FLAG)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.rgb(12, 43, 69))
        painter.color = AndroidColor.rgb(28, 104, 126)
        canvas.drawRect(0f, 30f, 640f, 200f, painter)
        painter.color = AndroidColor.rgb(247, 178, 99)
        canvas.drawCircle(460f, 105f, 88f, painter)
        painter.color = AndroidColor.rgb(11, 33, 50)
        canvas.drawRect(0f, 270f, 640f, 360f, painter)
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
        return file.toURI().toString()
    }

    /** A local synthetic poster with distinct top and bottom bands (not remote art). */
    private fun makePosterFixture(name: String): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(240, 360, Bitmap.Config.ARGB_8888)
        val painter = Paint(Paint.ANTI_ALIAS_FLAG)
        val canvas = Canvas(bitmap)
        painter.color = AndroidColor.rgb(30, 59, 83)
        canvas.drawColor(painter.color)
        painter.color = AndroidColor.rgb(112, 222, 237)
        canvas.drawRect(0f, 0f, 240f, 52f, painter)
        painter.color = AndroidColor.rgb(225, 165, 110)
        canvas.drawRect(0f, 305f, 240f, 360f, painter)
        painter.color = AndroidColor.WHITE
        painter.textSize = 28f
        canvas.drawText("TOP", 18f, 100f, painter)
        canvas.drawText("FULL POSTER", 18f, 185f, painter)
        canvas.drawText("BOTTOM", 18f, 343f, painter)
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
        return file.toURI().toString()
    }
}
