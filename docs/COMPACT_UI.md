# Narrow-window browsing

Nagi continues to prioritize tablet and unfolded-phone windows. Narrow windows
use full-width pages. With the default top-address layout, a 56dp bottom bar
provides Back, Forward, New tab, Search, and a tab-count button. The optional
bottom-address layout combines its controls into one row, described below.
The tab button opens the existing tabs, Favorites, and Spaces interface in a
modal bottom sheet; selecting a tab, Space, or favorite closes it. History and
Settings remain available in that sheet. Tab action/close buttons have 48dp
targets in the sheet.

The tab sheet wraps its visible content, so small tab collections keep the header
and actions near the bottom of the screen. It grows as tabs or expanded sections
need more room, up to 85% of the available sheet height; larger lists scroll.
With the software keyboard visible, compact navigation and the optional bottom
address bar are hidden instead of moving above it. They return when the keyboard
closes. The browser shell, including the wide sidebar footer and new-tab palette,
keeps its full height behind the keyboard. Page input resizes only the browser
page area (both panes in Split), while Command Bar input leaves the background
page at full height and applies keyboard spacing in its own dialog. Settings
keeps keyboard-safe content spacing.

Compact mode uses a single boundary: the current Nagi window is compact below 600dp and wide at 600dp or above. This decision is independent of previous widths and saved layout state. It measures the full app window before sidebar or Split-pane allocation, using logical dp (pixels divided by the current display density), rather than identifying a phone model or using orientation, the physical display, or the width of an individual web page. Display scaling can change this logical width; font scaling alone does not change the boundary. A portrait tablet or unfolded foldable with a window at least 600dp wide keeps the wide UI. Any device whose app window is narrowed below 600dp, including tablet multi-window, uses compact UI. A landscape phone at least 600dp wide uses wide UI as well. System video PiP freezes the preceding browser mode until it exits.

The previous 600dp-entry/640dp-exit hysteresis allowed both layouts at 600–639dp depending on resize history; it has been removed. The wide sidebar's width and collapsed preference remain independent of the temporary compact sheet. Resizing to a wide window dismisses the sheet and restores that preference.

The wide sidebar boundary has a transparent 24dp drag target centered on the
existing 8dp gap. A neutral gray guide appears only after resizing starts,
and disappears on release or cancellation; hovering or pressing alone does
not reveal it. Dragging still collapses at the minimum width, reopens a
collapsed sidebar, and saves the width only on release.

**Settings → Appearance → Search bar at bottom** uses one 56dp row below the page:
the address field, New tab, tab count/list and a vertical three-dot page menu.
It has no Home button or separate Back/Forward row. The address field shows the
host (and explicit port); tapping it opens the full URL in the Command Bar.
Share, Copy link and blocking controls move into the page menu, as does Forward.
Android Back and keyboard history shortcuts remain available. The choice
persists across launches and applies only below 600dp; wide windows retain the
top address bar. With this option enabled, search suggestions appear above the
input and the input stays above the keyboard. The default remains the top
address bar with the existing compact navigation controls.

The top-address compact layout moves Back/Forward to the bottom bar and Reload
into the page menu, leaving more width for the URL. Search uses most of the available width, a close icon, and omits the desktop keyboard hint. Wide headers and the sidebar keep their existing layout. Split is available only in wide windows. Below 600dp, the selected tab fills the page, including either member of a saved pair. The tab sheet lists both members individually; page/tab menus, Command Bar commands, and drag targets do not offer Split creation, replacement, swapping or closing. New-tab resume links also open individual pages in compact mode.

The page menu omits Forward when the top toolbar or compact bottom bar already
provides it. The single-row bottom toolbar, Split and narrow wide-window panes
keep the menu action because their address bar has no Forward button.

Narrowing a window never detaches a saved pair or closes its partner. Both live engines remain protected from suspension and archiving while the pair is selected, including across Settings. Widening the window restores both panes with their original left/right order and selected side. Explicit tab close, move and reorder actions retain their existing repository semantics. This does not add vertical splitting or hinge/posture handling.

The compact toolbar and the area behind system navigation share the same theme surface color, including live Space-color changes. In compact browsing, the three-button contrast scrim is disabled so it cannot add a different tint; leaving compact browsing restores the window's prior setting. Safe-area padding still keeps buttons above the system navigation controls.

