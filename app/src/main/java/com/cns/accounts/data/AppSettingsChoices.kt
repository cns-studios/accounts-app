package com.cns.accounts.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.StringRes
import com.cns.accounts.R
import java.util.Locale

/** The biometric lock's delays to choose from (how long in the background before it locks). */
enum class LockDelay(val millis: Long, @param:StringRes val label: Int) {
    Immediately(0L, R.string.lock_after_immediately),
    ThirtySeconds(30_000L, R.string.lock_after_30_seconds),
    OneMinute(60_000L, R.string.lock_after_1_minute),
    FiveMinutes(5 * 60_000L, R.string.lock_after_5_minutes),
    FifteenMinutes(15 * 60_000L, R.string.lock_after_15_minutes),
    ;

    companion object {
        val Default = ThirtySeconds
        fun of(millis: Long): LockDelay = entries.firstOrNull { it.millis == millis } ?: Default
    }
}

/**
 * The kinds of account event a push can announce (auth-v2's `account_event` names), each of
 * which can be muted on its own while Notifications is on.
 */
enum class NotificationEvent(val id: String, val events: Set<String>, @param:StringRes val label: Int) {
    NewLogin("new_login", setOf("new_login"), R.string.notify_event_new_login),
    Password("password", setOf("password_changed"), R.string.notify_event_password),
    Username("username", setOf("username_changed"), R.string.notify_event_username),
    TwoFactor("2fa", setOf("2fa_enabled", "2fa_disabled"), R.string.notify_event_2fa),
    SecurityApp("security_app", setOf("require_app_enabled", "require_app_disabled"), R.string.notify_event_security_app),
    ;

    companion object {
        fun forEvent(event: String): NotificationEvent? = entries.firstOrNull { event in it.events }
    }
}

/**
 * The app's own language (English or German), independent of the device's. The choice is
 * applied by wrapping each context's configuration (activity and application), so it takes
 * effect on the next activity start; changing it recreates the activity.
 */
object AppLanguage {
    const val Key = "language"
    val Supported = listOf("en", "de")

    fun stored(context: Context): String =
        context.getSharedPreferences(AppPreferences.Name, Context.MODE_PRIVATE).getString(Key, "").orEmpty()

    /** [base] with the chosen language applied ("" keeps the device's). */
    fun wrap(base: Context): Context {
        val tag = stored(base)
        val locale = if (tag in Supported) Locale.forLanguageTag(tag) else Resources.getSystem().configuration.locales[0]
        // Dates and numbers formatted without a context (DateTimeFormatter) follow it too.
        Locale.setDefault(locale)
        if (tag !in Supported) return base
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }
}
