package com.cns.accounts.ui.settings

import androidx.lifecycle.ViewModel
import com.cns.accounts.data.sampleSettingsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState(data = sampleSettingsData()))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setBiometricUnlock(enabled: Boolean) = updateToggles { copy(biometricUnlock = enabled) }
    fun setStrongAccountProtection(enabled: Boolean) = updateToggles { copy(strongAccountProtection = enabled) }
    fun setTwoFactorAuthentication(enabled: Boolean) = updateToggles { copy(twoFactorAuthentication = enabled) }
    fun setRequireSecurityApp(enabled: Boolean) = updateToggles { copy(requireSecurityApp = enabled) }

    private inline fun updateToggles(crossinline transform: com.cns.accounts.data.SettingsToggles.() -> com.cns.accounts.data.SettingsToggles) {
        _uiState.update { state -> state.copy(data = state.data.copy(toggles = state.data.toggles.transform())) }
    }

    fun editUsername(newUsername: String) {
        val trimmed = newUsername.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { state -> state.copy(data = state.data.copy(profile = state.data.profile.copy(name = trimmed))) }
    }
    fun changeAvatar() = Unit
    fun removeAvatar() = Unit
    fun configureTwoFactor() = Unit
    fun setupRecoveryPhrase() = Unit
    fun changePassword() = Unit
    fun logOut() = Unit
    fun deleteAccount() = Unit
}
