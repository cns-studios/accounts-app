package com.cns.accounts.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cns.accounts.R

// token.json: fontFamily=Rubik; files sourced from Google Fonts.
private val Rubik = FontFamily(
    Font(R.font.rubik_regular, FontWeight.Normal),
    Font(R.font.rubik_medium, FontWeight.Medium),
)

// token.json: fontFamily=Instrument Serif; file sourced from Google Fonts.
private val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal),
)

// token.json: fontFamily=Geist; no Geist resource is present in the export.
private val GeistFallback = FontFamily.SansSerif
// token.json: fontFamily=JetBrains Mono; file sourced from Google Fonts.
private val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
)

val Typography = Typography(
    bodyLarge = TextStyle(fontFamily = Rubik, fontSize = 16.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = Rubik, fontSize = 20.sp, lineHeight = 24.sp),
    headlineSmall = TextStyle(fontFamily = Rubik, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium),
)

val HomeNavigationText = TextStyle(fontFamily = GeistFallback, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
val ProfileNameText = TextStyle(fontFamily = InstrumentSerif, fontSize = 36.sp, lineHeight = 43.sp)
val AccountCodeText = TextStyle(fontFamily = JetBrainsMono, fontSize = 36.sp, lineHeight = 43.sp)
val RequestTitleText = TextStyle(fontFamily = Rubik, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium)
val RequestMetaText = TextStyle(fontFamily = Rubik, fontSize = 20.sp, lineHeight = 24.sp)
val RequestCaptionText = TextStyle(fontFamily = Rubik, fontSize = 15.sp, lineHeight = 19.sp)

// history.json: History event card text styles.
val HistoryEventTitleText = TextStyle(fontFamily = Rubik, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
val HistoryEventTimeText = TextStyle(fontFamily = Rubik, fontSize = 16.sp, lineHeight = 24.sp)
val HistoryEventDetailText = TextStyle(fontFamily = Rubik, fontSize = 15.sp, lineHeight = 18.sp)

// settings.tokens.json: the "80%" security-completion figure on the Security center card.
val SecurityPercentText = TextStyle(fontFamily = JetBrainsMono, fontSize = 40.sp, lineHeight = 48.sp)
val SecurityFactorLabelText = TextStyle(fontFamily = Rubik, fontSize = 16.sp, lineHeight = 20.sp)

// settings.tokens.json (Inputfield): the reusable input-field/action dialog text styles.
val DialogTitleText = TextStyle(fontFamily = InstrumentSerif, fontSize = 32.sp, lineHeight = 38.sp)
val DialogSubtitleText = TextStyle(fontFamily = Rubik, fontSize = 20.sp, lineHeight = 24.sp)
val DialogButtonText = TextStyle(fontFamily = Rubik, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)

// search.tokens.json: search-bar input text and result-card title styles.
val SearchBarText = TextStyle(fontFamily = Rubik, fontSize = 16.sp, lineHeight = 20.sp)
val SearchResultTitleText = TextStyle(fontFamily = Rubik, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
