package com.cns.accounts.data.api

import org.json.JSONObject

/**
 * An error the API answered with, normalized from auth-v2's `{"error":{"code","message"}}`
 * envelope. Transport failures stay [java.io.IOException]s so callers can tell "offline" apart
 * from "the server said no".
 */
class ApiException(
    val status: Int,
    val code: String,
    message: String,
) : Exception(message) {

    val isUnauthorized: Boolean get() = status == 401
    val isRateLimited: Boolean get() = status == 429

    companion object {
        fun from(response: HttpResponse): ApiException {
            val fallbackCode = if (response.status == 401) "unauthorized" else "request_failed"
            val error = runCatching { JSONObject(response.body).optJSONObject("error") }.getOrNull()
            return ApiException(
                status = response.status,
                code = error?.optString("code")?.takeIf { it.isNotBlank() } ?: fallbackCode,
                message = error?.optString("message")?.takeIf { it.isNotBlank() } ?: "Request failed (${response.status})",
            )
        }
    }
}

/** Thrown when the stored session can no longer be refreshed; the app returns to sign-in. */
class SessionExpiredException : Exception("Your session expired. Please sign in again.")
