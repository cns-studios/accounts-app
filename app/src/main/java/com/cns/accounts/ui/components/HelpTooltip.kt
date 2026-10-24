package com.cns.accounts.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.TooltipText
import kotlinx.coroutines.delay

/**
 * A "?" glyph that explains its row: tapping it pops a speech bubble just above the glyph, its
 * pointer aimed at the glyph, growing out of that point (and shrinking back into it). The bubble
 * floats over the page without covering the row it explains, stays clear of the screen edges,
 * and goes away on its own, on a tap on it, or on the glyph again.
 */
@Composable
fun HelpTooltipIcon(contentDescription: String, hint: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(visible.targetState) {
        if (visible.targetState) {
            delay(TooltipVisibleMillis + hint.length * 25L)
            visible.targetState = false
        }
    }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = contentDescription) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    visible.targetState = !visible.targetState
                }
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            TokenIcon(TokenIconKind.Help, tint = colors.mutedText)
        }
        // Kept mounted while the exit animation runs (target and current state differ mid-way).
        if (visible.targetState || visible.currentState) {
            TooltipPopup(hint = hint, visible = visible, onDismiss = { visible.targetState = false })
        }
    }
}

@Composable
private fun TooltipPopup(hint: String, visible: MutableTransitionState<Boolean>, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    // Where along the bubble's width the pointer sits; set when the popup is positioned.
    var pointerX by remember { mutableFloatStateOf(Float.NaN) }
    var pointsDown by remember { mutableStateOf(true) }
    val provider = remember(density) {
        BubblePositionProvider(
            gapPx = with(density) { 6.dp.roundToPx() },
            marginPx = with(density) { 16.dp.roundToPx() },
            onPlaced = { x, below ->
                pointerX = x
                pointsDown = !below
            },
        )
    }
    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = false, dismissOnClickOutside = false)) {
        val colors = LocalAppColors.current
        val shape = remember(pointerX, pointsDown, density) { BubbleShape(pointerX, pointsDown, density) }
        // Grows out of (and shrinks back into) the pointer's tip.
        var bubbleWidth by remember { mutableFloatStateOf(0f) }
        val origin = TransformOrigin(
            pivotFractionX = if (bubbleWidth > 0f && !pointerX.isNaN()) (pointerX / bubbleWidth).coerceIn(0f, 1f) else 0.5f,
            pivotFractionY = if (pointsDown) 1f else 0f,
        )
        AnimatedVisibility(
            visibleState = visible,
            enter = scaleIn(spring(dampingRatio = 0.62f, stiffness = 700f), initialScale = 0.4f, transformOrigin = origin) + fadeIn(tween(120)),
            exit = scaleOut(tween(150), targetScale = 0.6f, transformOrigin = origin) + fadeOut(tween(130)),
        ) {
            Box(
                modifier = Modifier
                    .onSizeChanged { bubbleWidth = it.width.toFloat() }
                    .widthIn(max = 260.dp)
                    .clip(shape)
                    .background(colors.onSurface)
                    .clickable(onClick = onDismiss)
                    .padding(
                        start = 14.dp,
                        end = 14.dp,
                        top = if (pointsDown) 10.dp else 10.dp + PointerHeight,
                        bottom = if (pointsDown) 10.dp + PointerHeight else 10.dp,
                    ),
            ) {
                Text(hint, color = colors.onLightSurface, style = TooltipText)
            }
        }
    }
}

private val PointerHeight = 7.dp
private val PointerWidth = 14.dp
private val BubbleRadius = 12.dp
private const val TooltipVisibleMillis = 2600L

/** Centers the bubble over the anchor (above it, or below when there is no room), clamped to the window. */
private class BubblePositionProvider(
    private val gapPx: Int,
    private val marginPx: Int,
    private val onPlaced: (pointerX: Float, below: Boolean) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val anchorCenter = anchorBounds.center.x
        val maxX = (windowSize.width - marginPx - popupContentSize.width).coerceAtLeast(marginPx)
        val x = (anchorCenter - popupContentSize.width / 2).coerceIn(marginPx, maxX)
        val above = anchorBounds.top - popupContentSize.height - gapPx
        val below = above < marginPx
        val y = if (below) anchorBounds.bottom + gapPx else above
        onPlaced((anchorCenter - x).toFloat(), below)
        return IntOffset(x, y)
    }
}

/** A rounded rectangle with a small pointer on its bottom (or top) edge at [pointerX]. */
private class BubbleShape(private val pointerX: Float, private val pointsDown: Boolean, private val density: Density) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val pointerH = with(this.density) { PointerHeight.toPx() }
        val pointerW = with(this.density) { PointerWidth.toPx() }
        val radius = with(this.density) { BubbleRadius.toPx() }
        val top = if (pointsDown) 0f else pointerH
        val bottom = if (pointsDown) size.height - pointerH else size.height
        val tipX = (if (pointerX.isNaN()) size.width / 2f else pointerX).coerceIn(radius + pointerW / 2f, size.width - radius - pointerW / 2f)
        val path = Path().apply {
            addRoundRect(RoundRect(0f, top, size.width, bottom, CornerRadius(radius)))
            if (pointsDown) {
                moveTo(tipX - pointerW / 2f, bottom - 0.5f)
                quadraticTo(tipX - pointerW / 4f, bottom, tipX - 1.5f, size.height - 1f)
                quadraticTo(tipX, size.height, tipX + 1.5f, size.height - 1f)
                quadraticTo(tipX + pointerW / 4f, bottom, tipX + pointerW / 2f, bottom - 0.5f)
            } else {
                moveTo(tipX - pointerW / 2f, top + 0.5f)
                quadraticTo(tipX - pointerW / 4f, top, tipX - 1.5f, 1f)
                quadraticTo(tipX, 0f, tipX + 1.5f, 1f)
                quadraticTo(tipX + pointerW / 4f, top, tipX + pointerW / 2f, top + 0.5f)
            }
            close()
        }
        return Outline.Generic(path)
    }
}
