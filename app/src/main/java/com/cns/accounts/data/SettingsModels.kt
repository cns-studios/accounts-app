package com.cns.accounts.data

import androidx.compose.runtime.Immutable

// settings.tokens.json (Frame9 "Security center"): a completion percentage plus one progress bar
// per security factor (2FA, password, authenticator app, recovery phrase). Computed on the device
// from the account state (see [securityStatus]); there is no server-side score. The embedded PNGs
// (Rectangle36-38, 76x7px) are solid white pill bars for achieved factors; the missing factor's
// image (Rectangle39) is a blank 1x1 pixel rendered only via its red pill-shaped border.
@Immutable
data class SecurityFactor(
    val label: String,
    val achieved: Boolean,
)

@Immutable
data class SecurityStatus(
    val percentComplete: Int,
    val factors: List<SecurityFactor>,
)

// settings.tokens.json (Frame30): each row is a label (some with a help glyph) paired with a
// toggle. Biometric unlock is device-local and 2FA mirrors the account; strong account protection
// and "require security app" have no auth-v2 backing yet, so they always read off.
@Immutable
data class SettingsToggles(
    val biometricUnlock: Boolean = false,
    val strongAccountProtection: Boolean = false,
    val twoFactorAuthentication: Boolean = false,
    val requireSecurityApp: Boolean = false,
)

@Immutable
data class SettingsData(
    val profile: Profile = Profile("", twoFactorEnabled = false),
    val security: SecurityStatus = securityStatus(twoFactorEnabled = false, authenticatorOnDevice = false),
    val toggles: SettingsToggles = SettingsToggles(),
)

/**
 * The security center score. Factors: 2FA on the account; a password (every CNS account has
 * one); the app factor = this device holds the CNS authenticator key; a recovery phrase, which
 * auth-v2 can't store yet, so it is never achieved.
 */
fun securityStatus(twoFactorEnabled: Boolean, authenticatorOnDevice: Boolean): SecurityStatus {
    val factors = listOf(
        SecurityFactor("2FA", achieved = twoFactorEnabled),
        SecurityFactor("PW", achieved = true),
        SecurityFactor("App", achieved = authenticatorOnDevice),
        SecurityFactor("Phrase", achieved = false),
    )
    return SecurityStatus(percentComplete = factors.count { it.achieved } * 100 / factors.size, factors = factors)
}

fun sampleSettingsData() = SettingsData(
    profile = Profile("aaron", twoFactorEnabled = true),
    security = securityStatus(twoFactorEnabled = true, authenticatorOnDevice = true),
    toggles = SettingsToggles(biometricUnlock = true, twoFactorAuthentication = true),
)
