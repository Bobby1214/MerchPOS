package com.bobbykong.merchpos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Grape,
    onPrimary = Color.White,
    primaryContainer = LilacSoft,
    onPrimaryContainer = Color(0xFF3B1F63),
    secondary = Bubblegum,
    onSecondary = Color.White,
    secondaryContainer = BlushSoft,
    onSecondaryContainer = Color(0xFF6A1D2D),
    tertiary = Honey,
    onTertiary = Color.White,
    tertiaryContainer = HoneySoft,
    onTertiaryContainer = Color(0xFF5C3A00),
    error = Color(0xFFD23A4E),
    errorContainer = Color(0xFFFFDADF),
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF5E6EE),
    onSurfaceVariant = Color(0xFF7A6470),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFFBEFF4),
    surfaceContainerHigh = Color(0xFFF7E8F0),
    surfaceContainerHighest = Color(0xFFF2E1EB),
    outline = Color(0xFFCDB5C3),
    outlineVariant = Color(0xFFEEDDE6),
)

private val DarkColorScheme = darkColorScheme(
    primary = Lilac,
    onPrimary = Color(0xFF3B1F63),
    primaryContainer = Color(0xFF55387D),
    onPrimaryContainer = LilacSoft,
    secondary = Blush,
    onSecondary = Color(0xFF5A1424),
    secondaryContainer = Color(0xFF6E2E3C),
    onSecondaryContainer = BlushSoft,
    tertiary = Color(0xFFFFC86B),
    onTertiary = Color(0xFF4A2F00),
    tertiaryContainer = Color(0xFF5E4310),
    onTertiaryContainer = HoneySoft,
    background = NightBg,
    onBackground = NightInk,
    surface = NightBg,
    onSurface = NightInk,
    surfaceVariant = Color(0xFF3A2E3D),
    onSurfaceVariant = Color(0xFFCDBBC8),
    surfaceContainerLowest = Color(0xFF17111A),
    surfaceContainerLow = Color(0xFF261D29),
    surfaceContainer = Color(0xFF2B2130),
    surfaceContainerHigh = Color(0xFF342938),
    surfaceContainerHighest = Color(0xFF3E3242),
    outline = Color(0xFF8C7A88),
    outlineVariant = Color(0xFF4A3D4C),
)

// Big round corners everywhere: the soft "shopping app" look
val MerchShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Our own colours always (no Android 12 wallpaper colours), so the app looks the same on every phone. */
@Composable
fun MerchPOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = MerchShapes,
        content = content,
    )
}
