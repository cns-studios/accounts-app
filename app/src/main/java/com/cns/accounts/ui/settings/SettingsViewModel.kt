package com.cns.accounts.ui.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cns.accounts.AppContainer
import com.cns.accounts.R
import com.cns.accounts.data.Profile
import com.cns.accounts.data.SettingsData
import com.cns.accounts.data.SettingsToggles
import com.cns.accounts.data.UnsupportedFeatureException
import com.cns.accounts.data.UserMessage
import com.cns.accounts.data.api.AccountUser
import com.cns.accounts.data.authenticator.TwoFactorState
import com.cns.accounts.data.securityStatus
import com.cns.accounts.data.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * "Your Account": profile edits, 2FA (through [AppContainer.twoFactor]), password, avatar, sign-out and deletion against auth-v2,
 * plus the device-local biometric lock. Strong account protection, "require security app" and
 * the recovery phrase have no backend yet; their controls report that instead of pretending.
 */
class SettingsViewModel(
    private val container: AppContainer,
    private val application: Application,
) : ViewModel() {
    private val account = container.account
    private val twoFactorManager = container.twoFactor
    private val messages = container.messages
    private val dialog = MutableStateFlow<SettingsDialog?>(null)
    private val enablingTwoFactor = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        account.user,
        twoFactorManager.state,
        container.preferences.biometricUnlock,
        container.preferences.notifications,
        dialog,
        enablingTwoFactor,
    ) { values ->
        val user = values[0] as AccountUser?
        val twoFactorState = values[1] as TwoFactorState
        val biometric = values[2] as Boolean
        val notifications = values[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val dialog = values[4] as SettingsDialog?
        val enabling = values[5] as Boolean
        val twoFactor = user?.totpEnabled == true
        SettingsUiState(
            data = SettingsData(
                profile = Profile(user?.username.orEmpty(), twoFactor, user?.avatarUrl),
                security = securityStatus(twoFactorEnabled = twoFactor, authenticatorOnDevice = twoFactorState is TwoFactorState.Active),
                // Shown on while it is being turned on, so the toggle doesn't bounce back meanwhile.
                toggles = SettingsToggles(
                    biometricUnlock = biometric,
                    notifications = notifications,
                    twoFactorAuthentication = twoFactor || enabling,
                ),
            ),
            dialog = dialog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Whether 2FA codes can come from this device, so dialogs needn't ask for one. */
    private val codeOnDevice get() = twoFactorManager.state.value is TwoFactorState.Active

    init {
        viewModelScope.launch { runCatching { account.refreshProfile() } }
    }

    // --- toggles ---

    /** Called only after the UI has confirmed the user with a biometric prompt (when enabling). */
    fun setBiometricUnlock(enabled: Boolean) = container.preferences.setBiometricUnlock(enabled)

    fun setTwoFactorAuthentication(enabled: Boolean) {
        if (enabled) enableTwoFactor() else openDialog(SettingsDialogKind.DisableTwoFactor)
    }

    /** Account-event notifications; the screen has asked for the notification permission first. */
    fun setNotifications(enabled: Boolean) {
        if (enabled && !container.push.isConfigured) {
            messages.show(R.string.push_unavailable)
            return
        }
        container.preferences.setNotifications(enabled)
        if (enabled) messages.show(R.string.notifications_on)
    }
    fun setRequireSecurityApp(@Suppress("UNUSED_PARAMETER") enabled: Boolean) = unsupported()
    fun setupRecoveryPhrase() = unsupported()

    private fun unsupported() = messages.show(UnsupportedFeatureException())

    // --- dialogs ---

    fun openDialog(kind: SettingsDialogKind) {
        // A code field only when the account needs one and this device can't supply it.
        val askForCode = uiState.value.data.profile.twoFactorEnabled && !codeOnDevice
        val values = when (kind) {
            SettingsDialogKind.EditUsername -> listOf(uiState.value.data.profile.name, "")
            SettingsDialogKind.DisableTwoFactor, SettingsDialogKind.DeleteAccount -> if (askForCode) listOf("", "") else listOf("")
            SettingsDialogKind.ChangePassword -> listOf("", "", "")
            else -> emptyList()
        }
        dialog.value = SettingsDialog(kind, values)
    }

    /** "Configure 2FA" in the profile menu: set it up when off, turn it off when on. */
    fun configureTwoFactor() = openDialog(
        if (uiState.value.data.toggles.twoFactorAuthentication) SettingsDialogKind.DisableTwoFactor else SettingsDialogKind.ConfigureTwoFactor,
    )

    fun updateField(index: Int, value: String) = dialog.update { current ->
        current?.copy(values = current.values.toMutableList().also { if (index in it.indices) it[index] = value }, error = null)
    }

    fun dismissDialog() = dialog.update { if (it?.isBusy == true) it else null }

    fun confirmDialog() {
        val current = dialog.value ?: return
        if (current.isBusy) return
        val values = current.values.map { it.trim() }
        when (current.kind) {
            SettingsDialogKind.EditUsername -> requireFilled(values) {
                run(R.string.username_changed) { account.changeUsername(password = current.values[1], newUsername = values[0]) }
            }
            SettingsDialogKind.ChangePassword -> requireFilled(current.values) {
                if (current.values[1] != current.values[2]) {
                    fail(UserMessage(resId = R.string.passwords_dont_match))
                } else {
                    run(R.string.password_changed) { account.changePassword(current.values[0], current.values[1]) }
                }
            }
            SettingsDialogKind.RemoveAvatar -> run(R.string.avatar_removed) { account.deleteAvatar() }
            SettingsDialogKind.ConfigureTwoFactor -> run(R.string.two_factor_on) { twoFactorManager.enable() }
            SettingsDialogKind.DisableTwoFactor -> requireFilled(values) {
                run(R.string.two_factor_off) { twoFactorManager.disable(password = current.values[0], code = values.getOrElse(1) { "" }) }
            }
            SettingsDialogKind.DeleteAccount -> requireFilled(values) {
                run(successMessage = null) {
                    account.deleteAccount(password = current.values[0], code = values.getOrNull(1) ?: twoFactorManager.currentCode().orEmpty())
                }
            }
            // The "Choose Photo" button launches the system photo picker from the screen; the
            // result comes back through uploadAvatar().
            SettingsDialogKind.ChangeAvatar -> Unit
        }
    }

    // --- 2FA ---

    /** The toggle: turn 2FA on right away, with this app as the authenticator. */
    private fun enableTwoFactor() {
        if (enablingTwoFactor.value) return
        enablingTwoFactor.value = true
        viewModelScope.launch {
            runCatching { twoFactorManager.enable() }
                .onSuccess { messages.show(R.string.two_factor_on) }
                .onFailure(messages::show)
            enablingTwoFactor.value = false
        }
    }

    // --- avatar ---

    fun uploadAvatar(uri: Uri) {
        dialog.update { it?.copy(isBusy = true, error = null) ?: SettingsDialog(SettingsDialogKind.ChangeAvatar, isBusy = true) }
        viewModelScope.launch {
            val jpeg = runCatching { withContext(Dispatchers.IO) { encodeAvatar(uri) } }.getOrNull()
            if (jpeg == null) {
                fail(UserMessage(resId = R.string.avatar_invalid))
                return@launch
            }
            runCatching { account.uploadAvatar(jpeg, "image/jpeg") }
                .onSuccess {
                    dialog.value = null
                    messages.show(R.string.avatar_updated)
                }
                .onFailure { fail(it.toUserMessage()) }
        }
    }

    /**
     * Re-encodes the picked photo as a JPEG of at most [MaxAvatarEdge]px: auth-v2 accepts only
     * JPEG/PNG/WebP up to 5 MB, and phone photos are often HEIC and far larger than that.
     */
    private fun encodeAvatar(uri: Uri): ByteArray {
        val source = ImageDecoder.createSource(application.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MaxAvatarEdge) {
                val scale = MaxAvatarEdge.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }

    // --- sign-out ---

    fun logOut() {
        viewModelScope.launch { account.signOut() }
    }

    // --- plumbing ---

    private inline fun requireFilled(values: List<String>, action: () -> Unit) {
        if (values.any { it.isBlank() }) fail(UserMessage(resId = R.string.fill_all_fields)) else action()
    }

    /** Runs a dialog's request with the busy spinner; closes on success, shows the error inline on failure. */
    private fun run(successMessage: Int?, request: suspend () -> Unit) {
        dialog.update { it?.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { request() }
                .onSuccess {
                    dialog.value = null
                    successMessage?.let(messages::show)
                }
                .onFailure { fail(it.toUserMessage()) }
        }
    }

    private fun fail(message: UserMessage) = dialog.update { it?.copy(isBusy = false, error = message) }

    private companion object {
        const val MaxAvatarEdge = 1024
    }
}
