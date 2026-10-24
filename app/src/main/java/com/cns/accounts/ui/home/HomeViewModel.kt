package com.cns.accounts.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cns.accounts.AppContainer
import com.cns.accounts.R
import com.cns.accounts.data.ActivityFeed
import com.cns.accounts.data.AppPreferences
import com.cns.accounts.data.HistoryEvent
import com.cns.accounts.data.HomeData
import com.cns.accounts.data.Profile
import com.cns.accounts.data.Service
import com.cns.accounts.data.SignInRequest
import com.cns.accounts.data.isSendly
import com.cns.accounts.data.api.ActivityEntry
import com.cns.accounts.data.UserMessage
import com.cns.accounts.data.authenticator.TwoFactorMoveIncompleteException
import com.cns.accounts.data.toUserMessage
import com.cns.accounts.data.session.AuthState
import com.cns.accounts.ui.history.HistoryUiState
import com.cns.accounts.ui.history.isUserFacingEvent
import kotlinx.coroutines.CancellationException
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
 * the 2FA card, connected apps and the activity feed, all from [AppContainer.account] and
 * [AppContainer.twoFactor].
 */
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val account = container.account
    private val messages = container.messages

    // Signed in at start: the first load is already due (init below), so start out "loading".
    private val signedInAtStart = container.session.authState.value == AuthState.SignedIn
    private val local = MutableStateFlow(LocalState(isLoadingAccount = signedInAtStart))
    private var loadMoreJob: Job? = null
    private var setUpJob: Job? = null

    private val homeData = combine(
        account.user,
        account.connectedApps,
        container.twoFactor.state,
        container.approvals.pending,
        container.serviceLinks.sendly,
    ) { user, apps, twoFactor, approvals, sendly ->
        HomeData(
            profile = Profile(user?.username.orEmpty(), user?.totpEnabled == true, user?.avatarUrl),
            signInRequest = approvals.firstOrNull()?.let { request ->
                SignInRequest(
                    id = request.id,
                    action = request.action,
                    device = listOfNotNull(request.device, request.ip).joinToString(" · "),
                    createdAt = request.createdAt,
                    detail = request.clientName ?: request.newUsername,
                )
            },
            twoFactor = twoFactor,
            services = apps?.map { app ->
                val sendlyApp = isSendly(app.clientId, app.name)
                Service(
                    id = app.clientId,
                    name = app.name,
                    lastUsed = app.lastUsed,
                    needsSetup = sendlyApp && !sendly.isSetUp,
                    loginRequest = sendly.pendingLogin.takeIf { sendlyApp },
                )
            },
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(homeData, account.activity, local, container.preferences.historySeenAt) { data, feed, local, seenAt ->
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
            isSettingUpTwoFactor = local.isSettingUpTwoFactor,
            expandedServiceId = local.expandedServiceId,
            isLoadingAccount = local.isLoadingAccount,
            historyUnread = seenAt != AppPreferences.NotSeen && newestEventMillis(feed) > seenAt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(isLoadingAccount = signedInAtStart))

    init {
        // This ViewModel outlives a sign-out (it is activity-scoped), so load on every sign-in,
        // not just on creation; the StateFlow replays the current state, covering the first load.
        viewModelScope.launch {
            container.session.authState.collect { state ->
                if (state == AuthState.SignedIn) refresh(showIndicator = false, initial = true)
            }
        }
        // The first feed this install ever loads counts as seen: only what arrives later is "new".
        viewModelScope.launch {
            account.activity.collect { feed ->
                if (feed.loaded && container.preferences.historySeenAt.value == AppPreferences.NotSeen) {
                    container.preferences.setHistorySeenAt(newestEventMillis(feed).coerceAtLeast(0L))
                }
            }
        }
    }

    /** The History tab is on screen: everything it shows now has been seen. */
    fun markHistorySeen() {
        val feed = account.activity.value
        if (feed.loaded) container.preferences.setHistorySeenAt(newestEventMillis(feed))
    }

    private fun newestEventMillis(feed: ActivityFeed): Long =
        feed.entries.filter { isUserFacingEvent(it.eventType) }.maxOfOrNull { it.createdAt?.toEpochMilli() ?: 0L } ?: 0L

    fun selectDestination(destination: String) = local.update { it.copy(selectedDestination = destination) }

    /** Pull-to-refresh on Home; also runs once on start. Reloads everything Home and History show. */
    fun refresh(showIndicator: Boolean = true, initial: Boolean = false) {
        viewModelScope.launch {
            if (showIndicator) local.update { it.copy(isRefreshing = true) }
            if (initial) local.update { it.copy(isLoadingAccount = true) }
            val failures = listOf(
                // The splash waits for the account itself, not for the slower extras.
                async { runCatching { account.refreshProfile() }.also { local.update { it.copy(isLoadingAccount = false) } } },
                async { runCatching { account.refreshConnectedApps() } },
                async { runCatching { account.refreshActivity() } },
                async { runCatching { container.approvals.refresh() } },
                async { runCatching { container.serviceLinks.refresh() } },
            ).awaitAll().mapNotNull { it.exceptionOrNull() }
            // Parallel calls usually fail for the same reason (offline): report it once.
            failures.firstOrNull()?.let(messages::show)
            local.update { it.copy(isRefreshing = false, isLoadingAccount = false) }
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

    // --- requests waiting for this device's approval (Require Security App) ---

    fun approveRequest() = respondToRequest(approve = true)
    fun denyRequest() = respondToRequest(approve = false)

    private fun respondToRequest(approve: Boolean) {
        val id = uiState.value.data.signInRequest?.id ?: return
        val request = container.approvals.pending.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            runCatching { container.approvals.respond(request, approve) }
                .onSuccess { messages.show(if (approve) R.string.request_allowed else R.string.request_denied) }
                .onFailure(messages::show)
        }
    }

    // --- 2FA card ---

    /** "Set Up": turns 2FA on with this app as the authenticator, no code to type. */
    fun setUpTwoFactor() {
        if (local.value.isSettingUpTwoFactor) return
        local.update { it.copy(isSettingUpTwoFactor = true) }
        viewModelScope.launch {
            runCatching { container.twoFactor.enable() }
                .onSuccess { messages.show(R.string.two_factor_on) }
                .onFailure(messages::show)
            local.update { it.copy(isSettingUpTwoFactor = false) }
        }
    }

    fun showTransferTwoFactor() = local.update { it.copy(dialog = HomeDialog.TransferTwoFactor()) }

    fun updateDialogField(index: Int, value: String) = local.update { state ->
        val dialog = state.dialog as? HomeDialog.TransferTwoFactor ?: return@update state
        state.copy(dialog = if (index == 0) dialog.copy(password = value, error = null) else dialog.copy(code = value, error = null))
    }

    fun confirmDialog() {
        when (val dialog = local.value.dialog) {
            is HomeDialog.TransferTwoFactor -> transferTwoFactor(dialog)
            is HomeDialog.DisconnectApp -> disconnect(dialog)
            is HomeDialog.SetUpService -> if (dialog.waitingCode != null) abortSetUp() else setUpService(dialog)
            null -> Unit
        }
    }

    fun dismissDialog() {
        // Closing the "approve this app" step withdraws the request.
        if ((local.value.dialog as? HomeDialog.SetUpService)?.waitingCode != null) return abortSetUp()
        dismissIdleDialog()
    }

    private fun dismissIdleDialog() = local.update { state ->
        val busy = when (val dialog = state.dialog) {
            is HomeDialog.DisconnectApp -> dialog.isBusy
            is HomeDialog.TransferTwoFactor -> dialog.isBusy
            is HomeDialog.SetUpService -> dialog.isBusy
            null -> false
        }
        if (busy) state else state.copy(dialog = null)
    }

    private fun transferTwoFactor(dialog: HomeDialog.TransferTwoFactor) {
        if (dialog.isBusy) return
        if (dialog.password.isEmpty() || dialog.code.length != 6) {
            local.update { it.copy(dialog = dialog.copy(error = UserMessage(resId = R.string.fill_all_fields))) }
            return
        }
        local.update { it.copy(dialog = dialog.copy(isBusy = true, error = null)) }
        viewModelScope.launch {
            runCatching { container.twoFactor.transferHere(dialog.password, dialog.code) }
                .onSuccess {
                    local.update { it.copy(dialog = null) }
                    messages.show(R.string.two_factor_transferred)
                }
                .onFailure { error ->
                    if (error is TwoFactorMoveIncompleteException) {
                        local.update { it.copy(dialog = null) }
                        messages.show(R.string.two_factor_transfer_incomplete)
                    } else {
                        local.update { it.copy(dialog = dialog.copy(isBusy = false, error = error.toUserMessage())) }
                    }
                }
        }
    }

    // --- connected apps ---

    /** A row's chevron: show that service in detail, or back to the list if it already is. */
    fun toggleService(service: Service) = local.update {
        it.copy(expandedServiceId = if (it.expandedServiceId == service.id) null else service.id)
    }

    fun showDisconnectApp(service: Service) = local.update { it.copy(dialog = HomeDialog.DisconnectApp(service)) }

    fun showSetUpService(service: Service) = local.update { it.copy(dialog = HomeDialog.SetUpService(service)) }

    fun approveServiceLogin(service: Service) = respondToServiceLogin(service, approve = true)
    fun denyServiceLogin(service: Service) = respondToServiceLogin(service, approve = false)

    private fun respondToServiceLogin(service: Service, approve: Boolean) {
        val request = service.loginRequest ?: return
        viewModelScope.launch {
            runCatching { container.serviceLinks.respondToSendlyLogin(request, approve) }
                .onSuccess { messages.show(if (approve) R.string.request_allowed else R.string.request_denied) }
                .onFailure(messages::show)
        }
    }

    /** Continue: sign in to Sendly and join as an approver device, waiting for approval if needed. */
    private fun setUpService(dialog: HomeDialog.SetUpService) {
        if (dialog.isBusy) return
        local.update { it.copy(dialog = dialog.copy(isBusy = true, error = null)) }
        setUpJob = viewModelScope.launch {
            // While a trusted Sendly device has to approve this app, show the code it will show.
            val codeWatcher = launch {
                container.serviceLinks.sendly.collect { link ->
                    val code = link.enrollment?.verificationCode
                    local.update { state ->
                        val current = state.dialog as? HomeDialog.SetUpService ?: return@update state
                        state.copy(dialog = current.copy(waitingCode = code, isBusy = code == null))
                    }
                }
            }
            val result = try {
                runCatching { container.serviceLinks.beginSendlySetup(onWaiting = { messages.show(R.string.approval_waiting) }) }
            } finally {
                // Stopped before the outcome is shown, so a late update can't mark the dialog busy again.
                codeWatcher.cancel()
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            result
                .onSuccess {
                    local.update { it.copy(dialog = null) }
                    messages.show(R.string.sendly_set_up)
                }
                .onFailure { error ->
                    local.update { state ->
                        val current = state.dialog as? HomeDialog.SetUpService ?: return@update state
                        state.copy(dialog = current.copy(isBusy = false, waitingCode = null, error = error.toUserMessage()))
                    }
                }
        }
    }

    private fun abortSetUp() {
        setUpJob?.cancel()
        local.update { it.copy(dialog = null) }
        viewModelScope.launch { runCatching { container.serviceLinks.abortSendlySetup() } }
    }

    private fun disconnect(dialog: HomeDialog.DisconnectApp) {
        local.update { it.copy(dialog = dialog.copy(isBusy = true)) }
        viewModelScope.launch {
            // A set-up Sendly also drops this app as a Sendly device and its key copy.
            if (isSendly(dialog.service.id, dialog.service.name) && container.serviceLinks.sendly.value.isSetUp) {
                runCatching { container.serviceLinks.disconnectSendly() }
            }
            runCatching { account.disconnectApp(dialog.service.id) }
                .onSuccess {
                    messages.show(R.string.app_disconnected)
                    local.update { it.copy(expandedServiceId = null) }
                }
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
        val isSettingUpTwoFactor: Boolean = false,
        val expandedServiceId: String? = null,
        val isLoadingAccount: Boolean = false,
    )
}
