package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
    lightColorScheme(
        primary = SignalLightPrimary,
        onPrimary = SignalLightOnPrimary,
        primaryContainer = SignalLightPrimaryContainer,
        onPrimaryContainer = SignalLightOnPrimaryContainer,
        inversePrimary = SignalLightInversePrimary,
        secondary = SignalLightSecondary,
        onSecondary = SignalLightOnSecondary,
        secondaryContainer = SignalLightSecondaryContainer,
        onSecondaryContainer = SignalLightOnSecondaryContainer,
        tertiary = SignalLightTertiary,
        onTertiary = SignalLightOnTertiary,
        tertiaryContainer = SignalLightTertiaryContainer,
        onTertiaryContainer = SignalLightOnTertiaryContainer,
        error = SignalLightError,
        onError = SignalLightOnError,
        errorContainer = SignalLightErrorContainer,
        onErrorContainer = SignalLightOnErrorContainer,
        background = SignalLightBackground,
        onBackground = SignalLightOnBackground,
        surface = SignalLightSurface,
        onSurface = SignalLightOnSurface,
        surfaceVariant = SignalLightSurfaceVariant,
        onSurfaceVariant = SignalLightOnSurfaceVariant,
        outline = SignalLightOutline,
        outlineVariant = SignalLightOutlineVariant,
        inverseSurface = SignalLightInverseSurface,
        inverseOnSurface = SignalLightInverseOnSurface,
        surfaceTint = SignalLightSurfaceTint,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = DarkPrimary,
        onPrimary = DarkOnPrimary,
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = DarkOnPrimaryContainer,
        inversePrimary = DarkInversePrimary,
        secondary = DarkSecondary,
        onSecondary = DarkOnSecondary,
        secondaryContainer = DarkSecondaryContainer,
        onSecondaryContainer = DarkOnSecondaryContainer,
        tertiary = DarkTertiary,
        onTertiary = DarkOnTertiary,
        tertiaryContainer = DarkTertiaryContainer,
        onTertiaryContainer = DarkOnTertiaryContainer,
        error = DarkError,
        onError = DarkOnError,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer,
        background = DarkBackground,
        onBackground = DarkOnBackground,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        inverseSurface = DarkInverseSurface,
        inverseOnSurface = DarkInverseOnSurface,
        surfaceTint = DarkSurfaceTint,
    )

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set dynamicColor to false by default to strictly enforce Classic Signal brand design
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
