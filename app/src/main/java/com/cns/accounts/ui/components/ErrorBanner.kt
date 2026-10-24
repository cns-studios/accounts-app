package com.cns.accounts.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.ToastText

/**
 * A failed submit, shown where it happened (in a dialog or on the sign-in form): a tinted
 * banner with a status mark that unfolds above the form's button and folds away once the user
 * edits a field. Pair it with [rememberErrorShake] so the form itself shakes and buzzes too.
 */
@Composable
fun ErrorBanner(message: String?, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues()) {
    val colors = LocalAppColors.current
    // Keeps the last text while folding away, so it doesn't go blank mid-animation.
    val lastMessage = remember { arrayOfNulls<String>(1) }
    if (message != null) lastMessage[0] = message
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 600f), expandFrom = Alignment.Top) +
            fadeIn(tween(180, delayMillis = 60)) +
            scaleIn(spring(dampingRatio = 0.7f, stiffness = 600f), initialScale = 0.94f),
        exit = shrinkVertically(tween(200)) + fadeOut(tween(140)),
    ) {
        Row(
            modifier = Modifier
                // Inside the animation, so the padding folds away with the banner.
                .padding(contentPadding)
                .fillMaxWidth()
                .clip(RoundedCornerShape(BannerRadius))
                .background(colors.accent.copy(alpha = 0.14f))
                .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(BannerRadius))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Assertive },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", color = colors.onSurface, style = ToastText.copy(fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium), textAlign = TextAlign.Center)
            }
            Text(lastMessage[0].orEmpty(), modifier = Modifier.weight(1f), color = colors.onSurface, style = ToastText)
        }
    }
}

private val BannerRadius = 12.dp

/**
 * A horizontal shake plus an error buzz each time [error] turns non-null (a new failed submit:
 * the screens clear the error when the user retries). Apply the result to the form's container.
 */
@Composable
fun rememberErrorShake(error: Any?): Modifier {
    val haptic = LocalHapticFeedback.current
    val offset = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (error == null) return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.Reject)
        offset.animateTo(
            0f,
            keyframes {
                durationMillis = 420
                -9f at 50
                8f at 120
                -6f at 190
                4f at 260
                -2f at 330
            },
        )
    }
    return Modifier.graphicsLayer { translationX = offset.value * density }
}

@Preview(showBackground = true, backgroundColor = 0xFF2D2D2D, widthDp = 340)
@Composable
private fun ErrorBannerPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        ErrorBanner("Wrong username or password.", modifier = Modifier.padding(16.dp))
    }
}
