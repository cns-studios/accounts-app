package com.cns.accounts.ui.history

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.R
import com.cns.accounts.data.HistoryEvent
import com.cns.accounts.data.sampleHistoryEvents
import com.cns.accounts.ui.components.NothingPullToRefreshBox
import com.cns.accounts.ui.components.fadingEdges
import com.cns.accounts.ui.components.navAlignedWidth
import com.cns.accounts.ui.components.TokenIcon
import com.cns.accounts.ui.format.relativeTime
import com.cns.accounts.ui.settings.searchHighlight
import kotlinx.coroutines.delay
import com.cns.accounts.ui.theme.HistoryEventDetailText
import com.cns.accounts.ui.theme.HistoryEventTimeText
import com.cns.accounts.ui.theme.HistoryEventTitleText
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalDimensions
import com.cns.accounts.ui.theme.LocalRadii
import com.cns.accounts.ui.theme.ProfileNameText

// history.json: the "History" tab - a page title and a scrollable feed of activity cards that
// fades out (history.json's Rectangle32 fade) before it reaches the bottom nav, and under the
// title. The feed is auth-v2's `/api/me/activity`, paged by cursor as the list nears its end.
@Immutable
data class HistoryUiState(
    val events: List<HistoryEvent> = emptyList(),
    val isLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val canLoadMore: Boolean = false,
)

// Start fetching the next page this many cards before the end, so scrolling rarely waits.
private const val LoadMoreThreshold = 3

private val TitleBottom = 98.dp
private val NavFadeGap = 8.dp
private val FadeLength = 40.dp
private const val FocusScrollDelayMillis = 250L
// Covers searchHighlight's whole flash; ending it sooner would cut the fade short.
private const val FocusHighlightMillis = 2_500L

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    // An event to scroll to and flash briefly (opened from a search result); [onFocusShown] consumes it.
    focusEventId: Long? = null,
    onFocusShown: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val dimensions = LocalDimensions.current
    val navClearance = dimensions.navHeight + dimensions.navBottomGap + NavFadeGap
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - LoadMoreThreshold
        }
    }
    LaunchedEffect(nearEnd, state.canLoadMore, state.events.size) {
        if (nearEnd && state.canLoadMore) onLoadMore()
    }
    // After a refresh, and whenever a new event arrives on top, jump back up so it's in view.
    var refreshed by remember { mutableStateOf(false) }
    LaunchedEffect(state.isRefreshing) {
        if (state.isRefreshing) {
            refreshed = true
        } else if (refreshed) {
            refreshed = false
            listState.animateScrollToItem(0)
        }
    }
    val newestId = state.events.firstOrNull()?.id
    LaunchedEffect(newestId) {
        if (newestId != null && focusEventId == null) listState.animateScrollToItem(0)
    }
    var highlightedId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(focusEventId) {
        val id = focusEventId ?: return@LaunchedEffect
        val index = state.events.indexOfFirst { it.id == id }
        if (index >= 0) {
            // Let the swipe over to History settle first.
            delay(FocusScrollDelayMillis)
            listState.animateScrollToItem(index)
            highlightedId = id
        }
        // Last: consuming it restarts (cancels) this effect.
        onFocusShown()
    }
    LaunchedEffect(highlightedId) {
        if (highlightedId == null) return@LaunchedEffect
        delay(FocusHighlightMillis)
        highlightedId = null
    }
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.history_title),
            modifier = Modifier.offset(x = 104.dp, y = 55.dp),
            color = colors.onSurface,
            style = ProfileNameText,
        )
        NothingPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // Hidden under the title (55dp + 43dp line) and from just above the nav down,
                    // so cards neither cover the title nor show around/below the nav pill.
                    .fadingEdges(topHidden = TitleBottom, bottomHidden = navClearance),
                // Cards as wide as the bottom nav (see navAlignedWidth).
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 127.dp,
                    bottom = navClearance + FadeLength,
                ),
                verticalArrangement = Arrangement.spacedBy(17.dp),
            ) {
                items(state.events, key = { it.id }) { event ->
                    HistoryEventCard(event, modifier = Modifier.navAlignedWidth().animateItem().searchHighlight(event.id == highlightedId))
                }
                if (state.isLoaded && state.events.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.history_empty),
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.mutedText,
                            style = HistoryEventDetailText,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEventCard(event: HistoryEvent, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val radii = LocalRadii.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, colors.elevatedSurface, RoundedCornerShape(radii.card))
            .clip(RoundedCornerShape(radii.card))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A long title wraps in its own space; the time stays on one line at the end.
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TokenIcon(eventIcon(event.eventType), tint = colors.onSurface)
                Text(eventTitle(event.eventType, event.data), color = colors.onSurface, style = HistoryEventTitleText)
            }
            Text(relativeTime(event.createdAt), color = colors.mutedText, style = HistoryEventTimeText, maxLines = 1, softWrap = false)
        }
        Text(eventDescription(event.eventType, event.data), color = colors.mutedText, style = HistoryEventDetailText)
        Text(event.subtitle ?: stringResource(R.string.unknown_device), color = colors.mutedText, style = HistoryEventDetailText)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 874)
@Composable
private fun HistoryScreenPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        HistoryScreen(state = HistoryUiState(events = sampleHistoryEvents(), isLoaded = true), onRefresh = {}, onLoadMore = {})
    }
}

@Preview(name = "History event card", showBackground = true, widthDp = 370, heightDp = 130)
@Composable
private fun HistoryEventCardPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        HistoryEventCard(sampleHistoryEvents().first())
    }
}
