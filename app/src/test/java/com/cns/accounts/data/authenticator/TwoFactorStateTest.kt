package com.cns.accounts.data.authenticator

import com.cns.accounts.data.api.AccountUser
import com.cns.accounts.data.api.ActivityEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TwoFactorStateTest {
    private val enrolledAt = Instant.parse("2026-10-23T12:00:00Z")
    private val key = AuthenticatorEntry("JBSWY3DPEHPK3PXP", userId = 1, username = "tester", enrolledAt = enrolledAt.toEpochMilli())

    private fun user(totp: Boolean) = AccountUser(1, "tester", null, null, null, totpEnabled = totp)
    private fun event(type: String, at: Instant) = ActivityEntry(at.epochSecond, type, null, null, at)

    @Test
    fun noProfileYetIsUnknown() = assertEquals(TwoFactorState.Unknown, resolveTwoFactorState(null, key, emptyList()))

    @Test
    fun twoFactorOffIsNotSetUpEvenWithAStoredKey() =
        assertEquals(TwoFactorState.NotSetUp, resolveTwoFactorState(user(totp = false), key, emptyList()))

    @Test
    fun twoFactorOnWithoutKeyHereIsElsewhere() =
        assertEquals(TwoFactorState.Elsewhere, resolveTwoFactorState(user(totp = true), null, emptyList()))

    @Test
    fun ownEnrollmentEventKeepsTheKeyActive() {
        val own = event("2fa_enabled", enrolledAt.plusSeconds(1))
        assertEquals(TwoFactorState.Active(key.secret), resolveTwoFactorState(user(totp = true), key, listOf(own)))
    }

    @Test
    fun laterEnrollmentElsewhereMakesTheKeyStale() {
        val events = listOf(
            event("2fa_enabled", enrolledAt.plusSeconds(600)),
            event("2fa_disabled", enrolledAt.plusSeconds(599)),
            event("2fa_enabled", enrolledAt),
        )
        assertEquals(TwoFactorState.Elsewhere, resolveTwoFactorState(user(totp = true), key, events))
    }

    @Test
    fun otherLaterEventsDontMatter() {
        val events = listOf(event("login_success", enrolledAt.plusSeconds(600)), event("password_changed", enrolledAt.plusSeconds(900)))
        assertEquals(TwoFactorState.Active(key.secret), resolveTwoFactorState(user(totp = true), key, events))
    }

    @Test
    fun keyWithoutEnrollmentTimeIsTrusted() {
        val legacy = key.copy(enrolledAt = 0L)
        val events = listOf(event("2fa_enabled", enrolledAt.plusSeconds(600)))
        assertEquals(TwoFactorState.Active(legacy.secret), resolveTwoFactorState(user(totp = true), legacy, events))
    }
}
