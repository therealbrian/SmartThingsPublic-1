package com.plexbooks.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PlexDarkColors = darkColorScheme(
    primary = PlexOrange,
    onPrimary = PlexBackground,
    primaryContainer = PlexOrangeDark,
    onPrimaryContainer = PlexOnSurface,
    secondary = PlexOrange,
    onSecondary = PlexBackground,
    background = PlexBackground,
    onBackground = PlexOnSurface,
    surface = PlexSurface,
    onSurface = PlexOnSurface,
    surfaceVariant = PlexSurfaceVariant,
    onSurfaceVariant = PlexOnSurfaceVariant,
    error = PlexError,
)

@Composable
fun PlexBooksTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PlexDarkColors,
        typography = Typography,
        content = content
    )
}
