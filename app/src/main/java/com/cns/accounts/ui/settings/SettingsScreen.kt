package com.cns.accounts.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.cns.accounts.R
import com.cns.accounts.data.SecurityFactor
import com.cns.accounts.data.SecurityStatus
import com.cns.accounts.data.SettingsData
import com.cns.accounts.data.SettingsToggles
import com.cns.accounts.data.sampleSettingsData
import com.cns.accounts.ui.components.ActionDialog
import com.cns.accounts.ui.components.StatusChip
import com.cns.accounts.ui.components.TokenIcon
import com.cns.accounts.ui.components.TokenIconKind
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalDimensions
import com.cns.accounts.ui.theme.LocalRadii
import com.cns.accounts.ui.theme.LocalSpacing
import com.cns.accounts.ui.theme.ProfileNameText
import com.cns.accounts.ui.theme.RequestCaptionText
import com.cns.accounts.ui.theme.RequestMetaText
import com.cns.accounts.ui.theme.SecurityFactorLabelText
import com.cns.accounts.ui.theme.SecurityPercentText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// settings.tokens.json ("Your Account"): the account/settings page reached from the profile
// header's gear button. A fixed back-button header sits above a scrollable body so the page
// still works when the security/toggle content doesn't fit a phone screen.
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onToggleBiometricUnlock: (Boolean) -> Unit,
    onToggleStrongAccountProtection: (Boolean) -> Unit,
    onToggleTwoFactorAuthentication: (Boolean) -> Unit,
    onToggleRequireSecurityApp: (Boolean) -> Unit,
    onEditUsername: (String) -> Unit,
    onChangeAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onConfigureTwoFactor: () -> Unit,
    onSetupRecoveryPhrase: () -> Unit,
    onChangePassword: () -> Unit,
    onLogOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val spacing = LocalSpacing.current
    val data = state.data
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        SettingsHeader(onBack = onBack, modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.lg))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = spacing.xl,
                end = spacing.xl,
                bottom = spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.section),
        ) {
            item(key = "profile") {
                ProfileRow(
                    data = data,
                    onEditUsername = onEditUsername,
                    onChangeAvatar = onChangeAvatar,
                    onRemoveAvatar = onRemoveAvatar,
                    onConfigureTwoFactor = onConfigureTwoFactor,
                )
            }
            item(key = "security") {
                SecurityCenterCard(data.security)
            }
            item(key = "toggles") {
                ToggleGroup(
                    toggles = data.toggles,
                    onToggleBiometricUnlock = onToggleBiometricUnlock,
                    onToggleStrongAccountProtection = onToggleStrongAccountProtection,
                    onToggleTwoFactorAuthentication = onToggleTwoFactorAuthentication,
                    onToggleRequireSecurityApp = onToggleRequireSecurityApp,
                    onSetupRecoveryPhrase = onSetupRecoveryPhrase,
                )
            }
            item(key = "change-password") {
                LightButton(text = stringResource(R.string.change_password), onClick = onChangePassword)
            }
            item(key = "delete-account") {
                DeleteAccountCard(onDeleteAccount = onDeleteAccount)
            }
            item(key = "delete-account-log-out-gap") {
                Spacer(modifier = Modifier.height(spacing.lg))
            }
            item(key = "log-out") {
                LightButton(text = stringResource(R.string.log_out), onClick = onLogOut)
            }
            item(key = "bottom-inset") {
                Box(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val backLabel = stringResource(R.string.back)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onBack()
            },
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(colors.elevatedSurface)
                .semantics { contentDescription = backLabel },
        ) {
            TokenIcon(TokenIconKind.ChevronLeft, tint = colors.onSurface)
        }
        Text(stringResource(R.string.your_account), color = colors.onSurface, style = ProfileNameText)
    }
}

private enum class ProfileDialog { EditUsername, ChangeAvatar, RemoveAvatar, ConfigureTwoFactor }

