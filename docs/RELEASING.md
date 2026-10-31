# Google Play distribution and release preparation

Starting with **1.1.1 / versionCode 10**, Google Play is Nagi's sole official distribution channel. Package: `com.takeruf.nagi`, minSdk 26, targetSdk 36. [Install and update through Google Play](https://play.google.com/store/apps/details?id=com.takeruf.nagi).

GitHub holds source, version tags and release notes. Existing APKs and updater manifests through 1.1.0 remain historical archives; new releases do not publish an APK, `update.json` or APK checksum for sideloading. Website and profile install actions point to Play. A source tag or GitHub note does not establish Play availability.

## Signing and migration

Signing reads `NAGI_SIGNING_PROPERTIES`, defaulting to `~/.config/nagi/release-signing.properties`. Required fields: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Keep signing material outside Git and never log passwords or private keys. A signed Release build must fail if configuration is missing.

The existing local distribution/upload-certificate SHA-256 is `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`. The upload key and Google Play app-signing key are different roles. Verify the Console upload certificate and the highest previously uploaded versionCode before each upload; do not change keys or the application ID to solve a mismatch.

The previously checked Play app-signing certificate differs from the GitHub/website APK certificate. The same application ID therefore does **not** guarantee an in-place switch. Android can reject the update. Uninstalling removes app-private tabs, bookmarks, settings, cookies and website storage. There is no verified automatic cross-channel workspace migration; do not instruct users to uninstall as a routine update step. The former `com.orbit.browser` package is also a separate app. Reconfirm Console signing before making a new migration claim.

## Build and verification

The `play` variant has no GitHub APK update transport, installer, `REQUEST_INSTALL_PACKAGES` or update FileProvider. Settings directs users to Google Play. The legacy `github` source set is retained solely for historical reproducibility and regression checks; its existence is not an active distribution channel. `scripts/prepare_release.py` rejects APK versions newer than 1.1.0.

```sh
./gradlew :app:testPlayDebugUnitTest :app:lintPlayDebug :app:lintPlayRelease --no-parallel
./gradlew :app:assemblePlayDebug :app:assemblePlayDebugAndroidTest --no-parallel
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedPlayDebugAndroidTest --no-parallel
./gradlew :app:bundlePlayRelease --no-parallel
```

Upload artifact: `app/build/outputs/bundle/playRelease/app-play-release.aab`. Developer/test APKs are local validation artifacts, not public downloads. Verify the bundle signature, version/package, non-debuggable flag, bundletool validation, native alignment, license assets and Play manifest boundaries. Compare source and installed-artifact evidence separately; 16KB packaging alignment does not establish 16KB runtime execution.

The [1.1.1 preflight](RELEASE_1.1.1_VERIFICATION.md) records the tested working-tree candidate; clean release-source builds and Console preparation are recorded separately. [1.1.0 verification](RELEASE_1.1.0_VERIFICATION.md) remains a dated record of the former dual-channel publication.

## Preparation and stop point

1. Increment versionName/versionCode and prepare release notes in English, Japanese, Simplified Chinese and Korean.
2. Verify relevant unit, instrumentation, lint and signed runtime checks. Review the complete candidate diff and preserve unrelated work.
3. Commit the intended source; rebuild the Play AAB from the selected clean revision and record its hash/signature. Create an immutable source tag `v<versionName>` on that revision. Never move old release tags.
4. Keep new GitHub release notes as a draft until their publication state is appropriate. Link to Play and clearly separate source version from store availability. Do not attach the AAB or publish a new APK/updater manifest.
5. In Play Console, inspect the current app, signing, version codes, track and pending changes. Upload the AAB, add four-language notes, retain intended countries/device support and resolve validation errors.
6. Save the production release as a draft. Check Publishing overview and record the exact remaining action. **For the current request, stop before sending changes for review, starting a rollout or publishing.** Do not click those actions.
7. Separately report local source/tag, GitHub draft, website/profile deployment, accepted bundle, Console saved draft, review submission and actual store availability. Preparation proves only the stages reached.

Privacy policy: `https://takeruf.com/nagi/privacy`, with English, Japanese, Simplified Chinese and Korean bodies. See [Play Console preparation](PLAY_CONSOLE.md) for declarations and store copy. Existing declarations must be checked against the exact candidate; no unrelated privacy/targeting changes are implied by distribution consolidation.
