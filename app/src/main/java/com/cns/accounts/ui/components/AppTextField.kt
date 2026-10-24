package com.cns.accounts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.RequestMetaText

/**
 * What a field holds: drives keyboard, masking and autofill hints (so password managers work).
 * [Phrase] is a recovery phrase (several lines) and [PhraseWord] one of its words: both get a
 * password keyboard, so the words stay out of the keyboard's suggestions and learned words.
 */
enum class FieldKind { Text, Username, Password, NewPassword, Code, Phrase, PhraseWord }

@Immutable
data class DialogField(
    val placeholder: String,
    val value: String = "",
    val kind: FieldKind = FieldKind.Text,
)

/**
 * settings.tokens.json ("Inputfield" Group12): the app's single-line input — a 53dp rounded
 * field-surface box with a muted placeholder, shared by [ActionDialog] and the sign-in screen.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    kind: FieldKind = FieldKind.Text,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    // The sign-in page sits on black, where the dialog's lighter field surface looked washed out.
    containerColor: Color = LocalAppColors.current.fieldSurface,
    shape: Shape = RoundedCornerShape(11.dp),
    borderColor: Color = Color.Transparent,
) {
    val colors = LocalAppColors.current
    val isSecret = kind == FieldKind.Password || kind == FieldKind.NewPassword
    val multiLine = kind == FieldKind.Phrase
    BasicTextField(
        value = value,
        onValueChange = { input -> onValueChange(if (kind == FieldKind.Code) input.filter(Char::isDigit).take(6) else input) },
        enabled = enabled,
        singleLine = !multiLine,
        textStyle = RequestMetaText.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.onSurface),
        visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = kind == FieldKind.Text,
            keyboardType = when (kind) {
                FieldKind.Password, FieldKind.NewPassword, FieldKind.Phrase, FieldKind.PhraseWord -> KeyboardType.Password
                FieldKind.Code -> KeyboardType.NumberPassword
                FieldKind.Username -> KeyboardType.Ascii
                FieldKind.Text -> KeyboardType.Text
            },
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        modifier = modifier
            .fillMaxWidth()
            .then(if (multiLine) Modifier.heightIn(min = 106.dp) else Modifier.height(53.dp))
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .semantics {
                when (kind) {
                    FieldKind.Username -> contentType = ContentType.Username
                    FieldKind.Password -> contentType = ContentType.Password
                    FieldKind.NewPassword -> contentType = ContentType.NewPassword
                    FieldKind.Code -> contentType = ContentType.SmsOtpCode
                    FieldKind.Text, FieldKind.Phrase, FieldKind.PhraseWord -> Unit
                }
            },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = if (multiLine) 15.dp else 0.dp),
                contentAlignment = if (multiLine) Alignment.TopStart else Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = colors.fieldPlaceholder, style = RequestMetaText)
                }
                innerTextField()
            }
        },
    )
}

@Preview(name = "Text field", showBackground = true, backgroundColor = 0xFF2D2D2D, widthDp = 340)
@Composable
private fun AppTextFieldPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        AppTextField(value = "", onValueChange = {}, placeholder = "Username", kind = FieldKind.Username)
    }
}
