package com.cns.accounts.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.R
import com.cns.accounts.ui.components.OnboardingGlow
import com.cns.accounts.ui.components.PillButton
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.RequestMetaText
import com.cns.accounts.ui.theme.WelcomeTitleText

/**
 * Shown over everything while biometric unlock is on and the app is locked, in the onboarding
 * look (bottom glow, serif title over a gray subtitle, white pill). Prompts once on appear; the
 * button re-opens the prompt after a cancel.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val title = stringResource(R.string.unlock_prompt_title)
    val subtitle = stringResource(R.string.biometric_prompt_subtitle)
    val prompt = {
        // No usable lock method any more (e.g. the screen lock was removed): don't trap the user.
        if (!Biometrics.isAvailable(context)) onUnlocked()
        else Biometrics.authenticate(context, title, subtitle, onSuccess = onUnlocked)
    }
    LaunchedEffect(Unit) { prompt() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        OnboardingGlow(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 27.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(43.dp, Alignment.CenterVertically),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.app_locked_title), color = colors.onSurface, style = WelcomeTitleText, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.app_locked_subtitle),
                    modifier = Modifier.width(278.dp),
                    color = colors.fieldPlaceholder,
                    style = RequestMetaText,
                    textAlign = TextAlign.Center,
                )
            }
            PillButton(
                label = stringResource(R.string.unlock),
                modifier = Modifier
                    .widthIn(max = 324.dp)
                    .fillMaxWidth(),
                onClick = prompt,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 874)
@Composable
private fun LockScreenPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme { LockScreen(onUnlocked = {}) }
}
