package com.cns.accounts.ui.settings

import androidx.compose.runtime.Immutable
import com.cns.accounts.data.SettingsData
import com.cns.accounts.data.UserMessage

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
    DisableTwoFactor,
    ChangePassword,
    DeleteAccount,
}

/**
 * The open settings dialog. [values] are its input fields in display order (the screen maps
 * [kind] to titles, placeholders and field types); [error] is shown inline under the fields.
 * Dialogs that need a 2FA code only ask for one when this device doesn't hold the key; otherwise
 * a current code is generated at submit time.
 */
@Immutable
data class SettingsDialog(
    val kind: SettingsDialogKind,
    val values: List<String> = emptyList(),
    val isBusy: Boolean = false,
    val error: UserMessage? = null,
)
