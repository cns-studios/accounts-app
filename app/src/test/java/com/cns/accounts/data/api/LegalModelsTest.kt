package com.cns.accounts.data.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalModelsTest {
    private val me = """
        {"id": 7, "username": "lena", "legal": {"blocking": true, "documents": [
          {"kind": "terms", "version": "2026-12-01", "material": true, "blocking": true, "changes": [
            {"version": "2026-12-01", "material": true, "effective_at": "2026-12-01T00:00:00Z", "in_effect": true, "summary": {"en": "Paid apps", "de": "Bezahl-Apps"}}]},
          {"kind": "privacy", "version": "2026-11-11", "material": false, "blocking": false, "changes": [
            {"version": "2026-11-11", "material": false, "effective_at": "2026-11-11T00:00:00Z", "in_effect": true, "summary": {"en": "Consent records"}}]},
          {"kind": "cookies", "version": "2026-11-11", "material": false, "blocking": false, "changes": []}
        ]}}
    """.trimIndent()

    @Test
    fun parsesTheOpenDocuments() {
        val legal = AccountUser.fromJson(JSONObject(me)).legal
        assertNotNull(legal)
        assertTrue(legal!!.blocking)
        // A document kind this version does not know is left out, not shown without a name.
        assertEquals(listOf("terms", "privacy"), legal.documents.map { it.kind })
        val terms = legal.documents[0]
        assertEquals("2026-12-01", terms.version)
        assertTrue(terms.material && terms.blocking)
        assertNull("in effect already: no upcoming date", terms.effectiveAt)
        assertEquals(listOf("Bezahl-Apps"), terms.summariesIn("de"))
        // No translation: English.
        assertEquals(listOf("Consent records"), legal.documents[1].summariesIn("de"))
    }

    @Test
    fun nothingOpenIsNull() {
        assertNull(AccountUser.fromJson(JSONObject("""{"id": 7, "username": "lena"}""")).legal)
        assertNull(LegalState.fromJson(JSONObject("""{"blocking": false, "documents": []}""")))
    }

    @Test
    fun anAnnouncedChangeKeepsItsDateThroughTheCache() {
        val json = JSONObject(
            """{"blocking": false, "documents": [{"kind": "terms", "version": "2026-12-01", "material": true, "blocking": false, "changes": [
               {"version": "2026-12-01", "material": true, "effective_at": "2026-12-01T00:00:00Z", "in_effect": false, "summary": {"en": "Paid apps"}}]}]}""",
        )
        val legal = LegalState.fromJson(json)!!
        assertFalse(legal.blocking)
        assertNotNull(legal.documents[0].effectiveAt)
        // The profile is cached on disk as JSON and read back at the next start.
        assertEquals(legal, LegalState.fromJson(legal.toJson()))
    }
}
