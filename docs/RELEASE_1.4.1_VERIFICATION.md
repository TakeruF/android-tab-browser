# Nagi 1.4.1 / code 22 verification

## Release scope and live preflight — 2026-11-19

The user explicitly authorized committing/pushing 1.4.1 and releasing it. The
six-language store copy preserves the three 1.4.0 lines and appends the requested
custom-settings update fix. Fresh Publisher API preflight across bundles, APKs
and every track found highest uploaded code **21**. The production lifecycle
independently confirmed **1.4.0/code 21 PUBLISHED** before this upload. Code 22
was not previously uploaded. Beta/internal were empty; alpha retained code 8.

Console preflight showed the established upload-key SHA-256
`a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc` and its separate Play app-signing role.
Managed publishing was off, and the dashboard showed no unpublished changes.
The release preserves all store listings, 177 explicit production countries
plus rest-of-world availability, other tracks, and device/permission declarations.

## Source and validation

- Clean source `f34d478b5faf6ca19ee482147c8772f0d16cac34`, source tree `f2c04dea149fdb14bffbae360390d744590115e1`, immutable annotated tag
  `v1.4.1`; source and tag were pushed to GitHub.
- All **245 Play Debug unit tests** passed with no failures/errors/skips. Debug
  and Release lint each reported **0 errors and 96 warnings**. Debug and test
  APK assembly passed with JDK 17, SDK 36 and the included Wrapper.
- Earlier selected emulator coverage for the same functional source includes
  three Command Bar/settings/Split cases and two download-location cases.
  API-26/API-35 tests cover all 36 settings fields, concurrent edits, disk
  reopening, old manual search choices, grant ordering and 12 read-recovery
  executions. No full connected suite or physical-device result is claimed.
- The release commit contains exactly the intended 19 source/test/document
  paths. No workflow or automatic trigger was added. The original dirty
  checkout remains separate, with unrelated files retained.

## Clean-source signed artifacts

- AAB SHA-256 `a453459034f28d0143657e8d6030dff4f3efc42a2604df283f13316c2f9e5892` (73,962,765 bytes).
- Local QA APK SHA-256 `737474dc674ccffb5f883d6fff8781677d351053805dbcdbd03296befaa60b7e` (84,486,089 bytes).
- Bundletool 1.18.3 validation, bundle/APK signatures, ZIP alignment, native ELF
  load alignment and bundled notices passed. The upload certificate matches
  the live Console upload-key fingerprint.
- Package `com.takeruf.nagi`, version 1.4.1/code 22, minSdk 26/targetSdk 36,
  non-debuggable. Debug IME/autofill providers and the legacy APK installer
  are absent. Device/permission declarations match the code-21 bundle.
- The installed APK digest matched the verified signed artifact on dedicated
  `Nagi_CommandBar131Upgrade_QA_20261119` / `emulator-5592` (API 36).
  An in-place signed **21 → 22** update and a further cold restart retained
  all five Command Bar choices OFF, bottom-search preference ON, the selected
  fixture tab, a cookie and localStorage. Site markers were seeded exactly
  once before updating; subsequent launches did not pass a URL intent or
  reseed the page. No uninstall, data clearing or network/provider setting
  change was used. The QA emulator and local fixture server were stopped.

These are local upload-key-signed APK results, separate from Play signing,
Play delivery or physical devices. Live DOM/form restoration after process
termination is not established. Injected read failures reproduce the default-ON
model defect; the original user-device cause remains unconfirmed.

## Accepted upload and review submission

Play accepted **code 22** with the exact AAB digest above. The saved draft and
final full-rollout edit retained all six approved notes exactly, country support,
store listings and the other tracks. Validation and commit succeeded with a
**100% production rollout request**. `ERROR_IF_IN_REVIEW` prevented cancellation
of another pending review.

A fresh API edit independently verified the accepted digest, committed notes,
completed production-track request and unchanged configuration, then was deleted
without committing changes. The release lifecycle at **2026-11-19 07:23:07 JST** reports
**22 (1.4.1) IN_REVIEW** and **21 (1.4.0) PUBLISHED**. Approval is configured to
publish automatically; **public 1.4.1 availability is not yet confirmed**.

The GitHub release is a source-only draft with no APK/AAB assets. The Console's
upload signing and managed-publishing state were observed before submission;
post-submission native browser reconnection failed with `cgWindowNotFound`, so
final submission/lifecycle evidence is the official API readback, not a claimed
Console screenshot. No manual release action remains before Google approval.

Compact checks, artifact hashes, API receipts and local QA evidence are retained
under ignored `artifacts/1.4.1/` in the original checkout.
