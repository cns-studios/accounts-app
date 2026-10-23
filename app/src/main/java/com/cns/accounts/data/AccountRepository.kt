package com.cns.accounts.data

import android.content.Context
import com.cns.accounts.data.api.AccountUser
import com.cns.accounts.data.api.ActivityEntry
import com.cns.accounts.data.api.CnsApi
import com.cns.accounts.data.api.ConnectedApp
import com.cns.accounts.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

data class ActivityFeed(
    val entries: List<ActivityEntry> = emptyList(),
    val nextCursor: Long? = null,
    val loaded: Boolean = false,
)

/**
 * The signed-in account as the screens see it: profile, connected apps and the activity feed,
 * each a [StateFlow] that Home, History, Settings and Search share, plus the account mutations
 * (which update those flows on success so every screen reflects the change without a reload).
 * The profile is cached on disk so the app opens with a name and avatar even before (or without)
 * a network round trip.
 */
class AccountRepository(
    context: Context,
    private val api: CnsApi,
    private val session: SessionManager,
) {
    private val cache = context.getSharedPreferences("cns_account", Context.MODE_PRIVATE)

    private val _user = MutableStateFlow(loadCachedUser())
    val user: StateFlow<AccountUser?> = _user.asStateFlow()

    private val _connectedApps = MutableStateFlow<List<ConnectedApp>?>(null)
    val connectedApps: StateFlow<List<ConnectedApp>?> = _connectedApps.asStateFlow()

    private val _activity = MutableStateFlow(ActivityFeed())
    val activity: StateFlow<ActivityFeed> = _activity.asStateFlow()

    suspend fun refreshProfile(): AccountUser = api.me().also(::setUser)

    /** Seeds the profile from the sign-in response so Home has a name before its first refresh. */
    fun onSignedIn(user: AccountUser?) {
        user?.let(::setUser)
    }

    suspend fun refreshConnectedApps() {
        // Not filtered on is_revoked: auth-v2 sets it as soon as ANY of an app's sessions was
        // revoked (a logout is enough), so it hid apps that are still connected. Same list as the
        // web dashboard until auth reports it per app (see docs/api_wiring.md).
        _connectedApps.value = api.connectedApps()
    }

    suspend fun refreshActivity() {
        val page = api.activity()
        _activity.value = ActivityFeed(page.entries, page.nextCursor, loaded = true)
    }

    suspend fun loadMoreActivity() {
        val cursor = _activity.value.nextCursor ?: return
        val page = api.activity(cursor)
        _activity.update { feed ->
            val known = feed.entries.mapTo(HashSet()) { it.id }
            feed.copy(entries = feed.entries + page.entries.filterNot { it.id in known }, nextCursor = page.nextCursor)
        }
    }

    suspend fun disconnectApp(clientId: String) {
        api.disconnectApp(clientId)
        _connectedApps.update { apps -> apps?.filterNot { it.clientId == clientId } }
    }

    suspend fun changeUsername(password: String, newUsername: String) {
        // auth-v2 revokes the session the request came from and returns a replacement pair.
        session.establish(api.changeUsername(password, newUsername))
        refreshProfile()
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) =
        api.changePassword(currentPassword, newPassword)

    /** Reflects a 2FA change made through [com.cns.accounts.data.authenticator.TwoFactorManager] without a reload. */
    fun markTwoFactor(enabled: Boolean) {
        _user.value?.let { setUser(it.copy(totpEnabled = enabled)) }
    }

    suspend fun disableTwoFactor(password: String, code: String) {
        api.disableTwoFactor(password, code)
        markTwoFactor(enabled = false)
    }

    suspend fun uploadAvatar(bytes: ByteArray, contentType: String) {
        val url = api.uploadAvatar(bytes, contentType)
        _user.value?.let { setUser(it.copy(avatarUrl = url.ifBlank { null })) }
    }

    suspend fun deleteAvatar() {
        api.deleteAvatar()
        _user.value?.let { setUser(it.copy(avatarUrl = null)) }
    }

    suspend fun deleteAccount(password: String, code: String) {
        api.deleteAccount(password, code)
        session.signOutLocally()
    }

    suspend fun signOut() = session.signOut()

    /** Drops everything account-specific; called whenever the session ends. */
    fun clear() {
        cache.edit().clear().apply()
        _user.value = null
        _connectedApps.value = null
        _activity.value = ActivityFeed()
    }

    private fun setUser(user: AccountUser) {
        _user.value = user
        cache.edit().putString(UserKey, user.toJson().toString()).apply()
    }

    private fun loadCachedUser(): AccountUser? = cache.getString(UserKey, null)?.let { raw ->
        runCatching { AccountUser.fromJson(JSONObject(raw)) }.getOrNull()
    }

    private companion object {
        const val UserKey = "user"
    }
}
