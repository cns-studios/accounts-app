package com.cns.accounts.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalRadii
import com.cns.accounts.ui.theme.LocalSpacing
import com.cns.accounts.ui.theme.LocalDimensions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Text

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = LocalAppColors.current.elevatedSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val spacing = LocalSpacing.current
    val radii = LocalRadii.current
    Box(
        modifier = modifier.width(LocalDimensions.current.contentMaxWidth),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(radii.card))
                .background(color)
                .padding(0.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing.md),
                content = content,
            )
        }
    }

}

@Preview
@Composable
private fun AppCardPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme { AppCard { Text("Card content") } }
}

@Preview(name = "App card dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppCardDarkPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme(darkTheme = true) {
        AppCard { Text("Card content") }
    }
}
