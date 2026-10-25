package com.cns.accounts.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Which security center factors the user still has to be shown as newly completed (the
 * particle burst on their bar, see ui/settings/SecurityCenter.kt). Each completion is shown
 * once; turning the feature behind a factor off forgets that, so turning it on again shows it
 * again. Tracked here, app-wide, rather than in the screen, so a factor turned off and on while
 * Settings is closed still counts. The first state ever recorded is taken as already seen.
 */
class SecurityMilestones(
    private val preferences: AppPreferences,
    account: AccountRepository,
    scope: CoroutineScope,
) {
    val celebrated: StateFlow<Set<String>?> = preferences.celebratedFactors

    init {
        scope.launch {
            account.user.collect { user ->
                val status = user?.let { securityStatus(twoFactorEnabled = it.totpEnabled, securityApp = it.requireAppApproval, recoveryPhrase = it.recoveryPhraseEnabled) }
                // Signed out: nothing changed on the account, keep what was seen.
                status ?: return@collect
                val achieved = status.factors.filter { it.achieved }.mapTo(HashSet()) { it.id }
                val seen = preferences.celebratedFactors.value
                preferences.setCelebratedFactors(if (seen == null) achieved else seen intersect achieved)
            }
        }
    }

    fun markCelebrated(factorId: String) {
        val seen = preferences.celebratedFactors.value ?: return
        preferences.setCelebratedFactors(seen + factorId)
    }
}
