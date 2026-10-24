package com.cns.accounts.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.R
import com.cns.accounts.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The launch screen: the monochrome CNS wordmark, dimmed, with a light sweeping across it,
 * held while the app starts and while [loading] (account data being fetched) is true, then
 * dissolved (scale up, blur, fade) to reveal [content], which settles in from slightly smaller
 * underneath. [sessionKey] changing to a new non-null value (a new sign-in) brings it back for
 * that account's load; changing to null (signed out) doesn't.
 * It always stays up long enough for one full sweep, so it never just flickers.
 */
@Composable
fun SplashOverlay(
    loading: Boolean,
    sessionKey: Any?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val isLoading by rememberUpdatedState(loading)
    // 0 = splash fully up, 1 = app fully revealed.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(sessionKey) {
        if (sessionKey == null && reveal.value > 0f) return@LaunchedEffect
        if (reveal.value > 0f) reveal.animateTo(0f, tween(SplashReturnMillis, easing = FastOutSlowInEasing))
        val shownAt = System.currentTimeMillis()
        // Offline, cached data is still worth showing: don't hold the app hostage to the network.
        withTimeoutOrNull(MaxHoldMillis) { snapshotFlow { isLoading }.first { !it } }
        val remaining = MinHoldMillis - (System.currentTimeMillis() - shownAt)
        if (remaining > 0) kotlinx.coroutines.delay(remaining)
        reveal.animateTo(1f, tween(RevealMillis, easing = RevealEasing))
    }
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = reveal.value
                    scaleX = 0.94f + 0.06f * t
                    scaleY = scaleX
                    alpha = (t * 1.6f).coerceIn(0f, 1f)
                },
            content = content,
        )
        val splashUp by remember { derivedStateOf { reveal.value < 1f } }
        if (splashUp) {
            SplashScreen(reveal = { reveal.value })
        }
    }
}

@Composable
private fun SplashScreen(reveal: () -> Float) {
    val colors = LocalAppColors.current
    val shine = rememberInfiniteTransition(label = "splash-shine")
    // The sweep runs left to right, then pauses before the next pass.
    val sweep by shine.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = SweepCycleMillis
                0f at 0 using SweepEasing
                1f at SweepMillis
                1f at SweepCycleMillis
            },
        ),
        label = "splash-sweep",
    )
    val breath by shine.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SweepCycleMillis, easing = LinearEasing), RepeatMode.Reverse),
        label = "splash-breath",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - reveal() }
            .background(colors.background)
            // Swallows touches while it is up.
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_notification),
            contentDescription = null,
            colorFilter = ColorFilter.tint(LogoBase),
            modifier = Modifier
                .size(LogoSize)
                .graphicsLayer {
                    val t = reveal()
                    val scale = 1f + 0.02f * breath + 0.18f * t
                    scaleX = scale
                    scaleY = scale
                    val blur = t * 18.dp.toPx()
                    renderEffect = if (blur > 0.1f) BlurEffect(blur, blur, TileMode.Decal) else null
                    // Offscreen, so the sweep below only lights the wordmark's own pixels.
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    val band = size.width * 0.45f
                    val x = -band + (size.width + band * 2f) * sweep
                    drawRect(
                        brush = Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White,
                            1f to Color.Transparent,
                            start = Offset(x - band, size.height * 0.15f),
                            end = Offset(x + band, size.height * 0.85f),
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                },
        )
    }
}

private val LogoSize = 168.dp
private val LogoBase = Color(0xFF4A4A4A)
private const val SweepMillis = 1100
private const val SweepCycleMillis = 1500
private const val MinHoldMillis = 1250L
private const val MaxHoldMillis = 5000L
private const val RevealMillis = 620
private const val SplashReturnMillis = 220
private val SweepEasing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)
private val RevealEasing = CubicBezierEasing(0.3f, 0f, 0f, 1f)

@Preview(showBackground = true, widthDp = 390, heightDp = 874)
@Composable
private fun SplashScreenPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme { SplashScreen(reveal = { 0f }) }
}
