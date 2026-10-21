package com.onepage.companion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalOnePageColors = staticCompositionLocalOf { LightColors }

val OnePageShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private fun lightScheme() = lightColorScheme(
    primary = Palette.Pine,
    onPrimary = Color.White,
    primaryContainer = Palette.PineSoft,
    onPrimaryContainer = Color(0xFF0C3B30),
    secondary = Palette.Ink2,
    onSecondary = Color.White,
    secondaryContainer = Palette.CardSunken,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Amber,
    onTertiary = Color.White,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Card,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.CardSunken,
    onSurfaceVariant = Palette.Ink2,
    outline = Palette.Hairline,
    outlineVariant = Palette.Hairline,
    error = Palette.Danger,
    onError = Color.White,
    errorContainer = Palette.DangerSoft,
    onErrorContainer = Color(0xFF5F1512),
    surfaceTint = Color.Transparent,
    scrim = Color(0x9914161A),
)

private fun darkScheme() = darkColorScheme(
    primary = Palette.PineDark,
    onPrimary = Color(0xFF06231C),
    primaryContainer = Palette.PineSoftDark,
    onPrimaryContainer = Color(0xFFBFEDDE),
    secondary = Palette.Ink2Dark,
    onSecondary = Color(0xFF14161A),
    secondaryContainer = Palette.CardSunkenDark,
    onSecondaryContainer = Palette.InkDark,
    tertiary = Palette.AmberDark,
    onTertiary = Color(0xFF2C1B00),
    background = Palette.PaperDark,
    onBackground = Palette.InkDark,
    surface = Palette.CardDark,
    onSurface = Palette.InkDark,
    surfaceVariant = Palette.CardSunkenDark,
    onSurfaceVariant = Palette.Ink2Dark,
    outline = Palette.HairlineDark,
    outlineVariant = Palette.HairlineDark,
    error = Palette.DangerDark,
    onError = Color(0xFF3A0B08),
    errorContainer = Palette.DangerSoftDark,
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color.Transparent,
    scrim = Color(0xCC000000),
)

@Composable
fun OnePageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalOnePageColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme() else lightScheme(),
            typography = OnePageTypography,
            shapes = OnePageShapes,
            content = content,
        )
    }
}

/** Shorthand for the app's semantic palette. */
val onePageColors: OnePageColors
    @Composable get() = LocalOnePageColors.current
