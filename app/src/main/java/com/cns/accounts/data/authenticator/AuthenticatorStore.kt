package com.cns.accounts.data.authenticator

import androidx.compose.runtime.Immutable
import com.cns.accounts.data.session.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * A CNS account's TOTP key, held by this app as that account's authenticator (see
 * [TwoFactorManager]). [userId] / [username] say which account it belongs to (the username
 * pre-fills the sign-in 2FA step; [userId] is null only for a key from an older build until the
 * next profile load); [enrolledAt] is the server time 2FA was enabled with it (0 if unknown),
 * which tells a key replaced from another device apart from the current one.
 */
@Immutable
data class AuthenticatorEntry(
    val secret: String,
    val userId: Long?,
    val username: String,
    val enrolledAt: Long,
    /** The account's `totp_enabled_at` (epoch ms) for this key; null for keys from older builds. */
    val keyStamp: Long? = null,
) {
    fun codeAt(timeMillis: Long): String? = Totp.decodeBase32(secret)?.let { Totp.code(it, timeMillis) }
}

/**
 * The keys of every CNS account that enabled 2FA from this device, Keystore-encrypted. One per
 * account, so signing in with another account never overwrites (and locks out) the first one.
 */
class AuthenticatorStore(private val secureStore: SecureStore) {
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<AuthenticatorEntry>> = _entries.asStateFlow()

    fun entryFor(userId: Long): AuthenticatorEntry? = _entries.value.firstOrNull { it.userId == userId }

    fun entryForUsername(username: String): AuthenticatorEntry? =
        _entries.value.firstOrNull { it.username.equals(username.trim(), ignoreCase = true) }

    /** Stores [entry], replacing the key of the same account (including its not yet assigned legacy key). */
    fun save(entry: AuthenticatorEntry) = update { entries ->
        entries.filterNot { it.userId == entry.userId || (it.userId == null && it.username.equals(entry.username, ignoreCase = true)) } + entry
    }

    fun remove(userId: Long?) = update { entries -> entries.filterNot { it.userId == userId } }

    private fun update(transform: (List<AuthenticatorEntry>) -> List<AuthenticatorEntry>) {
        val updated = transform(_entries.value)
        persist(updated)
        _entries.value = updated
    }

    private fun load(): List<AuthenticatorEntry> {
        secureStore.get(Key)?.let { raw ->
            return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).map { i ->
                    val json = array.getJSONObject(i)
                    AuthenticatorEntry(
                        secret = json.getString("secret"),
                        userId = if (json.isNull("user_id")) null else json.getLong("user_id"),
                        username = json.optString("username"),
                        enrolledAt = json.optLong("enrolled_at"),
                        keyStamp = if (json.has("key_stamp")) json.getLong("key_stamp") else null,
                    )
                }
            }.getOrDefault(emptyList())
        }
        return listOfNotNull(migrateLegacy())
    }

    /**
     * Earlier builds kept one key of any kind under [LegacyKey], with a "cns" flag for the CNS
     * key the app stored itself. A CNS key moves over (its account is filled in on the next
     * profile load); any other key is left where it is, untouched, rather than deleted.
     */
    private fun migrateLegacy(): AuthenticatorEntry? {
        val json = secureStore.get(LegacyKey)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        if (!json.optBoolean("cns")) return null
        val entry = AuthenticatorEntry(
            secret = json.optString("secret").ifBlank { return null },
            userId = null,
            username = json.optString("label").substringAfterLast(" · ", ""),
            enrolledAt = 0L,
        )
        persist(listOf(entry))
        secureStore.put(LegacyKey, null)
        return entry
    }

    private fun persist(entries: List<AuthenticatorEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("secret", entry.secret)
                    .put("user_id", entry.userId ?: JSONObject.NULL)
                    .put("username", entry.username)
                    .put("enrolled_at", entry.enrolledAt)
                    .apply { entry.keyStamp?.let { put("key_stamp", it) } },
            )
        }
        secureStore.put(Key, if (entries.isEmpty()) null else array.toString())
    }

    private companion object {
        const val Key = "cns_authenticators"
        const val LegacyKey = "authenticator"
    }
}
