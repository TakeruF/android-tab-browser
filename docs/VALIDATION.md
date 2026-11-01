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

Playing videos automatically pop out when their source tab is hidden by a switch to another tab or Space. Showing the source again (including in either Split pane) returns the same video to its page without pausing or reloading. Going Home automatically enters Android system PiP for inline, fullscreen, or pop-out video, including when only one tab is open. Inline Home/PiP restores the page when the app returns. Paused or ended videos do not start an automatic presentation. Settings independently persist automatic tab pop-out and Home/PiP, both enabled by default; no manual pop-out button is injected into websites.

In-app pop-outs initially show only the video. A tap overlays Lucide controls (return at top left, close at top right, play/pause at center, playback time at bottom left, fullscreen at bottom right) without changing the window dimensions or passing the tap to the site. Controls hide after three seconds of inactivity, respecting the system's recommended accessibility timeout. Drag anywhere to move the window and pinch to resize it. Mouse users can also drag any corner to resize with the opposite corner anchored and the video's aspect ratio preserved; corners show diagonal resize cursors and only intercept primary mouse input. Instrumentation advances the Compose clock to verify the timeout, separately from the live WebView playback clock.

`PageVideoCompatibilityTest` checks YouTube-shaped players in WebView: unstarted channel trailers receive the matching video's thumbnail, SPA changes discard stale helper-supplied posters, site-supplied posters are preserved, and inline video presentation restores the page's original styles. Nagi injects no manual video button over YouTube miniplayers. The compatibility helper does not start playback or change YouTube's own miniplayer behavior.

Video presentation leaves WebView page zoom untouched; it does not normalize or restore zoom through asynchronous scale callbacks. A desktop-width regression sets a user zoom, switches tabs three times, and checks the original zoom, playback, and absence of reloads on every return.

Video surfaces fit the visual viewport's dimensions and offsets rather than assuming the visible origin remains at layout position zero. A zoomed WebView regression repeatedly shrinks and enlarges its native bounds, checking that all four video edges track the visible viewport and playback continues.

Physical devices/fold transitions, manufacturer IMEs and trackpad drivers, real camera/microphone/location hardware, Android 8–15 runtime behavior, mainland-China networking, general production video compatibility, DRM, and embedded players in closed shadow roots remain unverified. Retaining a live WebView does not establish DOM/form recovery after process death. Updater Robolectric coverage on API 26 and 35 is separate from device proof.

WebView keeps its ordinary white canvas when the app theme changes. A pixel regression checks a transparent page after a live dark-theme switch, then confirms that an explicitly authored dark background survives both app themes.

Website color preference follows the effective app theme (System, Light, or Dark). Each engine uses an isolated native theme context; changes dispatch WebView configuration updates without navigation, JavaScript media-query shims, or forced color inversion. `WebViewEngineTest#websiteColorSchemeChangesWithoutReloadingOrLosingPageState` checks initial dark preference, live light/dark matchMedia events, actual CSS media-query colors, preserved JavaScript state, and a single document request.

Password autofill is disabled in the production configuration pending approval. Regression checks assert that native WebView autofill is excluded, direct manual requests return awaiting-approval status, the entire Settings section and page-menu action are absent, and no awaiting-approval notice is shown in the normal UI. Retained synthetic-provider tests explicitly opt in to the implementation and are not evidence that the production feature is available.

## Local artifact retention

Generated output belongs in Git-ignored `artifacts/` or `app/build/`. Keep the latest and previous signed release APK/AAB, checksums/update metadata, and compact final verification evidence. Remove superseded Debug APKs, repeated downloads, build intermediates, raw logcat/build logs, interrupted runs, and obsolete screenshots after their useful results are recorded.

The 2026-10-11 cleanup removed older local runs and chronological development reports. Source tests, reusable scripts, branding/store assets, README images, and release notes remain. Earlier tracked records can be recovered from Git history; ignored raw outputs are not retained.