Compact browser panes have straight top and bottom edges with either address-bar
position. No extra outer top spacing is added beyond system-bar/cutout safety.
With the address bar at the bottom, the idle top divider reserves no space;
loading progress overlays the page's top edge instead of pushing it down.

On the next document navigation or manual reload, sites without a saved manual
display choice use mobile mode on phone-sized configurations (Android's smallest
width below 600dp), including landscape phones whose browser chrome uses the
wide UI. Compact windows also request mobile sites. Wide tablet/unfolded
configurations with a smallest width of at least 600dp use the desktop default.
Disabling **Desktop site by default** also requests mobile on those wide windows
on the next navigation/reload. A site's explicit **Use desktop site** / **Use
mobile site** choice always takes precedence. Automatic window changes never
write to the per-site choice store. System video PiP retains the preceding
default until it exits.

Mobile site mode requests a phone layout even on a tablet: the installed WebView's UA keeps its browser/OS version and includes the `Mobile` token. Supported WebViews also receive matching User-Agent Client Hints (`mobile=true` for mobile mode; `mobile=false` and Linux desktop metadata for desktop mode). A tablet's unmodified default Android UA is insufficient: it can lack `Mobile` and request a fixed-width desktop document. Viewport tags are supplied by websites and respected by the browser; site HTML is not rewritten to force a layout.

Resizing changes the browser UI and the default for future navigation; it does not stop loading, update a live page's user agent, or reload its document. The WebView, DOM, input/textarea/contenteditable drafts, and current document mode remain intact for visible, hidden, and Split pages. Selecting a retained tab does not apply the new mode by reloading. Restoring a cached snapshot uses the mode in which its document was fetched, and defers the new default or saved site choice until the next navigation/reload. A URL-only restoration necessarily loads a document and uses the current choice.

Typing, page scrolling, same-document anchor navigation, and SPA history changes do not apply a pending mode. New tabs, address/search navigation, document links, manual reload, and document history navigation resolve the latest default/site choice at their normal navigation boundary. POST form submissions keep their original method/body and current document UA; they are not replayed as GET requests to change modes. Explicitly choosing **Use desktop site** / **Use mobile site** still switches the current page immediately. The page-menu action describes the mode of the currently loaded document. Entering the small system video PiP window keeps the preceding browser UI/default.

The page menu offers **Use mobile site** and **Use desktop site**, with the current mode checked. Saved device-default choices from older versions are read as mobile choices. After a detected human-verification failure in desktop mode, the optional retry action switches that site to mobile mode and reloads only when tapped.

## Optional Spaces

**Settings → General → Use Spaces** (the first setting) defaults to on, preserving existing installations. Both directions first show a confirmation dialog explaining the affected controls, search/restoration scope, colors, retained data and unaffected Favorites/pins/Split. Cancel or dismiss leaves the preference unchanged. Turning it off uses the currently selected Space as a single tab collection. Its tabs, pins, saved Split pairs, Favorites and archive remain available. Other Spaces and their tabs stay in Room and become accessible again when Spaces are enabled; disabling does not merge, move or delete tabs.

Both the wide sidebar (expanded or collapsed) and compact tab sheet hide the Space switcher, creation/edit/delete controls, move-to-Space actions and horizontal Space swipes. The expanded list labels its pinned section “Pinned tabs” instead of showing a Space name/icon. The UI keeps the current Space's theme color, so turning Spaces on or off never changes the color; Settings › Appearance › Theme color edits that Space either way. Command Bar suggestions and closed-tab restoration stay within the current Space. Shared Favorites and browsing history remain available. Changing this setting does not reload pages or destroy live WebViews, including either Split pane. The preference persists across restarts.

## Settings navigation

Settings uses a category-and-detail layout. At 760 dp of available settings
content width, categories stay on the left and the selected category opens on
the right. Wide windows use 8 dp of end padding, so this corresponds to a
768 dp window without additional insets. Smaller windows show a category list;
choosing a category opens its settings, and Back returns to the list. Selection
survives resizing and Activity recreation.

Search-engine customization opens inside the Search engines content pane,
with the same wide category menu and breadcrumb layout as Open-source licenses.
Its breadcrumb and system Back return to Search engines. Narrow windows show
one back button above customization; the open sub-page survives resizing and
Activity recreation. Selecting another category closes the sub-page.
When its content pane is narrow, engine actions use a second row so the name
and URL retain enough space.

