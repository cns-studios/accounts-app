package com.cns.accounts.data.api

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

// Response shapes of the auth-v2 endpoints the app uses (see accounts/API_REFERENCE.md), parsed
// with org.json. Only the fields the app actually shows or needs are kept.

@Immutable
data class AccountUser(
    val id: Long,
    val username: String,
    val avatarUrl: String?,
    val createdAt: Instant?,
    val lastLoginAt: Instant?,
    val totpEnabled: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("username", username)
        .put("profile", JSONObject().put("avatar", avatarUrl ?: JSONObject.NULL))
        .put("created_at", createdAt?.toString() ?: JSONObject.NULL)
        .put("last_login_at", lastLoginAt?.toString() ?: JSONObject.NULL)
        .put("totp_enabled", totpEnabled)

    companion object {
        fun fromJson(json: JSONObject) = AccountUser(
            id = json.optLong("id"),
            username = json.optString("username"),
            avatarUrl = json.optJSONObject("profile")?.optStringOrNull("avatar"),
            createdAt = json.optInstant("created_at"),
            lastLoginAt = json.optInstant("last_login_at"),
            totpEnabled = json.optBoolean("totp_enabled"),
        )
    }
}

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
) {
    companion object {
        fun fromJson(json: JSONObject): TokenPair {
            val access = json.optString("access_token")
            val refresh = json.optString("refresh_token")
            if (access.isBlank() || refresh.isBlank()) {
                throw ApiException(200, "bad_response", "The server did not return a session")
            }
            return TokenPair(access, refresh, json.optLong("expires_in", 900))
        }
    }
}

sealed interface LoginResult {
    data class SignedIn(val tokens: TokenPair, val user: AccountUser?) : LoginResult
    data class TwoFactorRequired(val loginToken: String) : LoginResult

    companion object {
        fun fromJson(json: JSONObject): LoginResult =
            if (json.optBoolean("requires_2fa")) {
                TwoFactorRequired(json.optString("login_token"))
            } else {
                SignedIn(TokenPair.fromJson(json), json.optJSONObject("user")?.let(AccountUser::fromJson))
            }
    }
}

@Immutable
data class ActivityEntry(
    val id: Long,
    val eventType: String,
    val ipAddress: String?,
    val device: String?,
    val createdAt: Instant?,
)

data class ActivityPage(val entries: List<ActivityEntry>, val nextCursor: Long?) {
    companion object {
        // auth-v2 appends a bare {"next_cursor": n} element to the list when there are more pages.
        fun fromJson(json: JSONObject): ActivityPage {
            val items = json.optJSONArray("activity") ?: JSONArray()
            var nextCursor: Long? = null
            val entries = buildList {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    if (!item.has("event_type") && item.has("next_cursor")) {
                        nextCursor = item.optLong("next_cursor").takeIf { it > 0 }
                        continue
                    }
                    add(
                        ActivityEntry(
                            id = item.optLong("id"),
                            eventType = item.optString("event_type"),
                            ipAddress = item.optStringOrNull("ip_address"),
                            device = item.optStringOrNull("device"),
                            createdAt = item.optInstant("created_at"),
                        ),
                    )
                }
            }
            return ActivityPage(entries, nextCursor)
        }
    }
}

@Immutable
data class ConnectedApp(
    val clientId: String,
    val name: String,
    val lastUsed: Instant?,
) {
    companion object {
        fun listFromJson(json: JSONObject): List<ConnectedApp> {
            val items = json.optJSONArray("connected_apps") ?: return emptyList()
            return (0 until items.length()).mapNotNull { i ->
                val item = items.optJSONObject(i) ?: return@mapNotNull null
                val clientId = item.optString("client_id")
                ConnectedApp(
                    clientId = clientId,
                    name = item.optStringOrNull("client_name") ?: clientId,
                    lastUsed = item.optInstant("last_used"),
                )
            }
        }
    }
}

data class MfaSetup(val secret: String, val otpauthUri: String)

internal fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

// Go encodes time.Time as RFC 3339 with an offset that isn't always "Z" (DB rows come back in the
// server's zone), which Instant.parse rejects on older Android runtimes — OffsetDateTime takes both.
internal fun JSONObject.optInstant(name: String): Instant? =
    optStringOrNull(name)?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
