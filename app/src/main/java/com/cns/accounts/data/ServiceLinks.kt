package com.cns.accounts.data

import android.os.Build
import com.cns.accounts.data.api.ApiException
import com.cns.accounts.data.approvals.ApprovalCancelledException
import com.cns.accounts.data.approvals.UserVerification
import com.cns.accounts.data.push.Notifier
import com.cns.accounts.data.sendly.SendlyApi
import com.cns.accounts.data.sendly.SendlyCrypto
import com.cns.accounts.data.sendly.SendlyEnrollment
import com.cns.accounts.data.sendly.SendlyPendingEnrollment
import com.cns.accounts.data.sendly.SendlyRegistration
import com.cns.accounts.data.sendly.SendlySession
import com.cns.accounts.data.session.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/**
 * What this app adds to a connected service beyond auth's connected-apps list. Only Sendly has
 * anything: once set up, this app is a trusted Sendly device of kind "approver" (Sendly
 * `docs/CNS_ACCOUNTS_APPROVER.md`) whose only job is to approve or deny new Sendly devices. Allow
 * hands the new device the account's Sendly identity key, wrapped for its public key, exactly
 * like approving from a browser; the app itself never reads files.
 *
 * Per account, [SecureStore] (Keystore-encrypted) keeps this device's RSA key and, once a trusted
 * device approved the app, the identity private key. Using that key asks for the screen lock when
 * the app's biometric setting is on.
 */
