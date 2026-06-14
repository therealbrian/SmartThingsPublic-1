package com.plexbooks.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PlexLightColors = lightColorScheme(
    primary = UKBlue,
    onPrimary = AppBackground,
    primaryContainer = UKBlueLight,
    onPrimaryContainer = UKBlueDark,
    secondary = UKBlue,
    onSecondary = AppBackground,
    secondaryContainer = UKBlueLight,
    onSecondaryContainer = UKBlueDark,
    background = AppBackground,
    onBackground = AppOnSurface,
    surface = AppSurface,
    onSurface = AppOnSurface,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = AppOnSurfaceVariant,
    error = AppError,
    onError = AppBackground,
)

@Composable
fun PlexBooksTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PlexLightColors,
        typography = Typography,
        content = content
    )
}
