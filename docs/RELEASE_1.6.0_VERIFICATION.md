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

Signed artifacts are pending. No 1.6.0 bundle has been uploaded, submitted for
review or verified as publicly available.
