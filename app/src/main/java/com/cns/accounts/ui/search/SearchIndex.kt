package com.cns.accounts.ui.search

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.cns.accounts.R
import com.cns.accounts.data.HistoryEvent
import com.cns.accounts.data.SearchResult
import com.cns.accounts.data.SearchResultKind
import com.cns.accounts.ui.history.eventDescription
import com.cns.accounts.ui.history.eventTitle

/**
 * The local search index: every settings entry (with extra keywords so "pw" or "mfa" find the
 * right row) plus the recent account activity already loaded for the History tab. The Help
 * Center and FAQ results the design also shows need help.cns-studios.com, which isn't built yet.
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

private data class IndexedResult(val result: SearchResult, val haystack: String)

@Composable
fun rememberSearchResults(query: String, activity: List<HistoryEvent>): List<SearchResult> {
    val context = LocalContext.current
    val settingsTitle = context.getString(R.string.search_kind_settings)
    val settingsIndex = remember(context) {
        SettingsEntries.map { entry ->
            val description = context.getString(entry.description)
            IndexedResult(
                SearchResult("$SettingsResultPrefix${entry.id}", SearchResultKind.Settings, settingsTitle, description),
                haystack = "$description ${entry.keywords}".lowercase(),
            )
        }
    }
    val activityTitle = context.getString(R.string.search_kind_activity)
    val activityIndex = activity.map { event ->
        val description = listOfNotNull(eventTitle(event.eventType), event.subtitle).joinToString(" · ")
        IndexedResult(
            SearchResult("activity-${event.id}", SearchResultKind.Activity, activityTitle, description),
            haystack = "$description ${eventDescription(event.eventType)}".lowercase(),
        )
    }
    val needle = query.trim().lowercase()
    return remember(needle, settingsIndex, activityIndex) {
        if (needle.isEmpty()) emptyList()
        else (settingsIndex + activityIndex).filter { needle in it.haystack }.map { it.result }.take(MaxResults)
    }
}

private const val MaxResults = 30

/** Settings results are ids "settings-<entry id>"; the entry id is what Settings highlights. */
const val SettingsResultPrefix = "settings-"
