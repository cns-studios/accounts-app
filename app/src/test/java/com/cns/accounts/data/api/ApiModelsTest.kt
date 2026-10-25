package com.cns.accounts.data.api

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ApiModelsTest {
    // --- account ---

    @Test
    fun accountUserSurvivesTheCacheRoundTrip() {
        val user = AccountUser(
            id = 7,
            username = "tester",
            avatarUrl = "https://example.com/a.png",
            createdAt = Instant.parse("2026-01-02T03:04:05Z"),
            lastLoginAt = null,
            totpEnabled = true,
            totpEnabledAt = Instant.parse("2026-02-03T04:05:06Z"),
            requireAppApproval = true,
            recoveryPhraseEnabled = true,
            recoveryPhraseCreatedAt = Instant.parse("2026-10-25T10:00:00Z"),
        )
        assertEquals(user, AccountUser.fromJson(user.toJson()))
    }

    @Test
    fun accountUserDefaultsWhenFieldsAreMissing() {
        val user = AccountUser.fromJson(JSONObject().put("id", 3).put("username", "x"))
        assertFalse(user.totpEnabled)
        assertFalse(user.requireAppApproval)
        assertFalse(user.recoveryPhraseEnabled)
        assertNull(user.recoveryPhraseCreatedAt)
    }

    // --- sign-in ---

    @Test
    fun loginResultSignedIn() {
        val result = LoginResult.fromJson(JSONObject().put("access_token", "a").put("refresh_token", "r").put("expires_in", 600).put("user", JSONObject().put("id", 1).put("username", "u")))
        result as LoginResult.SignedIn
        assertEquals(TokenPair("a", "r", 600), result.tokens)
        assertEquals("u", result.user?.username)
    }

    @Test
    fun loginResultTwoFactor() =
        assertEquals(LoginResult.TwoFactorRequired("tok"), LoginResult.fromJson(JSONObject().put("requires_2fa", true).put("login_token", "tok")))

    @Test
    fun loginResultApprovalWithTotpFallback() {
        val json = JSONObject()
            .put("requires_approval", true)
            .put("login_token", "fallback")
            .put("approval", JSONObject().put("approval_id", "id1").put("poll_token", "p").put("action", "login").put("nonce", "n").put("expires_at_unix", 99L))
        val result = LoginResult.fromJson(json) as LoginResult.ApprovalRequired
        assertEquals(ApprovalChallenge("id1", "p", "login", "n", 99L), result.approval)
        assertEquals("fallback", result.loginToken)
    }

    @Test
    fun tokensAreRequiredForASession() {
        assertThrows(ApiException::class.java) { TokenPair.fromJson(JSONObject().put("access_token", "a")) }
        assertThrows(ApiException::class.java) { LoginResult.fromJson(JSONObject()) }
    }

    @Test
    fun clientAuthorizationCodeOrApproval() {
        assertEquals(ClientAuthorization.Code("c"), ClientAuthorization.fromJson(JSONObject().put("code", "c")))
        val approval = ClientAuthorization.fromJson(JSONObject().put("requires_approval", true).put("approval", JSONObject().put("approval_id", "x")))
        assertTrue(approval is ClientAuthorization.ApprovalRequired)
        assertThrows(ApiException::class.java) { ClientAuthorization.fromJson(JSONObject()) }
    }

    // --- errors ---

    @Test
    fun apiErrorEnvelope() {
        val error = ApiException.from(HttpResponse(400, """{"error":{"code":"invalid_code","message":"Wrong code"}}"""))
        assertEquals(400, error.status)
        assertEquals("invalid_code", error.code)
        assertEquals("Wrong code", error.message)
    }

    @Test
    fun apiErrorFallbacksAndApproval() {
        assertEquals("unauthorized", ApiException.from(HttpResponse(401, "")).code)
        assertEquals("request_failed", ApiException.from(HttpResponse(502, "<html>")).code)
        val gated = ApiException.from(HttpResponse(403, """{"error":{"code":"approval_required","message":"m"},"approval":{"approval_id":"a1","action":"password_change"}}"""))
        assertEquals("a1", gated.approval?.id)
        assertEquals("password_change", gated.approval?.action)
    }

    // --- approvals ---

    @Test
    fun pendingApprovalsWithContext() {
        val json = JSONObject().put(
            "approvals",
            JSONArray()
                .put(
                    JSONObject().put("id", "a1").put("action", "authorize").put("nonce", "n").put("expires_at_unix", 50L)
                        .put("created_at", "2026-10-25T10:00:00Z")
                        .put("context", JSONObject().put("ip", "1.2.3.4").put("device", "Linux").put("client_name", "Sendly")),
                )
                .put("not an object"),
        )
        val list = PendingApproval.listFromJson(json)
        assertEquals(1, list.size)
        val first = list.single()
        assertEquals("Sendly", first.clientName)
        assertEquals("1.2.3.4", first.ip)
        assertNull(first.newUsername)
        assertEquals(Instant.parse("2026-10-25T10:00:00Z"), first.createdAt)
        assertEquals(emptyList<PendingApproval>(), PendingApproval.listFromJson(JSONObject()))
    }

    @Test
    fun approverInfo() {
        assertEquals(ApproverInfo(true, "d1", "Pixel 9"), ApproverInfo.fromJson(JSONObject().put("enabled", true).put("device", JSONObject().put("id", "d1").put("name", "Pixel 9"))))
        assertEquals(ApproverInfo(false, null, null), ApproverInfo.fromJson(JSONObject().put("enabled", false)))
    }

    // --- activity ---

    @Test
    fun activityPageReadsTheTrailingCursor() {
        val json = JSONObject().put(
            "activity",
            JSONArray()
                .put(JSONObject().put("id", 5).put("event_type", "login_success").put("device", "Android").put("created_at", "2026-10-25T10:00:00Z"))
                .put(JSONObject().put("next_cursor", 5)),
        )
        val page = ActivityPage.fromJson(json)
        assertEquals(1, page.entries.size)
        assertEquals(5L, page.nextCursor)
        assertEquals("login_success", page.entries.single().eventType)
    }

    @Test
    fun activityPageWithoutMorePages() {
        val page = ActivityPage.fromJson(JSONObject().put("activity", JSONArray().put(JSONObject().put("id", 1).put("event_type", "logout"))))
        assertNull(page.nextCursor)
        assertEquals(ActivityPage(emptyList(), null), ActivityPage.fromJson(JSONObject()))
    }

    @Test
    fun activityDataAsStringOrObjectKeepsOnlyScalars() {
        val asString = JSONObject().put("id", 1).put("event_type", "approval").put("event_data", """{"action":"login","status":"denied","n":3}""")
        val asObject = JSONObject().put("id", 2).put("event_type", "recovery_phrase_created").put("event_data", JSONObject().put("replaced", true).put("nested", JSONObject()))
        val entries = ActivityPage.fromJson(JSONObject().put("activity", JSONArray().put(asString).put(asObject))).entries
        assertEquals(mapOf("action" to "login", "status" to "denied"), entries[0].data)
        assertEquals(mapOf("replaced" to "true"), entries[1].data)
    }

    @Test
    fun activityDataThatIsNotJsonIsEmpty() {
        val entry = JSONObject().put("id", 1).put("event_type", "x").put("event_data", "{broken")
        assertEquals(emptyMap<String, String>(), ActivityPage.fromJson(JSONObject().put("activity", JSONArray().put(entry))).entries.single().data)
    }

    // --- recovery phrase ---

    private fun draftJson(words: Int = 12, positions: List<Int> = listOf(2, 7, 11), token: String = "setup-token-xyz") = JSONObject()
        .put("setup_token", token)
        .put("words", JSONArray(List(words) { "secret$it" }))
        .put("confirm_positions", JSONArray(positions))

    @Test
    fun recoveryPhraseDraft() {
        val draft = RecoveryPhraseDraft.fromJson(draftJson())
        assertEquals("setup-token-xyz", draft.setupToken)
        assertEquals(12, draft.words.size)
        assertEquals(listOf(2, 7, 11), draft.confirmPositions)
    }

    @Test
    fun recoveryPhraseDraftMustBeComplete() {
        assertThrows(ApiException::class.java) { RecoveryPhraseDraft.fromJson(draftJson(words = 11)) }
        assertThrows(ApiException::class.java) { RecoveryPhraseDraft.fromJson(draftJson(positions = emptyList())) }
        assertThrows(ApiException::class.java) { RecoveryPhraseDraft.fromJson(draftJson(token = "")) }
    }

    @Test
    fun recoveryPhraseDraftNeverPrintsTheWords() {
        val text = RecoveryPhraseDraft.fromJson(draftJson()).toString()
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("setup-token-xyz"))
        assertEquals("RecoveryPhraseDraft(12 words)", text)
    }
}
