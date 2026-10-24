package com.cns.accounts.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.R
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalSpacing
import com.cns.accounts.ui.theme.RequestCaptionText

/**
 * The app Settings page, opened from the profile header's gear (the account itself is Your
 * Account, behind the bottom nav's user button). Built like Your Account's toggle list — one-line
 * rows between dividers — with the app version above a Log Out button kept at the bottom.
 */
@Composable
fun AppSettingsScreen(
    state: AppSettingsUiState,
    onBack: () -> Unit,
    onToggleBiometricUnlock: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onLogOut: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: String? = null,
) {
    val colors = LocalAppColors.current
    val spacing = LocalSpacing.current
    val target = remember(highlight) { AppSettingsTarget.fromId(highlight) }
    val listState = rememberLazyListState()
    LaunchedEffect(target) {
        target?.itemIndex?.let { listState.animateScrollToItem(it) }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        SettingsHeader(
            title = stringResource(R.string.app_settings_title),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.lg),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = spacing.xl, end = spacing.xl, top = spacing.sm, bottom = spacing.xl),
        ) {
            item(key = "toggles") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ToggleRow(
                        modifier = Modifier.searchHighlight(target == AppSettingsTarget.Biometric),
                        label = stringResource(R.string.biometric_unlock),
                        checked = state.biometricUnlock,
                        onCheckedChange = onToggleBiometricUnlock,
                    )
                    HorizontalDivider(color = colors.elevatedSurface, thickness = 2.dp)
                    ToggleRow(
                        modifier = Modifier.searchHighlight(target == AppSettingsTarget.Notifications),
                        label = stringResource(R.string.notifications),
                        checked = state.notifications,
                        onCheckedChange = onToggleNotifications,
                    )
                }
            }
        }
        // Pinned to the bottom of the screen, below the list.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xl, vertical = spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.about_version_inline, state.about.versionName, state.about.versionCode),
                color = colors.mutedText,
                style = RequestCaptionText,
                textAlign = TextAlign.Center,
            )
            ActionButton(
                text = stringResource(R.string.log_out),
                onClick = onLogOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .searchHighlight(target == AppSettingsTarget.LogOut),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 874)
@Composable
private fun AppSettingsScreenPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        AppSettingsScreen(
            state = AppSettingsUiState(biometricUnlock = true),
            onBack = {},
            onToggleBiometricUnlock = {},
            onToggleNotifications = {},
            onLogOut = {},
        )
    }
}
