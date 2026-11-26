# Nagi 1.6.1 / code 25 verification

The user authorized a patch increment and publication on 2026-11-26. The
intended channel is the existing Google Play production track for
`com.takeruf.nagi`. The separate `com.takeruf.nagi.preview` APK is a local test
artifact and is not the Play upload.

## Preflight

The live Publisher API found highest uploaded code 24 across bundles, APKs and
all tracks. Production 24 (1.6.0) is PUBLISHED. Beta/internal are empty; alpha
retains code 8. The candidate therefore uses 1.6.1 / code 25. Country availability,
six store listings and other tracks are captured for unchanged readback.

The original 30 changed/untracked files were backed up with SHA-256 hashes
under ignored `artifacts/1.6.1/original-worktree/`. Feature commit `0b80229`
contains those changes. Release source, later verification/status documentation,
the immutable tag, upload, review and public availability remain separate.

Direct push to main was rejected by automatic approval review, so release work
uses the reviewable `release/1.6.1` branch. Main is not changed by this workflow.
Browser-host access was unavailable, and binding the whole Chrome app was
rejected because it could expose unrelated signed-in tabs. Official Publisher
API readbacks are used for the authorized release; Console UI was not inspected.
No automatic workflow/release trigger is added.

## Candidate validation

The 1.6.1 / code 25 candidate passed all 299 Play Debug unit tests with zero
failures, errors or skips. Both lint variants passed with zero errors / 97
warnings each, as did Debug app/test APK assembly, with side-by-side and autofill
diagnostics explicitly disabled. Selected feature emulator checks and the
separate opt-in YouTube check are recorded in
[validation](VALIDATION.md#history-native-menus-and-permissions-2026-11-26).
Before the final cancellation change, the fresh 31-case code-25 UI run passed
30 cases; the HTTP test found more than one DownloadManager row. The test now
excludes rows present before the fixture starts, so old entries cannot be
mistaken for its output. This is test isolation; the exact origin of the extra
row was not captured.

After the cancellation change, the 17-case real-WebView run passed 16 cases.
The generated-download completion notification timed out in that interrupted
combined run; its isolated rerun passed. The cases cover HTTP acceptance,
cancellation and Back dismissal, background selection, existing/manual tabs,
Split/drafts, generated files and the engine's navigation/failure guards.
The native host cancellation/failure tests run at API 35; an initial API-26 Robolectric
host construction failed on the existing dynamic-receiver permission harness,
so the new host-failure test uses the same API-35 setup as the other host tests.
The final 299-test run has no skipped cases. All four focused cancellation
cases also passed at 1080x2400 / 420 dpi phone width. Clean-source signed
validation is recorded separately below.

Clean source, signed AAB/APK metadata, upload/signing roles, packaging, signed
runtime checks and official release lifecycle will be recorded here after each
stage is verified. No upload or review submission is established by this file.
