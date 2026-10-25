package com.cns.accounts.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogOutBlockTest {
    @Test
    fun `a device that is neither can log out`() {
        assertNull(LogOutBlock.of(authenticator = false, securityApp = false))
    }

    @Test
    fun `the authenticator cannot log out`() {
        assertEquals(LogOutBlock.Authenticator, LogOutBlock.of(authenticator = true, securityApp = false))
    }

    @Test
    fun `the security app cannot log out`() {
        assertEquals(LogOutBlock.SecurityApp, LogOutBlock.of(authenticator = false, securityApp = true))
    }

    @Test
    fun `a device that is both says so`() {
        assertEquals(LogOutBlock.Both, LogOutBlock.of(authenticator = true, securityApp = true))
    }
}
