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

## Clean source and signed artifacts

- Clean source `a30729bfa2251462295ececf300520bf3310c1de`, tree `79b2aa0e5a16cfd701629bdb4cb52cf206597843`,
  immutable annotated tag `v1.5.0`; source and tag were pushed to origin/main
  and GitHub. Later status documentation is separate from this artifact source.
- AAB SHA-256 `6b2e0ee424bf7791b9c6db37ed8e86ed34bd62c749855de41f5b64c050e882e2`
  (74,077,955 bytes).
- Local signed QA APK SHA-256 `d9c58af5c32b09d21dd5de68f0d2ab8db5fd8f5813fbb66202eda525b52e2e4b`
  (84,527,057 bytes).
- Bundletool validation, bundle/APK signatures, APK ZIP alignment, native ELF
  load alignment and packaged notices passed. This verifies packaging, not
  runtime execution with 16KB pages. The upload-key fingerprint matches the
  live Console upload key; it differs from the Play app-signing role.
- The bundle is com.takeruf.nagi, 1.5.0/code 23, minSdk 26/targetSdk 36 and
  non-debuggable. Debug IME/autofill/passkey providers, legacy APK installer and
  update provider are absent. Device/permission declarations match the code-22
  bundle. No release signing material was added to source or artifacts.

## Signed in-place runtime check

Dedicated API-36 Nagi_CommandBar131Upgrade_QA_20261119 / emulator-5592 passed
the upload-key-signed 22→23 in-place upgrade and a further cold restart.
All five saved Command Bar categories remained OFF, bottom search stayed ON,
and the selected local fixture tab, cookie and localStorage were retained.
The fixture seeded these site markers exactly once before the update; later
launches neither reseeded nor passed another URL intent. The installed APK
digest matched the verified signed artifact. No uninstall, app-data clearing,
network/provider configuration change or physical-device test was used.
An initial concurrent UiAutomator dump terminated one test-side process with
137; the exclusive final verification passed without a product change. The
owned emulator and localhost fixture server were stopped afterward.

## Accepted upload and review submission

Google Play accepted code 23 with the exact AAB hash above. The production
draft, six note blocks and final full-rollout request were read back and
matched the canonical files. Countries, store listings and all other tracks
were unchanged. Validation and commit succeeded using ERROR_IF_IN_REVIEW,
without canceling another review.

At 2026-11-24 10:32:47 JST, the independent official lifecycle read reports
23 (1.5.0) IN_REVIEW and 22 (1.4.1) PUBLISHED. Console independently shows
Changes in review → Production → 23 (1.5.0) → Start full rollout, with quick
checks running and Managed publishing off. Approval will publish automatically;
**public 1.5.0 availability is not yet confirmed**. No manual publication action
remains before Google approval.

The GitHub release is a source-only draft, with no APK/AAB assets. Evidence,
exact receipts, artifact hashes, baseline/final runtime captures and preservation
checks are retained in the original checkout's ignored artifacts/1.5.0/.
All 120 inventoried original dirty source files remained byte-identical.
