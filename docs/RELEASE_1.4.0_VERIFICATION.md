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

Signing and distribution results will be recorded after completion.
This is selected emulator coverage; physical devices and a full
connected suite are not established. No upload, tag, review submission or public
availability is claimed at this stage.
