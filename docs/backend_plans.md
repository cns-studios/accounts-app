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

## 4. Sendly: this app as a trusted device that approves Sendly logins

Sendly already has what's needed under `/android/...` (device registration, enrollments,
approve/reject, WebSockets for pending approvals). The app would:

1. **Sign in to Sendly**: get a token Sendly accepts (see question 1), register a device
   (`POST /android/me/devices/register`) with a fresh RSA-OAEP-2048 key pair, label
   "CNS Accounts on <model>". The private key is generated in software and stored encrypted
   with a Keystore AES-GCM key (Keystore's own RSA-OAEP only does MGF1-SHA1 before Android 14,
   which can't decrypt WebCrypto's MGF1-SHA256 envelopes).
2. **Get approved**: `POST /android/me/devices/enrollments` → the "approve this app from a
   trusted Sendly device" modal with the verification code and Abort → wait on
   `/android/me/devices/enrollments/{id}/ws` → register again to receive the identity-key
   envelope → unwrap (RSA-OAEP-2048 + AES-GCM-256, same as `crypto.js`) and store it encrypted,
   unlockable only after biometric auth.
3. **Approve others**: pending enrollments (WebSocket while open, push otherwise) show as the
   Sendly login request (device + code). Allow → biometric → unwrap identity key → wrap it for
   the requesting device's public key → `POST .../enrollments/{id}/approve` with the
   verification code, exactly like the web approve flow. Deny → `.../reject`.

**Proposed Sendly changes** (branch `cns-accounts-2fa`, plan only so far):
- Let a requesting device cancel its own pending enrollment (the modal's Abort); today only a
  trusted device can reject.
- Let a device remove itself (Disconnect / sign-out in the CNS app).
- A device kind (`approver`) so the web lists it as "CNS Accounts" and it is never offered as a
  file-access device.
- Push for new enrollments (shares section 2's channel).

**Open questions**
1. Token: use the CNS app's own access token (works today, but relies on Sendly accepting any
   CNS client's token, the open issue from the last Sendly audit), or a real Sendly login
   (`/api/auth/authorize` with the app's bearer + PKCE for the `sendly` client, which needs a
   redirect URI for the app or a public-client exchange)? The second lets Sendly then reject
   other clients' tokens.
2. The app will hold the Sendly identity key, i.e. it could decrypt the account's files (it
   won't show any). Acceptable, given it's encrypted at rest and biometric-gated?
3. What is Sendly's production `client_id` in auth (the app matches `sendly` or the name)?
