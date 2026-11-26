# Validation

The [1.1.1 Play preparation](RELEASE_1.1.1_PLAY_PREPARATION.md) separates final Play validation from the [initial preflight](RELEASE_1.1.1_VERIFICATION.md) and saved Console state. The dated [1.1.0 release verification](RELEASE_1.1.0_VERIFICATION.md) records comprehensive tests, clean artifact checks and publication evidence. The [1.0.0 publication verification](RELEASE_1.0.0_VERIFICATION.md) and [0.3.0 verification](RELEASE_0.3.0_VERIFICATION.md) remain historical distribution records. These are recorded results, not a live Console or deployment status check.

## History, native menus and permissions, 2026-11-26

The working tree based on `8f134a3` passed all 298 Play Debug JVM tests, with
zero failures, errors or skips. JDK 17 / SDK 36 Gradle Wrapper checks passed:
`testPlayDebugUnitTest`, `lintPlayDebug`, `lintPlayRelease`, `assemblePlayDebug`
and `assemblePlayDebugAndroidTest`. Both lint variants reported zero errors and
97 warnings. The last selection-only test adjustment also passed Debug lint
and instrumentation APK assembly; the final native selection protection passed
the full JVM suite and both lint variants again.

On dedicated `Nagi_Tablet_Preview_20261119` / `emulator-5586` (API 36,
WebView 133.0.6943.137, 320 dpi), the 31-case integration selection passed:

- Five `HistoryPagePreviewEngineTest` cases, including real SPA clicks followed
  by three Back and three Forward steps with destination URL and bitmap-color
  assertions, retained drafts, scrolled images and new-tab traversal.
- Eight `HistorySwipeUiTest` cases covering both preview directions, cancel,
  actual native touchpad event routing, momentum, mouse/touchscreen exclusion,
  horizontal-scroller priority, Split focus and history boundaries.
- Three `HistoryRegressionUiTest` cases covering Room visits, delayed SPA/video
  titles without duplicate visits, reload/display-mode changes and recreation.
- Four `NativeSelectionMenuTest` cases checking visible floating-toolbar Copy
  buttons and actual accessibility clicks, existing multiword selection,
  editable text and a click outside the old selection.
- Three predictive-back cases at phone/light and tablet/dark Split sizes,
  including a system-edge gesture, cancellation and commit.
- Four media-permission cases: real WebView `getUserMedia` audio-track creation
  and stop, denial/retry, document-navigation cancellation, and a synthetic
  geolocation prompt-hide callback. Two upload/link-action dialog cases and two
  existing HTTP/generated download regressions also passed.

At 822 × 1828 px (411 × 914 dp), the bidirectional preview case, all four
media-permission cases and both action-dialog cases passed; permissions and
action dialogs used the dark app theme. All four native selection cases passed
on their focused rerun after correcting wrapped-word test coordinates and
protecting active selection from input-triggered software snapshot capture.
The final selection timing guard also passed four tablet cases independently.
After the active-selection snapshot protection, the latest production sources
passed a further 17-case tablet run of all preview-engine, touchpad and native
selection cases; the four narrow-screen selection cases also passed.
Screenshots of both preview directions, native horizontal selection toolbars,
light/dark permission cards and upload/link actions were inspected.

The opt-in `YouTubeLiveHistoryTest` separately passed a real desktop YouTube
search-result click into a `/watch?v=` URL, a corresponding visit event, and
saved Back/Forward images. This is a time-sensitive public-site check, separate
from the repeatable SPA fixture and Room tests; it does not establish completed
video playback or logged-in provider behavior.

Initial diagnostic failures included fixture JavaScript/coordinate and test
cleanup errors, late native selection-mode timing, and snapshot interference
with active text selection. Corrected cases were rerun; raw initial/final logs,
source hashes, APK hashes and screenshots remain in ignored
`artifacts/history-spa-20261126/`. The QA screen-size override was restored.

These are selected emulator checks, not a full instrumentation suite. Physical
trackpads, OEM menus, real camera/GPS hardware, microphone quality/headset routing
and a completed ChatGPT voice session remain unverified. The scoped permission
review and app-owned/system popup boundary are documented in
[browser integrations](BROWSER_INTEGRATIONS.md). No version change, signed
release artifact, upload or publication is included.

The requested coexistence APK was then built with `:app:assemblePlayDebug
-PnagiSideBySide=true -PnagiAutofillDiagnostics=false`. Its manifest identifies
`com.takeruf.nagi.preview`, launcher label `Nagi Test`, and unchanged
1.6.0 / code 24. APK metadata and its Android Debug signature were verified.
An in-place install and cold launch on the same QA emulator succeeded while
both main and preview package UIDs remained unchanged. The independently named
artifact is `artifacts/history-spa-20261126/side-by-side/nagi-test-preview-20261126.apk`.
The feature tests above used the main debug package; the coexistence build has
separate build/metadata/installation/startup coverage, not a repeated full suite.

