package com.cns.accounts.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.R
import com.cns.accounts.ui.components.TokenIcon
import com.cns.accounts.ui.components.TokenIconKind
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalDimensions
import com.cns.accounts.ui.theme.LocalSpacing
import com.cns.accounts.ui.theme.ProfileNameText
import com.cns.accounts.ui.theme.RequestCaptionText
import com.cns.accounts.ui.theme.RequestMetaText

/**
 * Shown over everything while biometric unlock is on and the app is locked. Prompts once on
 * appear; the button re-opens the prompt after a cancel.
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = LocalSpacing.current.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.lg, Alignment.CenterVertically),
    ) {
        TokenIcon(TokenIconKind.Shield, tint = colors.onSurface, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.app_locked_title), color = colors.onSurface, style = ProfileNameText)
        Text(
            stringResource(R.string.app_locked_subtitle),
            color = colors.mutedText,
            style = RequestCaptionText,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = prompt,
            modifier = Modifier
                .fillMaxWidth()
                .height(LocalDimensions.current.actionHeight),
            shape = RoundedCornerShape(11.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        ) {
            Text(stringResource(R.string.unlock), style = RequestMetaText)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 874)
@Composable
private fun LockScreenPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme { LockScreen(onUnlocked = {}) }
}
