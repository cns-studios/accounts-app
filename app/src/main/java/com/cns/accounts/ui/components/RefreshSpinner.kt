package com.cns.accounts.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cns.accounts.ui.theme.LocalAppColors
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * The app's loading glyph, in three shapes it morphs through: four arcs with gaps ([phase] 0),
 * a ring of twelve dots (1), and a single spinner arc (2); 3 is the four arcs again, so a looping
 * caller can count 0 → 3 and wrap. Fractional phases morph between the neighbours: every shape
 * is drawn from the same twelve round-capped segments, whose angles, lengths and stroke widths
 * are interpolated, and the glyph blurs slightly at the midpoint of each morph so the hand-off
 * reads as one shape melting into the next rather than a crossfade.
 *
 * It always rotates, on an eased (not linear) curve: each turn speeds up and settles again.
 */
@Composable
fun RefreshSpinner(
    phase: () -> Float,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    color: Color = LocalAppColors.current.onSurface,
    rotationOffset: () -> Float = { 0f },
) {
    val spin = rememberInfiniteTransition(label = "refresh-spinner")
    val turn = spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(SpinTurnMillis, easing = SpinEasing)),
        label = "refresh-spinner-turn",
    )
    // The spinner shape's arc also "breathes" a little, so phase 2 looks alive between morphs.
    val breath = spin.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BreathMillis, easing = LinearEasing)),
        label = "refresh-spinner-breath",
    )
    Box(
        modifier = modifier
            .size(size)
            // Read in the layer block, so a morph redraws instead of recomposing every frame.
            // (BlurEffect needs Android 12; older releases just skip the blur.)
            .graphicsLayer {
                val radius = morphBlur(phase()) * MaxMorphBlur.toPx()
                renderEffect = if (radius > 0.1f) BlurEffect(radius, radius, TileMode.Decal) else null
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val p = phase().coerceIn(0f, 3f)
            val from = floor(p).toInt().coerceAtMost(2)
            val t = MorphEasing.transform(p - from)
            val breathe = sin(breath.value * 2f * Math.PI.toFloat()) * 0.5f + 0.5f
            val radius = this.size.minDimension / 2f - MaxStroke.toPx() / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            rotate(turn.value + rotationOffset(), center) {
                for (i in 0 until SegmentCount) {
                    val a = segment(from, i, breathe)
                    val b = segment(from + 1, i, breathe)
                    val mid = lerpAngle(a.center, b.center, t)
                    val sweep = a.sweep + (b.sweep - a.sweep) * t
                    val stroke = (a.stroke + (b.stroke - a.stroke) * t).dp.toPx()
                    drawArc(
                        color = color,
                        startAngle = mid - sweep / 2f - 90f,
                        // A zero sweep with a round cap is a dot.
                        sweepAngle = sweep.coerceAtLeast(0.01f),
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

/** Runs through all three shapes on its own, forever: for waits with no pull gesture. */
@Composable
fun LoopingSpinner(modifier: Modifier = Modifier, size: Dp = 32.dp, color: Color = LocalAppColors.current.onSurface) {
    val loop = rememberInfiniteTransition(label = "looping-spinner")
    val phase = loop.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            androidx.compose.animation.core.keyframes {
                durationMillis = LoopMillis
                // Hold each shape, then morph to the next.
                0f at 0
                0f at 400
                1f at 820
                1f at 1220
                2f at 1640
                2f at 2040
                3f at LoopMillis
            },
        ),
        label = "looping-spinner-phase",
    )
    RefreshSpinner(phase = { phase.value }, modifier = modifier, size = size, color = color)
}

private const val LoopMillis = 2460

/** The plain spinner shape, for busy buttons. */
const val BusySpinnerPhase = 2f

private class SegmentShape(val center: Float, val sweep: Float, val stroke: Float)

private const val SegmentCount = 12

/** Segment [i] of shape [shape] (0 arcs, 1 dots, 2 spinner, 3 arcs); angles in degrees, stroke in dp. */
private fun segment(shape: Int, i: Int, breathe: Float): SegmentShape = when (shape % 3) {
    // Four 60° arcs with 30° gaps: three contiguous 20° segments per arc.
    0 -> SegmentShape(center = (i / 3) * 90f + 10f + (i % 3) * 20f, sweep = 20f, stroke = 3f)
    // Twelve evenly spaced dots, a little heavier than the arcs so they read as dots.
    1 -> SegmentShape(center = i * 30f + 15f, sweep = 0f, stroke = 4f)
    // One spinner arc of about 270°, breathing between 250° and 290°.
    else -> {
        val total = 250f + 40f * breathe
        val each = total / SegmentCount
        SegmentShape(center = each * i + each / 2f, sweep = each, stroke = 3f)
    }
}

private fun lerpAngle(from: Float, to: Float, t: Float): Float {
    // Shortest way round, so no segment spins backwards across the circle during a morph.
    var delta = (to - from) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return from + delta * t
}

/** 0 at each resting shape, 1 at the middle of a morph. */
private fun morphBlur(phase: Float): Float {
    val f = phase - floor(phase)
    return (1f - abs(f - 0.5f) * 2f).coerceIn(0f, 1f).let { it * it }
}

private val MaxStroke = 4.dp
private val MaxMorphBlur = 2.5.dp
private const val SpinTurnMillis = 1150
private const val BreathMillis = 1800
private val SpinEasing = CubicBezierEasing(0.55f, 0.05f, 0.35f, 1f)
private val MorphEasing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun RefreshSpinnerPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        androidx.compose.foundation.layout.Row {
            RefreshSpinner(phase = { 0f })
            RefreshSpinner(phase = { 0.5f })
            RefreshSpinner(phase = { 1f })
            RefreshSpinner(phase = { 2f })
        }
    }
}
