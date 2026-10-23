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

    /** Returns the fresh token pair: auth-v2 revokes the current session when the username changes. */
    suspend fun changeUsername(password: String, newUsername: String): TokenPair {
        val body = JSONObject().put("password", password).put("new_username", newUsername)
        val json = authed("POST", "/api/me/username", body)
        return TokenPair.fromJson(json.optJSONObject("tokens") ?: JSONObject())
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        authed("POST", "/api/me/password", JSONObject().put("current_password", currentPassword).put("new_password", newPassword))
    }

    suspend fun deleteAccount(password: String, code: String) {
        authed("POST", "/api/me/delete", JSONObject().put("password", password).put("code", code))
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

    private fun encodePath(segment: String): String = java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
}