The browser sidebar is hidden while Settings is open. Returning to browsing
restores the existing sidebar preference and live tab/Split sessions. General
contains browsing behavior and Spaces; default search, AI and region choices
are grouped under Search engines. Appearance, Tabs, Video, Privacy and About
Nagi have their own categories. Password autofill has its own category in the 1.3.0/code-19 candidate; provider
availability is documented separately.

Settings uses a flat background continuous with the window edges.

Routine toggle explanations and duplicate headings have been removed. Brief
video trigger descriptions, camera registration instructions, privacy lifetime
and saved-download boundaries, archive/suspension limitations, and destructive
action confirmations remain where they help users make a decision. About Nagi
shows the installed version once; update dialogs retain their version display.

## Page zoom

Pinch zoom remains available. The page menu also exposes minus, the current
percentage (tap to reset), and plus; Ctrl/Command + plus, minus and zero use the
same focused-page operations. These are native WebView page-scale changes,
including images and layout, rather than changing only `textZoom`. They do not
reload the document or inject CSS. Reader has its separate text-size controls.

100% means the current document's initial rendered scale, independent of screen
density. Native viewport minimum/maximum scales remain in force, so a site can
limit zooming; the controls reflect WebView's available directions. Scale
callbacks update the displayed percentage after pinching. Rapid operations are
serialized against native scale changes, with reset replacing queued operations.
Pending zoom operations prevent background suspension.

Live tab/Space switches, Settings, theme changes and resizing keep the existing
WebView. Its history snapshot also carries the density-independent reset
baseline and relative zoom. Explicit snapshot restoration waits for the restored
document's first frame, reapplies that zoom once if needed, and restores saved
scroll coordinates after the scale settles. New touch, zoom or navigation cancels
this delayed restoration. A new document or reload establishes a new initial-render baseline;
there is no new persisted per-site zoom preference or process-death DOM guarantee.
The implementation observes scale callbacks without automatically reapplying a
zoom during video presentation or window resizing.

