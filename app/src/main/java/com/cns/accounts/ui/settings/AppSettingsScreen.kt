package com.cns.accounts.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import com.cns.accounts.data.AppLanguage
import com.cns.accounts.data.LockDelay
import com.cns.accounts.data.NotificationEvent
import com.cns.accounts.data.update.AppRelease
import com.cns.accounts.data.update.UpdateState
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalSpacing
import com.cns.accounts.ui.theme.RequestCaptionText

/**
 * The app Settings page, opened from the profile header's gear (the account itself is Your
 * Account, behind the bottom nav's user button). Built like Your Account's toggle list - one-line
 * rows between dividers, a setting's options (lock delay, which events notify) unfolding under
 * it while it is on - with the app version above a Log Out button kept at the bottom.
 */
@Composable
fun AppSettingsScreen(
    state: AppSettingsUiState,
    onBack: () -> Unit,
    onToggleBiometricUnlock: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onLogOut: () -> Unit,
    modifier: Modifier = Modifier,
    onLockDelayChange: (LockDelay) -> Unit = {},
    onToggleEvent: (NotificationEvent, Boolean) -> Unit = { _, _ -> },
    onLanguageChange: (String) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onInstallUpdate: (AppRelease) -> Unit = {},
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
            item(key = "settings") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ToggleRow(
                        modifier = Modifier.searchHighlight(target == AppSettingsTarget.Biometric),
                        label = stringResource(R.string.biometric_unlock),
                        checked = state.biometricUnlock,
                        onCheckedChange = onToggleBiometricUnlock,
                    )
                    // Its options unfold under a setting only while it is on.
                    NestedRows(visible = state.biometricUnlock) {
                        ChoiceRow(
                            modifier = Modifier,
                            label = stringResource(R.string.lock_after),
                            options = LockDelay.entries,
                            selected = state.lockDelay,
                            optionLabel = { stringResource(it.label) },
                            onSelect = onLockDelayChange,
                            nested = true,
                        )
                    }
                    HorizontalDivider(color = colors.elevatedSurface, thickness = 2.dp)
                    ToggleRow(
                        modifier = Modifier.searchHighlight(target == AppSettingsTarget.Notifications),
                        label = stringResource(R.string.notifications),
                        checked = state.notifications,
                        onCheckedChange = onToggleNotifications,
                    )
                    NestedRows(visible = state.notifications) {
                        NotificationEvent.entries.forEach { event ->
                            ToggleRow(
                                modifier = Modifier,
                                label = stringResource(event.label),
                                checked = event.id !in state.mutedEvents,
                                onCheckedChange = { onToggleEvent(event, it) },
                                nested = true,
                            )
                        }
                    }
                    HorizontalDivider(color = colors.elevatedSurface, thickness = 2.dp)
                    ChoiceRow(
                        modifier = Modifier.searchHighlight(target == AppSettingsTarget.Language),
                        label = stringResource(R.string.language),
                        options = listOf("") + AppLanguage.Supported,
                        selected = state.language.takeIf { it in AppLanguage.Supported }.orEmpty(),
                        optionLabel = { languageLabel(it) },
                        onSelect = onLanguageChange,
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
            if (state.updatesEnabled) UpdateBlock(state.update, onCheckForUpdates, onInstallUpdate)
            state.logOutBlock?.let { block ->
                Text(
                    stringResource(block.message),
                    color = colors.mutedText,
                    style = RequestCaptionText,
                    textAlign = TextAlign.Center,
                )
            }
            ActionButton(
                text = stringResource(R.string.log_out),
                onClick = onLogOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .searchHighlight(target == AppSettingsTarget.LogOut),
                enabled = state.logOutBlock == null,
            )
        }
    }
}

/** Check for updates, and what came of it: up to date, a version to install, its progress, or an error. */
@Composable
private fun UpdateBlock(update: UpdateState, onCheck: () -> Unit, onInstall: (AppRelease) -> Unit) {
    val colors = LocalAppColors.current
    val status = when (update) {
        UpdateState.Idle, UpdateState.Checking -> null
        UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
        is UpdateState.Available -> stringResource(R.string.update_available, update.release.version.toString())
        is UpdateState.Downloading -> stringResource(R.string.update_downloading, (update.progress * 100).toInt())
        is UpdateState.Installing -> stringResource(R.string.update_installing)
        is UpdateState.Failed -> stringResource(update.message)
    }
    if (status != null) {
        Text(
            status,
            color = if (update is UpdateState.Failed) colors.onSurface else colors.mutedText,
            style = RequestCaptionText,
            textAlign = TextAlign.Center,
        )
    }
    val release = (update as? UpdateState.Available)?.release ?: (update as? UpdateState.Failed)?.release
    when {
        release != null -> ActionButton(
            text = stringResource(if (update is UpdateState.Failed) R.string.update_retry else R.string.update_install),
            onClick = { onInstall(release) },
            modifier = Modifier.fillMaxWidth(),
        )
        update is UpdateState.Downloading || update is UpdateState.Installing -> Unit
        else -> ActionButton(
            text = stringResource(if (update is UpdateState.Checking) R.string.update_checking else R.string.update_check),
            onClick = { if (update !is UpdateState.Checking) onCheck() },
            modifier = Modifier.fillMaxWidth(),
            secondary = true,
        )
    }
}

/** Secondary rows under a setting, indented, unfolding while [visible]. */
@Composable
private fun NestedRows(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = 500f)) + fadeIn(tween(200, delayMillis = 60)),
        exit = shrinkVertically(tween(220)) + fadeOut(tween(140)),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
    }
}

/** A language in its own name; "" is the device's language. */
@Composable
private fun languageLabel(tag: String): String = when (tag) {
    "en" -> "English"
    "de" -> "Deutsch"
    else -> stringResource(R.string.language_system)
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
