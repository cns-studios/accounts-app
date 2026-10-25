package com.cns.accounts.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.R
import com.cns.accounts.data.SettingsData
import com.cns.accounts.data.SettingsToggles
import com.cns.accounts.data.sampleSettingsData
import com.cns.accounts.ui.components.ActionDialog
import com.cns.accounts.ui.components.AvatarImage
import com.cns.accounts.ui.components.DialogField
import com.cns.accounts.ui.components.FieldKind
import com.cns.accounts.ui.components.TokenIcon
import com.cns.accounts.ui.components.TokenIconKind
import com.cns.accounts.ui.components.TwoFactorChip
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalDimensions
import com.cns.accounts.ui.theme.LocalRadii
import com.cns.accounts.ui.theme.LocalSpacing
import com.cns.accounts.ui.theme.ProfileNameText
import com.cns.accounts.ui.theme.RequestCaptionText
import com.cns.accounts.ui.theme.RequestMetaText
import com.cns.accounts.ui.theme.SettingsValueText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// settings.tokens.json ("Your Account"): the account page, reached from the bottom nav's user
// button. A fixed back-button header sits above a scrollable body so the page still works when
// the security/toggle content doesn't fit a phone screen. Device settings (biometric unlock,
// notifications, sign-out) live on the app Settings page instead (AppSettingsScreen.kt).
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onToggleTwoFactorAuthentication: (Boolean) -> Unit,
    onToggleRequireSecurityApp: (Boolean) -> Unit,
    onEditUsername: () -> Unit,
    onChangeAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onConfigureTwoFactor: () -> Unit,
    onSetupRecoveryPhrase: () -> Unit,
    onChangePassword: () -> Unit,
    onDeleteAccount: () -> Unit,
    onDialogFieldChange: (Int, String) -> Unit,
    onDialogConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
    onPickAvatar: () -> Unit,
    modifier: Modifier = Modifier,
    onSecurityFactorCelebrated: (String) -> Unit = {},
    highlight: String? = null,
) {
    val colors = LocalAppColors.current
    val spacing = LocalSpacing.current
    val data = state.data
    // Opened from a search result: scroll to that setting; its row flashes (searchHighlight).
    val target = remember(highlight) { SettingsTarget.fromId(highlight) }
    val listState = rememberLazyListState()
    LaunchedEffect(target) {
        target?.let { listState.animateScrollToItem(it.itemIndex) }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        SettingsHeader(
            title = stringResource(R.string.your_account),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.lg),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = spacing.xl,
                end = spacing.xl,
                // Room above the first row for a highlight drawn around it.
                top = spacing.sm,
                bottom = spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.section),
        ) {
            item(key = "profile") {
                ProfileRow(
                    modifier = Modifier.searchHighlight(target == SettingsTarget.Username || target == SettingsTarget.Avatar),
                    data = data,
                    onEditUsername = onEditUsername,
                    onChangeAvatar = onChangeAvatar,
                    onRemoveAvatar = onRemoveAvatar,
                    onConfigureTwoFactor = onConfigureTwoFactor,
                )
            }
            item(key = "security") {
                SecurityCenterCard(
                    security = data.security,
                    newlyCompleted = data.newlyCompletedFactors,
                    onCelebrated = onSecurityFactorCelebrated,
                    modifier = Modifier.searchHighlight(target == SettingsTarget.SecurityCenter),
                )
            }
            item(key = "toggles") {
                ToggleGroup(
                    target = target,
                    toggles = data.toggles,
                    onToggleTwoFactorAuthentication = onToggleTwoFactorAuthentication,
                    onToggleRequireSecurityApp = onToggleRequireSecurityApp,
                    onSetupRecoveryPhrase = onSetupRecoveryPhrase,
                )
            }
            item(key = "account-actions") {
                ActionButton(
                    text = stringResource(R.string.change_password),
                    onClick = onChangePassword,
                    secondary = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .searchHighlight(target == SettingsTarget.Password),
                )
            }
            item(key = "delete-account") {
                DeleteAccountCard(onDeleteAccount = onDeleteAccount, modifier = Modifier.searchHighlight(target == SettingsTarget.Delete))
            }
            item(key = "bottom-inset") {
                Box(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
    SettingsDialogHost(
        dialog = state.dialog,
        onFieldChange = onDialogFieldChange,
        onConfirm = onDialogConfirm,
        onDismiss = onDialogDismiss,
        onPickAvatar = onPickAvatar,
        hasAvatar = data.profile.avatarUrl != null,
    )
}

@Composable
private fun ProfileRow(
    modifier: Modifier,
    data: SettingsData,
    onEditUsername: () -> Unit,
    onChangeAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onConfigureTwoFactor: () -> Unit,
) {
    val colors = LocalAppColors.current
    val dimensions = LocalDimensions.current
    var menuExpanded by remember { mutableStateOf(false) }
    val hasAvatar = data.profile.avatarUrl != null
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarImage(data.profile.avatarUrl, modifier = Modifier.size(dimensions.avatar))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(data.profile.name, color = colors.onSurface, style = ProfileNameText, maxLines = 1)
                TwoFactorChip(data.profile.twoFactorEnabled)
            }
        }
        Box {
            RoundIconButton(TokenIconKind.Edit, contentDescription = stringResource(R.string.edit_profile), onClick = { menuExpanded = true })
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = colors.surface,
                shape = RoundedCornerShape(LocalRadii.current.card),
            ) {
                // The menu offers what applies to this account: no "remove" without an avatar,
                // and 2FA's entry says which way it switches.
                fun pick(action: () -> Unit): () -> Unit = {
                    menuExpanded = false
                    action()
                }
                SettingsMenuItem(stringResource(R.string.edit_username), TokenIconKind.User, pick(onEditUsername))
                SettingsMenuDivider()
                SettingsMenuItem(
                    stringResource(if (hasAvatar) R.string.change_avatar else R.string.upload_avatar),
                    TokenIconKind.Image,
                    pick(onChangeAvatar),
                )
                if (hasAvatar) {
                    SettingsMenuDivider()
                    SettingsMenuItem(stringResource(R.string.remove_avatar), TokenIconKind.Trash, pick(onRemoveAvatar))
                }
                SettingsMenuDivider()
                SettingsMenuItem(
                    stringResource(if (data.toggles.twoFactorAuthentication) R.string.disable_2fa_menu else R.string.enable_2fa_menu),
                    TokenIconKind.Shield,
                    pick(onConfigureTwoFactor),
                )
            }
        }
    }
}

