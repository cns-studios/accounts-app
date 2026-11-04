package com.cns.accounts.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Frosted-glass dialogs. [WindowBlurBehind] (called from inside a Dialog) blurs the app behind the
 * dialog window, easing the radius in. [windowBlurAvailable] says whether that works at all, so
 * the dialog can make its own surface translucent (see [glassSurface]) and let the blur show
 * through; when it isn't available (Android 11 and below, or the device or battery saver has
 * cross-window blur off) the surface stays opaque and nothing looks off. Only for dialogs: a
 * window blur covers the whole screen behind the window, which is wrong for small popups.
 */
@Composable
fun windowBlurAvailable(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val windowManager = LocalContext.current.getSystemService(WindowManager::class.java) ?: return false
    return windowManager.isCrossWindowBlurEnabled
}

@Composable
fun rememberWindowBlur(radius: Dp = 20.dp): Boolean {
    val available = windowBlurAvailable()
    if (available) WindowBlurBehind(radius)
    return available
}

@Composable
fun WindowBlurBehind(radius: Dp = 20.dp) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val radiusPx = with(LocalDensity.current) { radius.toPx() }
    val amount = remember { Animatable(0f) }
    LaunchedEffect(radiusPx) {
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        amount.animateTo(1f, tween(240)) {
            window.attributes = window.attributes.apply { blurBehindRadius = (value * radiusPx).toInt() }
        }
    }
}

/** [color] as a frosted surface: translucent while the window behind it is blurred, else opaque. */
fun glassSurface(color: Color, blurred: Boolean, alpha: Float = 0.74f): Color =
    if (blurred) color.copy(alpha = alpha) else color
