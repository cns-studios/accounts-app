package com.cns.accounts.data.api

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * A legal document (terms of service or privacy policy) with changes the account has not
 * confirmed yet, as auth-v2 reports it in `/api/me` (`legal`).
 */
data class LegalDocument(
    /** "terms" or "privacy". */
    val kind: String,
    /** The version to confirm (its date, YYYY-MM-DD). */
    val version: String,
    /** A change in between needs an explicit accept. */
    val material: Boolean,
    /** A material change is in effect: the account has to accept before it goes on. */
    val blocking: Boolean,
    /** When a material change that is announced, but not in effect yet, starts to apply. */
    val effectiveAt: Instant?,
    /** What changed, per language tag, one entry per unconfirmed version. */
    val summaries: List<Map<String, String>>,
) {
    /** The summaries in [language], falling back to English. */
    fun summariesIn(language: String): List<String> =
        summaries.mapNotNull { it[language] ?: it["en"] }.filter { it.isNotBlank() }

    fun toJson(): JSONObject {
        val changes = JSONArray()
        summaries.forEachIndexed { index, summary ->
            val change = JSONObject().put("summary", JSONObject(summary))
            // Only the first change carries the date this model keeps.
            if (index == 0 && effectiveAt != null) change.put("material", true).put("in_effect", false).put("effective_at", effectiveAt.toString())
            changes.put(change)
        }
        return JSONObject().put("kind", kind).put("version", version).put("material", material).put("blocking", blocking).put("changes", changes)
    }

    companion object {
        fun fromJson(json: JSONObject): LegalDocument {
            val changes = json.optJSONArray("changes") ?: JSONArray()
            val summaries = ArrayList<Map<String, String>>()
            var effectiveAt: Instant? = null
            for (i in 0 until changes.length()) {
                val change = changes.optJSONObject(i) ?: continue
                val summary = change.optJSONObject("summary")
                summaries += summary?.keys()?.asSequence()?.associateWith { summary.optString(it) }.orEmpty()
                if (effectiveAt == null && change.optBoolean("material") && !change.optBoolean("in_effect")) {
                    effectiveAt = change.optInstant("effective_at")
                }
            }
            return LegalDocument(
                kind = json.optString("kind"),
                version = json.optString("version"),
                material = json.optBoolean("material"),
                blocking = json.optBoolean("blocking"),
                effectiveAt = effectiveAt,
                summaries = summaries,
            )
        }
    }
}

/** What the account still has to confirm; [blocking] when any document is. */
data class LegalState(val blocking: Boolean, val documents: List<LegalDocument>) {
    fun toJson(): JSONObject =
        JSONObject().put("blocking", blocking).put("documents", JSONArray(documents.map { it.toJson() }))

    companion object {
        /** Null when there is nothing to confirm. */
        fun fromJson(json: JSONObject?): LegalState? {
            val documents = json?.optJSONArray("documents") ?: return null
            val parsed = (0 until documents.length()).mapNotNull { documents.optJSONObject(it) }.map(LegalDocument::fromJson)
                .filter { it.kind == "terms" || it.kind == "privacy" }
            return if (parsed.isEmpty()) null else LegalState(json.optBoolean("blocking"), parsed)
        }
    }
}