@Composable
private fun ToggleGroup(
    target: SettingsTarget?,
    toggles: SettingsToggles,
    onToggleTwoFactorAuthentication: (Boolean) -> Unit,
    onToggleRequireSecurityApp: (Boolean) -> Unit,
    onSetupRecoveryPhrase: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ToggleRow(
            modifier = Modifier.searchHighlight(target == SettingsTarget.TwoFactor),
            label = stringResource(R.string.two_factor_authentication),
            checked = toggles.twoFactorAuthentication,
            onCheckedChange = onToggleTwoFactorAuthentication,
        )
        HorizontalDivider(color = colors.elevatedSurface, thickness = 2.dp)
        ToggleRow(
            modifier = Modifier.searchHighlight(target == SettingsTarget.SecurityApp),
            label = stringResource(R.string.require_security_app),
            helpContentDescription = stringResource(R.string.require_security_app_help),
            helpHint = stringResource(R.string.require_security_app_hint),
            checked = toggles.requireSecurityApp,
            onCheckedChange = onToggleRequireSecurityApp,
        )
        toggles.approverElsewhere?.let { name ->
            Text(
                if (name.isBlank()) stringResource(R.string.require_app_elsewhere_unnamed) else stringResource(R.string.require_app_elsewhere, name),
                color = colors.mutedText,
                style = RequestCaptionText,
            )
        }
        HorizontalDivider(color = colors.elevatedSurface, thickness = 2.dp)
        Row(
            modifier = Modifier
                .searchHighlight(target == SettingsTarget.RecoveryPhrase)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.recovery_phrase), color = colors.onSurface, style = RequestMetaText)
            val haptic = LocalHapticFeedback.current
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSetupRecoveryPhrase()
                },
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
            ) {
                Text(stringResource(if (toggles.recoveryPhrase) R.string.manage else R.string.setup), style = RequestMetaText)
            }
        }
    }
}

