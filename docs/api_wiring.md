# API wiring (auth-v2)

The app talks to the CNS auth API from the `auth-v2` branch of `auth/` (the backend the web
dashboard in `accounts/` uses; `auth/main` is the older v1 surface). Contracts:
`accounts/API_REFERENCE.md` and `auth` @ `auth-v2`.

Base URL: `BuildConfig.CNS_API_BASE_URL`, default `https://api.cns-studios.com`, overridable with
`-PcnsApiBaseUrl=...` (e.g. `http://10.0.2.2:3845` for a local auth-v2 from the emulator — plain
http additionally needs a network security config). `-PcnsAccountsWebUrl` sets the sign-up link.

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
| Services card | `GET /api/me/connected-apps` | all apps listed, like the web dashboard (see "Known auth issues"); row → disconnect dialog → `POST /api/me/connected-apps/{id}/disconnect` |
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

- **Account code card** = standalone TOTP authenticator (one key, Keystore-encrypted). "Add"
  accepts a base32 key or an `otpauth://` link; long-press removes it. Codes are RFC 6238
  (SHA1/6/30s) and align to wall-clock periods like any authenticator.
  When 2FA is enabled from the app and the card is free (or already holds a CNS key), the new
  CNS key is stored there, so the confirm code is pre-filled; the 2FA code fields in sign-in,
  disable 2FA and delete account are pre-filled from it too. A card holding a non-CNS key is
  never overwritten — the setup dialog then shows the key and an "open in authenticator app" link.
  Turning 2FA off removes the (now dead) CNS key from the card.
- **Biometric unlock**: app lock on cold start and after 30s in the background
  (biometric or device credential).
- **Security center**: computed on device — 2FA on, password (always), App = this device holds
  the CNS authenticator key, recovery phrase (never; see below).
- **Search**: local index over settings entries + loaded account activity.

## Not implemented — backend missing (UI kept)

| UI | What's missing in auth-v2 | Current behaviour |
| --- | --- | --- |
| Sign-in request card (Allow/Deny) | push/approval flow: list pending sign-ins, approve/deny, "check your phone" login step | `SignInRequests` stub never yields a request, so the card is hidden; Allow/Deny would report "not available" |
| Strong account protection toggle | the protection bundle (email for codes, enforced 2FA, recovery phrase, required app…) | toggle stays off, snackbar "not available yet" |
| Require Security App toggle | depends on the approval flow above | same |
| Recovery phrase "Setup" | recovery phrase storage/verification | same |
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

## Not verified here

Signed-in flows were not run against a live backend from this machine (no test account). The
TOTP implementation is unit-tested against the RFC 6238 vectors (`TotpTest`); sign-in screen and
its validation were checked on an emulator.
