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

The 1.6.1 / code 25 candidate passed all 298 Play Debug unit tests with zero
failures, errors or skips. Both lint variants passed with zero errors / 97
warnings each, as did Debug app/test APK assembly, with side-by-side and autofill
diagnostics explicitly disabled. Selected feature emulator checks and the
separate opt-in YouTube check are recorded in
[validation](VALIDATION.md#history-native-menus-and-permissions-2026-11-26).
A fresh code-25 UI run and clean-source signed validation are recorded separately.

Clean source, signed AAB/APK metadata, upload/signing roles, packaging, signed
runtime checks and official release lifecycle will be recorded here after each
stage is verified. No upload or review submission is established by this file.
