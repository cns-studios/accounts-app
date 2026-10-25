package com.cns.accounts.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun parsesTagsWithAndWithoutPrefix() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("v1.2.3"))
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("1.2.3"))
        assertEquals(AppVersion(2, 0, 0), AppVersion.parse(" v2 "))
        assertEquals(AppVersion(1, 4, 0), AppVersion.parse("1.4"))
    }

    @Test
    fun rejectsAnythingElse() {
        listOf(null, "", "v", "1.2.3.4", "1.2.3-beta", "1.100.0", "1.2.100", "latest", "v1..2").forEach {
            assertNull("\"$it\" should not parse", AppVersion.parse(it))
        }
    }

    @Test
    fun comparesNumericallyNotAsText() {
        assertTrue(AppVersion.parse("1.10.0")!! > AppVersion.parse("1.9.9")!!)
        assertTrue(AppVersion.parse("2.0.0")!! > AppVersion.parse("1.99.99")!!)
        assertTrue(AppVersion.parse("1.0.1")!! > AppVersion.parse("1.0")!!)
        assertEquals(0, AppVersion.parse("1.0")!!.compareTo(AppVersion.parse("v1.0.0")!!))
    }

    @Test
    fun versionCodeMatchesTheGradleFormulaAndKeepsOrder() {
        assertEquals(10203, AppVersion(1, 2, 3).code)
        assertEquals(20000, AppVersion(2, 0, 0).code)
        val ordered = listOf("0.9.9", "1.0.0", "1.0.1", "1.1.0", "1.99.99", "2.0.0").map { AppVersion.parse(it)!! }
        assertEquals(ordered.map { it.code }.sorted(), ordered.map { it.code })
    }

    @Test
    fun printsAsDottedTriple() = assertEquals("1.4.0", AppVersion.parse("v1.4").toString())
}
