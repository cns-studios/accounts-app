package com.cns.accounts.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.cns.accounts.R
import com.cns.accounts.ui.theme.HomeNavigationText
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalDimensions
import com.cns.accounts.ui.theme.LocalRadii
import com.cns.accounts.ui.theme.SearchBarText
import kotlinx.coroutines.delay

/**
 * token.json (PositionHistory export): a two-destination segmented control.
 * Frame1 (outer, bg=rgba(45,45,45,1), radius=85px, padding=4px, gap=4px) contains:
 *  - the "Home" segment (expanded 194x46 dark pill with cross-dot icon + label when active,
 *    collapsed to a 46x46 dimmed cross-dot glyph when inactive)
 *  - the "History" segment (expanded 194x46 dark pill with scattered-dot icon + label when
 *    active, collapsed to a 46x46 dimmed scattered-dot glyph when inactive)
 * Home is always the left segment, History is always the right segment; only their widths and
 * content (bright label+icon vs. dimmed icon only) change when the active tab changes.
 *
 * The control doubles as a swipe track: dragging anywhere on it slides the active pill 1:1 with
 * the finger (progress 0f = Home, 1f = History) and settles to whichever side it's closer to (or
 * whichever side a fast flick pointed at) with a spring. `onSwipeProgress` mirrors that same
 * progress so a caller can drive a matching page transition.
 */
private const val HOME_TAB = "home"
private const val HISTORY_TAB = "history"
private const val FLING_VELOCITY_THRESHOLD = 900f
private val TabSettleSpring = spring<Float>(dampingRatio = 0.68f, stiffness = 380f)

private val SearchExpandSpec = tween<Float>(320, easing = FastOutSlowInEasing)

@Composable
fun HomeBottomNavigation(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSwipeProgress: (Float) -> Unit = {},
    onUserClick: () -> Unit = {},
    isSearchActive: Boolean = false,
    onSearchActiveChange: (Boolean) -> Unit = {},
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
) {
    val dimensions = LocalDimensions.current
    val haptic = LocalHapticFeedback.current
    // 0f = the normal three-part nav (user circle + tab switch + search circle); 1f = the search
    // circle has expanded to fill the whole bar, replacing the other two. The user/tab controls
    // shrink to 0dp width (rather than being removed outright) so the search control's own
    // Modifier.weight(1f) picks up exactly the width they give up, frame by frame.
    val searchProgress by animateFloatAsState(
        targetValue = if (isSearchActive) 1f else 0f,
        animationSpec = SearchExpandSpec,
        label = "search-expand-progress",
    )
    Row(
        modifier = modifier.width(dimensions.navWidth),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(lerp(dimensions.navHeight, 0.dp, searchProgress))
                .graphicsLayer { alpha = 1f - searchProgress },
        ) {
            if (searchProgress < 1f) {
                NavCircle(TokenIconKind.User, onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onUserClick()
                })
            }
        }
        Box(
            modifier = Modifier
                .width(lerp(dimensions.navSelectedFrameWidth, 0.dp, searchProgress))
                .graphicsLayer { alpha = 1f - searchProgress },
        ) {
            if (searchProgress < 1f) {
                SegmentedTabControl(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(tab)
                    },
                    onSwipeProgress = onSwipeProgress,
                )
            }
        }
        SearchControl(
            modifier = Modifier.weight(1f),
            expanded = isSearchActive,
            progress = searchProgress,
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onExpand = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSearchActiveChange(true)
            },
            onCollapse = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSearchQueryChange("")
                onSearchActiveChange(false)
            },
        )
    }
}

// search.tokens.json ("Nav" > "Frame2"): the same pill either collapsed to a plain search-icon
// circle, or expanded to a full search bar (icon, text field, clear button) — one shape at every
// width, so the expand animation never needs to swap between a circle and a stadium shape.
@Composable
private fun SearchControl(
    modifier: Modifier,
    expanded: Boolean,
    progress: Float,
    query: String,
    onQueryChange: (String) -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
) {
    val colors = LocalAppColors.current
    val dimensions = LocalDimensions.current
    val focusRequester = remember { FocusRequester() }
    val openLabel = stringResource(R.string.open_search)
    val clearLabel = stringResource(R.string.clear_search)
    val placeholder = stringResource(R.string.search_placeholder)
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(180)
            focusRequester.requestFocus()
        }
    }
    // Below the midpoint the bar is still narrow (down to a 54dp circle), so only the centered
    // icon renders there. The full icon+field+clear row only mounts once the bar is already more
    // than half expanded — by then there's easily enough width for it — and crossfades in against
    // the icon-only version fading out, rather than the two ever fighting for the same cramped space.
    val expandedContentAlpha = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(dimensions.navHeight)
            .clip(RoundedCornerShape(50))
            .background(colors.elevatedSurface)
            .then(
                if (!expanded) {
                    Modifier
                        .clickable(onClickLabel = openLabel, onClick = onExpand)
                        .semantics { contentDescription = openLabel }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (progress < 1f) {
            TokenIcon(
                TokenIconKind.Search,
                tint = colors.onSurface,
                modifier = Modifier.graphicsLayer { alpha = 1f - expandedContentAlpha },
            )
        }
        if (progress > 0.5f) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .graphicsLayer { alpha = expandedContentAlpha },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TokenIcon(TokenIconKind.Search, tint = colors.onSurface)
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = SearchBarText.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.onSurface),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(placeholder, color = colors.fieldPlaceholder, style = SearchBarText)
                            }
                            innerTextField()
                        }
                    },
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClickLabel = clearLabel, onClick = onCollapse)
                        .semantics { contentDescription = clearLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    TokenIcon(TokenIconKind.CircleX, tint = colors.onSurface)
                }
            }
        }
    }
}

