package com.cns.accounts.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pending "approve this sign-in" prompts for the Home sign-in request card.
 *
 * NOT BACKED YET: auth-v2 has no push-approval flow (no endpoint to list pending sign-ins or to
 * allow/deny one; 2FA is TOTP only). Until it does, there is never a pending request, so the card
 * stays hidden. Wire [pending] to the API (polling or FCM) and implement [respond] once the
 * backend lands — the card UI and its Allow/Deny callbacks are already in place.
 * See docs/api_wiring.md.
 */
class SignInRequests {
    private val _pending = MutableStateFlow<SignInRequest?>(null)
    val pending: StateFlow<SignInRequest?> = _pending.asStateFlow()

    /** Always fails until the backend exists; callers surface [UnsupportedFeatureException]. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun respond(request: SignInRequest, approve: Boolean): Nothing = throw UnsupportedFeatureException()
}

/** A feature whose UI exists but whose auth-v2 endpoint doesn't yet. */
class UnsupportedFeatureException : Exception("Not available yet")
