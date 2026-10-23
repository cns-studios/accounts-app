package com.cns.accounts.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.R
import com.cns.accounts.data.HistoryEvent
import com.cns.accounts.data.sampleHistoryEvents
import com.cns.accounts.ui.components.NothingPullToRefreshBox
import com.cns.accounts.ui.components.TokenIcon
import com.cns.accounts.ui.format.relativeTime
import com.cns.accounts.ui.theme.HistoryEventDetailText
import com.cns.accounts.ui.theme.HistoryEventTimeText
import com.cns.accounts.ui.theme.HistoryEventTitleText
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalDimensions
import com.cns.accounts.ui.theme.LocalRadii
import com.cns.accounts.ui.theme.ProfileNameText

// history.json: the "History" tab — a page title, a scrollable feed of activity cards, and a
// fade-to-black scrim (Rectangle32) where the feed runs under the bottom nav bar. The feed is
// auth-v2's `/api/me/activity`, paged by cursor as the list nears its end.
@Immutable
data class HistoryUiState(
    val events: List<HistoryEvent> = emptyList(),
    val isLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val canLoadMore: Boolean = false,
)

// Start fetching the next page this many cards before the end, so scrolling rarely waits.
private const val LoadMoreThreshold = 3

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val dimensions = LocalDimensions.current
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 127.dp,
                    bottom = dimensions.navHeight + 19.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(17.dp),
            ) {
                items(state.events, key = { it.id }) { event ->
                    HistoryEventCard(event)
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
        // history.json (Rectangle32): a black fade at the bottom of the feed, top=702px of 874px.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(75.dp)
                .offset(y = 702.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, colors.background)),
                ),
        )
    }
}

@Composable
private fun HistoryEventCard(event: HistoryEvent) {
    val colors = LocalAppColors.current
    val radii = LocalRadii.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, colors.elevatedSurface, RoundedCornerShape(radii.card))
            .clip(RoundedCornerShape(radii.card))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TokenIcon(eventIcon(event.eventType), tint = colors.onSurface)
                Text(eventTitle(event.eventType), color = colors.onSurface, style = HistoryEventTitleText)
            }
            Text(relativeTime(event.createdAt), color = colors.mutedText, style = HistoryEventTimeText)
        }
        Text(eventDescription(event.eventType), color = colors.mutedText, style = HistoryEventDetailText)
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