@Composable
private fun NavCircle(icon: TokenIconKind, onClick: (() -> Unit)? = null) {
    val colors = LocalAppColors.current
    val dimensions = LocalDimensions.current
    Box(
        modifier = Modifier
            .size(dimensions.navHeight)
            .clip(CircleShape)
            .background(colors.elevatedSurface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        TokenIcon(icon, tint = colors.onSurface)
    }
}

@Composable
private fun SegmentedTabControl(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onSwipeProgress: (Float) -> Unit,
) {
    val colors = LocalAppColors.current
    val dimensions = LocalDimensions.current
    val radii = LocalRadii.current
    val density = LocalDensity.current

    // progress: 0f = Home fully active (expanded), 1f = History fully active (expanded).
    var isDragging by remember { mutableStateOf(false) }
    var liveProgress by remember { mutableFloatStateOf(if (selectedTab == HOME_TAB) 0f else 1f) }
    var settledProgress by remember { mutableFloatStateOf(if (selectedTab == HOME_TAB) 0f else 1f) }

    LaunchedEffect(selectedTab) {
        if (!isDragging) settledProgress = if (selectedTab == HOME_TAB) 0f else 1f
    }

    val progress by animateFloatAsState(
        targetValue = if (isDragging) liveProgress else settledProgress,
        animationSpec = if (isDragging) snap() else TabSettleSpring,
        label = "nav-tab-progress",
    )
    SideEffect { onSwipeProgress(progress) }

    val dragRangePx = with(density) {
        (dimensions.navSelectedWidth - dimensions.navSelectedInnerHeight).toPx()
    }
    val homeWidth = lerp(dimensions.navSelectedWidth, dimensions.navSelectedInnerHeight, progress)
    val historyWidth = lerp(dimensions.navSelectedInnerHeight, dimensions.navSelectedWidth, progress)

    Row(
        modifier = Modifier
            .width(dimensions.navSelectedFrameWidth)
            .height(dimensions.navHeight)
            .clip(RoundedCornerShape(radii.nav))
            .background(colors.elevatedSurface)
            .padding(4.dp)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    liveProgress = (liveProgress + delta / dragRangePx).coerceIn(0f, 1f)
                },
                onDragStarted = {
                    liveProgress = progress
                    isDragging = true
                },
                onDragStopped = { velocity ->
                    val settleToHistory = when {
                        velocity > FLING_VELOCITY_THRESHOLD -> true
                        velocity < -FLING_VELOCITY_THRESHOLD -> false
                        else -> liveProgress > 0.5f
                    }
                    isDragging = false
                    settledProgress = if (settleToHistory) 1f else 0f
                    val newTab = if (settleToHistory) HISTORY_TAB else HOME_TAB
                    if (newTab != selectedTab) onTabSelected(newTab)
                },
            )
            .semantics { role = Role.Tab },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabSegment(
            active = progress < 0.5f,
            activeFraction = 1f - progress,
            width = homeWidth,
            label = "Home",
            onClick = { onTabSelected(HOME_TAB) },
        ) { tint -> TokenDotMark(tint) }
        TabSegment(
            active = progress >= 0.5f,
            activeFraction = progress,
            width = historyWidth,
            label = "History",
            onClick = { onTabSelected(HISTORY_TAB) },
        ) { tint -> TokenMiniDotMark(tint) }
    }
}

@Composable
private fun TabSegment(
    active: Boolean,
    activeFraction: Float,
    width: Dp,
    label: String,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val colors = LocalAppColors.current
    val dimensions = LocalDimensions.current
    val radii = LocalRadii.current
    val backgroundColor = lerp(Color.Transparent, colors.background, activeFraction)
    val tint = lerp(colors.mutedSurface, colors.onSurface, activeFraction)
    // The icon sits at a fixed offset rather than being centered together with the label, so it
    // never has to jump to a new position when the label mounts/unmounts — every visual change
    // here (background, tint, label reveal) is driven continuously by the same `activeFraction`.
    val iconInset = (dimensions.navSelectedInnerHeight - dimensions.navIconSize) / 2
    Box(
        modifier = Modifier
            .width(width)
            .height(dimensions.navSelectedInnerHeight)
            .clip(RoundedCornerShape(radii.nav))
            .background(backgroundColor)
            .semantics { role = Role.Tab }
            .clickable(enabled = !active) { onClick() },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = iconInset)
                .size(dimensions.navIconSize),
            contentAlignment = Alignment.Center,
        ) {
            icon(tint)
        }
        Text(
            label,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = iconInset + dimensions.navIconSize + 4.dp)
                .graphicsLayer { alpha = activeFraction },
            color = colors.onSurface,
            style = HomeNavigationText,
        )
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
