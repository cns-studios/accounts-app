package com.cns.accounts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.cns.accounts.R
import com.cns.accounts.ui.theme.HomeNavigationText
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalDimensions
import com.cns.accounts.ui.theme.LocalRadii

@Composable
fun HomeBottomNavigation(
    selected: String,
    onItemSelected: (String) -> Unit,
    onHomeDrag: (Float) -> Unit = {},
    onHomeDragEnd: () -> Unit = {},
    historySelected: Boolean = false,
    dragProgress: Float = 0f,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.width(LocalDimensions.current.navWidth),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavigationItem("profile", TokenIconKind.User, selected, onItemSelected, iconTint = lerp(LocalAppColors.current.onSurface, LocalAppColors.current.mutedSurface, dragProgress))
        NavigationItem(if (historySelected) "history" else "home", TokenIconKind.Dots, if (historySelected) "history" else selected, onItemSelected, onHomeDrag, onHomeDragEnd, iconTint = LocalAppColors.current.onSurface, dragProgress = dragProgress)
        NavigationItem("search", TokenIconKind.Search, selected, onItemSelected, iconTint = lerp(LocalAppColors.current.mutedSurface, LocalAppColors.current.onSurface, dragProgress))
    }
}

@Composable
private fun NavigationItem(
    route: String,
    icon: TokenIconKind,
    selected: String,
    onItemSelected: (String) -> Unit,
    onHomeDrag: (Float) -> Unit = {},
    onHomeDragEnd: () -> Unit = {},
    iconTint: androidx.compose.ui.graphics.Color = LocalAppColors.current.onSurface,
    dragProgress: Float = 0f,
) {
    val colors = LocalAppColors.current
    val dimensions = LocalDimensions.current
    val selectedItem = route == selected
    Box(
        modifier = Modifier
            .size(
                width = if (selectedItem) {
                    if (route == "home" || route == "history") dimensions.navSelectedFrameWidth else dimensions.navSelectedOuterWidth
                } else {
                    dimensions.navHeight
                },
                height = dimensions.navHeight,
            )
            .clip(if (selectedItem) RoundedCornerShape(LocalRadii.current.nav) else CircleShape)
            .background(colors.elevatedSurface)
            .then(
                if (route == "home" || route == "history") Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = onHomeDragEnd,
                        onDragCancel = onHomeDragEnd,
                    ) { change, dragAmount ->
                        change.consume()
                        onHomeDrag(dragAmount.x)
                    }
                } else Modifier,
            )
            .offset(x = if (route == "home" || route == "history") (dragProgress * 220f).dp else 0.dp)
            .semantics { role = Role.Tab }
            .clickable(enabled = route == "home") { onItemSelected(route) },
        contentAlignment = Alignment.Center,
    ) {
        if (selectedItem && route == "home") {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(dimensions.navSelectedWidth, dimensions.navSelectedInnerHeight)
                        .clip(RoundedCornerShape(LocalRadii.current.nav))
                        .background(colors.background)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TokenDotMark()
                        androidx.compose.material3.Text("Home", color = colors.onSurface, style = HomeNavigationText)
                    }
                }
                TokenMiniDotMark()
            }
        } else if (selectedItem && route == "history") {
            HistoryPill()
        } else {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TokenIcon(icon, tint = iconTint)
                if (selectedItem) {
                    androidx.compose.material3.Text("Home", color = colors.onSurface, style = HomeNavigationText)
                }

            }
        }
    }
}

@Composable
private fun HistoryPill() {
    val colors = LocalAppColors.current
    val dimensions = LocalDimensions.current
    Box(
        modifier = Modifier
            .size(dimensions.navSelectedWidth, dimensions.navSelectedInnerHeight)
            .clip(RoundedCornerShape(LocalRadii.current.nav))
            .background(colors.background)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Text("History", color = colors.onSurface, style = HomeNavigationText)
            TokenMiniDotMark()
        }
    }
}

@Composable
private fun TokenDotMark() {
    Box(modifier = Modifier.size(38.dp)) {
        val positions = listOf(
            0.dp to 7.dp, 7.dp to 7.dp, 14.dp to 7.dp,
            7.dp to 0.dp, 7.dp to 14.dp,
        )
        positions.forEach { (x, y) ->
            Image(
                painter = painterResource(R.drawable.token_asset_0),
                contentDescription = null,
                modifier = Modifier
                    .size(4.dp)
                    .offset(x = x + 9.dp, y = y + 10.dp),
            )
        }

    }
}

@Composable
private fun TokenMiniDotMark() {
    Box(modifier = Modifier.size(46.dp)) {
        val positions = listOf(0.dp to 8.dp, 8.dp to 0.dp, 16.dp to 16.dp)
        positions.forEach { (x, y) ->
            Image(
                painter = painterResource(R.drawable.token_asset_1),
                contentDescription = null,
                modifier = Modifier
                    .size(4.dp)
                    .offset(x = x + 13.5.dp, y = y + 13.dp),
            )
        }
    }
}

@Preview
@Composable
private fun BottomNavigationPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme { HomeBottomNavigation("home", {}) }
}

@Preview(name = "Bottom navigation dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BottomNavigationDarkPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme(darkTheme = true) {
        HomeBottomNavigation("home", {})
    }
}
