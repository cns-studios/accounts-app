package com.cns.accounts.ui.login

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.cns.accounts.BuildConfig
import com.cns.accounts.R
import com.cns.accounts.ui.components.AppTextField
import com.cns.accounts.ui.components.BusySpinnerPhase
import com.cns.accounts.ui.components.ErrorBanner
import com.cns.accounts.ui.components.FieldKind
import com.cns.accounts.ui.components.LoopingSpinner
import com.cns.accounts.ui.components.OnboardingGlow
import com.cns.accounts.ui.components.PillButton
import com.cns.accounts.ui.components.PillButtonHeight
import com.cns.accounts.ui.components.RefreshSpinner
import com.cns.accounts.ui.components.SecureWindow
import com.cns.accounts.ui.components.rememberErrorShake
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.RequestCaptionText
import com.cns.accounts.ui.theme.RequestMetaText
import com.cns.accounts.ui.theme.WelcomeTitleText

// TEMP_ONBOARDING_TOKENS.json ("Onboarding"): black page, a centered serif "Welcome to CNS" over
// a gray subtitle (278dp wide), 43dp gap, then a 324dp column of buttons 12dp apart - white
// "Log In" and #2D2D2D "Create Account" - and a soft glow along the bottom edge. The buttons are
// 48dp pills. "Log In" splits in place into "Continue" (the credentials form, then the 2FA step)
// and "Use Passkey"; "Create Account" is the web sign-up. Steps slide in the direction of travel;
// the system back gesture steps back (there is no on-screen back button).
private val SubtitleWidth = 278.dp
private val ButtonColumnWidth = 324.dp
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
    onUseCodeInstead: () -> Unit = {},
    onUsePasskey: () -> Unit = {},
    onShowRecovery: () -> Unit = {},
    onPhraseChange: (String) -> Unit = {},
    onNewPasswordChange: (String) -> Unit = {},
    onNewPasswordConfirmChange: (String) -> Unit = {},
    onSubmitRecovery: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val step = when {
        state.recovering -> LoginStep.Recover
        state.awaitingApproval -> LoginStep.Approval
        state.loginToken != null -> LoginStep.TwoFactor
        state.showCredentials -> LoginStep.Credentials
        else -> LoginStep.Welcome
    }
    // "Log In" tapped: it has split into Continue / Use Passkey; back folds it up again.
    var logInSplit by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = step == LoginStep.Welcome && logInSplit) { logInSplit = false }
    BackHandler(enabled = step != LoginStep.Welcome, onBack = onBack)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        OnboardingGlow(modifier = Modifier.fillMaxSize())
        AnimatedContent(
            targetState = step,
            transitionSpec = { stepTransition(forward = targetState.ordinal > initialState.ordinal) },
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
                    LoginStep.Welcome -> WelcomeStep(
                        split = logInSplit,
                        onLogIn = { logInSplit = true },
                        onContinue = onShowCredentials,
                        onUsePasskey = onUsePasskey,
                    )
                    LoginStep.Credentials -> CredentialsStep(state, onUsernameChange, onPasswordChange, onSubmitCredentials, onShowRecovery)
                    LoginStep.TwoFactor -> TwoFactorStep(state, onCodeChange, onSubmitCode, onShowRecovery)
                    LoginStep.Approval -> ApprovalStep(state, onUseCodeInstead)
                    LoginStep.Recover -> RecoverStep(
                        state, onUsernameChange, onPhraseChange, onNewPasswordChange, onNewPasswordConfirmChange, onSubmitRecovery,
                    )
                }
            }
        }
    }
}

private enum class LoginStep { Welcome, Credentials, TwoFactor, Approval, Recover }

private val StepSpring = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 480f, visibilityThreshold = IntOffset.VisibilityThreshold)

