package com.cns.accounts.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    Row(
        modifier = modifier
            .width(dimensions.contentMaxWidth)
            .height(101.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.profile_avatar),
                contentDescription = "Profile avatar",
                modifier = Modifier.size(dimensions.avatar).semantics { contentDescription = "Profile avatar" },
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(profile.name, color = colors.onBackground, style = ProfileNameText)
                StatusChip(profile.twoFactorLabel)
            }

        }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(LocalRadii.current.pill))
                .background(colors.surface)
                .clickable(onClick = onSettingsClick)
                .padding(12.dp),
        ) {
            TokenIcon(TokenIconKind.Settings, tint = colors.onSurface)
        }
    }

}

@Preview
@Composable
private fun ProfileHeaderPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        ProfileHeader(com.cns.accounts.data.Profile("aaron", "2FA enabled"), {})
    }
}

@Preview(name = "Profile header dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileHeaderDarkPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme(darkTheme = true) {
        ProfileHeader(com.cns.accounts.data.Profile("aaron", "2FA enabled"), {})
    }
}
