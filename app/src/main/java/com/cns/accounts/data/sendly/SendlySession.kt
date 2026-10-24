package com.cns.accounts.data.sendly

import android.os.SystemClock
import com.cns.accounts.data.AccountRepository
import com.cns.accounts.data.api.ApiException
import com.cns.accounts.data.api.ApprovalPoll
import com.cns.accounts.data.api.ClientAuthorization
import com.cns.accounts.data.api.CnsApi
import com.cns.accounts.data.api.TokenPair
import com.cns.accounts.data.approvals.Approvals
import com.cns.accounts.data.session.SecureStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * This app signed in to Sendly: a CNS token issued to the `sendly` OAuth client, the way Sendly's
 * own clients get one, but without a browser. The app's CNS session asks auth for an
 * authorization code (PKCE, redirect URI [RedirectUri] registered on the client) and exchanges it.
 * The refresh token is kept per account in [SecureStore] and rotated like the app's own.
 */
class SendlySession(
    private val api: CnsApi,
    private val account: AccountRepository,
    private val approvals: Approvals,
    private val store: SecureStore,
) {
    private val mutex = Mutex()
    private var cached: TokenPair? = null
    private var cachedFor: Long? = null
    private var validUntil = 0L

    /** [onWaiting]: the sign-in to Sendly needs the approver phone (Require Security App). */
    @Volatile
    var onWaiting: () -> Unit = {}

    suspend fun accessToken(forceRefresh: Boolean = false): String = mutex.withLock {
        val userId = (account.user.value ?: account.refreshProfile()).id
        val current = cached
        if (!forceRefresh && current != null && cachedFor == userId && SystemClock.elapsedRealtime() < validUntil) {
            return current.accessToken
        }
        val stored = store.get(refreshKey(userId))
        val tokens = stored?.let { refreshToken ->
            try {
                api.refreshClientToken(refreshToken, ClientId)
            } catch (e: ApiException) {
                // Revoked (Sendly disconnected, signed out everywhere): sign in to Sendly again.
                if (e.status == 401) null else throw e
            }
        } ?: signIn()
        store.put(refreshKey(userId), tokens.refreshToken)
        cached = tokens
        cachedFor = userId
        validUntil = SystemClock.elapsedRealtime() + (tokens.expiresInSeconds * 1000 - 60_000).coerceAtLeast(0)
        tokens.accessToken
    }

    /** Drops the account's Sendly sign-in (Disconnect). */
    fun forget(userId: Long) {
        store.put(refreshKey(userId), null)
        if (cachedFor == userId) cached = null
    }

    private suspend fun signIn(): TokenPair {
        val verifier = randomToken()
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val state = randomToken()
        val code = when (val result = api.authorizeClient(ClientId, RedirectUri, challenge, state)) {
            is ClientAuthorization.Code -> result.code
            is ClientAuthorization.ApprovalRequired -> {
                val done = approvals.approveOrAwait(result.approval, onWaiting) as? ApprovalPoll.Completed
                done?.json?.optString("code")?.takeIf { it.isNotBlank() }
                    ?: throw ApiException(200, "bad_response", "The server did not return a code")
            }
        }
        return api.exchangeCode(code, verifier, ClientId, RedirectUri, state)
    }

    private fun randomToken(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))

    private fun refreshKey(userId: Long) = "sendly_refresh_$userId"

    companion object {
        // The OAuth client's id in auth (its display name is "Sendly"); Sendly's CNS_AUTH_CLIENT_ID.
        const val ClientId = "sendly"

        /** Must be listed on the `sendly` OAuth client in auth (admin panel). Never opened: no browser. */
        const val RedirectUri = "cnsaccounts://sendly-callback"
    }
}
