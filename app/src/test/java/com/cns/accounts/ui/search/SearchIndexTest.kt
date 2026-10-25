package com.cns.accounts.ui.search

import com.cns.accounts.data.SearchResult
import com.cns.accounts.data.SearchResultKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexTest {
    private val password = SearchCandidate(
        SearchResult("settings-password", SearchResultKind.Settings, "Settings", "Change your password"),
        keywords = listOf("password", "pw", "passphrase"),
    )
    private val twoFactor = SearchCandidate(
        SearchResult("settings-2fa", SearchResultKind.Settings, "Settings", "Change 2FA settings"),
        keywords = listOf("2fa", "mfa", "totp"),
    )
    private val signIn = SearchCandidate(SearchResult("activity-7", SearchResultKind.Activity, "Activity", "Signed in · Linux · Firefox"))
    private val all = listOf(password, twoFactor, signIn)

    @Test
    fun descriptionMatchesIgnoreCase() {
        assertEquals(listOf("settings-2fa"), searchCandidates("2FA SETT", all).map { it.id })
        assertNull(searchCandidates("firefox", all).single().matchedKeyword)
    }

    @Test
    fun everyResultShowsTheQuery() {
        // Each result has the query in its title, its description or the keyword it shows.
        listOf("i", "in", "pw", "mfa", "set", "act").forEach { query ->
            searchCandidates(query, all).forEach { result ->
                val shown = listOfNotNull(result.title, result.description, result.matchedKeyword).joinToString(" ").lowercase()
                assertTrue("$query -> ${result.id}", query in shown)
            }
        }
    }

    @Test
    fun keywordMatchIsShownOnTheCard() {
        val result = searchCandidates("mf", all).single()
        assertEquals("settings-2fa", result.id)
        assertEquals("mfa", result.matchedKeyword)
    }

    @Test
    fun keywordsNeedTwoCharactersAndMatchTheirStart() {
        // "p" alone is everywhere in the keywords; it only finds what shows a "p".
        assertEquals(listOf("settings-password"), searchCandidates("p", all).map { it.id })
        assertTrue(searchCandidates("fa", listOf(password)).isEmpty())
    }

    @Test
    fun titleMatchesNeedThreeCharacters() {
        assertTrue(searchCandidates("ac", listOf(signIn)).isEmpty())
        assertEquals(listOf("activity-7"), searchCandidates("act", listOf(signIn)).map { it.id })
    }

    @Test
    fun blankQueryFindsNothingAndResultsAreCapped() {
        assertTrue(searchCandidates("  ", all).isEmpty())
        assertEquals(2, searchCandidates("e", all, limit = 2).size)
    }
}