class ServiceLinks(
    private val account: AccountRepository,
    private val session: SendlySession,
    private val store: SecureStore,
    private val preferences: AppPreferences,
    private val verification: UserVerification,
    private val notifier: Notifier,
    scope: CoroutineScope,
) {
    private val sendlyApi = SendlyApi(session)
    private val _sendly = MutableStateFlow(SendlyLink())
    val sendly: StateFlow<SendlyLink> = _sendly.asStateFlow()

    init {
        scope.launch {
            account.user.collect { user ->
                if (user == null) _sendly.value = SendlyLink() else _sendly.update { it.copy(isSetUp = identity(user.id) != null) }
            }
        }
    }

    /**
     * Set Up: registers this app as an approver device. If Sendly already trusts it the key comes
     * back at once; otherwise this asks for an enrollment, publishes its code in
     * [SendlyLink.enrollment] and waits until a trusted device approves it (or it expires, is
     * denied, or the caller cancels, see [abortSendlySetup]).
     */
    suspend fun beginSendlySetup(onWaiting: () -> Unit = {}) {
        val userId = currentUserId()
        session.onWaiting = onWaiting
        val device = device(userId) ?: newDevice(userId)
        var registration = register(device)
        if (registration.needsIdentitySetup) throw SendlyNotReadyException()
        if (registration.envelope == null) {
            val enrollment = sendlyApi.createEnrollment(device.id)
            _sendly.update { it.copy(enrollment = enrollment) }
            try {
                registration = awaitEnrollment(device, enrollment)
            } finally {
                _sendly.update { it.copy(enrollment = null) }
            }
        }
        keepIdentity(userId, device, registration)
        _sendly.update { it.copy(isSetUp = true) }
        refresh()
    }

    /** Abort while waiting: withdraws this app's own enrollment. */
    suspend fun abortSendlySetup() {
        val enrollment = _sendly.value.enrollment ?: return
        val device = device(currentUserId()) ?: return
        runCatching { sendlyApi.cancelEnrollment(enrollment.id, device.id) }
        _sendly.update { it.copy(enrollment = null) }
    }

    /** Reloads the Sendly devices waiting for approval (shown on the Sendly row). */
    suspend fun refresh() {
        val userId = account.user.value?.id ?: return
        val device = device(userId)
        if (identity(userId) == null || device == null) {
            _sendly.update { it.copy(pendingLogin = null, pending = emptyList()) }
            return
        }
        // Our own enrollment (during setup) is in the same list; it isn't a request for us.
        val pending = sendlyApi.pendingEnrollments().filter { it.requestDeviceId != device.id }
        _sendly.update { link ->
            link.copy(
                pending = pending,
                pendingLogin = pending.firstOrNull()?.let { ServiceLoginRequest(it.id, it.deviceLabel, it.verificationCode, it.createdAt) },
            )
        }
    }

    /** Allow hands the identity key to the new device; Deny rejects it. */
    suspend fun respondToSendlyLogin(request: ServiceLoginRequest, approve: Boolean) {
        val userId = currentUserId()
        val device = device(userId) ?: throw SendlyNotSetUpException()
        val enrollment = _sendly.value.pending.firstOrNull { it.id == request.id } ?: return
        if (approve) {
            val identity = identity(userId) ?: throw SendlyNotSetUpException()
            if (preferences.biometricUnlock.value && !verification.confirm()) throw ApprovalCancelledException()
            val publicJwk = enrollment.devicePublicJwk ?: throw ApiException(200, "bad_response", "The new device sent no key")
            val wrapped = withContext(Dispatchers.Default) { SendlyCrypto.wrapIdentityKey(identity.jwk, publicJwk) }
            sendlyApi.approve(enrollment, device.id, wrapped, identity.version)
        } else {
            sendlyApi.reject(enrollment.id, device.id)
        }
        notifier.cancel(enrollment.id)
        refresh()
    }

    /** Disconnect: Sendly forgets this device and its key copy, and so does the app. */
    suspend fun disconnectSendly() {
        val userId = currentUserId()
        device(userId)?.let { device -> runCatching { sendlyApi.removeDevice(device.id) } }
        store.put(deviceKey(userId), null)
        store.put(identityKey(userId), null)
        session.forget(userId)
        _sendly.value = SendlyLink()
    }

    private suspend fun awaitEnrollment(device: StoredDevice, enrollment: SendlyEnrollment): SendlyRegistration {
        while (true) {
            delay(PollMillis)
            // Approved: registering again returns the key. Still listed: keep waiting.
            val registration = register(device)
            if (registration.envelope != null) return registration
            val stillPending = sendlyApi.pendingEnrollments().any { it.id == enrollment.id }
            val expired = enrollment.expiresAt?.isBefore(Instant.now()) == true
            if (!stillPending || expired) throw SendlyEnrollmentEndedException(expired)
        }
    }

    private suspend fun register(device: StoredDevice): SendlyRegistration =
        sendlyApi.register(device.id, "CNS Accounts on ${deviceModel()}", device.publicJwk)

    private suspend fun keepIdentity(userId: Long, device: StoredDevice, registration: SendlyRegistration) {
        val envelope = registration.envelope ?: throw SendlyNotReadyException()
        val jwk = withContext(Dispatchers.Default) {
            SendlyCrypto.unwrapIdentityKey(envelope, SendlyCrypto.decodePrivateKey(device.privateKey))
        }
        // The key must belong to the account's active identity public key.
        val expectedModulus = registration.identityPublicJwk?.optString("n")
        if (expectedModulus != null && JSONObject(jwk).optString("n") != expectedModulus) {
            throw ApiException(200, "identity_key_mismatch", "Sendly handed over a key that doesn't match the account")
        }
        store.put(identityKey(userId), JSONObject().put("jwk", jwk).put("version", registration.identityKeyVersion).toString())
    }

    private suspend fun newDevice(userId: Long): StoredDevice {
        val pair = withContext(Dispatchers.Default) { SendlyCrypto.generateDeviceKey() }
        val device = StoredDevice(UUID.randomUUID().toString(), SendlyCrypto.encodePrivateKey(pair.private), SendlyCrypto.publicJwk(pair.public))
        store.put(
            deviceKey(userId),
            JSONObject().put("id", device.id).put("private_key", device.privateKey).put("public_jwk", device.publicJwk).toString(),
        )
        return device
    }

    private fun device(userId: Long): StoredDevice? = store.get(deviceKey(userId))?.let { raw ->
        runCatching {
            val json = JSONObject(raw)
            StoredDevice(json.getString("id"), json.getString("private_key"), json.getJSONObject("public_jwk"))
        }.getOrNull()
    }

    private fun identity(userId: Long): StoredIdentity? = store.get(identityKey(userId))?.let { raw ->
        runCatching { JSONObject(raw).let { StoredIdentity(it.getString("jwk"), it.getInt("version")) } }.getOrNull()
    }

    private suspend fun currentUserId(): Long = (account.user.value ?: account.refreshProfile()).id

    private fun deviceModel(): String =
        if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "${Build.MANUFACTURER} ${Build.MODEL}"

    private fun deviceKey(userId: Long) = "sendly_device_$userId"
    private fun identityKey(userId: Long) = "sendly_identity_$userId"

    private class StoredDevice(val id: String, val privateKey: String, val publicJwk: JSONObject)
    private class StoredIdentity(val jwk: String, val version: Int)

    private companion object {
        const val PollMillis = 3_000L
    }
}

/**
 * This app's link to the user's Sendly: whether it is a trusted device yet, a sign-in waiting on
 * it, and (during Set Up) this app's own enrollment waiting for another device.
 */
data class SendlyLink(
    val isSetUp: Boolean = false,
    val pendingLogin: ServiceLoginRequest? = null,
    val enrollment: SendlyEnrollment? = null,
    internal val pending: List<SendlyPendingEnrollment> = emptyList(),
)

class SendlyNotSetUpException : Exception("Sendly is not set up in this app")

/** The account has no Sendly encryption key yet (or one from before identity keys). */
class SendlyNotReadyException : Exception("Sendly encryption is not set up yet")

/** This app's enrollment ended without an approval: [expired], or denied/withdrawn. */
class SendlyEnrollmentEndedException(val expired: Boolean) : Exception("Sendly enrollment ended")

/** auth-v2 has no stable id list for first-party apps; Sendly is matched by client id or name. */
fun isSendly(clientId: String, name: String): Boolean =
    clientId.equals("sendly", ignoreCase = true) || name.equals("Sendly", ignoreCase = true)
