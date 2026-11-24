# Video presentation and surface ownership

User-started playing videos can pop out inside Nagi when another tab or Space
hides their source. Muted autoplay does not authorize this. Returning to the
source, including either visible Split pane, restores the same WebView/video
without reloading or changing page zoom. Home/PiP and automatic tab pop-out
remain independently configurable, enabled by default. Ordinary inline playback
does not enable system PiP.

An existing fullscreen or pop-out can continue into Android system PiP. Expanding
back into the resumed app shows that same presentation at fullscreen size,
including a video that was previously an in-app pop-out. It does not select a
different tab or create another player. Restoration requires a completed system
PiP entry and an actual host resume; an aborted entry, a closed presentation,
host stop or task finish cannot promote a pending pop-out to fullscreen.
`MainActivity.onPause` only marks the host as paused. Playback is paused by
`onStop`, and stopping/dismissing a system PiP closes its presentation. This
follows Android's [PiP lifecycle guidance](https://developer.android.com/develop/ui/views/picture-in-picture).

## Native surface ownership

`VideoPresentationController` owns the active presentation, while
`BrowserSessionController` and `EnginePool` retain its tab's live engine. A
presentation may temporarily reparent the source WebView into its overlay.
Each `NativeSurface` holder records its currently bound surface and has at most
one native child. Compose can reuse that holder for a different tab, so the
original parent reference alone is insufficient to restore a video.

`PageVideo.restoreInline` restores the page's DOM styles and attaches the
WebView only to an attached holder that still declares ownership of that exact
surface. Returning to a hidden source never inserts its WebView over the
currently displayed tab. A later source-tab selection attaches the retained
surface through the normal renderer. Binding/releasing a holder removes stale
source registrations; returning a presented surface clears the preceding native
child before attaching it. The native boundary also repairs a reused holder
with extra children even if the expected child was already attached.

## Regression coverage

- `VideoPresentationTest` covers API 26/35 policy, source visibility, independent
  settings, autoplay exclusion and reentrant close. Additional cases cover
  resumed fullscreen restoration after actual PiP, canceled entry, dismissal
  before resume and a finishing task.
- `VideoUiTest#popupSystemPipReturnExpandsTheSamePlayingVideoToFullscreen`
  uses a real video and a different live WebView, enters actual system PiP,
  returns the task, and checks fullscreen playback with no source reload.
- `VideoUiTest#repeatedPopupReturnsNeverAttachTheVideoOverAnotherLiveTab`
  repeatedly alternates the video and another loaded tab, verifying both native
  holders contain only their own WebView and that playback continues.
- The remaining video UI cases cover native fullscreen/PiP, cross-origin frame
  playback, source closure, mouse/touch resize, pause/return controls, page zoom
  and both disabled settings. `PageVideoCompatibilityTest` checks restoration
  of site-authored inline styles.

These fixtures do not prove every site's media/DRM behavior or physical-device
PiP implementation. Actual executed checks belong in [validation](VALIDATION.md).
