package com.cns.accounts.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cns.accounts.AppContainer
import com.cns.accounts.BuildConfig
import com.cns.accounts.R
import com.cns.accounts.data.LockDelay
import com.cns.accounts.data.NotificationEvent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class AppSettingsUiState(
    val biometricUnlock: Boolean = false,
    val notifications: Boolean = false,
    val lockDelay: LockDelay = LockDelay.Default,
    val mutedEvents: Set<String> = emptySet(),
    val language: String = "",
    val about: AboutInfo = AboutInfo(),
)

/** The app's version, shown above Log Out. */
@Immutable
data class AboutInfo(
    val versionName: String = BuildConfig.VERSION_NAME,
    val versionCode: Int = BuildConfig.VERSION_CODE,
)

/**
 * The app Settings page (the profile header's gear): this device's settings — biometric
 * unlock (and how soon it locks), account notifications (and which events), the app's
 * language, all device-local — plus sign-out and the app version.
 */
class AppSettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val messages = container.messages

    val uiState: StateFlow<AppSettingsUiState> = combine(
        container.preferences.biometricUnlock,
        container.preferences.notifications,
        container.preferences.lockAfterMillis,
        container.preferences.mutedEvents,
        container.preferences.language,
    ) { biometric, notifications, lockAfter, muted, language ->
        AppSettingsUiState(
            biometricUnlock = biometric,
            notifications = notifications,
            lockDelay = LockDelay.of(lockAfter),
            mutedEvents = muted,
            language = language,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettingsUiState())

    /** Called only after the UI has confirmed the user with a biometric prompt (when enabling). */
    fun setBiometricUnlock(enabled: Boolean) = container.preferences.setBiometricUnlock(enabled)

    /** Account-event notifications; the screen has asked for the notification permission first. */
    fun setNotifications(enabled: Boolean) {
        if (enabled && !container.push.isConfigured) {
            messages.show(R.string.push_unavailable, isError = true)
            return
        }
        container.preferences.setNotifications(enabled)
        if (enabled) messages.show(R.string.notifications_on)
    }

    fun setLockDelay(delay: LockDelay) = container.preferences.setLockAfterMillis(delay.millis)

    fun setEventNotifications(event: NotificationEvent, enabled: Boolean) = container.preferences.setEventNotifications(event, enabled)

    /** "" follows the device. The screen recreates the activity afterwards so it takes effect. */
    fun setLanguage(tag: String) = container.preferences.setLanguage(tag)

    fun logOut() {
        viewModelScope.launch { container.account.signOut() }
    }
}
