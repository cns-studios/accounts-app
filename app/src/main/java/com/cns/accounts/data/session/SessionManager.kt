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
 * Owns the app's CNS session: the short-lived access token (memory only) and the rotating
 * refresh token (Keystore-encrypted on disk), refreshed through `/api/auth/mobile/refresh`.
 *
 * Refreshes are single-flight: auth-v2 rotates refresh tokens and treats a second use of the
 * same one as token theft (it revokes the whole family and alerts admins), so two parallel 401s
 * must never both spend the stored token.
 */
class SessionManager(
    private val transport: HttpTransport,
    private val secureStore: SecureStore,
) {
    private val refreshMutex = Mutex()

    @Volatile private var accessToken: String? = null
    @Volatile private var accessTokenValidUntil: Long = 0L

    // Signed in as long as a refresh token is stored: the app opens straight to Home (and works
    // from cache while offline); the first API call fetches an access token.
    private val _authState = MutableStateFlow(
        if (secureStore.get(RefreshTokenKey) != null) AuthState.SignedIn else AuthState.SignedOut,
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** Called on successful sign-in (or when an endpoint hands back a fresh pair, e.g. username change). */
    fun establish(tokens: TokenPair) {
        secureStore.put(RefreshTokenKey, tokens.refreshToken)
        setAccessToken(tokens)
        _authState.value = AuthState.SignedIn
    }

    /** A usable access token, refreshing first if the cached one is missing or about to expire. */
    suspend fun accessToken(): String {
        val current = accessToken
        if (current != null && SystemClock.elapsedRealtime() < accessTokenValidUntil) return current
        return refresh(staleToken = current)
    }

    /**
     * Exchanges the stored refresh token for a new pair. [staleToken] is the access token the
     * caller saw fail: if another caller already replaced it while we waited for the lock, that
     * newer token is returned instead of spending the refresh token a second time.
     */
    suspend fun refresh(staleToken: String?): String = refreshMutex.withLock {
        val current = accessToken
        if (current != null && current != staleToken && SystemClock.elapsedRealtime() < accessTokenValidUntil) {
            return current
        }
        val refreshToken = secureStore.get(RefreshTokenKey) ?: run {
            signOutLocally()
            throw SessionExpiredException()
        }
        val response = transport.execute(
            method = "POST",
            path = "/api/auth/mobile/refresh",
            body = RequestBody.Json(JSONObject().put("refresh_token", refreshToken)),
        )
        if (!response.isSuccessful) {
            // 401 = token rejected (revoked, expired, reused): the session is gone for good.
            // Anything else (429, 5xx) is transient - keep the refresh token for the next try.
            if (response.status == 401) {
                signOutLocally()
                throw SessionExpiredException()
            }
            throw ApiException.from(response)
        }
        val tokens = TokenPair.fromJson(response.json())
        secureStore.put(RefreshTokenKey, tokens.refreshToken)
        setAccessToken(tokens)
        tokens.accessToken
    }

    /** Server-side sign-out is best effort; this always forgets the session on the device. */
    suspend fun signOut() {
        val token = accessToken
        if (token != null) {
            runCatching { transport.execute("POST", "/api/auth/logout", bearerToken = token) }
        }
        signOutLocally()
    }

    fun signOutLocally() {
        accessToken = null
        accessTokenValidUntil = 0L
        secureStore.put(RefreshTokenKey, null)
        _authState.value = AuthState.SignedOut
    }

    private fun setAccessToken(tokens: TokenPair) {
        accessToken = tokens.accessToken
        val lifetimeMillis = (tokens.expiresInSeconds * 1000 - ExpirySkewMillis).coerceAtLeast(0)
        accessTokenValidUntil = SystemClock.elapsedRealtime() + lifetimeMillis
    }

    private companion object {
        const val RefreshTokenKey = "refresh_token"
        // Refresh a minute early so a request doesn't race the token's expiry.
        const val ExpirySkewMillis = 60_000L
    }
}
