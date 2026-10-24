package com.cns.accounts.data.push

import com.cns.accounts.CnsApplication
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives auth-v2's data pushes. They carry ids only (Firebase never sees account data):
 * account events become a notification when Notifications is on; approval requests (and Sendly
 * device requests) always do, and screens that are open refresh from [PushRegistrar.events].
 */
class PushService : FirebaseMessagingService() {
    private val container get() = (application as CnsApplication).container

    override fun onNewToken(token: String) {
        container.push.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        container.push.onMessage(data)
        when (data["type"]) {
            "account_event" -> if (container.preferences.notifications.value) container.notifier.accountEvent(data["event"].orEmpty())
            "approval_request" -> data["id"]?.let(container.notifier::approvalRequest)
            "service_event" -> if (data["event"] == "sendly_enrollment") data["id"]?.let(container.notifier::sendlyEnrollment)
        }
    }
}
