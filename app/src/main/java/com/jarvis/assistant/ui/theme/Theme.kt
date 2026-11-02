package com.jarvis.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ElectricOrange,
    onPrimary = AbsoluteBlack,
    background = AbsoluteBlack,
    onBackground = IceWhite,
    surface = GraphiteBlack,
    onSurface = IceWhite,
    surfaceVariant = GraphiteSurfaceDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = GraphiteBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricOrange,
    onPrimary = PureWhiteLight,
    background = IceWhite,
    onBackground = GraphiteBlack,
    surface = PureWhiteLight,
    onSurface = GraphiteBlack,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight
)

@Composable
fun JarvisTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
