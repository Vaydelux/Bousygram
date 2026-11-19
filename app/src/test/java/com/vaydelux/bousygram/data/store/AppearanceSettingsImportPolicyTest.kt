package com.vaydelux.bousygram.data.store

import com.vaydelux.bousygram.presentation.theme.BousygramBackdropPattern
import com.vaydelux.bousygram.presentation.theme.BousygramDesignStyle
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Prevent current theme/style/pattern choices from breaking settings backup restore. */
class AppearanceSettingsImportPolicyTest {
    @Test fun everyInstalledThemeRoundTripsThroughImportValidation() {
        for (preset in BousygramThemePreset.entries) {
            val document = JSONObject().put("version", 1).put("visualTheme", preset.id)
            assertEquals(preset.id, SettingsImportPolicy.parse(document.toString(), null).visualTheme)
        }
    }

    @Test fun everyInstalledDesignAndBackdropRoundTrips() {
        for (style in BousygramDesignStyle.entries) {
            val document = JSONObject().put("visualDesignStyle", style.id)
            assertEquals(style.id, SettingsImportPolicy.parse(document.toString(), null).visualDesignStyle)
        }
        for (pattern in BousygramBackdropPattern.entries) {
            val document = JSONObject().put("visualBackdropPattern", pattern.id)
            assertEquals(pattern.id, SettingsImportPolicy.parse(document.toString(), null).visualBackdropPattern)
        }
    }

    @Test fun fullCuratedLookRestoresAllAppearanceFields() {
        val imported = SettingsImportPolicy.parse(
            JSONObject()
                .put("version", 1)
                .put("visualTheme", "midnight_glass")
                .put("visualDesignStyle", "iphone_glass")
                .put("visualBackdropPattern", "ios_mesh")
                .toString(),
            null
        )
        assertEquals("midnight_glass", imported.visualTheme)
        assertEquals("iphone_glass", imported.visualDesignStyle)
        assertEquals("ios_mesh", imported.visualBackdropPattern)
    }

    @Test fun unspecifiedAppearanceFieldsDoNotOverwriteStoredPreferences() {
        val imported = SettingsImportPolicy.parse("""{"version":1,"safeMode":true}""", null)
        assertNull(imported.visualTheme)
        assertNull(imported.visualDesignStyle)
        assertNull(imported.visualBackdropPattern)
    }

    @Test fun unknownOrWronglyTypedAppearanceValuesAreRejectedBeforeImport() {
        for (key in listOf("visualTheme", "visualDesignStyle", "visualBackdropPattern")) {
            for (invalid in listOf("\"not_a_real_style\"", "17", "null", "true", "[]")) {
                val document = "{\"version\":1,\"$key\":$invalid}"
                assertThrows(IllegalArgumentException::class.java) {
                    SettingsImportPolicy.parse(document, null)
                }
            }
        }
    }
}