The same QA emulator's later network failure was isolated to unresponsive
virtual DNS aliases (`10.0.2.3` / `10.0.2.4`): direct public-DNS queries and
hostname-verified TLS/HTTP to a resolved address succeeded. Restarting the same
AVD/port with `-dns-server 1.1.1.1,8.8.8.8` restored ordinary resolution,
Google/gstatic HTTPS 204 responses, YouTube HTTPS 200 and Android `VALIDATED`.
Nagi Test then rendered an actual HTTPS page's title and body in a new tab.
Package UIDs and existing tabs were retained. The DNS override belongs to this
launch; use the same argument on later launches if needed. Evidence is under
ignored `artifacts/emulator-network-20261126/`; see the
[official emulator DNS option](https://developer.android.com/studio/run/emulator-networking-dns).

## Repeatable checks

Use JDK 17 and Android SDK Platform 36, following the [build setup](../README.md#build-and-run). Run instrumentation on a dedicated QA emulator; select its serial explicitly.

```sh
./gradlew :app:testPlayDebugUnitTest :app:lintPlayDebug :app:lintPlayRelease --no-parallel
./gradlew :app:assemblePlayDebug :app:assemblePlayDebugAndroidTest --no-parallel
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedPlayDebugAndroidTest --no-parallel
```

For signed Play AAB checks and local runtime-validation APKs, follow [RELEASING.md](RELEASING.md). A targeted instrumentation run establishes coverage only for its selected cases; it does not establish a passing full suite.

The legacy GitHub source set may still be checked for historical regressions, but new official releases use Play only.

## Feature coverage and limitations

- [New-tab background editor](NEW_TAB_APPEARANCE.md): preview, mode/color controls, saved-photo reuse and cancellation.

- [Compact-view spacing](UI_DENSITY.md): Settings, tabs/history, toolbar spacing and retained input/page state when keyboard availability changes.
- [Menu layout](MENU_LAYOUT.md): hardware-keyboard density, shared popup sizing, hover/dismissal and large-font reachability.
- [Favorites](FAVORITES.md): sidebar/sheet editing, URL validation, retained identity/order, draft restoration and fixture WebView navigation.
- [Command Bar settings](COMMAND_BAR.md): category/command visibility, saved choices, shortcut hints, empty-list navigation and retained live pages.
- [Narrow-window browsing](COMPACT_UI.md): window boundaries, tab sheets, phone UA/Client Hints, per-site choices, and page/draft retention through resizing.
- [Privacy, Reader, and blocking](PRIVACY_READER_BLOCKING.md): actual WebView requests, exceptions, storage isolation, Reader sanitization, and restoration of blocked-ad containers.
- [Page translation](PAGE_TRANSLATION.md): page-menu/language controls, live DOM translation and restoration, session cancellation, and an opt-in real-model download/translation smoke test.
- [Live-site advertising checks](AD_SLOT_COLLAPSE_VALIDATION.md): recorded DOM measurements, content preservation, and remaining detection gaps.
- [History by Space](HISTORY_SPACES.md): scoped recording, suggestions, per-Space limits, schema-v4 upgrades and legacy ownership.
- [History gestures](HISTORY_GESTURES.md): predictive edge-back previews, cancellation/commit, native WebView history, trackpad dispatch, nested scrolling and Split targeting. The 2026-11-23 record includes the baseline-matched horizontal mouse-wheel failure.
- [Video presentation](VIDEO_PRESENTATION.md): fullscreen/system-PiP lifecycle, single-surface ownership, repeated source returns, playback and per-site limits.
- [Password autofill](PASSWORD_AUTOFILL.md), [passkeys](PASSKEYS.md), [browser integrations](BROWSER_INTEGRATIONS.md), and [localization](LOCALIZATION.md): feature-specific scope and provider/device boundaries.

Video instrumentation uses local HTML5 fixtures for playback, fullscreen, system PiP, pop-out movement/resizing, cross-origin frames, and source-tab lifecycle. The previous release includes the recorded video regression results. Regenerate the checked-in six-second looping fixture when needed:

```sh
ffmpeg -f lavfi -i testsrc2=size=320x180:rate=15 -t 6 -an -c:v libx264 -profile:v baseline -pix_fmt yuv420p -crf 32 -movflags +faststart app/src/androidTest/assets/video.mp4
```

User-started playing videos automatically pop out when their source tab is hidden by a switch to another tab or Space. Showing the source again (including in either Split pane) returns the same video to its page without pausing or reloading. Going Home automatically enters Android system PiP only for an existing fullscreen or pop-out video, including when only one tab is open. Ordinary inline playback, including muted autoplay, never enables Home/PiP; leaving fullscreen disables it again. Autoplaying page demos do not pop out on tab switches; playback intent is tracked per video and cleared when its media source is reset. Paused or ended videos do not start an automatic presentation. Settings independently persist automatic tab pop-out and Home/PiP, both enabled by default; no manual pop-out button is injected into websites.

Local verification on 2026-11-04 passed 16 `VideoPresentationTest` cases (API 26/35), all 12 `VideoUiTest` cases on the API 36 `Nagi_113_PiP_Release_QA` emulator, GitHub Debug build, and lint. The final policy permits tab pop-out for user-started videos and existing fullscreen playback, while autoplaying page demos remain inline. The real WebView regression visits a muted autoplay demo without controls, performs an unrelated page action, switches tabs, and checks that no presentation is created. Existing tests cover manual playback pop-out, cross-origin frames, repeated zoomed tab switches, and fullscreen/system PiP. Logs and screenshots are saved in `artifacts/pip-manual-20261104/`. This does not establish verification on the recording's physical device or a distributed release.

The Home/PiP policy was checked against public browser sources on 2026-11-03. Chromium's [fullscreen video controller](https://github.com/chromium/chromium/blob/main/chrome/android/java/src/org/chromium/chrome/browser/media/FullscreenVideoPictureInPictureController.java) requires an active effectively fullscreen video with PiP allowed. Firefox's [Android PiP feature](https://github.com/mozilla/firefox/blob/main/mobile/android/android-components/components/feature/session/src/main/java/mozilla/components/feature/session/PictureInPictureFeature.kt) requires fullscreen plus playing media. Chromium also has a separate [site-registered automatic PiP feature](https://github.com/chromium/chromium/blob/main/media/base/media_switches.cc), disabled by default in the inspected source; it is not equivalent to promoting every playing HTML video. These are source observations, not tests of installed Chrome/Firefox versions. Nagi additionally preserves PiP continuation from an existing in-app video pop-out.

Local verification on 2026-11-03 passed 161 Play Debug unit tests, `assemblePlayDebug`, `assemblePlayDebugAndroidTest`, and `lintPlayDebug`. Four selected `VideoUiTest` cases passed on the API 36 `Nagi_111_PreRelease_QA` emulator: muted autoplay does not enter Home/PiP, manually started inline playback with one tab does not enter Home/PiP, fullscreen playback enters PiP and disables auto-entry again after leaving fullscreen, and an existing tab pop-out still supports playback/resize/PiP/return. Home screenshots were visually checked and retained under ignored `artifacts/pip-home-20261103/`. The initial instrumentation attempt stopped in setup because the emulator had a saved Chinese app locale while these tests expect English; rerunning with English passed. This is local fixture/emulator evidence, not verification on the recording's physical device or a Play release.

In-app pop-outs initially show only the video. A tap overlays Lucide controls (return at top left, close at top right, play/pause at center, playback time at bottom left, fullscreen at bottom right) without changing the window dimensions or passing the tap to the site. Controls hide after three seconds of inactivity, respecting the system's recommended accessibility timeout. Drag anywhere to move the window and pinch to resize it. Mouse users can also drag any corner to resize with the opposite corner anchored and the video's aspect ratio preserved; corners show diagonal resize cursors and only intercept primary mouse input. Instrumentation advances the Compose clock to verify the timeout, separately from the live WebView playback clock.

`PageVideoCompatibilityTest` checks YouTube-shaped players in WebView: unstarted channel trailers receive the matching video's thumbnail, SPA changes discard stale helper-supplied posters, site-supplied posters are preserved, and inline video presentation restores the page's original styles. Nagi injects no manual video button over YouTube miniplayers. The compatibility helper does not start playback or change YouTube's own miniplayer behavior.

Video presentation leaves WebView page zoom untouched; it does not normalize or restore zoom through asynchronous scale callbacks. A desktop-width regression sets a user zoom, switches tabs three times, and checks the original zoom, playback, and absence of reloads on every return.

Video surfaces fit the visual viewport's dimensions and offsets rather than assuming the visible origin remains at layout position zero. A zoomed WebView regression repeatedly shrinks and enlarges its native bounds, checking that all four video edges track the visible viewport and playback continues.

Physical devices/fold transitions, manufacturer IMEs and trackpad drivers, real camera/microphone/location hardware, Android 8–15 runtime behavior, mainland-China networking, general production video compatibility, DRM, and embedded players in closed shadow roots remain unverified. Retaining a live WebView does not establish DOM/form recovery after process death. Updater Robolectric coverage on API 26 and 35 is separate from device proof.

With Adaptive dark mode off (the default), WebView keeps its ordinary white canvas when the app theme changes. A pixel regression checks a transparent page after a live dark-theme switch, then confirms that an explicitly authored dark background survives both app themes.

Website color preference follows the effective app theme (System, Light, or Dark). Each engine uses an isolated native theme context; changes dispatch WebView configuration updates without navigation or JavaScript media-query shims. Optional native algorithmic darkening is controlled separately in Appearance settings. `WebViewEngineTest#websiteColorSchemeChangesWithoutReloadingOrLosingPageState` checks initial dark preference, live light/dark matchMedia events, actual CSS media-query colors, preserved JavaScript state, and a single document request.

Password autofill is disabled in the production configuration pending approval. Regression checks assert that native WebView autofill is excluded, direct manual requests return awaiting-approval status, the entire Settings section and page-menu action are absent, and no awaiting-approval notice is shown in the normal UI. Retained synthetic-provider tests explicitly opt in to the implementation and are not evidence that the production feature is available.

## Commit validation (2026-11-09)

Play Debug unit tests passed all 180 cases. Play Debug/Release lint reported
zero errors and 72 warnings each; Debug and instrumentation APK builds passed.
On the API 36 `Nagi_114_Camera_QA` emulator (`emulator-5580`, WebView 133),
all 12 `VideoUiTest` cases passed. The initial combined run then crashed while
closing an unused private profile. `PrivateProfiles.clear` now checks that the
profile exists before accessing provider storage; the added unused-profile
regression and all 15 selected private-lifetime, privacy/Reader-engine, and
camera-settings cases passed on rerun.

Camera settings also passed standalone at 1280×800 dp in light mode and
540×360 dp in dark mode, with screenshots visually inspected. The narrow run
initially timed out because the test assumed a visible sidebar Settings button;
the test now opens the compact tab sheet when needed and passed on rerun.
Evidence is retained under ignored `artifacts/commit-review-20261109/`.
Split-pane camera layout and physical-device behavior were not checked in this
run. These results do not establish a full instrumentation suite or store release.

## Landscape camera avoidance

The camera behavior matches the shipped 1.1.7 source: Settings → Appearance
registers the camera's physical side in landscape. Extra spacing of 0–64 dp
(default 32 dp) is applied only when the camera is on the left, following
180-degree display rotation. Portrait and right-side camera positions add no
camera margin. Registration and margin survive preference updates and restarts.
The expanded sidebar continues its background into the left margin.

The 2026-11-09 commit initially reintroduced the earlier symmetric-margin
implementation from the old 1.1.2 branch. The follow-up restores camera-specific
production code, translations, and calibration regression tests from `v1.1.7`,
while preserving the Spaces setting and private-profile crash fix. Earlier
symmetric-margin test results above describe that superseded implementation.

Follow-up validation on 2026-11-09 passed all 180 Play Debug unit tests,
Debug/Release lint (zero errors, 72 warnings each), and Debug/test APK builds.
Both restored `CameraAvoidanceUiTest` cases passed on `emulator-5580`:
position registration, half-turn left/right switching, portrait exclusion,
fine adjustment/reset, and Activity recreation. The position selector screenshot
was inspected in the wide light layout. Evidence is under ignored
`artifacts/camera-preserve-20261109/`. Narrow/dark and Split were not rerun for
this follow-up; physical camera placement remains unverified.

## Settings category navigation (2026-11-10)

`SettingsNavigationUiTest` passed both cases on the dedicated API-36
`Nagi_Download_QA` emulator (`emulator-5584`, WebView 133.0.6943.137).
It verifies the category/detail hierarchy at 360/700/768/1000 dp, the 760 dp
available-content boundary, selection and preference retention through Activity
recreation, meaningful confirmations, hidden unapproved autofill, and returning
to the same live WebViews, Split pair, drafts and saved sidebar width.

Sixteen distinct existing settings-related cases passed across focused runs:
camera, Spaces, common/AI/custom engine settings, CJK engine-name composition,
Japanese UI navigation, video settings, new-tab appearance, theme persistence,
the bottom search bar, site-data confirmation/workspace retention, and the
autofill gate. Initial harness failures were corrected for the new categories
and hidden browser sidebar, a fixed English label, horizontal Space scrolling,
and portrait display-cutout geometry. All selected cases passed on their final
runs; this is not a full instrumentation suite.

Debug and instrumentation APK builds passed. Screenshots for wide/light,
wide/dark, compact/dark and the two-pane boundary were inspected and retained
with logs and frozen artifacts under ignored `artifacts/settings-ui-20261110/`.
The privacy lifetime/download explanation, camera instructions, and destructive
action confirmations remain. About shows its version once; update dialogs keep
their version display. Physical devices, OEM rendering and a store release were
not verified by this UI task.

## Download lifecycle and notifications (2026-11-10)

The local working tree, including pre-existing UI work, passed all 190 Play Debug
unit tests, Play Debug/Release lint (zero errors, 72 warnings each), and Debug/test
APK builds. Download contract/notification unit coverage includes API 26/35;
native confirmation-dialog callbacks are covered on API 35.

On the dedicated `Nagi_Download_QA` emulator (`emulator-5584`, API 36, WebView
133.0.6943.137), all seven `DownloadUiTest` cases passed at 411×914 dp with a
light app theme and 1000×800 dp with a dark app theme. They cover an attachment
opened in a new tab, redirects, live opener/draft retention, saved Split pairs,
cancellation, manually created tabs, already-rendered popup pages, and selection
changes while confirmation is open. A paced 2 MiB HTTP fixture completes through
DownloadManager with byte-for-byte verification. An 8 MiB Blob fixture exercises
the native progress notification, destination copy, completion notification and
saved output length. Screenshots include the notification shade, opener and Split.

Five selected `WebViewEngineTest` cases also passed: native acceptance before
cleanup, navigation during confirmation, cookie/filename preservation, Blob/data
exports after object-URL revocation, and actual Downloads collection output.
Initial test-harness failures involved dialog scheduling, a fixture route collision,
notification accessibility lookup and Split rendering/hit-test timing; the
corrected final runs passed. The HTTP notification can be minimized by Android;
generated-file notifications visibly showed percentage, byte counts and a progress
bar. Evidence and frozen Debug/test APKs are under ignored
`artifacts/download-qa-20261110/`.

This is selected local emulator coverage, not a full instrumentation suite,
physical-device/OEM notification validation, live-site compatibility proof, or
a store release. No version bump, tag, upload or publication was performed.

## Combined feature validation (2026-11-10)

After integrating Settings navigation, native page zoom, the six additional
keyboard actions and download handling, all 193 Play Debug unit tests passed.
Play Debug/Release lint each reported zero errors and 81 warnings; Debug and
instrumentation APK assembly passed. Debug lint passed again after adding the
download fixture diagnostics. Selected Settings, zoom/video and keyboard/IME
emulator results are recorded above and in `KEYBOARD_SHORTCUTS.md` and
`COMPACT_UI.md`; this was not a full connected-test run.

The final three-case download rerun passed generated-file progress/output and
Split restoration under normal scheduling. Its HTTP case closed the temporary
tab and preserved the live opener/draft, but DownloadManager stayed pending with
zero bytes. The QA emulator's default network lacked Android's validated-network
capability. A data-preserving reboot and an A/B run with the previously passing
download-only APK both reproduced that wait. Ordinary HTTP worker startup is
therefore not established by this final integrated run.

For a separate controlled loopback check, `DownloadUiTest` accepts the explicit
instrumentation argument `forceLocalDownloadJob=true`. Only its paced HTTP
fixture can force its own pending DownloadProvider job once through
`cmd jobscheduler run -f`; the default test path and production code do not
bypass scheduling constraints or change persistent device configuration.
All three selected cases passed in this mode (24.981 s), including native HTTP
progress notification, byte-for-byte 2 MiB output, temporary-tab close, live
opener/draft preservation, an 8 MiB Blob notification/save and the visible Split
pair. This verifies the transfer after explicit job execution, not normal OS
scheduling on the current QA network.

The frozen integrated main APK has SHA-256
`0ef992d15caf81b66610c7ebce5249a265a692d95be3969c7d7d23873bf2cb6f`.
Final logs, diagnostic output, test APK and compact evidence are retained under
ignored `artifacts/integration-20261110/`; the main APK and zoom evidence are in
`artifacts/page-zoom-20261110/`. No physical-device test or release was performed.

## Settings selective commit validation (2026-11-15)

An isolated checkout from `7a566ce` validated category/detail Settings navigation,
the Settings-only sidebar visibility change, four-language labels, and the
single About-version display in both distribution source sets.

All 193 Play Debug unit tests passed with zero failures, errors or skips.
Play Debug/Release lint each reported zero errors and 81 warnings.
The JDK-17 Gradle Wrapper passed these tasks with `--no-parallel`:

```sh
./gradlew :app:testPlayDebugUnitTest :app:lintPlayDebug :app:lintPlayRelease :app:assemblePlayDebug :app:assemblePlayDebugAndroidTest :app:compileGithubDebugKotlin --no-parallel
```

Dedicated `Nagi_Selected_20261115` (`emulator-5586`, API 36, WebView
133.0.6943.137) passed all 22 selected instrumentation executions:

- Both `SettingsNavigationUiTest` cases passed across 360/700/768/1000 dp,
  light/dark themes, Activity recreation, persisted preference/category
  selection, the 760 dp available-content boundary, confirmation cancellation,
  the production autofill gate, and return to identical live Split WebViews,
  edited Japanese drafts and the saved sidebar width.
- Nineteen affected regression cases passed for search engines, custom-engine
  persistence, engine-name CJK composition, theme chips, camera registration
  and rotation, Spaces, video settings, new-tab appearance, background
  suspension, site-data confirmation, autofill availability, the bottom-search
  setting, and keyboard navigation through Settings/Omnibox.
- `LocalizationUiTest#japanese` passed category navigation, search customization,
  theme selection and return to browsing with Japanese UI labels.

No selected case failed or skipped. The original IME was confirmed restored.
The three new Settings strings exist in all four locales with matching numbered
placeholders. Narrow category/detail, wide light/dark and boundary screenshots
were inspected. Installed main/test APK hashes matched the selected build.

Logs, exact regression selections, screenshots and content hashes are retained
under ignored `artifacts/settings-commit-20261115/`. This is selected emulator
coverage, not a full instrumentation suite, physical-device/OEM validation or
a store release. No version bump or release action was performed.

## Download, page zoom and resume validation (2026-11-15)

An isolated checkout from `ab003cc` validated download lifecycle/notifications
and the shared Compose confirmation, native page-menu zoom, and new-tab resume
links for current-Space pinned tabs and saved Split pairs.

The selected tree passed all 190 Play Debug unit tests with no failures, errors
or skips. Play Debug/Release lint each reported zero errors and 72 warnings.
The following JDK-17 Gradle Wrapper tasks passed with `--no-parallel`:

```sh
./gradlew :app:testPlayDebugUnitTest :app:lintPlayDebug :app:lintPlayRelease :app:assemblePlayDebug :app:assemblePlayDebugAndroidTest --no-parallel
```

After the final download-test update, instrumentation APK assembly and Play
Debug lint passed again. Four-language resource checks confirmed all ten added
strings and matching numbered placeholders.

Dedicated `Nagi_Selected_20261115` (`emulator-5586`, API 36, WebView
133.0.6943.137) passed 28 selected instrumentation executions:

| Window and theme | Selection | Passed |
| --- | --- | --- |
| 1000 × 800 dp, dark | DownloadUiTest, PageZoomEngineTest, PageZoomUiTest, NewTabShortcutsUiTest | 14 |
| Approximately 411 × 914 dp, light | DownloadUiTest, PageZoomUiTest, NewTabShortcutsUiTest | 11 |
| Wide window | WebViewEngineTest native-acceptance and navigation-during-confirmation cases; VideoUiTest returningFromPopupPreservesDesktopPageZoomAcrossRepeatedTabSwitches | 3 |

Both HTTP runs used ordinary DownloadManager scheduling, with no forced-job
argument. The paced 2 MiB fixture completed with byte-for-byte verification,
automatic empty-tab closure and retention of the live opener/draft. Generated
8 MiB exports passed progress/completion notification and saved-length checks.
Cancellation, manual tabs, previously displayed popup documents, changed
selection, saved Split/opener restoration, metadata and Back dismissal passed.

Zoom checks cover native whole-page scale, reset, rapid operations, viewport
limits, pending-work protection, live state and snapshot scale/scroll restoration.
Resume checks verify pinned selection and restoration of both saved Split panes,
identical WebViews, edited Japanese drafts and a single document request.
Phone/light and tablet/dark screenshots of resume links, restored Split, page
zoom, download confirmation and progress were inspected.

An earlier shared-device attempt reported a process crash; its installed APK
hash differed from the selected build when inspected afterward. The final runs
used the separate AVD, complete confirmation-UI dependencies and a matching
installed APK. The selected main APK SHA-256 is
`dea5dfb7b097d6803ed26b74ef7465bbd824c24c02e9c393f70260d992e5fa2b`.
Exact selections, logs, screenshots and content hashes are retained in ignored
`artifacts/selected-commit-20261115/`.

This is selected emulator coverage, not a full instrumentation-suite result,
physical-device/OEM validation, other-provider or live-site compatibility proof.
No version bump or release action was performed.

## Local artifact retention

Generated output belongs in Git-ignored `artifacts/` or `app/build/`. Keep the latest and previous signed release APK/AAB, checksums/update metadata, and compact final verification evidence. Remove superseded Debug APKs, repeated downloads, build intermediates, raw logcat/build logs, interrupted runs, and obsolete screenshots after their useful results are recorded.

The 2026-10-11 cleanup removed older local runs and chronological development reports. Source tests, reusable scripts, branding/store assets, README images, and release notes remain. Earlier tracked records can be recovered from Git history; ignored raw outputs are not retained.

## Adaptive dark mode checks

- `PageDarkeningSettingsTest` covers the upgrade default (off), atomic concurrent
  theme/exception updates, persistence after reopening, and retained exclusions
  while disabled.
- `EnginePoolTest#darkeningPolicyReachesBothPanesBackgroundNewAndResumedEnginesWithoutReloads`
  covers live panes, background engines, new sessions and suspended restoration.
- `PageDarkeningEngineTest` uses local HTTP fixtures and native screenshot pixels:
  default off, dark-theme opt-in, light-theme inactivity, live site/global
  switches, drafts and scroll without reload, destination-host policy through
  redirects/history, authored dark colors and author opt-out.
- `SettingsNavigationUiTest#adaptiveDarkModeSettingAndSiteMenuUpdateLiveSplitAndSurviveRecreation`
  exercises the actual Appearance toggle and page-menu actions at 1000dp and
  411dp, both themes, two live Split WebViews, site-specific exclusions,
  re-enabling and Activity recreation. Screenshots are kept in ignored artifacts.

These checks use the dedicated API 36 QA emulator. Other WebView versions,
physical devices and arbitrary public websites remain separate verification.

## Theme-aware favicon validation (2026-11-17)

See [favicon acquisition, storage and validation](FAVICONS.md) for the 218-unit-test
build/lint results, 11 selected local WebView/UI cases, two separate GitHub/ChatGPT
live/restart checks, screenshot coverage, cache limits and provider limitations.


## Settings retention audit (2026-11-18)

An isolated candidate based on `22934d3` fixes two settings-retention paths:
legacy search-engine selections without an automatic-region flag keep their
manual web/AI choices; download-folder grants are released only after saving
succeeds and are retained while selected by the normal workspace. Private-only
replacement grants can still be released when no longer selected.

`SettingsPersistenceTest` checks all 36 `BrowserSettings` fields against raw
shipped preference keys, disk reopening, concurrent unrelated updates, cleared
nullable/empty values, unknown keys and seeding markers. Its legacy search tests
failed before the fix on API 26 and 35, then passed. Download-folder tests cover
write failure, release ordering, reselecting the same folder and private/normal
separation.

The JDK-17 Wrapper passed all 233 Play Debug unit tests with zero failures,
errors or skips, Play Debug/Release lint (zero errors, 96 warnings each), and
Debug/main-test APK assembly using `--no-parallel`:

```sh
./gradlew :app:testPlayDebugUnitTest :app:lintPlayDebug :app:lintPlayRelease :app:assemblePlayDebug :app:assemblePlayDebugAndroidTest --no-parallel
ANDROID_SERIAL=emulator-5590 ./gradlew :app:connectedPlayDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.takeruf.nagi.DownloadLocationSettingsUiTest,com.takeruf.nagi.CommandBarSettingsUiTest,com.takeruf.nagi.SettingsNavigationUiTest#adaptiveDarkModeSettingAndSiteMenuUpdateLiveSplitAndSurviveRecreation' --no-parallel
```

All five selected UI cases passed with no failures or skips on dedicated
`Nagi_SettingsRetention_QA_20261118` (`emulator-5590`, API 36, WebView
133.0.6943.137). They cover cancellation/resetting of a saved download folder,
Command Bar choices, Activity recreation, live Split/draft retention and page
darkening across phone/light and tablet/dark states. Both download-location
cases also passed a direct-instrumentation screenshot rerun; the 411dp/light
and 1000dp/dark screenshots were visually inspected.

The folder UI test uses a synthetic URI. Actual document-provider grants,
physical/OEM devices, full instrumentation and Google Play update delivery
were not tested. The earlier reported Command Bar reset remains unreproduced;
these findings do not establish its cause. Compact evidence and the focused
patch are under ignored `artifacts/settings-retention-20261118/`. No version
bump or release action was performed.


## Command Bar read recovery (2026-11-19)

The reported update was clarified as 1.3.1 → 1.4.0, with Settings switches
returning to ON. A fresh locally signed 1.3.1/code-20 install on dedicated
`Nagi_CommandBar131Upgrade_QA_20261119` (`emulator-5592`, API 36) retained all five
OFF choices through process restart, an in-place 1.4.0/code-21 update and another
restart. Real Command Bar screenshots before/after the update showed only the
input with all suggestions hidden. This ordinary APK-replacement path did not
reproduce the reset; it is separate from Google Play delivery and the user's
physical device.

An independent injected-read-failure test found a matching model-state defect:
`IOException` produced empty preferences, enabling every category, and completed
the observer so later saved values were no longer received. Before the fix,
eight of ten API-26/API-35 test executions failed. Read recovery now retries
transient I/O after 500ms, including startup seed reads, without emitting defaults
or writing settings. Cancellation stops retries. Corruption and other unexpected
errors propagate; the ViewModel disables readiness and uses the existing error
screen while retaining its last settings value. A permanent I/O outage waits for
storage instead of pretending that the store is empty.

All six recovery cases pass on both API 26 and 35 (12 executions). They cover
startup failure, failure after valid settings, seed-marker reads, continued
outages, cancellation, and corruption/non-I/O failures. The isolated candidate
and original working tree both passed all 245 Play Debug unit tests, with zero
failures/errors/skips, and Debug/Release lint (zero errors, 96 warnings each).
The candidate also passed Debug and instrumentation APK assembly:

```sh
./gradlew :app:testPlayDebugUnitTest :app:lintPlayDebug :app:lintPlayRelease :app:assemblePlayDebug :app:assemblePlayDebugAndroidTest --no-parallel
ANDROID_SERIAL=emulator-5590 ./gradlew :app:connectedPlayDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.takeruf.nagi.CommandBarSettingsUiTest,com.takeruf.nagi.SettingsNavigationUiTest#adaptiveDarkModeSettingAndSiteMenuUpdateLiveSplitAndSurviveRecreation' --no-parallel
```

All three selected UI cases passed without failures or skips on dedicated
`Nagi_SettingsRetention_QA_20261118` (API 36, WebView 133.0.6943.137), covering
phone/light and tablet/dark Command Bar settings, recreation, retained live
Split/drafts and adaptive dark mode. These emulator tests did not inject storage
errors; read-failure evidence is from the separate JVM tests.

Evidence is under ignored `artifacts/commandbar-131-140-20261119/`. The actual
user-device cause remains unconfirmed. No version bump or release action ran.

## Reload, bottom notices and compact tab dragging (2026-11-23)

The dedicated API-36 `Nagi_CompactPanel_QA_20261118` emulator (`emulator-5588`,
WebView 133.0.6943.137) reproduced reload selecting native history before the
requested document committed. The first HTTP load returned to `about:blank` /
New tab; a subsequent pending load reloaded the previous page. A controlled
late blank-history callback also overwrote the requested URL. Three of the four
initial engine regressions failed before the fix; committed POST resubmission
already passed. This is local fixture/provider evidence, not a reproduction on
the user's physical device or the live Google Play website.

The final source passed all 251 Play Debug unit tests (no failures, errors or
skips), Debug/test APK assembly, and Play Debug/Release lint (zero errors and
96 warnings each). Seventeen selected instrumentation cases passed without
failures or skips:

- Six `ReloadNavigationEngineTest` cases: pending initial/next GET, blank-history
  callback, redirects, native committed-POST resubmission and retained Referer.
  Header input is controlled; the original request is retried rather than
  forwarding its headers directly to a redirect destination.
- Two `ReloadButtonUiTest` cases: actual toolbar/menu controls at phone/bottom/
  dark and tablet/top/light widths, retained selection/identity and a freshly
  rendered document after a committed reload.
- Two `BottomBrowserNoticeUiTest` cases: external-app and translation cards
  above the bottom toolbar or actual IME, top-layout restoration, wide Split,
  retained translation choice and the same live WebView/document/draft.
- Two `CompactTabDragUiTest` cases: top/light and bottom/dark sheet bounds,
  pointer tracking, cancellation without mutation and intended reorder.
- Existing whole-row reorder/pin/cancel, Split-pair drag and Favorite landing
  cases in `SidebarDragTest`, plus content-sized sheet and resize/draft/mode/
  reload cases in `CompactBrowserUiTest`.

Initial post-fix harness failures were corrected for a provider-omitted Referer,
a legitimate pre-URL state-reset sample, and clipped off-screen preview bounds.
The final runs above passed. Screenshots were visually inspected; logs, baseline
and final hashes and compact results are under ignored
`artifacts/parallel-browser-fixes-20261123/`. The full instrumentation suite,
physical devices and other WebView/OEM versions remain unverified. No version
bump, release build, tag, upload or publication was performed.

## Mouse clicks through non-modal notices (2026-11-23)

The supplied recording showed a translation-card click navigating a page link
under the card. A raw-window-event fixture reproduced a page click from the
card's inert title with a mouse; the touchscreen case passed before the fix.
`NativeSurface` now accepts generic mouse button events only for its own
device/down-time gesture, including a matching final release after `UP`.
Clearing ownership immediately at `UP` dropped that late-release page click;
the final implementation waits for the matching sequence to end instead.

All 27 selected API-36 emulator cases passed in the final runs: four
`BrowserNoticeInputUiTest`, two `BottomBrowserNoticeUiTest`, seven
`HistorySwipeUiTest`, the nested/fractional/pane-targeted three
`BrowserScrollTest` cases, seven `PageTranslationUiTest`, and four
`OverflowMenuUiTest`. The input fixture checks inert areas, disabled controls,
one translation callback, dismissal, both release orders, zero underlying page
clicks/down events, and normal outside/subsequent page clicks. The translation
callback fixture does not download models or establish live translation quality.
Existing checks cover top/bottom/IME placement, wide Split and retained pages.

Debug/test APK builds, Play Debug/Release lint (zero errors, 97 warnings each),
and `git diff --check` passed. JVM and full instrumentation suites, physical
input hardware, the original live article and other OEM/WebView versions were
not rerun. The source video was inspected locally; frames, baseline/final logs
and Debug-signed parallel-APK metadata are in ignored
`artifacts/popup-input-20261123/`. The preview package remains
`com.takeruf.nagi.preview`; no release/publication or version bump is included.

## Single photo control in the background editor (2026-11-23)

The separate Choose photo button was removed. Photo opens the picker when no
photo is saved; switching from another style reuses a saved photo. Tapping an
already selected Photo opens the picker again. Canceling keeps the current photo
and style. The existing localized Choose photo label describes the picker action
to accessibility services. Import progress remains visible, and the photo storage
description is shown only in photo/import states. Private-mode import restrictions
and the stored-photo pipeline remain in place.

Both `NewTabAppearanceTest` cases passed at 1000 dp/light and 411 dp/dark
(four executions), with an additional wide visual check. This covers removal of
the duplicate control, picker launch and cancellation, reuse without launching a
picker, settings navigation/recreation, bounded EXIF-oriented import and retained
images after the source is deleted. Picker results are controlled through the
real activity-result contract; cloud-photo providers and physical devices are
not established by these fixtures. Phone/tablet editor screenshots were inspected.

Debug/test APK builds, Play Debug lint (zero errors, 97 warnings) and
`git diff --check` passed. JVM/full instrumentation suites and Release lint were
not rerun for this small UI change. Evidence and the Debug-signed parallel test
APK are under ignored `artifacts/photo-chip-20261123/`. No version bump or
publication is included.

## Compact preference and background editor (2026-11-24, local)

Appearance now offers Always off, With a physical keyboard (default), and
Always on for shared chrome spacing, menus and Command Bar suggestions.
Settings category width stays 236 dp and browser window/Split breakpoints do
not change. The background editor has a search-field preview, two-column mode
samples and grouped preset/HEX color controls; its existing photo import,
replacement, cancellation and saved-image reuse remain available.

Empty Command Bar input lists only the selected Space's open tabs. Explicit
search still includes other Spaces and prefixes those tab subtitles with their
Space name. The previous global empty list could show a second, identically
named blank tab from another Space even though the sidebar listed only one.
Different tab IDs remain separate even when their URLs match; no tabs or
workspace data are removed.

Play Debug JVM tests passed 257 cases with no failures or skips, including
mode defaults/unknown values, concurrent preference edits, disk reopening and
cross-Space suggestion behavior. Play Debug/Release lint each reported zero
errors and 97 warnings. Debug and instrumentation APK assembly passed. The
first integration build caught two stale `dimensions.keyboard` references in
the background editor after the shared field became `compact`; both were
corrected before the final successful source build.

On the dedicated API-36 emulator `Nagi_CompactPanel_QA_20261118`
(`emulator-5588`), 17 selected cases passed: CompactUiSettingUiTest (2),
HardwareKeyboardUiTest (2), CommandBarKeyboardLayoutUiTest (1),
CommandBarDropdownUiTest (4), CommandBarFaviconUiTest (2), OverflowMenuUiTest
(4), and NewTabAppearanceTest (2). The background cases ran at 1000 dp/light;
both passed again at 411 dp/dark (19 successful executions total). Screenshots
of both background layouts and compact Settings were visually inspected.
Controlled Android keyboard configurations cover automatic and forced modes;
these checks do not establish physical-keyboard/OEM behavior. Live WebView and
Japanese draft retention, including Split, passed during preference changes.
Photo results use the real picker contract with controlled test results, not a
live cloud-photo provider. The full instrumentation suite was not run.

The local debug APK `nagi-test-20261124-r6.apk` uses `com.takeruf.nagi.preview`,
launcher label Nagi Test, and unchanged 1.4.1/code 22. APK metadata and its v2
Android Debug signature were verified. Installing with `-r` retained both main
and preview package UIDs, and the preview cold launch reached the foreground.
SHA-256: `a96571e27ff1535764b66353a8b29ca7a7da36206b2aee9c360455705458ac61`.
Logs, screenshots and artifact metadata are in ignored
`artifacts/ui-refinement-20261124/`. No physical device, release upload, tag,
publication or version change was performed.

## Search layout choice, migration tip and AFPBB recovery (2026-11-24, local)

Command Bar settings now save Address bar (new) or Centered (previous), defaulting
to Address bar for existing/missing/unknown values. The new panel has a return
and a Dismiss tip action pinned below its scrollable results. Explicit layout
choices and dismissal suppress the migration tip persistently; Settings retains
the selector. Changing layout keeps the same editor node, text, selection,
focused suggestion and InputConnection. It does not rebuild the live page.
Centered placement uses the safe area above the IME, independently of the
browser toolbar's top/bottom choice and compact spacing. Five added labels are
present once in each of the six locales. No analytics collection was added.

Play Debug JVM tests passed 261 cases without failures/errors/skips, including
layout/dismissal persistence, all-field concurrent settings retention and
canonical main-document URL comparisons. Debug/test APK assembly and Play
Debug/Release lint passed (zero errors and 97 warnings each). The final input
placement follow-up was checked again with assembly and both lint tasks.

On dedicated API-36 `emulator-5588`, the final 13-case UI run passed:
CommandBarLayoutUiTest (2), CommandBarDropdownUiTest (4),
CommandBarSettingsUiTest (2), CommandBarMotionUiTest (2),
CommandBarReducedMotionUiTest (1), CommandBarInputUiTest (1) and the TLS/image/404
case from PageErrorUiTest (1). The layout cases use 411 dp/light/bottom and
1000 dp/dark/Split, covering touch and Tab/Enter return actions, current text
selection at activation, candidate selection, compact spacing, persistent
choice/dismissal, empty suggestions and retained live WebViews/Japanese drafts.
Native InputConnection Japanese, Chinese and Korean cases each passed in both
ANCHORED and CENTERED modes (six further executions). Screenshots of both
layouts and the pinned actions were visually inspected.

The initial layout checks found that moving the field between separate
composable call sites collapsed the selection on a phone bottom-to-center
switch. A stable two-child Layout now changes placement without recreating the
field; both cases passed afterward. The tablet assertion was also corrected to
compare selection at button activation, after ordinary Tab focus movement.
The first bottom-motion case ran in a wide window (where bottom bars are
inactive); the final run used phone width and passed both directions and reduced
motion. No case was skipped in the final selected runs.

An explicitly opted-in live Nagi-engine check first reproduced the AFPBB bare
HTTPS timeout while a direct www control rendered the site. Accepting WebView's
canonical root slash in the current-document check fixed the skipped recovery;
both mobile and desktop live cases then reached the www site with no page error
and an AFPBB title (two further executions). Certificate cancellation and normal
validation of the new www navigation remain enforced. See
[page-error recovery](PAGE_ERRORS.md#bare-https-host-recovery). There were 21
successful final instrumentation executions in total. These selected emulator
and time-sensitive live results are not a full instrumentation suite or a
physical-device claim.

The local parallel-install debug APK `nagi-test-20261124-r7.apk` uses
`com.takeruf.nagi.preview` / Nagi Test, with unchanged 1.4.1/code 22. Its APK
metadata and v2 Android Debug signature were verified. An in-place QA install
preserved main/preview UIDs, and a cold launch reached the foreground.
SHA-256: `bcd46f67910c89e9c455f73b28cb8162a8d585c4606d0a5644e304a982397c19`.
Logs, inspected screenshots and compact evidence are retained in ignored
`artifacts/command-layout-20261124/`. No release upload, tag or publication was
performed.

## Binary design and compact switches (2026-11-24, local)

Command Bar now exposes Use the new design as a switch (on = anchored,
off = centered). Appearance exposes Compact view as a switch showing the
effective spacing. Its untouched AUTO default still follows physical keyboards;
a manual toggle saves explicit ON/OFF. Existing saved layout/spacing values,
new-design default and migration-tip dismissal remain intact. No preference
keys, defaults or persistence mappings were changed for this UI follow-up.
The obsolete popup choices were removed, and the new title has six-language
resources.

Debug/test APK assembly, Play Debug lint (zero errors, 97 warnings) and
`git diff --check` passed. On dedicated API-36 emulator-5588, the existing two
CommandBarLayoutUiTest and two CompactUiSettingUiTest cases all passed at
411 dp/light and 1000 dp/dark, including Split. They use the real switches and
check checked states, initial keyboard-following behavior, persistent manual
overrides, Settings re-entry, saved layout, tip dismissal, 236 dp category width,
and retained live WebViews/Japanese drafts. Phone/tablet Settings screenshots
were visually inspected. No new tests were added. JVM, Release lint, the full
instrumentation suite, live-provider checks and physical devices were not rerun
for this display/control-only change.

The parallel debug APK is `nagi-test-20261124-r8.apk`, com.takeruf.nagi.preview /
Nagi Test, unchanged 1.4.1/code 22. APK metadata/v2 Android Debug signature,
in-place coexistence with preserved package UIDs, and cold foreground launch
were verified. SHA-256:
`bfb3179ce10da8834f39e94f0da0d1a8c02ad6ec182d408bab53fb8d483db8ae`.
Evidence is in ignored `artifacts/design-toggle-20261124/`. No release action
was performed.

## Adaptive settings, Space history, phone search, media and pane drops (2026-11-24, local)

Theme and Compact interface use current-value/chevron menus with three choices
and a checked current option. Compact keeps AUTO as its default and offers AUTO
again after explicit OFF/ON overrides. Use the new design remains an on/off
switch. The collapsed sidebar shows the current Space with a picker for the
others, at 52dp with compact spacing or 72dp otherwise. Horizontal Space swipes
retain the phone tab sheet; explicit selection, Back and close still dismiss it.

History recording, display, removal and Command Bar candidates are scoped to
the originating/selected Space. Room v4 preserves orbit.db, IDs and timestamps;
older shared visits stay once in Personal or the first existing Space, without
being copied to every Space. Favorites remain shared. See [HISTORY_SPACES.md](HISTORY_SPACES.md).

On phones the new search design fills the safe viewport above the IME and moves
the touched field to the top over 200ms, reversing on dismissal. Keyboard
shortcut entry and zero-duration animation are immediate. The resting bottom
address-bar preference, old centered design, wide Split dropdown, editor
text/selection and retained background WebViews remain supported.

Completed system PiP returns the resumed video presentation to fullscreen.
Native holders declare their current surface owner and keep one child, avoiding
restoration of a video over another live tab. The regressions distinguish
completed entry, canceled entry, playback stop and finishing tasks.

Collapsed wide-sidebar tab icons support touch-hold and primary-mouse dragging
for insertion, combining standalone tabs and page Split creation/replacement.
Saved pairs move together; cancellation and a resize below 600dp clear the drag.
Favorites dropped into an existing pane open there and focus that side while
retaining its partner's live WebView and draft. On a single page, the central
60% opens in the current tab; each 20% edge atomically creates, inserts, pairs
and selects a new tab. The shortcut's identity, editable fields and ordering
remain unchanged; ordinary favicon caching is allowed. See [FAVORITES.md](FAVORITES.md).

Play Debug's complete JVM suite passed 280 tests without failures, errors or
skips, including four Favorite pane transaction cases, all 22 video-policy
executions on API 26/35, and three v3 history migration cases. Debug/test APK
assembly and Play Debug/Release lint passed; each lint report has zero errors
and 97 warnings. The final test-only mouse-cancellation update rebuilt both
Debug APKs. Six-locale resource checks and git diff --check passed.

Staged selected UI runs on dedicated API-36 emulator-5588 (WebView 133) cover 62
distinct cases; each case's final execution passed. They include VideoUiTest
(14), PageVideoCompatibilityTest (4), phone/wide Command Bar dropdown (5), motion
(3), reduced motion (1), layout (2), keyboard layout (2), CJK input (4), Settings
navigation (3), Appearance (2), Compact UI settings (2), localization (6), Spaces
(3), scoped history (2), private lifetime (1), passkey dispatch (4), collapsed
sidebar drag (2) and Favorite pane drag (2). Coverage includes 411dp/light and
1000dp/dark, Split, physical-keyboard policy, IME composition, retained live
WebViews/Japanese drafts and both touch/mouse cancellation. Phone search,
menus, collapsed rail, pane drops and PiP screenshots were visually inspected.
These were staged runs as changes landed, not 62 executions against one final
APK or a full instrumentation suite. The final four drag cases all passed
against the final production source.

Initial harness failures were corrected without relaxing product invariants:
optional schema indices, editable text versus placeholders, obsolete phone
Split grouping, width prerequisites, asynchronous sheet dismissal, visual-
viewport geometry, and selection of the resumed non-finishing Activity.
The engine-name CJK test now opens the phone tab sheet to reach Settings.
Compose ui-test 1.10.1 routes mouse CANCEL differently from DOWN/MOVE/UP; the
shared test helper clears its button state and sends native cancellation to
the original Compose touch stream. Favorite comparisons allow only normal
favicon enrichment; editable fields/order remain strict.

The passkey probe confirms native credential-provider dispatch and the exact
origin tuple, including Chromium's optional root slash. Its synthetic provider
is debug-only and enabled on API 34+. Both provider settings were restored and
read back. Actual external-provider selection UI, QR and account authentication
remain unverified/unresolved. No production approval or trust gate was bypassed.
See [PASSKEYS.md](PASSKEYS.md).

The parallel-install debug APK is `nagi-test-20261124-r10.apk`,
`com.takeruf.nagi.preview` / Nagi Test, unchanged 1.4.1/code 22. APK metadata,
v2 Android Debug signature, in-place coexistence with unchanged package UIDs
and a cold foreground launch were verified.
SHA-256: `5a79b572a0e4604601bead974fd8a2b74607726a7c19a8090b7cba5062f62871`.
Evidence, inspected screenshots, source/artifact metadata and the per-case
staged UI result index are retained in ignored `artifacts/collapsed-drag-20261124/`,
with earlier stage logs in `artifacts/choice-menus-20261124/`,
`artifacts/phone-search-20261124/` and `artifacts/passkeys-20261124/`.
Source is the dirty working tree at b1685db. Physical devices, arbitrary live
sites/providers and release availability are separate and were not established.
No tag, upload, rollout or publication occurred.

## Favorite drag preview and source visibility follow-up (2026-11-24, local)

The Favorite-only edge/center/edge targets added in r10 produced a narrow
column-shaped Split preview. Favorite drags now use the same equal left/right
halves as tab drags on a single wide page; either half creates and focuses a
new adjacent tab. An existing Split still opens the Favorite in the actual
destination pane and retains its partner. The original Favorite tile stays
painted during dragging and landing, while a separate preview follows the
pointer. Bookmark identity, editable fields and order remain unchanged.
This supersedes the central-60%/edge-20% behavior recorded above for r10.

Focused Play Debug unit tests passed 12 cases (FavoritePaneDropTest 4,
TabSplitDragStateTest 5, FavoriteDragStateTest 3). Debug/test APK assembly and
Play Debug lint passed with zero errors and 97 warnings. The final test APK
was rebuilt after adding the touch half-width regression.

On dedicated API-36 emulator-5588, all seven selected UI cases passed:
FavoritePaneDropUiTest (3), CollapsedSidebarDragUiTest (2), and the existing
SidebarDragTest Favorite mouse-reorder and touch-landing cases (2). Tests cover
both equal-half destinations with touch/light and mouse/dark, absence of the
third region, rendered source-tile pixels while held, cancellation, saved
shortcut fields/order, unfocused existing Split panes, retained partner
WebViews/Japanese drafts and the 599dp boundary. Left/light and right/dark held-
drag screenshots were visually inspected. Full unit/instrumentation suites,
Release lint, actual sites and physical devices were not rerun for this focused
UI follow-up. git diff --check passed.

The parallel-install debug APK is `nagi-test-20261124-r11.apk`,
`com.takeruf.nagi.preview` / Nagi Test, unchanged 1.4.1/code 22. Metadata and its
v2 Android Debug signature were verified. Evidence, hashes, check logs and
inspected screenshots remain in ignored `artifacts/favorite-two-pane-20261124/`.
SHA-256: `4294b895f8e3cca20dddce7ef3c3c7d63302618b9e36d35e03701dc84328ccc9`.
In-place QA installation preserved all package UIDs, and a cold launch reached
the preview foreground Activity. Source remains the dirty working tree at b1685db; no release action occurred.

## Compact Chrome-style page menu (2026-11-24, local)

The compact page menu uses a right-aligned popup overlapping the actual bottom
toolbar by 12dp, 72% of the app-window width up to 280dp, with an 8dp right gap and 24dp
corners. Its scrolling area respects system bars and the toolbar, with a 640dp
cap. Back, Forward, Pin/Unpin and Reload stay fixed in a four-icon row at the
bottom. Each button stays square at the shared menu row height (48dp normally,
36dp compact), without horizontal stretching. The list above can scroll without
moving or hiding these four controls. New tab, History and Settings remain
ordinary text rows. There is no separate navigation footer, dimmed sheet,
handle or extra Close button. The visible bottom trigger, outside taps and
Android Back dismiss the popup. Home and direct tab Close controls remain.

JDK-17 side-by-side Debug/test APK assembly and Play Debug/Release lint passed
(zero errors; 98/97 warnings). The fixed-footer revision passed six selected UI cases on
dedicated API-36 emulator-5588 / WebView 133.0.6943.137, with no failures or skips:

- `phoneMenuKeepsChromeWidthAndFourFixedBottomActions`: 320dp/top/light,
  411dp/bottom/dark and 360x540dp short-window proportions; all four buttons are
  48dp square, at the bottom and stationary/visible after scrolling to Settings.
- `phonePopupClosesFromTriggerOutsideTapAndBack`: WindowManager taps on the
  bottom trigger and outside the popup, plus Android Back, with one fixture
  request and unchanged tab selection/count and live drafts.
- `phoneHomeAndMenuKeepTabIdentityAndSimplifyTabRows`: Pin/Unpin, Settings,
  History, Home and the direct tab controls through the popup.
- `bottomChromeToolbarUsesOneRowAndKeepsNavigationAndPageActions`: narrow
  toolbar bounds, actual Back/Forward through the restored footer and tab changes.
- `ReloadButtonUiTest#compactBottomMenuReloadKeepsPendingPageAndSelectedTab`:
  pending and committed native reload through the fixed Reload button.
- `savedSplitUsesOneSelectedPageInCompactAndRestoresBothLivePagesWhenWide`:
  compact/wide boundaries, themes, saved pairs and the same live drafts.

The earlier popup work corrected trigger callbacks to toggle instead of always
opening. Its dismissal tests were also corrected to use WindowManager taps and
wait for popup focus before sending Back. Those corrections remain in the final
passing tests. Evidence for that revision remains under ignored
`artifacts/chrome-popup-footer-20261124/`.

After adding the 12dp toolbar overlap, the first four-case run exposed an
incorrect whole-toolbar overlap in the top-address layout: clipped bounds of
the zero-sized popup host lost its origin. This also let a native trigger tap
hit Reload. Preserving the host origin and translating the actual toolbar
bounds into popup coordinates corrected both failures. The final build and
Debug/Release lint passed again with the same warning counts. Four selected
cases were rerun and all passed without failures or skips: menu geometry/fixed
actions, native dismissal, Home/tab identity, and saved Split restoration.
The geometry test measures screen coordinates across both popup and Activity
windows, asserting 12dp overlap for 320dp/top/light, 411dp/bottom/dark and
360x540dp/bottom/dark. Light/dark and scrolled-footer screenshots were visually
inspected. `git diff --check` passed. Final source/APK hashes, logs and screenshots
are in ignored `artifacts/chrome-popup-overlap-20261124/`; the initial failing
logs remain there as well. The separate Back/Forward and pending-reload cases
listed above were not rerun after the placement-only correction.

Unit tests, the full instrumentation suite, physical devices and live providers
were not rerun for this UI update. No version or release action changed.

## Compact five-action footer follow-up (2026-11-24, local)

The fixed row now contains Back, Forward, Copy link, Share and Reload. It has
no Pin button or duplicate Copy/Share text rows. The five buttons stay square
and shrink equally in narrow popups, retaining the existing width and 12dp
toolbar overlap. The first geometry run found rounding squeezed the last
button by two pixels at 320dp. Dividing the available whole pixels before
converting to dp corrected the actual layout; the assertion was unchanged.

JDK-17 side-by-side Debug/test APK assembly and Play Debug/Release lint passed
(zero errors; 98/97 warnings). All eight selected UI cases passed on the final
APK on dedicated API-36 emulator-5588: five CompactBrowserUiTest cases covering
five-action geometry/scrolling, Home/tab identity, native dismissal, toolbar
navigation and saved Split restoration; the pending-reload case; and both
AddressActionsUiTest cases covering wide/narrow pane actions and Split copying.
The geometry case covers 320dp/top/light, 411dp/bottom/dark and 360x540dp/bottom/dark.
Screenshots of these layouts and the scrolled fixed row were visually inspected.

The Home/menu case checks actual clipboard contents and captures the Android
share chooser intent, including the URL, title and MIME type, while intercepting
its launch. It verifies disabled Copy/Share controls on the new-tab home. It
does not establish interaction with the external system chooser or a recipient.
Unit/full instrumentation suites, physical devices and live sites were not run.
No version or release action changed. `git diff --check` passed. Final results,
source/APK hashes, screenshots and the initial failing run remain in ignored
`artifacts/chrome-popup-five-actions-20261124/`.

## Wide sidebar tab actions (2026-11-25, local)

Wide sidebar tab rows omit the separate three-dot button, matching the compact
tab sheet. Direct Close controls keep their previous sizing and visibility;
secondary-click menus, accessibility actions and drag destinations remain.
JDK-17 side-by-side Debug/test APK assembly and Play Debug/Release lint passed
(zero errors; 98/97 warnings). Four selected UI cases passed on dedicated
API-36 emulator-5588: SidebarDragTest hover/secondary-click, Split grouping,
row reorder/pin/cancel, and CompactBrowserUiTest Home/menu/tab identity.
Light/dark hovered-row and narrow-sidebar Split screenshots were inspected.

The first Split case timed out at an obsolete width check below 450 pixels.
The configured 220dp sidebar is 577.5 pixels at the QA device's density of
2.625. The test now asserts 220dp converted at the actual density, within one
pixel, and passed on rerun. The other three cases passed initially. The test
APK and Debug lint were rerun after this test-only correction. Unit/full
instrumentation suites and physical devices were not run. No version or release
action changed. `git diff --check` passed. Logs, results, hashes and screenshots
remain in ignored `artifacts/sidebar-without-ellipsis-20261125/`.


## Download cancellation cleanup (2026-11-26)

The native HTTP result distinguishes acceptance, explicit cancellation and
failure. Cancelling or dismissing the confirmation closes only a page-opened
tab that has never committed a document. Transactional close returns to its live
opener and saved Split pair; a newer selection, manually created tab, existing
document and failed enqueue remain protected. Generated exports keep their
source document.

Code-25 validation passed 299 unit tests and Debug/Release lint (zero errors,
97 warnings each). The 17-case WebView run passed 16; the generated-download
completion notification timed out during the interrupted combined run and
passed its isolated rerun. The preceding 31-case run passed 30, with the HTTP
case failing on multiple DownloadManager rows; fixture isolation now ignores
pre-existing IDs. Four focused cancellation cases also passed at phone width
(1080x2400 / 420 dpi). See [release verification](RELEASE_1.6.1_VERIFICATION.md) for
phone checks, signed artifacts and publication status. These are selected
emulator checks, not the full instrumentation suite or physical-device proof.
