package com.cns.accounts.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.RequestCaptionText
import kotlin.math.PI
import kotlin.math.sin

/**
 * A pull-to-refresh container that reuses Material3's [PullToRefreshState] gesture engine
 * (distance tracking, rubber-band overscroll, threshold, release-to-trigger) but replaces its
 * default spinner chrome entirely with [NothingPullToRefreshIndicator] — a monochrome, full-width
 * dithered curtain instead of the platform spinner.
 */
@Composable
fun NothingPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.pullToRefresh(state = state, isRefreshing = isRefreshing, onRefresh = onRefresh),
    ) {
        content()
        NothingPullToRefreshIndicator(
            state = state,
            isRefreshing = isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

// A curtain that unrolls from the very top edge down to roughly the release threshold, its
// height tracking `state.distanceFraction` (Material3's own rubber-banded pull distance — it
// already resists past the threshold, we just stop clamping it away). While a finger is actually
// on screen this tracks with zero lag (snap()) so the pull itself feels immediate; the spring
// only kicks in once the gesture ends — Material3 flips `isAnimating` true while it settles the
// value to hidden/threshold, which is exactly when a bouncy snap-back reads as alive rather than
// laggy. Same "snap while dragging, spring once released" split already used for the nav pill's
// swipe in BottomNavigation.kt.
//
// The curtain's bottom edge is a full-width ordered dither (4x4 Bayer matrix — the classic 1-bit
// trick for faking a gradient out of two tones) whose threshold is perturbed by a traveling sine
// wave, so the edge ripples across the width instead of sitting flat. White-on-black only.
private val CurtainSettleSpring = spring<Float>(dampingRatio = 0.62f, stiffness = 340f)
private const val MaxOverpull = 1.35f
private val CellSize = 4.dp
private const val DitherBandThickness = 0.4f
private const val WaveAmplitude = 0.16f
private const val WaveCycles = 2.2f
private const val WavePeriodMillis = 1600
private val BayerThreshold4x4 = floatArrayOf(
    0f, 8f, 2f, 10f,
    12f, 4f, 14f, 6f,
    3f, 11f, 1f, 9f,
    15f, 7f, 13f, 5f,
).map { it / 16f }

@Composable
private fun NothingPullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    maxHeight: Dp = PullToRefreshDefaults.IndicatorMaxDistance,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // Fires once each time an active pull first crosses the release threshold — the same tick
    // other controls in this app (e.g. the tab switcher) give on a meaningful state change.
    val armedToRefresh = !isRefreshing && state.distanceFraction >= 1f
    LaunchedEffect(armedToRefresh) {
        if (armedToRefresh) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val targetFraction = if (isRefreshing) 1f else state.distanceFraction.coerceIn(0f, MaxOverpull)
    // Settling (not actively dragged) whenever Material3 itself is driving the value toward
    // hidden/threshold, or we're driving it toward the pinned refreshing position ourselves.
    val settling = isRefreshing || state.isAnimating
    val curtainFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = if (settling) CurtainSettleSpring else snap(),
        label = "pull-refresh-curtain-fraction",
    )
    if (curtainFraction <= 0f) return

    // The traveling ripple only needs to run while there's something to show it on — an active
    // pull or an ongoing refresh — never as a background loop while the curtain is hidden.
    val rippleActive = isRefreshing || state.distanceFraction > 0f
    val wavePhase: State<Float> = if (rippleActive) {
        val wave = rememberInfiniteTransition(label = "pull-refresh-wave")
        wave.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(WavePeriodMillis, easing = LinearEasing)),
            label = "pull-refresh-wave-phase",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val dotColor = LocalAppColors.current.onSurface
    val maxHeightPx = with(density) { maxHeight.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { (curtainFraction * maxHeightPx).toDp() }),
    ) {
        if (size.height <= 0f) return@Canvas
        val cellPx = CellSize.toPx()
        val cols = (size.width / cellPx).toInt().coerceAtLeast(1)
        val rows = (size.height / cellPx).toInt().coerceAtLeast(1)
        val phase = wavePhase.value
        for (row in 0 until rows) {
            val rowFraction = (row + 0.5f) / rows
            for (col in 0 until cols) {
                val colFraction = (col + 0.5f) / cols
                val waveOffset = sin(colFraction * WaveCycles * 2f * PI.toFloat() + phase) * WaveAmplitude
                val ditherValue = ((1f + waveOffset) - rowFraction) / DitherBandThickness
                val threshold = BayerThreshold4x4[(row % 4) * 4 + (col % 4)]
                if (ditherValue.coerceIn(0f, 1f) > threshold) {
                    drawRect(
                        color = dotColor,
                        topLeft = Offset(col * cellPx, row * cellPx),
                        size = Size(cellPx - 1f, cellPx - 1f),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 200)
@Composable
private fun NothingPullToRefreshBoxPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        NothingPullToRefreshBox(isRefreshing = false, onRefresh = {}, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Content", color = LocalAppColors.current.onSurface, style = RequestCaptionText)
            }
        }
    }
}
