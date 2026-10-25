package com.cns.accounts.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityStatusTest {
    private fun achieved(status: SecurityStatus) = status.factors.filter { it.achieved }.map { it.id }

    @Test
    fun passwordAloneIsAQuarter() {
        val status = securityStatus(twoFactorEnabled = false, securityApp = false, recoveryPhrase = false)
        assertEquals(25, status.percentComplete)
        assertEquals(listOf("password"), achieved(status))
    }

    @Test
    fun everyFactorIsFull() {
        val status = securityStatus(twoFactorEnabled = true, securityApp = true, recoveryPhrase = true)
        assertEquals(100, status.percentComplete)
        assertEquals(listOf("2fa", "password", "app", "phrase"), achieved(status))
    }

    @Test
    fun eachFactorCountsOnItsOwn() {
        assertEquals(50, securityStatus(twoFactorEnabled = true, securityApp = false).percentComplete)
        assertEquals(50, securityStatus(twoFactorEnabled = false, securityApp = false, recoveryPhrase = true).percentComplete)
        assertEquals(75, securityStatus(twoFactorEnabled = true, securityApp = false, recoveryPhrase = true).percentComplete)
    }

    @Test
    fun factorOrderIsStable() =
        assertEquals(listOf("2fa", "password", "app", "phrase"), securityStatus(false, false).factors.map { it.id })
}
