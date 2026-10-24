package com.cns.accounts.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device-local settings that never reach the API. */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("cns_preferences", Context.MODE_PRIVATE)

    private val _biometricUnlock = MutableStateFlow(prefs.getBoolean(BiometricUnlockKey, false))
    val biometricUnlock: StateFlow<Boolean> = _biometricUnlock.asStateFlow()

    fun setBiometricUnlock(enabled: Boolean) {
        prefs.edit().putBoolean(BiometricUnlockKey, enabled).apply()
        _biometricUnlock.value = enabled
    }

    private val _notifications = MutableStateFlow(prefs.getBoolean(NotificationsKey, false))

    /** Account-event notifications (new sign-ins, password changes, ...). */
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()

    fun setNotifications(enabled: Boolean) {
        prefs.edit().putBoolean(NotificationsKey, enabled).apply()
        _notifications.value = enabled
    }

    private companion object {
        const val BiometricUnlockKey = "biometric_unlock"
        const val NotificationsKey = "notifications"
    }
}
