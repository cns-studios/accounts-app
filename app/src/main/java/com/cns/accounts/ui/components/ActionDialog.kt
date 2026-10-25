package com.cns.accounts.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cns.accounts.ui.theme.AccountCodeText
import com.cns.accounts.ui.theme.DialogButtonText
import com.cns.accounts.ui.theme.DialogSubtitleText
import com.cns.accounts.ui.theme.DialogTitleText
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalRadii

/**
 * False while the app lock covers the app. A dialog is its own window, above the lock screen,
 * so dialogs stay hidden until the app is unlocked again (their state is kept meanwhile).
 */
val LocalDialogsShown = compositionLocalOf { true }

/**
 * settings.tokens.json ("Inputfield"): the app's standard input/action dialog - a dark rounded
 * card (title, subtitle, optional input fields, and one primary control) over a darkened scrim.
 * Use this whenever a flow needs a short piece of text input or a single confirmed action.
 *
 * Inputs: pass [fieldValue]/[onFieldValueChange]/[fieldPlaceholder] together for one plain text
 * row, or [fields]/[onFieldChange] for several (e.g. password + 2FA code). [detail] shows a
 * monospace value such as a setup key; [errorText] unfolds as an [ErrorBanner] below the fields
 * (and shakes the card); [isBusy] disables
 * the controls while a request is in flight; [secondaryButtonLabel] adds a text button.
 *
 * The scrim darkens the screen behind the card and, tapping it (or the system back gesture)
 * dismisses the dialog via [onDismissRequest] - the card itself consumes its own taps so they
 * don't fall through to the scrim.
 */