@Composable
private fun ProfileRow(
    data: SettingsData,
    onEditUsername: (String) -> Unit,
    onChangeAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onConfigureTwoFactor: () -> Unit,
) {
    val colors = LocalAppColors.current
    val dimensions = LocalDimensions.current
    val haptic = LocalHapticFeedback.current
    var menuExpanded by remember { mutableStateOf(false) }
    var activeDialog by remember { mutableStateOf<ProfileDialog?>(null) }
    var usernameInput by remember(data.profile.name) { mutableStateOf(data.profile.name) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.profile_avatar),
                contentDescription = stringResource(R.string.profile_avatar),
                modifier = Modifier.size(dimensions.avatar).clip(CircleShape),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(data.profile.name, color = colors.onSurface, style = ProfileNameText)
                StatusChip(data.profile.twoFactorLabel)
            }
        }
        Box {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    menuExpanded = true
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.elevatedSurface)
                    .semantics { role = Role.Button },
            ) {
                TokenIcon(TokenIconKind.Edit, tint = colors.onSurface)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = colors.surface,
                shape = RoundedCornerShape(LocalRadii.current.card),
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_username), color = colors.onSurface) },
                    leadingIcon = { TokenIcon(TokenIconKind.User, tint = colors.onSurface) },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        menuExpanded = false
                        usernameInput = data.profile.name
                        activeDialog = ProfileDialog.EditUsername
                    },
                )
                HorizontalDivider(color = colors.elevatedSurface)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.change_avatar), color = colors.onSurface) },
                    leadingIcon = { TokenIcon(TokenIconKind.Image, tint = colors.onSurface) },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        menuExpanded = false
                        activeDialog = ProfileDialog.ChangeAvatar
                    },
                )
                HorizontalDivider(color = colors.elevatedSurface)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_avatar), color = colors.onSurface) },
                    leadingIcon = { TokenIcon(TokenIconKind.Trash, tint = colors.onSurface) },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        menuExpanded = false
                        activeDialog = ProfileDialog.RemoveAvatar
                    },
                )
                HorizontalDivider(color = colors.elevatedSurface)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.configure_2fa), color = colors.onSurface) },
                    leadingIcon = { TokenIcon(TokenIconKind.Shield, tint = colors.onSurface) },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        menuExpanded = false
                        activeDialog = ProfileDialog.ConfigureTwoFactor
                    },
                )
            }
        }
    }

    when (activeDialog) {
        ProfileDialog.EditUsername -> ActionDialog(
            title = stringResource(R.string.edit_username),
            subtitle = stringResource(R.string.edit_username_dialog_subtitle),
            fieldValue = usernameInput,
            onFieldValueChange = { usernameInput = it },
            fieldPlaceholder = stringResource(R.string.new_username_placeholder),
            primaryButtonLabel = stringResource(R.string.update),
            onDismissRequest = { activeDialog = null },
            onPrimaryAction = {
                onEditUsername(usernameInput)
                activeDialog = null
            },
        )
        ProfileDialog.ChangeAvatar -> ActionDialog(
            title = stringResource(R.string.change_avatar),
            subtitle = stringResource(R.string.change_avatar_dialog_subtitle),
            primaryButtonLabel = stringResource(R.string.choose_photo),
            onDismissRequest = { activeDialog = null },
            onPrimaryAction = {
                onChangeAvatar()
                activeDialog = null
            },
        )
        ProfileDialog.RemoveAvatar -> ActionDialog(
            title = stringResource(R.string.remove_avatar),
            subtitle = stringResource(R.string.remove_avatar_dialog_subtitle),
            primaryButtonLabel = stringResource(R.string.remove),
            onDismissRequest = { activeDialog = null },
            onPrimaryAction = {
                onRemoveAvatar()
                activeDialog = null
            },
        )
        ProfileDialog.ConfigureTwoFactor -> ActionDialog(
            title = stringResource(R.string.configure_2fa),
            subtitle = stringResource(R.string.configure_2fa_dialog_subtitle),
            primaryButtonLabel = stringResource(R.string.continue_action),
            onDismissRequest = { activeDialog = null },
            onPrimaryAction = {
                onConfigureTwoFactor()
                activeDialog = null
            },
        )
        null -> Unit
    }
}

