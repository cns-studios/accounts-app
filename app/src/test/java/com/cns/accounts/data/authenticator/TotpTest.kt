package com.cns.accounts.data.authenticator

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TotpTest {
    // RFC 6238 appendix B, SHA1 variant: the 20-byte ASCII secret "12345678901234567890". The RFC
    // lists 8-digit codes; the app uses 6 digits, i.e. the last six of each.
    private val rfcSecret = "12345678901234567890".toByteArray()

    @Test
    fun matchesRfc6238Vectors() {
        assertEquals("287082", Totp.code(rfcSecret, 59_000L))
        assertEquals("081804", Totp.code(rfcSecret, 1_111_111_109_000L))
        assertEquals("050471", Totp.code(rfcSecret, 1_111_111_111_000L))
        assertEquals("005924", Totp.code(rfcSecret, 1_234_567_890_000L))
        assertEquals("279037", Totp.code(rfcSecret, 2_000_000_000_000L))
    }

    @Test
    fun codeIsStableWithinAPeriod() {
        assertEquals(Totp.code(rfcSecret, 30_000L), Totp.code(rfcSecret, 59_999L))
    }

    @Test
    fun decodesBase32() {
        // Base32 of "12345678901234567890".
        assertArrayEquals(rfcSecret, Totp.decodeBase32("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"))
        // Lowercase, spaces and padding, as users paste keys.
        assertArrayEquals("foobar".toByteArray(), Totp.decodeBase32("mzxw 6ytb oi======"))
    }

    @Test
    fun rejectsNonBase32() {
        assertNull(Totp.decodeBase32(""))
        assertNull(Totp.decodeBase32("not-base32!"))
        assertNull(Totp.decodeBase32("01890"))
    }
}
