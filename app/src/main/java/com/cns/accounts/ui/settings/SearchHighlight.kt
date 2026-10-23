package com.cns.accounts.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.cns.accounts.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

/**
 * Settings rows a search result can point at (ids from ui/search/SearchIndex.kt), with the
 * index of the list item that holds each, so Settings can scroll there before highlighting it.
 */
internal enum class SettingsTarget(val id: String, val itemIndex: Int) {
    Username("username", 0),
    Avatar("avatar", 0),
    SecurityCenter("security-center", 1),
    Biometric("biometric", 2),
    Notifications("notifications", 2),
    TwoFactor("2fa", 2),
    SecurityApp("security-app", 2),
    RecoveryPhrase("recovery-phrase", 2),
    Password("password", 3),
    LogOut("log-out", 3),
    Delete("delete", 4),
    ;

    companion object {
        fun fromId(id: String?): SettingsTarget? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Briefly flashes an accent-tinted rounded backdrop behind the row a search result led to
 * (drawn slightly outside the row so the row's own content stays put), once, after the page
 * has scrolled there.
 */
@Composable
internal fun Modifier.searchHighlight(active: Boolean): Modifier {
    val strength = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        delay(HighlightStartDelayMillis)
        strength.animateTo(1f, tween(220))
        delay(900)
        strength.animateTo(0f, tween(700))
    }
    val color = LocalAppColors.current.accent
    return drawBehind {
        val value = strength.value
        if (value <= 0f) return@drawBehind
        val inset = Offset(10.dp.toPx(), 8.dp.toPx())
        drawRoundRect(
            color = color.copy(alpha = 0.35f * value),
            topLeft = Offset(-inset.x, -inset.y),
            size = Size(size.width + inset.x * 2, size.height + inset.y * 2),
            cornerRadius = CornerRadius(14.dp.toPx()),
        )
    }
}

// Long enough for the scroll to the row to finish first.
private const val HighlightStartDelayMillis = 350L
