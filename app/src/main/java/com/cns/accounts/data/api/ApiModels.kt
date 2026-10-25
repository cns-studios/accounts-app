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
    /** When the account's current 2FA key was confirmed; identifies that key. */
    val totpEnabledAt: Instant? = null,
    /** Require Security App: sign-ins and sensitive changes wait for the approver device. */
    val requireAppApproval: Boolean = false,
    /** A recovery phrase is set up (it can reset the password, 2FA and Require Security App). */
    val recoveryPhraseEnabled: Boolean = false,
    val recoveryPhraseCreatedAt: Instant? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("username", username)
        .put("profile", JSONObject().put("avatar", avatarUrl ?: JSONObject.NULL))
        .put("created_at", createdAt?.toString() ?: JSONObject.NULL)
        .put("last_login_at", lastLoginAt?.toString() ?: JSONObject.NULL)
        .put("totp_enabled", totpEnabled)
        .put("totp_enabled_at", totpEnabledAt?.toString() ?: JSONObject.NULL)
        .put("require_app_approval", requireAppApproval)
        .put("recovery_phrase_enabled", recoveryPhraseEnabled)
        .put("recovery_phrase_created_at", recoveryPhraseCreatedAt?.toString() ?: JSONObject.NULL)

    companion object {
        fun fromJson(json: JSONObject) = AccountUser(
            id = json.optLong("id"),
            username = json.optString("username"),
            avatarUrl = json.optJSONObject("profile")?.optStringOrNull("avatar"),
            createdAt = json.optInstant("created_at"),
            lastLoginAt = json.optInstant("last_login_at"),
            totpEnabled = json.optBoolean("totp_enabled"),
            totpEnabledAt = json.optInstant("totp_enabled_at"),
            requireAppApproval = json.optBoolean("require_app_approval"),
            recoveryPhraseEnabled = json.optBoolean("recovery_phrase_enabled"),
            recoveryPhraseCreatedAt = json.optInstant("recovery_phrase_created_at"),
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

    /**
     * Require Security App: the sign-in waits for the approver device. [loginToken] is the TOTP
     * fallback for a lost phone; using it turns the requirement off.
     */
    data class ApprovalRequired(val approval: ApprovalChallenge, val loginToken: String?) : LoginResult

    companion object {
        fun fromJson(json: JSONObject): LoginResult {
            val approval = json.optJSONObject("approval")
            return when {
                json.optBoolean("requires_approval") && approval != null ->
                    ApprovalRequired(ApprovalChallenge.fromJson(approval), json.optStringOrNull("login_token"))
                json.optBoolean("requires_2fa") -> TwoFactorRequired(json.optString("login_token"))
                else -> SignedIn(TokenPair.fromJson(json), json.optJSONObject("user")?.let(AccountUser::fromJson))
            }
        }
    }
}

/**
 * An action waiting for the approver device (auth-v2 Require Security App). [pollToken] lets the
 * requester follow it; [nonce] and [expiresAtUnix] complete the message the approver signs.
 */
data class ApprovalChallenge(
    val id: String,
    val pollToken: String,
    val action: String,
    val nonce: String,
    val expiresAtUnix: Long,
) {
    companion object {
        fun fromJson(json: JSONObject) = ApprovalChallenge(
            id = json.optString("approval_id"),
            pollToken = json.optString("poll_token"),
            action = json.optString("action"),
            nonce = json.optString("nonce"),
            expiresAtUnix = json.optLong("expires_at_unix"),
        )
    }
}

/** `POST /api/auth/authorize` on a session: a code, or (Require Security App) an approval first. */
sealed interface ClientAuthorization {
    data class Code(val code: String) : ClientAuthorization
    data class ApprovalRequired(val approval: ApprovalChallenge) : ClientAuthorization

    companion object {
        fun fromJson(json: JSONObject): ClientAuthorization {
            val approval = json.optJSONObject("approval")
            if (json.optBoolean("requires_approval") && approval != null) return ApprovalRequired(ApprovalChallenge.fromJson(approval))
            val code = json.optStringOrNull("code") ?: throw ApiException(200, "bad_response", "The server did not return a code")
            return Code(code)
        }
    }
}

/** What a poll of an [ApprovalChallenge] found. */
sealed interface ApprovalPoll {
    data object Pending : ApprovalPoll

    /** An account action was approved: repeat it with the approval id. */
    data object Approved : ApprovalPoll

    /** An approved sign-in (or single sign-on), completed in the poll response. */
    data class Completed(val json: JSONObject) : ApprovalPoll
    data object Denied : ApprovalPoll

    /** Expired, cancelled or already used. */
    data object Gone : ApprovalPoll
}

/**
 * A request waiting for this device's decision (`GET /api/me/approvals`). The context fields say where it
 * comes from: the requester's IP and device, the app for a single sign-on, the new username.
 */
@Immutable
data class PendingApproval(
    val id: String,
    val action: String,
    val nonce: String,
    val ip: String?,
    val device: String?,
    val clientName: String?,
    val newUsername: String?,
    val createdAt: Instant?,
    val expiresAtUnix: Long,
) {
    companion object {
        fun fromJson(json: JSONObject): PendingApproval {
            val context = json.optJSONObject("context") ?: JSONObject()
            return PendingApproval(
                id = json.optString("id"),
                action = json.optString("action"),
                nonce = json.optString("nonce"),
                ip = context.optStringOrNull("ip"),
                device = context.optStringOrNull("device"),
                clientName = context.optStringOrNull("client_name"),
                newUsername = context.optStringOrNull("new_username"),
                createdAt = json.optInstant("created_at"),
                expiresAtUnix = json.optLong("expires_at_unix"),
            )
        }

        fun listFromJson(json: JSONObject): List<PendingApproval> {
            val items = json.optJSONArray("approvals") ?: return emptyList()
            return (0 until items.length()).mapNotNull { i -> items.optJSONObject(i)?.let(::fromJson) }
        }
    }
}

/** The account's approver device (`GET /api/me/approver`); [deviceId] is null while it is off. */
data class ApproverInfo(val enabled: Boolean, val deviceId: String?, val deviceName: String? = null) {
    companion object {
        fun fromJson(json: JSONObject) = ApproverInfo(
            enabled = json.optBoolean("enabled"),
            deviceId = json.optJSONObject("device")?.optStringOrNull("id"),
            deviceName = json.optJSONObject("device")?.optStringOrNull("name"),
        )
    }
}

@Immutable
data class ActivityEntry(
    val id: Long,
    val eventType: String,
    val ipAddress: String?,
    val device: String?,
    val createdAt: Instant?,
    /** The event's string details (e.g. an approval's action and status), from `event_data`. */
    val data: Map<String, String> = emptyMap(),
)

/** auth-v2 sends `event_data` as a JSON string (older rows: an object); only string values are kept. */
private fun activityData(item: JSONObject): Map<String, String> {
    val raw = item.opt("event_data")
    val json = when (raw) {
        is JSONObject -> raw
        is String -> runCatching { JSONObject(raw) }.getOrNull()
        else -> null
    } ?: return emptyMap()
    return buildMap {
        json.keys().forEach { key ->
            when (val value = json.opt(key)) {
                is String -> put(key, value)
                is Boolean -> put(key, value.toString())
            }
        }
    }
}

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
                            data = activityData(item),
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

/** `POST /api/me/recovery-phrase/setup`: the words are shown once and never again. */
class RecoveryPhraseDraft(
    val setupToken: String,
    val words: List<String>,
    /** 1-based positions of the words the user types back to confirm. */
    val confirmPositions: List<Int>,
) {
    // Keep the words out of logs and crash reports.
    override fun toString() = "RecoveryPhraseDraft(${words.size} words)"

    companion object {
        fun fromJson(json: JSONObject): RecoveryPhraseDraft {
            val words = json.optJSONArray("words") ?: JSONArray()
            val positions = json.optJSONArray("confirm_positions") ?: JSONArray()
            return RecoveryPhraseDraft(
                setupToken = json.optString("setup_token"),
                words = List(words.length()) { words.optString(it) },
                confirmPositions = List(positions.length()) { positions.optInt(it) },
            ).also {
                if (it.setupToken.isBlank() || it.words.size != 12 || it.confirmPositions.isEmpty()) {
                    throw ApiException(200, "bad_response", "The server did not return a recovery phrase")
                }
            }
        }
    }
}

internal fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

// Go encodes time.Time as RFC 3339 with an offset that isn't always "Z" (DB rows come back in the
// server's zone), which Instant.parse rejects on older Android runtimes — OffsetDateTime takes both.
internal fun JSONObject.optInstant(name: String): Instant? =
    optStringOrNull(name)?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
