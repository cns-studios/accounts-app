package com.cns.accounts

import android.content.Context
import com.cns.accounts.data.AccountRepository
import com.cns.accounts.data.AppPreferences
import com.cns.accounts.data.SignInRequests
import com.cns.accounts.data.UserMessages
import com.cns.accounts.data.api.CnsApi
import com.cns.accounts.data.api.HttpTransport
import com.cns.accounts.data.authenticator.AuthenticatorStore
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
    val preferences = AppPreferences(context)
    val signInRequests = SignInRequests()
    val messages = UserMessages()
    val appLock = AppLock(preferences)

    init {
        // Whatever ended the session (sign-out, account deletion, a refresh token the server
        // rejected), forget the account data. The authenticator key stays: it is not tied to
        // the session.
        appScope.launch {
            session.authState.drop(1).filter { it == AuthState.SignedOut }.collect {
                account.clear()
            }
        }
    }
}

/**
 * Biometric app lock: when enabled, the app is locked on a cold start and after it has been in
 * the background for [RelockAfterMillis]. Purely device-local; the API knows nothing about it.
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
        if (preferences.biometricUnlock.value && backgroundedAt != 0L && nowMillis - backgroundedAt >= RelockAfterMillis) {
            _locked.value = true
        }
    }

    private companion object {
        const val RelockAfterMillis = 30_000L
    }
}
