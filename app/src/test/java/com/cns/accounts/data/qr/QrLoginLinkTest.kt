package com.cns.accounts.data.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrLoginLinkTest {
    private val site = "https://accounts.cns.ad"
    private val id = "0b6f5d0e-6b52-4b7a-9d3a-2f6b0c1e4a77"
    private val secret = "Zm9vYmFyLWJhei1xdXgtc2VjcmV0LXZhbHVl"
    private val code = QrLoginCode(id, secret)

    @Test
    fun readsTheCodeFromTheFragment() {
        assertEquals(code, QrLoginLink.parse("https://accounts.cns.ad/qr#$id.$secret", site))
        assertEquals(code, QrLoginLink.parse("  https://ACCOUNTS.cns.ad/qr/#${id.uppercase()}.$secret ", site))
    }

    @Test
    fun readsTheCodeFromTheAppLinkQuery() {
        assertEquals(code, QrLoginLink.parse("https://accounts.cns.ad/qr?c=$id.$secret", site))
        assertEquals(code, QrLoginLink.parse("https://accounts.cns.ad/qr?x=1&c=$id.$secret", site))
    }

    @Test
    fun onlyTheConfiguredSiteCounts() {
        listOf(
            "https://evil.example/qr#$id.$secret",
            "https://accounts.cns.ad.evil.example/qr#$id.$secret",
            "http://accounts.cns.ad/qr#$id.$secret",
            "https://accounts.cns.ad:8443/qr#$id.$secret",
            "https://accounts.cns.ad/login#$id.$secret",
        ).forEach { assertNull(it, QrLoginLink.parse(it, site)) }
    }

    @Test
    fun aLocalBuildTakesItsOwnSite() {
        val local = "http://10.0.2.2:3672"
        assertEquals(code, QrLoginLink.parse("http://10.0.2.2:3672/qr#$id.$secret", local))
        assertNull(QrLoginLink.parse("http://10.0.2.2:3000/qr#$id.$secret", local))
    }

    @Test
    fun anythingElseIsNotACode() {
        listOf(
            "", "not a url", "otpauth://totp/CNS:me?secret=ABC",
            "https://accounts.cns.ad/qr", "https://accounts.cns.ad/qr#",
            "https://accounts.cns.ad/qr#$id", "https://accounts.cns.ad/qr#$id.short",
            "https://accounts.cns.ad/qr#not-a-uuid.$secret", "https://accounts.cns.ad/qr#$id.$secret.extra",
        ).forEach { assertNull(it, QrLoginLink.parse(it, site)) }
    }

    @Test
    fun theSecretStaysOutOfToString() = assertEquals("QrLoginCode($id)", code.toString())
}
