package com.cns.accounts.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import com.cns.accounts.ui.theme.RequestMetaText
import com.cns.accounts.ui.theme.SettingsValueText
import kotlinx.coroutines.delay
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
                    HorizontalDivider(color = colors.elevatedSurface, thickness = 2.dp)
                    VersionRow(
                        versionName = state.about.versionName,
                        updatesEnabled = state.updatesEnabled,
                        update = state.update,
                        onCheck = onCheckForUpdates,
                        onInstall = onInstallUpdate,
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
            // The reason Log Out is refused shows only after the button is pressed.
            var blockShown by remember { mutableStateOf(false) }
            LaunchedEffect(blockShown) {
                if (blockShown) {
                    delay(BlockMessageMillis)
                    blockShown = false
                }
            }
            val block = state.logOutBlock
            AnimatedVisibility(
                visible = blockShown && block != null,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
            ) {
                // Kept while it fades out, after the block itself is gone.
                val shown = remember(block) { block }
                shown?.let {
                    Text(
                        stringResource(it.message),
                        color = colors.mutedText,
                        style = RequestCaptionText,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            ActionButton(
                text = stringResource(R.string.log_out),
                onClick = { if (block != null) blockShown = true else onLogOut() },
                modifier = Modifier
                    .fillMaxWidth()
                    .searchHighlight(target == AppSettingsTarget.LogOut),
                secondary = block != null,
            )
            Text(
                stringResource(R.string.about_version_inline, state.about.versionName, state.about.versionCode),
                color = colors.mutedText,
                style = RequestCaptionText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val BlockMessageMillis = 5_000L
private const val UpToDateMillis = 2_500L

/** What the Version row's pill offers. */
private enum class VersionAction { Check, Checking, UpToDate, Update, Progress, Retry }

/**
 * The app version, with the update check on the same row: a Check pill that becomes a spinner
 * while it looks, then Update when there is a newer release or Up to date for a moment.
 */
@Composable
private fun VersionRow(
    versionName: String,
    updatesEnabled: Boolean,
    update: UpdateState,
    onCheck: () -> Unit,
    onInstall: (AppRelease) -> Unit,
) {
    val colors = LocalAppColors.current
    // "Up to date" is only shown for a check made here, and goes back to Check after a moment.
    var requested by remember { mutableStateOf(false) }
    var settled by remember(update) { mutableStateOf(false) }
    LaunchedEffect(update) {
        if (update is UpdateState.UpToDate) {
            delay(UpToDateMillis)
            settled = true
        }
    }
    val release = (update as? UpdateState.Available)?.release ?: (update as? UpdateState.Failed)?.release
    val action = when {
        update is UpdateState.Checking || update is UpdateState.Installing -> VersionAction.Checking
        update is UpdateState.UpToDate && requested && !settled -> VersionAction.UpToDate
        update is UpdateState.Available -> VersionAction.Update
        update is UpdateState.Downloading -> VersionAction.Progress
        update is UpdateState.Failed && release != null -> VersionAction.Retry
        else -> VersionAction.Check
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(stringResource(R.string.version), color = colors.onSurface, style = RequestMetaText, maxLines = 1)
                Text(versionName, color = colors.mutedText, style = SettingsValueText, maxLines = 1)
            }
            if (updatesEnabled) {
                VersionPill(
                    action = action,
                    progress = (update as? UpdateState.Downloading)?.progress ?: 0f,
                    onClick = {
                        when (action) {
                            VersionAction.Check -> {
                                requested = true
                                onCheck()
                            }
                            VersionAction.Update, VersionAction.Retry -> release?.let(onInstall)
                            else -> Unit
                        }
                    },
                )
            }
        }
        if (updatesEnabled && update is UpdateState.Failed) {
            Text(stringResource(update.message), color = colors.mutedText, style = RequestCaptionText)
        }
    }
}

@Composable
private fun VersionPill(action: VersionAction, progress: Float, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val clickable = action == VersionAction.Check || action == VersionAction.Update || action == VersionAction.Retry
    val highlighted = action == VersionAction.Update
    Box(
        modifier = Modifier
            .height(32.dp)
            .widthIn(min = 72.dp)
            .clip(RoundedCornerShape(50))
            .background(if (highlighted) Color.White else colors.elevatedSurface)
            .then(
                if (clickable) {
                    Modifier.clickable(role = Role.Button) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = action,
            transitionSpec = { fadeIn(tween(180, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
            label = "versionAction",
        ) { shown ->
            if (shown == VersionAction.Checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = colors.onSurface,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    when (shown) {
                        VersionAction.Check -> stringResource(R.string.update_check_short)
                        VersionAction.UpToDate -> stringResource(R.string.update_up_to_date_short)
                        VersionAction.Update -> stringResource(R.string.update_install_short)
                        VersionAction.Progress -> stringResource(R.string.update_progress_short, (progress * 100).toInt())
                        else -> stringResource(R.string.update_retry)
                    },
                    color = if (highlighted) Color.Black else if (shown == VersionAction.UpToDate) colors.mutedText else colors.onSurface,
                    style = SettingsValueText,
                    maxLines = 1,
                )
            }
        }
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
