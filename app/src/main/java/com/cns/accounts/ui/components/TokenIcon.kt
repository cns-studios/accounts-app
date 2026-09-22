package com.cns.accounts.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.cns.accounts.R
import com.cns.accounts.ui.theme.LocalAppColors

enum class TokenIconKind { User, Search, Settings, ChevronRight, ChevronLeft, Edit, Help, Image, Trash, Shield, Check, Dots, LogOut }

@Composable
fun TokenIcon(
    kind: TokenIconKind,
    modifier: Modifier = Modifier,
    tint: Color = LocalAppColors.current.onSurface,
) {
    if (kind != TokenIconKind.Dots && kind != TokenIconKind.LogOut) {
        Image(
            painter = painterResource(
                when (kind) {
                    TokenIconKind.User -> R.drawable.token_user
                    TokenIconKind.Search -> R.drawable.token_search
                    TokenIconKind.Settings -> R.drawable.token_settings
                    TokenIconKind.ChevronRight -> R.drawable.token_chevron
                    TokenIconKind.ChevronLeft -> R.drawable.token_chevron_left
                    TokenIconKind.Edit -> R.drawable.token_pen
                    TokenIconKind.Help -> R.drawable.token_help
                    TokenIconKind.Image -> R.drawable.token_image
                    TokenIconKind.Trash -> R.drawable.token_trash
                    TokenIconKind.Shield -> R.drawable.token_shield
                    TokenIconKind.Check -> R.drawable.token_check
                    TokenIconKind.Dots, TokenIconKind.LogOut -> R.drawable.token_user
                },
            ),
            contentDescription = kind.name,
            colorFilter = ColorFilter.tint(tint),
            modifier = modifier.size(24.dp),
        )
        return
    }

    Canvas(modifier = modifier.size(24.dp)) {
        val stroke = 1.8.dp.toPx()
        val style = androidx.compose.ui.graphics.Paint().apply {
            color = tint
            this.strokeWidth = stroke
            isAntiAlias = true
            strokeCap = StrokeCap.Round
            strokeJoin = StrokeJoin.Round
            style = androidx.compose.ui.graphics.PaintingStyle.Stroke
        }

        when (kind) {
            TokenIconKind.User -> {
                drawCircle(tint, radius = 4f * density, center = Offset(size.width / 2, 7f * density))
                drawArc(
                    color = tint,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(5f * density, 11f * density),
                    size = androidx.compose.ui.geometry.Size(14f * density, 10f * density),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            TokenIconKind.Search -> {
                drawCircle(
                    color = tint,
                    radius = 7f * density,
                    center = Offset(10f * density, 10f * density),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
                drawLine(tint, Offset(15f * density, 15f * density), Offset(20f * density, 20f * density), stroke, StrokeCap.Round)
            }
            TokenIconKind.Settings -> {
                drawCircle(tint, radius = 7f * density, center = Offset(size.width / 2, size.height / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                drawCircle(tint, radius = 2.5f * density, center = Offset(size.width / 2, size.height / 2))
            }
            TokenIconKind.ChevronRight -> {
                drawLine(tint, Offset(9f * density, 5f * density), Offset(16f * density, 12f * density), stroke, StrokeCap.Round)
                drawLine(tint, Offset(16f * density, 12f * density), Offset(9f * density, 19f * density), stroke, StrokeCap.Round)
            }
            TokenIconKind.Dots -> {
                val points = listOf(
                    Offset(5f, 12f), Offset(12f, 5f), Offset(19f, 12f),
                    Offset(12f, 19f), Offset(12f, 12f),
                )
                points.forEach { drawCircle(tint, radius = 2f * density, center = it * density) }
            }
            TokenIconKind.LogOut -> {
                fun p(x: Float, y: Float) = Offset(x * density, y * density)
                // history.json (lucidelogout): lucide's "log-out" glyph — a door left open
                // (rounded-rect missing its right edge) with an arrow pointing through it.
                val door = androidx.compose.ui.graphics.Path().apply {
                    moveTo(p(9f, 21f).x, p(9f, 21f).y)
                    lineTo(p(5f, 21f).x, p(5f, 21f).y)
                    quadraticBezierTo(p(3f, 21f).x, p(3f, 21f).y, p(3f, 19f).x, p(3f, 19f).y)
                    lineTo(p(3f, 5f).x, p(3f, 5f).y)
                    quadraticBezierTo(p(3f, 3f).x, p(3f, 3f).y, p(5f, 3f).x, p(5f, 3f).y)
                    lineTo(p(9f, 3f).x, p(9f, 3f).y)
                }
                val arrow = androidx.compose.ui.graphics.Path().apply {
                    moveTo(p(16f, 7f).x, p(16f, 7f).y)
                    lineTo(p(21f, 12f).x, p(21f, 12f).y)
                    lineTo(p(16f, 17f).x, p(16f, 17f).y)
                }
                val strokeStyle = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(door, tint, style = strokeStyle)
                drawPath(arrow, tint, style = strokeStyle)
                drawLine(tint, p(21f, 12f), p(9f, 12f), stroke, StrokeCap.Round)
            }
            TokenIconKind.ChevronLeft, TokenIconKind.Edit, TokenIconKind.Help,
            TokenIconKind.Image, TokenIconKind.Trash, TokenIconKind.Shield, TokenIconKind.Check,
            -> Unit // rendered via the vector-drawable branch above
        }
    }

}
