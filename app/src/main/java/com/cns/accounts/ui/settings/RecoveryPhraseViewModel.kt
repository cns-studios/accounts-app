package com.cns.accounts.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cns.accounts.AppContainer
import com.cns.accounts.R
import com.cns.accounts.data.UserMessage
import com.cns.accounts.data.api.ApiException
import com.cns.accounts.data.api.RecoveryPhraseDraft
import com.cns.accounts.data.authenticator.TwoFactorState
import com.cns.accounts.data.toUserMessage
import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

enum class RecoveryPhraseStep { Overview, Verify, Show, Confirm }

@Immutable
data class RecoveryPhraseUiState(
    val step: RecoveryPhraseStep = RecoveryPhraseStep.Verify,
    val enabled: Boolean = false,
    val createdAt: Instant? = null,
    /** Verify: true when the check is for removing the phrase, false for a new one. */
    val removing: Boolean = false,
    val password: String = "",
    val code: String = "",
    /** A 2FA code field: the account has 2FA, this device can't supply the code and no approval replaces it. */
    val askForCode: Boolean = false,
    val draft: RecoveryPhraseDraft? = null,
    /** For the saved file's name and heading. */
    val username: String = "",
    val revealed: Boolean = false,
    val savedIt: Boolean = false,
    val answers: List<String> = emptyList(),
    val isBusy: Boolean = false,
    val error: UserMessage? = null,
    /** Set once the flow is over; the screen goes back. */
    val finished: Boolean = false,
)

/**
 * The recovery phrase page: create (or replace) it — password plus 2FA code or an approval, the
 * 12 words shown once, three typed back — or remove it. The words live only in this ViewModel,
 * which goes away with the page.
 */
class RecoveryPhraseViewModel(private val container: AppContainer) : ViewModel() {
    private val account = container.account
    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<RecoveryPhraseUiState> = _uiState.asStateFlow()

    private fun initialState(): RecoveryPhraseUiState {
        val user = account.user.value
        val enabled = user?.recoveryPhraseEnabled == true
        return RecoveryPhraseUiState(
            step = if (enabled) RecoveryPhraseStep.Overview else RecoveryPhraseStep.Verify,
            enabled = enabled,
            createdAt = user?.recoveryPhraseCreatedAt,
            username = user?.username.orEmpty(),
            askForCode = needsTypedCode(),
        )
    }

    private val requireAppOn get() = account.user.value?.requireAppApproval == true

    private val codeOnDevice get() = container.twoFactor.state.value is TwoFactorState.Active

    private fun needsTypedCode() = account.user.value?.totpEnabled == true && !requireAppOn && !codeOnDevice

    /** The 2FA code to send: none with Require Security App (the approval replaces it) or without 2FA. */
    private fun codeToSend(typed: String): String? = when {
        requireAppOn || account.user.value?.totpEnabled != true -> null
        codeOnDevice -> container.twoFactor.currentCode()
        else -> typed.trim()
    }

