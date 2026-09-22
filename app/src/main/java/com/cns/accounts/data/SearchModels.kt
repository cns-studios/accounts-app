package com.cns.accounts.data

import androidx.compose.runtime.Immutable

// search.tokens.json ("Search"): the fixed set of sample result cards shown once the user
// starts typing. They're static "fake" results — what's dynamic is which substring of the
// current query gets highlighted within each one's description.
enum class SearchResultKind { Settings, Help, QuestionAnswer }

@Immutable
data class SearchResult(
    val kind: SearchResultKind,
    val title: String,
    val description: String,
)

fun sampleSearchResults(): List<SearchResult> = listOf(
    SearchResult(SearchResultKind.Settings, "Settings", "Change 2FA settings"),
    SearchResult(SearchResultKind.Help, "Help", "Learn about 2FA"),
    SearchResult(SearchResultKind.QuestionAnswer, "Q&A", "Is 2FA increasing security?"),
)
