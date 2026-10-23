package com.cns.accounts.ui.login

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cns.accounts.AppContainer
import com.cns.accounts.R
import com.cns.accounts.data.UserMessage
import com.cns.accounts.data.api.ApiException
import com.cns.accounts.data.api.LoginResult
import com.cns.accounts.data.toUserMessage
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
    val isBusy: Boolean = false,
    val error: UserMessage? = null,
)

/**
 * Password sign-in through auth-v2's mobile endpoints (`/api/auth/mobile/login` and `/login/2fa`),
 * which hand the refresh token back in the body for [com.cns.accounts.data.session.SessionManager]
 * to keep. When this device's authenticator holds the account's CNS key, the 2FA step is pre-filled.
 */
class LoginViewModel(private val container: AppContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

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

    /** Back from the 2FA step to the username/password form. */
    fun restart() = _uiState.update { LoginUiState(username = it.username) }

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
