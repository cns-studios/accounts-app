package com.cns.accounts.data

import androidx.compose.runtime.Immutable

// search.tokens.json ("Search"): result cards shown once the user starts typing; whichever
// substring of the query matches is highlighted within each one's description. Results come
// from a local index (settings entries + recent account activity, see ui/search/SearchIndex.kt).
// Help and Q&A results need the Help Center (help.cns-studios.com), which doesn't exist yet,
// so those kinds keep their icons but nothing produces them.
enum class SearchResultKind { Settings, Activity, Help, QuestionAnswer }

@Immutable
data class SearchResult(
    val id: String,
    val kind: SearchResultKind,
    val title: String,
    val description: String,
)

fun sampleSearchResults(): List<SearchResult> = listOf(
    SearchResult("settings-2fa", SearchResultKind.Settings, "Settings", "Change 2FA settings"),
    SearchResult("help-2fa", SearchResultKind.Help, "Help", "Learn about 2FA"),
    SearchResult("qa-2fa", SearchResultKind.QuestionAnswer, "Q&A", "Is 2FA increasing security?"),
)
