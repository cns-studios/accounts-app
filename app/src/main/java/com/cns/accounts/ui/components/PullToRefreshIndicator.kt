package com.cns.accounts.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.RequestCaptionText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A pull-to-refresh container that reuses Material3's [PullToRefreshState] gesture engine
 * (distance tracking, rubber-band overscroll, threshold, release-to-trigger) but draws its own
 * indicator: a small round chip holding the [RefreshSpinner], which drops in with the pull and,
 * once a refresh starts, runs through all three of the spinner's shapes at least once, even when
 * the refresh itself finishes sooner, before it leaves.
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
        RefreshIndicator(
            state = state,
            isRefreshing = isRefreshing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        )
    }
}

private val ChipSize = 44.dp
private val SpinnerSize = 24.dp
// How far below the status bar the chip rests while refreshing.
private val RestingOffset = 18.dp
private const val ShapeHoldMillis = 380L
private const val MorphMillis = 420
private const val ExitMillis = 260

@Composable
private fun RefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val refreshing by rememberUpdatedState(isRefreshing)
    val phase = remember { Animatable(0f) }
    // 0 = hidden above the top edge, 1 = resting below the status bar.
    val presence = remember { Animatable(0f) }
    var cycling by remember { mutableStateOf(false) }

    // Fires once each time an active pull first crosses the release threshold.
    val armedToRefresh = !isRefreshing && !cycling && state.distanceFraction >= 1f
    LaunchedEffect(armedToRefresh) {
        if (armedToRefresh) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing || cycling) return@LaunchedEffect
        cycling = true
        // Its own scope: the cycle must outlive the refresh that started it.
        scope.launch {
            // Picks up from wherever the released pull left the chip.
            presence.snapTo(state.distanceFraction.coerceIn(0f, 1.2f))
            launch { presence.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f)) }
            phase.snapTo(0f)
            while (true) {
                for (target in 1..2) {
                    delay(ShapeHoldMillis)
                    phase.animateTo(target.toFloat(), tween(MorphMillis, easing = LinearEasing))
                }
                delay(ShapeHoldMillis)
                // All three shapes shown at least once; keep looping only while still refreshing.
                if (!refreshing) break
                phase.animateTo(3f, tween(MorphMillis, easing = LinearEasing))
                phase.snapTo(0f)
            }
            presence.animateTo(0f, tween(ExitMillis))
            phase.snapTo(0f)
            cycling = false
        }
    }

    // While dragging (no cycle yet) the chip follows the finger directly.
    val pulling = !cycling && !isRefreshing
    val shown = if (pulling) state.distanceFraction.coerceIn(0f, 1.2f) else presence.value
    if (shown <= 0f) return
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .graphicsLayer {
                val travel = (ChipSize + RestingOffset).toPx()
                translationY = -ChipSize.toPx() + travel * shown
                val visible = shown.coerceIn(0f, 1f)
                alpha = visible
                scaleX = 0.55f + 0.45f * visible
                scaleY = scaleX
                // Leaving (and the start of a pull) melts the chip in and out of a blur.
                val blur = (1f - visible) * 6.dp.toPx()
                renderEffect = if (blur > 0.1f) BlurEffect(blur, blur, TileMode.Decal) else null
            }
            .size(ChipSize)
            .clip(CircleShape)
            .background(colors.surface)
            .border(1.dp, colors.elevatedSurface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        RefreshSpinner(
            phase = { phase.value },
            size = SpinnerSize,
            // The pull itself winds the arcs up, on top of their own spin.
            rotationOffset = { if (pulling) state.distanceFraction * 270f else 0f },
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 200)
@Composable
private fun NothingPullToRefreshBoxPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        NothingPullToRefreshBox(isRefreshing = true, onRefresh = {}, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Content", color = LocalAppColors.current.onSurface, style = RequestCaptionText)
            }
        }
    }
}