/** Forward: the next step comes in from the right while the current one drifts left; back mirrors it. */
private fun stepTransition(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    return (
        slideInHorizontally(StepSpring) { direction * it / 3 } +
            fadeIn(tween(220, delayMillis = 70)) +
            scaleIn(spring(stiffness = 480f), initialScale = 0.97f)
        ) togetherWith (
        slideOutHorizontally(StepSpring) { -direction * it / 5 } +
            fadeOut(tween(140)) +
            scaleOut(tween(200), targetScale = 0.97f)
        )
}

@Composable
private fun WelcomeStep(split: Boolean, onLogIn: () -> Unit, onContinue: () -> Unit, onUsePasskey: () -> Unit) {
    val context = LocalContext.current
    Heading(stringResource(R.string.welcome_title), stringResource(R.string.welcome_subtitle))
    ButtonColumn {
        LogInButtons(split = split, onLogIn = onLogIn, onContinue = onContinue, onUsePasskey = onUsePasskey)
        PillButton(stringResource(R.string.create_account_button), secondary = true) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.CNS_ACCOUNTS_WEB_URL}/signup")))
        }
    }
}

/**
 * "Log In" that, once tapped, splits into two side-by-side pills: it shrinks to the left half
 * and becomes "Continue" while "Use Passkey" grows in on the right.
 */
@Composable
private fun LogInButtons(split: Boolean, onLogIn: () -> Unit, onContinue: () -> Unit, onUsePasskey: () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = if (split) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "log-in-split",
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(PillButtonHeight)) {
        val gap = 10.dp
        val half = (maxWidth - gap) / 2
        PillButton(
            label = if (split) stringResource(R.string.continue_label) else stringResource(R.string.log_in),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(lerp(maxWidth, half, progress)),
            onClick = if (split) onContinue else onLogIn,
        )
        if (progress > 0.01f) {
            PillButton(
                label = stringResource(R.string.use_passkey),
                secondary = true,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(half)
                    .graphicsLayer {
                        alpha = progress.coerceIn(0f, 1f)
                        val scale = 0.8f + 0.2f * progress
                        scaleX = scale
                        scaleY = scale
                        translationX = (1f - progress) * -half.toPx() * 0.35f
                    },
                onClick = onUsePasskey,
            )
        }
    }
}

@Composable
private fun CredentialsStep(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onShowRecovery: () -> Unit,
) {
    Heading(stringResource(R.string.log_in), stringResource(R.string.sign_in_subtitle))
    ButtonColumn(modifier = rememberErrorShake(state.error)) {
        LoginField(
            value = state.username,
            onValueChange = onUsernameChange,
            placeholder = stringResource(R.string.username_placeholder),
            kind = FieldKind.Username,
            enabled = !state.isBusy,
        )
        LoginField(
            value = state.password,
            onValueChange = onPasswordChange,
            placeholder = stringResource(R.string.password_placeholder),
            kind = FieldKind.Password,
            enabled = !state.isBusy,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
        )
        WithError(state) {
            PillButton(stringResource(R.string.finish), isBusy = state.isBusy, onClick = onSubmit)
        }
        TextLink(stringResource(R.string.lost_access_link), enabled = !state.isBusy, onClick = onShowRecovery)
    }
}

@Composable
private fun TwoFactorStep(
    state: LoginUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onShowRecovery: () -> Unit,
) {
    Heading(
        stringResource(R.string.two_factor_title),
        stringResource(
            when {
                state.usingFallback -> R.string.two_factor_subtitle_fallback
                state.codePrefilled -> R.string.two_factor_subtitle_prefilled
                else -> R.string.two_factor_subtitle
            },
        ),
    )
    ButtonColumn(modifier = rememberErrorShake(state.error)) {
        LoginField(
            value = state.code,
            onValueChange = onCodeChange,
            placeholder = stringResource(R.string.code_placeholder),
            kind = FieldKind.Code,
            enabled = !state.isBusy,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
        )
        WithError(state) {
            PillButton(stringResource(R.string.verify), isBusy = state.isBusy, onClick = onSubmit)
        }
        TextLink(stringResource(R.string.lost_authenticator_link), enabled = !state.isBusy, onClick = onShowRecovery)
    }
}

