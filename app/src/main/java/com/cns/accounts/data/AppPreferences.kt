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

    private val _historySeenAt = MutableStateFlow(prefs.getLong(HistorySeenAtKey, NotSeen))

    /** The newest History event time (epoch ms) the user has seen on the History tab; [NotSeen] before the first look. */
    val historySeenAt: StateFlow<Long> = _historySeenAt.asStateFlow()

    fun setHistorySeenAt(epochMillis: Long) {
        if (epochMillis <= _historySeenAt.value) return
        prefs.edit().putLong(HistorySeenAtKey, epochMillis).apply()
        _historySeenAt.value = epochMillis
    }

    private val _celebratedFactors = MutableStateFlow(prefs.getStringSet(CelebratedFactorsKey, null)?.toSet())

    /**
     * Security center factors whose completion the user has already been shown (see
     * [SecurityMilestones]); null until the first account state was recorded.
     */
    val celebratedFactors: StateFlow<Set<String>?> = _celebratedFactors.asStateFlow()

    fun setCelebratedFactors(ids: Set<String>) {
        if (ids == _celebratedFactors.value) return
        prefs.edit().putStringSet(CelebratedFactorsKey, ids).apply()
        _celebratedFactors.value = ids
    }

    companion object {
        const val NotSeen = -1L
        private const val BiometricUnlockKey = "biometric_unlock"
        private const val NotificationsKey = "notifications"
        private const val HistorySeenAtKey = "history_seen_at"
        private const val CelebratedFactorsKey = "celebrated_security_factors"
    }
}
