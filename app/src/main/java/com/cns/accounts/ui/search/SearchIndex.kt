package com.cns.accounts.ui.search

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import com.cns.accounts.R
import com.cns.accounts.data.HistoryEvent
import com.cns.accounts.data.SearchResult
import com.cns.accounts.data.SearchResultKind
import com.cns.accounts.ui.history.eventTitle

/**
 * The local search index: every settings entry (with extra keywords so "pw" or "mfa" find the
 * right row) plus the recent account activity already loaded for the History tab. The Help
 * Center and FAQ results the design also shows need help.cns-studios.com, which isn't built yet.
 *
 * A result is only listed when the query shows up somewhere on its card, so there is always a
 * highlight: in its description, in its title (for queries of [MinTitleMatch]+ characters), or
 * in the keyword it was found by, which the card then shows.
 */
private class SettingsEntry(val id: String, @param:StringRes val description: Int, val keywords: String)

private val SettingsEntries = listOf(
    SettingsEntry("2fa", R.string.search_entry_2fa, "2fa mfa two factor authenticator totp code otp"),
    SettingsEntry("password", R.string.search_entry_password, "password pw passphrase"),
    SettingsEntry("username", R.string.search_entry_username, "username name rename profile"),
    SettingsEntry("avatar", R.string.search_entry_avatar, "avatar photo picture image profile"),
    SettingsEntry("biometric", R.string.search_entry_biometric, "biometric fingerprint face unlock lock timeout delay"),
    SettingsEntry("notifications", R.string.search_entry_notifications, "notifications alerts notify push login password username 2fa changes"),
    SettingsEntry("security-app", R.string.search_entry_security_app, "security app approve sign-in request"),
    SettingsEntry("recovery-phrase", R.string.search_entry_recovery_phrase, "recovery phrase backup restore"),
    SettingsEntry("security-center", R.string.search_entry_security_center, "security center score"),
    SettingsEntry("language", R.string.search_entry_language, "language sprache english deutsch german"),
    SettingsEntry("log-out", R.string.search_entry_log_out, "log out logout sign out signout"),
    SettingsEntry("delete", R.string.search_entry_delete, "delete remove account close"),
)

/** A result before matching: what its card shows, and the keywords that also find it. */
internal data class SearchCandidate(val result: SearchResult, val keywords: List<String> = emptyList())

/**
 * The candidates [query] finds, in order, as results ready to show. Matching ignores case; a
 * keyword has to start with the query, which needs [MinKeywordMatch]+ characters.
 */
internal fun searchCandidates(query: String, candidates: List<SearchCandidate>, limit: Int = MaxResults): List<SearchResult> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return emptyList()
    return candidates.asSequence().mapNotNull { candidate ->
        val result = candidate.result
        when {
            needle in result.description.lowercase() -> result
            needle.length >= MinTitleMatch && needle in result.title.lowercase() -> result
            needle.length >= MinKeywordMatch -> candidate.keywords.firstOrNull { it.startsWith(needle) }?.let { result.copy(matchedKeyword = it) }
            else -> null
        }
    }.take(limit).toList()
}

@Composable
fun rememberSearchResults(query: String, activity: List<HistoryEvent>): List<SearchResult> {
    // LocalResources (not LocalContext) so a configuration change re-reads the strings.
    val resources = LocalResources.current
    val settingsTitle = resources.getString(R.string.search_kind_settings)
    val settingsIndex = remember(resources) {
        SettingsEntries.map { entry ->
            SearchCandidate(
                SearchResult("$SettingsResultPrefix${entry.id}", SearchResultKind.Settings, settingsTitle, resources.getString(entry.description)),
                keywords = entry.keywords.split(' '),
            )
        }
    }
    val activityTitle = resources.getString(R.string.search_kind_activity)
    val activityIndex = activity.map { event ->
        val description = listOfNotNull(eventTitle(event.eventType, event.data), event.subtitle).joinToString(" · ")
        SearchCandidate(SearchResult("$ActivityResultPrefix${event.id}", SearchResultKind.Activity, activityTitle, description, timestamp = event.createdAt))
    }
    return remember(query, settingsIndex, activityIndex) { searchCandidates(query, settingsIndex + activityIndex) }
}

private const val MaxResults = 30
private const val MinTitleMatch = 3
private const val MinKeywordMatch = 2

/** Settings results are ids "settings-<entry id>"; the entry id is what Settings highlights. */
const val SettingsResultPrefix = "settings-"

/** Activity results are ids "activity-<event id>"; History scrolls to and highlights that event. */
const val ActivityResultPrefix = "activity-"
