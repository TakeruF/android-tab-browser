# Page history gestures

## Android edge-back preview

On Android versions that deliver predictive-back progress, dragging inward from
either system back edge reveals the preceding page beneath the current page.
The page follows gesture progress without resizing the browser chrome. In Split,
only the focused pane moves. Releasing to cancel settles back to the original
page; committing settles to the preceding page, then traverses native history.
Back buttons and keyboard Back continue to navigate immediately. Reader exit,
tab closure and Settings navigation retain their existing back behavior.
Native navigation availability gates the preview: raw history entries that
WebView cannot traverse do not cause a misleading history preview during tab
closure. The action chosen when a gesture starts stays fixed through settling.

`PredictiveBackHandler` owns the gesture in the composition; no Views or images
are stored in the ViewModel. The engine captures recent departing viewports in
memory, with three images of at most 0.5 megapixels each per live engine. History
index and URL identify each image, including repeated URLs. New navigations
discard forward-branch images. Images are not persisted or added to saved engine
state, and engine destruction clears its cache, including private engines.
The original WebView remains live throughout a cancelled gesture. After commit,
the image covers it until native visual commit, with a 1.5-second fallback for
failed/provider-incomplete loads. A real history entry pointing to `about:blank`
can return to the new-tab UI; stale blank callbacks during HTTP loading remain
ignored.

Restored, evicted or differently shaped viewports may lack an image. Those
entries reveal a neutral surface with the destination URL (or Nagi's new-tab
surface for `about:blank`); they are not loaded speculatively. The direct drag is
not eased. Completion/cancellation uses a 180ms ease-out translation and follows
Android's animation duration scale. A newly started gesture supersedes a settling
gesture. See [Android predictive-back progress](https://developer.android.com/develop/ui/compose/system/predictive-back-progress)
and [WebView visual commit](https://developer.android.com/reference/android/webkit/WebViewClient#onPageCommitVisible(android.webkit.WebView,%20java.lang.String)).

## Trackpad navigation

Swipe two fingers right on the trackpad to go back, or left to go forward. In Split, the gesture operates on the pane under the pointer. Reaching the beginning/end of history keeps the tab open.

Touchscreen input within the page is passed to the native page for scrolling,
pinch zoom, and website interactions. It never triggers a trackpad history swipe,
including a long horizontal two-finger gesture. Android system edge-back remains
separate from page input.

Trackpad wheel bursts use horizontal/vertical scroll axes, with a 250ms gap separating bursts. A burst navigates at most once, including momentum. Page and nested horizontal scrollers under the pointer take priority, and mouse wheels retain normal scrolling. Source-touchpad events retain the existing exact-source-mouse normalization for Compose hit testing; a scoped dispatch marker preserves their origin without changing the normalized event source. Mouse-source trackpads can also be recognized by device capabilities or finger tool type.

Android 14+ classified two-finger touchpad scrolls use relative pixel scroll-distance axes, including historical samples. They commit on release, honor cancellation, and check the horizontal scroller under the pointer. See [MotionEvent](https://developer.android.com/reference/android/view/MotionEvent) and Android's [GestureConverter](https://android.googlesource.com/platform/frameworks/native/+/master/services/inputflinger/reader/mapper/gestures/GestureConverter.cpp), which supplies scroll-distance axes for two-finger scrolling.

## Preview validation, 2026-11-23

JDK 17 / SDK 36: Play Debug app/test APKs, all 251 JVM tests and Play Debug/Release
lint passed (zero errors, 97 warnings each). On the dedicated API-36 emulator,
all nine preview-engine, predictive-back and keyboard-density cases passed:
phone/light left-edge and tablet/dark right-edge Split pixel checks at different
progress values; cancellation, overlapping gestures and commit; unchanged live
documents/drafts on cancellation; scrolled viewport and forward images;
same-document history; explicit new-tab traversal; non-traversable script
entries; and Settings category width remaining 236 dp with a hardware keyboard.
The system-edge case injects real touchscreen events through Android's gesture
navigation, separately from dispatcher-driven progress tests.

Of 22 existing regressions, 21 passed: seven history gestures, three of four
scroll cases, six reload-engine, two reload-button and three Settings navigation
cases. `BrowserScrollTest.mouseWheelScrollsPageInBothAxesAndDirections` times out
on positive horizontal mouse scroll. Both the changed app and the preceding
side-by-side APK (SHA-256 `8bcf4af207762f264c5186f84849eaa46de91111c814004b312c666f80d3995a`)
receive two wheel events with identical metrics (`x=0`, `y=192`, viewport width
720, scroll width 4000). This pre-existing emulator/provider result remains
unresolved; no wheel-direction workaround was added. Temporary diagnostics were
removed after comparison. Logs and inspected screenshots are in ignored
`artifacts/history-preview-20261123/`. Physical devices/trackpads and other
Android/WebView versions remain separate checks; no release is included.

## Validation, 2026-10-10

An isolated working-tree snapshot avoids concurrent edits/builds affecting generated classes. The six gesture-related source/test files were compared with the workspace after the build and matched exactly. JDK 17 / SDK 36: GitHub Debug app/test APKs, all 145 unit tests, and Lint passed (0 errors). Existing unrelated Lint warnings remain.

`HistorySwipeUiTest` exercises actual WebView history through window event dispatch: trackpad back/forward, one step per gesture/burst, touchscreen exclusion, cancellation, Android classified trackpad input, nested/page scrolling priority, Split targeting/focus, and history boundaries. All 7 cases and all 4 existing `BrowserScrollTest` regressions passed (11 total). Runtime evidence uses the API-36 `Nagi_History_QA` tablet emulator, 2560×1600 at 320dpi. Physical trackpad hardware and manufacturer drivers remain unverified. No release publication is included.

The temporary Debug APK and raw feature logs were removed during local cleanup. The previous release's [publication verification](RELEASE_0.3.0_VERIFICATION.md) includes the combined gesture/scrolling regression coverage; test sources remain available for reruns.
