package com.cns.accounts.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.RequestMetaText

// The onboarding look, shared by the sign-in pages and the lock screen: the bottom glow and
// the 48dp pill buttons.

val PillButtonHeight = 48.dp

/**
 * Vector10, now drawn rather than a pre-blurred bitmap: an oval glow below the bottom edge,
 * 140% the export's size so it runs off the left, right and bottom of the screen, brighter, and
 * with a firmer core than the export's heavy blur.
 */
@Composable
fun OnboardingGlow(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = size.width * 0.62f * GlowScale
        val center = Offset(size.width / 2f, size.height + radius * 0.18f)
        // The gradient is circular; squashing it vertically makes the oval.
        scale(scaleX = 1f, scaleY = 0.62f, pivot = center) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to GlowCore,
                    0.38f to GlowCore.copy(alpha = 0.85f),
                    0.7f to GlowCore.copy(alpha = 0.3f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

private const val GlowScale = 1.4f
private val GlowCore = Color(0xFF3A3A3A)

/**
 * The onboarding button: a 48dp pill, white (primary) or the elevated surface (secondary), that
 * dips slightly while pressed. While [isBusy] it shows the app's spinner instead of its label.
 */
@Composable
fun PillButton(
    label: String,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    isBusy: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 900f), label = "pill-press")
    val container = if (secondary) colors.elevatedSurface else Color.White
    val content = if (secondary) Color.White else Color.Black
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PillButtonHeight)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(50))
            .background(if (isBusy) container.copy(alpha = 0.85f) else container)
            .clickable(interactionSource = interaction, indication = null, enabled = !isBusy, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = if (isBusy) null else label,
            transitionSpec = {
                (slideInVertically(spring(stiffness = 600f)) { it / 2 } + fadeIn(tween(160)))
                    .togetherWith(slideOutVertically(tween(140)) { -it / 2 } + fadeOut(tween(100)))
            },
            label = "pill-label",
        ) { text ->
            if (text == null) {
                RefreshSpinner(phase = { BusySpinnerPhase }, size = 22.dp, color = content)
            } else {
                Text(text, color = content, style = RequestMetaText, maxLines = 1)
            }
        }
    }
}

