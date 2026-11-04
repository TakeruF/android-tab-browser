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

## Preparation and publication

1. Increment versionName/versionCode and prepare release notes in English, Japanese, Simplified Chinese and Korean.
2. Verify relevant unit, instrumentation, lint and signed runtime checks. Review the complete candidate diff and preserve unrelated work.
3. Commit the intended source; rebuild the Play AAB from the selected clean revision and record its hash/signature. Create an immutable source tag `v<versionName>` on that revision. Never move old release tags.
4. Keep new GitHub release notes as a draft until their publication state is appropriate. Link to Play and clearly separate source version from store availability. Do not attach the AAB or publish a new APK/updater manifest.
5. In Play Console, inspect the current app, signing, version codes, track and pending changes. Upload the AAB, add four-language notes, retain intended countries/device support and resolve validation errors.
6. Save the production release as a draft. Check Publishing overview and record the exact remaining action. If the request is preparation only, stop before sending changes for review, starting a rollout or publishing. When the user explicitly authorizes a release, submit the verified change for review and complete the authorized rollout steps; report pending review separately from actual store availability.
7. Separately report local source/tag, GitHub draft, website/profile deployment, accepted bundle, Console saved draft, review submission and actual store availability. Preparation proves only the stages reached.

## Manual release workflow (GitHub Actions)

[`.github/workflows/play-release.yml`](../.github/workflows/play-release.yml) automates steps 3–5 above. It has **only** a `workflow_dispatch` trigger; merging or pushing to `main` never builds or uploads. It runs only from `main` or `release/*`, one run at a time, in the `play-release` environment (add required reviewers there for a second approval gate).

Inputs:

| Input | Meaning |
| --- | --- |
| `release_notes` (required) | Play Console tags (`<en-US>…</en-US><ja-JP>…</ja-JP>…`), plain text (treated as en-US), or `@docs/play-store/release-notes-X.Y.Z.txt`. The web form is single-line: use `\n` for line breaks. Each language must be ≤500 characters. |
| `track` | `internal` (default), `alpha` (closed), `beta` (open) or `production`. |
| `status` | `draft` (default; saved in Console only) or `completed` (roll out, after review when required). |
| `send_for_review` | Default `false`: committed changes wait for **Send for review** in Play Console. |
| `run_unit_tests` | Default `true`: run `testPlayDebugUnitTest` before building. |

```sh
gh workflow run play-release.yml --ref main \
  -f release_notes=@docs/play-store/release-notes-1.1.7.txt -f track=production -f status=draft
```

Pipeline: refuse other branches → read `versionName`/`versionCode` from `app/build.gradle.kts` → parse notes → refuse a versionCode already in the changelog or an existing `v<versionName>` tag on another commit → unit tests → build `bundlePlayRelease` with secrets-provided signing → verify the AAB signature and the upload-certificate SHA-256 above → upload bundle + notes to the track through the Play Developer API (the API's reported versionCode must match) → append to [`docs/releases/changelog.json`](releases/changelog.json) on `main` → optionally POST the same entry to an external store → create the `v<versionName>` tag if missing. The AAB and changelog entry are also kept as workflow artifacts. A successful run proves only that Play accepted the bundle; check Console for review and availability.

Required secrets, stored as environment secrets of `play-release` (Settings → Environments → play-release; repository secrets also work):

| Secret | Content |
| --- | --- |
| `NAGI_UPLOAD_KEYSTORE_BASE64` | `base64 -i upload.jks` of the upload keystore |
| `NAGI_UPLOAD_STORE_PASSWORD` | keystore password (`storePassword`) |
| `NAGI_UPLOAD_KEY_ALIAS` | `keyAlias` |
| `NAGI_UPLOAD_KEY_PASSWORD` | `keyPassword` |
| `PLAY_SERVICE_ACCOUNT_JSON` | JSON key of a Google Cloud service account invited in Play Console → Users and permissions with release permissions for Nagi |
| `CHANGELOG_WEBHOOK_URL` / `CHANGELOG_WEBHOOK_TOKEN` | optional; endpoint receiving each changelog entry as JSON POST (Bearer token) |

Optional repository variable `NAGI_UPLOAD_CERT_SHA256` overrides the expected upload-certificate fingerprint after an approved upload-key reset. Changelog entries contain `versionName`, `versionCode`, `releasedAt` (UTC), `track`, `status`, `commit`, `runUrl`, `aabSha256` and per-language `releaseNotes`. Helper logic lives in `scripts/play_release.py` (tests: `python3 -m unittest scripts/tests/test_play_release.py`).

Privacy policy: `https://takeruf.com/nagi/privacy`, with English, Japanese, Simplified Chinese and Korean bodies. See [Play Console preparation](PLAY_CONSOLE.md) for declarations and store copy. Existing declarations must be checked against the exact candidate; no unrelated privacy/targeting changes are implied by distribution consolidation.
