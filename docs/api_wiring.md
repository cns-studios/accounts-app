# API wiring (auth-v2)

The app talks to the CNS auth API from the `auth-v2` branch of `auth/` (the backend the web
dashboard in `accounts/` uses; `auth/main` is the older v1 surface). Contracts:
`accounts/API_REFERENCE.md` and `auth` @ `auth-v2`.

Base URL: `BuildConfig.CNS_API_BASE_URL`, default `https://api.cns-studios.com`, overridable with
`-PcnsApiBaseUrl=...`. `-PcnsAccountsWebUrl` sets the sign-up link. A build with any other base
URL gets the application id `com.cns.accounts.local`, so it installs next to the real app and
never shares its session or 2FA keys. Debug builds allow plain http to `localhost` and
`10.0.2.2` only. For a local auth-v2: `adb reverse tcp:3845 tcp:3845` and
`-PcnsApiBaseUrl=http://localhost:3845`.

## Sign-in

Signed out, the app shows the onboarding page (`TEMP_ONBOARDING_TOKENS.json`): Log In → the
credentials step → the 2FA step; Create Account → the web sign-up.

## Session

| What | Where |
| --- | --- |
| Sign-in | `POST /api/auth/mobile/login`, then `/api/auth/mobile/login/2fa` when `requires_2fa` |
| Refresh | `POST /api/auth/mobile/refresh` (rotating; single-flight, see `SessionManager`) |
| Sign-out | `POST /api/auth/logout` with the bearer token (best effort), then local wipe |

- Access token: memory only. Refresh token: AES-GCM with an Android Keystore key
  (`SecureStore`), excluded from backups.
- Any `401` from refresh ends the session → back to the sign-in screen, account cache cleared.
- Refreshes are serialized with a mutex: auth-v2 treats reuse of a rotated refresh token as theft
  and revokes the whole token family.

## Screens → endpoints

| UI | Endpoint(s) | Notes |
| --- | --- | --- |
| Profile header (Home, Settings) | `GET /api/me` | name, `totp_enabled` chip, `profile.avatar` image; cached on disk |
| Services card | `GET /api/me/connected-apps` | all apps listed, like the web dashboard (see "Known auth issues"); a row opens the service's detail (last used, Disconnect → `POST /api/me/connected-apps/{id}/disconnect`). Sendly's Set Up / login requests: see below |
| History tab | `GET /api/me/activity?cursor=` | paged as you scroll; labels mirror `accounts/lib/format.ts`; internal events hidden |
| Pull-to-refresh (Home/History) | the three above | |
| Edit username | `POST /api/me/username` | needs password (API requirement); new token pair is stored |
| Change avatar | `POST /api/me/avatar` (multipart) | photo picker → re-encoded to ≤1024px JPEG |
| Remove avatar | `DELETE /api/me/avatar` | |
| 2FA toggle / Configure 2FA | `POST /api/me/mfa/setup` + `/confirm`; `/disable` | see "Authenticator" below |
| Change password | `POST /api/me/password` | other sessions revoked by the API |
| Hold to delete | `POST /api/me/delete` | password, + 2FA code when enabled |
| Log out | `POST /api/auth/logout` | |

## Device-local features (no API needed)

