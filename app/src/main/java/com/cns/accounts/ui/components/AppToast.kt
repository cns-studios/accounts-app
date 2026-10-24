package com.cns.accounts.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalDimensions
import com.cns.accounts.ui.theme.ToastText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** One toast: [id] tells two identical messages apart, so the second still animates in. */
@Stable
class ToastData internal constructor(val message: String, val isError: Boolean, internal val id: Long) {
    internal val dismissed = CompletableDeferred<Unit>()
}

/** Queues toasts and shows them one at a time; [show] suspends until its toast has left. */
@Stable
class ToastState {
    var current by mutableStateOf<ToastData?>(null)
        private set
    private val mutex = Mutex()
    private var nextId = 0L

    suspend fun show(message: String, isError: Boolean = false) = mutex.withLock {
        val toast = ToastData(message, isError, nextId++)
        current = toast
        // Long enough to read: a base plus a little per character, errors a bit longer.
        val visibleMillis = (BaseVisibleMillis + message.length * PerCharMillis + if (isError) 900 else 0).coerceAtMost(MaxVisibleMillis)
        withTimeoutOrNull(visibleMillis) { toast.dismissed.await() }
        current = null
        delay(ToastExitMillis.toLong())
    }

    fun dismiss() {
        current?.dismissed?.complete(Unit)
    }

    private companion object {
        const val BaseVisibleMillis = 2200L
        const val PerCharMillis = 35L
        const val MaxVisibleMillis = 6500L
    }
}

private const val ToastExitMillis = 220

/**
 * The app's toast: a floating dark pill (an error gets a red edge and a short buzz) that springs up from the bottom and can be flicked away
 * downwards. Replaces Material's snackbar, which looked out of place next to the app's controls.
 */
@Composable
fun AppToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.current) {
        if (state.current?.isError == true) haptic.performHapticFeedback(HapticFeedbackType.Reject)
    }
    AnimatedContent(
        targetState = state.current,
        modifier = modifier,
        contentKey = { it?.id },
        contentAlignment = Alignment.BottomCenter,
        transitionSpec = { toastTransition() },
        label = "toast",
    ) { toast ->
        if (toast != null) ToastBubble(toast, onDismiss = state::dismiss)
    }
}

private fun toastTransition(): ContentTransform =
    (
        slideInVertically(spring(dampingRatio = 0.78f, stiffness = 520f)) { it } +
            scaleIn(spring(dampingRatio = 0.78f, stiffness = 520f), initialScale = 0.86f) +
            fadeIn(tween(160))
        ) togetherWith (
        slideOutVertically(tween(ToastExitMillis)) { it / 2 } +
            scaleOut(tween(ToastExitMillis), targetScale = 0.92f) +
            fadeOut(tween(ToastExitMillis - 40))
        )

@Composable
private fun ToastBubble(toast: ToastData, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val dismissDistance = with(LocalDensity.current) { 28.dp.toPx() }
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = LocalDimensions.current.navWidth)
            .graphicsLayer {
                translationY = drag.value
                alpha = 1f - (drag.value / 120.dp.toPx()).coerceIn(0f, 0.6f)
            }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
                },
                onDragStopped = { velocity ->
                    if (drag.value > dismissDistance || velocity > 600f) onDismiss()
                    else drag.animateTo(0f, spring(dampingRatio = 0.7f))
                },
            )
            .clip(RoundedCornerShape(ToastRadius))
            .background(colors.surface)
            .border(1.dp, if (toast.isError) colors.accent.copy(alpha = 0.55f) else colors.elevatedSurface, RoundedCornerShape(ToastRadius))
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(toast.message, color = colors.onSurface, style = ToastText, maxLines = 4)
    }
}

private val ToastRadius = 22.dp

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 160)
@Composable
private fun AppToastPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ToastBubble(ToastData("Can't reach CNS. Check your connection and try again.", isError = true, id = 0), onDismiss = {})
        }
    }
}
