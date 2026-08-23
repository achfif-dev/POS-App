package com.example.posapp.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PosPrimaryLight,
    onPrimary = PosOnPrimaryLight,
    primaryContainer = PosPrimaryContainerLight,
    onPrimaryContainer = PosOnPrimaryContainerLight,
    secondary = PosSecondaryLight,
    onSecondary = PosOnSecondaryLight,
    secondaryContainer = PosSecondaryContainerLight,
    onSecondaryContainer = PosOnSecondaryContainerLight,
    tertiary = PosTertiaryLight,
    onTertiary = PosOnTertiaryLight,
    tertiaryContainer = PosTertiaryContainerLight,
    onTertiaryContainer = PosOnTertiaryContainerLight,
    error = PosErrorLight,
    onError = PosOnErrorLight,
    errorContainer = PosErrorContainerLight,
    onErrorContainer = PosOnErrorContainerLight,
    background = PosBackgroundLight,
    onBackground = PosOnBackgroundLight,
    surface = PosSurfaceLight,
    onSurface = PosOnSurfaceLight,
    surfaceVariant = PosSurfaceVariantLight,
    onSurfaceVariant = PosOnSurfaceVariantLight,
    outline = PosOutlineLight,
    surfaceContainer = PosSurfaceContainerLight,
    surfaceContainerHigh = PosSurfaceContainerHighLight,
    surfaceContainerLow = PosSurfaceContainerLowLight
)

private val DarkColors = darkColorScheme(
    primary = PosPrimaryDark,
    onPrimary = PosOnPrimaryDark,
    primaryContainer = PosPrimaryContainerDark,
    onPrimaryContainer = PosOnPrimaryContainerDark,
    secondary = PosSecondaryDark,
    onSecondary = PosOnSecondaryDark,
    secondaryContainer = PosSecondaryContainerDark,
    onSecondaryContainer = PosOnSecondaryContainerDark,
    tertiary = PosTertiaryDark,
    onTertiary = PosOnTertiaryDark,
    tertiaryContainer = PosTertiaryContainerDark,
    onTertiaryContainer = PosOnTertiaryContainerDark,
    error = PosErrorDark,
    onError = PosOnErrorDark,
    errorContainer = PosErrorContainerDark,
    onErrorContainer = PosOnErrorContainerDark,
    background = PosBackgroundDark,
    onBackground = PosOnBackgroundDark,
    surface = PosSurfaceDark,
    onSurface = PosOnSurfaceDark,
    surfaceVariant = PosSurfaceVariantDark,
    onSurfaceVariant = PosOnSurfaceVariantDark,
    outline = PosOutlineDark,
    surfaceContainer = PosSurfaceContainerDark,
    surfaceContainerHigh = PosSurfaceContainerHighDark,
    surfaceContainerLow = PosSurfaceContainerLowDark
)

/**
 * Tema Material 3 modern minimalis untuk seluruh aplikasi.
 * Mengikuti tema gelap sistem secara otomatis (isSystemInDarkTheme).
 */
@Composable
fun PosAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PosTypography,
        shapes = PosShapes,
        content = content
    )
}
