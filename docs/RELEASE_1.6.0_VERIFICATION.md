# Nagi 1.6.0 / code 24 verification

The user explicitly authorized releasing 1.6.0 on 2026-11-25. The intended
channel is the existing Google Play production track. Source/tag, GitHub draft,
accepted upload, review submission and public availability are separate states.

## Preflight

The official Publisher API found highest uploaded code 23 across bundles,
APKs and all tracks. Production 23 (1.5.0) is PUBLISHED; beta/internal are empty,
and alpha retains code 8. The new artifact uses code 24. Existing countries,
store listings, other tracks and device/permission declarations are preserved.

The candidate includes the phone-menu and sidebar changes from the supplied
test APK. All 19 original changed/untracked source files were backed up with
hashes in ignored artifacts/1.6.0/ before release preparation. No automatic
workflows or release triggers are added.

## Verification and release state

All 280 Play Debug unit tests passed with no failures, errors or skips. Debug
and Release lint passed with zero errors (98 and 97 warnings respectively).
Side-by-side Debug/main-test APK assembly passed with JDK 17 and SDK 36.

Selected UI runs on dedicated API-36 emulator-5588 / WebView 133.0.6943.137
cover 17 distinct cases, each with a passing final execution. These cover phone
toolbar/menu geometry and dismissal, Home/tab identity, clipboard contents and
the intercepted share intent, pending reload, wide/Split address actions,
sidebar hover/secondary-click/reordering, retained pages/drafts and search
placement across widths and Activity recreation. The original 16-case run had
one obsolete expectation that results appear above a bottom search input.
That assertion was already present in v1.5.0, whose unchanged Command Bar puts
phone search at the top. The corrected assertion and the existing bottom-bar
search/IME regression both passed on rerun, followed by a rebuilt test APK and
Debug lint. No product behavior was changed to satisfy the old expectation.

Three additional direct-instrumentation cases passed for screenshot capture.
Phone/top/light, phone/bottom/dark, short-window scrolled controls and wide
light/dark sidebar screenshots were visually inspected. The full instrumentation
suite, physical devices and arbitrary live sites were not run.

The Gradle connected runner automatically removed its preview test packages
after the runs. The subsequent screenshot run used adb install -r and direct
instrumentation, retaining that dedicated preview installation. The normal
package was not uninstalled or cleared. Evidence remains in artifacts/1.6.0/.

## Clean source and signed artifacts

- Feature source was committed and pushed separately as f0104e5. The clean
  release source is a467d0066c97291649ea05d88e667a218ec03877, tree
  4e2f57c6636b73c3dcbbf092e15332dcdf1f343f, with immutable annotated tag v1.6.0.
  The source and tag were reread from origin. Later status documentation is
  separate from this exact artifact source.
- AAB SHA-256: e51e46d1d562b025af11a91da20a43ee1c4bbfbec63dad176cf14f42d4b633ff
  (74,094,644 bytes).
- Local signed QA APK SHA-256:
  49c5ae10b48a87dc13130d6f92aecec4c92b112f6cf9bcc266ec85ee82fda870
  (84,527,633 bytes). This is not a public sideload release.
- The upload certificate downloaded from live Console matches the existing
  upload key, SHA-256 a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc.
  The separate Play app-signing role was checked; no signing keys were changed.
- Bundletool validation, AAB/APK signatures, APK ZIP alignment, native ELF
  alignment and packaged notices passed. These packaging checks do not prove
  runtime behavior with 16KB pages. The bundle is com.takeruf.nagi,
  1.6.0/code 24, minSdk 26/targetSdk 36 and non-debuggable. Debug IME/autofill/
  passkey providers and legacy APK update/installer components are absent.
  Device and permission declarations match the 1.5.0 bundle.

## Signed in-place update

Dedicated API-36 Nagi_CommandBar131Upgrade_QA_20261119 / emulator-5592 passed
the upload-key-signed 23 to 24 update and a cold restart. The installed baseline
and candidate APK hashes matched their verified artifacts, and the package UID
stayed unchanged. All five Command Bar category choices stayed OFF, bottom
search stayed ON, and the selected fixture tab, cookie and localStorage were
retained. The fixture seed URL was requested exactly once; verification and
restart did not reseed it. This is local signed-emulator evidence, separate
from Play-delivered or physical-device update proof.

The first baseline capture was blocked by a System UI ANR during emulator boot.
After choosing Wait, the same loaded fixture and complete update checks passed;
no app data was cleared and no product workaround was added. Both owned QA
emulators, the reverse port and the local fixture server were stopped afterward.

## Accepted upload and review submission

Play accepted code 24 with the exact AAB hash above. The production draft was
saved and independently shown in Console as Draft release: 24 (1.6.0). The six
notes were reread and exactly matched the canonical file. Validation and the
100% rollout commit succeeded with ERROR_IF_IN_REVIEW, without canceling
another review. Independent readback verified the accepted artifact, all six
notes, existing country availability, store listings and other tracks.

At 2026-11-25 17:46:12 JST, the official lifecycle reports code 24 (1.6.0)
IN_REVIEW and code 23 (1.5.0) PUBLISHED. Managed publishing is off, so approval
will publish automatically. Public 1.6.0 availability is not yet confirmed.
At 17:47:09 JST, Console independently showed Changes in review, Production,
24 (1.6.0), Start full rollout and Managed publishing off. No manual publication
action remains before Google approval.

The GitHub release remains a source-only draft with no APK/AAB assets. Exact
receipts, check logs, screenshots and artifact hashes are retained in ignored
artifacts/1.6.0/. The original changed source is backed up there; the intended
changes are committed in main, with the test expectation correction recorded
above. No release keystores or credentials were added to Git.

## Publication confirmed — 2026-11-26

A fresh official production lifecycle readback during 1.6.1 preflight reports
code 24 (1.6.0) PUBLISHED. This confirms the current state, not the precise
publication timestamp. The accepted artifact and submitted notes are unchanged.
The readback is retained in ignored artifacts/1.6.1/lifecycle-before.json.
