# Validation

The [1.1.1 Play preparation](RELEASE_1.1.1_PLAY_PREPARATION.md) separates final Play validation from the [initial preflight](RELEASE_1.1.1_VERIFICATION.md) and saved Console state. The dated [1.1.0 release verification](RELEASE_1.1.0_VERIFICATION.md) records comprehensive tests, clean artifact checks and publication evidence. The [1.0.0 publication verification](RELEASE_1.0.0_VERIFICATION.md) and [0.3.0 verification](RELEASE_0.3.0_VERIFICATION.md) remain historical distribution records. These are recorded results, not a live Console or deployment status check.

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

- [Command Bar settings](COMMAND_BAR.md): category/command visibility, saved choices, shortcut hints, empty-list navigation and retained live pages.
- [Narrow-window browsing](COMPACT_UI.md): window boundaries, tab sheets, phone UA/Client Hints, per-site choices, and page/draft retention through resizing.
- [Privacy, Reader, and blocking](PRIVACY_READER_BLOCKING.md): actual WebView requests, exceptions, storage isolation, Reader sanitization, and restoration of blocked-ad containers.
- [Page translation](PAGE_TRANSLATION.md): page-menu/language controls, live DOM translation and restoration, session cancellation, and an opt-in real-model download/translation smoke test.
- [Live-site advertising checks](AD_SLOT_COLLAPSE_VALIDATION.md): recorded DOM measurements, content preservation, and remaining detection gaps.
- [Trackpad history](HISTORY_GESTURES.md): WebView history, gesture dispatch, nested scrolling, Split targeting, and touchscreen exclusion.
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