- **Account code card** = this app is the CNS account's authenticator (`TwoFactorManager`).
  Keys are Keystore-encrypted, one per account that enabled 2FA here. The card has three states:
  - *2FA off* → "Set Up": `/api/me/mfa/setup`, then `/confirm` with a code the app generates
    from the new key itself (nothing to type); the key stays on the device.
  - *2FA on, key here* → the live code (RFC 6238, SHA1/6/30s, wall-clock periods). Copy puts it
    on the clipboard flagged sensitive.
  - *2FA on, key elsewhere* (another device, another authenticator, or this key was replaced)
    → "Transfer to this device": password + a code from the other device →
    `/mfa/disable`, then setup/confirm here. auth-v2 can't rotate a key in place, so 2FA is
    briefly off in between; if the second half fails the app says so and Set Up finishes it.
  A replaced key is detected from the activity feed (a `2fa_enabled` event later than this
  key's enrollment, compared in server time) since auth-v2 exposes no key id. Turning 2FA off
  anywhere drops the key on the next profile load. Disable 2FA / delete account / sign-in use
  the device's code instead of asking for one when the key is here.
- **Biometric unlock**: app lock on cold start and after 30s in the background
  (biometric or device credential).
- **Notifications**: FCM data pushes from auth (`PUT /api/me/push`); account events become
  notifications while the toggle is on, approval requests always do.
- **Require Security App** (`data/approvals`, auth `docs/APPROVALS.md`): the toggle enrolls an
  EC P-256 Keystore key (attested; bound to the screen lock when biometric unlock is on) with
  the password and the device's 2FA code. While on, the red request card lists what waits for
  this phone (sign-ins, password/username change, deletion, turning it off) and Allow/Deny sign
  the decision. The app approves its own sign-in and its own gated changes itself; when the
  approver is another phone it waits ("Approve sign-in"), with the 2FA code as the lost-phone
  fallback (which turns the requirement off).
- **Sendly approver** (`data/ServiceLinks.kt`, `data/sendly`, Sendly
  `docs/CNS_ACCOUNTS_APPROVER.md`): Set Up signs the app in to the `sendly` OAuth client with its
  own session (PKCE, redirect URI `cnsaccounts://sendly-callback` must be on that client),
  registers an RSA-OAEP-2048 device of kind `approver`, and waits for a trusted Sendly device to
  approve it (code shown, Abort withdraws). The identity key it receives stays Keystore-encrypted.
  New Sendly devices then show as the login request on the Sendly row; Allow wraps the identity
  key for them (screen lock first when biometric unlock is on), Deny rejects. Disconnect also
  removes the device from Sendly. Build against a local Sendly with `-PcnsSendlyUrl=...`.
- **Recovery phrase** (`ui/settings/RecoveryPhrase*`, `data/RecoveryWords.kt`): the Settings row
  opens its own page (secure window: no screenshots or recents preview). Set up / new phrase =
  password plus the 2FA code (from this device when it holds the key) or a Security App approval,
  then the 12 words (hidden until tapped), then three of them typed back
  (`/api/me/recovery-phrase/setup`, `/confirm`); Remove is gated the same way. Sign-in has "Lost
  your password or phone?" (also on the 2FA step): username + phrase + new password
  (`/api/auth/recover`), which resets the password, turns 2FA and Require Security App off and
  signs out everywhere; the user then signs in normally. Phrase fields use a password keyboard so
  the words don't end up in the keyboard's dictionary.
- **Security center**: computed on device - 2FA on, password (always), App = this device holds
  the CNS authenticator key, Phrase = a recovery phrase is set up.
- **Search**: local index over settings entries + loaded account activity.

## Not implemented - backend missing (UI kept)

| UI | What's missing in auth-v2 | Current behaviour |
| --- | --- | --- |
| Search: Help / Q&A results | Help Center at help.cns-studios.com (+ its index) | kinds and icons exist, nothing produces them |

Also not exposed in the app (API exists, no design yet): the session list / revoke
(`/api/me/sessions*`). Known API mismatches already noted for later in auth: username change
requires the password; delete requires password + code.

## Known auth issues

- `/api/me/connected-apps` aggregates an app's sessions wrongly (`ConnectedApps` in
  `internal/service/account.go`): `is_revoked` becomes true when *any* session of the app was
  revoked (e.g. one logout), and `last_used` / `revoked_at` end up as the *oldest* session's
  values. The app therefore ignores `is_revoked` (it used to hide e.g. Sendly while it was still
  connected), and a disconnected app reappears on the next refresh until auth reports
  `is_revoked` as "every session revoked" and `last_used` as the newest.

## Verification

Run on an emulator against a local auth-v2 (`git archive auth-v2`, docker compose, a test
user): sign-in, Set Up, the codes accepted by auth for sign-in and disable, the "another device"
state after a key change made through the API, Transfer, and the card falling back to Set Up
after 2FA was turned off elsewhere. Unit tests: `TotpTest` (RFC 6238 vectors) and
`TwoFactorStateTest` (card state / replaced-key detection).
