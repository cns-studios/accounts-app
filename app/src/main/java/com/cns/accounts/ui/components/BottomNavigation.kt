package com.cns.accounts.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.R
import com.cns.accounts.ui.theme.HomeNavigationText
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalDimensions
import com.cns.accounts.ui.theme.LocalRadii

/**
 * token.json (PositionHistory export): a two-destination segmented control.
 * Frame1 (outer, bg=rgba(45,45,45,1), radius=85px, padding=4px, gap=4px) contains:
 *  - the "Home" segment (expanded 194x46 dark pill with cross-dot icon + label when active,
 *    collapsed to a 46x46 dimmed cross-dot glyph when inactive)
 *  - the "History" segment (expanded 194x46 dark pill with scattered-dot icon + label when
 *    active, collapsed to a 46x46 dimmed scattered-dot glyph when inactive)
 * Home is always the left segment, History is always the right segment; only their widths and
 * content (bright label+icon vs. dimmed icon only) change when the active tab changes.
 */
private const val HOME_TAB = "home"
private const val HISTORY_TAB = "history"
private val DRAG_SWITCH_THRESHOLD_PX = 60f

@Composable
fun HomeBottomNavigation(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val dimensions = LocalDimensions.current
    Row(
        modifier = modifier.width(dimensions.navWidth),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InertNavCircle(TokenIconKind.User)
        SegmentedTabControl(selectedTab = selectedTab, onTabSelected = onTabSelected)
        InertNavCircle(TokenIconKind.Search)
    }
}

@Composable
private fun InertNavCircle(icon: TokenIconKind) {
    val colors = LocalAppColors.current
    val dimensions = LocalDimensions.current
    Box(
        modifier = Modifier
            .size(dimensions.navHeight)
            .clip(CircleShape)
            .background(colors.elevatedSurface),
        contentAlignment = Alignment.Center,
    ) {
        TokenIcon(icon, tint = colors.onSurface)
    }
}

@Composable
private fun SegmentedTabControl(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    val dimensions = LocalDimensions.current
    val radii = LocalRadii.current
    val homeActive = selectedTab == HOME_TAB

    val homeWidth by animateDpAsState(
        targetValue = if (homeActive) dimensions.navSelectedWidth else dimensions.navSelectedInnerHeight,
        label = "home-tab-width",
    )
    val historyWidth by animateDpAsState(
        targetValue = if (homeActive) dimensions.navSelectedInnerHeight else dimensions.navSelectedWidth,
        label = "history-tab-width",
    )

    var dragAccumulatedPx by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = Modifier
            .width(dimensions.navSelectedFrameWidth)
            .height(dimensions.navHeight)
            .clip(RoundedCornerShape(radii.nav))
            .background(colors.elevatedSurface)
            .padding(4.dp)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> dragAccumulatedPx += delta },
                onDragStopped = {
                    if (homeActive && dragAccumulatedPx > DRAG_SWITCH_THRESHOLD_PX) {
                        onTabSelected(HISTORY_TAB)
                    } else if (!homeActive && dragAccumulatedPx < -DRAG_SWITCH_THRESHOLD_PX) {
                        onTabSelected(HOME_TAB)
                    }
                    dragAccumulatedPx = 0f
                },
            )
            .semantics { role = Role.Tab },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabSegment(
            route = HOME_TAB,
            active = homeActive,
            width = homeWidth,
            label = "Home",
            onClick = { onTabSelected(HOME_TAB) },
        ) { tint -> TokenDotMark(tint) }
        TabSegment(
            route = HISTORY_TAB,
            active = !homeActive,
            width = historyWidth,
            label = "History",
            onClick = { onTabSelected(HISTORY_TAB) },
        ) { tint -> TokenMiniDotMark(tint) }
    }
}

@Composable
private fun TabSegment(
    route: String,
    active: Boolean,
    width: androidx.compose.ui.unit.Dp,
    label: String,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val colors = LocalAppColors.current
    val dimensions = LocalDimensions.current
    val radii = LocalRadii.current
    Box(
        modifier = Modifier
            .width(width)
            .height(dimensions.navSelectedInnerHeight)
            .clip(RoundedCornerShape(radii.nav))
            .background(if (active) colors.background else Color.Transparent)
            .semantics { role = Role.Tab }
            .clickable(enabled = !active) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon(if (active) colors.onSurface else colors.mutedSurface)
            AnimatedVisibility(visible = active, enter = fadeIn(), exit = fadeOut()) {
                Text(label, color = colors.onSurface, style = HomeNavigationText)
            }
        }
    }
}

@Composable
private fun TokenDotMark(tint: Color) {
    Box(modifier = Modifier.size(38.dp)) {
        val positions = listOf(
            0.dp to 7.dp, 7.dp to 7.dp, 14.dp to 7.dp,
            7.dp to 0.dp, 7.dp to 14.dp,
        )
        positions.forEach { (x, y) ->
            Image(
                painter = painterResource(R.drawable.token_asset_0),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier
                    .size(4.dp)
                    .offset(x = x + 9.dp, y = y + 10.dp),
            )
        }
    }
}

@Composable
private fun TokenMiniDotMark(tint: Color) {
    Box(modifier = Modifier.size(38.dp)) {
        val positions = listOf(0.dp to 8.dp, 8.dp to 0.dp, 16.dp to 16.dp)
        positions.forEach { (x, y) ->
            Image(
                painter = painterResource(R.drawable.token_asset_1),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier
                    .size(4.dp)
                    .offset(x = x + 9.dp, y = y + 9.dp),
            )
        }
    }
}

@Preview
@Composable
private fun BottomNavigationPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme { HomeBottomNavigation(HOME_TAB, {}) }
}

@Preview(name = "Bottom navigation - history", showBackground = true)
@Composable
private fun BottomNavigationHistoryPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme { HomeBottomNavigation(HISTORY_TAB, {}) }
}

@Preview(name = "Bottom navigation dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BottomNavigationDarkPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme(darkTheme = true) {
        HomeBottomNavigation(HOME_TAB, {})
    }
}
