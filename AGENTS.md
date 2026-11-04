# Agent guidance

## Project and scope

- Nagi is an Android phone/tablet workspace browser built with Kotlin, Jetpack Compose, WebView, Room, and DataStore. Package/application ID: `com.takeruf.nagi`.
- Read `README.md` for setup, `docs/ARCHITECTURE.md` for ownership and persistence, and `docs/VALIDATION.md` for feature-specific checks. Consult the relevant feature document under `docs/` before changing its behavior.
- Inspect `git status --short` before editing. Preserve existing staged, unstaged, and untracked work; keep the requested change narrow. Do not reset, clean, uninstall apps, clear app data, or remove artifacts as routine setup.
- Use `TakeruF` for public attribution. Keep personal details, credentials, keystores, and signing-property contents out of source, logs, and documentation.

## Build and verification

Use the included Gradle Wrapper with JDK 17 and Android SDK Platform 36. Configure the SDK through `ANDROID_HOME` or ignored `local.properties`. The minimum Android API is 26.

```sh
# Unit tests and lint for the active distribution flavor
./gradlew :app:testPlayDebugUnitTest :app:lintPlayDebug :app:lintPlayRelease --no-parallel

# Development APK and instrumentation APK
./gradlew :app:assemblePlayDebug :app:assemblePlayDebugAndroidTest --no-parallel

# Dedicated QA device; replace the example serial explicitly
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedPlayDebugAndroidTest --no-parallel
```

- Development APK: `app/build/outputs/apk/play/debug/app-play-debug.apk`.
- Run checks appropriate to the change. For focused unit tests, append `--tests 'com.takeruf.nagi.ExampleTest'` to the unit-test task. For focused instrumentation, use `-Pandroid.testInstrumentationRunnerArguments.class=com.takeruf.nagi.ExampleTest` with the connected-test task.
- Prefer repeatable local HTML fixtures in `app/src/androidTest/java/com/takeruf/nagi/FixtureServer.kt` for WebView regressions. Live-provider checks establish a separate, time-sensitive result.
- Select a dedicated QA device explicitly (`adb -s <serial>` / `ANDROID_SERIAL`). Avoid touching personal-device data or changing emulator/provider configuration unnecessarily.
- For UI changes, inspect the affected phone/narrow-window and tablet/wide-window states, including Split and light/dark themes when relevant. Exercise real WebView behavior when the change concerns page rendering or lifecycle.
- Put generated APKs, screenshots, and logs in ignored `artifacts/` or build directories. Record compact evidence rather than committing raw output.
- Report exactly what ran and any failures/skips. Focused tests, full-suite tests, emulator checks, physical-device checks, signed artifacts, and store publication are separate claims. Documentation-only changes do not require an Android build; check links/commands and run `git diff --check`.

## Architecture and behavior to preserve

- Room is the workspace source of truth. Keep tab/Space selection, ordering, close/restore, and move operations transactional in repositories.
- `BrowserViewModel` must not retain Activity, View, or WebView references. Keep WebView configuration/API calls in `WebViewBrowserEngine` and native platform operations behind host/rendering contracts.
- `BrowserSessionController` and `EnginePool` own live sessions. Preserve tab engines across tab/Space switches and settings navigation; protect both visible Split panes and pages with active media, edited forms, or pending native operations from suspension. Do not use process-wide `WebView.pauseTimers()` to pause one tab.
- Preserve the existing `orbit.db` database identity and upgrade data. Export Room schemas under `app/schemas/` and provide migration coverage for schema changes; do not introduce destructive migration.
- DataStore preference updates should use atomic `edit` operations. Add settings through the model, persistence, and UI with defaults that preserve existing users' choices.
- Preserve saved left/right Split pairs, target-tab close behavior, and the distinction between drag insertion/reordering and Split drop targets. Use the corresponding repository and UI tests when changing these paths.
- Keep CJK IME text, selection, and composition together. Enter/arrows/Escape during composition must not navigate or commit a stale Command Bar suggestion.
- Preserve page state and drafts when resizing or changing the app theme. Distinguish retaining a live WebView from restoration after Activity/process death.
- Private browsing uses a separate in-memory workspace and supported isolated WebView profiles. Do not fall back to the normal profile when isolation is unavailable.

## Localization and feature boundaries

- Update user-facing strings together in `app/src/main/res/values/strings.xml`, `values-ja/strings.xml`, `values-ko/strings.xml`, and `values-zh/strings.xml`. Preserve numbered placeholders and provide accessibility descriptions for new controls.
- Check `docs/LOCALIZATION.md` for display-time translation and persisted-name behavior. Do not rewrite user-defined or saved values merely because the language changes.
- Read `docs/PASSWORD_AUTOFILL.md` and `docs/PASSKEYS.md` before modifying authentication integrations. Synthetic providers, a reached password screen, or successful URL handoff do not prove completed provider login or production feature availability.
- Keep debug-only IME/autofill test providers out of release variants. Preserve production approval gates unless the task explicitly changes them.
- When modifying bundled scripts, filters, native libraries, or other third-party assets, preserve provenance and required license/notices; consult `THIRD_PARTY_NOTICES.md` and `third_party/adblock/README.md`.

## Versioning and release notes

