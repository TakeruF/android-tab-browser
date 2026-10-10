# Distribution and in-app updates

Nagi is distributed through [GitHub Releases](https://github.com/TakeruF/android-tab-browser/releases) and [takeruf.com/nagi](https://takeruf.com/nagi). The package is `com.takeruf.nagi`. Version 0.3.0 has `versionCode=7`, minSdk 26, targetSdk 36, and a signed, R8-optimized APK with `debuggable=false`.

See the dated [0.3.0 publication verification](RELEASE_0.3.0_VERIFICATION.md) for artifact checks and Play submission status.

## Signing

Release signing reads the Properties file specified by `NAGI_SIGNING_PROPERTIES`, defaulting to `~/.config/nagi/release-signing.properties`. Required fields are `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. A Release build without signing configuration must fail; do not distribute unsigned APKs.

Version 0.3.0 uses the same distribution key as the 0.1.0 APK published at takeruf.com/nagi. Verify the original public APK's package, version, and certificate fingerprint when configuring signing. Keeping the same package and signature allows an in-place update without uninstalling. Local Debug builds have a different signature and cannot update to this distribution in place. Keep using the distribution key for future releases.

The signing configuration and keystore are outside Git in `~/.config/nagi/`, with file permissions 600. Back up the private key and passwords securely. Publish only the certificate fingerprint and APK checksum.

Signing-certificate SHA-256: `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`.

The former `com.orbit.browser` package and any other application ID are separate apps under Android's update rules.

## Update flow

Once on startup, and when requested from Settings → App updates, the app fetches `releases/latest/download/update.json` over HTTPS. Check failures do not block browsing. A higher version code displays an update button; downloading starts only after user interaction.

The APK is stored in the app's private cache. Checks cover size, SHA-256, package, version name/code, minSdk, and the installed app's signing certificate. Different signatures/packages, downgrades, and corrupt files never reach the installer. Verification runs again immediately before installation. Partial files are deleted after completion, failure, or cancellation.

On Android 8+, the app opens the install-from-this-source setting when needed, then Android's standard installation confirmation. Installation is not automatic in the background. Version 0.1.0 has no updater: install 0.1.1 manually, then use in-app checks for future versions.

## GitHub and Google Play variants

The `distribution` flavor dimension provides `github` and `play` variants. Both keep the application ID `com.takeruf.nagi`, the existing database, and shared browser features.

- `github`: `./gradlew :app:assembleGithubRelease`; APK at `app/build/outputs/apk/github/release/app-github-release.apk`. The APK updater, installer, FileProvider, and `REQUEST_INSTALL_PACKAGES` are included only in this source set.
- `play`: `./gradlew :app:bundlePlayRelease`; AAB at `app/build/outputs/bundle/playRelease/app-play-release.aab`. There is no APK update transport, installer, install permission, or update FileProvider. Settings opens the app's Google Play listing; Play manages delivery and updates.

The current Play app-signing certificate differs from the GitHub distribution certificate, so installed users must stay within their current channel for an in-place update. The same application ID does not by itself guarantee cross-channel upgrades. To preserve installed GitHub users' data when moving to Play, configure Play App Signing using the existing distribution signing key; a distinct Play app-signing certificate prevents in-place upgrades. The upload certificate and Play app-signing certificate serve different roles. Before uploading, check the Play certificate and use a version code higher than any already uploaded artifact. Do not change the existing distribution key to solve a Play signing mismatch.

The privacy policy is hosted at `https://takeruf.com/nagi/privacy` with English, Japanese, Simplified Chinese, and Korean pages. See [Play Console preparation](PLAY_CONSOLE.md) for declarations and store copy.

Verification commands:

```sh
./gradlew :app:testGithubDebugUnitTest :app:testPlayDebugUnitTest
./gradlew :app:lintGithubRelease :app:lintPlayRelease
./gradlew :app:assembleGithubRelease :app:bundlePlayRelease
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedPlayDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.takeruf.nagi.ReleaseFeaturesUiTest
```

## Publication

1. Increment version name/code and write English release notes.
2. Verify unit/instrumentation tests, Lint, and the signed Release.
3. Commit only relevant changes and generate the APK from a clean checkout of that commit.
4. Run `scripts/prepare_release.py APK --notes RELEASE_NOTES --out OUTPUT_DIR` to produce the APK, `update.json`, and `SHA256SUMS`.
5. Push the commit and upload all three assets to a GitHub Release tagged `v<versionName>`.
6. Re-fetch the public JSON/APK and verify checksum, signature, package, and version. Check the app's update flow too.
7. Update the website's APK, displayed version/size/checksum, feature descriptions, and installation guidance; verify the deployed page and downloaded bytes.

`update.json` schema:

```json
{
  "applicationId": "com.takeruf.nagi",
  "versionCode": 7,
  "versionName": "0.3.0",
  "minSdk": 26,
  "apkUrl": "https://github.com/TakeruF/android-tab-browser/releases/download/v0.3.0/nagi-0.3.0.apk",
  "sha256": "64 lowercase hexadecimal characters",
  "size": 123,
  "notes": "English release notes"
}
```

The updater uses public GitHub HTTPS assets with no embedded API key or GitHub token. The host sees the source IP and Nagi-version User-Agent, but receives no browsing history or search queries. When GitHub is unreachable, display an error and allow retry.
