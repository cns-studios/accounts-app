package com.cns.accounts.data

import androidx.compose.runtime.Immutable
import java.time.Instant

@Immutable
data class Profile(
    val name: String,
    val twoFactorEnabled: Boolean,
    val avatarUrl: String? = null,
)

/** A pending sign-in to approve; always null until auth-v2 supports it (see [SignInRequests]). */
@Immutable
data class SignInRequest(
    val id: String,
    val device: String,
    val timeLabel: String,
)

/** The account code card's authenticator key (base32), from [com.cns.accounts.data.authenticator.AuthenticatorStore]. */
@Immutable
data class Account(
    val label: String,
    val secret: String,
    val secretLength: Int = TimerSquareCount,
)

/** A connected app (auth-v2 `/api/me/connected-apps`); [lastUsed] drives the "now"/"2h ago" label. */
@Immutable
data class Service(
    val id: String,
    val name: String,
    val lastUsed: Instant?,
)

@Immutable
data class HomeData(
    val profile: Profile = Profile("", twoFactorEnabled = false),
    val signInRequest: SignInRequest? = null,
    val account: Account? = null,
    // null until the first load, so the card can tell "loading" from "no apps".
    val services: List<Service>? = null,
)

// The card's timer row: 17 squares, one spent every 30s/17 (token.json's fixed square count).
const val TimerSquareCount = 17

fun sampleHomeData() = HomeData(
    profile = Profile("aaron", twoFactorEnabled = true),
    signInRequest = SignInRequest("preview", "Firefox on Linux", "now"),
    account = Account("Account", "JBSWY3DPEHPK3PXP"),
    services = listOf(Service("sendly", "Sendly", Instant.now())),
)
