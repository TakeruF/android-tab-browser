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

## Validation

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
