package com.cns.accounts.data

import androidx.compose.runtime.Immutable

// settings.tokens.json (Frame9 "Security center"): a completion percentage plus one progress bar
// per security factor (2FA, password, authenticator app, recovery phrase). Computed on the device
// from the account state (see [securityStatus]); there is no server-side score. The embedded PNGs
// (Rectangle36-38, 76x7px) are solid white pill bars for achieved factors; the missing factor's
// image (Rectangle39) is a blank 1x1 pixel rendered only via its red pill-shaped border.
@Immutable
data class SecurityFactor(
    val id: String,
    val label: String,
    val achieved: Boolean,
)

@Immutable
data class SecurityStatus(
    val percentComplete: Int,
    val factors: List<SecurityFactor>,
)

// settings.tokens.json (Frame30): each row is a label (some with a help glyph) paired with a
// toggle; 2FA and "require security app" mirror the account. (The device settings, biometric
// unlock and notifications, are on the app Settings page.)
@Immutable
data class SettingsToggles(
    val twoFactorAuthentication: Boolean = false,
    val requireSecurityApp: Boolean = false,
    /** Require Security App is on with another device as the approver: its name ("" if unnamed). */
    val approverElsewhere: String? = null,
    /** Not a toggle: the recovery phrase row shows "Set Up" or "Manage". */
    val recoveryPhrase: Boolean = false,
)

@Immutable
data class SettingsData(
    val profile: Profile = Profile("", twoFactorEnabled = false),
    val security: SecurityStatus = securityStatus(twoFactorEnabled = false, authenticatorOnDevice = false),
    val toggles: SettingsToggles = SettingsToggles(),
    // Completed factors the user hasn't been shown yet (see SecurityMilestones).
    val newlyCompletedFactors: Set<String> = emptySet(),
)

/**
 * The security center score. Factors: 2FA on the account; a password (every CNS account has
 * one); the app factor = this device holds the CNS authenticator key; a recovery phrase is set up.
 */
fun securityStatus(twoFactorEnabled: Boolean, authenticatorOnDevice: Boolean, recoveryPhrase: Boolean = false): SecurityStatus {
    val factors = listOf(
        SecurityFactor("2fa", "2FA", achieved = twoFactorEnabled),
        SecurityFactor("password", "PW", achieved = true),
        SecurityFactor("app", "App", achieved = authenticatorOnDevice),
        SecurityFactor("phrase", "Phrase", achieved = recoveryPhrase),
    )
    return SecurityStatus(percentComplete = factors.count { it.achieved } * 100 / factors.size, factors = factors)
}

fun sampleSettingsData() = SettingsData(
    profile = Profile("aaron", twoFactorEnabled = true),
    security = securityStatus(twoFactorEnabled = true, authenticatorOnDevice = true),
    toggles = SettingsToggles(twoFactorAuthentication = true),
)
