package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Emerald500,
    onPrimary = Obsidian950,
    primaryContainer = Emerald900,
    onPrimaryContainer = Emerald400,
    secondary = Cyan400,
    onSecondary = Obsidian950,
    secondaryContainer = Cyan900,
    onSecondaryContainer = Cyan400,
    tertiary = Amber400,
    onTertiary = Obsidian950,
    tertiaryContainer = Amber900,
    onTertiaryContainer = Amber400,
    error = Coral400,
    onError = Obsidian950,
    errorContainer = Coral900,
    onErrorContainer = Coral400,
    background = Obsidian950,
    onBackground = Mist100,
    surface = Obsidian900,
    onSurface = Mist100,
    surfaceVariant = Navy800,
    onSurfaceVariant = Slate300,
    surfaceContainerLowest = Obsidian950,
    surfaceContainerLow = Obsidian900,
    surfaceContainer = Navy850,
    surfaceContainerHigh = Navy800,
    surfaceContainerHighest = Slate700,
    outline = Slate700,
    outlineVariant = Navy800
)

private val LightColorScheme = lightColorScheme(
    primary = Emerald500,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF065F46),
    secondary = Cyan500,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF075985),
    tertiary = Amber500,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF92400E),
    error = Coral500,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    background = Mist50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Mist100,
    onSurfaceVariant = Slate600,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Mist50,
    surfaceContainer = Mist100,
    surfaceContainerHigh = Mist200,
    surfaceContainerHighest = Slate300,
    outline = Slate300,
    outlineVariant = Mist200
)

val FieldPulseShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FieldPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = FieldPulseTypography,
        shapes = FieldPulseShapes,
        content = content
    )
}
