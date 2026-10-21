package com.onepage.companion.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Palette: warm paper + ink, with a single pine accent.
 * Deliberately low-chroma so the e-ink subject stays the hero; colour is reserved
 * for state (connected / warning / destructive).
 */
object Palette {
    // Light
    val Paper = Color(0xFFF7F5F0)
    val Card = Color(0xFFFFFFFF)
    val CardSunken = Color(0xFFF0EEE7)
    val Ink = Color(0xFF14161A)
    val Ink2 = Color(0xFF5C6169)
    val Ink3 = Color(0xFF8B9098)
    val Hairline = Color(0xFFE4E1D9)
    val Pine = Color(0xFF1F6F5C)
    val PinePressed = Color(0xFF17594A)
    val PineSoft = Color(0xFFE3EFEA)
    val Amber = Color(0xFF9A5B00)
    val AmberSoft = Color(0xFFF7EBD8)
    val Danger = Color(0xFFB3261E)
    val DangerSoft = Color(0xFFF9E3E1)

    // Dark
    val PaperDark = Color(0xFF0E1012)
    val CardDark = Color(0xFF171A1E)
    val CardSunkenDark = Color(0xFF101316)
    val InkDark = Color(0xFFECEEF0)
    val Ink2Dark = Color(0xFF98A0A8)
    val Ink3Dark = Color(0xFF6C747C)
    val HairlineDark = Color(0xFF252A30)
    val PineDark = Color(0xFF6FD3B8)
    val PineSoftDark = Color(0xFF122E27)
    val AmberDark = Color(0xFFE9B463)
    val AmberSoftDark = Color(0xFF2C2317)
    val DangerDark = Color(0xFFF2A6A0)
    val DangerSoftDark = Color(0xFF331B1A)
}

@Immutable
data class OnePageColors(
    val card: Color,
    val cardSunken: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val hairline: Color,
    val accent: Color,
    val accentSoft: Color,
    val amber: Color,
    val amberSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val isDark: Boolean,
)

val LightColors = OnePageColors(
    card = Palette.Card,
    cardSunken = Palette.CardSunken,
    ink = Palette.Ink,
    ink2 = Palette.Ink2,
    ink3 = Palette.Ink3,
    hairline = Palette.Hairline,
    accent = Palette.Pine,
    accentSoft = Palette.PineSoft,
    amber = Palette.Amber,
    amberSoft = Palette.AmberSoft,
    danger = Palette.Danger,
    dangerSoft = Palette.DangerSoft,
    isDark = false,
)

val DarkColors = OnePageColors(
    card = Palette.CardDark,
    cardSunken = Palette.CardSunkenDark,
    ink = Palette.InkDark,
    ink2 = Palette.Ink2Dark,
    ink3 = Palette.Ink3Dark,
    hairline = Palette.HairlineDark,
    accent = Palette.PineDark,
    accentSoft = Palette.PineSoftDark,
    amber = Palette.AmberDark,
    amberSoft = Palette.AmberSoftDark,
    danger = Palette.DangerDark,
    dangerSoft = Palette.DangerSoftDark,
    isDark = true,
)
