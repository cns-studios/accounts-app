package com.cns.accounts.data.authenticator

import androidx.compose.runtime.Immutable
import com.cns.accounts.data.session.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * One TOTP key for the Home "account code" card: a standalone authenticator that works without
 * the CNS session. [isCnsAccount] marks a key the app stored itself while enabling CNS 2FA, so
 * flows that need a CNS 2FA code (disable 2FA, delete account, sign-in) can pre-fill it.
 */
@Immutable
data class AuthenticatorEntry(
    val label: String,
    val secret: String,
    val isCnsAccount: Boolean,
) {
    fun codeAt(timeMillis: Long): String? = Totp.decodeBase32(secret)?.let { Totp.code(it, timeMillis) }
}

class AuthenticatorStore(private val secureStore: SecureStore) {
    private val _entry = MutableStateFlow(load())
    val entry: StateFlow<AuthenticatorEntry?> = _entry.asStateFlow()

    fun save(entry: AuthenticatorEntry) {
        secureStore.put(Key, JSONObject().put("label", entry.label).put("secret", entry.secret).put("cns", entry.isCnsAccount).toString())
        _entry.value = entry
    }

    fun remove() {
        secureStore.put(Key, null)
        _entry.value = null
    }

    /** The current CNS 2FA code, if this device holds the CNS key. */
    fun currentCnsCode(): String? = _entry.value?.takeIf { it.isCnsAccount }?.codeAt(System.currentTimeMillis())

    private fun load(): AuthenticatorEntry? = secureStore.get(Key)?.let { raw ->
        runCatching {
            val json = JSONObject(raw)
            AuthenticatorEntry(json.getString("label"), json.getString("secret"), json.optBoolean("cns"))
        }.getOrNull()
    }

    private companion object {
        const val Key = "authenticator"
    }
}
