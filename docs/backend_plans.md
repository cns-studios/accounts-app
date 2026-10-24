# Backend plans (not implemented, need sign-off)

Everything here changes `auth/` or `Sendly/` and touches production accounts or encryption keys,
so it is a proposal only. The app side already has the UI; each section lists what it would
wire up and the open questions.

## 1. Small auth fixes (low risk)

**Connected apps aggregation** (`internal/service/account.go`, `ConnectedApps`): `is_revoked` is
set as soon as any one of an app's sessions was revoked, and `last_used` / `revoked_at` end up
as the oldest session's values (families come newest-first and every row overwrites). Fix:
`is_revoked` = every family revoked; `last_used` = max `last_token_at`; `revoked_at` = latest.
The app then hides revoked apps again (today a disconnected app comes back on refresh). Test:
`tests/functional/oauth.test.js` already covers connected apps; add a case with one revoked and
one live family.

**2FA key identity in `/api/me`**: add `totp_enabled_at` (when the current key was confirmed).
The app currently infers "this device's key was replaced" from the activity feed; with this
field it compares directly. No secret material is exposed.

## 2. Notifications (2FA on/off, password/username change, new sign-ins)

The app can render notifications; the question is how events reach a phone that isn't running
the app. Options:

| Option | Reliability | Backend work | Notes |
| --- | --- | --- | --- |
| **FCM push** (recommended) | high, wakes the app | auth: store push tokens per session (`POST/DELETE /api/me/push-token`), send on audit events | needs a Firebase project; payload carries only an event id/type, the app fetches details over the API, so Google never sees account data |
| UnifiedPush / ntfy | high | same as FCM + a push server | no Google dependency, needs a distributor app on the phone |
| WorkManager polling of `/api/me/activity` | low (15 min minimum, delayed in Doze) | none | fine as a fallback without Play services, not for "someone just signed in" |
| Foreground service + WebSocket | high | auth: event stream | permanent notification, battery cost |

Recommendation: FCM, with polling as the fallback when Play services are missing. Events caused
by this same device (you changed the password here) are not notified. The same push channel
carries sign-in approvals (section 3) and Sendly login requests (section 4).

## 3. Require Security App (signed approvals) — implemented

Built as agreed: one approver device, TOTP as the lost-phone fallback (using it turns the
requirement off), no number matching, the fingerprint/screen lock only when the app's biometric
setting is on. Server side: auth `docs/APPROVALS.md`; app side: `data/approvals` (see
`docs/api_wiring.md`).

## 4. Sendly: this app as a trusted device that approves Sendly logins — implemented

Built as agreed: a Sendly-scoped token through the `Sendly` OAuth client (needs the redirect URI
`cnsaccounts://sendly-callback` on that client), the identity key kept on the phone encrypted
under a Keystore key, client id `Sendly`. Server side: Sendly `docs/CNS_ACCOUNTS_APPROVER.md`;
app side: `docs/api_wiring.md`.