@Composable
fun ActionDialog(
    title: String,
    subtitle: String,
    primaryButtonLabel: String,
    onDismissRequest: () -> Unit,
    onPrimaryAction: () -> Unit,
    fieldValue: String? = null,
    onFieldValueChange: (String) -> Unit = {},
    fieldPlaceholder: String? = null,
    fields: List<DialogField> = emptyList(),
    onFieldChange: (index: Int, value: String) -> Unit = { _, _ -> },
    detail: String? = null,
    errorText: String? = null,
    isBusy: Boolean = false,
    secondaryButtonLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
    // The subtitle as a warning (accent red), e.g. before logging in another device.
    subtitleIsWarning: Boolean = false,
    // A small grey line under the subtitle, e.g. which device asks.
    note: String? = null,
    // The secondary action as a full-width button under the primary one, not a text link.
    secondaryAsButton: Boolean = false,
    // A spinner above the buttons, for a step that waits on something else.
    showProgress: Boolean = false,
) {
    if (!LocalDialogsShown.current) return
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val allFields = if (fieldValue != null && fieldPlaceholder != null) listOf(DialogField(fieldPlaceholder, fieldValue)) + fields else fields
    val singleFieldOffset = allFields.size - fields.size
    val submit = {
        if (!isBusy) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onPrimaryAction()
        }
    }
    Dialog(
        onDismissRequest = { if (!isBusy) onDismissRequest() },
        // decorFitsSystemWindows = false: the window spans the whole screen and only the padding
        // below makes room for the keyboard. Left fitting, the window shrank for the keyboard and
        // the padding took the keyboard's height off again, squashing the card.
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    if (!isBusy) onDismissRequest()
                }
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            // The card springs in from slightly smaller, and shakes on a failed submit.
            val entrance = remember { Animatable(0f) }
            LaunchedEffect(Unit) { entrance.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
            Column(
                modifier = Modifier
                    .then(rememberErrorShake(errorText))
                    .graphicsLayer {
                        val t = entrance.value
                        scaleX = 0.92f + 0.08f * t
                        scaleY = scaleX
                        alpha = t.coerceIn(0f, 1f)
                    }
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LocalRadii.current.card))
                    .background(colors.elevatedSurface)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        // Consume taps on the card itself so they don't dismiss via the scrim below.
                    }
                    // Too little room (keyboard open on a small screen): scroll rather than squeeze.
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(title, color = colors.onSurface, style = DialogTitleText, textAlign = TextAlign.Center)
                    Text(subtitle, color = if (subtitleIsWarning) colors.accent else colors.dialogSubtitle, style = DialogSubtitleText, textAlign = TextAlign.Center)
                    if (note != null) {
                        Text(note, color = colors.dialogSubtitle, style = DialogNoteText, textAlign = TextAlign.Center)
                    }
                }
                if (showProgress) {
                    RefreshSpinner(phase = { BusySpinnerPhase }, size = 26.dp)
                }
                if (detail != null) {
                    SelectionContainer {
                        Text(detail, color = colors.onSurface, style = DialogDetailText, textAlign = TextAlign.Center)
                    }
                }
                if (allFields.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        allFields.forEachIndexed { index, field ->
                            val isLast = index == allFields.lastIndex
                            AppTextField(
                                value = field.value,
                                onValueChange = { value ->
                                    if (index < singleFieldOffset) onFieldValueChange(value) else onFieldChange(index - singleFieldOffset, value)
                                },
                                placeholder = field.placeholder,
                                kind = field.kind,
                                enabled = !isBusy,
                                imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
                                onImeAction = { if (isLast) submit() },
                            )
                        }
                    }
                }
                // One child of the card's column, so the folded-away banner adds no gap.
                Column {
                    ErrorBanner(errorText, contentPadding = PaddingValues(bottom = 14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(53.dp)
                            .clip(RoundedCornerShape(50))
                            .background(colors.fieldSurface)
                            .clickable(enabled = !isBusy, onClick = submit),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isBusy) {
                            RefreshSpinner(phase = { BusySpinnerPhase }, size = 22.dp)
                        } else {
                            Text(primaryButtonLabel, color = colors.onSurface, style = DialogButtonText)
                        }
                    }
                }
                if (secondaryButtonLabel != null && secondaryAsButton) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(53.dp)
                            .clip(RoundedCornerShape(50))
                            .background(SecondaryButtonColor)
                            .clickable(enabled = !isBusy) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSecondaryAction()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(secondaryButtonLabel, color = Color.White, style = DialogButtonText)
                    }
                } else if (secondaryButtonLabel != null) {
                    Text(
                        secondaryButtonLabel,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(enabled = !isBusy) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSecondaryAction()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = colors.dialogSubtitle,
                        style = DialogSubtitleText,
                    )
                }
            }
        }
    }
}

private val DialogDetailText = AccountCodeText.copy(fontSize = 18.sp, lineHeight = 24.sp)
private val DialogNoteText = DialogSubtitleText.copy(fontSize = 13.sp, lineHeight = 18.sp)

// The dark secondary button (#1A1A1A, white text), the same as the Home scan card's.
internal val SecondaryButtonColor = Color(0xFF1A1A1A)

@Preview(name = "Action dialog with field", showBackground = true, widthDp = 390, heightDp = 500)
@Composable
private fun ActionDialogWithFieldPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        Box(modifier = Modifier.fillMaxSize().background(colorsBg())) {
            ActionDialog(
                title = "Edit username",
                subtitle = "Choose a new username for your account.",
                fieldValue = "aaron",
                onFieldValueChange = {},
                fieldPlaceholder = "New username...",
                primaryButtonLabel = "Update",
                onDismissRequest = {},
                onPrimaryAction = {},
            )
        }
    }
}

@Composable
private fun colorsBg() = LocalAppColors.current.background

@Preview(name = "Action dialog confirm-only", showBackground = true, widthDp = 390, heightDp = 500)
@Composable
private fun ActionDialogConfirmOnlyPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        Box(modifier = Modifier.fillMaxSize().background(colorsBg())) {
            ActionDialog(
                title = "Remove Avatar",
                subtitle = "Your profile photo will be removed.",
                primaryButtonLabel = "Remove",
                onDismissRequest = {},
                onPrimaryAction = {},
            )
        }
    }
}
