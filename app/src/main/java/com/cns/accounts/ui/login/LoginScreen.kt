package com.cns.accounts.ui.login

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.BuildConfig
import com.cns.accounts.R
import com.cns.accounts.ui.components.AppTextField
import com.cns.accounts.ui.components.FieldKind
import com.cns.accounts.ui.components.TokenIcon
import com.cns.accounts.ui.components.TokenIconKind
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.RequestCaptionText
import com.cns.accounts.ui.theme.RequestMetaText
import com.cns.accounts.ui.theme.WelcomeTitleText

// TEMP_ONBOARDING_TOKENS.json ("Onboarding"): black page, a centered serif "Welcome to CNS" over
// a gray subtitle (278dp wide), 43dp gap, then a 324dp column of two 11dp-radius buttons 12dp
// apart — white "Log In" and #2D2D2D "Create Account" — and a blurred #303030 glow (Vector10)
// along the bottom edge. "Log In" opens the credentials form and then the 2FA step, laid out
// the same way (the export only covers the welcome page); "Create Account" is the web sign-up.
private val SubtitleWidth = 278.dp
private val ButtonColumnWidth = 324.dp
private val ButtonRadius = 11.dp
private val ButtonPadding = PaddingValues(horizontal = 24.dp, vertical = 9.dp)
private val TitleButtonsGap = 43.dp
private val ButtonGap = 12.dp

@Composable
fun LoginScreen(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onShowCredentials: () -> Unit,
    onSubmitCredentials: () -> Unit,
    onSubmitCode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val step = when {
        state.loginToken != null -> LoginStep.TwoFactor
        state.showCredentials -> LoginStep.Credentials
        else -> LoginStep.Welcome
    }
    BackHandler(enabled = step != LoginStep.Welcome, onBack = onBack)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Vector10: rendered from the export's SVG (with its 55.9 gaussian blur) to a bitmap,
        // since the blur can't be expressed as a vector drawable on every supported release.
        Image(
            painter = painterResource(R.drawable.onboarding_glow),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
        )
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn().togetherWith(fadeOut()) },
            label = "login-step",
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 27.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TitleButtonsGap, Alignment.CenterVertically),
            ) {
                when (current) {
                    LoginStep.Welcome -> WelcomeStep(onShowCredentials)
                    LoginStep.Credentials -> CredentialsStep(state, onUsernameChange, onPasswordChange, onSubmitCredentials, onBack)
                    LoginStep.TwoFactor -> TwoFactorStep(state, onCodeChange, onSubmitCode, onBack)
                }
            }
        }
    }
}

private enum class LoginStep { Welcome, Credentials, TwoFactor }

@Composable
private fun WelcomeStep(onLogIn: () -> Unit) {
    val context = LocalContext.current
    Heading(stringResource(R.string.welcome_title), stringResource(R.string.welcome_subtitle))
    ButtonColumn {
        OnboardingButton(stringResource(R.string.log_in), onClick = onLogIn)
        OnboardingButton(stringResource(R.string.create_account_button), secondary = true) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.CNS_ACCOUNTS_WEB_URL}/signup")))
        }
    }
}

@Composable
private fun CredentialsStep(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Heading(stringResource(R.string.log_in), stringResource(R.string.sign_in_subtitle), onBack = onBack)
    ButtonColumn {
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
            onImeAction = onSubmit,
        )
        ErrorText(state)
        OnboardingButton(stringResource(R.string.log_in), isBusy = state.isBusy, onClick = onSubmit)
    }
}

@Composable
private fun TwoFactorStep(
    state: LoginUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Heading(
        stringResource(R.string.two_factor_title),
        stringResource(if (state.codePrefilled) R.string.two_factor_subtitle_prefilled else R.string.two_factor_subtitle),
        onBack = onBack,
    )
    ButtonColumn {
        AppTextField(
            value = state.code,
            onValueChange = onCodeChange,
            placeholder = stringResource(R.string.code_placeholder),
            kind = FieldKind.Code,
            enabled = !state.isBusy,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
        )
        ErrorText(state)
        OnboardingButton(stringResource(R.string.verify), isBusy = state.isBusy, onClick = onSubmit)
    }
}

/** Title + subtitle, centered; with [onBack], a round back button above them. */
@Composable
private fun Heading(title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    val colors = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onBack != null) {
            val haptic = LocalHapticFeedback.current
            val backLabel = stringResource(R.string.back)
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBack()
                },
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.elevatedSurface)
                    .semantics { contentDescription = backLabel },
            ) {
                TokenIcon(TokenIconKind.ChevronLeft, tint = colors.onSurface)
            }
        }
        Text(title, color = colors.onSurface, style = WelcomeTitleText, textAlign = TextAlign.Center)
        Text(
            subtitle,
            modifier = Modifier.width(SubtitleWidth),
            color = colors.fieldPlaceholder,
            style = RequestMetaText,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ButtonColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = ButtonColumnWidth)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ButtonGap),
    ) {
        content()
    }
}

@Composable
private fun ErrorText(state: LoginUiState) {
    val error = state.error ?: return
    Text(
        error.resolve(LocalContext.current),
        modifier = Modifier.fillMaxWidth(),
        color = LocalAppColors.current.accent,
        style = RequestCaptionText,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun OnboardingButton(label: String, secondary: Boolean = false, isBusy: Boolean = false, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val container = if (secondary) colors.elevatedSurface else Color.White
    val content = if (secondary) Color.White else Color.Black
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = !isBusy,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ButtonRadius),
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.7f),
            disabledContentColor = content,
        ),
    ) {
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = content, strokeWidth = 2.dp)
        } else {
            Text(label, style = RequestMetaText)
        }
    }
}

@Preview(showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun LoginWelcomePreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        LoginScreen(LoginUiState(), {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Login credentials", showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun LoginCredentialsPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        LoginScreen(LoginUiState(username = "aaron", showCredentials = true), {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Login 2FA step", showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun LoginTwoFactorPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        LoginScreen(LoginUiState(username = "aaron", loginToken = "token"), {}, {}, {}, {}, {}, {}, {})
    }
}
