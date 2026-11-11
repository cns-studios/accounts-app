package com.cns.accounts.data.session

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionBookTest {
    private val ann = StoredSession(1, "ann", "r-ann")
    private val bob = StoredSession(2, "bob", "r-bob")

    @Test
    fun signingInAddsTheAccountAndActivatesIt() {
        val book = SessionBook().signedIn(ann).signedIn(bob)
        assertEquals(listOf(1L, 2L), book.sessions.map { it.userId })
        assertEquals(2L, book.activeId)
        assertEquals(listOf(false, true), book.accounts().map { it.isActive })
    }

    @Test
    fun signingInAgainReplacesTheSessionInPlace() {
        val book = SessionBook().signedIn(ann).signedIn(bob).signedIn(ann.copy(refreshToken = "r-ann-2"))
        assertEquals(listOf(1L, 2L), book.sessions.map { it.userId })
        assertEquals("r-ann-2", book.active?.refreshToken)
        assertEquals(1L, book.activeId)
    }

    @Test
    fun aRotatedTokenIsStoredForItsOwnAccountWhicheverIsActive() {
        val book = SessionBook().signedIn(ann).signedIn(bob).withRefreshToken(1, "r-ann-2")
        assertEquals("r-ann-2", book.sessions.first { it.userId == 1L }.refreshToken)
        assertEquals("r-bob", book.sessions.first { it.userId == 2L }.refreshToken)
        assertEquals(2L, book.activeId)
        // A token for an account that is not here is not stored anywhere.
        assertEquals(book, book.withRefreshToken(9, "x"))
    }

    @Test
    fun switchingOnlyToAnAccountThatIsSignedIn() {
        val book = SessionBook().signedIn(ann).signedIn(bob)
        assertEquals(1L, book.activate(1).activeId)
        assertEquals(2L, book.activate(9).activeId)
    }

    @Test
    fun signingOutTheActiveAccountHandsOverToAnother() {
        val book = SessionBook().signedIn(ann).signedIn(bob)
        val after = book.without(2)
        assertEquals(listOf(1L), after.sessions.map { it.userId })
        assertEquals(1L, after.activeId)
        // An account that is not the active one leaves the active one alone.
        assertEquals(2L, book.without(1).activeId)
        // Nobody left: nobody active.
        val none = after.without(1)
        assertNull(none.activeId)
        assertNull(none.active)
    }

    @Test
    fun theBookSurvivesTheDisk() {
        val book = SessionBook().signedIn(ann).signedIn(bob).activate(1)
        assertEquals(book, SessionBook.fromJson(book.toJson()))
        assertEquals(SessionBook(), SessionBook.fromJson("not json"))
        // An active id that is not in the list falls back to the first account.
        val odd = """{"active": 9, "sessions": [{"user_id": 3, "username": "cy", "refresh": "r"}]}"""
        assertEquals(3L, SessionBook.fromJson(odd).activeId)
    }

    @Test
    fun aSessionFromASingleAccountBuildBecomesTheRealAccountOnItsFirstToken() {
        val migrated = SessionBook.migrated("old-refresh")
        assertEquals(SessionBook.UnknownUser, migrated.activeId)
        val known = migrated.identified(SessionBook.UnknownUser, 7, "dee").withRefreshToken(7, "new-refresh")
        assertEquals(listOf(StoredSession(7, "dee", "new-refresh")), known.sessions)
        assertEquals(7L, known.activeId)
        // Signing in as the same person replaces the unknown session instead of doubling it.
        assertEquals(1, migrated.signedIn(StoredSession(7, "dee", "r")).sessions.size)
    }

    @Test
    fun readsWhoAnAccessTokenWasIssuedTo() {
        fun jwt(payload: String) = "h." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray()) + ".s"
        assertEquals(AccessTokenClaims(5, "eve"), AccessTokenClaims.parse(jwt("""{"user_id":5,"username":"eve","typ":"access"}""")))
        assertNull(AccessTokenClaims.parse(jwt("""{"username":"eve"}""")))
        assertNull(AccessTokenClaims.parse(jwt("""{"user_id":0}""")))
        assertNull(AccessTokenClaims.parse("garbage"))
    }
}
