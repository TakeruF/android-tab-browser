# Nagi 1.3.0 / code 19 verification and submission

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
were approved by the user on 2026-11-15 and submitted unchanged in all six locales. Counts: en-US 473, es-ES 475, fr-FR 474, ja-JP 281, zh-CN 172 and ko-KR 290 characters. The original approved/submitted
code-18 notes are preserved unchanged in `release-notes-1.3.0.txt`.

The code-19 upload and review submission are complete. Public code-19 availability is not yet confirmed.
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

## Final source and accepted artifacts

- Source `7fadfab18581a3e2ecb295e119887e4789e2610b`, immutable tag
  `v1.3.0-code19`; the original `v1.3.0` still points to
  `9aaf98f6af1eb2799e847474d8959fe5e0897ce4`.
- Accepted Play AAB SHA-256:
  `b25592eea70b5ae63585b9707ba6585b9fc2fcf823964ebb52ed4428cbc29d6f`.
- Local signed APK SHA-256:
  `f1dd207ee1b9cdc83f738213ded2c2905c04467ec0b200328f8b3728d0eda8bc`.
  The final artifact was installed in place on the dedicated signed QA emulator;
  its retained fixture tab and production autofill Settings were rechecked.
- The accepted bundle reports the same digest as the verified file. GitHub holds
  a source release draft without binary assets; Play remains the sole distribution
  channel. Subsequent commits only record store copy and release evidence.

## Store copy and submission

The user approved the six-language notes and requested equivalent Play listing
coverage. Spanish (es-ES) and French (fr-FR) listings were added; the existing
English, Japanese, Simplified Chinese and Korean descriptions now list all six
interface languages. [Canonical submitted store copy](play-store/listings-1.3.0-code19.json)
contains the exact title, short description and full description for each locale.
New translations inherit default-language graphics; no graphics were replaced.
All title/short/full limits (30/80/4,000 characters) passed.

The service account could upload and manage releases but could not validate store
copy changes (403). Store text was therefore saved through the logged-in owner
Console, without expanding permissions. An initial candidate draft also failed
upgrade validation when it retained code 17 below the existing code-18 candidate;
only the new draft was requested in the successful commit. The app rejected the
`changesNotSentForReview` parameter; the successful commit omitted it.

During Console editing, French text was temporarily entered in the Korean field.
The user pointed this out before saving. Korean was restored, and each locale's
selected-language control plus all three text fields was checked against the
canonical copy. All 18 text fields matched before saving. An independent Publisher
API reread after saving, and another after submission, confirmed the full six
listings and six approved release-note blocks matched exactly. No mixed-language
Korean copy was submitted.

A later lifecycle reread showed code 18 as PUBLISHED, unlike the initial
NOT_SENT_FOR_REVIEW preflight. The earlier statement that draft creation had
replaced the old candidate was premature. Code 19 was then submitted as the
production update to code 18; no downgrade or app-data removal was attempted.

The final lifecycle API reports **code 19 IN_REVIEW** and **code 18 PUBLISHED**.
Live Publishing overview confirms **Changes in review**, including
**19 (1.3.0) → Start full rollout**, both added languages and all four existing
language-description updates. Quick checks are running and **Managed publishing
is off**: successful checks proceed to review, and approval is configured to
publish automatically. **Code 19 public availability is not yet confirmed.**

Production country availability was compared unchanged before draft and final
submission: 177 explicit countries plus rest-of-world availability, retaining the
existing 178-country/region scope. `uses-sdk`, required features and screen-support
manifest declarations match the earlier code-18 build. No device targeting or
country changes were requested or made.
