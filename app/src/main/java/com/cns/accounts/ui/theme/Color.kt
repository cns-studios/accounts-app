package com.cns.accounts.ui.theme

import androidx.compose.ui.graphics.Color

// token.json: rgba(0, 0, 0, 1)
val Ink = Color(0xFF000000)
// token.json: rgba(26, 26, 26, 1)
val SurfaceDark = Color(0xFF1A1A1A)
// token.json: rgba(45, 45, 45, 1)
val SurfaceElevated = Color(0xFF2D2D2D)
// token.json: rgba(200, 16, 46, 1)
val AccentRed = Color(0xFFC8102E)
// token.json: rgba(215, 25, 32, 1)
val ServiceRed = Color(0xFFD71920)
// token.json: rgba(255, 255, 255, 1)
val OnDark = Color(0xFFFFFFFF)
// token.json: rgba(0, 0, 0, 1)
val OnLight = Color(0xFF000000)
// token.json: rgba(217, 217, 217, 1)
val MutedLight = Color(0xFFD9D9D9)

data class AppColors(
    val background: Color = Ink,
    val surface: Color = SurfaceDark,
    val elevatedSurface: Color = SurfaceElevated,
    val accent: Color = AccentRed,
    val serviceAccent: Color = ServiceRed,
    val onBackground: Color = OnDark,
    val onSurface: Color = OnDark,
    val onLightSurface: Color = OnLight,
    val mutedSurface: Color = MutedLight,
)
