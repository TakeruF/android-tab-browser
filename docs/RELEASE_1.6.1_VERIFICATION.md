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

## Clean source and signed artifacts

The immutable source tag `v1.6.1` points to `19f3cc1a4ae20c43500d787495ab5fa199e091ed`
on the `release/1.6.1` branch. [PR #18](https://github.com/TakeruF/android-tab-browser/pull/18)
remains separate from release submission; main has not been updated. The final
AAB and APK were built from this clean revision. The source-only GitHub release
is a draft with no attached binaries.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| nagi-1.6.1-play.aab | 74116256 | `9d8ab6d66882e3c997cba669741c9d2f8f5add56c939c327ad6f36f1a7ba084e` |
| nagi-1.6.1-play.apk | 84544057 | `53fa37a43362c50a24c64b653bbb019d954f57a5ba160b5ecf15ad1d2e2d71fd` |

Bundletool validation, AAB/APK signatures, package/version, SDK 26/36,
non-debuggable flag, Play installer/debug-harness exclusions, notices and
16 KB packaging/64-bit ELF alignment passed. Compared with accepted 1.6.0,
device declarations are unchanged and the only added permission is normal
`android.permission.MODIFY_AUDIO_SETTINGS`. Packaging alignment does not prove
a 16 KB-kernel runtime.

The live accepted code-24 AAB hash matches the previous signed artifact. Its
upload certificate matches the new candidate; Play-generated APK metadata
independently confirms the distinct app-signing certificate:

- Upload SHA-256: `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`.
- Play app-signing SHA-256: `4e8950a7922886ba495048edfdc46f009134f1f4c8e65264ec8613ca51e99e49`.

## Signed runtime

Dedicated `emulator-5592` retained the exact signed code-24 APK, then accepted
code 25 with `adb install -r`. UID and installed APK hash were verified. The
five Command Bar category choices, bottom-search preference, selected fixture
tab, Cookie and localStorage survived the update and force-stop/cold restart.
The fixture was seeded once. No uninstall or app-data clear was used. This
checks local upload-key-signed updates, not Play-delivered app-signing updates.

## Local comparison APK

The separate `nagi-1.6.1-test.apk` is version 1.6.1 / code 25, package
`com.takeruf.nagi.preview`, label `Nagi Test`. Package/version/label and APK
signature verification passed, and it installed alongside the normal package
on dedicated `emulator-5586` with distinct UIDs. It is a local development
artifact requested for comparison; it is not an official distribution upload.
The instrumentation runs above used the normal Play Debug package.

## Play status

The initial simple upload response timed out after 300 seconds. Instead of
resending, the same edit's bundle list was read back: code 25 was accepted with
SHA-256 `9d8ab6d66882e3c997cba669741c9d2f8f5add56c939c327ad6f36f1a7ba084e`,
matching the exact verified AAB. No duplicate upload was performed. Acceptance
also confirms the current upload key is valid.

The production draft was saved and validated with exact six-language notes,
unchanged country availability and unchanged store listings. Commit requests
use `changesInReviewBehavior=ERROR_IF_IN_REVIEW` to avoid cancelling an existing
review. Independent readback at **17:27:04 JST on 2026-11-26** confirms production
code 25 is **IN_REVIEW**; code 24 remains **PUBLISHED**. Code 25 is configured
for 100% rollout after approval; **public 1.6.1 availability is not confirmed**.
The accepted AAB hash, all six exact note blocks, countries, six store listings
and other tracks matched their captured baseline. Console UI/managed-publishing
controls were not inspected; no publication claim is inferred from completed
track status or commit acceptance.

Evidence is under ignored `artifacts/1.6.1/`. Full instrumentation, physical
trackpad/OEM/microphone/headset/ChatGPT voice sessions were not run. See the
feature validation documents for their narrower live-provider/fixture results.
