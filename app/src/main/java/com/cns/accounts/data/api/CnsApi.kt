package com.cns.accounts.data.api

import com.cns.accounts.data.session.SessionManager
import org.json.JSONObject

/**
 * The auth-v2 endpoints the app uses. Sign-in goes through the `/api/auth/mobile/...` variants,
 * which return the refresh token in the body instead of a cookie; everything else is the
 * bearer-authenticated `/api/me/...` self-service surface the web dashboard uses too.
 */
class CnsApi(
    private val transport: HttpTransport,
    private val session: SessionManager,
) {
    // --- sign-in (no bearer) ---

    suspend fun login(username: String, password: String): LoginResult {
        val body = JSONObject().put("username", username).put("password", password)
        return LoginResult.fromJson(call("POST", "/api/auth/mobile/login", body))
    }

    suspend fun loginTwoFactor(loginToken: String, code: String): LoginResult {
        val body = JSONObject().put("login_token", loginToken).put("code", code)
        return LoginResult.fromJson(call("POST", "/api/auth/mobile/login/2fa", body))
    }

    // --- account ---

    suspend fun me(): AccountUser = AccountUser.fromJson(authed("GET", "/api/me"))

    suspend fun activity(cursor: Long? = null): ActivityPage =
        ActivityPage.fromJson(authed("GET", "/api/me/activity" + (cursor?.let { "?cursor=$it" } ?: "")))

    suspend fun connectedApps(): List<ConnectedApp> = ConnectedApp.listFromJson(authed("GET", "/api/me/connected-apps"))

    suspend fun disconnectApp(clientId: String) {
        authed("POST", "/api/me/connected-apps/${encodePath(clientId)}/disconnect")
    }

    // [approvalId]: the approved request, when Require Security App made the first try wait for
    // one (ApiException "approval_required", see data/approvals).

    /** Returns the fresh token pair: auth-v2 revokes the current session when the username changes. */
    suspend fun changeUsername(password: String, newUsername: String, approvalId: String? = null): TokenPair {
        val body = JSONObject().put("password", password).put("new_username", newUsername).putOpt("approval_id", approvalId)
        val json = authed("POST", "/api/me/username", body)
        return TokenPair.fromJson(json.optJSONObject("tokens") ?: JSONObject())
    }

    suspend fun changePassword(currentPassword: String, newPassword: String, approvalId: String? = null) {
        val body = JSONObject().put("current_password", currentPassword).put("new_password", newPassword).putOpt("approval_id", approvalId)
        authed("POST", "/api/me/password", body)
    }

    suspend fun deleteAccount(password: String, code: String, approvalId: String? = null) {
        authed("POST", "/api/me/delete", JSONObject().put("password", password).put("code", code).putOpt("approval_id", approvalId))
    }

    // --- 2FA ---

    suspend fun setupTwoFactor(): MfaSetup {
        val json = authed("POST", "/api/me/mfa/setup")
        return MfaSetup(secret = json.optString("secret"), otpauthUri = json.optString("otpauth_uri"))
    }

    /** Returns the server's time of the confirmation (falls back to the device clock). */
    suspend fun confirmTwoFactor(code: String): Long {
        val response = authedResponse("POST", "/api/me/mfa/confirm", RequestBody.Json(JSONObject().put("code", code)))
        return response.serverTimeMillis ?: System.currentTimeMillis()
    }

    suspend fun disableTwoFactor(password: String, code: String) {
        authed("POST", "/api/me/mfa/disable", JSONObject().put("password", password).put("code", code))
    }

    // --- push ---

    /** Binds this session to its FCM token; [accountEvents] false keeps only approval pushes. */
    suspend fun registerPush(token: String, accountEvents: Boolean) {
        authed("PUT", "/api/me/push", JSONObject().put("token", token).put("platform", "android").put("account_events", accountEvents))
    }

    // --- Require Security App (approvals) ---

    /**
     * Follows an approval this client started. [wait] long-polls (the server holds the request up
     * to 25s while it is pending). An approved sign-in is completed in this response, once.
     */
    suspend fun pollApproval(approval: ApprovalChallenge, wait: Boolean): ApprovalPoll {
        val body = JSONObject().put("poll_token", approval.pollToken).put("wait", wait)
        val response = transport.execute(
            "POST", "/api/auth/approvals/${encodePath(approval.id)}/poll", RequestBody.Json(body),
            timeoutMillis = if (wait) LongPollTimeoutMillis else HttpTransport.TimeoutMillis,
        )
        return when {
            response.status == 202 -> ApprovalPoll.Pending
            response.isSuccessful -> {
                val json = response.json()
                if (json.has("access_token") || json.has("code")) ApprovalPoll.Completed(json) else ApprovalPoll.Approved
            }
            response.status == 403 -> ApprovalPoll.Denied
            response.status == 404 || response.status == 410 -> ApprovalPoll.Gone
            else -> throw ApiException.from(response)
        }
    }

    /** The approver's signed decision. No session: the device signature is the authentication. */
    suspend fun decideApproval(id: String, approve: Boolean, signature: String) {
        val body = JSONObject().put("decision", if (approve) "approve" else "deny").put("signature", signature)
        call("POST", "/api/auth/approvals/${encodePath(id)}/decision", body)
    }

    suspend fun pendingApprovals(): List<PendingApproval> = PendingApproval.listFromJson(authed("GET", "/api/me/approvals"))

    suspend fun approver(): ApproverInfo = ApproverInfo.fromJson(authed("GET", "/api/me/approver"))

    /** A single-use enrollment challenge (base64) for this session. */
    suspend fun approverChallenge(): String = authed("POST", "/api/me/approver/challenge").optString("challenge")

    suspend fun enableApprover(
        publicKey: String,
        attestation: List<String>,
        name: String,
        password: String,
        code: String,
        signature: String,
    ): ApproverInfo {
        val body = JSONObject()
            .put("public_key", publicKey)
            .put("attestation", org.json.JSONArray(attestation))
            .put("name", name)
            .put("password", password)
            .put("code", code)
            .put("signature", signature)
        return ApproverInfo.fromJson(authed("POST", "/api/me/approver", body))
    }

    suspend fun disableApprover(approvalId: String? = null) {
        authed("DELETE", "/api/me/approver", JSONObject().putOpt("approval_id", approvalId))
    }

    // --- signing this app in to another CNS app (Sendly) ---

    /**
     * Asks auth for an authorization code for [clientId] on this session (no browser: the
     * session is the sign-in). With Require Security App on, the answer is an approval first.
     */
    suspend fun authorizeClient(clientId: String, redirectUri: String, codeChallenge: String, state: String): ClientAuthorization {
        val body = JSONObject()
            .put("response_type", "code")
            .put("client_id", clientId)
            .put("redirect_uri", redirectUri)
            .put("code_challenge", codeChallenge)
            .put("code_challenge_method", "S256")
            .put("scope", "openid profile")
            .put("state", state)
        return ClientAuthorization.fromJson(authed("POST", "/api/auth/authorize", body))
    }

    suspend fun exchangeCode(code: String, codeVerifier: String, clientId: String, redirectUri: String, state: String): TokenPair {
        val body = JSONObject()
            .put("code", code)
            .put("code_verifier", codeVerifier)
            .put("client_id", clientId)
            .put("redirect_uri", redirectUri)
            .put("state", state)
        return TokenPair.fromJson(call("POST", "/api/auth/token", body))
    }

    suspend fun refreshClientToken(refreshToken: String, clientId: String): TokenPair =
        TokenPair.fromJson(call("POST", "/api/auth/token/refresh", JSONObject().put("refresh_token", refreshToken).put("client_id", clientId)))

    // --- avatar ---

    /** Returns the new avatar URL. */
    suspend fun uploadAvatar(bytes: ByteArray, contentType: String): String {
        val body = RequestBody.MultipartFile("avatar", "avatar.${contentType.substringAfter('/')}", contentType, bytes)
        return authed("POST", "/api/me/avatar", body).optString("avatar")
    }

    suspend fun deleteAvatar() {
        authed("DELETE", "/api/me/avatar")
    }

    // --- plumbing ---

    private suspend fun call(method: String, path: String, json: JSONObject? = null): JSONObject {
        val response = transport.execute(method, path, json?.let(RequestBody::Json))
        if (!response.isSuccessful) throw ApiException.from(response)
        return response.json()
    }

    private suspend fun authed(method: String, path: String, json: JSONObject) =
        authed(method, path, RequestBody.Json(json))

    private suspend fun authed(method: String, path: String, body: RequestBody? = null): JSONObject =
        authedResponse(method, path, body).json()

    /** Bearer call with one transparent refresh-and-retry when the access token is rejected. */
    private suspend fun authedResponse(method: String, path: String, body: RequestBody? = null): HttpResponse {
        var token = session.accessToken()
        var response = transport.execute(method, path, body, bearerToken = token)
        if (response.status == 401) {
            token = session.refresh(staleToken = token)
            response = transport.execute(method, path, body, bearerToken = token)
        }
        if (!response.isSuccessful) throw ApiException.from(response)
        return response
    }

    private companion object {
        // Above the server's 25s hold, so a long poll ends on the server's side.
        const val LongPollTimeoutMillis = 35_000
    }

    private fun encodePath(segment: String): String = java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
}