@Composable
private fun DeleteAccountCard(onDeleteAccount: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(colors.elevatedSurface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.delete_account_title), color = colors.onSurface, style = ProfileNameText)
            Text(stringResource(R.string.delete_account_description), color = colors.onSurface, style = RequestCaptionText)
        }
        HoldToDeleteButton(onConfirmed = onDeleteAccount)
    }
}

private const val HoldToDeleteDurationMillis = 900

private val HoldToDeleteLabelStyle = RequestMetaText.copy(fontWeight = FontWeight.Medium)

@Composable
private fun HoldToDeleteButton(onConfirmed: () -> Unit) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(LocalDimensions.current.actionHeight)
            .clip(RoundedCornerShape(11.dp))
            .background(colors.accent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val fillJob = scope.launch {
                            progress.animateTo(1f, animationSpec = tween(HoldToDeleteDurationMillis, easing = LinearEasing))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onConfirmed()
                        }
                        // A tick per ~1/8th of the hold so the press itself feels alive, not just
                        // its visual fill — separate from fillJob so it always stops on release.
                        val tickJob = scope.launch {
                            val tickInterval = HoldToDeleteDurationMillis / 8L
                            while (true) {
                                delay(tickInterval)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        tryAwaitRelease()
                        fillJob.cancel()
                        tickJob.cancel()
                        scope.launch { progress.animateTo(0f, animationSpec = tween(150)) }
                    },
                )
            }
            .semantics { role = Role.Button }
            // Reading progress.value here (draw phase, not a Modifier argument evaluated during
            // composition) keeps every hold-frame update to a redraw, not a recomposition.
            .drawWithCache {
                onDrawBehind {
                    drawRect(
                        color = Color.White.copy(alpha = 0.25f),
                        size = Size(size.width * progress.value, size.height),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.hold_to_delete),
            color = Color.White,
            style = HoldToDeleteLabelStyle,
        )
    }
}

