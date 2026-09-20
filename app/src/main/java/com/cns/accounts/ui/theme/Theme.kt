package com.cns.accounts.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalAppColors = staticCompositionLocalOf { AppColors() }
val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalRadii = staticCompositionLocalOf { Radii() }
val LocalDimensions = staticCompositionLocalOf { DesignDimensions() }

private val DarkScheme = darkColorScheme(
    primary = AccentRed,
    secondary = SurfaceElevated,
    background = Ink,
    surface = SurfaceDark,
    onBackground = OnDark,
    onSurface = OnDark,
)

@Composable
fun CNSAccountsTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = AppColors()
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalSpacing provides Spacing(),
        LocalRadii provides Radii(),
        LocalDimensions provides DesignDimensions(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else lightColorScheme(
                primary = AccentRed,
                background = Ink,
                surface = SurfaceDark,
                onBackground = OnDark,
                onSurface = OnDark,
            ),
            typography = Typography,
            content = content,
        )
    }
}
