# Cross-feature audit and missing functionality — 2026-10-06

Historical audit target: checkout `989538a` plus the uncommitted UI changes present at the start. Environment: JDK 17.0.20.1, API 36 / Android 16, `Orbit_Tablet_QA` (`emulator-5554`), WebView 133.0.6943.137. This audit added/updated tests and records without changing product code. The subsequent fixes and successful 0.1.1 validation are recorded in [VALIDATION.md](VALIDATION.md).

## Bugs reproduced by regression tests

### B1 / High: right-tab keyboard selection disagrees with the action target

Reproduce: display two tabs in Split, press `Ctrl Tab` or the right tab's `Ctrl 1–9` shortcut, then `Ctrl W`.

Expected: close the selected right tab and retain the left. The audited implementation selected the right tab while assigning `rightFocused = false`, leaving `focusedTab` on the left. Close, find, reload, back, and URL input could affect the wrong page. Command Bar `SelectTab` followed the same path.

Locations: `ui/NagiApp.kt`, `focusedTab`, `Shortcut.NEXT_TAB`/`PREVIOUS_TAB`, numbered shortcuts, and `SuggestionAction.SelectTab`.

Tests: `CrossFeatureUiTest#nextTabSelectionMakesCloseShortcutCloseRightTab` and `#numberedRightTabSelectionMakesCloseShortcutCloseRightTab`.

### B2 / Medium: Archive removes a visible right-hand Split page

Reproduce: display a right-hand tab whose last-access timestamp is older than the archive threshold, then run Archive.

Expected: retain both visible pages. `BrowserDao.archiveInactive` protected only `spaces.activeTabId`, not the right Split tab. Assigning `archivedAt` removed the tab from `visibleTabs` and session retention, losing the right pane/live WebView.

The test injects an old timestamp. It does not directly inspect lost form values or scroll position.

Locations: `data/repository/TabRepository.kt#archiveNow`, `data/room/BrowserDao.kt#archiveInactive`, and `retainTabIds` in `ui/NagiApp.kt`.

Test: `CrossFeatureUiTest#archiveDoesNotHideEitherCurrentlyDisplayedSplitPage`.

### B3 / Medium: an accepted uppercase HTTPS template cannot navigate

Reproduce: save `HTTPS://example.com/?q={query}` as a custom search template and search.

`InputResolver.validateEngine` accepted schemes case-insensitively, but `search` did not normalize the template and `WebViewBrowserEngine.loadUrl` accepted lowercase `https://`/`http://` only. It returned without loading or displaying an error.

Test: `WebViewEngineTest#validatedUppercaseSearchTemplateCanNavigate`. This checks the accepted-URL navigation contract, not successful remote network delivery.

### B4 / Medium: extension-based upload filters are discarded

Reproduce: request file selection with accept `.pdf`.

`NativeBrowserHost.chooseFiles` kept only values containing `/`, discarding `.pdf` and setting the Intent type to `*/*`. The picker showed every file instead of filtering PDFs. Explicit multiple MIME types and multiple selection have separate coverage.

Test: `FilePickerContractTest#pdfExtensionAcceptRestrictsPickerToPdfFiles`, inspecting the real requested picker Intent with Robolectric. Physical-device picker rendering was not tested.

### Follow-up: ChatGPT handoff leaves the stored URL at about:blank

`ChatGptBrowserHandoffUiTest` failed in the initial suite, the updated suite, and a standalone rerun. Even after waiting for the intended new tab before clicking “Ask ChatGPT,” Room still held `about:blank` after 10 seconds. Suggestion generation, selection, and callback component tests passed.

Standalone diagnostics showed WebView `url=https://chatgpt.com/?q=Nagi+browser+test`, `originalUrl=about:blank`, and `progress=10`. The query URL reached WebView, but loading stalled and Room was not updated. This did not establish a handoff-callback failure. Connection/site/engine behavior and saving the requested URL before page-start remained follow-ups. Cloudflare, login, or networking was not established as the cause, and ChatGPT query acceptance was not proved.

## Missing functionality found in code and current navigation

- **Ordinary Bookmarks:** `LibraryScreen(history = false)` and persistence existed, but NavHost exposed only `browser`, `settings`, and `history`. Sidebar/Command Bar had no Bookmarks entry, and the page-save action was missing. Existing Bookmarks could not be opened through the UI, while Favorites remained available. README promises disagreed with the interface; either restore access or document retirement and existing-data access.
- **Cookie/site-data clearing:** Settings cleared browsing history only. Users could not reset site logins, cookies, or localStorage globally or per site. This impeded login-loop troubleshooting and local logout. History clearing did not claim to clear site data, so its existing action was not itself defective.
- **Link/image context actions:** no WebView hit-test menu offered per-link new-tab, URL-copy, or image-save actions. `target=_blank` and the global open-links-in-new-tabs setting are different features.

Private Browsing, Sync, Reader, Content Blocking, blob/data downloads, camera capture, and folder picking were already documented as unsupported and were not counted as new regressions.

Another documentation mismatch: README claimed a three-session pool, while EnginePool retained every opened live session and tests checked retention of 25 tabs. The retention/memory policy needed clarification; this audit did not reproduce OOM.

## Repairs to existing tests

The initial instrumentation run passed 70 of 82 tests and failed 12. Failures were not all treated as product bugs.

- Engine editing/CJK names: navigate through “Customize search engines”; select the custom engine as common for keyword tests.
- Four locales: check the current Dark selector rather than Theme color, which moved out of the old Settings location.
- Favicons: use the tab's Pin accessibility action instead of the removed page-menu item.
- New-tab home icon: wait for the intended new tab and Omnibox, dismiss it, then inspect that tab rather than an identically named hidden icon.
- Space animation: scroll the target Space into view before freezing the animation clock; other tests had left QA Spaces outside the horizontal viewport. Standalone rerun passed.
- ChatGPT handoff: wait for the new target tab instead of acting on a Settings button also present in an older Space.
- CJK search: retain real InputConnection composition coverage, but use a device-local HTTP search fixture so remote redirects/CAPTCHAs do not alter stored-URL assertions. Separate tests retain HTTPS-registration validation.

## Historical results and reports

All 85 original unit tests passed. Adding the picker contracts produced 87 unit tests: 86 passed, one failed (B4). Debug, unsigned Release, and AndroidTest APKs built. Lint had 0 errors/33 warnings; `git diff --check` passed.

The expanded instrumentation run had **80 passed, six failed, zero skipped out of 86**. Failures: two B1 cases, B2, B3, ChatGPT handoff, and Space animation. After repairing the Space navigation, standalone runs passed Space animation and still failed ChatGPT. This stage did not establish a fully passing updated suite.

Artifacts are local under `artifacts/cross-feature-audit-2026-10-06/`: initial suite in `baseline-results`/`baseline-report`; expanded suite in `expanded-results`/`expanded-report`; reruns in `targeted-results`/`targeted-report`; unit results in `unit-results`/`unit-report`; ChatGPT diagnostics in `handoff-results`/`handoff-report`.

The local diagnostic screenshot `handoff-audit.png` showed the query URL in the address bar, a blank body with progress still visible, and the tab title “New tab.”

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest
ANDROID_SERIAL=emulator-5554 JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:connectedDebugAndroidTest
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:lintDebug
```

Regression tests deliberately failed against the audited pre-fix code. Physical devices, API 26–35 device behavior, manufacturer IMEs, real trackpads/camera/microphone/location, mainland-China networking, and ChatGPT query acceptance/answers were outside this audit. An API-26 emulator image was not installed.
