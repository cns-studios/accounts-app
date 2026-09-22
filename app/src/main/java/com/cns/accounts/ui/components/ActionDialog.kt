package com.cns.accounts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cns.accounts.ui.theme.DialogButtonText
import com.cns.accounts.ui.theme.DialogSubtitleText
import com.cns.accounts.ui.theme.DialogTitleText
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalRadii
import com.cns.accounts.ui.theme.RequestMetaText

/**
 * settings.tokens.json ("Inputfield"): the app's standard input/action dialog — a dark rounded
 * card (title, subtitle, an optional single-line field, and one primary control) over a darkened
 * scrim. Use this whenever a flow needs a short piece of text input or a single confirmed action;
 * pass [fieldValue]/[onFieldValueChange]/[fieldPlaceholder] together to show the input row, or
 * leave them null for a plain confirm dialog.
 *
 * The scrim darkens the screen behind the card and, tapping it (or the system back gesture)
 * dismisses the dialog via [onDismissRequest] — the card itself consumes its own taps so they
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
) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    onDismissRequest()
                }
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LocalRadii.current.card))
                    .background(colors.elevatedSurface)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        // Consume taps on the card itself so they don't dismiss via the scrim below.
                    }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(title, color = colors.onSurface, style = DialogTitleText, textAlign = TextAlign.Center)
                    Text(subtitle, color = colors.dialogSubtitle, style = DialogSubtitleText, textAlign = TextAlign.Center)
                }
                if (fieldValue != null && fieldPlaceholder != null) {
                    DialogTextField(
                        value = fieldValue,
                        onValueChange = onFieldValueChange,
                        placeholder = fieldPlaceholder,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(53.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.fieldSurface)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPrimaryAction()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(primaryButtonLabel, color = colors.onSurface, style = DialogButtonText)
                }
            }
        }
    }
}

@Composable
private fun DialogTextField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val colors = LocalAppColors.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = RequestMetaText.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.onSurface),
        modifier = Modifier
            .fillMaxWidth()
            .height(53.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(colors.fieldSurface),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = colors.fieldPlaceholder, style = RequestMetaText)
                }
                innerTextField()
            }
        },
    )
}

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
