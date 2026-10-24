package com.cns.accounts.data

import androidx.compose.runtime.Immutable
import com.cns.accounts.data.authenticator.TwoFactorState
import java.time.Instant

@Immutable
data class Profile(
    val name: String,
    val twoFactorEnabled: Boolean,
    val avatarUrl: String? = null,
)

/**
 * A request waiting for this device's approval (Require Security App, see
 * [com.cns.accounts.data.approvals.Approvals]): a sign-in or a sensitive change. [action] is
 * auth-v2's action name; [detail] the app being signed in to or the requested new username.
 */
@Immutable
data class SignInRequest(
    val id: String,
    val action: String,
    val device: String,
    val createdAt: Instant?,
    val detail: String? = null,
)

/**
 * A connected app (auth-v2 `/api/me/connected-apps`). [needsSetup] marks a service this app can
 * do more for once set up (Sendly, see [ServiceLinks]); [loginRequest] is a sign-in to that
 * service waiting for this app's approval.
 */
@Immutable
data class Service(
    val id: String,
    val name: String,
    val lastUsed: Instant?,
    val needsSetup: Boolean = false,
    val loginRequest: ServiceLoginRequest? = null,
)

/** A new device signing in to a service, to be allowed or denied here (Sendly device enrollment). */
@Immutable
data class ServiceLoginRequest(
    val id: String,
    val device: String,
    val code: String,
    val createdAt: Instant?,
)

@Immutable
data class HomeData(
    val profile: Profile = Profile("", twoFactorEnabled = false),
    val signInRequest: SignInRequest? = null,
    val twoFactor: TwoFactorState = TwoFactorState.Unknown,
    // null until the first load, so the card can tell "loading" from "no apps".
    val services: List<Service>? = null,
)

// The card's timer row: 17 squares, one spent every 30s/17 (token.json's fixed square count).
const val TimerSquareCount = 17

fun sampleHomeData() = HomeData(
    profile = Profile("aaron", twoFactorEnabled = true),
    signInRequest = SignInRequest("preview", "login", "Firefox on Linux", Instant.now()),
    twoFactor = TwoFactorState.Active("JBSWY3DPEHPK3PXP"),
    services = listOf(Service("sendly", "Sendly", Instant.now(), needsSetup = true), Service("ilc", "ILC", Instant.now())),
)
