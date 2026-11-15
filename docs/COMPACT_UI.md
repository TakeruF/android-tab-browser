# Narrow-window browsing

Nagi continues to prioritize tablet and unfolded-phone windows. Since version 1.0.0, narrow windows use full-width pages with a 56dp bottom bar for Back, Forward, New tab, Search, and a tab-count button. The tab button opens the existing tabs, Favorites, and Spaces interface in a modal bottom sheet; selecting a tab, Space, or favorite closes it. History and Settings remain available in that sheet. Tab action/close buttons have 48dp targets in the sheet.

Compact mode uses a single boundary: the current Nagi window is compact below 600dp and wide at 600dp or above. This decision is independent of previous widths and saved layout state. It measures the full app window before sidebar or Split-pane allocation, using logical dp (pixels divided by the current display density), rather than identifying a phone model or using orientation, the physical display, or the width of an individual web page. Display scaling can change this logical width; font scaling alone does not change the boundary. A portrait tablet or unfolded foldable with a window at least 600dp wide keeps the wide UI. Any device whose app window is narrowed below 600dp, including tablet multi-window, uses compact UI. A landscape phone at least 600dp wide uses wide UI as well. System video PiP freezes the preceding browser mode until it exits.

The previous 600dp-entry/640dp-exit hysteresis allowed both layouts at 600–639dp depending on resize history; it has been removed. The wide sidebar's width and collapsed preference remain independent of the temporary compact sheet. Resizing to a wide window dismisses the sheet and restores that preference.

**Settings → Appearance → Search bar at bottom** optionally moves the address/search bar below the page, just above the compact navigation controls. The choice persists across launches and applies only below 600dp; wide windows retain the top address bar. With this option enabled, search suggestions appear above the input and the input stays above the keyboard. The default remains the top address bar.

Compact page headers move Back/Forward to the bottom bar and Reload into the page menu, leaving more width for the URL. Search uses most of the available width, a close icon, and omits the desktop keyboard hint. Wide headers and the sidebar keep their existing layout. Split remains a left/right pair; this change does not add vertical splitting or hinge/posture handling.

The compact toolbar and the area behind system navigation share the same theme surface color, including live Space-color changes. In compact browsing, the three-button contrast scrim is disabled so it cannot add a different tint; leaving compact browsing restores the window's prior setting. Safe-area padding still keeps buttons above the system navigation controls.

On the next document navigation or manual reload, sites without a saved manual display choice use mobile mode in compact windows and the desktop default in wide windows. Disabling **Desktop site by default** also requests mobile on wide windows on the next navigation/reload. A site's explicit **Use desktop site** / **Use mobile site** choice always takes precedence. Automatic window changes never write to the per-site choice store.

Mobile site mode requests a phone layout even on a tablet: the installed WebView's UA keeps its browser/OS version and includes the `Mobile` token. Supported WebViews also receive matching User-Agent Client Hints (`mobile=true` for mobile mode; `mobile=false` and Linux desktop metadata for desktop mode). A tablet's unmodified default Android UA is insufficient: it can lack `Mobile` and request a fixed-width desktop document. Viewport tags are supplied by websites and respected by the browser; site HTML is not rewritten to force a layout.

Resizing changes the browser UI and the default for future navigation; it does not stop loading, update a live page's user agent, or reload its document. The WebView, DOM, input/textarea/contenteditable drafts, and current document mode remain intact for visible, hidden, and Split pages. Selecting a retained tab does not apply the new mode by reloading. Restoring a cached snapshot uses the mode in which its document was fetched, and defers the new default or saved site choice until the next navigation/reload. A URL-only restoration necessarily loads a document and uses the current choice.

Typing, page scrolling, same-document anchor navigation, and SPA history changes do not apply a pending mode. New tabs, address/search navigation, document links, manual reload, and document history navigation resolve the latest default/site choice at their normal navigation boundary. POST form submissions keep their original method/body and current document UA; they are not replayed as GET requests to change modes. Explicitly choosing **Use desktop site** / **Use mobile site** still switches the current page immediately. The page-menu action describes the mode of the currently loaded document. Entering the small system video PiP window keeps the preceding browser UI/default.

## Optional Spaces

**Settings → General → Use Spaces** (the first setting) defaults to on, preserving existing installations. Both directions first show a confirmation dialog explaining the affected controls, search/restoration scope, colors, retained data and unaffected Favorites/pins/Split. Cancel or dismiss leaves the preference unchanged. Turning it off uses the currently selected Space as a single tab collection. Its tabs, pins, saved Split pairs, Favorites and archive remain available. Other Spaces and their tabs stay in Room and become accessible again when Spaces are enabled; disabling does not merge, move or delete tabs.

Both the wide sidebar (expanded or collapsed) and compact tab sheet hide the Space switcher, creation/edit/delete controls, move-to-Space actions and horizontal Space swipes. The expanded list labels its pinned section “Pinned tabs” instead of showing a Space name/icon. The UI uses the configured theme color while Spaces are disabled. Command Bar suggestions and closed-tab restoration stay within the current Space. Shared Favorites and browsing history remain available. Changing this setting does not reload pages or destroy live WebViews, including either Split pane. The preference persists across restarts.

## Settings navigation

Settings uses a category-and-detail layout. At 760 dp of available settings
content width, categories stay on the left and the selected category opens on
the right. The usual wide window frame consumes 8 dp, so this corresponds to a
768 dp window without additional insets. Smaller windows show a category list;
choosing a category opens its settings, and Back returns to the list. Selection
survives resizing and Activity recreation.

The browser sidebar is hidden while Settings is open. Returning to browsing
restores the existing sidebar preference and live tab/Split sessions. General
contains browsing behavior and Spaces; default search, AI and region choices
are grouped under Search engines. Appearance, Tabs, Video, Privacy and About
Nagi have their own categories. Password autofill remains hidden pending the
existing production approval gate.

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
