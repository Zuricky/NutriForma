package com.nutriforma.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * NutriForma is a light-only theme: no dark scheme, no dynamic color:
 * the Mediterranean palette must look identical on every device.
 */
private val NutriFormaLightColorScheme = lightColorScheme(
    primary = HerbGreen,
    onPrimary = Color.White,
    primaryContainer = HerbGreenContainer,
    onPrimaryContainer = OnHerbGreenContainer,
    secondary = TomatoRed,
    onSecondary = Color.White,
    secondaryContainer = TomatoRedContainer,
    onSecondaryContainer = OnTomatoRedContainer,
    tertiary = EggplantPurple,
    onTertiary = Color.White,
    tertiaryContainer = EggplantPurpleContainer,
    onTertiaryContainer = OnEggplantPurpleContainer,
    background = WarmCream,
    onBackground = Ink,
    surface = WarmCream,
    onSurface = Ink,
    surfaceVariant = SoftBeige,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = WarmCream,
    surfaceContainer = SoftBeige,
    surfaceContainerHigh = SoftBeige,
    surfaceContainerHighest = OutlineVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
)

@Composable
fun NutriFormaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NutriFormaLightColorScheme,
        typography = Typography,
        content = content,
    )
}
