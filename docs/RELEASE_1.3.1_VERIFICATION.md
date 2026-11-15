# Nagi 1.3.1 / code 20 verification

Checked on 2026-11-15 (JST). This release includes the reviewed development
changes from this session. The original working tree is retained separately.

## Live preflight

The Android Publisher API lists highest uploaded code **19** across bundles,
APKs and production/beta/alpha/internal tracks. Production's edit has code 19
with status completed; this alone does not prove approval or public delivery.
Country availability was saved for exact comparison: 177 explicit entries plus
rest-of-world availability. Console independently shows code 19 as the latest production release at 100%,
no unpublished changes, and Managed publishing off. The live upload-certificate
SHA-256 was copied from the Upload key section and matches the existing key:
`a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`.

## Release notes

All six note blocks are checked against the Console length limits and will be
reread after saving. Japanese preserves the approved minor-fixes sentence.

## Verification

- Version-specific Play Debug unit suite: **211 tests**, zero failures, errors
  or skips. Debug/Release lint: zero errors, 92 existing warnings each. Debug
  and instrumentation APK assembly passed with JDK 17 / SDK 36.
- The 29-case API-36 emulator run passed 28 cases. One Download UI case stopped
  while waiting for the second Split pane's persisted title, before its link
  tap/download confirmation. A focused rerun passed (3.08 s); the complete
  eight-case Download suite then passed (36.526 s), without source changes.
  The original timeout is retained in the evidence rather than described as a
  clean full-suite pass. All 29 selected distinct cases have a passing execution.
- Coverage includes native darkening pixels, default-off and light-theme behavior,
  per-host exceptions, redirects/history, authored dark themes and author opt-out,
  live Split drafts/scroll, Settings navigation/recreation at phone/tablet widths,
  the boundary drag target/guide, new-tab Enter behavior, Command Bar Space
  availability, compact navigation and native/system/generated downloads.
- Six equivalent Play-note blocks are within 500 characters (en-US 154, es-ES
  187, fr-FR 194, ja-JP 73, zh-CN 60, ko-KR 89); Japanese preserves the exact
  approved minor-fixes sentence. `git diff --check` passes.

These are selected local emulator checks, not a full instrumentation suite,
physical-device checks or proof of arbitrary website/provider compatibility.
The source will be committed before building the signed Play artifact. Evidence
is retained under ignored `artifacts/1.3.1/` in the development checkout.

## Current release state

Preparing; no code-20 upload, review submission or public availability is claimed.