- Change `versionName` and `versionCode` in `app/build.gradle.kts` when preparing an explicitly requested version/release. Ordinary fixes and builds do not automatically require a version bump.
- Use `MAJOR.MINOR.PATCH` for `versionName`: patch for compatible fixes, minor for compatible features, major for breaking changes. Follow an explicitly requested version instead of choosing another one silently.
- Increase the integer `versionCode` for each new Play upload artifact. Check the highest code already uploaded across Play tracks, including drafts/testing tracks; the new code must exceed it. Do not reuse an uploaded code or infer the next code solely from the local file.
- Keep the Gradle version, `docs/releases/<version>.md`, `docs/play-store/release-notes-<version>.txt`, and any version references changed for that release consistent. Use `v<versionName>` for the source tag.
- Developer-facing release notes live in `docs/releases/<version>.md`; state the version/code, concrete user-visible changes, material limitations, and actual release status. Keep verification evidence separate from store availability.
- Play notes live in `docs/play-store/release-notes-<version>.txt`, with `<en-US>`, `<ja-JP>`, `<zh-CN>`, and `<ko-KR>` blocks. Keep the four languages equivalent, concise, and within Console limits; validate the exact text before submission. Describe shipped behavior without internal implementation chatter or unverified claims.
- Preserve user-supplied or approved release-note wording unless a change is requested. Do not copy an earlier release's approval or publication status into a new release.

## Secrets

- Keep release keystores, signing properties, API tokens, service-account credentials, and passwords outside Git. Use the existing external signing configuration; use the CI platform's Secrets facility if CI credentials are explicitly requested.
- Never print secret values, dump secret-bearing files/environment variables, or put secrets in chat, command arguments, release notes, artifacts, or test fixtures. Verify configuration through presence checks and non-secret artifact metadata.
- Do not commit secrets even temporarily or rely on `.gitignore` to protect an already tracked file. Inspect the intended diff and staged paths before committing.
- Do not invent credentials, silently change signing keys, or fall back to debug signing for a release. If required credentials are unavailable, report the missing configuration without exposing values; let the user enter credentials through the appropriate secure interface.

## Workflow and release authorization

- Do not add automatic workflow triggers. This includes push/tag, pull-request, scheduled, upstream-workflow, or other automatic release/build/deployment triggers. Do not add a workflow with automatic triggers to simplify a release; any exception requires an explicit user instruction changing this rule. If a workflow is requested, prefer manual `workflow_dispatch` within the requested scope.
- Do not execute a release without the user's explicit instruction. A request to implement a fix, build, test, bump a version, or edit release notes does not authorize release execution.
- Before executing release actions, confirm the existing user instruction covers the intended version/channel and actions: pushing release tags, creating remote release drafts, uploading bundles, submitting for review, starting a rollout, or publishing. Act only within that scope; existing explicit authorization need not be requested again.
- Historical approval text in repository documents is evidence of a past request, not fresh authorization for a different version or expanded release scope. Local builds and preparation may proceed within the development task; do not run release/deployment commands as verification.

## Release and distribution

- Read `docs/RELEASING.md` and `docs/PLAY_CONSOLE.md` before release work. Obtain current version metadata from `app/build.gradle.kts`; dated verification documents are historical evidence, not current Console state.
- Google Play is the sole official distribution channel starting with 1.1.1. The `github` flavor remains for historical reproducibility/regression checks. Do not publish new sideload APKs, updater manifests, or checksums through the legacy `scripts/prepare_release.py` path.
- Signed upload bundle: `./gradlew :app:bundlePlayRelease --no-parallel`; output: `app/build/outputs/bundle/playRelease/app-play-release.aab`. Signing configuration is external, via `NAGI_SIGNING_PROPERTIES` or the default path documented in `docs/RELEASING.md`. Debug development needs no release secrets.
- Verify version codes, signing roles, exact source revision, artifact metadata/signature, and relevant release checks before upload. Never move an existing release tag.
- Play and historical sideload signatures may differ; do not suggest uninstalling as a routine migration step because it deletes private workspace/site data.
- When release preparation is explicitly authorized, follow this order: finalize version and four-language notes; run relevant tests/lint; review the intended source revision; build and verify the signed AAB from that clean revision; record its hash/signature; create the authorized immutable tag/draft; inspect current Console signing, tracks, version codes and pending changes; upload and save the authorized release draft. Preserve intended countries/device support and resolve validation errors before proceeding.
- Stop at the limit of the user's authorization. A request for preparation/upload/draft does not authorize review submission, rollout, or publication. Record the exact remaining action and report source/tag, draft, accepted upload, review submission, and public availability separately.
- Authorized Play uploads go through the manual workflow `.github/workflows/play-release.yml` (`workflow_dispatch` only; `play-release` environment with required reviewers and its secrets). Defaults are `track=internal`, `status=draft`, `send_for_review=false`; choose `production`, `completed` or `send_for_review=true` only within explicit authorization. A successful run proves an accepted upload, not review or availability. Inputs, pipeline and secret names are in `docs/RELEASING.md`.
- `docs/releases/changelog.json` is appended by that workflow after a successful upload. Do not edit it by hand except to restore an entry from a failed run's `changelog-entry` artifact. Helper logic is `scripts/play_release.py`; test with `python3 -m unittest discover scripts/tests`.
