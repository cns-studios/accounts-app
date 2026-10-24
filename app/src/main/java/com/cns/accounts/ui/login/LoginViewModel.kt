package com.cns.accounts.ui.login

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cns.accounts.AppContainer
import com.cns.accounts.R
import com.cns.accounts.data.UserMessage
import com.cns.accounts.data.api.ApiException
import com.cns.accounts.data.api.ApprovalChallenge
import com.cns.accounts.data.api.ApprovalPoll
import com.cns.accounts.data.api.LoginResult
import com.cns.accounts.data.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val code: String = "",
    // Non-null once the password was accepted and auth-v2 asked for a TOTP code.
    val loginToken: String? = null,
    val codePrefilled: Boolean = false,
    // Require Security App: the sign-in waits for the approver phone ([loginToken] is the TOTP
    // fallback). [usingFallback]: the user chose the code instead, which turns the requirement off.
    val awaitingApproval: Boolean = false,
    val usingFallback: Boolean = false,
    // false: the welcome page (Log In / Create Account); true: the username/password form.
    val showCredentials: Boolean = false,
    val isBusy: Boolean = false,
    val error: UserMessage? = null,
)

/**
 * Password sign-in through auth-v2's mobile endpoints (`/api/auth/mobile/login` and `/login/2fa`),
 * which hand the refresh token back in the body for [com.cns.accounts.data.session.SessionManager]
 * to keep. When this device's authenticator holds the account's CNS key, the 2FA step is pre-filled.
 * With Require Security App on, the sign-in waits for the approver: when that is this phone, it
 * approves its own sign-in (after the screen-lock check); otherwise it waits, with the 2FA code as
 * the way out for a lost phone.
 */
class LoginViewModel(private val container: AppContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    private var approvalJob: Job? = null

    fun updateUsername(value: String) = _uiState.update { it.copy(username = value, error = null) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun updateCode(value: String) = _uiState.update { it.copy(code = value, codePrefilled = false, error = null) }

    fun submitCredentials() {
        val state = _uiState.value
        if (state.isBusy) return
        if (state.username.isBlank() || state.password.isEmpty()) {
            _uiState.update { it.copy(error = UserMessage(resId = R.string.sign_in_missing_fields)) }
            return
        }
        _uiState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { container.api.login(state.username.trim(), state.password) }
                .onSuccess(::handleResult)
                .onFailure { error -> fail(error, wrongCredentials = R.string.login_failed) }
        }
    }

    fun submitCode() {
        val state = _uiState.value
        val loginToken = state.loginToken ?: return
        if (state.isBusy) return
        if (state.code.length != 6) {
            _uiState.update { it.copy(error = UserMessage(resId = R.string.fill_all_fields)) }
            return
        }
        // A pre-filled code may have rolled over while the screen sat open; send a current one.
        val code = if (state.codePrefilled) deviceCode(state.username) ?: state.code else state.code
        _uiState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { container.api.loginTwoFactor(loginToken, code) }
                .onSuccess(::handleResult)
                .onFailure { error -> fail(error, wrongCredentials = R.string.two_factor_failed) }
        }
    }

    /** "Log In" on the welcome page. */
    fun showCredentials() = _uiState.update { it.copy(showCredentials = true, error = null) }

    /** "Use a 2FA code instead" while waiting for the approver phone. */
    fun useCodeInstead() {
        val state = _uiState.value
        if (state.loginToken == null) return
        approvalJob?.cancel()
        val prefill = deviceCode(state.username)
        _uiState.update {
            it.copy(awaitingApproval = false, usingFallback = true, code = prefill.orEmpty(), codePrefilled = prefill != null, isBusy = false, error = null)
        }
    }

    /** Back: from the 2FA or approval step to the form, from the form to the welcome page. */
    fun back() {
        val state = _uiState.value
        if (state.awaitingApproval) approvalJob?.cancel() else if (state.isBusy) return
        _uiState.value = when {
            state.loginToken != null || state.awaitingApproval -> LoginUiState(username = state.username, showCredentials = true)
            else -> LoginUiState(username = state.username)
        }
    }

    private fun handleResult(result: LoginResult) {
        when (result) {
            is LoginResult.SignedIn -> {
                container.account.onSignedIn(result.user)
                container.session.establish(result.tokens)
                _uiState.value = LoginUiState()
            }
            is LoginResult.TwoFactorRequired -> {
                val prefill = deviceCode(_uiState.value.username)
                _uiState.update {
                    it.copy(
                        loginToken = result.loginToken,
                        password = "",
                        code = prefill.orEmpty(),
                        codePrefilled = prefill != null,
                        isBusy = false,
                    )
                }
            }
            is LoginResult.ApprovalRequired -> awaitApproval(result.approval, result.loginToken)
        }
    }

    private fun awaitApproval(approval: ApprovalChallenge, loginToken: String?) {
        val approvals = container.approvals
        val ownUserId = approvals.approverFor(_uiState.value.username)
        _uiState.update {
            it.copy(password = "", loginToken = loginToken, awaitingApproval = ownUserId == null, isBusy = ownUserId != null, error = null)
        }
        approvalJob = viewModelScope.launch {
            try {
                // This phone is the approver: approve the sign-in it just started itself.
                if (ownUserId != null) approvals.approveOwnSignIn(ownUserId, approval)
                val done = approvals.awaitDecision(approval) as? ApprovalPoll.Completed
                    ?: throw ApiException(200, "bad_response", "The server did not return a session")
                handleResult(LoginResult.fromJson(done.json))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { LoginUiState(username = it.username, showCredentials = true, error = e.toUserMessage()) }
            }
        }
    }

    // Only from a key this device holds for this very account.
    private fun deviceCode(username: String): String? =
        container.authenticator.entryForUsername(username)?.codeAt(System.currentTimeMillis())

    private fun fail(error: Throwable, wrongCredentials: Int) {
        // auth-v2's mobile endpoints answer every rejected sign-in with a generic 401.
        val message = if (error is ApiException && error.isUnauthorized) UserMessage(resId = wrongCredentials) else error.toUserMessage()
        _uiState.update { it.copy(isBusy = false, error = message) }
    }
}