/** "Lost your password or phone?": the recovery phrase sets a new password and lifts 2FA. */
@Composable
private fun RecoverStep(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPhraseChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onNewPasswordConfirmChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    // The phrase is on screen while it's typed.
    SecureWindow()
    val colors = LocalAppColors.current
    Heading(stringResource(R.string.recover_title), stringResource(R.string.recover_subtitle))
    ButtonColumn(modifier = rememberErrorShake(state.error)) {
        LoginField(
            value = state.username,
            onValueChange = onUsernameChange,
            placeholder = stringResource(R.string.username_placeholder),
            kind = FieldKind.Username,
            enabled = !state.isBusy,
        )
        LoginField(
            value = state.phrase,
            onValueChange = onPhraseChange,
            placeholder = stringResource(R.string.recover_phrase_placeholder),
            kind = FieldKind.Phrase,
            enabled = !state.isBusy,
        )
        LoginField(
            value = state.newPassword,
            onValueChange = onNewPasswordChange,
            placeholder = stringResource(R.string.new_password_placeholder),
            kind = FieldKind.NewPassword,
            enabled = !state.isBusy,
        )
        LoginField(
            value = state.newPasswordConfirm,
            onValueChange = onNewPasswordConfirmChange,
            placeholder = stringResource(R.string.confirm_password_placeholder),
            kind = FieldKind.NewPassword,
            enabled = !state.isBusy,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
        )
        WithError(state) {
            PillButton(stringResource(R.string.recover_button), isBusy = state.isBusy, onClick = onSubmit)
        }
        Text(
            stringResource(R.string.recover_no_phrase),
            modifier = Modifier.fillMaxWidth(),
            color = colors.fieldPlaceholder,
            style = RequestCaptionText,
            textAlign = TextAlign.Center,
        )
    }
}

/** A quiet text action under a step's button. */
@Composable
private fun TextLink(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        color = colors.fieldPlaceholder,
        style = RequestCaptionText,
        textAlign = TextAlign.Center,
    )
}

/** Require Security App: waiting for the approver phone, with the 2FA code as the way out. */
@Composable
private fun ApprovalStep(state: LoginUiState, onUseCodeInstead: () -> Unit) {
    Heading(stringResource(R.string.login_approval_title), stringResource(R.string.login_approval_subtitle))
    ButtonColumn(modifier = rememberErrorShake(state.error)) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            LoopingSpinner()
        }
        WithError(state) {
            if (state.loginToken != null) {
                PillButton(stringResource(R.string.login_approval_use_code), secondary = true, onClick = onUseCodeInstead)
            }
        }
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    val colors = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun ButtonColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .widthIn(max = ButtonColumnWidth)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ButtonGap),
    ) {
        content()
    }
}

/** The sign-in fields: darker than the dialogs' (this page is black), with a hairline edge. */
@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    kind: FieldKind,
    enabled: Boolean,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        kind = kind,
        enabled = enabled,
        imeAction = imeAction,
        onImeAction = onImeAction,
        containerColor = colors.surface,
        shape = RoundedCornerShape(16.dp),
        borderColor = colors.elevatedSurface,
    )
}

/**
 * The step's error, as the app's error banner, unfolding just above [below] (the submit
 * button). One child of the column with it, so the folded-away banner leaves no extra gap.
 */
@Composable
private fun WithError(state: LoginUiState, below: @Composable () -> Unit) {
    Column {
        ErrorBanner(state.error?.resolve(LocalContext.current), contentPadding = PaddingValues(bottom = ButtonGap))
        below()
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

@Preview(name = "Login approval step", showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun LoginApprovalPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        LoginScreen(LoginUiState(username = "aaron", loginToken = "token", awaitingApproval = true), {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Login 2FA step", showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun LoginTwoFactorPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        LoginScreen(LoginUiState(username = "aaron", loginToken = "token"), {}, {}, {}, {}, {}, {}, {})
    }
}
