package com.cns.accounts.ui.settings

import androidx.compose.runtime.Immutable
import com.cns.accounts.data.SettingsData

@Immutable
data class SettingsUiState(
    val data: SettingsData = SettingsData(),
)
