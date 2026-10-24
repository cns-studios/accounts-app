package com.cns.accounts

import android.content.Context
import com.cns.accounts.data.AccountRepository
import com.cns.accounts.data.AppPreferences
import com.cns.accounts.data.SecurityMilestones
import com.cns.accounts.data.ServiceLinks
import com.cns.accounts.data.UserMessages
import com.cns.accounts.data.api.CnsApi
import com.cns.accounts.data.api.HttpTransport
import com.cns.accounts.data.approvals.ApproverKeys
import com.cns.accounts.data.approvals.Approvals
import com.cns.accounts.data.approvals.UserVerification
import com.cns.accounts.data.authenticator.AuthenticatorStore
import com.cns.accounts.data.authenticator.TwoFactorManager
import com.cns.accounts.data.push.Notifier
import com.cns.accounts.data.push.PushRegistrar
import com.cns.accounts.data.sendly.SendlySession
import com.cns.accounts.data.session.AuthState
import com.cns.accounts.data.session.SecureStore
import com.cns.accounts.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Hand-rolled dependency graph: one instance of each app-wide object, created in [CnsApplication]. */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val secureStore = SecureStore(context)
    private val transport = HttpTransport()

    val session = SessionManager(transport, secureStore)
    val api = CnsApi(transport, session)
    val account = AccountRepository(context, api, session)
    val authenticator = AuthenticatorStore(secureStore)
    val twoFactor = TwoFactorManager(api, account, authenticator, appScope)
    val preferences = AppPreferences(context)
    val securityMilestones = SecurityMilestones(preferences, account, twoFactor, appScope)
    val messages = UserMessages()
    val appLock = AppLock(preferences)
    val notifier = Notifier(context)
    val push = PushRegistrar(context, api, session, preferences, appScope)
    val verification = UserVerification()
    val approvals = Approvals(api, account, ApproverKeys(context), verification, preferences, notifier, appScope)
    val serviceLinks = ServiceLinks(
        account, SendlySession(api, account, approvals, secureStore), secureStore, preferences, verification, notifier, appScope,
    )

    init {
        // Whatever ended the session (sign-out, account deletion, a refresh token the server
        // rejected), forget the account data. Authenticator keys stay: they belong to the
        // account, not the session, and are needed to sign back in.
        appScope.launch {
            session.authState.drop(1).filter { it == AuthState.SignedOut }.collect {
                account.clear()
            }
        }
        push.start()
        // Pushes are ids only: reload what they point at.
        appScope.launch {
            push.events.collect { event ->
                when (event.type) {
                    "approval_request" -> runCatching { approvals.refresh() }
                    "account_event" -> runCatching { account.refreshProfile() }
                    "service_event" -> runCatching { serviceLinks.refresh() }
                }
            }
        }
    }

    /** The app came to the foreground: requests may have arrived without a push. */
    fun onForeground() {
        if (session.authState.value != AuthState.SignedIn) return
        appScope.launch { runCatching { approvals.refresh() } }
        appScope.launch { runCatching { serviceLinks.refresh() } }
    }
}

/**
 * Biometric app lock: when enabled, the app is locked on a cold start and after it has been in
 * the background for the chosen delay ([AppPreferences.lockAfterMillis]). Purely device-local; the API knows nothing about it.
 */
class AppLock(private val preferences: AppPreferences) {
    private val _locked = MutableStateFlow(preferences.biometricUnlock.value)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundedAt = 0L

    fun unlock() {
        _locked.value = false
    }

    fun onBackground(nowMillis: Long) {
        backgroundedAt = nowMillis
    }

    fun onForeground(nowMillis: Long) {
        if (preferences.biometricUnlock.value && backgroundedAt != 0L && nowMillis - backgroundedAt >= preferences.lockAfterMillis.value) {
            _locked.value = true
        }
    }
}
