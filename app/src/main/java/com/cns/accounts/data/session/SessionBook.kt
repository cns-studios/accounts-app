package com.cns.accounts.data.session

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** One account signed in on this device: who it is and the refresh token that keeps it signed in. */
data class StoredSession(val userId: Long, val username: String, val refreshToken: String)

/** An account as the screens list it (no token). */
data class SignedInAccount(val userId: Long, val username: String, val isActive: Boolean)

/**
 * Every account signed in on this device and which one the app is showing. Pure data: the
 * [SessionManager] keeps it in the Keystore-encrypted store and swaps the whole book on each change.
 * The first account in the list is the one to fall back to when the active one signs out.
 */
data class SessionBook(
    val sessions: List<StoredSession> = emptyList(),
    val activeId: Long? = sessions.firstOrNull()?.userId,
) {
    val active: StoredSession? get() = sessions.firstOrNull { it.userId == activeId }

    /** Adds the session, or replaces the one of the same account, and makes it the active one. */
    fun signedIn(session: StoredSession): SessionBook {
        // A session stored before the account was known (see [UnknownUser]) is the same login.
        val index = sessions.indexOfFirst { it.userId == session.userId || (it.userId == UnknownUser && sessions.size == 1) }
        val list = if (index >= 0) sessions.toMutableList().also { it[index] = session } else sessions + session
        return SessionBook(list, session.userId)
    }

    /** A rotated refresh token of [userId]; a token of an unknown account is not stored. */
    fun withRefreshToken(userId: Long, refreshToken: String): SessionBook =
        copy(sessions = sessions.map { if (it.userId == userId) it.copy(refreshToken = refreshToken) else it })

    /** The account is known now (a session migrated from an older build had no id). */
    fun identified(from: Long, userId: Long, username: String): SessionBook {
        if (from == userId || sessions.none { it.userId == from } || sessions.any { it.userId == userId }) return this
        return SessionBook(
            sessions.map { if (it.userId == from) it.copy(userId = userId, username = username) else it },
            if (activeId == from) userId else activeId,
        )
    }

    fun activate(userId: Long): SessionBook = if (sessions.any { it.userId == userId }) copy(activeId = userId) else this

    /** Drops the account; when it was the active one, the first of the others takes over. */
    fun without(userId: Long): SessionBook {
        val rest = sessions.filterNot { it.userId == userId }
        return SessionBook(rest, if (activeId == userId || rest.none { it.userId == activeId }) rest.firstOrNull()?.userId else activeId)
    }

    fun accounts(): List<SignedInAccount> = sessions.map { SignedInAccount(it.userId, it.username, it.userId == activeId) }

    fun toJson(): String = JSONObject()
        .put("active", activeId ?: JSONObject.NULL)
        .put(
            "sessions",
            JSONArray().apply {
                sessions.forEach { put(JSONObject().put("user_id", it.userId).put("username", it.username).put("refresh", it.refreshToken)) }
            },
        )
        .toString()

    companion object {
        /** The id of a session migrated from the single-account builds, until the first token shows whose it is. */
        const val UnknownUser = 0L

        fun fromJson(raw: String): SessionBook = runCatching {
            val json = JSONObject(raw)
            val array = json.getJSONArray("sessions")
            val sessions = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                StoredSession(item.getLong("user_id"), item.optString("username"), item.getString("refresh"))
            }.distinctBy { it.userId }
            val active = if (json.isNull("active")) null else json.getLong("active")
            SessionBook(sessions, active?.takeIf { id -> sessions.any { it.userId == id } } ?: sessions.firstOrNull()?.userId)
        }.getOrDefault(SessionBook())

        /** The one session older builds kept, before the accounts were told apart. */
        fun migrated(refreshToken: String) = SessionBook(listOf(StoredSession(UnknownUser, "", refreshToken)))
    }
}

/** Who an access token (a JWT) was issued to, read from its payload; the signature is the server's business. */
data class AccessTokenClaims(val userId: Long, val username: String) {
    companion object {
        fun parse(accessToken: String): AccessTokenClaims? = runCatching {
            val payload = accessToken.split('.')[1]
            val json = JSONObject(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8))
            val id = json.getLong("user_id")
            if (id <= 0) null else AccessTokenClaims(id, json.optString("username"))
        }.getOrNull()
    }
}
