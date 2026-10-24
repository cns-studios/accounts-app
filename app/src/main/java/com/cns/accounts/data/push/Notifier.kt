package com.cns.accounts.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cns.accounts.MainActivity
import com.cns.accounts.R

/** Android notifications for account events and requests waiting for this app's approval. */
class Notifier(private val context: Context) {
    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(AccountChannel, context.getString(R.string.notification_channel_account), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(ApprovalChannel, context.getString(R.string.notification_channel_approvals), NotificationManager.IMPORTANCE_HIGH),
        )
    }

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
            else -> return
        }
        post(AccountChannel, event.hashCode(), context.getString(title), context.getString(R.string.notify_account_body))
    }

    /** A sign-in or change waiting for this app's approval (Require Security App). */
    fun approvalRequest(id: String) =
        post(ApprovalChannel, id.hashCode(), context.getString(R.string.notify_approval_title), context.getString(R.string.notify_approval_body))

    /** A new Sendly device waiting for this app's approval. */
    fun sendlyEnrollment(id: String) =
        post(ApprovalChannel, id.hashCode(), context.getString(R.string.notify_sendly_title), context.getString(R.string.notify_sendly_body))

    fun cancel(id: String) = NotificationManagerCompat.from(context).cancel(id.hashCode())

    private fun post(channel: String, id: Int, title: String, body: String) {
        if (!canPost()) return
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
            .build()
        @Suppress("MissingPermission") // checked in canPost()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private companion object {
        const val AccountChannel = "account_events"
        const val ApprovalChannel = "approvals"
    }
}
