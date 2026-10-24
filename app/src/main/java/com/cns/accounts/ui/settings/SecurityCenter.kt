package com.cns.accounts.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.R
import com.cns.accounts.data.SecurityFactor
import com.cns.accounts.data.SecurityStatus
import com.cns.accounts.data.securityStatus
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.RequestMetaText
import com.cns.accounts.ui.theme.SecurityFactorLabelText
import com.cns.accounts.ui.theme.SecurityPercentText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * settings.tokens.json (Frame9 "Security center"): the completion percentage over one bar per
 * factor. Completed bars always sit on the left and missing ones on the right; when a factor
 * changes, its bar slides to its new place. A factor listed in [newlyCompleted] gets a one-time
 * celebration — its bar fills from left to right with a burst of dot particles — after which
 * [onCelebrated] records it as seen.
 */
@Composable
internal fun SecurityCenterCard(
    security: SecurityStatus,
    newlyCompleted: Set<String>,
    onCelebrated: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val percent by animateIntAsState(security.percentComplete, tween(700, easing = FastOutSlowInEasing), label = "security-percent")
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Not clipped: a celebration's particles may fly a little past the bars.
            .border(2.dp, colors.elevatedSurface, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.security_center), color = colors.onSurface, style = RequestMetaText)
        Text("$percent%", color = colors.onSurface, style = SecurityPercentText)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val count = security.factors.size.coerceAtLeast(1)
            val barWidth = (maxWidth - BarGap * (count - 1)) / count
            // Stable within each group, so bars keep their usual order among themselves.
            val sorted = security.factors.sortedBy { !it.achieved }
            security.factors.forEach { factor ->
                key(factor.id) {
                    val slot = sorted.indexOf(factor)
                    val x by animateDpAsState(
                        targetValue = (barWidth + BarGap) * slot,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 300f),
                        label = "security-bar-${factor.id}",
                    )
                    SecurityFactorBar(
                        factor = factor,
                        celebrate = factor.id in newlyCompleted,
                        onCelebrated = { onCelebrated(factor.id) },
                        modifier = Modifier
                            .offset(x = x)
                            .width(barWidth),
                    )
                }
            }
        }
    }
}

private val BarGap = 10.dp
private val BarHeight = 7.dp

// settings.tokens.json (Rectangle36-39): each factor is a 76x7px pill — solid white when
// achieved, or an empty pill outlined with the accent red when still missing.
@Composable
private fun SecurityFactorBar(factor: SecurityFactor, celebrate: Boolean, onCelebrated: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val latestOnCelebrated by rememberUpdatedState(onCelebrated)
    // How much of the bar is filled white (0..1, left to right).
    val fill = remember { Animatable(if (factor.achieved && !celebrate) 1f else 0f) }
    // 0..1 over a celebration's particle burst; 0 when none runs.
    val burst = remember { Animatable(0f) }
    val particles = remember { burstParticles() }
    LaunchedEffect(factor.achieved, celebrate) {
        if (factor.achieved && celebrate) {
            fill.snapTo(0f)
            // Lets the page (and a bar sliding into place) settle first.
            delay(CelebrationDelayMillis)
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            launch { burst.snapTo(0f); burst.animateTo(1f, tween(BurstMillis, easing = LinearEasing)) }
            fill.animateTo(1f, tween(FillMillis, easing = FillEasing))
            delay((BurstMillis - FillMillis).toLong().coerceAtLeast(0))
            burst.snapTo(0f)
            latestOnCelebrated()
        } else {
            fill.animateTo(if (factor.achieved) 1f else 0f, tween(360, easing = FastOutSlowInEasing))
        }
    }
    val accent = colors.accent
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2)
                    val filled = fill.value
                    if (filled < 1f) {
                        drawRoundRect(
                            color = accent.copy(alpha = 1f - filled),
                            cornerRadius = radius,
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx())),
                            ),
                        )
                    }
                    val b = burst.value
                    if (b > 0f) {
                        // A soft glow that swells out of the bar and fades.
                        val grow = 6.dp.toPx() * b
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.28f * (1f - b)),
                            topLeft = Offset(-grow, -grow),
                            size = Size(size.width + grow * 2, size.height + grow * 2),
                            cornerRadius = CornerRadius(size.height / 2 + grow),
                        )
                        particles.forEach { p -> drawParticle(p, b) }
                    }
                    if (filled > 0f) {
                        drawRoundRect(
                            color = Color.White,
                            size = Size((size.width * filled).coerceAtLeast(size.height), size.height),
                            cornerRadius = radius,
                            alpha = filled.coerceIn(0f, 1f).let { if (it < 0.15f) it / 0.15f else 1f },
                        )
                    }
                },
        )
        Text(factor.label, color = colors.onSurface, style = SecurityFactorLabelText)
    }
}

private class Particle(val originX: Float, val angle: Float, val distanceDp: Float, val sizeDp: Float, val start: Float)

/** Small square "pixels" thrown out of the bar, mostly upwards and sideways. */
private fun burstParticles(): List<Particle> {
    val random = Random(System.nanoTime())
    return List(ParticleCount) {
        Particle(
            originX = random.nextFloat(),
            angle = (-PI / 2 + (random.nextFloat() - 0.5f) * PI * 1.5).toFloat(),
            distanceDp = 12f + random.nextFloat() * 20f,
            sizeDp = 1.8f + random.nextFloat() * 1.8f,
            start = random.nextFloat() * 0.25f,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawParticle(p: Particle, burst: Float) {
    val local = ((burst - p.start) / (1f - p.start)).coerceIn(0f, 1f)
    if (local <= 0f || local >= 1f) return
    val travel = ParticleEasing.transform(local) * p.distanceDp.dp.toPx()
    val origin = Offset(size.width * p.originX, size.height / 2)
    val center = origin + Offset(cos(p.angle) * travel, sin(p.angle) * travel)
    val side = p.sizeDp.dp.toPx() * (1f - local * 0.4f)
    drawRect(
        color = Color.White,
        topLeft = center - Offset(side / 2, side / 2),
        size = Size(side, side),
        alpha = (1f - local).let { it * it },
    )
}

private const val ParticleCount = 18
private const val CelebrationDelayMillis = 520L
private const val FillMillis = 560
private const val BurstMillis = 950
private val FillEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val ParticleEasing = CubicBezierEasing(0.1f, 0.6f, 0.2f, 1f)

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 366)
@Composable
private fun SecurityCenterCardPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        SecurityCenterCard(
            securityStatus(twoFactorEnabled = true, authenticatorOnDevice = false),
            newlyCompleted = emptySet(),
            onCelebrated = {},
        )
    }
}
