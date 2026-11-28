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

## Release state

Source finalization precedes the clean signed build. Signed AAB verification,
in-place runtime update, immutable tag, upload and production submission are
pending at this preparation revision. Raw evidence remains under ignored
`artifacts/1.6.3/`; no new official sideload channel is introduced.
