# Nagi 1.6.2 verification — 2026-11-27

## Source and signed artifacts

- Version: **1.6.2 / code 26**, package `com.takeruf.nagi`, minSdk 26, targetSdk 36.
- Clean build source: `dda3dd1138fca6eb8d2d4c1368cdde1f68939b58`, tree
  `3e9b23b8e8164285abaaad0ea9dc001b3d3d3d84`, immutable tag `v1.6.2`.
- Feature commit: `71c2ee5`; version/notes commit: `dda3dd1`.
- Signed AAB SHA-256:
  `2b5a239e96b1a4da46f39b2bfedfa16483fe4496c804320c6e86797716601a15`
  (74,128,777 bytes).
- Local signed QA APK SHA-256:
  `13482c21f5880350657149075e29447e67fba1c063097337fe207165c799cb24`.

JDK 17 / SDK 36 `bundlePlayRelease` and `assemblePlayRelease` passed with
`--no-parallel`. Bundletool validation, JAR/APK signatures, package/version,
non-debuggable status, APK zip alignment, bundle 16KB packaging and native ELF
alignment passed. License assets match source. Device/permission declarations
match the accepted 1.6.1 bundle; debug providers and the legacy updater are absent.

The upload certificate is
`a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`.
Its role was reconfirmed by matching the live accepted code-25 bundle digest and
certificate. Live generated Play APKs report the distinct app-signing certificate
`4e8950a7922886ba495048edfdc46f009134f1f4c8e65264ec8613ca51e99e49`.
The Console signing UI was not inspected.

## Relevant checks

After the version update, all **299 Play Debug JVM tests** passed with zero
failures, errors or skips. Debug and Release lint each passed with zero errors
and 97 existing warnings. Debug APK package/version metadata was verified.

The unchanged feature/test sources match the recorded SHA-256 inventory from
the **38 distinct selected API-36 UI cases** covering native mouse/trackpad
selection, Command Bar layouts/themes, CJK composition, Split/draft retention,
trackpad history arbitration and popup input protection. See the dated
[validation record](VALIDATION.md#automatic-pointer-selection-menus-and-coverage-audit-2026-11-27).
These UI cases preceded the metadata-only version change.

An additional signed **25 → 26 in-place update** passed on fresh dedicated
`Nagi_162_Signed_QA` / `emulator-5710` (API 36, WebView 133.0.6943.137,
2560 × 1600 px, 320 dpi). The installed APK hash and unchanged UID were checked.
The selected fixture tab, all five Command Bar suggestion choices, the bottom
search preference, Cookie and localStorage survived the update and cold restart.
The fixture was seeded exactly once. Screenshots and accessibility trees record
the retained values. The initial helper assumed a narrow settings Back button,
then resumed from the wrong screen; both test-navigation failures were corrected
for the wide settings layout before the final passing run. No app-data reset or
uninstall was used. This QA emulator and its fixture server were then stopped.

Physical devices, older Android APIs, the full connected suite and a
Play-delivered build were not tested. This emulator uses 4KB pages; packaging
alignment is separate from 16KB runtime validation. The remaining 12 Compose
fields, license text and separate Reader adapter listed in the
[menu audit](MENU_LAYOUT.md#selection-audit-2026-11-27) were not changed.

## Distribution status

The exact AAB was accepted as code 26, saved as a production draft, validated
and submitted for 100% production rollout. Independent official API readback
at **20:19:58 JST on 2026-11-27** confirmed code 26 **IN_REVIEW** and code 25
**PUBLISHED**. Public 1.6.2 availability is not yet confirmed.

The accepted AAB digest and all six canonical notes match the verified source
artifact and approved copy. Countries, store listings and every other track
match the preflight snapshots. Both draft and submission commits used
`changesInReviewBehavior=ERROR_IF_IN_REVIEW`; no existing review was cancelled.
Read-only edits were deleted without committing. Before upload, the highest
code across all bundles, APKs and tracks was 25. The Console UI and current
managed-publishing controls were not inspected.

The six [Play notes](play-store/release-notes-1.6.2.txt) preserve the approved
brief wording, “Made some improvements.”, with equivalent translations. Each
block is a single sentence below 500 characters. The GitHub release is a
source-only draft with no binary assets.

Evidence is retained under ignored `artifacts/1.6.2/`,
`artifacts/1.6.2-preparation-20261127/`, and
`artifacts/pointer-selection-20261127/`.