API references: [WebView zoom operations](https://developer.android.com/reference/android/webkit/WebView#zoomBy(float)),
[scale callbacks](https://developer.android.com/reference/android/webkit/WebViewClient#onScaleChanged(android.webkit.WebView,%20float,%20float)).

## Validation

The compact top-edge update on 2026-11-18 passed Debug/test APK assembly and
Debug lint (0 errors, 96 warnings). Four existing `CompactBrowserUiTest` cases
passed individually on the dedicated API-36 `Nagi_CompactPanel_QA_20261118`
emulator / WebView 133.0.6943.137: the single-row toolbar, navigation backgrounds
across themes, address placement and retained pages across widths, and saved
Split restoration. Screenshots were inspected for compact top/bottom address
placement in light/dark themes and wide Split. In the 360dp bottom-address
fixture capture, the page starts at row 119 across both edges and the center;
the previous center started at row 124 (the removed 2dp divider). System-bar
and display-cutout safety remain applied. Evidence and the co-installable
Debug APK are under ignored `artifacts/compact-panel-ime-20261118/flat-top-edge/`.
Unit tests were not rerun for this cosmetic update; neither a full connected
suite nor a physical device was checked. No version or release action was performed.

The single-row bottom-toolbar update on 2026-11-18 passed all 218 Play Debug
unit tests, Debug/test APK assembly, and Debug lint (0 errors, 96 warnings). Six selected
`CompactBrowserUiTest` cases passed individually on the dedicated API-36
`Nagi_CompactPanel_QA_20261118` emulator / WebView 133.0.6943.137:
the new `bottomChromeToolbarUsesOneRowAndKeepsNavigationAndPageActions`, existing
bottom-address placement/persistence, compact keyboard handling, navigation
background/theme colors (both address placements), saved Split retention, and
the wide Command Bar keyboard regression. No selected case failed or skipped.
The new case checks a single 56dp row at 320/360/411dp, control order and 48dp
action targets, no Home/Back/Forward buttons in that row, full-URL editing,
relocated page actions, Android Back, menu Forward, New tab and tab selection,
and the same live WebView/draft across layout and theme changes.
Light/dark screenshots were inspected. Evidence and the co-installable Debug
APK are under ignored `artifacts/compact-panel-ime-20261118/chrome-bottom-bar/`.
These are local fixture/selected emulator checks; a full connected suite and
physical-device rendering were not checked. No version or release action was
performed.

The wide-keyboard correction on 2026-11-18 first reproduced the remaining issue:
Command Bar input reduced the wide sidebar/page by 778px and moved History and
the new-tab palette upward by the same amount. The corrected browser shell
keeps all four bounds unchanged (0px movement) at 1000 x 820dp/light and
820 x 360dp/dark while the Command Bar remains usable above the keyboard.
Actual page input still resizes single/Split WebViews, retains both live engines
and drafts, and keeps the wide sidebar at full height.

All 218 Play Debug unit tests passed; Debug/test APK assembly and Debug lint
passed (0 errors, 95 warnings). Four selected `CompactBrowserUiTest` cases passed
individually on the dedicated API-36 emulator / WebView 133.0.6943.137:
`commandKeyboardKeepsWideBrowserChromeBehindIt`,
`pageKeyboardResizesPanesWithoutRaisingWideSidebar`,
`keyboardHidesCompactFooterAndBottomAddressWithoutReloadingPage`, and
`savedSplitUsesOneSelectedPageInCompactAndRestoresBothLivePagesWhenWide`.
Intermediate combined runs each had one failure: a compact assertion preceded
Compose's IME visibility update, and a subsequent Command Bar IME-show wait
timed out. Tests now await the inset update, zero remaining hide-animation
insets and Activity focus before the next interaction. The final four independent
runs passed without failures or skips; this is not a passing full connected suite.
Screenshots were inspected. Logs, bounds and the updated test APK are under
ignored `artifacts/compact-panel-ime-20261118/wide-keyboard-shell/`.
Physical-device/OEM keyboard behavior remains unverified. No version or release
action was performed.

The phone-default update on 2026-11-18 passed all 218 Play Debug unit tests,
Debug lint (0 errors, 95 warnings), and Debug/test APK assembly. Three selected
`CompactBrowserUiTest` cases passed on API 36 / WebView 133.0.6943.137:
`phoneLandscapeKeepsMobileSitesAndExplicitDesktopChoice`,
`tabletEngineRequestsPhoneLayoutAndClientHintsWhenItsWindowBecomesCompact`, and
`explicitDesktopAndMobileChoicesSurviveBothLayoutsAndRecreation`.
These check actual fixture HTTP user agents and mobile Client Hints, mobile
defaults in 360dp portrait and 820 x 360dp landscape, retained live drafts during
resizing, saved per-host desktop/mobile choices, and tablet defaults. The
landscape screenshot was inspected. Evidence and the separately packaged Debug
APK are under ignored `artifacts/compact-panel-ime-20261118/phone-mobile-default/`.
This is selected emulator coverage, not a full connected suite, physical-device
or live-provider check. No version bump or release action was performed.

On 2026-11-18, the current development worktree passed all 218 Play Debug unit
tests (no failures, errors or skips), Debug/test APK assembly, and Play Debug lint
(0 errors, 95 warnings). Four selected `CompactBrowserUiTest` cases passed on
the dedicated API-36 `Nagi_CompactPanel_QA_20261118` emulator (`emulator-5588`,
WebView 133.0.6943.137): content-sized sheets, keyboard/footer behavior, compact
tab/Space actions, and saved-Split retention across compact/wide windows.
The sheet regression covers adding/removing tabs, expanding/collapsing sections,
scrolling a capped list and disabling Spaces. The keyboard regression covers
page and Command Bar input with both address-bar positions and light/dark themes,
including footer restoration and retained live WebViews/drafts.
An initial keyboard assertion ran during the IME hide animation; it now waits
for the footer to be displayed after the animation. The final four-case run
passed without failures or skips. Evidence is under ignored
`artifacts/compact-panel-ime-20261118/`. This is selected emulator coverage of
the mixed development worktree, not a full connected suite or physical-device
result. No version bump or release action was performed.

On 2026-11-15, an isolated candidate from `045f1f8` passed all 195 Play Debug
unit tests (zero failures/errors/skips), Debug/test APK assembly, and Play
Debug/Release lint (0 errors and 81 warnings each). The candidate contains only
the compact single-page change, its tests and documentation.

All seven selected UI cases passed together on the dedicated API-36
`Nagi_Download_QA` emulator (`emulator-5584`, WebView 133.0.6943.137). Coverage
includes 360/599/600/1000dp windows and light/dark themes; separate compact entries
for both saved members; absent Split menus, commands and drag previews; identical
live WebViews and retained input/textarea/contenteditable drafts and document
markers through resizing; saved-pair restoration after Activity recreation;
bottom-search placement; hidden-partner navigation; closing either focused wide
pane; whole-pair dragging; and replacing one wide pane. Recreation verifies saved
pairing, not arbitrary live DOM/draft recovery. Screenshots were inspected.
Evidence and frozen Debug/test APKs are under ignored
`artifacts/compact-single-pane-20261115/commit/`. This is selected emulator
coverage, not a full connected suite or physical-device result. No release action
or version bump was performed.

Earlier validation of the mixed development worktree on 2026-11-15 passed the compact single-page policy in
`CompactBrowserUiTest#savedSplitUsesOneSelectedPageInCompactAndRestoresBothLivePagesWhenWide`
on the dedicated API-36 `Nagi_Download_QA` emulator (`emulator-5584`, WebView
133.0.6943.137). It covers 360/599/600/1000dp windows, light/dark app themes,
individual tab-sheet entries, no compact Split menu/commands/drop previews,
selection of either saved member, original left/right order on widening, identical
live WebViews, input/textarea/contenteditable drafts and a JavaScript document
marker, one HTTP document request per page before recreation, and saved-pair
restoration after Activity recreation. Recreation checks saved pairing,
not restoration of arbitrary live drafts. Narrow and wide screenshots were inspected.

All 195 Play Debug unit tests passed (zero failures/errors/skips), including compact
Command Bar filtering and individual new-tab resume links. Debug/test APK assembly
and Play Debug/Release lint passed; lint reports 0 errors and 82 warnings each.
The first build exposed an incorrect test accessibility API call; initial device
runs exposed title-only readiness and outer-container semantics selectors. Those
test issues were corrected before the passing run. Evidence, source snapshots and
frozen Debug/test APKs are under ignored `artifacts/compact-single-pane-20261115/`.
Seven distinct selected UI cases passed across the final focused runs: the new
compact-pair case, bottom-search placement/retention, hidden-partner navigation,
closing either focused wide pane, dragging a whole wide pair, and replacing one
wide pane. The first seven-case run passed six cases; the older hidden-page test
looked for asynchronous metadata/request records too early. It now waits for the
specific URL and selects the compact partner before checking its completed
navigation; its final focused rerun passed. These are selected local emulator
checks; physical devices and a full connected suite were not verified for this
change. No version bump or release action was performed.

On 2026-11-10, page zoom passed three native WebView cases, one menu case at
1000×800 dp/dark, and two existing video regressions on `emulator-5584`
(API 36, WebView 133.0.6943.137). The menu case also passed at approximately
411×914 dp/light; both screenshots were inspected. Checks cover whole-page scale
with `textZoom` unchanged, draft/document retention, rapid zoom/reset ordering,
viewport limits, live theme/visibility changes, suspension protection and snapshot
restoration. This provider initially restored the document at 100% after saving
125%; the explicit restore now preserves 125%, its original reset baseline and
nonzero scroll Y=600, then resets to the original rendered scale. The existing
video cases preserve user zoom through repeated tab switches and shrinking viewports.

After the snapshot fix, all 193 Play Debug unit tests passed with no failures or
skips, Play Debug and Release lint each reported 0 errors/81 warnings, and both
Debug APKs built. Logs, frozen APKs and screenshots are retained under ignored
`artifacts/page-zoom-20261110/`. This is selected emulator coverage; physical pinch
gestures, other WebView providers and arbitrary live sites remain unverified.

On 2026-11-10, settings navigation passed two new UI cases and 16 distinct
existing settings-related cases across focused runs on the dedicated API-36
`Nagi_Download_QA` emulator (`emulator-5584`, WebView 133.0.6943.137).
The new cases cover category/detail navigation, selection through Activity
recreation and 360/700/768/1000 dp window changes, persisted preferences,
confirmation cancellation, the autofill approval gate, and return to the same
live Split pages/drafts and sidebar width. Screenshots were inspected for the
category list, details and the 760 dp available-content boundary in light/dark
themes under ignored `artifacts/settings-ui-20261110/`.

The existing cases cover camera registration/rotation and portrait exclusion,
Spaces, common/AI/custom search engines, CJK engine-name composition, Japanese
interface navigation, video settings, background appearance, themes, the bottom
search bar, site-data confirmation and workspace retention. Earlier harness
failures used the old sidebar/category assumptions, a fixed label, a Space
control outside the horizontal viewport, and an absolute portrait coordinate
that included the system display cutout; corrected focused reruns passed.
This is local selected emulator coverage, not a full instrumentation suite or
physical-device/OEM validation. No release was performed.

The 2026-11-09 confirmation update passed the same `SpacesUiTest` and Play Debug lint (0 errors). It also checks that the toggle precedes the default search engine in General, that neither direction changes the saved preference before confirmation, and that cancellation preserves the preference. Confirmation dialogs were inspected at 1000dp/light and 360dp/dark; the explanation scrolls in constrained windows. The updated Debug APK was installed and launched on the dedicated QA emulator.

On 2026-11-09, `SpacesUiTest` passed on the dedicated API-36 `Nagi_Spaces_QA_20261109` emulator. It checks the Settings toggle; 360dp/1000dp widths in light/dark themes; expanded/collapsed sidebar; disabled swiping; preserved Space records, Split pairing, identical live WebViews and unfinished page inputs; re-enabling; and preference retention through Activity recreation. Screenshots were inspected under ignored `artifacts/spaces-toggle/`. `SpacesSettingsTest`, `SpacesSuggestionsTest`, and the focused restoration case in `WorkspaceRepositoryTest` cover DataStore reopen/default compatibility, search filtering, and restoration restricted to the current Space. This is local development validation, not a physical-device or store-release result.

On 2026-10-30, API-36 emulator `Nagi_BottomSearch_QA` passed three targeted `CompactBrowserUiTest` cases: the bottom-search setting through the Settings UI, saved state after activity recreation, address/input placement across 360/1000dp widths, search execution, retained WebViews/scroll, and existing tab-sheet/Split behavior. `BottomSearchBarSettingsTest` also verifies the default for existing settings and persistence after reopening DataStore. This run passed 159 GitHub Debug unit tests and GitHub Debug Lint (0 errors; existing warnings remain).

API-36 ARM64 emulator `Nagi_Foldable_UI_QA`, tested on 2026-10-11:

- `AdaptiveSidebarUiTest`: transitions through narrow, boundary, and wide widths, temporary sheet opening, and restoration of both expanded and collapsed wide preferences.
- `CompactBrowserUiTest`: full-width pages, touch-target sizes, tab creation/selection/closure, Space switching, sheet dismissal with Back, and retention of the same WebViews across 320/360/480/1000dp widths. Checks also cover page scroll, Back/Forward history, unfinished search input, both pages of an existing Split, automatic site modes through the 600dp boundary in both directions, real HTTP user agents, visit deduplication, and manual overrides across resizing/recreation.
- `SiteDisplayModeEngineTest`: per-host choices, cross-host links/redirects and Back/Forward, automatic changes without creating an override or an extra history entry, and the current mode after snapshot restoration.
- Additional compact/video cases check deferred mode changes for hidden tabs, both Split panes, and continuous desktop-video playback through Home/PiP/restore without reloading.
- A separate 2560×1600 / 320dpi run checks existing browsing, Split resizing/swapping, settings and recreation, responsive address actions, and sidebar copy behavior.

The final 1.0.0 Play Debug combined run passed 29 cases covering the current compact/site-mode behavior and privacy/blocking regressions. GitHub 147 and Play 136 unit tests passed; signed builds and Release Lint passed. See [publication verification](RELEASE_1.0.0_VERIFICATION.md) for the final source and counts.

The latest deferred-navigation feature run also verified that input, textarea, contenteditable content, and a document marker survive both resize directions, hidden-tab selection, Split changes, and anchor navigation without another document request. On the live Asahi page, narrowing from 800dp to 432dp preserved the draft, marker, and existing desktop UA; the next document link loaded the phone site.

Physical fold/unfold transitions, OEM keyboard behavior, hinges, older Android navigation rendering, and hand reach on a real foldable remain unverified. Retaining the WebView does not establish DOM/form restoration after process death.

The sheet uses Compose's [ModalBottomSheet](https://developer.android.com/develop/ui/compose/components/bottom-sheets). Superseded layout runs and temporary Debug APKs were removed during local cleanup; final release XML results remain in ignored `artifacts/1.0.0/validation/`.
