package com.cns.accounts.data.qr

import com.cns.accounts.R
import com.cns.accounts.data.UserMessage
import com.cns.accounts.data.api.ApiException
import com.cns.accounts.data.api.CnsApi
import com.cns.accounts.data.api.QrLoginRequest
import com.cns.accounts.data.api.QrLoginRequirement
import com.cns.accounts.data.approvals.Approvals
import com.cns.accounts.data.authenticator.TwoFactorManager
import com.cns.accounts.data.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Logging another device in with a QR code (auth-v2 `docs/API.md`, "QR code login"): the
 * accounts website shows a code, this app scans it ([offer], from the scanner or a link the
 * camera app opened), shows which browser asks, and confirms or cancels it.
 *
 * Confirming takes what a sign-in would. With Require Security App on it needs an approval:
 * this device gives it itself when it is the approver ([Approvals.gated], no card or
 * notification), otherwise the approver device has to. Otherwise, with 2FA on, it needs a code:
 * sent without asking when this device holds the 2FA key, typed by the user when not.
 */
class QrLogins(
    private val api: CnsApi,
    private val approvals: Approvals,
    private val twoFactor: TwoFactorManager,
    private val accountsWebUrl: String,
) {
    private val _pending = MutableStateFlow<QrLoginCode?>(null)

    /** A scanned code waiting for Home to show it (once signed in and unlocked). */
    val pending: StateFlow<QrLoginCode?> = _pending.asStateFlow()

    /** A scanned or opened link: true when it was a login QR code for this app's site. */
    fun offer(link: String): Boolean {
        val code = QrLoginLink.parse(link, accountsWebUrl) ?: return false
        _pending.value = code
        return true
    }

    fun take(): QrLoginCode? = _pending.getAndUpdate { null }

    suspend fun scan(code: QrLoginCode): QrLoginRequest = api.scanQrLogin(code.id, code.secret)

    /**
     * Logs the browser in. [typedCode]: the 2FA code the user entered, if asked for one.
     * [onWaiting]: the approval has to come from another device. Returns false when a 2FA code
     * is needed that this device can't supply.
     */
    suspend fun confirm(code: QrLoginCode, request: QrLoginRequest, typedCode: String? = null, onWaiting: () -> Unit = {}): Boolean {
        when (request.requires) {
            QrLoginRequirement.Approval ->
                approvals.gated(onWaiting) { approvalId -> api.confirmQrLogin(code.id, code.secret, approvalId = approvalId) }
            QrLoginRequirement.Code -> {
                val totp = typedCode ?: twoFactor.currentCode() ?: return false
                api.confirmQrLogin(code.id, code.secret, code = totp)
            }
            QrLoginRequirement.Nothing -> api.confirmQrLogin(code.id, code.secret)
        }
        return true
    }

    /** Declines it; the browser is told, and a pending approval for it is withdrawn. */
    suspend fun cancel(code: QrLoginCode) = api.cancelQrLogin(code.id, code.secret)

    companion object {
        /** What went wrong, in words for this flow. */
        fun messageFor(error: Throwable): UserMessage = when ((error as? ApiException)?.code) {
            "qr_login_not_found" -> UserMessage(resId = R.string.qr_login_expired)
            "qr_login_used" -> UserMessage(resId = R.string.qr_login_used)
            "app_only" -> UserMessage(resId = R.string.qr_login_app_only)
            "invalid_code", "code_required" -> UserMessage(resId = R.string.qr_login_invalid_code)
            else -> error.toUserMessage()
        }.copy(isError = true)
    }
}
