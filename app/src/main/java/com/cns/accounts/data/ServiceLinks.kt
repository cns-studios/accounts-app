package com.cns.accounts.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What this app adds to a connected service beyond auth's connected-apps list. Only Sendly has
 * anything: once set up, this app becomes a trusted Sendly device that approves or denies new
 * Sendly sign-ins (Sendly's device enrollment, which hands the account's encryption key to the
 * new device), shown as a login request on the Sendly row.
 *
 * NOT BACKED YET: setting up means registering this app as a Sendly device and holding the
 * account's Sendly identity key, which needs the plan in docs/backend_plans.md agreed first.
 * Until then Sendly always reads "not set up" (the orange dot), there is never a login request,
 * and [beginSendlySetup] / [respondToSendlyLogin] report "not available yet".
 */
class ServiceLinks {
    private val _sendly = MutableStateFlow(SendlyLink())
    val sendly: StateFlow<SendlyLink> = _sendly.asStateFlow()

    @Suppress("RedundantSuspendModifier")
    suspend fun beginSendlySetup(): Nothing = throw UnsupportedFeatureException()

    @Suppress("RedundantSuspendModifier", "UNUSED_PARAMETER")
    suspend fun respondToSendlyLogin(request: ServiceLoginRequest, approve: Boolean): Nothing = throw UnsupportedFeatureException()
}

/** This app's link to the user's Sendly: whether it is a trusted device yet, and a sign-in waiting on it. */
data class SendlyLink(
    val isSetUp: Boolean = false,
    val pendingLogin: ServiceLoginRequest? = null,
)

/** auth-v2 has no stable id list for first-party apps; Sendly is matched by client id or name. */
fun isSendly(clientId: String, name: String): Boolean =
    clientId.equals("sendly", ignoreCase = true) || name.equals("Sendly", ignoreCase = true)
