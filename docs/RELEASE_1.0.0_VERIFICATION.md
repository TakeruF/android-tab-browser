# Nagi 1.0.0 publication verification

Verified 2026-10-11. Package `com.takeruf.nagi`, versionCode `8`, minSdk 26, targetSdk 36.

## Build and runtime

- Release source: `6436511d6d02e54053f8255527a6b2158d860050`. Signed GitHub APK and Play AAB verified from a clean, isolated checkout. The final source includes test-fixture hardening and current four-language store copy; release rebuild confirms unchanged artifact bytes.
- Unit tests: GitHub 147 and Play 136, zero failures/errors/skips. Release Lint: zero errors, 51 GitHub and 62 Play warnings.
- API-36 ARM64 `Nagi_Foldable_UI_QA`: final combined Play Debug run passed 29 instrumentation cases, zero failures/errors/skips, 130.617 seconds. Coverage includes narrow/wide UI transitions, temporary tab sheets, mobile UA/Client Hints, current-document and draft preservation, hidden/Split tabs, per-site choices and history, Reader, private storage, blocking exceptions, and generic empty-ad container collapse.
- The initial combined run had one local Referer-fixture loading timeout; the same case passed alone. A new regression deterministically reproduced the fixture server blocking all subsequent requests behind an unused browser preconnection. The test server now serves connections independently with bounded header reads. Its new regression and all previous cases passed together. The production browser implementation was unchanged by this test-server fix.
- Signed Release upgrade: installed the public 0.3.0 APK, opened `https://example.com/#nagi-release-100-upgrade`, then upgraded in place to 1.0.0 and cold-launched. The saved Example Domain tab and complete URL remained. Installed package metadata has version 8/1.0.0 and is non-debuggable; installed APK bytes match the published artifact.
- APK: 16,664,710 bytes; SHA-256 `82580f438c047e735761b1ab2b45b52e03f9da4082e0ddb4bb36a819f529c525`. Signature v2 and 16 KB ZIP alignment passed. Existing distribution-certificate SHA-256 remains `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`.
- Play AAB: 14,388,733 bytes; SHA-256 `3c7da33e08066ebcc4180575757d5bb743d624d99d6069260b961f7fae4ca829`. JAR signature and bundletool validation passed. Manifest confirms version 8/1.0.0 and excludes `REQUEST_INSTALL_PACKAGES` and the GitHub update provider.

Signed artifacts, update metadata/checksums, final unit/instrumentation and Lint XML reports, installed package metadata, the Play manifest, Console comparison JSON, and the upgrade screenshot are retained in ignored `artifacts/1.0.0/`. Raw logs, interrupted/rerun outputs, repeated APK downloads, and the temporary bundletool JAR were removed during local cleanup. Physical fold transitions, older Android/WebView providers, and all website/advertisement patterns remain unverified.

## Google Play closed-test continuity

Submitted signed `8 (1.0.0)` to the existing Alpha track at 100%, with four-language notes. The release plus eight short/full-description changes were accepted as **9 changes sent for review**. Publishing overview confirms **Changes in review**, with managed publishing still off. Alpha remains **Active**; 1.0.0 is **In review**, while 0.3.0 remains **Available to selected testers**. This proves submission, not approval or delivery of 1.0.0 to testers.

Tester selection, `AndroidClosedJP@googlegroups.com`, and `support@takeruf.com` match the pre-submission form and input values exactly. All 178 country/region rows match exactly. Dashboard still reports 12 testers opted in for 4 days continuously. No track pause, new track, tester/country change, signing-key change, managed-publishing change, or production promotion was performed. Console reports zero lost supported devices; its only release warning recommends optional native debug symbols.

Evidence: [review submission](play-store/console-100-review.png), [Alpha release statuses](play-store/console-100-alpha-active.png), and ignored `validation/console-baseline.json` / `validation/console-continuity.json`.

## GitHub

[GitHub v1.0.0](https://github.com/TakeruF/android-tab-browser/releases/tag/v1.0.0) is public, targets the release-source commit above, and contains the signed APK, `update.json`, and `SHA256SUMS`. Public latest metadata and APK were downloaded again and match version, size, checksum, signature, and bytes. The tag points to the release source above.

## takeruf.com

[Website PR 28](https://github.com/TakeruF/me/pull/28) merged as `223d624a626c7bf1a0917cd184a3a29a9a18c63a`. Local ESLint, four-language static builds, 60-page export verification, GitHub CI and Vercel preview checks passed. Desktop and 390px phone layouts were visually inspected and have no horizontal overflow. EdgeOne production checks are recorded separately from Vercel preview checks.

EdgeOne production was verified at the canonical public URLs in all four languages. Product pages show 1.0.0 / 16.7 MB, the October 11 date, current release link/checksum and new feature descriptions. All four homepages and privacy routes return HTTP 200. The canonical website APK was downloaded and is byte-for-byte identical to the GitHub artifact. All seven older APK URLs remain available. The final verification JSON and live desktop screenshot are retained under ignored `artifacts/1.0.0/website-verification/`; downloaded HTML and the duplicate APK were removed during local cleanup. The canonical signed APK remains at `artifacts/1.0.0/nagi-1.0.0.apk`.

The original website worktree's 27 unrelated modified paths were preserved. Site changes were committed and verified in an isolated worktree. The task's local preview server was stopped after verification; pre-existing emulators and the connected physical device were not shut down.
