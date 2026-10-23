package com.cns.accounts.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cns.accounts.data.SearchResult
import com.cns.accounts.data.SearchResultKind
import com.cns.accounts.data.sampleSearchResults
import com.cns.accounts.ui.components.AppCard
import com.cns.accounts.ui.components.TokenIcon
import com.cns.accounts.ui.components.TokenIconKind
import com.cns.accounts.ui.theme.LocalAppColors
import com.cns.accounts.ui.theme.LocalDimensions
import com.cns.accounts.ui.theme.LocalRadii
import com.cns.accounts.ui.theme.LocalSpacing
import com.cns.accounts.ui.theme.RequestMetaText
import com.cns.accounts.ui.theme.SearchResultTitleText

// search.tokens.json ("Search"): an opaque full-screen result feed shown while the bottom nav's
// search control is expanded. Results only appear once there's a query; whichever substring of
// the query matches inside a result's description is highlighted in the accent color, live.
@Composable
fun SearchScreen(
    query: String,
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val spacing = LocalSpacing.current
    val dimensions = LocalDimensions.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        if (query.isNotBlank()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = spacing.xl,
                    end = spacing.xl,
                    top = dimensions.headerTop,
                    bottom = dimensions.navHeight + spacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                items(results, key = { it.id }) { result ->
                    SearchResultCard(result, query, onClick = { onResultClick(result) })
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(result: SearchResult, query: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    AppCard(
        modifier = Modifier
            .clip(RoundedCornerShape(LocalRadii.current.card))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    ) {
        Column(
            modifier = Modifier.padding(LocalSpacing.current.md),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TokenIcon(iconFor(result.kind), tint = colors.onSurface)
                Text(result.title, color = colors.onSurface, style = SearchResultTitleText)
            }
            Text(
                highlightMatches(result.description, query, baseColor = colors.onSurface, matchColor = colors.accent),
                style = RequestMetaText,
            )
        }
    }
}

private fun iconFor(kind: SearchResultKind): TokenIconKind = when (kind) {
    SearchResultKind.Settings -> TokenIconKind.Settings
    SearchResultKind.Activity -> TokenIconKind.User
    SearchResultKind.Help -> TokenIconKind.LifeBuoy
    SearchResultKind.QuestionAnswer -> TokenIconKind.MessageQuestion
}

private fun highlightMatches(text: String, query: String, baseColor: Color, matchColor: Color): AnnotatedString {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) {
        return buildAnnotatedString { withStyle(SpanStyle(color = baseColor)) { append(text) } }
    }
    val lowerText = text.lowercase()
    val lowerQuery = trimmedQuery.lowercase()
    return buildAnnotatedString {
        var cursor = 0
        while (cursor <= text.length) {
            val matchIndex = lowerText.indexOf(lowerQuery, cursor)
            if (matchIndex == -1) {
                withStyle(SpanStyle(color = baseColor)) { append(text.substring(cursor)) }
                break
            }
            if (matchIndex > cursor) {
                withStyle(SpanStyle(color = baseColor)) { append(text.substring(cursor, matchIndex)) }
            }
            withStyle(SpanStyle(color = matchColor)) { append(text.substring(matchIndex, matchIndex + trimmedQuery.length)) }
            cursor = matchIndex + trimmedQuery.length
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 874)
@Composable
private fun SearchScreenPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        SearchScreen(query = "2fa", results = sampleSearchResults(), onResultClick = {})
    }
}

@Preview(name = "Search screen empty query", showBackground = true, widthDp = 390, heightDp = 874)
@Composable
private fun SearchScreenEmptyPreview() {
    com.cns.accounts.ui.theme.CNSAccountsTheme {
        SearchScreen(query = "", results = emptyList(), onResultClick = {})
    }
}
