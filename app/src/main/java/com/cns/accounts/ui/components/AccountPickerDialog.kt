package com.cns.accounts.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cns.accounts.R
import com.cns.accounts.data.session.SignedInAccount
import com.cns.accounts.ui.theme.DialogButtonText
import com.cns.accounts.ui.theme.DialogSubtitleText
import com.cns.accounts.ui.theme.DialogTitleText
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.ToastText

/**
 * The accounts signed in on this device: tap one to switch to it, or add another. The active
 * account shows its profile picture ([activeAvatarUrl]); the others only know their name until
 * they are opened. Signing an account out is done from Settings while it is the active one, where
 * the rule about the authenticator and Security App already lives.
 */
@Composable
fun AccountPickerDialog(
    accounts: List<SignedInAccount>,
    activeAvatarUrl: String?,
    onSwitch: (Long) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!LocalDialogsShown.current) return
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false),
    ) {
        val blurred = rememberWindowBlur(24.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (blurred) 0.3f else 0.6f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss)
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(glassSurface(Color(0xFF222222), blurred, alpha = 0.78f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.accounts_title), color = colors.onSurface, style = DialogTitleText)
                    Text(stringResource(R.string.accounts_subtitle), color = colors.dialogSubtitle, style = DialogSubtitleText)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { account ->
                        AccountRow(
                            account = account,
                            avatarUrl = activeAvatarUrl.takeIf { account.isActive },
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (account.isActive) onDismiss() else onSwitch(account.userId)
                            },
                        )
                    }
                }
                Text(stringResource(R.string.accounts_notifications_note), color = colors.dialogSubtitle, style = ToastText)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable(role = Role.Button) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAdd()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.accounts_add), color = colors.onLightSurface, style = DialogButtonText, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AccountRow(account: SignedInAccount, avatarUrl: String?, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = if (account.isActive) 0.1f else 0.05f))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = account.isActive }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatarUrl != null) {
            AvatarImage(avatarUrl, modifier = Modifier.size(40.dp))
        } else {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.elevatedSurface),
                contentAlignment = Alignment.Center,
            ) {
                Text(account.username.take(1).uppercase(), color = colors.onSurface, style = DialogButtonText)
            }
        }
        Text(
            account.username.ifBlank { "?" },
            modifier = Modifier.weight(1f),
            color = colors.onSurface,
            style = DialogButtonText,
            maxLines = 1,
        )
        if (account.isActive) {
            Text(stringResource(R.string.accounts_active), color = colors.dialogSubtitle, style = ToastText)
        }
    }
}

@Preview
@Composable
private fun AccountPickerPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme(darkTheme = true) {
        AccountPickerDialog(
            accounts = listOf(SignedInAccount(1, "aaron", true), SignedInAccount(2, "sendly_dev", false)),
            activeAvatarUrl = null,
            onSwitch = {},
            onAdd = {},
            onDismiss = {},
        )
    }
}
