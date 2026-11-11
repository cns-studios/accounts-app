package com.cns.accounts.data.session

import android.os.SystemClock
import com.cns.accounts.data.api.ApiException
import com.cns.accounts.data.api.HttpTransport
import com.cns.accounts.data.api.RequestBody
import com.cns.accounts.data.api.SessionExpiredException
import com.cns.accounts.data.api.TokenPair
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

enum class AuthState { SignedIn, SignedOut }

/**
 * Owns the app's CNS sessions: one per account signed in on this device ([SessionBook]), of
 * which one is the active account the whole app talks as. Each has a rotating refresh token
 * (Keystore-encrypted on disk), refreshed through `/api/auth/mobile/refresh`; the short-lived
 * access token of the active account lives in memory only.
 *
 * Refreshes are single-flight: auth-v2 rotates refresh tokens and treats a second use of the
 * same one as token theft (it revokes the whole family and alerts admins), so two parallel 401s
 * must never both spend the stored token. A refresh belongs to the account that started it: if
 * the app switched accounts meanwhile, its new refresh token is still stored for that account.
 */
class SessionManager(
    private val transport: HttpTransport,
    private val secureStore: SecureStore,
) {
    private val refreshMutex = Mutex()

    @Volatile private var accessToken: String? = null
    @Volatile private var accessTokenValidUntil: Long = 0L

    // Signed in as long as an account has a refresh token stored: the app opens straight to Home
    // (and works from cache while offline); the first API call fetches an access token.
    @Volatile private var book = load()

    private val _authState = MutableStateFlow(book.stateOf())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _activeAccountId = MutableStateFlow(book.activeId)

    /** The account the app shows, or null when nobody is signed in. */
    val activeAccountId: StateFlow<Long?> = _activeAccountId.asStateFlow()

    private val _accounts = MutableStateFlow(book.accounts())

    /** Every account signed in on this device, in the order they signed in. */
    val accounts: StateFlow<List<SignedInAccount>> = _accounts.asStateFlow()

    private val _addingAccount = MutableStateFlow(false)

    /** The sign-in screen is open on top of the signed-in app, to add another account. */
    val addingAccount: StateFlow<Boolean> = _addingAccount.asStateFlow()

    fun beginAddingAccount() {
        _addingAccount.value = true
    }

    fun cancelAddingAccount() {
        _addingAccount.value = false
    }

    /** Called on successful sign-in (or when an endpoint hands back a fresh pair, e.g. username change). */
    @Synchronized
    fun establish(tokens: TokenPair) {
        val claims = AccessTokenClaims.parse(tokens.accessToken)
        val current = book.active
        // A token we cannot read belongs to the account that was active (a username change hands
        // back a pair for the same account); with nobody active there is nobody to file it under.
        val userId = claims?.userId ?: current?.userId ?: return
        val username = claims?.username?.takeIf { it.isNotBlank() } ?: current?.username.orEmpty()
        if (userId != book.activeId) clearAccessToken()
        publish(book.signedIn(StoredSession(userId, username, tokens.refreshToken)))
        setAccessToken(tokens)
        _addingAccount.value = false
    }

    /** Makes [userId] the account the app shows. Its access token is fetched on first use. */
    @Synchronized
    fun switchTo(userId: Long) {
        val after = book.activate(userId)
        if (after.activeId == book.activeId) return
        clearAccessToken()
        publish(after)
    }

    /** A usable access token, refreshing first if the cached one is missing or about to expire. */
    suspend fun accessToken(): String {
        val current = accessToken
        if (current != null && SystemClock.elapsedRealtime() < accessTokenValidUntil) return current
        return refresh(staleToken = current)
    }

    /**
     * Exchanges the stored refresh token of the active account for a new pair. [staleToken] is
     * the access token the caller saw fail: if another caller already replaced it while we waited
     * for the lock, that newer token is returned instead of spending the refresh token a second time.
     */
    suspend fun refresh(staleToken: String?): String = refreshMutex.withLock {
        val current = accessToken
        if (current != null && current != staleToken && SystemClock.elapsedRealtime() < accessTokenValidUntil) {
            return current
        }
        val session = book.active ?: throw SessionExpiredException()
        val response = transport.execute(
            method = "POST",
            path = "/api/auth/mobile/refresh",
            body = RequestBody.Json(JSONObject().put("refresh_token", session.refreshToken)),
        )
        if (!response.isSuccessful) {
            // 401 = token rejected (revoked, expired, reused): that account's session is gone for
            // good. Anything else (429, 5xx) is transient - keep the refresh token for the next try.
            if (response.status == 401) {
                removeAccount(session.userId)
                throw SessionExpiredException()
            }
            throw ApiException.from(response)
        }
        val tokens = TokenPair.fromJson(response.json())
        keepRotatedToken(session, tokens)
        // Only the access token of the account in use is kept in memory.
        val owner = AccessTokenClaims.parse(tokens.accessToken)?.userId ?: session.userId
        if (book.activeId == owner || book.activeId == session.userId) setAccessToken(tokens)
        tokens.accessToken
    }

    /** Server-side sign-out is best effort; this always forgets the active account on the device. Another account, if any, takes over. */
    suspend fun signOut() {
        val userId = book.activeId ?: return
        accessToken?.let { token -> runCatching { transport.execute("POST", "/api/auth/logout", bearerToken = token) } }
        removeAccount(userId)
    }

    /** Forgets the active account on the device only, e.g. after it was deleted. */
    fun signOutLocally() {
        book.activeId?.let(::removeAccount)
    }

    @Synchronized
    private fun removeAccount(userId: Long) {
        if (book.sessions.none { it.userId == userId }) return
        val after = book.without(userId)
        if (after.activeId != book.activeId) clearAccessToken()
        publish(after)
    }

    /** Stores the rotated token under the account that spent the old one; learns whose a migrated session is. */
    @Synchronized
    private fun keepRotatedToken(spent: StoredSession, tokens: TokenPair) {
        var next = book
        val claims = AccessTokenClaims.parse(tokens.accessToken)
        if (spent.userId == SessionBook.UnknownUser && claims != null) {
            next = next.identified(SessionBook.UnknownUser, claims.userId, claims.username)
        }
        val id = claims?.userId?.takeIf { next.sessions.any { s -> s.userId == it } } ?: spent.userId
        publish(next.withRefreshToken(id, tokens.refreshToken))
    }

    private fun publish(next: SessionBook) {
        // On disk first: a rotated refresh token must be stored before the old one is dropped.
        secureStore.put(BookKey, if (next.sessions.isEmpty()) null else next.toJson())
        secureStore.put(LegacyRefreshTokenKey, null)
        book = next
        _accounts.value = next.accounts()
        _activeAccountId.value = next.activeId
        _authState.value = next.stateOf()
    }

    private fun load(): SessionBook {
        secureStore.get(BookKey)?.let { return SessionBook.fromJson(it) }
        // A build from before several accounts: one refresh token, no idea whose it is yet.
        return secureStore.get(LegacyRefreshTokenKey)?.let(SessionBook::migrated) ?: SessionBook()
    }

    private fun SessionBook.stateOf() = if (active != null) AuthState.SignedIn else AuthState.SignedOut

    private fun clearAccessToken() {
        accessToken = null
        accessTokenValidUntil = 0L
    }

    private fun setAccessToken(tokens: TokenPair) {
        accessToken = tokens.accessToken
        val lifetimeMillis = (tokens.expiresInSeconds * 1000 - ExpirySkewMillis).coerceAtLeast(0)
        accessTokenValidUntil = SystemClock.elapsedRealtime() + lifetimeMillis
    }

    private companion object {
        const val BookKey = "cns_sessions"
        // What single-account builds stored; read once, then moved into the book.
        const val LegacyRefreshTokenKey = "refresh_token"
        // Refresh a minute early so a request doesn't race the token's expiry.
        const val ExpirySkewMillis = 60_000L
    }
}
