package com.cns.accounts.data

import androidx.compose.runtime.Immutable

// settings.tokens.json (Frame9 "Security center"): a completion percentage plus one progress bar
// per security factor (2FA, password, authenticator app, recovery phrase). The embedded PNGs
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
// toggle; defaults mirror the exported toggle thumb positions.
@Immutable
data class SettingsToggles(
    val biometricUnlock: Boolean = true,
    val strongAccountProtection: Boolean = false,
    val twoFactorAuthentication: Boolean = true,
    val requireSecurityApp: Boolean = true,
)

@Immutable
data class SettingsData(
    val profile: Profile = Profile("aaron", "2FA enabled"),
    val security: SecurityStatus = SecurityStatus(
        percentComplete = 80,
        factors = listOf(
            SecurityFactor("2FA", achieved = true),
            SecurityFactor("PW", achieved = true),
            SecurityFactor("App", achieved = true),
            SecurityFactor("Phrase", achieved = false),
        ),
    ),
    val toggles: SettingsToggles = SettingsToggles(),
)

fun sampleSettingsData() = SettingsData()
