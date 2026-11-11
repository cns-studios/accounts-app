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
    Password("password", setOf("password_changed", "admin_reset_requested", "admin_reset_used"), R.string.notify_event_password),
    Username("username", setOf("username_changed"), R.string.notify_event_username),
    TwoFactor("2fa", setOf("2fa_enabled", "2fa_disabled"), R.string.notify_event_2fa),
    SecurityApp("security_app", setOf("require_app_enabled", "require_app_disabled"), R.string.notify_event_security_app),
    RecoveryPhrase("recovery_phrase", setOf("recovery_phrase_created", "recovery_phrase_removed", "recovery_phrase_used"), R.string.notify_event_recovery_phrase),
    Legal("legal", setOf("legal_update"), R.string.notify_event_legal),
    ;

    companion object {
        fun forEvent(event: String): NotificationEvent? = entries.firstOrNull { event in it.events }
    }
}

/**
 * The app's own language (see [Supported]), independent of the device's. The choice is
 * applied by wrapping each context's configuration (activity and application), so it takes
 * effect on the next activity start; changing it recreates the activity.
 */
object AppLanguage {
    const val Key = "language"
    /**
     * The tag of the joke language: English in internet slang, all lower case. It is the same
     * tag the website uses. It is never picked from the device's language, only from the
     * setting.
     *
     * aapt2 cannot compile a resource folder for a variant (b+en+brainrot), so its resources
     * live in values-en-rZZ: ZZ is a region code that is reserved for private use and no
     * device reports, so only this setting selects it.
     */
    const val Brainrot = "en-brainrot"
    private val BrainrotResources = Locale("en", "ZZ")

    val Supported = listOf("en", "de", "fr", "es", Brainrot)

    fun stored(context: Context): String =
        context.getSharedPreferences(AppPreferences.Name, Context.MODE_PRIVATE).getString(Key, "").orEmpty()

    /** The language tag texts from the API should be shown in: the chosen one, or the device's. */
    fun current(context: Context): String =
        stored(context).takeIf { it in Supported } ?: context.resources.configuration.locales[0].language

    /** [base] with the chosen language applied ("" keeps the device's). */
    fun wrap(base: Context): Context {
        val tag = stored(base)
        val locale = if (tag in Supported) formatLocale(tag) else Resources.getSystem().configuration.locales[0]
        // Dates and numbers formatted without a context (DateTimeFormatter) follow it too.
        Locale.setDefault(locale)
        if (tag !in Supported) return base
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(resourceLocale(tag))) }
        return base.createConfigurationContext(config)
    }

    /** The locale whose resources show the language. */
    private fun resourceLocale(tag: String): Locale = if (tag == Brainrot) BrainrotResources else Locale.forLanguageTag(tag)

    /** The locale dates and numbers follow: the joke language formats like English. */
    private fun formatLocale(tag: String): Locale = if (tag == Brainrot) Locale.ENGLISH else Locale.forLanguageTag(tag)
}
