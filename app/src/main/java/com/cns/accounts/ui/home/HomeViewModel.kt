package com.cns.accounts.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cns.accounts.AppContainer
import com.cns.accounts.R
import com.cns.accounts.data.Account
import com.cns.accounts.data.HistoryEvent
import com.cns.accounts.data.HomeData
import com.cns.accounts.data.Profile
import com.cns.accounts.data.Service
import com.cns.accounts.data.api.ActivityEntry
import com.cns.accounts.data.authenticator.parseAuthenticatorInput
import com.cns.accounts.data.session.AuthState
import com.cns.accounts.ui.history.HistoryUiState
import com.cns.accounts.ui.history.isUserFacingEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Home + History (they share one screen and swipe between each other): the profile header,
 * the authenticator card, connected apps and the activity feed, all from [AppContainer.account]
 * and [AppContainer.authenticator].
 */
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val account = container.account
    private val messages = container.messages

    private val local = MutableStateFlow(LocalState())
    private var loadMoreJob: Job? = null

    private val homeData = combine(
        account.user,
        account.connectedApps,
        container.authenticator.entry,
        container.signInRequests.pending,
    ) { user, apps, authenticator, request ->
        HomeData(
            profile = Profile(user?.username.orEmpty(), user?.totpEnabled == true, user?.avatarUrl),
            signInRequest = request,
            account = authenticator?.let { Account(it.label, it.secret) },
            services = apps?.map { Service(it.clientId, it.name, it.lastUsed) },
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(homeData, account.activity, local) { data, feed, local ->
        HomeUiState(
            data = data,
            history = HistoryUiState(
                events = feed.entries.filter { isUserFacingEvent(it.eventType) }.map(::toHistoryEvent),
                isLoaded = feed.loaded,
                isRefreshing = local.isRefreshingHistory,
                canLoadMore = feed.nextCursor != null,
            ),
            isRefreshing = local.isRefreshing,
            selectedDestination = local.selectedDestination,
            dialog = local.dialog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // This ViewModel outlives a sign-out (it is activity-scoped), so load on every sign-in,
        // not just on creation; the StateFlow replays the current state, covering the first load.
        viewModelScope.launch {
            container.session.authState.collect { state ->
                if (state == AuthState.SignedIn) refresh(showIndicator = false)
            }
        }
    }

    fun selectDestination(destination: String) = local.update { it.copy(selectedDestination = destination) }

    /** Pull-to-refresh on Home; also runs once on start. Reloads everything Home and History show. */
    fun refresh(showIndicator: Boolean = true) {
        viewModelScope.launch {
            if (showIndicator) local.update { it.copy(isRefreshing = true) }
            val failures = listOf(
                async { runCatching { account.refreshProfile() } },
                async { runCatching { account.refreshConnectedApps() } },
                async { runCatching { account.refreshActivity() } },
            ).awaitAll().mapNotNull { it.exceptionOrNull() }
            // Three parallel calls usually fail for the same reason (offline): report it once.
            failures.firstOrNull()?.let(messages::show)
            local.update { it.copy(isRefreshing = false) }
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            local.update { it.copy(isRefreshingHistory = true) }
            runCatching { account.refreshActivity() }.onFailure(messages::show)
            local.update { it.copy(isRefreshingHistory = false) }
        }
    }

    fun loadMoreHistory() {
        if (loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch {
            runCatching { account.loadMoreActivity() }.onFailure(messages::show)
        }
    }

    // --- sign-in requests (not backed by auth-v2 yet; see SignInRequests) ---

    fun approveRequest() = respondToRequest(approve = true)
    fun denyRequest() = respondToRequest(approve = false)

    private fun respondToRequest(approve: Boolean) {
        val request = uiState.value.data.signInRequest ?: return
        viewModelScope.launch {
            runCatching { container.signInRequests.respond(request, approve) }.onFailure(messages::show)
        }
    }

    // --- authenticator card ---

    fun showAddAuthenticator() = local.update { it.copy(dialog = HomeDialog.AddAuthenticator()) }
    fun showRemoveAuthenticator() = local.update { it.copy(dialog = HomeDialog.RemoveAuthenticator) }

    fun updateDialogField(index: Int, value: String) = local.update { state ->
        val dialog = state.dialog as? HomeDialog.AddAuthenticator ?: return@update state
        state.copy(dialog = if (index == 0) dialog.copy(key = value, invalid = false) else dialog.copy(label = value))
    }

    fun confirmDialog(defaultLabel: String) {
        when (val dialog = local.value.dialog) {
            is HomeDialog.AddAuthenticator -> {
                val entry = parseAuthenticatorInput(dialog.key, fallbackLabel = defaultLabel)
                if (entry == null) {
                    local.update { it.copy(dialog = dialog.copy(invalid = true)) }
                    return
                }
                container.authenticator.save(dialog.label.trim().takeIf { it.isNotEmpty() }?.let { entry.copy(label = it) } ?: entry)
                dismissDialog()
                messages.show(R.string.authenticator_added)
            }
            HomeDialog.RemoveAuthenticator -> {
                container.authenticator.remove()
                dismissDialog()
            }
            is HomeDialog.DisconnectApp -> disconnect(dialog)
            null -> Unit
        }
    }

    fun dismissDialog() = local.update { state ->
        if ((state.dialog as? HomeDialog.DisconnectApp)?.isBusy == true) state else state.copy(dialog = null)
    }

    // --- connected apps ---

    fun showDisconnectApp(service: Service) = local.update { it.copy(dialog = HomeDialog.DisconnectApp(service)) }

    private fun disconnect(dialog: HomeDialog.DisconnectApp) {
        local.update { it.copy(dialog = dialog.copy(isBusy = true)) }
        viewModelScope.launch {
            runCatching { account.disconnectApp(dialog.service.id) }
                .onSuccess { messages.show(R.string.app_disconnected) }
                .onFailure(messages::show)
            local.update { it.copy(dialog = null) }
        }
    }

    private fun toHistoryEvent(entry: ActivityEntry) = HistoryEvent(
        id = entry.id,
        eventType = entry.eventType,
        createdAt = entry.createdAt,
        subtitle = entry.device?.takeUnless { it == "Unknown device" } ?: entry.ipAddress,
    )

    private data class LocalState(
        val isRefreshing: Boolean = false,
        val isRefreshingHistory: Boolean = false,
        val selectedDestination: String = "home",
        val dialog: HomeDialog? = null,
    )
}
