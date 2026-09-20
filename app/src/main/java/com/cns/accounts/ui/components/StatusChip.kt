package com.cns.accounts.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalRadii
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
    background: Color = LocalAppColors.current.mutedSurface,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .clip(RoundedCornerShape(LocalRadii.current.card))
                .background(background)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            color = LocalAppColors.current.onLightSurface,
            style = textStyle,
        )
    }

}

@Preview
@Composable
private fun StatusChipPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme { StatusChip("2FA enabled") }
}

@Preview(name = "Status chip dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatusChipDarkPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme(darkTheme = true) { StatusChip("2FA enabled") }
}
