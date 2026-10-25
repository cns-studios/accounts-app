package com.cns.accounts.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cns.accounts.AppContainer
import com.cns.accounts.BuildConfig
import com.cns.accounts.R
import com.cns.accounts.data.LockDelay
import com.cns.accounts.data.NotificationEvent
import com.cns.accounts.data.authenticator.TwoFactorState
import com.cns.accounts.data.update.AppRelease
import com.cns.accounts.data.update.UpdateState
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
    /** In-app updates; off (no update block) in debug and `.local` builds. */
    val updatesEnabled: Boolean = false,
    val update: UpdateState = UpdateState.Idle,
    /** Set while this device is the account's authenticator and/or Security App: Log Out is off. */
    val logOutBlock: LogOutBlock? = null,
)

/**
 * Why this device can't log out: it is what the account signs in or approves with (its 2FA key
 * and/or the Require Security App approver key). Signed out, the approver gets no requests and
 * the codes on Home are gone, so the user has to move or turn these off first.
 */
enum class LogOutBlock(@StringRes val message: Int) {
    Authenticator(R.string.log_out_blocked_authenticator),
    SecurityApp(R.string.log_out_blocked_security_app),
    Both(R.string.log_out_blocked_both),
    ;

    companion object {
        fun of(authenticator: Boolean, securityApp: Boolean): LogOutBlock? = when {
            authenticator && securityApp -> Both
            authenticator -> Authenticator
            securityApp -> SecurityApp
            else -> null
        }
    }
}

/** The app's version, shown above Log Out. */
@Immutable
data class AboutInfo(
    val versionName: String = BuildConfig.VERSION_NAME,
    val versionCode: Int = BuildConfig.VERSION_CODE,
)

/**
 * The app Settings page (the profile header's gear): this device's settings - biometric
 * unlock (and how soon it locks), account notifications (and which events), the app's
 * language, all device-local - plus sign-out and the app version.
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
            updatesEnabled = container.appUpdates.enabled,
        )
    }.combine(container.appUpdates.state) { state, update -> state.copy(update = update) }
        .combine(logOutBlock()) { state, block -> state.copy(logOutBlock = block) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettingsUiState())

    private fun logOutBlock() = combine(container.twoFactor.state, container.approvals.isApprover) { twoFactor, approver ->
        LogOutBlock.of(authenticator = twoFactor is TwoFactorState.Active, securityApp = approver)
    }

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

    fun checkForUpdates() = container.appUpdates.checkNow()

    fun installUpdate(release: AppRelease) = container.appUpdates.install(release)

    fun logOut() {
        // The screen disables the button too; this check is the one that counts.
        val block = LogOutBlock.of(
            authenticator = container.twoFactor.state.value is TwoFactorState.Active,
            securityApp = container.approvals.isApprover.value,
        )
        if (block != null) {
            messages.show(block.message, isError = true)
            return
        }
        viewModelScope.launch { container.account.signOut() }
    }
}
