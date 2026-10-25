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
    "signup_request" to EventCopy(R.string.event_signup_request, R.string.event_signup_request_hint, TokenIconKind.User),
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
    "require_app_enabled" to EventCopy(R.string.event_require_app_enabled, R.string.event_require_app_enabled_hint, TokenIconKind.Shield),
    "require_app_disabled" to EventCopy(R.string.event_require_app_disabled, R.string.event_require_app_disabled_hint, TokenIconKind.Shield),
    "approval_bad_signature" to EventCopy(R.string.event_approval_bad_signature, R.string.event_approval_bad_signature_hint, TokenIconKind.CircleX),
    "recovery_phrase_created" to EventCopy(R.string.event_recovery_phrase_created, R.string.event_recovery_phrase_created_hint, TokenIconKind.Shield),
    "recovery_phrase_removed" to EventCopy(R.string.event_recovery_phrase_removed, R.string.event_recovery_phrase_removed_hint, TokenIconKind.Shield),
    "recovery_phrase_used" to EventCopy(R.string.event_recovery_phrase_used, R.string.event_recovery_phrase_used_hint, TokenIconKind.Shield),
    "recovery_failed" to EventCopy(R.string.event_recovery_failed, R.string.event_recovery_failed_hint, TokenIconKind.CircleX),
    "admin_action" to EventCopy(R.string.event_admin_action, R.string.event_admin_action_hint, TokenIconKind.Shield),
    "qr_login_cancelled" to EventCopy(R.string.event_qr_login_cancelled, R.string.event_qr_login_cancelled_hint, TokenIconKind.CircleX),
)

// "approval": one Require Security App request (auth-v2 folds its requested/decided/used rows
// into one), described by what it was for and how it ended.
private val ApprovalSubjects = mapOf(
    "login" to R.string.approval_subject_login,
    "authorize" to R.string.approval_subject_authorize,
    "password_change" to R.string.approval_subject_password_change,
    "username_change" to R.string.approval_subject_username_change,
    "account_delete" to R.string.approval_subject_account_delete,
    "require_app_disable" to R.string.approval_subject_require_app_disable,
    "recovery_phrase_create" to R.string.approval_subject_recovery_phrase_create,
    "recovery_phrase_remove" to R.string.approval_subject_recovery_phrase_remove,
    "qr_login" to R.string.approval_subject_qr_login,
)

private class OutcomeCopy(@param:StringRes val word: Int, @param:StringRes val hint: Int)

private val ApprovalOutcomes = mapOf(
    "approved" to OutcomeCopy(R.string.approval_outcome_approved, R.string.approval_outcome_approved_hint),
    "denied" to OutcomeCopy(R.string.approval_outcome_denied, R.string.approval_outcome_denied_hint),
    "expired" to OutcomeCopy(R.string.approval_outcome_expired, R.string.approval_outcome_expired_hint),
    "cancelled" to OutcomeCopy(R.string.approval_outcome_cancelled, R.string.approval_outcome_cancelled_hint),
    "pending" to OutcomeCopy(R.string.approval_outcome_pending, R.string.approval_outcome_pending_hint),
)

private fun outcome(data: Map<String, String>) = ApprovalOutcomes[data["status"]] ?: ApprovalOutcomes.getValue("pending")

// Bookkeeping events the API still returns but that mean nothing to an end user.
private val HiddenEventTypes = setOf("refresh_reuse_detected", "login_challenge_approval")
private val HiddenEventPrefixes = listOf("service_data_", "token_", "auth_code_")

fun isUserFacingEvent(eventType: String): Boolean =
    eventType !in HiddenEventTypes && HiddenEventPrefixes.none(eventType::startsWith)

@Composable
fun eventTitle(eventType: String, data: Map<String, String> = emptyMap()): String = when {
    eventType == "approval" -> {
        val client = data["client_name"]
        val subject = if (data["action"] == "authorize" && !client.isNullOrBlank()) {
            stringResource(R.string.approval_subject_authorize_to, client)
        } else {
            stringResource(ApprovalSubjects[data["action"]] ?: R.string.approval_subject_other)
        }
        stringResource(R.string.approval_event_title, subject, stringResource(outcome(data).word))
    }
    eventType == "recovery_phrase_created" && data["replaced"] == "true" -> stringResource(R.string.event_recovery_phrase_replaced)
    eventType == "login_success" && data["via"] == "qr" -> stringResource(R.string.event_login_qr)
    eventType == "login_success" && !data["client_name"].isNullOrBlank() -> stringResource(R.string.event_login_app, data.getValue("client_name"))
    else -> EventCopies[eventType]?.let { stringResource(it.label) } ?: humanize(eventType)
}

@Composable
fun eventDescription(eventType: String, data: Map<String, String> = emptyMap()): String = when {
    eventType == "approval" -> stringResource(outcome(data).hint)
    eventType == "require_app_disabled" && data["via"] == "2fa_code" -> stringResource(R.string.event_require_app_disabled_code_hint)
    eventType == "recovery_phrase_created" && data["replaced"] == "true" -> stringResource(R.string.event_recovery_phrase_replaced_hint)
    eventType == "login_success" && data["via"] == "qr" -> stringResource(R.string.event_login_qr_hint)
    eventType == "login_success" && !data["client_name"].isNullOrBlank() -> stringResource(R.string.event_login_app_hint, data.getValue("client_name"))
    else -> stringResource(EventCopies[eventType]?.hint ?: R.string.event_unknown_hint)
}

fun eventIcon(eventType: String): TokenIconKind = when (eventType) {
    "approval" -> TokenIconKind.Shield
    else -> EventCopies[eventType]?.icon ?: TokenIconKind.User
}

private fun humanize(eventType: String): String =
    eventType.split('_').filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
