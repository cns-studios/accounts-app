package com.cns.accounts.data

import android.content.Context
import com.cns.accounts.data.api.AccountUser
import com.cns.accounts.data.api.ActivityEntry
import com.cns.accounts.data.api.CnsApi
import com.cns.accounts.data.api.ConnectedApp
import com.cns.accounts.data.session.SessionBook
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
 * The active account as the screens see it: profile, connected apps and the activity feed,
 * each a [StateFlow] that Home, History, Settings and Search share, plus the account mutations
 * (which update those flows on success so every screen reflects the change without a reload).
 * The profile of every signed-in account is cached on disk, so the app opens with a name and
 * avatar even before (or without) a network round trip, and switching accounts shows the
 * other one right away. An answer that arrives after the app switched to another account is
 * kept out of the flows: they only ever hold the active account.
 */
class AccountRepository(
    context: Context,
    private val api: CnsApi,
    private val session: SessionManager,
) {
    private val cache = context.getSharedPreferences("cns_account", Context.MODE_PRIVATE)

    private val _user = MutableStateFlow(session.activeAccountId.value?.let(::loadCachedUser))
    val user: StateFlow<AccountUser?> = _user.asStateFlow()

    private val _connectedApps = MutableStateFlow<List<ConnectedApp>?>(null)
    val connectedApps: StateFlow<List<ConnectedApp>?> = _connectedApps.asStateFlow()

    private val _activity = MutableStateFlow(ActivityFeed())
    val activity: StateFlow<ActivityFeed> = _activity.asStateFlow()

    suspend fun refreshProfile(): AccountUser = api.me().also(::setUser)

    /**
     * Seeds the profile from the sign-in response so Home has a name before its first refresh.
     * It is only stored: the screens get it when the session of that account becomes the active one.
     */
    fun onSignedIn(user: AccountUser?) {
        user?.let(::setUser)
    }

    /** The app now shows [userId] (null: nobody is signed in). Everything of the account before it leaves the flows. */
    fun switchTo(userId: Long?) {
        if (userId == null || _user.value?.id != userId) _user.value = userId?.let(::loadCachedUser)
        _connectedApps.value = null
        _activity.value = ActivityFeed()
    }

    /** The account signed out: its cached profile goes with it. */
    fun forget(userId: Long) {
        cache.edit().remove(userKey(userId)).apply()
    }

    suspend fun refreshConnectedApps() {
        // Not filtered on is_revoked: auth-v2 sets it as soon as ANY of an app's sessions was
        // revoked (a logout is enough), so it hid apps that are still connected. Same list as the
        // web dashboard until auth reports it per app (see docs/api_wiring.md).
        val asked = session.activeAccountId.value
        val apps = api.connectedApps()
        if (session.activeAccountId.value == asked) _connectedApps.value = apps
    }

    suspend fun refreshActivity() {
        val asked = session.activeAccountId.value
        val page = api.activity()
        if (session.activeAccountId.value == asked) _activity.value = ActivityFeed(page.entries, page.nextCursor, loaded = true)
    }

    suspend fun loadMoreActivity() {
        val cursor = _activity.value.nextCursor ?: return
        val asked = session.activeAccountId.value
        val page = api.activity(cursor)
        if (session.activeAccountId.value != asked) return
        _activity.update { feed ->
            val known = feed.entries.mapTo(HashSet()) { it.id }
            feed.copy(entries = feed.entries + page.entries.filterNot { it.id in known }, nextCursor = page.nextCursor)
        }
    }

    suspend fun disconnectApp(clientId: String) {
        api.disconnectApp(clientId)
        _connectedApps.update { apps -> apps?.filterNot { it.clientId == clientId } }
    }

    // [approvalId]: see Approvals.gated (Require Security App).

    suspend fun changeUsername(password: String, newUsername: String, approvalId: String? = null) {
        // auth-v2 revokes the session the request came from and returns a replacement pair.
        session.establish(api.changeUsername(password, newUsername, approvalId))
        refreshProfile()
    }

    suspend fun changePassword(currentPassword: String, newPassword: String, approvalId: String? = null) =
        api.changePassword(currentPassword, newPassword, approvalId)

    // Recovery phrase: [code] is null when the account has no 2FA or Require Security App replaces it.

    suspend fun setupRecoveryPhrase(password: String, code: String?, approvalId: String? = null) =
        api.setupRecoveryPhrase(password, code, approvalId)

    suspend fun confirmRecoveryPhrase(setupToken: String, words: List<String>) {
        val createdAt = api.confirmRecoveryPhrase(setupToken, words)
        _user.value?.let { setUser(it.copy(recoveryPhraseEnabled = true, recoveryPhraseCreatedAt = createdAt)) }
    }

    suspend fun removeRecoveryPhrase(password: String, code: String?, approvalId: String? = null) {
        api.removeRecoveryPhrase(password, code, approvalId)
        _user.value?.let { setUser(it.copy(recoveryPhraseEnabled = false, recoveryPhraseCreatedAt = null)) }
    }

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

    suspend fun deleteAccount(password: String, code: String, approvalId: String? = null) {
        api.deleteAccount(password, code, approvalId)
        session.signOutLocally()
    }

    /** Confirms the open legal documents and reflects the answer without a reload. */
    suspend fun acceptLegal() {
        val open = _user.value?.legal ?: return
        val remaining = api.acceptLegal(open.documents)
        _user.value?.let { setUser(it.copy(legal = remaining)) }
    }

    suspend fun signOut() = session.signOut()

    private fun setUser(user: AccountUser) {
        cache.edit().putString(userKey(user.id), user.toJson().toString()).apply()
        // Only the active account is shown; the profile of another one is just remembered.
        if (user.id == session.activeAccountId.value) _user.value = user
    }

    private fun loadCachedUser(userId: Long): AccountUser? {
        cache.getString(userKey(userId), null)?.let { raw ->
            return runCatching { AccountUser.fromJson(JSONObject(raw)) }.getOrNull()
        }
        // The one profile single-account builds cached, for the account that was signed in then.
        val legacy = cache.getString(LegacyUserKey, null)?.let { raw ->
            runCatching { AccountUser.fromJson(JSONObject(raw)) }.getOrNull()
        } ?: return null
        if (userId != SessionBook.UnknownUser && legacy.id != userId) return null
        cache.edit().remove(LegacyUserKey).putString(userKey(legacy.id), legacy.toJson().toString()).apply()
        return legacy
    }

    private fun userKey(userId: Long) = "user_$userId"

    private companion object {
        const val LegacyUserKey = "user"
    }
}
