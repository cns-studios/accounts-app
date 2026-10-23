package com.cns.accounts.ui.login

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.BuildConfig
import com.cns.accounts.R
import com.cns.accounts.ui.components.AppTextField
import com.cns.accounts.ui.components.FieldKind
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalDimensions
import com.cns.accounts.ui.theme.LocalSpacing
import com.cns.accounts.ui.theme.ProfileNameText
import com.cns.accounts.ui.theme.RequestCaptionText
import com.cns.accounts.ui.theme.RequestMetaText

/**
 * Sign-in, in the app's own visual language (no design export exists for it): serif title,
 * the dialog input fields, and the white pill button from Settings. Two steps — credentials,
 * then the TOTP code when the account has 2FA. Sign-up stays on the web dashboard.
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmitCredentials: () -> Unit,
    onSubmitCode: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.xl, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg, Alignment.CenterVertically),
    ) {
        Spacer(Modifier.height(48.dp))
        val twoFactorStep = state.loginToken != null
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(if (twoFactorStep) R.string.two_factor_title else R.string.sign_in_title),
                color = colors.onSurface,
                style = ProfileNameText,
            )
            Text(
                stringResource(
                    when {
                        !twoFactorStep -> R.string.sign_in_subtitle
                        state.codePrefilled -> R.string.two_factor_subtitle_prefilled
                        else -> R.string.two_factor_subtitle
                    },
                ),
                color = colors.mutedText,
                style = RequestCaptionText,
            )
        }
        if (twoFactorStep) {
            AppTextField(
                value = state.code,
                onValueChange = onCodeChange,
                placeholder = stringResource(R.string.code_placeholder),
                kind = FieldKind.Code,
                enabled = !state.isBusy,
                imeAction = ImeAction.Done,
                onImeAction = onSubmitCode,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(
                    value = state.username,
                    onValueChange = onUsernameChange,
                    placeholder = stringResource(R.string.username_placeholder),
                    kind = FieldKind.Username,
                    enabled = !state.isBusy,
                )
                AppTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    placeholder = stringResource(R.string.password_placeholder),
                    kind = FieldKind.Password,
                    enabled = !state.isBusy,
                    imeAction = ImeAction.Done,
                    onImeAction = onSubmitCredentials,
                )
            }
        }
        state.error?.let { error ->
            Text(error.resolve(context), color = colors.accent, style = RequestCaptionText)
        }
        PrimaryButton(
            label = stringResource(if (twoFactorStep) R.string.verify else R.string.sign_in),
            isBusy = state.isBusy,
            onClick = if (twoFactorStep) onSubmitCode else onSubmitCredentials,
        )
        if (twoFactorStep) {
            TextLink(stringResource(R.string.use_different_account), onClick = onRestart)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.no_account_yet), color = colors.mutedText, style = RequestCaptionText)
                TextLink(stringResource(R.string.create_account)) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.CNS_ACCOUNTS_WEB_URL}/signup")))
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(label: String, isBusy: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = !isBusy,
        modifier = Modifier
            .fillMaxWidth()
            .height(LocalDimensions.current.actionHeight),
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
            disabledContainerColor = Color.White.copy(alpha = 0.7f),
            disabledContentColor = Color.Black,
        ),
    ) {
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
        } else {
            Text(label, style = RequestMetaText)
        }
    }
}

@Composable
private fun TextLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        color = LocalAppColors.current.onSurface,
        style = RequestCaptionText,
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 874)
@Composable
private fun LoginScreenPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        LoginScreen(LoginUiState(username = "aaron"), {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Login 2FA step", showBackground = true, widthDp = 390, heightDp = 874)
@Composable
private fun LoginTwoFactorPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        LoginScreen(LoginUiState(username = "aaron", loginToken = "token"), {}, {}, {}, {}, {}, {})
    }
}
