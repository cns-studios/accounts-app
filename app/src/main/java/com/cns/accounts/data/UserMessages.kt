package com.cns.accounts.data

import android.content.Context
import androidx.annotation.StringRes
import com.cns.accounts.R
import com.cns.accounts.data.api.ApiException
import com.cns.accounts.data.api.SessionExpiredException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException

/** A short app-wide notice, shown in the root snackbar host. Either a string resource or raw text. */
data class UserMessage(@param:StringRes val resId: Int? = null, val text: String? = null) {
    fun resolve(context: Context): String = resId?.let(context::getString) ?: text.orEmpty()
}

class UserMessages {
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    fun show(@StringRes resId: Int) {
        _messages.tryEmit(UserMessage(resId = resId))
    }

    fun show(error: Throwable) {
        _messages.tryEmit(error.toUserMessage())
    }
}

/** Maps a failure to copy a user can act on (auth-v2's own messages are already user-facing). */
fun Throwable.toUserMessage(): UserMessage = when (this) {
    is SessionExpiredException -> UserMessage(resId = R.string.error_session_expired)
    is UnsupportedFeatureException -> UserMessage(resId = R.string.feature_unavailable)
    is ApiException -> when {
        isRateLimited -> UserMessage(resId = R.string.error_rate_limited)
        status >= 500 -> UserMessage(resId = R.string.error_server)
        else -> UserMessage(text = message?.replaceFirstChar { it.uppercase() })
    }
    is IOException -> UserMessage(resId = R.string.error_offline)
    else -> UserMessage(resId = R.string.error_generic)
}
