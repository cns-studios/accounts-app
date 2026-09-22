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
// timer square "spent" color
val TimerSpent = Color(0xFF616161)
// history.json: rgba(118, 118, 118, 1)
val MutedText = Color(0xFF767676)
// settings.tokens.json (Rectangle40, off-state track): rgba(150, 150, 150, 1)
val ToggleOffTrack = Color(0xFF969696)
// settings.tokens.json (Inputfield subtitle): rgba(149, 149, 149, 1)
val DialogSubtitleGray = Color(0xFF959595)
// settings.tokens.json (Inputfield Group12/Group13 fill): rgba(76, 76, 76, 1)
val FieldSurface = Color(0xFF4C4C4C)
// settings.tokens.json (Inputfield placeholder text): rgba(109, 109, 109, 1)
val FieldPlaceholder = Color(0xFF6D6D6D)

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
    val timerSpent: Color = TimerSpent,
    val mutedText: Color = MutedText,
    val toggleOffTrack: Color = ToggleOffTrack,
    val dialogSubtitle: Color = DialogSubtitleGray,
    val fieldSurface: Color = FieldSurface,
    val fieldPlaceholder: Color = FieldPlaceholder,
)
