# Trackpad page history gestures

Swipe two fingers right on the trackpad to go back, or left to go forward. In Split, the gesture operates on the pane under the pointer. Reaching the beginning/end of history keeps the tab open.

Touchscreen input is passed to the native page for scrolling, pinch zoom, and website interactions. It never triggers a history swipe, including a long horizontal two-finger gesture.

Trackpad wheel bursts use horizontal/vertical scroll axes, with a 250ms gap separating bursts. A burst navigates at most once, including momentum. Page and nested horizontal scrollers under the pointer take priority, and mouse wheels retain normal scrolling. Source-touchpad events retain the existing exact-source-mouse normalization for Compose hit testing; a scoped dispatch marker preserves their origin without changing the normalized event source. Mouse-source trackpads can also be recognized by device capabilities or finger tool type.

Android 14+ classified two-finger touchpad scrolls use relative pixel scroll-distance axes, including historical samples. They commit on release, honor cancellation, and check the horizontal scroller under the pointer. See [MotionEvent](https://developer.android.com/reference/android/view/MotionEvent) and Android's [GestureConverter](https://android.googlesource.com/platform/frameworks/native/+/master/services/inputflinger/reader/mapper/gestures/GestureConverter.cpp), which supplies scroll-distance axes for two-finger scrolling.

## Validation, 2026-10-10

An isolated working-tree snapshot avoids concurrent edits/builds affecting generated classes. The six gesture-related source/test files were compared with the workspace after the build and matched exactly. JDK 17 / SDK 36: GitHub Debug app/test APKs, all 145 unit tests, and Lint passed (0 errors). Existing unrelated Lint warnings remain.

`HistorySwipeUiTest` exercises actual WebView history through window event dispatch: trackpad back/forward, one step per gesture/burst, touchscreen exclusion, cancellation, Android classified trackpad input, nested/page scrolling priority, Split targeting/focus, and history boundaries. All 7 cases and all 4 existing `BrowserScrollTest` regressions passed (11 total). Runtime evidence uses the API-36 `Nagi_History_QA` tablet emulator, 2560×1600 at 320dpi. Physical trackpad hardware and manufacturer drivers remain unverified. No release publication is included.

Final evidence: `app/build/reports/history-swipe-validation/trackpad-only/final-instrumentation.txt`, `build-unit-lint.txt`, and `installed-apk.txt`. The installed APK matched `artifacts/history-swipe/nagi-two-finger-swipe-debug.apk` by SHA-256.
