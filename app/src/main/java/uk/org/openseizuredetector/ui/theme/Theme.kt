package uk.org.openseizuredetector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable

private val DarkColorPalette = darkColors(
    primary = Purple200,
    primaryVariant = Purple700,
    secondary = Teal200
)

private val LightColorPalette = lightColors(
    primary = OsdTeal,
    primaryVariant = OsdCharcoal,
    secondary = OsdBrown,
    background = OsdBackground,
    surface = OsdBackground,
    onPrimary = OsdWhite,
    onSecondary = OsdWhite,
    onBackground = OsdCharcoal,
    onSurface = OsdCharcoal,
    error = OsdBrown
)

@Composable
fun OpenSeizureDetectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColorPalette // TODO: Define a proper dark theme palette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
