package com.cns.accounts.ui.settings

import androidx.compose.runtime.Immutable
import com.cns.accounts.data.SettingsData
import com.cns.accounts.data.UserMessage
import com.cns.accounts.data.api.MfaSetup

@Immutable
data class SettingsUiState(
    val data: SettingsData = SettingsData(),
    val dialog: SettingsDialog? = null,
)

enum class SettingsDialogKind {
    EditUsername,
    ChangeAvatar,
    RemoveAvatar,
    ConfigureTwoFactor,
    SetupTwoFactor,
    DisableTwoFactor,
    ChangePassword,
    DeleteAccount,
}

/**
 * The open settings dialog. [values] are its input fields in display order (the screen maps
 * [kind] to titles, placeholders and field types); [error] is shown inline under the fields.
 * For [SettingsDialogKind.SetupTwoFactor], [setup] is the pending TOTP key and
 * [keyStoredOnDevice] says whether this app's authenticator holds it (so the code is pre-filled
 * and there is nothing to copy). [prefilledCode] is the 2FA code the dialog was opened with, if
 * any: when the user leaves it untouched, a fresh code is generated at submit time instead, so a
 * dialog left open past the 30s window doesn't send an expired one.
 */
@Immutable
data class SettingsDialog(
    val kind: SettingsDialogKind,
    val values: List<String> = emptyList(),
    val isBusy: Boolean = false,
    val error: UserMessage? = null,
    val setup: MfaSetup? = null,
    val keyStoredOnDevice: Boolean = false,
    val prefilledCode: String? = null,
)
