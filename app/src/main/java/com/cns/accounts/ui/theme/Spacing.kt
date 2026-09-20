package com.cns.accounts.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
    val xs: androidx.compose.ui.unit.Dp = 4.dp,
    val sm: androidx.compose.ui.unit.Dp = 8.dp,
    val md: androidx.compose.ui.unit.Dp = 14.dp,
    val lg: androidx.compose.ui.unit.Dp = 18.dp,
    val xl: androidx.compose.ui.unit.Dp = 27.dp,
    val section: androidx.compose.ui.unit.Dp = 18.dp,
)

@Immutable
data class Radii(
    val card: androidx.compose.ui.unit.Dp = 17.dp,
    val pill: androidx.compose.ui.unit.Dp = 68.dp,
    val nav: androidx.compose.ui.unit.Dp = 45.dp,
    val circle: androidx.compose.ui.unit.Dp = 100.dp,
)

@Immutable
data class DesignDimensions(
    val contentMaxWidth: androidx.compose.ui.unit.Dp = 340.dp,
    val avatar: androidx.compose.ui.unit.Dp = 70.dp,
    val navHeight: androidx.compose.ui.unit.Dp = 54.dp,
    val navSelectedWidth: androidx.compose.ui.unit.Dp = 194.dp,
    val navSelectedOuterWidth: androidx.compose.ui.unit.Dp = 202.dp,
    val navSelectedFrameWidth: androidx.compose.ui.unit.Dp = 252.dp,
    val navSelectedInnerHeight: androidx.compose.ui.unit.Dp = 46.dp,
    val navWidth: androidx.compose.ui.unit.Dp = 366.dp,
    val requestButtonRadius: androidx.compose.ui.unit.Dp = 12.dp,
    val copyRadius: androidx.compose.ui.unit.Dp = 24.dp,
    val requestHeight: androidx.compose.ui.unit.Dp = 187.dp,
    val icon: androidx.compose.ui.unit.Dp = 24.dp,
    val headerTop: androidx.compose.ui.unit.Dp = 62.dp,
    val requestCardHeight: androidx.compose.ui.unit.Dp = 146.dp,
    val accountCardHeight: androidx.compose.ui.unit.Dp = 137.dp,
    val serviceCardHeight: androidx.compose.ui.unit.Dp = 110.dp,
    val actionHeight: androidx.compose.ui.unit.Dp = 41.dp,
    val navIconSize: androidx.compose.ui.unit.Dp = 38.dp,
)