/** Renders [SettingsUiState.dialog]: titles, fields and buttons per [SettingsDialogKind]. */
@Composable
private fun SettingsDialogHost(
    dialog: SettingsDialog?,
    onFieldChange: (Int, String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onPickAvatar: () -> Unit,
    hasAvatar: Boolean,
) {
    if (dialog == null) return
    val context = LocalContext.current
    val error = dialog.error?.resolve(context)
    fun fields(vararg specs: Pair<Int, FieldKind>) = specs.mapIndexed { index, (placeholder, kind) ->
        DialogField(context.getString(placeholder), dialog.values.getOrElse(index) { "" }, kind)
    }
    when (dialog.kind) {
        SettingsDialogKind.EditUsername -> ActionDialog(
            title = stringResource(R.string.edit_username),
            subtitle = stringResource(R.string.edit_username_dialog_subtitle),
            primaryButtonLabel = stringResource(R.string.update),
            fields = fields(R.string.new_username_placeholder to FieldKind.Username, R.string.current_password_placeholder to FieldKind.Password),
            onFieldChange = onFieldChange,
            errorText = error,
            isBusy = dialog.isBusy,
            onDismissRequest = onDismiss,
            onPrimaryAction = onConfirm,
        )
        SettingsDialogKind.ChangeAvatar -> ActionDialog(
            title = stringResource(if (hasAvatar) R.string.change_avatar else R.string.upload_avatar),
            subtitle = stringResource(R.string.change_avatar_dialog_subtitle),
            primaryButtonLabel = stringResource(R.string.choose_photo),
            errorText = error,
            isBusy = dialog.isBusy,
            onDismissRequest = onDismiss,
            onPrimaryAction = onPickAvatar,
        )
        SettingsDialogKind.RemoveAvatar -> ActionDialog(
            title = stringResource(R.string.remove_avatar),
            subtitle = stringResource(R.string.remove_avatar_dialog_subtitle),
            primaryButtonLabel = stringResource(R.string.remove),
            errorText = error,
            isBusy = dialog.isBusy,
            onDismissRequest = onDismiss,
            onPrimaryAction = onConfirm,
        )
        SettingsDialogKind.ConfigureTwoFactor -> ActionDialog(
            title = stringResource(R.string.configure_2fa),
            subtitle = stringResource(R.string.configure_2fa_dialog_subtitle),
            primaryButtonLabel = stringResource(R.string.set_up),
            errorText = error,
            isBusy = dialog.isBusy,
            onDismissRequest = onDismiss,
            onPrimaryAction = onConfirm,
        )
        SettingsDialogKind.DisableTwoFactor -> {
            val needsCode = dialog.values.size > 1
            ActionDialog(
                title = stringResource(R.string.disable_2fa_title),
                subtitle = stringResource(if (needsCode) R.string.disable_2fa_subtitle else R.string.disable_2fa_subtitle_on_device),
                primaryButtonLabel = stringResource(R.string.turn_off),
                fields = if (needsCode) {
                    fields(R.string.current_password_placeholder to FieldKind.Password, R.string.code_placeholder to FieldKind.Code)
                } else {
                    fields(R.string.current_password_placeholder to FieldKind.Password)
                },
                onFieldChange = onFieldChange,
                errorText = error,
                isBusy = dialog.isBusy,
                onDismissRequest = onDismiss,
                onPrimaryAction = onConfirm,
            )
        }
        SettingsDialogKind.ChangePassword -> ActionDialog(
            title = stringResource(R.string.change_password),
            subtitle = stringResource(R.string.change_password_dialog_subtitle),
            primaryButtonLabel = stringResource(R.string.update),
            fields = fields(
                R.string.current_password_placeholder to FieldKind.Password,
                R.string.new_password_placeholder to FieldKind.NewPassword,
                R.string.repeat_password_placeholder to FieldKind.NewPassword,
            ),
            onFieldChange = onFieldChange,
            errorText = error,
            isBusy = dialog.isBusy,
            onDismissRequest = onDismiss,
            onPrimaryAction = onConfirm,
        )
        SettingsDialogKind.EnableSecurityApp -> {
            val needsCode = dialog.values.size > 1
            ActionDialog(
                title = stringResource(R.string.require_app_dialog_title),
                subtitle = stringResource(if (needsCode) R.string.require_app_dialog_subtitle_code else R.string.require_app_dialog_subtitle),
                primaryButtonLabel = stringResource(R.string.turn_on),
                fields = if (needsCode) {
                    fields(R.string.current_password_placeholder to FieldKind.Password, R.string.code_placeholder to FieldKind.Code)
                } else {
                    fields(R.string.current_password_placeholder to FieldKind.Password)
                },
                onFieldChange = onFieldChange,
                errorText = error,
                isBusy = dialog.isBusy,
                onDismissRequest = onDismiss,
                onPrimaryAction = onConfirm,
            )
        }
        SettingsDialogKind.DeleteAccount -> {
            val needsCode = dialog.values.size > 1
            ActionDialog(
                title = stringResource(R.string.delete_account_dialog_title),
                subtitle = stringResource(if (needsCode) R.string.delete_account_dialog_subtitle_2fa else R.string.delete_account_dialog_subtitle),
                primaryButtonLabel = stringResource(R.string.delete),
                fields = if (needsCode) {
                    fields(R.string.current_password_placeholder to FieldKind.Password, R.string.code_placeholder to FieldKind.Code)
                } else {
                    fields(R.string.current_password_placeholder to FieldKind.Password)
                },
                onFieldChange = onFieldChange,
                errorText = error,
                isBusy = dialog.isBusy,
                onDismissRequest = onDismiss,
                onPrimaryAction = onConfirm,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 1200)
@Composable
private fun SettingsScreenPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        SettingsScreen(
            state = SettingsUiState(data = sampleSettingsData()),
            onBack = {},
            onToggleTwoFactorAuthentication = {},
            onToggleRequireSecurityApp = {},
            onEditUsername = {},
            onChangeAvatar = {},
            onRemoveAvatar = {},
            onConfigureTwoFactor = {},
            onSetupRecoveryPhrase = {},
            onChangePassword = {},
            onDeleteAccount = {},
            onDialogFieldChange = { _, _ -> },
            onDialogConfirm = {},
            onDialogDismiss = {},
            onPickAvatar = {},
        )
    }
}
