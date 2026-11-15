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
