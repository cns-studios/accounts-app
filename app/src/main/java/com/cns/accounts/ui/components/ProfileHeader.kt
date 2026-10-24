package com.cns.accounts.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.data.Profile
import com.cns.accounts.R
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalDimensions
import com.cns.accounts.ui.theme.LocalRadii
import com.cns.accounts.ui.theme.ProfileNameText

@Composable
fun ProfileHeader(
    profile: Profile,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val dimensions = LocalDimensions.current
    val haptic = LocalHapticFeedback.current
    val settingsLabel = stringResource(R.string.settings)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(101.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarImage(profile.avatarUrl, modifier = Modifier.size(dimensions.avatar))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(profile.name, color = colors.onBackground, style = ProfileNameText, maxLines = 1)
                TwoFactorChip(profile.twoFactorEnabled)
            }

        }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(LocalRadii.current.pill))
                .background(colors.surface)
                .clickable(onClickLabel = settingsLabel, role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSettingsClick()
                }
                .semantics { contentDescription = settingsLabel }
                .padding(12.dp),
        ) {
            TokenIcon(TokenIconKind.Settings, tint = colors.onSurface)
        }
    }

}

@Composable
fun twoFactorLabel(enabled: Boolean): String =
    stringResource(if (enabled) R.string.two_factor_enabled_label else R.string.two_factor_disabled_label)

/** The "2FA enabled" pill under the profile name; dark when 2FA is off. */
@Composable
fun TwoFactorChip(enabled: Boolean, modifier: Modifier = Modifier) {
    StatusChip(
        text = twoFactorLabel(enabled),
        modifier = modifier,
        background = if (enabled) LocalAppColors.current.mutedSurface else LocalAppColors.current.elevatedSurface,
        contentColor = if (enabled) LocalAppColors.current.onLightSurface else LocalAppColors.current.onSurface,
    )
}

@Preview
@Composable
private fun ProfileHeaderPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        ProfileHeader(com.cns.accounts.data.Profile("aaron", twoFactorEnabled = true), {})
    }
}

@Preview(name = "Profile header dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileHeaderDarkPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme(darkTheme = true) {
        ProfileHeader(com.cns.accounts.data.Profile("aaron", twoFactorEnabled = true), {})
    }
}
