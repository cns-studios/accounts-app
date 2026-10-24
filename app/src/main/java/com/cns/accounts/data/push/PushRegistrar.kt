package com.cns.accounts.data.push

import android.content.Context
import android.util.Log
import com.cns.accounts.BuildConfig
import com.cns.accounts.data.AppPreferences
import com.cns.accounts.data.api.CnsApi
import com.cns.accounts.data.session.AuthState
import com.cns.accounts.data.session.SessionManager
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A data push from auth-v2: ids only; the details are loaded over the API. */
data class PushEvent(val data: Map<String, String>) {
    val type: String get() = data["type"].orEmpty()
}

/**
 * Keeps auth-v2 told where to push this session (`PUT /api/me/push`). Push is Firebase Cloud
 * Messaging, set up from build properties instead of google-services.json (see
 * app/build.gradle.kts); a build without them simply has no push. Account-event pushes follow
 * the Notifications setting; sign-in approval pushes always come.
 */
class PushRegistrar(
    private val context: Context,
    private val api: CnsApi,
    private val session: SessionManager,
    private val preferences: AppPreferences,
    private val scope: CoroutineScope,
) {
    val isConfigured: Boolean = listOf(
        BuildConfig.FIREBASE_APP_ID, BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_PROJECT_ID, BuildConfig.FIREBASE_SENDER_ID,
    ).all { it.isNotBlank() }

    private val _events = MutableSharedFlow<PushEvent>(extraBufferCapacity = 8)

    /** Pushes received while the app process runs, for screens that want to refresh. */
    val events: SharedFlow<PushEvent> = _events.asSharedFlow()

    fun start() {
        if (!isConfigured) return
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build(),
            )
        }
        scope.launch {
            combine(session.authState, preferences.notifications) { auth, events -> auth to events }
                .distinctUntilChanged()
                .collect { (auth, _) -> if (auth == AuthState.SignedIn) sync() }
        }
    }

    fun onNewToken(token: String) {
        scope.launch { sync(token) }
    }

    fun onMessage(data: Map<String, String>) {
        _events.tryEmit(PushEvent(data))
    }

    private suspend fun sync(knownToken: String? = null) {
        if (session.authState.value != AuthState.SignedIn) return
        runCatching {
            val token = knownToken ?: currentToken()
            api.registerPush(token, accountEvents = preferences.notifications.value)
        }.onFailure { Log.w(Tag, "push registration failed: ${it.message}") }
    }

    private suspend fun currentToken(): String = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private companion object {
        const val Tag = "CnsPush"
    }
}
