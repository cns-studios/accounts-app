package com.cns.accounts.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cns.accounts.MainActivity
import com.cns.accounts.R
import com.cns.accounts.data.AppLanguage

/**
 * Android notifications for account events and requests waiting for this app's approval. Each
 * kind is posted under its own tag so it can be taken down again once it is resolved: a request
 * once decided (here or elsewhere) or expired, account events once History has been seen, the
 * update note once no update is waiting.
 */
class Notifier(private val appContext: Context) {
    // In the app's chosen language, even when that changed after the process started.
    private val context: Context get() = AppLanguage.wrap(appContext)

    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(AccountChannel, context.getString(R.string.notification_channel_account), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(ApprovalChannel, context.getString(R.string.notification_channel_approvals), NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(UpdateChannel, context.getString(R.string.notification_channel_updates), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** A new version of the app is out (the daily update check). */
    fun appUpdate(version: String) =
        post(UpdateChannel, UpdateTag, UpdateChannel, context.getString(R.string.notify_update_title, version), context.getString(R.string.notify_update_body))

    /** No update is waiting (any more). */
    fun dismissAppUpdate() = dismiss(UpdateTag) { true }

    fun canPost(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** An auth-v2 account event (new sign-in, password change, ...). */
    fun accountEvent(event: String) {
        val title = when (event) {
            "new_login" -> R.string.notify_new_login
            "password_changed" -> R.string.notify_password_changed
            "username_changed" -> R.string.notify_username_changed
            "2fa_enabled" -> R.string.notify_2fa_enabled
            "2fa_disabled" -> R.string.notify_2fa_disabled
            "require_app_enabled" -> R.string.notify_require_app_enabled
            "require_app_disabled" -> R.string.notify_require_app_disabled
            "recovery_phrase_created" -> R.string.notify_recovery_phrase_created
            "recovery_phrase_removed" -> R.string.notify_recovery_phrase_removed
            "recovery_phrase_used" -> R.string.notify_recovery_phrase_used
            else -> return
        }
        post(AccountChannel, AccountTag, event, context.getString(title), context.getString(R.string.notify_account_body))
    }

    /** History was on screen: the account events notified about have been seen. */
    fun dismissAccountEvents() = dismiss(AccountTag) { true }

    /** A sign-in or change waiting for this app's approval (Require Security App). */
    fun approvalRequest(id: String) =
        post(
            ApprovalChannel, ApprovalTag, id, context.getString(R.string.notify_approval_title), context.getString(R.string.notify_approval_body),
            // Requests expire after two minutes; the push doesn't say exactly when.
            timeoutMillis = ApprovalTimeoutMillis,
        )

    /** A new Sendly device waiting for this app's approval. */
    fun sendlyEnrollment(id: String) =
        post(ApprovalChannel, SendlyTag, id, context.getString(R.string.notify_sendly_title), context.getString(R.string.notify_sendly_body))

    /** Takes down the notification of request [id] (approval or Sendly). */
    fun cancel(id: String) = dismiss(ApprovalTag, SendlyTag) { it == id }

    /**
     * Takes down approval request notifications posted before [since] (epoch millis) whose request
     * is not in [pending]: decided on another device, withdrawn or expired. Newer ones are left
     * alone, their request may not have been in the list that was just loaded yet.
     */
    fun dismissApprovalsExcept(pending: Set<String>, since: Long) = dismiss(ApprovalTag, before = since) { it !in pending }

    /** The same for Sendly device requests. */
    fun dismissSendlyExcept(pending: Set<String>, since: Long) = dismiss(SendlyTag, before = since) { it !in pending }

    private fun dismiss(vararg tags: String, before: Long = Long.MAX_VALUE, which: (key: String) -> Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val active = runCatching { manager.activeNotifications }.getOrNull() ?: return
        active.filter { it.tag in tags && it.postTime < before }
            .filter { which(it.notification.extras.getString(KeyExtra).orEmpty()) }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    private fun post(channel: String, tag: String, key: String, title: String, body: String, timeoutMillis: Long? = null) {
        if (!canPost()) return
        val id = key.hashCode()
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(if (channel == ApprovalChannel) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_STATUS)
            .setPriority(if (channel == ApprovalChannel) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addExtras(Bundle().apply { putString(KeyExtra, key) })
            .apply { if (timeoutMillis != null) setTimeoutAfter(timeoutMillis) }
            .build()
        @Suppress("MissingPermission") // checked in canPost()
        NotificationManagerCompat.from(context).notify(tag, id, notification)
    }

    private companion object {
        const val AccountChannel = "account_events"
        const val ApprovalChannel = "approvals"
        const val UpdateChannel = "app_updates"
        const val AccountTag = "account"
        const val ApprovalTag = "approval"
        const val SendlyTag = "sendly"
        const val UpdateTag = "update"
        // What a notification is about (event name or request id), to find it again.
        const val KeyExtra = "com.cns.accounts.notification_key"
        const val ApprovalTimeoutMillis = 120_000L
    }
}
