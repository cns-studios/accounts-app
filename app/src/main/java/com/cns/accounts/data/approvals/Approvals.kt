package com.cns.accounts.data.approvals

import android.os.Build
import android.util.Base64
import com.cns.accounts.data.AccountRepository
import com.cns.accounts.data.AppPreferences
import com.cns.accounts.data.api.AccountUser
import com.cns.accounts.data.api.ApiException
import com.cns.accounts.data.api.ApprovalChallenge
import com.cns.accounts.data.api.ApprovalPoll
import com.cns.accounts.data.api.CnsApi
import com.cns.accounts.data.api.PendingApproval
import com.cns.accounts.data.push.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Require Security App on this device (auth-v2 `docs/APPROVALS.md`): turning it on makes this
 * phone the account's approver, after which sign-ins and sensitive changes anywhere wait for a
 * decision signed here. This class lists the requests waiting for that decision (the Home
 * request card), signs them, approves the app's own sign-in and account changes, and turns the
 * requirement on and off.
 */
class Approvals(
    private val api: CnsApi,
    private val account: AccountRepository,
    private val keys: ApproverKeys,
    private val verification: UserVerification,
    private val preferences: AppPreferences,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
) {
    private val keyChanges = MutableStateFlow(0)
    private val _pending = MutableStateFlow<List<PendingApproval>>(emptyList())
    private val _approverElsewhere = MutableStateFlow<String?>(null)
    private var expiryJob: Job? = null

    // Requests this device raised and decides itself (gated): never a card or a notification.
    private val decidedHere: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Requests waiting for this device, oldest first. */
    val pending: StateFlow<List<PendingApproval>> = _pending.asStateFlow()

    /**
     * The approver's device name when Require Security App is on but another device (or an
     * earlier install of this app) holds the key: requests go there, not to this app.
     */
    val approverElsewhere: StateFlow<String?> = _approverElsewhere.asStateFlow()

    /** Whether this device is the signed-in account's approver. */
    val isApprover: StateFlow<Boolean> = combine(account.user, keyChanges) { user, _ -> isApproverFor(user) }
        .stateIn(scope, SharingStarted.Eagerly, isApproverFor(account.user.value))

    init {
        scope.launch { account.user.collect(::reconcile) }
    }

    /** Reloads the waiting requests; also notices when this device stopped being the approver. */
    suspend fun refresh() {
        val user = account.user.value
        if (user == null || !isApproverFor(user)) {
            _pending.value = emptyList()
            _approverElsewhere.value = if (user?.requireAppApproval == true) api.approver().deviceName.orEmpty() else null
            return
        }
        _approverElsewhere.value = null
        val server = api.approver()
        if (!server.enabled || server.deviceId != keys.deviceId(user.id)) {
            // Turned off (the TOTP fallback, or from the web) or moved to another device.
            forget(user.id)
            return
        }
        setPending(api.pendingApprovals())
    }

    /** Whether a pushed request [id] is one for this device to show (it is the approver, and didn't raise it itself). */
    fun shouldNotify(id: String): Boolean = isApprover.value && id !in decidedHere

    /** Allow or Deny on the request card. */
    suspend fun respond(request: PendingApproval, approve: Boolean) {
        val user = account.user.value ?: return
        try {
            decide(user.id, request.id, request.action, request.nonce, request.expiresAtUnix, approve)
        } catch (e: ApiException) {
            // 409: expired or already decided, so it's gone too. Other failures keep the card.
            if (e.status == 409 || e.status == 404) drop(request.id)
            throw e
        }
        drop(request.id)
    }

    private fun drop(id: String) {
        setPending(_pending.value.filterNot { it.id == id })
        notifier.cancel(id)
    }

    /**
     * Runs an account change that Require Security App may hold back: [action] is tried without
     * an approval; if auth-v2 asks for one, this device approves it itself when it is the
     * approver (after the user check), or else waits for the approver; then [action] runs again
     * with the approval. [onWaiting] is called when the decision has to come from another device.
     */
    suspend fun <T> gated(onWaiting: () -> Unit = {}, action: suspend (approvalId: String?) -> T): T {
        val challenge = try {
            return action(null)
        } catch (e: ApiException) {
            e.approval?.takeIf { e.code == "approval_required" } ?: throw e
        }
        approveOrAwait(challenge, onWaiting)
        return action(challenge.id)
    }

    /**
     * Gets [challenge] (an action of the signed-in account) approved: by this device itself when it
     * is the approver, otherwise by waiting for the approver ([onWaiting] first). Returns the poll
     * result, which for a single sign-on carries the code.
     */
    suspend fun approveOrAwait(challenge: ApprovalChallenge, onWaiting: () -> Unit = {}): ApprovalPoll {
        val user = account.user.value
        if (user != null && keys.has(user.id)) {
            // The user is right here asking for it: approve in place, no card or notification.
            decidedHere += challenge.id
            try {
                decide(user.id, challenge.id, challenge.action, challenge.nonce, challenge.expiresAtUnix, approve = true)
            } finally {
                drop(challenge.id)
            }
        } else {
            onWaiting()
        }
        return awaitDecision(challenge)
    }

    /** The account id this device can approve a sign-in of [username] for, if it holds that key. */
    fun approverFor(username: String): Long? = keys.userIdFor(username.trim())

    /** Approves this app's own sign-in for [userId] (no session yet: the signature is enough). */
    suspend fun approveOwnSignIn(userId: Long, challenge: ApprovalChallenge) =
        decide(userId, challenge.id, challenge.action, challenge.nonce, challenge.expiresAtUnix, approve = true)

    /** Polls [challenge] until it is decided: returns on approval, throws on deny or expiry. */
    suspend fun awaitDecision(challenge: ApprovalChallenge): ApprovalPoll {
        while (true) {
            when (val result = api.pollApproval(challenge, wait = true)) {
                ApprovalPoll.Pending -> if (System.currentTimeMillis() / 1000 > challenge.expiresAtUnix) throw ApprovalExpiredException()
                ApprovalPoll.Approved, is ApprovalPoll.Completed -> return result
                ApprovalPoll.Denied -> throw ApprovalDeniedException()
                ApprovalPoll.Gone -> throw ApprovalExpiredException()
            }
        }
    }

    /**
     * Turns Require Security App on with this device as the approver. auth-v2 wants the password
     * and a current 2FA code (2FA must be on: it is the way back in if this phone is lost).
     */
    suspend fun enable(password: String, code: String) {
        val user = account.user.value ?: account.refreshProfile()
        val challenge = api.approverChallenge()
        // Bound to the screen lock when the app lock is on, so a decision needs the owner too.
        val userAuth = preferences.biometricUnlock.value
        val key = withContext(Dispatchers.Default) { keys.create(user.id, Base64.decode(challenge, Base64.NO_WRAP), userAuth) }
        try {
            if (userAuth) confirmUser()
            val signature = withContext(Dispatchers.Default) { keys.sign(user.id, enrollMessage(user.id, challenge)) }
            val info = api.enableApprover(key.publicKey, key.attestation, deviceName(), password, code, signature)
            keys.saveEnrollment(user.id, user.username, info.deviceId.orEmpty())
        } catch (e: Throwable) {
            keys.remove(user.id)
            throw e
        }
        keyChanges.update { it + 1 }
        runCatching { account.refreshProfile() }
    }

    /** Turns it off; the server wants that approved too, which this device does itself. */
    suspend fun disable(onWaiting: () -> Unit = {}) {
        val user = account.user.value ?: return
        gated(onWaiting) { approvalId -> api.disableApprover(approvalId) }
        forget(user.id)
        runCatching { account.refreshProfile() }
    }

    private suspend fun decide(userId: Long, id: String, action: String, nonce: String, expiresAtUnix: Long, approve: Boolean) {
        // The key may demand it; the app lock setting asks for it either way.
        if (keys.requiresUserAuth(userId) || preferences.biometricUnlock.value) confirmUser()
        val message = approvalMessage(id, userId, action, nonce, approve, expiresAtUnix)
        val signature = withContext(Dispatchers.Default) { keys.sign(userId, message) }
        api.decideApproval(id, approve, signature)
    }

    private suspend fun confirmUser() {
        if (!verification.confirm()) throw ApprovalCancelledException()
    }

    private fun setPending(list: List<PendingApproval>) {
        val now = System.currentTimeMillis() / 1000
        val live = list.filter { it.expiresAtUnix > now && it.id !in decidedHere }.sortedBy { it.expiresAtUnix }
        _pending.value = live
        // Drop each card when its request expires.
        expiryJob?.cancel()
        val next = live.firstOrNull() ?: return
        expiryJob = scope.launch {
            delay((next.expiresAtUnix - now).coerceAtLeast(1) * 1000)
            setPending(_pending.value)
        }
    }

    private fun forget(userId: Long) {
        keys.remove(userId)
        keyChanges.update { it + 1 }
        setPending(emptyList())
    }

    /** Keeps the local key in step with the account: renamed, or retired once it was turned off. */
    private fun reconcile(user: AccountUser?) {
        user ?: return setPending(emptyList())
        if (!keys.has(user.id)) return
        keys.rename(user.id, user.username)
        // A profile fetched just before an enrollment would still say "off"; spare a fresh key.
        val fresh = System.currentTimeMillis() - keys.enrolledAt(user.id) < FreshEnrollmentMillis
        if (!user.requireAppApproval && !fresh) forget(user.id)
    }

    private fun isApproverFor(user: AccountUser?): Boolean = user != null && user.requireAppApproval && keys.has(user.id)

    private fun deviceName(): String {
        val model = if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "${Build.MANUFACTURER} ${Build.MODEL}"
        return model.replaceFirstChar { it.uppercase() }
    }

    private companion object {
        const val FreshEnrollmentMillis = 120_000L
    }
}

/**
 * Asks whoever holds the phone to prove it's the owner (strong biometric or the screen lock)
 * before the approver key signs. [MainActivity][com.cns.accounts.MainActivity] provides the
 * prompt while it exists; without it nothing can be confirmed.
 */
class UserVerification {
    @Volatile
    var prompt: (suspend () -> Boolean)? = null

    suspend fun confirm(): Boolean = prompt?.invoke() ?: false
}

class ApprovalDeniedException : Exception("The request was denied in the CNS Accounts app")
class ApprovalExpiredException : Exception("The request expired before it was approved")
class ApprovalCancelledException : Exception("Not confirmed")
