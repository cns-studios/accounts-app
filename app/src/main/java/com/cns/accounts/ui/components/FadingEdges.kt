package com.cns.accounts.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Masks a scrolling list so it is fully hidden in the top [topHidden] and bottom [bottomHidden]
 * bands and fades in over [fade] next to each band. Used where a feed scrolls under something
 * it must not show through: the bottom nav / search bar, the status bar, a page title. Unlike a
 * gradient scrim on top, nothing of the list stays visible behind or below the fade.
 */
fun Modifier.fadingEdges(topHidden: Dp = 0.dp, bottomHidden: Dp = 0.dp, fade: Dp = 40.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val height = size.height
        if (height <= 0f) return@drawWithContent
        val fadePx = fade.toPx()
        val topStart = (topHidden.toPx() / height).coerceIn(0f, 1f)
        val topEnd = ((topHidden.toPx() + fadePx) / height).coerceIn(topStart, 1f)
        val bottomEnd = (1f - bottomHidden.toPx() / height).coerceIn(topEnd, 1f)
        val bottomStart = (1f - (bottomHidden.toPx() + fadePx) / height).coerceIn(topEnd, bottomEnd)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                topStart to Color.Transparent,
                topEnd to Color.Black,
                bottomStart to Color.Black,
                bottomEnd to Color.Transparent,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
