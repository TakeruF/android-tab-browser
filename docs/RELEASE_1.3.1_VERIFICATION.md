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
The source was committed and merged through [PR #16](https://github.com/TakeruF/android-tab-browser/pull/16)
before the clean signed build. Evidence is retained under ignored
`artifacts/1.3.1/` in the development checkout.

## Current release state

Google Play accepted code **20 (1.3.1)** and the exact verified bundle digest.
The six note blocks were reread through an independent Publisher edit and match
the approved canonical file exactly. Country availability and all six saved
store listings match the pre-upload snapshots unchanged. The production edit
was validated and committed for full rollout; live Console Publishing overview
shows **Changes in review → Production → 20 (1.3.1) → Start full rollout**.
Managed publishing is **off**; approval is configured to publish automatically.
**Code-20 approval and public availability are not yet confirmed.**

Before this submission, Console showed code 19 (1.3.0) as Available on Google
Play. A Publisher track's `completed` status is not used as publication proof.

## Source and signed artifacts

- Clean source `81a19825d42df4b9d31404833d65a178815fc988`, immutable tag `v1.3.1`; tag remains on the exact built
  revision. Later documentation commits only record release evidence.
- Accepted Play AAB SHA-256: `1e3523bd022545d85a0deaf7dc38e2df0171ca00b8a7ff143673a4e640e0cea3`.
- Local upload-key-signed Release APK SHA-256: `f6a8021fee9e73705df97d170f67fa63bed172eb26124cd43ded02e8ddd1902a`.
- Bundletool 1.18.3 validation, bundle/APK signatures, upload-certificate match,
  package/version/non-debuggable metadata, 16KB ZIP/bundle packaging and all
  arm64-v8a/x86_64 ELF load alignments passed. The initial local checker also
  applied the 64-bit 16KB threshold to 32-bit libraries; the corrected check
  follows [Android's ABI-specific guidance](https://developer.android.com/guide/practices/page-sizes)
  and records the unchanged 4KB 32-bit alignments separately. This establishes
  packaging/alignment, not execution on a 16KB-page device.
- SDK, required features, permissions and screen-support declarations match the
  code-19 bundle. The Play manifest excludes legacy APK installation permission
  and Debug-only IME/autofill providers. Bundled notices match the intended source.
- A dedicated API-36 signed QA emulator accepted an in-place local signed
  code-19 → code-20 update and retained the fixture tab. Actual Appearance and
  site-menu actions verified default off, darkened pixels, a white excluded
  site, retained draft text and no document request during toggles.
- This is local upload-key-signed APK evidence, distinct from Play-delivered app
  signing, physical-device behavior or eventual store availability.
- GitHub retains a source release draft with no APK/AAB assets. No new workflow
  triggers, sideload release or updater manifest was introduced. The development
  checkout's original 42 modified/untracked source files were preserved.

## Publication confirmed — 2026-11-18

The later [1.4.0 release checks](RELEASE_1.4.0_VERIFICATION.md) recorded the
official lifecycle as **code 20 (1.3.1) PUBLISHED**. Console independently showed
1.3.1 available on Google Play at 100% in the established countries/regions, as
recorded in the [2026-11-18 Console summary](PLAY_CONSOLE.md#historical-production-submission--2026-11-18).
This supersedes the earlier review-pending status above. It confirms store
publication, not a Play-delivered installation or physical-device test. The
latest published version is recorded in [1.4.1 notes](releases/1.4.1.md).
