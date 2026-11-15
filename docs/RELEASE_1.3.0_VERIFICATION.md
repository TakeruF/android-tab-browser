# Nagi 1.3.0 verification and Play submission

Checked on 2026-11-15 (JST). VersionName **1.3.0**, versionCode **18**.

## Source and artifact

- Immutable tag `v1.3.0` identifies clean source `9aaf98f6af1eb2799e847474d8959fe5e0897ce4`, pushed to main. Its tree is `9a30ca1977fe9b3597c3bcb460405ef48b4a7d9b`.
- The 12 pre-existing uncommitted files in the original checkout are excluded. Their content hashes and unstaged status were checked unchanged. The signed build used a separate clean worktree at the tag source.
- AAB SHA-256: `712aadefeb608baa63b51e85e5109704a90bae927d5dbcf21292ee758a763c7d`. Play's accepted code-18 bundle reports the same digest.
- Local signed Release APK SHA-256: `297b2068c3f5309b88606673c76a2825cca15a8de7ecf9bad07e5089cb216989`.
- Console's upload-certificate SHA-256 matches the AAB/APK: `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`. Console's separate app-signing fingerprint is `4e8950a7922886ba495048edfdc46f009134f1f4c8e65264ec8613ca51e99e49`. No signing key or application ID was changed.
- GitHub holds a source release draft with no binary assets. Google Play remains the sole official distribution channel.

## Validation

- Version-specific Play Debug unit tests: all **193** passed, zero failures, errors or skips. Play Debug/Release lint: **zero errors, 81 warnings** each. Play Debug APK assembly passed.
- The preceding Settings feature baseline passed 22 selected API-36 emulator executions, Debug/test APK assembly and GitHub Debug Kotlin compilation. These were focused checks; the full instrumentation suite was not rerun for this version bump.
- Clean-source `bundlePlayRelease` and local `assemblePlayRelease` passed with JDK 17 and SDK 36. Bundletool 1.18.3 validation, package/version/non-debuggable metadata, AAB/APK signatures, APK 16KB ZIP alignment, bundle 16KB packaging configuration, all 10 ARM64/x86_64 shared-library ELF load alignments and notice-asset checks passed. The Play manifest excludes install-packages permission and debug IME/autofill providers.
- A fresh, dedicated API-36 emulator (`Nagi_130_Release_QA_20261115`, `emulator-5582`, WebView 133.0.6943.137) accepted an in-place update from the locally signed 1.2.0/code-17 APK to the exact local signed 1.3.0/code-18 APK. Existing tabs were retained; no uninstall or app-data clearing was used.
- Signed runtime checks passed: wide Settings categories/details, narrow list/detail/back navigation, one installed version in About, inspected light/dark screenshots, and unchanged live-document token plus edited draft through Settings, theme change and wide-to-narrow resize.
- Page-menu zoom changed the real WebView `visualViewport.scale` from 1.0 to 1.25; reset returned it to 1.0. HTTP confirmation saved 41,984 matching bytes and closed the transient download tab while retaining its opener. Generated Blob confirmation saved 30 matching bytes, requested notification permission and showed a completion notification.
- The exact approved Play notes contain four equivalent blocks, each below 500 characters: en-US 332, ja-JP 171, zh-CN 115 and ko-KR 199. The saved draft and submitted track notes were compared to those files.

The signed smoke checks are local APK/emulator evidence, not a Play-delivered installation, physical-device check, physical-keyboard test, full instrumentation run or production-provider login. Runtime page size was 4KB; the separate 16KB packaging checks do not prove 16KB runtime execution. Early UI automation selectors had wrong labels/capitalization and were corrected after reading the visible hierarchy; the completed checks above passed.

## Lint warning assessment

The 81 warnings remain unchanged. Most concern unused resources (27), KTX style
(15), and dependency/tooling update suggestions (20). The remainder include
feature guards, accessibility, plural candidates and platform/style guidance.
The reported feature calls were inspected: WebView capability checks guard their
entry paths; the favicon singleton is created with application context. No
release-blocking defect was established by this inspection and the relevant
checks. This is not a claim that every warning is harmless. Address accessibility,
capability annotations/guards and performance guidance in focused follow-up work;
dependency upgrades need their own regression checks. No blanket suppression or
warning-only source change was included in this release.

## Play state

Before upload, code 17 (1.2.0) was published and there were no unpublished Console
changes. The highest uploaded code across all current bundles/APKs and all four
tracks was 17. Code 18 was accepted with the verified digest and approved notes,
saved as a production draft, validated and committed for full rollout. Country
availability was compared unchanged before both draft and submission: 177
explicit entries plus rest-of-world availability, matching the existing
178-country/region selection. SDK levels and required device features were not
changed.

The lifecycle API returns **IN_REVIEW** for 18 (1.3.0). Live Publishing overview
confirms **Changes in review → Production → 18 (1.3.0) → Start full rollout**,
with quick checks running and **Managed publishing off**. Checks proceed to
review automatically after success. Approval is configured to publish without a
separate managed-publication step. **Public availability of 1.3.0 is not yet
confirmed.** Code 17 remains published meanwhile.

Compact evidence, signed artifacts, API responses and inspected screenshots are
under ignored `artifacts/1.3.0/`. The dedicated emulator and local fixture server
were stopped afterward; the unrelated existing emulator was left running. See
[developer notes](releases/1.3.0.md), [approved Play notes](play-store/release-notes-1.3.0.txt)
and [Google Play](https://play.google.com/store/apps/details?id=com.takeruf.nagi).
