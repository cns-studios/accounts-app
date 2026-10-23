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
import com.cns.accounts.data.authenticator.AuthenticatorEntry
import com.cns.accounts.data.authenticator.CnsIssuer
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
 * "Your Account": profile edits, 2FA, password, avatar, sign-out and deletion against auth-v2,
 * plus the device-local biometric lock. Strong account protection, "require security app" and
 * the recovery phrase have no backend yet; their controls report that instead of pretending.
 */
class SettingsViewModel(
    private val container: AppContainer,
    private val application: Application,
) : ViewModel() {
    private val account = container.account
    private val authenticator = container.authenticator
    private val messages = container.messages
    private val dialog = MutableStateFlow<SettingsDialog?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        account.user,
        authenticator.entry,
        container.preferences.biometricUnlock,
        dialog,
    ) { user, key, biometric, dialog ->
        val twoFactor = user?.totpEnabled == true
        SettingsUiState(
            data = SettingsData(
                profile = Profile(user?.username.orEmpty(), twoFactor, user?.avatarUrl),
                security = securityStatus(twoFactorEnabled = twoFactor, authenticatorOnDevice = key?.isCnsAccount == true),
                toggles = SettingsToggles(biometricUnlock = biometric, twoFactorAuthentication = twoFactor),
            ),
            dialog = dialog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch { runCatching { account.refreshProfile() } }
    }

    // --- toggles ---

    /** Called only after the UI has confirmed the user with a biometric prompt (when enabling). */
    fun setBiometricUnlock(enabled: Boolean) = container.preferences.setBiometricUnlock(enabled)

    fun setTwoFactorAuthentication(enabled: Boolean) {
        if (enabled) startTwoFactorSetup() else openDialog(SettingsDialogKind.DisableTwoFactor)
    }

    fun setStrongAccountProtection(@Suppress("UNUSED_PARAMETER") enabled: Boolean) = unsupported()
    fun setRequireSecurityApp(@Suppress("UNUSED_PARAMETER") enabled: Boolean) = unsupported()
    fun setupRecoveryPhrase() = unsupported()

    private fun unsupported() = messages.show(UnsupportedFeatureException())

    // --- dialogs ---

    fun openDialog(kind: SettingsDialogKind) {
        val code = authenticator.currentCnsCode()
        val values = when (kind) {
            SettingsDialogKind.EditUsername -> listOf(uiState.value.data.profile.name, "")
            SettingsDialogKind.DisableTwoFactor -> listOf("", code.orEmpty())
            SettingsDialogKind.ChangePassword -> listOf("", "", "")
            SettingsDialogKind.DeleteAccount ->
                if (uiState.value.data.toggles.twoFactorAuthentication) listOf("", code.orEmpty()) else listOf("")
            else -> emptyList()
        }
        dialog.value = SettingsDialog(kind, values, prefilledCode = code)
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
            SettingsDialogKind.ConfigureTwoFactor -> startTwoFactorSetup()
            SettingsDialogKind.SetupTwoFactor -> requireFilled(values) { confirmTwoFactorSetup(current, freshCode(current, values[0])) }
            SettingsDialogKind.DisableTwoFactor -> requireFilled(values) {
                run(R.string.two_factor_off) {
                    account.disableTwoFactor(password = current.values[0], code = freshCode(current, values[1]))
                    // The key is dead server-side now; don't leave a card generating useless codes.
                    if (authenticator.entry.value?.isCnsAccount == true) authenticator.remove()
                }
            }
            SettingsDialogKind.DeleteAccount -> requireFilled(values) {
                run(successMessage = null) { account.deleteAccount(password = current.values[0], code = freshCode(current, values.getOrElse(1) { "" })) }
            }
            // The "Choose Photo" button launches the system photo picker from the screen; the
            // result comes back through uploadAvatar().
            SettingsDialogKind.ChangeAvatar -> Unit
        }
    }

    // --- 2FA setup ---

    private fun startTwoFactorSetup() {
        dialog.value = (dialog.value ?: SettingsDialog(SettingsDialogKind.ConfigureTwoFactor)).copy(isBusy = true, error = null)
        viewModelScope.launch {
            runCatching { account.startTwoFactorSetup() }
                .onSuccess { setup ->
                    // Use this app as the authenticator when its card is free (or already holds
                    // an old CNS key); never overwrite a key the user added for something else.
                    val existing = authenticator.entry.value
                    val storeLocally = existing == null || existing.isCnsAccount
                    val code = if (storeLocally) {
                        AuthenticatorEntry(label = "$CnsIssuer · ${uiState.value.data.profile.name}", secret = setup.secret, isCnsAccount = true)
                            .codeAt(System.currentTimeMillis())
                    } else {
                        null
                    }
                    dialog.value = SettingsDialog(
                        kind = SettingsDialogKind.SetupTwoFactor,
                        values = listOf(code.orEmpty()),
                        setup = setup,
                        keyStoredOnDevice = storeLocally,
                        prefilledCode = code,
                    )
                }
                .onFailure { error ->
                    dialog.value = null
                    messages.show(error)
                }
        }
    }

    private fun confirmTwoFactorSetup(current: SettingsDialog, code: String) {
        val setup = current.setup ?: return
        run(R.string.two_factor_on) {
            account.confirmTwoFactor(code)
            if (current.keyStoredOnDevice) {
                authenticator.save(AuthenticatorEntry("$CnsIssuer · ${uiState.value.data.profile.name}", setup.secret, isCnsAccount = true))
            }
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

    /** The entered code, or a current one if the user kept the pre-filled code (see [SettingsDialog.prefilledCode]). */
    private fun freshCode(dialog: SettingsDialog, entered: String): String {
        if (dialog.prefilledCode == null || entered != dialog.prefilledCode) return entered
        val now = System.currentTimeMillis()
        val pendingSetupCode = dialog.setup?.takeIf { dialog.keyStoredOnDevice }?.let {
            AuthenticatorEntry("", it.secret, isCnsAccount = true).codeAt(now)
        }
        return pendingSetupCode ?: authenticator.currentCnsCode() ?: entered
    }

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
