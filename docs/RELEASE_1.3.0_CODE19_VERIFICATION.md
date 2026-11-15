# Nagi 1.3.0 / code 19 replacement preparation

Checked on 2026-11-15 (JST). This candidate includes all current development work.
The earlier code-18 artifact and immutable `v1.3.0` tag remain historical.

## Live Play preflight

The Android Publisher API lists highest uploaded code 18 across bundles, APKs
and production/beta/alpha/internal tracks. The production lifecycle shows code 18
as NOT_SENT_FOR_REVIEW and code 17 (1.2.0) as PUBLISHED. The production edit still
contains code 18 with status completed; this does not establish public availability.
The country-availability response is retained for an exact comparison at upload.

## Release-note review

The [six-language replacement notes](play-store/release-notes-1.3.0-code19.txt)
are a proposal awaiting user confirmation. Counts are checked against the 500-character Console limit for every locale. The original approved/submitted
code-18 notes are preserved unchanged in `release-notes-1.3.0.txt`.

No replacement upload, review submission or public release has occurred yet.
Evidence is retained in ignored `artifacts/1.3.0-replacement-20261115/`.

## Password autofill scope update

The user explicitly requested production password autofill on 2026-11-15.
The candidate enables Android native form exposure, Settings/service selection
and page-menu requests in normal browsing. Private/Reader restrictions remain.
This is distinct from Google provider approval, matching saved credentials or
a completed provider login. Diagnostics and synthetic providers remain Debug-only.

## Completed candidate checks

- All 208 Play Debug unit tests passed, with no failures, errors or skips.
  Play Debug and Release lint reported zero errors and 92 warnings each.
  Debug and instrumentation APK assembly passed with JDK 17 and SDK 36.
- Across the selected API-36 runs, 32 distinct current cases passed. Coverage
  includes phone/light and tablet/dark Command Bar settings, saved preferences,
  live Split/draft retention, two-choice site modes, Settings resize/recreation,
  Split close/reorder/resume behavior, themed link/image dragging, licenses,
  production autofill visibility/service selection, default native form exposure,
  automatic/manual synthetic credential delivery, duplicate-ID right-Split
  targeting, no-match requests and private-profile autofill exclusion.
- The first 26-case run had one stale Command Bar expectation: with Commands
  hidden, `>` is ordinary searchable text. The assertion now checks that behavior
  and passes. Enabling autofill also required replacing two old hidden-category
  assertions. The picker probe now reads Android 16's `topResumedActivity` field.
- Four autofill delivery cases initially stopped before provider requests because
  TestIme passed a full component string while Android registered the short IME
  ID. The helper now uses `ComponentName.flattenToShortString()`; all four cases
  passed on rerun (11.937 s). A trial keyboard-display setting change did not fix
  the cause and was restored to its original value before the passing run.
- A clean signed Play AAB and local Release APK passed bundletool validation,
  package/version/non-debuggable checks, signature verification, upload-certificate
  comparison against the live Console, 16KB ZIP/bundle packaging and native ELF
  load-alignment checks, and bundled notice checks. Debug IME/autofill providers
  and the legacy install-packages permission are absent from the release manifest.
  Final source revision and artifact digests are retained in
  `artifacts/1.3.0-replacement-20261115/verified-artifacts.json`.
- The dedicated signed QA emulator accepted an in-place code-18 to code-19
  update and retained its existing tabs. The Release build displayed the autofill
  category at tablet/wide and phone/narrow sizes and opened Android's native
  provider selector. Android diagnostics remain disabled in Release.
- Through the native folder picker, the signed build received access to a
  dedicated QA folder. HTTP (41,984 bytes) and generated (30 bytes) downloads
  landed there and matched the fixture bytes. This validates actual folder writes,
  separately from the download-folder DataStore unit tests.
- The latest Debug candidate was installed and launched on the visible Pixel
  Tablet QA emulator. The two headless QA devices and loopback fixture are stopped
  after verification; no personal-device data was used or cleared.

These are selected local emulator checks, not a full instrumentation suite,
physical-device validation, successful Google password delivery or completed
Google login. Synthetic providers supply test credentials only. Native library
packaging checks do not establish runtime testing on a 16KB-page device.

## Remaining authorized work after note confirmation

Confirm the proposed six-language Play notes with the user, refresh Play state,
create the distinct immutable `v1.3.0-code19` source tag without moving `v1.3.0`,
replace the stopped code-18 candidate with the verified code-19 bundle, compare
country/device support, and submit the production release for review. Managed
publishing is currently off; review submission and actual public availability
must be reported separately. No code-19 upload or review submission has occurred.
