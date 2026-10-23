package com.cns.accounts.ui.history

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cns.accounts.R
import com.cns.accounts.ui.components.TokenIconKind

// User-facing names for auth-v2 audit event types, mirroring the web dashboard's
// accounts/lib/format.ts so both clients describe the same event the same way.

private class EventCopy(@param:StringRes val label: Int, @param:StringRes val hint: Int, val icon: TokenIconKind)

private val EventCopies = mapOf(
    "signup" to EventCopy(R.string.event_signup, R.string.event_signup_hint, TokenIconKind.User),
    "login_success" to EventCopy(R.string.event_login_success, R.string.event_login_success_hint, TokenIconKind.User),
    "login_failure" to EventCopy(R.string.event_login_failure, R.string.event_login_failure_hint, TokenIconKind.CircleX),
    "logout" to EventCopy(R.string.event_logout, R.string.event_logout_hint, TokenIconKind.LogOut),
    "password_changed" to EventCopy(R.string.event_password_changed, R.string.event_password_changed_hint, TokenIconKind.Shield),
    "username_changed" to EventCopy(R.string.event_username_changed, R.string.event_username_changed_hint, TokenIconKind.Edit),
    "two_factor_enabled" to EventCopy(R.string.event_2fa_enabled, R.string.event_2fa_enabled_hint, TokenIconKind.Shield),
    "2fa_enabled" to EventCopy(R.string.event_2fa_enabled, R.string.event_2fa_enabled_hint, TokenIconKind.Shield),
    "two_factor_disabled" to EventCopy(R.string.event_2fa_disabled, R.string.event_2fa_disabled_hint, TokenIconKind.Shield),
    "2fa_disabled" to EventCopy(R.string.event_2fa_disabled, R.string.event_2fa_disabled_hint, TokenIconKind.Shield),
    "avatar_updated" to EventCopy(R.string.event_avatar_updated, R.string.event_avatar_updated_hint, TokenIconKind.Image),
    "avatar_deleted" to EventCopy(R.string.event_avatar_deleted, R.string.event_avatar_deleted_hint, TokenIconKind.Trash),
    "account_deleted" to EventCopy(R.string.event_account_deleted, R.string.event_account_deleted_hint, TokenIconKind.Trash),
    "revoke_session" to EventCopy(R.string.event_revoke_session, R.string.event_revoke_session_hint, TokenIconKind.LogOut),
    "session_revoked" to EventCopy(R.string.event_revoke_session, R.string.event_revoke_session_hint, TokenIconKind.LogOut),
    "revoke_all_sessions" to EventCopy(R.string.event_revoke_all_sessions, R.string.event_revoke_all_sessions_hint, TokenIconKind.LogOut),
    "account_locked" to EventCopy(R.string.event_account_locked, R.string.event_account_locked_hint, TokenIconKind.Shield),
)

// Bookkeeping events the API still returns but that mean nothing to an end user.
private val HiddenEventTypes = setOf("refresh_reuse_detected")
private val HiddenEventPrefixes = listOf("service_data_", "token_", "auth_code_")

fun isUserFacingEvent(eventType: String): Boolean =
    eventType !in HiddenEventTypes && HiddenEventPrefixes.none(eventType::startsWith)

@Composable
fun eventTitle(eventType: String): String =
    EventCopies[eventType]?.let { stringResource(it.label) } ?: humanize(eventType)

@Composable
fun eventDescription(eventType: String): String =
    stringResource(EventCopies[eventType]?.hint ?: R.string.event_unknown_hint)

fun eventIcon(eventType: String): TokenIconKind = EventCopies[eventType]?.icon ?: TokenIconKind.User

private fun humanize(eventType: String): String =
    eventType.split('_').filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