@Composable
private fun SecurityCenterCard(security: SecurityStatus) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, colors.elevatedSurface, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.security_center), color = colors.onSurface, style = RequestMetaText)
        Text("${security.percentComplete}%", color = colors.onSurface, style = SecurityPercentText)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            security.factors.forEach { factor ->
                SecurityFactorBar(factor, modifier = Modifier.weight(1f))
            }
        }
    }
}

// settings.tokens.json (Rectangle36-39): each factor is a 76x7px pill — solid white when
// achieved, or an empty pill outlined with the accent red when still missing.
@Composable
private fun SecurityFactorBar(factor: SecurityFactor, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        val accent = colors.accent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .drawWithCache {
                    val cornerRadius = CornerRadius(size.height / 2)
                    onDrawBehind {
                        if (factor.achieved) {
                            drawRoundRect(color = Color.White, cornerRadius = cornerRadius)
                        } else {
                            val strokeWidth = 1.5.dp.toPx()
                            drawRoundRect(
                                color = accent,
                                cornerRadius = cornerRadius,
                                style = Stroke(
                                    width = strokeWidth,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx())),
                                ),
                            )
                        }
                    }
                },
        )
        Text(factor.label, color = colors.onSurface, style = SecurityFactorLabelText)
    }
}

@Composable
private fun ToggleGroup(
    toggles: SettingsToggles,
    onToggleBiometricUnlock: (Boolean) -> Unit,
    onToggleStrongAccountProtection: (Boolean) -> Unit,
    onToggleTwoFactorAuthentication: (Boolean) -> Unit,
    onToggleRequireSecurityApp: (Boolean) -> Unit,
    onSetupRecoveryPhrase: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ToggleRow(
            label = stringResource(R.string.biometric_unlock),
            checked = toggles.biometricUnlock,
            onCheckedChange = onToggleBiometricUnlock,
        )
        HorizontalDivider(color = colors.elevatedSurface, thickness = 2.dp)
        ToggleRow(
            label = stringResource(R.string.strong_account_protection),
            helpContentDescription = stringResource(R.string.strong_account_protection_help),
            helpHint = stringResource(R.string.strong_account_protection_hint),
            checked = toggles.strongAccountProtection,
            onCheckedChange = onToggleStrongAccountProtection,
        )
        HorizontalDivider(color = colors.elevatedSurface, thickness = 2.dp)
        ToggleRow(
            label = stringResource(R.string.two_factor_authentication),
            checked = toggles.twoFactorAuthentication,
            onCheckedChange = onToggleTwoFactorAuthentication,
        )
        HorizontalDivider(color = colors.elevatedSurface, thickness = 2.dp)
        ToggleRow(
            label = stringResource(R.string.require_security_app),
            helpContentDescription = stringResource(R.string.require_security_app_help),
            helpHint = stringResource(R.string.require_security_app_hint),
            checked = toggles.requireSecurityApp,
            onCheckedChange = onToggleRequireSecurityApp,
        )
        HorizontalDivider(color = colors.elevatedSurface, thickness = 2.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                Text(stringResource(R.string.setup), style = RequestMetaText)
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    helpContentDescription: String? = null,
    helpHint: String? = null,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = colors.onSurface, style = RequestMetaText)
            if (helpContentDescription != null && helpHint != null) {
                HelpHintIcon(contentDescription = helpContentDescription, hint = helpHint)
            }
        }
        SettingsToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private const val HelpHintVisibleMillis = 2400L

