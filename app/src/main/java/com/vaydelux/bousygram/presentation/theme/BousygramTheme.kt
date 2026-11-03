package com.vaydelux.bousygram.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Unified dark-first visual theme designed for OLED displays and TV viewing distances. */
enum class BousygramThemePreset(
    val id: String,
    val displayName: String,
    val description: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val outline: Color
) {
    DARK(
        "dark", "Midnight blue", "Cinematic navy, ice cyan, and Chow gold",
        Color(0xFF37D7F2), Color(0xFF82C3FF), Color(0xFFFFC67D),
        Color(0xFF061426), Color(0xFF0D223A), Color(0xFF19354F), Color(0xFF68809B)
    ),
    HIGH_CONTRAST(
        "high_contrast", "High contrast", "Bright focus indicators and enhanced text contrast",
        Color(0xFFFFE66A), Color(0xFF7CE7FF), Color(0xFFFFB5EB),
        Color(0xFF060708), Color(0xFF0F1215), Color(0xFF1E2429), Color(0xFFE1E7EF)
    ),
    LIGHT(
        "light", "Daylight", "Bright ice-blue surfaces with deep accessible ink",
        Color(0xFF075B83), Color(0xFF19548B), Color(0xFF805016),
        Color(0xFFF5FAFF), Color(0xFFFFFFFF), Color(0xFFE4F0F8), Color(0xFF70849A)
    );

    companion object {
        val MIDNIGHT = DARK
        fun fromId(id: String?): BousygramThemePreset =
            entries.firstOrNull { it.id == id || (id == "midnight" && it == DARK) } ?: DARK
    }
}

internal fun schemeFor(preset: BousygramThemePreset): ColorScheme {
    if (preset == BousygramThemePreset.LIGHT) {
        return lightColorScheme(
            primary = preset.primary,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD9F3FF),
            onPrimaryContainer = Color(0xFF07364B),
            secondary = preset.secondary,
            onSecondary = Color.White,
            tertiary = preset.tertiary,
            onTertiary = Color.White,
            background = preset.background,
            onBackground = Color(0xFF13273D),
            surface = preset.surface,
            onSurface = Color(0xFF13273D),
            surfaceVariant = preset.surfaceVariant,
            onSurfaceVariant = Color(0xFF344F66),
            surfaceContainer = Color(0xFFEBF4FC),
            surfaceContainerHigh = preset.surfaceVariant,
            outline = preset.outline,
            outlineVariant = Color(0xFFB8C9D8),
            error = Color(0xFFB42335),
            onError = Color.White
        )
    }
    val highContrast = preset == BousygramThemePreset.HIGH_CONTRAST
    return darkColorScheme(
    primary = preset.primary,
    onPrimary = Color(0xFF071014),
    // Opaque semantic containers retain contrast even over video surfaces.
    primaryContainer = Color(0xFF123D56),
    onPrimaryContainer = Color(0xFFF8FBFF),
    secondary = preset.secondary,
    onSecondary = Color(0xFF06120F),
    secondaryContainer = Color(0xFF1B3A56),
    onSecondaryContainer = Color(0xFFF3FFFB),
    tertiary = preset.tertiary,
    onTertiary = Color(0xFF17110A),
    tertiaryContainer = Color(0xFF52351E),
    onTertiaryContainer = Color(0xFFFFF8EF),
    background = preset.background,
    onBackground = if (highContrast) Color.White else Color(0xFFF4F7FC),
    surface = preset.surface,
    onSurface = if (highContrast) Color.White else Color(0xFFF4F7FC),
    surfaceVariant = preset.surfaceVariant,
    onSurfaceVariant = if (highContrast) Color(0xFFE7EBF1) else Color(0xFFB4C0D2),
    surfaceContainerLowest = preset.background,
    // Foreground labels must never inherit the underlying video/image colors.
    // Opaque surfaces protect readable contrast in dark and high-contrast modes.
    surfaceContainerLow = preset.surface,
    surfaceContainer = preset.surface,
    surfaceContainerHigh = preset.surfaceVariant,
    surfaceContainerHighest = preset.surfaceVariant,
    outline = preset.outline,
    outlineVariant = preset.outline.copy(alpha = 0.46f),
    error = Color(0xFFFF6B80),
    onError = Color(0xFF2B0000),
    errorContainer = Color(0xFF5C1824),
    onErrorContainer = Color(0xFFFFD9DF)
)
}

private val BousygramTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1.6).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1.2).sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.7).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    )
)

private val BousygramShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BousygramTheme(
    preset: BousygramThemePreset = BousygramThemePreset.MIDNIGHT,
    content: @Composable () -> Unit
) {
    val scheme = remember(preset) { schemeFor(preset) }
    MaterialTheme(
        colorScheme = scheme,
        typography = BousygramTypography,
        shapes = BousygramShapes,
        content = content
    )
}
