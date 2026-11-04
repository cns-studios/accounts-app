package com.cns.accounts.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import com.cns.accounts.ui.theme.ToastText

/**
 * False while the app lock covers the app. A dialog is its own window, above the lock screen,
 * so dialogs stay hidden until the app is unlocked again (their state is kept meanwhile).
 */
val LocalDialogsShown = compositionLocalOf { true }

/**
 * The app's standard input/action dialog: a rounded #2D2D2D card over a darkened scrim, laid out
 * top to bottom as the title and subtitle, any info tiles (note, warning, detail), input fields,
 * the inline error and the buttons - all start-aligned on one 24dp inset, so every dialog in the
 * app shares the same rhythm.
 *
 * Inputs: pass [fieldValue]/[onFieldValueChange]/[fieldPlaceholder] together for one plain text
 * row, or [fields]/[onFieldChange] for several (e.g. password + 2FA code). [detail] shows a
 * monospace value such as a setup key in its own tile; [errorText] unfolds as an [ErrorBanner]
 * above the buttons (and shakes the card); [isBusy] disables the controls while a request is in
 * flight; [secondaryButtonLabel] adds a text button, or with [secondaryAsButton] a second button
 * beside the primary one. [destructive] paints the primary button in the accent red.
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
    // The subtitle as a warning callout (accent red), e.g. before logging in another device.
    subtitleIsWarning: Boolean = false,
    // A small info tile under the subtitle, e.g. which device asks.
    note: String? = null,
    // The secondary action as a button beside the primary one, not a text link.
    secondaryAsButton: Boolean = false,
    // A spinner above the buttons, for a step that waits on something else.
    showProgress: Boolean = false,
    // The primary action deletes or removes something: a red button.
    destructive: Boolean = false,
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
    val secondary = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onSecondaryAction()
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
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
                        scaleX = 0.94f + 0.06f * t
                        scaleY = scaleX
                        alpha = t.coerceIn(0f, 1f)
                    }
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DialogRadius))
                    .background(DialogColor)
                    .border(1.dp, DialogBorderColor, RoundedCornerShape(DialogRadius))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        // Consume taps on the card itself so they don't dismiss via the scrim below.
                    }
                    // Too little room (keyboard open on a small screen): scroll rather than squeeze.
                    .verticalScroll(rememberScrollState())
                    .padding(DialogPadding),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, color = colors.onSurface, style = DialogTitleText)
                    if (!subtitleIsWarning) {
                        Text(subtitle, color = colors.dialogSubtitle, style = DialogSubtitleText)
                    }
                }
                if (note != null || subtitleIsWarning || detail != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (note != null) DialogNote(note)
                        if (subtitleIsWarning) DialogWarning(subtitle)
                        if (detail != null) DialogDetail(detail)
                    }
                }
                if (showProgress) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        RefreshSpinner(phase = { BusySpinnerPhase }, size = 26.dp)
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
                                shape = RoundedCornerShape(TileRadius),
                                containerColor = colors.elevatedSurface,
                            )
                        }
                    }
                }
                // One child of the card's column, so the folded-away banner adds no gap.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ErrorBanner(errorText, contentPadding = PaddingValues(bottom = 14.dp))
                    if (secondaryButtonLabel != null && secondaryAsButton) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DialogButton(
                                label = secondaryButtonLabel,
                                container = colors.elevatedSurface,
                                content = colors.onSurface,
                                enabled = !isBusy,
                                onClick = secondary,
                            )
                            PrimaryButton(primaryButtonLabel, destructive, isBusy, submit)
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            PrimaryButton(primaryButtonLabel, destructive, isBusy, submit)
                        }
                        if (secondaryButtonLabel != null) {
                            Text(
                                secondaryButtonLabel,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable(enabled = !isBusy, onClick = secondary)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                color = colors.dialogSubtitle,
                                style = DialogButtonText,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.PrimaryButton(label: String, destructive: Boolean, isBusy: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    DialogButton(
        label = label,
        container = if (destructive) colors.accent else Color.White,
        content = if (destructive) Color.White else colors.onLightSurface,
        enabled = !isBusy,
        busy = isBusy,
        onClick = onClick,
    )
}

@Composable
private fun RowScope.DialogButton(
    label: String,
    container: Color,
    content: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    busy: Boolean = false,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(DialogButtonHeight)
            .clip(RoundedCornerShape(50))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            RefreshSpinner(phase = { BusySpinnerPhase }, size = 20.dp, color = content)
        } else {
            Text(label, color = content, style = DialogButtonText, maxLines = 1)
        }
    }
}

/** A quiet info line in its own tile, e.g. "Requested by Firefox on Linux". */
@Composable
private fun DialogNote(text: String) {
    val colors = LocalAppColors.current
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TileRadius))
            .background(TileColor)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        color = colors.onSurface,
        style = ToastText,
    )
}

/** A red-tinted callout for something the user must read before confirming. */
@Composable
private fun DialogWarning(text: String) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(TileRadius)
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.accent.copy(alpha = 0.14f))
            .border(1.dp, colors.accent.copy(alpha = 0.45f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        color = colors.onSurface,
        style = ToastText,
    )
}

/** A monospace value (a setup or verification code) in its own centered tile. */
@Composable
private fun DialogDetail(text: String) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TileRadius))
            .background(TileColor)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        SelectionContainer {
            Text(text, color = colors.onSurface, style = DialogDetailText, textAlign = TextAlign.Center)
        }
    }
}

private val DialogRadius = 24.dp
private val DialogPadding = 24.dp
private val TileRadius = 14.dp
private val DialogButtonHeight = 50.dp
// Darker than the app's #2D2D2D cards but not black, with a hairline edge to lift it off the scrim.
private val DialogColor = Color(0xFF222222)
private val DialogBorderColor = Color.White.copy(alpha = 0.08f)
// A tile a shade lighter than the card, so it reads as part of it.
private val TileColor = Color.White.copy(alpha = 0.05f)
private val DialogDetailText = AccountCodeText.copy(fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = 2.sp)

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
                destructive = true,
            )
        }
    }
}

@Preview(name = "QR login confirm", showBackground = true, widthDp = 390, heightDp = 640)
@Composable
private fun ActionDialogQrPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        Box(modifier = Modifier.fillMaxSize().background(colorsBg())) {
            ActionDialog(
                title = "New device",
                subtitle = "Clicking 'Log In' will log your account into another device. Never accept remote logins you didn't initiate.",
                subtitleIsWarning = true,
                note = "Requested by Firefox on Linux",
                primaryButtonLabel = "Log In",
                secondaryButtonLabel = "Cancel",
                secondaryAsButton = true,
                onDismissRequest = {},
                onPrimaryAction = {},
            )
        }
    }
}