// A short, transient hint bubble shown above the "?" glyph on tap — the reference only marks
// these as help affordances, it doesn't specify a full help screen, so a small popover is enough.
@Composable
private fun HelpHintIcon(contentDescription: String, hint: String) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val hintVisible = remember { MutableTransitionState(false) }
    LaunchedEffect(hintVisible.targetState) {
        if (hintVisible.targetState) {
            delay(HelpHintVisibleMillis)
            hintVisible.targetState = false
        }
    }
    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = contentDescription) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    hintVisible.targetState = true
                }
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            TokenIcon(TokenIconKind.Help, tint = colors.mutedText)
        }
        // Kept mounted for as long as the exit fade is still running (targetState/currentState
        // diverge mid-fade), so the Popup doesn't just vanish the instant it's dismissed.
        if (hintVisible.targetState || hintVisible.currentState) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = with(density) { IntOffset(0, -(44.dp).roundToPx()) },
                onDismissRequest = { hintVisible.targetState = false },
            ) {
                AnimatedVisibility(
                    visibleState = hintVisible,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(320)),
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 220.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.elevatedSurface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(hint, color = colors.onSurface, style = RequestCaptionText)
                    }
                }
            }
        }
    }
}

// settings.tokens.json (Rectangle40/41): a fixed-size white pill track (66x36) with a fixed-size
// black thumb (26x26) that only slides between left/right — unlike Material3's Switch, the thumb
// never shrinks in the off state, and neither color ever changes.
private val ToggleTrackWidth = 66.dp
private val ToggleTrackHeight = 36.dp
private val ToggleThumbSize = 26.dp
private val ToggleThumbInset = 5.dp

@Composable
private fun SettingsToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) ToggleTrackWidth - ToggleThumbSize - ToggleThumbInset else ToggleThumbInset,
        label = "toggle-thumb-offset",
    )
    // settings.tokens.json (Rectangle40): white track when on, gray (150,150,150) when off.
    val trackColor by animateColorAsState(if (checked) Color.White else colors.toggleOffTrack, label = "toggle-track-color")
    Box(
        modifier = Modifier
            .size(ToggleTrackWidth, ToggleTrackHeight)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .toggleable(value = checked, role = Role.Switch) { newValue ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(newValue)
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = thumbOffset)
                .size(ToggleThumbSize)
                .clip(CircleShape)
                .background(Color.Black),
        )
    }
}

@Composable
private fun LightButton(text: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(LocalDimensions.current.actionHeight),
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
    ) {
        Text(text, style = RequestMetaText)
    }
}

@Composable
private fun DeleteAccountCard(onDeleteAccount: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
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

@Preview(showBackground = true, widthDp = 390, heightDp = 1200)
@Composable
private fun SettingsScreenPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        SettingsScreen(
            state = SettingsUiState(data = sampleSettingsData()),
            onBack = {},
            onToggleBiometricUnlock = {},
            onToggleStrongAccountProtection = {},
            onToggleTwoFactorAuthentication = {},
            onToggleRequireSecurityApp = {},
            onEditUsername = {},
            onChangeAvatar = {},
            onRemoveAvatar = {},
            onConfigureTwoFactor = {},
            onSetupRecoveryPhrase = {},
            onChangePassword = {},
            onLogOut = {},
            onDeleteAccount = {},
        )
    }
}

@Preview(name = "Settings screen dark", showBackground = true, widthDp = 390, heightDp = 1200, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenDarkPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme(darkTheme = true) {
        SettingsScreen(
            state = SettingsUiState(data = sampleSettingsData()),
            onBack = {},
            onToggleBiometricUnlock = {},
            onToggleStrongAccountProtection = {},
            onToggleTwoFactorAuthentication = {},
            onToggleRequireSecurityApp = {},
            onEditUsername = {},
            onChangeAvatar = {},
            onRemoveAvatar = {},
            onConfigureTwoFactor = {},
            onSetupRecoveryPhrase = {},
            onChangePassword = {},
            onLogOut = {},
            onDeleteAccount = {},
        )
    }
}
