package com.scansfer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Violet = Color(0xFF6C5CE7)
val VioletSoft = Color(0xFFB9B1FF)
val Teal = Color(0xFF00C2A8)
val Amber = Color(0xFFFFB020)
val Ink = Color(0xFF0B0B12)
val InkElevated = Color(0xFF15151F)
val InkCard = Color(0xFF1C1C29)

private val DarkColors = darkColorScheme(
    primary = VioletSoft,
    onPrimary = Color(0xFF1B1440),
    primaryContainer = Violet,
    onPrimaryContainer = Color.White,
    secondary = Teal,
    onSecondary = Color(0xFF00201B),
    secondaryContainer = Color(0xFF00473D),
    onSecondaryContainer = Color(0xFF7FF5E2),
    tertiary = Amber,
    onTertiary = Color(0xFF3B2600),
    background = Ink,
    onBackground = Color(0xFFE9E8F2),
    surface = Ink,
    onSurface = Color(0xFFE9E8F2),
    surfaceVariant = InkCard,
    onSurfaceVariant = Color(0xFFA9A7BD),
    surfaceContainer = InkElevated,
    surfaceContainerHigh = InkCard,
    outline = Color(0xFF3A3A4D),
    outlineVariant = Color(0xFF2A2A38),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E2FF),
    onPrimaryContainer = Color(0xFF1B1440),
    secondary = Color(0xFF00806E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB8FFF1),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    background = Color(0xFFFBFAFF),
    onBackground = Color(0xFF15151F),
    surface = Color(0xFFFBFAFF),
    onSurface = Color(0xFF15151F),
    surfaceVariant = Color(0xFFF0EEF8),
    onSurfaceVariant = Color(0xFF56546B),
    surfaceContainer = Color(0xFFF3F1FA),
    surfaceContainerHigh = Color(0xFFEDEAF6),
    outline = Color(0xFFCFCCE0),
    outlineVariant = Color(0xFFE3E0EF),
    error = Color(0xFFC0392B),
)

private val ScansferTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Tabular-ish style for counters that must not jitter as digits change. */
val MonoNumber = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
)

@Composable
fun ScansferTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ScansferTypography,
        content = content,
    )
}
