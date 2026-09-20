package com.cns.accounts.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "Token icons", showBackground = true)
@Composable
private fun TokenIconPreview() {
    Row {
        TokenIconKind.entries.forEach { kind ->
            TokenIcon(kind, modifier = Modifier.padding(8.dp))
        }
    }
}

@Preview(name = "Token icons dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TokenIconDarkPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme(darkTheme = true) {
        TokenIconPreview()
    }
}
