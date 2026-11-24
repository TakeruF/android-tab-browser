# Nagi 1.5.0 / code 23 verification

The user authorized a 0.1.0 minor-version increment and release on 2026-11-24.
Fresh official Publisher API preflight found highest uploaded code 22 across
bundles, APKs and all tracks. Production 22 (1.4.1) is PUBLISHED; beta/internal
are empty and alpha retains code 8. Other tracks, countries, store listings and
permission/device declarations are to remain unchanged.

The candidate is isolated from the original dirty checkout and contains the
current user-tested r11 feature source. Original source files are hash-
inventoried under ignored artifacts/1.5.0/ and remain untouched. Validation,
source revision, signed artifacts and release states will be recorded only
when each stage is actually verified.

## Candidate checks

The isolated 1.5.0/code-23 candidate passed all 280 Play Debug unit tests with
zero failures/errors/skips, Debug and Release lint (zero errors, 97 warnings
each), and Debug/main-test APK assembly with JDK 17 and SDK 36. All six exact
Play note blocks contain four equivalent lines and are below 500 characters.
No automatic workflows or release triggers were added.

Selected feature evidence from the same functional source includes the staged
local UI runs recorded in VALIDATION.md and the final r11 seven-case drag run.
Those checks include touch/mouse, phone/tablet, light/dark, Split, CJK input,
Space-scoped history, retained pages/drafts and PiP restoration. They are not a
full connected suite, physical-device or live-provider claim. The known
horizontal-wheel emulator/provider baseline failure remains documented.

Live Console preflight shows no unpublished changes, Managed publishing off,
and upload certificate a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc.
The Play app-signing role is separate. Release artifacts will be built from
the selected clean source revision and checked before upload.
