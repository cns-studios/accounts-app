package com.cns.accounts.data.authenticator

import com.cns.accounts.data.AccountRepository
import com.cns.accounts.data.ActivityFeed
import com.cns.accounts.data.api.AccountUser
import com.cns.accounts.data.api.ActivityEntry
import com.cns.accounts.data.api.CnsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the signed-in account's 2FA stands from this device's point of view. */
sealed interface TwoFactorState {
    /** No profile loaded yet. */
    data object Unknown : TwoFactorState

    /** 2FA is off on the account. */
    data object NotSetUp : TwoFactorState

    /** 2FA is on and this device holds the account's current key. */
    data class Active(val secret: String) : TwoFactorState

    /**
     * 2FA is on but its key lives somewhere else: another device running this app, another
     * authenticator app, or this device's key was replaced by a transfer to another device.
     */
    data object Elsewhere : TwoFactorState
}

/**
 * The app as the CNS account's authenticator. Enabling 2FA here keeps the key on this device
 * and confirms it with a code generated from it, so there is nothing for the user to type;
 * "transfer" re-keys the account onto this device (auth-v2 has no other way to move a key).
 */
class TwoFactorManager(
    private val api: CnsApi,
    private val account: AccountRepository,
    private val store: AuthenticatorStore,
    scope: CoroutineScope,
) {
    val state: StateFlow<TwoFactorState> = combine(account.user, store.entries, account.activity) { user, _, feed ->
        resolve(user, feed)
    }.stateIn(scope, SharingStarted.Eagerly, resolve(account.user.value, account.activity.value))

    init {
        scope.launch { account.user.collect(::reconcile) }
    }

    /** The current code for the signed-in account, if this device holds its key. */
    fun currentCode(): String? = liveEntry()?.codeAt(System.currentTimeMillis())

    /** Turns 2FA on with this app as the authenticator. */
    suspend fun enable() {
        val user = account.user.value ?: account.refreshProfile()
        val setup = api.setupTwoFactor()
        val code = Totp.decodeBase32(setup.secret)?.let { Totp.code(it, System.currentTimeMillis()) }
            ?: error("auth returned an unreadable 2FA key")
        val enrolledAt = api.confirmTwoFactor(code)
        // Key first, then the profile flag, so the card never sees "on, but no key here".
        store.save(AuthenticatorEntry(setup.secret, user.id, user.username, enrolledAt))
        account.markTwoFactor(enabled = true)
    }

    /** Turns 2FA off; [code] may be blank when this device holds the key. */
    suspend fun disable(password: String, code: String) {
        account.disableTwoFactor(password, code.ifBlank { currentCode().orEmpty() })
        account.user.value?.let { store.remove(it.id) }
    }

    /**
     * Moves the account's 2FA onto this device: turns it off with the password and a code from
     * the device that has it now, then on again with a new key kept here. The old key stops
     * working. If the second half fails, 2FA is left off and [TwoFactorMoveIncompleteException]
     * says so (Set Up finishes it).
     */
    suspend fun transferHere(password: String, code: String) {
        disable(password, code)
        val retried = runCatching { enable() }.recoverCatching { enable() }
        retried.exceptionOrNull()?.let { throw TwoFactorMoveIncompleteException(it) }
    }

    private fun liveEntry(): AuthenticatorEntry? {
        val user = account.user.value ?: return null
        return (state.value as? TwoFactorState.Active)?.let { store.entryFor(user.id) }
    }

    private fun resolve(user: AccountUser?, feed: ActivityFeed): TwoFactorState =
        resolveTwoFactorState(user, user?.let { store.entryFor(it.id) }, feed.entries)

    /** Keeps the stored keys in step with the account: drop dead ones, adopt and rename. */
    private fun reconcile(user: AccountUser?) {
        user ?: return
        // A key from an older build has no account id yet: it is this account's only if it was
        // stored for the same username.
        val entry = store.entryFor(user.id)
            ?: store.entries.value.firstOrNull { it.userId == null && it.username.equals(user.username, ignoreCase = true) }
        entry ?: return
        when {
            // Off on the account (here, on the web, on another device): the key is dead. A key
            // enrolled moments ago is spared, as the profile may have been fetched before it.
            !user.totpEnabled -> if (System.currentTimeMillis() - entry.enrolledAt > FreshEnrollmentMillis) store.remove(entry.userId)
            entry.userId == null || entry.username != user.username -> store.save(entry.copy(userId = user.id, username = user.username))
        }
    }

    private companion object {
        const val FreshEnrollmentMillis = 120_000L
    }
}

/** The card state for [user] given the key this device holds for it ([entry]) and recent [activity]. */
internal fun resolveTwoFactorState(user: AccountUser?, entry: AuthenticatorEntry?, activity: List<ActivityEntry>): TwoFactorState {
    user ?: return TwoFactorState.Unknown
    if (!user.totpEnabled) return TwoFactorState.NotSetUp
    entry ?: return TwoFactorState.Elsewhere
    return if (replacedSince(entry, activity)) TwoFactorState.Elsewhere else TwoFactorState.Active(entry.secret)
}

/**
 * Whether 2FA was (re-)enabled after [entry] was enrolled, i.e. another device took it over.
 * auth-v2 exposes no key id, so this reads the activity feed: a `2fa_enabled` event clearly after
 * our own enrollment (both server times; our own event lands within [EnrollmentEventSlackMillis]).
 * Keys from older builds have no enrollment time and are trusted.
 */
internal fun replacedSince(entry: AuthenticatorEntry, activity: List<ActivityEntry>): Boolean {
    if (entry.enrolledAt <= 0L) return false
    return activity.any { event ->
        event.eventType == "2fa_enabled" &&
            (event.createdAt?.toEpochMilli() ?: 0L) > entry.enrolledAt + EnrollmentEventSlackMillis
    }
}

private const val EnrollmentEventSlackMillis = 60_000L

/** 2FA was turned off for a transfer but couldn't be turned on again on this device. */
class TwoFactorMoveIncompleteException(cause: Throwable) : Exception(cause.message, cause)
