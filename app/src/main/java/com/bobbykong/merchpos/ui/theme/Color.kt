package com.bobbykong.merchpos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Colours taken from the app icon: lilac body, pink receipt, dark brown outline
val Ink = Color(0xFF3A2A1E)
val Grape = Color(0xFF7C4DBA)
val Lilac = Color(0xFFD3ADF0)
val LilacSoft = Color(0xFFF0E3FC)
val Bubblegum = Color(0xFFE8687D)
val Blush = Color(0xFFFD919E)
val BlushSoft = Color(0xFFFFE1E6)
val Honey = Color(0xFFE59A1F)
val HoneySoft = Color(0xFFFFEDC9)
val Cream = Color(0xFFFFF7F9)

val NightBg = Color(0xFF1C1520)
val NightInk = Color(0xFFF3E9EE)

/** A pastel background and a matching text colour, one pair per category. */
@Immutable
data class Tint(val container: Color, val content: Color)

private val LightTints = listOf(
    Tint(Color(0xFFF0E3FC), Color(0xFF5B3391)), // lilac
    Tint(Color(0xFFFFE1E6), Color(0xFF9C2F44)), // pink
    Tint(Color(0xFFDDF5EA), Color(0xFF1F6B4F)), // mint
    Tint(Color(0xFFFFEDC9), Color(0xFF8A5600)), // honey
    Tint(Color(0xFFDDEBFF), Color(0xFF2B5797)), // sky
    Tint(Color(0xFFFFE3D1), Color(0xFF9A4515)), // peach
)

private val DarkTints = listOf(
    Tint(Color(0xFF45305F), Color(0xFFE6D2FA)),
    Tint(Color(0xFF5C2A36), Color(0xFFFFD3DB)),
    Tint(Color(0xFF1F4A3B), Color(0xFFC6F0DD)),
    Tint(Color(0xFF55401A), Color(0xFFFFE2A8)),
    Tint(Color(0xFF243D61), Color(0xFFCFE1FF)),
    Tint(Color(0xFF5A3420), Color(0xFFFFD6BD)),
)

/** Pastel pair number [index] (wraps around), in light or dark form. */
@Composable
fun tintAt(index: Int): Tint {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val tints = if (dark) DarkTints else LightTints
    return tints[Math.floorMod(index, tints.size)]
}

/** The same category always gets the same colour (picked from its name). */
@Composable
fun categoryTint(category: String): Tint = tintAt(category.hashCode())
