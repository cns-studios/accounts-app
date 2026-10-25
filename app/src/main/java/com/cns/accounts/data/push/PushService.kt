package com.cns.accounts.data.push

import com.cns.accounts.CnsApplication
import com.cns.accounts.data.NotificationEvent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives auth-v2's data pushes. They carry ids only (Firebase never sees account data):
 * account events become a notification when Notifications is on and that kind isn't muted; approval requests (and Sendly
 * device requests) always do, and screens that are open refresh from [PushRegistrar.events].
 */
class PushService : FirebaseMessagingService() {
    private val container get() = (application as CnsApplication).container

    // Registration tokens, see PushRegistrar.currentToken.
    @Deprecated("Registration-token messaging; see PushRegistrar")
    override fun onNewToken(token: String) {
        container.push.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        container.push.onMessage(data)
        when (data["type"]) {
            "account_event" -> {
                val event = data["event"].orEmpty()
                val muted = NotificationEvent.forEvent(event)?.id in container.preferences.mutedEvents.value
                if (container.preferences.notifications.value && !muted) container.notifier.accountEvent(event)
            }
            // Every signed-in device gets the push; only the approver can answer it.
            "approval_request" -> data["id"]?.takeIf(container.approvals::shouldNotify)?.let(container.notifier::approvalRequest)
            "service_event" -> if (data["event"] == "sendly_enrollment") data["id"]?.let(container.notifier::sendlyEnrollment)
        }
    }
}
