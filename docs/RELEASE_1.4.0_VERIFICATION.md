# Nagi 1.4.0 / code 21 verification

## Live preflight — 2026-11-18

Android Publisher API preflight across bundles, APKs and all tracks found highest
uploaded code **20**. Production code 20 (1.3.1) is completed; alpha code 8 is
completed, and beta/internal have no release. Production retains its 177 explicit
country entries plus rest-of-world availability. A dedicated API edit is used for
this release; existing tracks/listings remain unchanged until the verified upload.

## Candidate and verification

Preparation uses a separate `nagi-140-release` worktree on the latest main,
including the previously pushed favicon update. The original dirty checkout is
preserved; only the newly requested error-screen source changes are added there.
Version/code and the six-language notes are prepared for 1.4.0 / 21.

JDK 17 / Gradle Wrapper passed all **218 Play Debug unit tests** with no failures,
errors or skips, Debug/main instrumentation APK assembly, and Play Debug/Release
lint (**0 errors, 96 warnings each**). These checks use the version-specific
candidate, not the original checkout's stale version metadata.

Dedicated API-36 `emulator-5588` / WebView 133.0.6943.137 passed all four
`PageErrorUiTest` cases in one final run. Real connection refusal/recovery,
30-second timeout, untrusted local TLS, denied/missing local-file errors,
server-authored 404 content and rejected TLS image subresources were checked.
Screenshots cover 360/411dp phone and 1000dp tablet widths, both address placements,
light/dark themes and Split with the partner's identical WebView/draft retained.
The final rerun also verifies that touching the error pane selects it for browser
actions without changing the partner's content.
The initial TLS fixture could not perform the provider's RSA signing operation;
adjusting that ephemeral test key's capabilities and TLS fixture protocol fixed
the harness. Its focused rerun and the final four-case run passed without changing
the app's TLS validation policy.

Nine additional existing UI cases passed individually: content-sized tab sheets,
single-row bottom actions, navigation colors/themes, compact keyboard hiding,
wide Command Bar keyboard placement, wide page/Split keyboard resizing, phone
landscape mobile-site defaults and explicit overrides, saved Split retention,
and theme-specific favicon selection with retained pages/drafts. Evidence is in
ignored `artifacts/1.4.0/selected-ui/` in the original checkout.

This is selected emulator coverage; physical devices and a full connected suite
are not established.

## Clean source and signed artifacts

- Source `49ee997ab40085644fb69da45bf3705d574a8a78`, source tree
  `07590dcb26bcd841583653f41feefc6350e3a019`, immutable tag `v1.4.0`.
  Source and tag were pushed to GitHub; the original dirty checkout is retained.
- AAB SHA-256 `9a4b921855a38b63bee9570d38e7df4ace68e3c23917e5c9a450bc318adb0b12`
  (73,961,081 bytes). Local signed APK SHA-256
  `84fa987def28835c9a472dc70133940615725814fd7a8ca65f7f830fa8a76104`
  (84,486,089 bytes). Neither artifact is a new public sideload distribution.
- The clean-source Release bundle/APK built with JDK 17. Bundletool 1.18.3
  validation, bundle/APK signatures, ZIP alignment, 64-bit native ELF 16KB load
  alignment, bundled notices and manifest boundaries passed. Package is
  `com.takeruf.nagi`, version 1.4.0/code 21, minSdk 26/targetSdk 36,
  non-debuggable, with no Debug IME/autofill providers or legacy APK installer.
- The upload-key SHA-256 matches the live Console fingerprint
  `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`.
  Play's app-signing identity is separate. Device/permission declarations match
  the previous code-20 bundle.
- Dedicated API-36 `Nagi_140_Release_QA_20261118` / `emulator-5590` accepted
  an in-place local signed code-20 to code-21 update without uninstalling. The
  original fixture tab was restored without another URL launch intent. A real
  network-off test displayed the native Wi-Fi-off error screen and Retry in the
  optimized signed app. All three recorded QA network settings were restored
  after asynchronous radio changes settled. The emulator/fixture server stopped.
  This is local upload-key-signed APK evidence, separate from Play delivery and
  physical-device behavior; it does not establish live draft recovery after an update.

## Submitted distribution state — 2026-11-18

Google Play accepted code **21 (1.4.0)** with the exact verified AAB digest.
The saved production draft and final full-rollout edit retained all six approved
notes exactly, the existing country configuration and all store listings.
Final validation and commit succeeded with a 100% production rollout request.
The commit used `ERROR_IF_IN_REVIEW` to avoid cancelling another pending review.

Console independently showed **Changes in review → Production → 21 (1.4.0) →
Start full rollout**, quick checks running and **Managed publishing off**.
The official release lifecycle API reports **code 21 IN_REVIEW** and
**code 20 PUBLISHED**. Approval is configured to publish automatically; public
1.4.0 availability has not yet been established. The GitHub release remains a
source-only draft with no APK/AAB assets. No automatic workflow was added.

Compact evidence, accepted bundle metadata, lifecycle readbacks, screenshots,
source digests and local QA artifacts are under ignored `artifacts/1.4.0/` in
the original checkout. The public Play note text remains the user's three points.

## Publication confirmed — 2026-11-19

The official production lifecycle independently confirmed **code 21 (1.4.0)
PUBLISHED** before the 1.4.1 upload, as recorded in the
[1.4.1 preflight](RELEASE_1.4.1_VERIFICATION.md#release-scope-and-live-preflight--2026-11-19).
This supersedes the earlier review-pending status above without changing the
source tag, signed artifact or approved notes. It confirms store publication,
not a Play-delivered installation or physical-device test. The latest published
version is recorded in [1.4.1 notes](releases/1.4.1.md).
