# Nagi 1.6.3 verification — 2026-11-28

## Preparation

- Package `com.takeruf.nagi`, version **1.6.3 / code 27**, minSdk 26, targetSdk 36.
- Features: retained Claude authentication windows, Android web-search intents,
  tab-close predictive Back, and sharper byte-bounded Back/Forward snapshots.
- Live preflight: highest uploaded code 26 across all bundles, APKs and tracks;
  code 26 is PUBLISHED. Read-only edits were deleted without committing changes.
- The accepted code-26 AAB digest matches the local baseline. Its upload
  certificate is `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`;
  live generated Play APKs use the distinct app-signing certificate.
- Six equivalent one-sentence release-note blocks are all under 500 characters;
  Japanese is 56 characters. Canonical text is in
  [Play notes](play-store/release-notes-1.6.3.txt).

## Feature checks

All **313 unit tests** and both Play lints passed; Debug/Release lint reported
zero errors and 98/97 warnings. A focused 25-case device batch passed, followed
by 17 final cases after the close-transaction settlement change, with no skips.
The final cases cover native back progress, tab closure and cancellation,
Split/drafts, stale destinations, pixel detail, three-step Back/Forward, search
intent cold/warm delivery, opener communication and POST bodies.

The calibrated native CSS fixture and saved images both retained 1-pixel detail
at 1956 × 1604 with contrast 255. See [validation](VALIDATION.md) for exact scope
and corrected initial measurement failures/skips. Physical devices, equal-
condition Chrome comparisons and the full connected suite were not tested.
Live Claude navigation stopped at Cloudflare human verification; completed
Google authentication remains unverified.

## Source and signed artifacts

- Clean source: `af0772f39e6e9775b7a367276bbe0789614353ce`, tree
  `33784a4c57a09f04b15d91497f69582cac04aca8`; immutable tag `v1.6.3`.
- Signed AAB SHA-256:
  `0bb8ac0d640ae83a1d9abf2d82920cc7006d94b7566d21130a592d3186add468`
  (74,139,887 bytes).
- Local signed QA APK SHA-256:
  `55e4c9cd6b3c2416f674ef35ae2a333ee2d24e602235948b4c7fc81c69bd837e`.

`bundlePlayRelease` and `assemblePlayRelease` passed from that clean revision.
Bundletool validation, JAR/APK signatures, non-debuggable package/version,
APK zip alignment, 16KB bundle packaging, native ELF alignment and license
asset equality passed. Device/permission declarations match the accepted
1.6.2 baseline; debug IME/autofill/passkey providers and the legacy updater are
absent. Packaging alignment does not establish 16KB runtime execution.

## Signed runtime

The dedicated API-36 QA emulator passed a signed **26 → 27 in-place update**
without app-data clearing or uninstall. Installed APK hash and unchanged UID
were verified. The selected fixture tab, all five Command Bar category choices,
bottom-search preference, Cookie and localStorage survived the update and cold
restart. The fixture was seeded exactly once. A separate round trip on the
optimized Release APK confirmed the native popup opener, copied sessionStorage,
postMessage delivery and child-window close returning to the original page.
These are local signed APK checks, not a Play-delivered or physical-device test.

## Production submission

The exact AAB was accepted, saved as a production draft, validated and submitted
for 100% rollout with all six canonical note blocks. Independent official
lifecycle readback on **2026-11-28 at 11:37:22 JST** reports code 27 **IN_REVIEW**
and code 26 **PUBLISHED**. Public code-27 availability is not yet confirmed.

The initial immediate readback after the full-rollout commit still reported
NOT_SENT_FOR_REVIEW; a later independent read confirmed IN_REVIEW without
another mutating request. The committed track status alone was not used as
submission/publication proof. No prior review was cancelled: commits used
`changesInReviewBehavior=ERROR_IF_IN_REVIEW`. Read-only edits were deleted
without committing.

The accepted bundle digest, all six note blocks, country availability, store
listings and every other track match the preflight snapshots. The GitHub release
is a source-only draft without APK/AAB assets. The Console UI and current
managed-publishing controls were not inspected. Raw evidence is retained in
ignored `artifacts/1.6.3/`; no new official sideload channel was introduced.
