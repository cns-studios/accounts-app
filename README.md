# CNS Accounts

The Android app for CNS accounts. It signs in to your CNS account and serves as its
authenticator and Security App.

## Features

- **Authenticator**: the app can hold your account's 2FA key and show the current code on Home.
  Signing in on this phone fills the code in for you.
- **Require Security App**: sign-ins and high-risk actions on your account are approved or denied
  in the app. Each decision is signed with a key that never leaves the phone.
- **Recovery phrase**: create a 12-word phrase that resets your account if you lose access. You
  can write it down or save it as a file.
- **History**: your account's activity, such as sign-ins, 2FA changes and approvals.
- **Notifications**: push notifications for account events and approval requests (Firebase Cloud
  Messaging).
- **Sendly**: the app can be a trusted Sendly device and approve Sendly logins.
- **App lock**: biometric unlock with a configurable lock delay.
- **Updates**: release builds check GitHub releases for updates and install them in the app.

The app won't log out while it is the account's authenticator or Security App. Signed out, it
could show no codes and answer no approvals, so move these to another device or turn them off
first.

## Building

You need JDK 21 and the Android SDK. Point to the SDK with `local.properties` or `ANDROID_HOME`.

```sh
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:lintDebug            # lint
```

Push notifications need Firebase configuration. Put a `google-services.json` in `app/` (it is
git-ignored), or pass the values as Gradle properties: `cnsFirebaseAppId`, `cnsFirebaseApiKey`,
`cnsFirebaseProjectId` and `cnsFirebaseSenderId`. Without them the app builds, but push
notifications are off.

### Against a local backend

The app talks to the production services by default. Override them with Gradle properties:

| Property            | Default                            |
| ------------------- | ---------------------------------- |
| `cnsApiBaseUrl`     | `https://api.cns.ad`      |
| `cnsAccountsWebUrl` | `https://accounts.cns.ad` |
| `cnsSendlyUrl`      | `https://sendly.zip`   |

For example, for a local auth-v2 server reached from the emulator:

```sh
./gradlew :app:installDebug -PcnsApiBaseUrl=http://10.0.2.2:3845
```

Plain-http hosts also need an entry in the network security config. A build with any other API URL gets the `com.cns.accounts.local` application ID, so it installs
beside the production app instead of replacing it.

## CI and releases

- `.github/workflows/ci.yml` runs the unit tests, lint and a debug build on every pull request
  and every push to `main`.
- `.github/workflows/release.yml` builds a signed release APK when a `vMAJOR.MINOR.PATCH` tag is
  pushed, and publishes it as a GitHub release.

[docs/releasing.md](docs/releasing.md) covers versioning, the signing key, and how installed
apps update.

## License

GPL-3.0. See [LICENSE](LICENSE).
