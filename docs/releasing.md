# Releasing

Production builds come only from CI (`.github/workflows/release.yml`).

1. Make sure `main` is green (`.github/workflows/ci.yml` runs unit tests, lint and a debug build
   on every pull request and push).
2. Tag the commit and push the tag:

   ```sh
   git tag v1.2.0
   git push origin v1.2.0
   ```

   Tags are `vMAJOR.MINOR.PATCH` with MINOR and PATCH below 100. The version code is
   `MAJOR*10000 + MINOR*100 + PATCH`, so every release must be higher than the last.
3. CI runs the unit tests, builds the release APK signed with the release key, checks the
   signing certificate against `RELEASE_CERT_SHA256`, and publishes a GitHub release with
   `cns-accounts-<version>.apk` and its `.sha256`.

Installed apps check the latest release once a day and when opened (at most every 12 hours),
and from App Settings > Check for updates. They download the APK, compare its SHA-256 with the
release, and hand it to Android's installer, which also requires the release signature.

## Signing key

The release keystore and its credentials are kept outside the repository; CI gets them from the
repository secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`
and `RELEASE_KEY_PASSWORD`. Keep an offline backup of the keystore and its credentials: if they
are lost, no update can be installed over existing installs again.

Builds made before the release key existed are debug-signed. Android refuses to update those
with a release-signed APK, so they need a one-time uninstall and reinstall (which clears the
app's data: move the authenticator and Require Security App off the device first).

Debug builds, `.local` builds and release builds without the release key never check for
updates (`BuildConfig.UPDATES_ENABLED`).
