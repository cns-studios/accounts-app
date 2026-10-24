package com.cns.accounts.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device-local settings that never reach the API. */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(Name, Context.MODE_PRIVATE)

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

    private val _lockAfterMillis = MutableStateFlow(prefs.getLong(LockAfterKey, LockDelay.Default.millis))

    /** How long the app may sit in the background before biometric unlock locks it again. */
    val lockAfterMillis: StateFlow<Long> = _lockAfterMillis.asStateFlow()

    fun setLockAfterMillis(millis: Long) {
        prefs.edit().putLong(LockAfterKey, millis).apply()
        _lockAfterMillis.value = millis
    }

    private val _mutedEvents = MutableStateFlow(prefs.getStringSet(MutedEventsKey, null)?.toSet().orEmpty())

    /** [NotificationEvent] ids the user turned off; everything else notifies while Notifications is on. */
    val mutedEvents: StateFlow<Set<String>> = _mutedEvents.asStateFlow()

    fun setEventNotifications(event: NotificationEvent, enabled: Boolean) {
        val muted = if (enabled) _mutedEvents.value - event.id else _mutedEvents.value + event.id
        prefs.edit().putStringSet(MutedEventsKey, muted).apply()
        _mutedEvents.value = muted
    }

    private val _language = MutableStateFlow(prefs.getString(AppLanguage.Key, "").orEmpty())

    /** The app's language tag ("en", "de"), or "" to follow the device. See [AppLanguage]. */
    val language: StateFlow<String> = _language.asStateFlow()

    fun setLanguage(tag: String) {
        // commit(), not apply(): the activity is recreated right after and reads it back.
        prefs.edit().putString(AppLanguage.Key, tag).commit()
        _language.value = tag
    }

    companion object {
        const val Name = "cns_preferences"
        const val NotSeen = -1L
        private const val BiometricUnlockKey = "biometric_unlock"
        private const val NotificationsKey = "notifications"
        private const val HistorySeenAtKey = "history_seen_at"
        private const val CelebratedFactorsKey = "celebrated_security_factors"
        private const val LockAfterKey = "lock_after_millis"
        private const val MutedEventsKey = "muted_notification_events"
    }
}
