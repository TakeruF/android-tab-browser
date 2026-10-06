# Distribution and in-app updates

Nagi is distributed through [GitHub Releases](https://github.com/TakeruF/android-tab-browser/releases) and [takeruf.com/nagi](https://takeruf.com/nagi). The package is `com.takeruf.nagi`. Version 0.1.1 has `versionCode=2`, minSdk 26, and a signed, R8-optimized APK with `debuggable=false`.

## Signing

Release signing reads the Properties file specified by `NAGI_SIGNING_PROPERTIES`, defaulting to `~/.config/nagi/release-signing.properties`. Required fields are `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. A Release build without signing configuration must fail; do not distribute unsigned APKs.

Version 0.1.1 uses the same distribution key as the 0.1.0 APK published at takeruf.com/nagi. Verify the original public APK's package, version, and certificate fingerprint when configuring signing. Keeping the same package and signature allows an in-place update without uninstalling. Local Debug builds have a different signature and cannot update to this distribution in place. Keep using the distribution key for future releases.

The signing configuration and keystore are outside Git in `~/.config/nagi/`, with file permissions 600. Back up the private key and passwords securely. Publish only the certificate fingerprint and APK checksum.

Signing-certificate SHA-256: `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`.

The former `com.orbit.browser` package and any other application ID are separate apps under Android's update rules.

## Update flow

Once on startup, and when requested from Settings → App updates, the app fetches `releases/latest/download/update.json` over HTTPS. Check failures do not block browsing. A higher version code displays an update button; downloading starts only after user interaction.

The APK is stored in the app's private cache. Checks cover size, SHA-256, package, version name/code, minSdk, and the installed app's signing certificate. Different signatures/packages, downgrades, and corrupt files never reach the installer. Verification runs again immediately before installation. Partial files are deleted after completion, failure, or cancellation.

On Android 8+, the app opens the install-from-this-source setting when needed, then Android's standard installation confirmation. Installation is not automatic in the background. Version 0.1.0 has no updater: install 0.1.1 manually, then use in-app checks for future versions.

A future Google Play distribution should use a separate flavor with Play's update mechanism instead of the GitHub APK channel and `REQUEST_INSTALL_PACKAGES`.

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
  "versionCode": 2,
  "versionName": "0.1.1",
  "minSdk": 26,
  "apkUrl": "https://github.com/TakeruF/android-tab-browser/releases/download/v0.1.1/nagi-0.1.1.apk",
  "sha256": "64 lowercase hexadecimal characters",
  "size": 123,
  "notes": "English release notes"
}
```

The updater uses public GitHub HTTPS assets with no embedded API key or GitHub token. The host sees the source IP and Nagi-version User-Agent, but receives no browsing history or search queries. When GitHub is unreachable, display an error and allow retry.