    fun updatePassword(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun updateCode(value: String) = _uiState.update { it.copy(code = value, error = null) }
    fun updateAnswer(index: Int, value: String) = _uiState.update { state ->
        state.copy(answers = state.answers.mapIndexed { i, a -> if (i == index) value else a }, error = null)
    }

    fun startNewPhrase() = toVerify(removing = false)
    fun startRemove() = toVerify(removing = true)

    private fun toVerify(removing: Boolean) = _uiState.update {
        it.copy(step = RecoveryPhraseStep.Verify, removing = removing, password = "", code = "", askForCode = needsTypedCode(), error = null)
    }

    fun reveal() = _uiState.update { it.copy(revealed = true) }

    /** Writes [text] (the words and a warning) to the file the user picked in the system file picker. */
    fun savePhraseFile(resolver: ContentResolver, uri: Uri, text: String) {
        viewModelScope.launch {
            val saved = runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("no output stream")
                }
            }.isSuccess
            container.messages.show(if (saved) R.string.recovery_phrase_file_saved else R.string.recovery_phrase_file_failed, isError = !saved)
        }
    }
    fun setSavedIt(saved: Boolean) = _uiState.update { it.copy(savedIt = saved) }
    fun toConfirm() = _uiState.update { if (it.revealed && it.savedIt) it.copy(step = RecoveryPhraseStep.Confirm, error = null) else it }

    /** Back inside the flow; false when the page itself should close. */
    fun back(): Boolean {
        val state = _uiState.value
        if (state.isBusy) return true
        when {
            state.step == RecoveryPhraseStep.Confirm -> _uiState.update { it.copy(step = RecoveryPhraseStep.Show, error = null) }
            state.step == RecoveryPhraseStep.Verify && state.enabled -> _uiState.update { it.copy(step = RecoveryPhraseStep.Overview, error = null) }
            else -> return false
        }
        return true
    }

    fun submitVerify() {
        val state = _uiState.value
        if (state.isBusy) return
        if (state.password.isEmpty() || (state.askForCode && state.code.length != 6)) {
            _uiState.update { it.copy(error = UserMessage(resId = R.string.fill_all_fields)) }
            return
        }
        _uiState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                container.approvals.gated(onWaiting = { container.messages.show(R.string.approval_waiting) }) { approvalId ->
                    val code = codeToSend(state.code)
                    if (state.removing) {
                        account.removeRecoveryPhrase(state.password, code, approvalId)
                        null
                    } else {
                        account.setupRecoveryPhrase(state.password, code, approvalId)
                    }
                }
            }.onSuccess { draft ->
                if (draft == null) {
                    container.messages.show(R.string.recovery_phrase_removed)
                    _uiState.update { it.copy(isBusy = false, password = "", finished = true) }
                } else {
                    _uiState.update {
                        it.copy(
                            step = RecoveryPhraseStep.Show,
                            draft = draft,
                            answers = draft.confirmPositions.map { "" },
                            password = "",
                            code = "",
                            revealed = false,
                            savedIt = false,
                            isBusy = false,
                        )
                    }
                }
            }.onFailure(::fail)
        }
    }

    fun submitConfirm() {
        val state = _uiState.value
        val draft = state.draft ?: return
        if (state.isBusy) return
        val words = state.answers.map { container.recoveryWords.normalize(it) }
        if (words.any { it == null }) {
            _uiState.update { it.copy(error = UserMessage(resId = R.string.recovery_phrase_unknown_word)) }
            return
        }
        _uiState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { account.confirmRecoveryPhrase(draft.setupToken, words.filterNotNull()) }
                .onSuccess {
                    container.messages.show(if (state.enabled) R.string.recovery_phrase_replaced else R.string.recovery_phrase_saved)
                    _uiState.update { it.copy(isBusy = false, draft = null, answers = emptyList(), finished = true) }
                }
                .onFailure { error ->
                    // An expired draft can't be confirmed any more: start over with a new phrase.
                    if (error is ApiException && error.code == "setup_expired") {
                        _uiState.update { initialState().copy(step = RecoveryPhraseStep.Verify, error = recoveryMessage(error)) }
                    } else {
                        fail(error)
                    }
                }
        }
    }

    private fun fail(error: Throwable) = _uiState.update { it.copy(isBusy = false, error = recoveryMessage(error)) }

    private fun recoveryMessage(error: Throwable): UserMessage = when ((error as? ApiException)?.code) {
        "invalid_password" -> UserMessage(resId = R.string.recovery_phrase_wrong_password)
        "code_required", "invalid_code" -> UserMessage(resId = R.string.recovery_phrase_wrong_code)
        "words_mismatch" -> UserMessage(resId = R.string.recovery_phrase_words_mismatch)
        "setup_expired" -> UserMessage(resId = R.string.recovery_phrase_setup_expired)
        else -> error.toUserMessage()
    }
}
