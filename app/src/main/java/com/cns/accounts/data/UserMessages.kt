package com.cns.accounts.data

import android.content.Context
import androidx.annotation.StringRes
import com.cns.accounts.R
import com.cns.accounts.data.api.ApiException
import com.cns.accounts.data.api.SessionExpiredException
import com.cns.accounts.data.approvals.ApprovalCancelledException
import com.cns.accounts.data.approvals.ApprovalDeniedException
import com.cns.accounts.data.approvals.ApprovalExpiredException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException

/**
 * A short app-wide notice, shown as a toast (ui/components/AppToast.kt). Either a string resource
 * or raw text; [isError] marks failures, which the toast shows (and buzzes) differently.
 */
data class UserMessage(@param:StringRes val resId: Int? = null, val text: String? = null, val isError: Boolean = false) {
    fun resolve(context: Context): String = resId?.let(context::getString) ?: text.orEmpty()
}

class UserMessages {
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    fun show(@StringRes resId: Int, isError: Boolean = false) {
        _messages.tryEmit(UserMessage(resId = resId, isError = isError))
    }

    fun show(error: Throwable) {
        _messages.tryEmit(error.toUserMessage().copy(isError = true))
    }
}

/** Maps a failure to copy a user can act on (auth-v2's own messages are already user-facing). */
fun Throwable.toUserMessage(): UserMessage = when (this) {
    is SessionExpiredException -> UserMessage(resId = R.string.error_session_expired)
    is UnsupportedFeatureException -> UserMessage(resId = R.string.feature_unavailable)
    is ApprovalDeniedException -> UserMessage(resId = R.string.approval_denied)
    is ApprovalExpiredException -> UserMessage(resId = R.string.approval_expired)
    is ApprovalCancelledException -> UserMessage(resId = R.string.approval_cancelled)
    is SendlyNotSetUpException -> UserMessage(resId = R.string.sendly_not_set_up)
    is SendlyNotReadyException -> UserMessage(resId = R.string.sendly_not_ready)
    is SendlyEnrollmentEndedException -> UserMessage(resId = if (expired) R.string.sendly_enrollment_expired else R.string.sendly_enrollment_denied)
    is ApiException -> when {
        isRateLimited -> UserMessage(resId = R.string.error_rate_limited)
        status >= 500 -> UserMessage(resId = R.string.error_server)
        else -> UserMessage(text = message?.replaceFirstChar { it.uppercase() })
    }
    is IOException -> UserMessage(resId = R.string.error_offline)
    else -> UserMessage(resId = R.string.error_generic)
}
