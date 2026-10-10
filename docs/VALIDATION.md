# Validation records

The [2026-10-06 cross-feature audit](CROSS_FEATURE_AUDIT_2026-10-06.md) found Split action-target, Archive, uppercase-scheme, and upload-extension bugs. Version 0.1.1 fixes them and adds Bookmarks, site-data clearing, link/image menus, and in-app updates. Navigation immediately stores the requested URL and supports recovery after a 30-second loading timeout. ChatGPT answers/login are not established by these checks.

Current results are in [0.1.2 release validation](#2026-10-06--012-release-validation). The records below are chronological: failures and incomplete checks describe their historical stage, not the final release.

Initial validation date: 2026-10-03. JDK 17 / Android SDK 36 / Gradle 8.13. Instrumentation used the API-36 Pixel Tablet emulator `Orbit_Tablet_QA`, 2560 × 1600 / 320 dpi / landscape.

## Initial implementation build and tests

```sh
./gradlew :app:assembleDebug :app:assembleRelease \
  :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest
```

| Check | Result |
| --- | --- |
| Debug APK | Successful |
| Release APK / R8 | Successful; unsigned because signing was not configured |
| Android Lint | 0 errors; warnings included dependency updates |
| Unit tests | 26 passed; no failures/skips |
| Instrumentation | 13 passed; no failures/skips |

Unit coverage: URL/IDN/IPv4/IPv6/ports/unsafe schemes, keywords, and UTF-8 search (11); live retention of 25 tabs, visibility, closed-session disposal, and full shutdown (4); real Room Space/Tab/Bookmark/Engine/Archive invariants (8); download filenames (3).

Instrumentation: browsing/history/Favorites/Bookmarks/tab restoration; Split ratios/swap/exit; engine addition/recreation (3); CJK input (4); actual WebView JavaScript/cookies/localStorage/IndexedDB/back-forward/find/history snapshots/desktop UA/download/HTML input/target blank/file-picker/fullscreen callbacks (6).

Reports:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`

## Japanese, Chinese, and Korean input

A debug-only InputMethodService exercised Android's actual InputConnection instead of replacing Compose text directly. Tests restore the original IME. The service is absent from Release APKs.

| Target | Actions and checks |
| --- | --- |
| Japanese Command Bar | `に` → `にほん` → `にほんごのけんさく` → `日本語の検索`; retain text/focus during composing Down/Enter/IME Go, then reconvert and search after commit |
| Chinese Command Bar | `zh` → `zhongwen` → `zhongwensousuo` → `中文搜索`; prevent premature execution and search with IME Go after commit |
| Korean Command Bar | `ㅎ` → `하` → `한` → `한구` → `한국어 검색`; verify composition updates and committed Enter |
| Search-engine name | Japanese/Chinese/Korean preedit and commit in one field |
| HTML form | Send composition/commit to a real WebView field and read the final value with JavaScript |

Fixed a regression where composing Down moved Compose focus to another suggestion and lost subsequent input. TextFieldState retains composition; Enter/Up/Down/Escape and IME Go reaching the app during composition do not navigate or search.

Initial Command Bar focus waits until the dialog window can receive input, fixing a race caused by requesting parent-window focus before the IME connected. Test-IME binding/unbinding synchronization is separate from app focus.

Command Bar applies safe-area/IME insets and shortens the scrollable suggestion list while the keyboard is visible, following [Android's WindowInsets guide](https://developer.android.com/develop/ui/compose/system/insets-ui).

This validates app/input-connection behavior, not Gboard/Samsung dictionaries, physical keyboards, or manufacturer-specific IME behavior.

## Trackpad and mouse scrolling

Added BrowserScrollTest on 2026-10-03. Four tests passed on Orbit_Tablet_QA, then four passed again on a dedicated Orbit_Scroll_QA to avoid concurrent APK installation. Input entered through the Activity window, covering Compose/native forwarding.

- Mouse `ACTION_SCROLL` moves vertically/horizontally and back.
- `SOURCE_TOUCHPAD` reaches an HTML scroll container without moving the whole page; each input produces one DOM wheel event.
- Fractional mouse/touchpad scrolling still works after touch swipes.
- In Split, only the pane under the pointer scrolls without a prior click; focus follows that pane and Ctrl W closes it.

Because touchpad events normally follow focus, only `ACTION_SCROLL` is converted to pointer input at the window boundary. Coordinates, axes, and fractions are retained for standard WebView dispatch. The native container observes touch starts/scrolling to report pane focus. References: [MotionEvent](https://developer.android.com/reference/android/view/MotionEvent) and [dispatchGenericMotionEvent](https://developer.android.com/reference/android/view/View#dispatchGenericMotionEvent(android.view.MotionEvent)).

This injects Android events on an emulator; physical drivers, acceleration, and gestures are not verified. Debug, 34 unit tests, and Lint (0 errors) passed. Dedicated-device runs passed these four plus BrowserUi (3), CJK (4), and WebView (6), totaling 17; execution used `adb -s emulator-5556 shell am instrument -w -r`. Logs: `app/build/reports/scroll-validation/`.

The complete suite was not considered finished at that stage: an independently added sidebar Favorite-drag test exceeded its one-second wait and auto-scroll was interrupted while waiting. Six remaining WebView tests passed separately. Sidebar dragging was outside the scrolling repair.

## Manual checks

- Installed/launched the APK and inspected home, sidebar, Command Bar, Settings, and Split.
- Displayed normal Gboard and checked the leading suggestion/action hints. This was English-keyboard rendering, not CJK dictionary coverage.
- Loaded an external HTTPS page and verified its title/content.
- Opened the Android document picker from a local HTML file input, selected `orbit-upload.txt`, and verified the filename through HTML File API.
- Saved a local HTTP attachment to public Downloads with DownloadManager; verified SUCCESS and `orbit.txt` content `Orbit download verification`.
- Fixed attachment navigation overwriting the document URL and returning to an empty tab after recreation; added a regression asserting original URL/title retention after downloading.

[Screenshots](screenshots/) use actual WebView rendering. Split/file examples use local fixture HTML; `web-https.png` shows an external HTTPS page.

## Remaining device validation

Real camera/microphone/location, video codecs/fullscreen playback, Android 8–9 download destinations, manufacturer IMEs, and broad real-site compatibility remain unverified. Permission/fullscreen callbacks are implemented, but callback tests do not prove hardware behavior. See [README](../README.md) for MVP scope, restoration guarantees, shared cookies, and unimplemented features.

## Arc sidebar and regional search

[ARC_REFINEMENT.md](ARC_REFINEMENT.md) records the scope and screenshots. At this stage, 34 unit and 16 targeted device tests, Debug/Release builds, and Lint passed.

## Colors and readability — 2026-10-03

Fixed unspecified root `contentColor` causing black sidebar text/action icons/Favorite initials in dark mode when the background mixed Space colors.

- Increased new-tab heading/supporting-text contrast and outlined search fields.
- Added fill/border selection for tabs, suggestions, and Spaces, with semantic selection.
- Defined Material 3 surface/text colors per theme and replaced translucent Settings/search cards with opaque theme surfaces.
- Preserved stored Space colors; corrected displayed icon colors for at least 4.5:1 background contrast. Cloud/heart symbols use text presentation.
- Synchronized app/dialog system-bar text with the app theme independently of the OS theme.
- Improved Split focus/divider and error Retry colors.

Computed sRGB ratios: normal text/surface 12.67:1 light, 12.06:1 dark; secondary text/surfaceContainerHigh 5.60:1/6.84:1; selected text/primaryContainer 8.71:1/7.36:1; search outline/surfaceContainer 3.77:1/5.10:1. Disabled states, web content, and IME colors are excluded.

Debug/Lint passed (0 errors/23 warnings); BrowserUi's three cases passed. Inspected home, Settings, Command Bar, and Space editing on API 36, without physical-device proof.

Screenshots: [light](screenshots/colors-light-home.png), [dark](screenshots/colors-dark-home.png), [dark Settings](screenshots/colors-dark-settings.png), [suggestions](screenshots/colors-dark-commandbar.png), [Space editing](screenshots/colors-dark-editor.png).

## Detailed UI tests and layout repairs — 2026-10-03

| Check | Result |
| --- | --- |
| Debug / AndroidTest APK | Successful |
| Release / R8 | Successful, unsigned |
| Unit tests | 34 passed; no failures/skips |
| Instrumentation | 29 passed; no failures/skips |
| Lint | 0 errors, 23 warnings |

The API-36 connectedDebugAndroidTest run took 2m 2s: existing BrowserUi (3), CJK (4), WebView (6), scroll (4), drag (3), plus AppearanceUiTest (9).

Additional coverage:

- Composited light/dark sidebar text, Favorite initials, headings, icons, and new-tab text: 4.5:1 text and 3:1 icons.
- Actual theme chips, Settings/selection persistence after recreation.
- Command Bar focus, Down selection, Escape dismissal, and dialog system-bar text in both themes.
- Six Space colors in both themes and rendered cloud-icon contrast; correction targets 4.5:1, with 4.4:1 minimum for this thin anti-aliased glyph.
- Collapsed tab names, selection, and clicks through semantics.
- Portrait Split with a 380dp sidebar, moved divider, find-field width ≥100dp and height ≤64dp, dismissal, and Split exit.
- System-theme night-mode changes preserving tabs and tracking rendering/system bars; OS settings restored afterward.
- Actual local WebView connection errors and readable, working Retry in both themes.

Portrait inspection found narrow wrapping and a nearly zero-width find field. Compact panes move Reload into the menu, shorten new-tab copy/padding, and put find input/navigation on two rows. Split ratios adapt to width, keeping each pane ≥180dp when possible. Collapsed-tab accessibility names were also restored.

The first full run failed three drag setups because Favorites left by earlier tests reduced the viewport. Drag tests now use a fixed Favorite fixture and restore entries afterward. All 29 then passed together.

Screenshots: [portrait Split](screenshots/ui-portrait-split.png), [portrait find](screenshots/ui-portrait-find.png), [light suggestions](screenshots/ui-light-commands.png), [dark error](screenshots/ui-dark-error.png), [system dark](screenshots/ui-system-dark.png).

Reports: `app/build/reports/androidTests/connected/debug/index.html`, `app/build/reports/tests/testDebugUnitTest/index.html`; logs: `app/build/reports/ui-validation/`. Emulator only; physical devices, other manufacturers' IMEs, and Android 8–15 were not covered.

## README and licenses

Added MIT LICENSE, dependency attribution, original texts, and copyrights. The then-current inventory matched 108 resolved Release runtime modules. Nine bundled license/NOTICE/project-MIT files matched bytes in Debug/Release APK `assets/licenses/`; both builds and Lint passed afterward. Checked README local links and 21 external license links, and refreshed light/dark home captures.

## Favicons — 2026-10-04

Debug/test builds, 39 unit tests, FaviconUi (1), BrowserUi (3), and Lint (0 errors) passed. Coverage: Favorites without cached images or with deleted files, initial fallback after failed fetch, site-provided icon updates, pin/reload/recreation rendering and persistence. Inspected Gmail/ChatGPT/GitHub seed icons. Screenshots: `app/build/reports/favicon-validation/`; no physical-device proof.

## Sidebar annotation refinements — 2026-10-04

- Personal/Work default icons use house/briefcase emoji. Space editing adds presets/free input, including compound emoji persistence/recreation; old monochrome choices remain supported.
- Empty tabs use a home icon in expanded/collapsed views.
- Mouse hover reveals tab ellipsis/close and Favorite menus. Touch shows selected-tab actions and Favorite menus, with accessible “Show actions.”
- Right-click opens tab/Favorite menus without selecting or dragging.
- Revalidated reorder, pin, Favorite conversion, Space movement, and cancellation.
- The icon editor scrolls to accommodate keyboards/small screens.

Debug, 39 unit tests, and Lint (0 errors/23 existing warnings) passed. SidebarDrag (5) passed together; Appearance (9) and BrowserUi (3) passed in a separate run. API-36 emulator only, without physical trackpad validation.

Screenshots: [emoji/home](screenshots/sidebar-emoji-home.png), [hover actions](screenshots/sidebar-hover-actions.png), [context menu](screenshots/sidebar-context-menu.png).

Long-list auto-scroll now advances with rendering frames instead of a fixed timer, stops at list boundaries, and waits for rendering in assertions.

## Passkeys and Lucide — 2026-10-04

Enabled browser-mode WebAuthn on supported WebViews and added permission/dependencies for origin-preserving Credential Manager calls. API exposure, mismatched RP rejection, and cancellation passed on API 36/WebView 133.0.6943.137. Real-service registration/login remains unverified. Google Password Manager requires browser approval for third-party passkeys; settings alone are insufficient. See [PASSKEYS.md](PASSKEYS.md).

Replaced general Material icons with Lucide: 73 uses/34 kinds across sidebar, address bar, suggestions, library, Settings, and menus. Preserved labels, touch targets, theme colors, favicons, and Space emoji; Back/Forward support RTL. Bundled Lucide/Feather/Android licenses and updated the inventory to 127 modules including transitive dependencies.

Screenshots: [home](screenshots/lucide-home.png), [Settings](screenshots/lucide-settings.png), [context menu](screenshots/lucide-context-menu.png).

Debug/unsigned Release, 39 unit, and 35 instrumentation tests passed with no failures/skips, including three passkey cases plus existing UI/CJK/scroll/WebView/favicon/sidebar coverage. Lint: 0 errors/24 warnings (new WebKit update notice). Eleven bundled license files matched APK bytes. Physical passkey registration/login was not tested.

## Sidebar resizing and accent color — 2026-10-04

Hovering the boundary shows a horizontal resize cursor/guide. Dragging updates width directly; at 220dp minimum it collapses to a 72dp rail, and dragging right expands it. Prior width is retained for the ordinary expand button. The Settings slider also collapses at minimum; maximum width is 380dp. Secondary clicks/canceled drags do not change settings. Root-coordinate calculations compensate for boundary movement.

Appearance adds six accent presets/custom HEX, updating buttons, switches, chips, and selection with DataStore persistence. Space colors/emoji remain separate. Display colors target ≥4.5:1 text contrast in light/dark while preserving the entered color.

Screenshots: [minimum collapse](screenshots/sidebar-minimum-collapsed.png), [custom light](screenshots/custom-accent-light.png), [custom dark](screenshots/custom-accent-dark.png).

All 41 unit/38 device tests passed. An extra AppearanceUi (12) rerun passed final chip colors and resize moves separated by render frames. Debug/unsigned Release/Lint passed; API-36 mouse/touch injection only, without physical mouse/trackpad proof.

## Four locales and four additional engines — 2026-10-04

Added Sogou (`sg`), 360 (`360`), Douyin (`dy`), and Shenma (`sm`), bringing defaults to 13 at this stage. Fresh/existing installs seed once without overwriting IDs/keywords or restoring later edits/deletions. Douyin path queries encode spaces as `%20`. Verified CJK and `+`, `/`, `?`, `&` encoding.

Added 219 strings per English/Japanese/Korean/Simplified-Chinese locale, covering home, Settings, sidebar, menus, suggestions, permission/download prompts, and accessibility. User names/page titles are retained. Four-language localeConfig supports Android 13+ per-app languages; commands match translated/English names.

Four-locale home/dark Settings/command tests passed on API 36, including the four engines. A Compose Activity-management hang during locale changes was resolved by selecting the per-app language before launching the Activity; all four cases passed, and language settings were restored.

Raised custom-color primary/container contrast targets to 5.0 after anti-aliased text narrowly missed the rendered threshold.

Screenshots: [Japanese home](screenshots/locale-ja-home.png), [Korean Settings](screenshots/locale-ko-settings.png), [Chinese commands](screenshots/locale-zh-CN-commands.png), [English Settings](screenshots/locale-en-settings.png). See [localization](LOCALIZATION.md).

All 44 unit/42 device tests passed with no failures/skips. Debug/unsigned Release succeeded; Lint: 0 errors/25 warnings (new API-33 localeConfig notice). Physical devices, Android 8–15, and remote engine results were not verified.

## Space icons and theme color — 2026-10-04

Space editing adds emoji/Lucide modes, 24-item wrapping grids each, previews, and free-form emoji. Lucide IDs use `lucide:<name>` in the existing icon column; legacy emoji/symbols remain displayable.

Renamed accent color to theme color and applied it to backgrounds, panels, and selection. Retained DataStore key `accent_color` for existing values. Tested contrast in light/dark and extreme colors.

Debug/AndroidTest builds, 44 unit tests, and Lint (0 errors) passed. API-36 `AppearanceUiTest#customThemeColorPersistsAndRemainsReadableInBothThemes` and `#lucideAndEmojiPersistAcrossRecreationAndBothThemes` passed, covering persistence, Lucide restoration, pickers, and custom colors in both themes. No physical-device verification.

## Theme-derived base palette — 2026-10-05

Removed the fixed green tint before applying the selected theme color while preserving surface lightness. Backgrounds/panels/borders use the chosen color. Root/sidebar Space overlays were removed and the sidebar gradient now follows the theme. Space identity colors remain; stored theme settings/keys are unchanged.

Debug/AndroidTest, 45 unit tests, AppearanceUi (13), Lint, and diff checks passed with no failures/skips. Pixel coverage confirms red/blue sidebar colors even with a green Space, light/dark contrast, and recreation persistence. Unit tests ensure red/blue/gray surfaces have no fixed green tint. Inspected light-red/dark-blue captures in `app/build/reports/theme-base-validation/`. No physical-device proof.

## Rounded overflow menus — 2026-10-05

Unified page/tab/Favorite/Space menus with NagiOverflowMenu/NagiOverflowMenuItem: 24dp outer radius, 16dp inner radius after 8dp inset, 12dp vertical padding, ≥52dp items, 8dp card + 12dp item horizontal padding, surfaceContainerHigh, and 6dp shadow. Material hover/focus/press clips to inner corners. Icons/text align centrally; only delete/remove-Favorite uses error color. Tab/Favorite menus anchor to the ellipsis Box. Settings engine selection/right-pane selection were unchanged at this stage.

Retained standard [DropdownMenu](https://developer.android.com/reference/kotlin/androidx/compose/material3/DropdownMenu.composable) positioning, scroll, focus, and fade/scale motion. Rows grow for larger text.

OverflowMenuUi (3), BrowserUi (3), and SidebarDrag (5) passed together: 11 tests, no failures/skips. Coverage includes disabled items, click dismissal, Back, corner preservation under light/dark hover, 2× font row growth/wrapping/scroll, and existing right-click/drag. Corrected the Space-editor selector to the current “Custom emoji” label.

Inspected real menus, cards, hover, and enlarged text in `app/build/reports/overflow-validation/`. Enlarged-text tests double Popup LocalDensity at the component level, not the OS font setting. Physical/ColorOS behavior remains unverified. Debug/unsigned Release/AndroidTest, Lint (0 errors/25 existing warnings), and diff checks passed. No domain/persistence changes.

## Shared corner geometry (2026-10-05)

- Rectangular controls, search fields, tab/new-tab rows, favorites, Space/icon pickers, settings cards, command results, dialogs and overflow menus use `NagiShapes.Rounded` (12dp). The Material shape scale uses the same geometry; rectangular text buttons explicitly use it.
- The sidebar clips its gradient and children at its top-end/bottom-end corners, including its collapsed form. WebView content uses matching bottom corners.
- Clickable library rows, settings rows and command results clip press/hover feedback to the same shape as their visible backgrounds and borders.
- Circular icon buttons, switches and color dots retain their circular/capsule geometry. Small favicon tiles (6dp) and narrow drag/resize indicators (2–4dp) retain geometry appropriate to their size.
- Debug app/test builds and `git diff --check` passed. API-36 emulator validation covered 26 cases across AppearanceUiTest, OverflowMenuUiTest, LocalizationUiTest and SidebarDragTest: 25 passed in the combined run; the new corner/press case passed on rerun after waiting for the deferred scroll-container press indication. Its pixel assertions cover both sidebar end corners and clipped new-tab press feedback in light/dark modes.
- Inspected emulator screenshots for light/dark home, Chinese settings and dark overflow hover. Physical-device visual verification remains pending.

## Sidebar Space swipe (2026-10-05)

- Quick horizontal touch swipes across the sidebar select the adjacent Space in its saved order: left advances, right goes back, and endpoints do not wrap. Works with the sidebar expanded or collapsed.
- Direction locks after touch slop; vertical motion yields to scrolling. Long-press tab drags, immediate favicon drag handles, and mouse drags retain their existing behavior. A threshold distance and normal release are required; canceled/multitouch gestures do not select a Space.
- Debug app/test builds, APK signature verification and `git diff --check` passed. All 8 SidebarDragTest cases passed on the API-36 emulator, including switching from the new-tab button without creating a tab, quick tab-row swipes, canceled/vertical gestures, endpoint behavior, recreation persistence, collapsed sidebar, and existing mouse/touch drag operations. Inspected the resulting sidebar screenshot; physical-device verification remains pending.

## Responsive sidebar favorites (2026-10-05)

- Favorite tiles fill each row using equal widths. Column capacity comes from the available sidebar content width with a 72dp minimum tile width and 6dp gap; the column count is capped by the actual favorite count. At sidebar widths of 220/264/380dp this gives 2/3/4 columns. Incomplete final rows retain the same tile width as preceding rows.
- Debug app/test builds, APK signature verification and `git diff --check` passed. All 9 SidebarDragTest cases passed on the API-36 emulator, including measured two-item full-width filling, reflow at 2/3/4 columns, existing drag/drop and Space swipes. Inspected two-item and four-column screenshots.

## ChatGPT command-bar handoff (2026-10-05)

- Non-URL search input always offers the configured search engine and ChatGPT as the first two actions. ChatGPT comes first for 80+ Unicode code points, trailing question marks, or explanation/comparison/summary request markers in Japanese, English, Simplified/Traditional Chinese and Korean. Explicit search-engine keywords retain priority. URLs, unsupported schemes, empty input and `>` commands do not offer ChatGPT.
- Reuses the command bar's Up/Down and Enter handling, selected-row border/background, click/touch execution and IME composition guard. ChatGPT uses a lightning icon. Labels are localized in Japanese, English, Korean and Simplified Chinese.
- The action navigates to `https://chatgpt.com/?q=…`, encoding the question as one query parameter. No API key or page-content extraction is used. ChatGPT login, prompt prefill and submission remain provider-controlled; actual remote prompt acceptance/answer generation and physical-device behavior were not verified.
- Debug app/test builds and all 54 unit tests passed, including 5 new policy/encoding cases. API-36 emulator: 2 new component tests passed (arrows, Enter, touch, query-change selection reset), 3 existing omnibox CJK composition tests passed on the targeted rerun, and 1 real MainActivity handoff test passed (active tab URL updated, command bar dismissed). Inspected light/dark screenshots under `app/build/reports/chatgpt-validation/chatgpt-validation/`.
- The initial combined run had a Japanese test-IME binding failure (passed on targeted rerun) and the unrelated existing settings-engine-name test failed because its `Add search engine` selector assumes the previous settings layout. That test was left unchanged; its settings-editor coverage is not claimed here.
- Debug lint passed with 0 errors and 25 warnings; Release (unsigned) build and `git diff --check` passed.

## Common search engines (2026-10-05)

Separated the engine registry from common suggestions. Unconfigured installs use Baidu + Qwen in mainland China and Google + ChatGPT elsewhere. Settings checkboxes and the ChatGPT switch persist the combination; manual changes disable regional automation. Keywords for non-common engines are treated as ordinary search text.

All 61 unit tests and seven targeted API-36 tests passed: CommonSearchEnginesUiTest (1), ChatGptCommandBarUiTest (2), LocalizationUiTest (4). Debug/Release builds succeeded; Lint had 0 errors/25 warnings. Coverage includes persistence/recreation, suggestion inclusion/exclusion, ordinary search for an unselected 360 keyword, and four-locale Settings. Physical devices and mainland-China networking were not tested. Screenshots: `app/build/reports/common-search-validation/`.

## 2026-10-06 — drag tabs into split view and filled theme presets

- A sidebar tab can be dropped into the left or right half of the browser area. The drop target highlights and the tab preview follows the pointer outside the sidebar. Existing split panes can be replaced or swapped; canceled gestures leave the layout unchanged.
- The settings header uses the localized Settings title. Theme preset buttons use their own color across the button, with contrasting labels and a check on the selected preset.
- Verified with an isolated clean build and an API 36 tablet emulator: debug app/test APKs, unit tests, and lint passed. Two split-drop UI tests covered touch, mouse, cancellation, left/right placement, replacement, and swapping. Two existing sidebar drag tests passed. The preset UI test checked all six colors, selection, and screen-pixel text contrast in light and dark themes.

## 2026-10-06 — Sidebar split pair

- When two panes are visible, the expanded sidebar groups their tabs in a single rounded card, with the left page on the left and the right page on the right. Equal-width tiles show each favicon and a single-line ellipsized title. The group occupies the left tab's section; the right tab is not duplicated elsewhere.
- Clicking a grouped title focuses that pane without replacing either page. Clicking its icon opens the existing tab actions; favicon-handle and whole-tile dragging remain available. Closing one page restores the ordinary sidebar row.
- Debug app/test builds, lint, and diff whitespace checks passed. Three targeted SidebarDragTest cases passed on an isolated API-36 tablet emulator, covering grouped geometry/focus, mixed pinned state, right-page closure, drag cancellation, left/right placement, replacement, and swapping. The group test was rerun after adding a wait for actual dark-mode/narrow-width rendering and passed. Inspected light and dark 220dp-sidebar screenshots in `app/build/reports/split-sidebar-validation/`. Physical-device validation is not included.

## 2026-10-06 — Split pane title alignment

- Both pane title rows reserve 48dp and center their contents vertically, matching the right pane's tab picker. The progress/divider slot reserves 2dp so independent loading states do not shift the page content.
- Debug app/test builds and `git diff --check` passed. API-36 tablet emulator: existing split creation/resize/swap/close and sidebar split grouping/focus tests passed (2 cases). Inspected light/dark screenshots in `app/build/reports/split-title-validation/`, including a loading right page beside an idle left pane. Physical-device validation is not included.


## 2026-10-06 — Back from newly opened tabs

- System Back and the Back keyboard shortcut use the focused page history first. At the start of a normal tab's history, if another visible tab exists in the space, Back closes the focused tab and selects its opener. A missing/closed/archived/moved opener falls back to a remaining tab in the same space. The final visible tab and pinned tabs retain their existing exit behavior when no page history exists; fullscreen Back still exits fullscreen first.
- Selected new tabs remember the previously selected tab; page-created tabs explicitly remember their source engine's tab, including split panes. Parent IDs are persisted with a Room version 1-to-2 auto-migration that preserves existing data. Closing an active tab also prefers its live opener.
- Debug app/test builds and lint passed. All 78 unit tests passed, including nested openers, unrelated neighboring tabs, an unselected split child, unavailable parents, and a populated version-1 database migration. API-36 tablet emulator: all three TabBackUiTest cases passed using device-local pages, covering a real target=_blank link, open-links-in-new-tab with Activity recreation, and in-tab history before child closure. Physical-device gesture validation is not included.
## 2026-10-06 — Address action spacing and Space selection frame

- Both address bars use 32dp-wide copy/share buttons instead of 48dp, with unchanged 16dp icons. Space selection frames use 36dp squares inside the existing 48dp click/drop targets.
- Debug app/test builds and `git diff --check` passed. API-36 tablet emulator: SidebarAddressBarTest and the existing Space tap/drag-to-Space cases passed (3 tests). Inspected the rendered address bars and selection frame in `app/build/reports/address-spacing-validation/sidebar-address-always-visible.png`. Physical-device verification is pending.

## 2026-10-06 — 0.1.1 release validation

- Unit tests: 98 passed, including update manifest validation, APK download/checksum/signature/version checks on API 26 and 35, and file-picker extension contracts.
- API-36 tablet emulator: all 91 cross-feature instrumentation tests passed. Includes right-pane keyboard focus, visible-page archive protection, bookmark flows, confirmed/canceled site-data clearing, real WebView link/image context menus, cookie/localStorage/IndexedDB removal, and stalled-navigation timeout/recovery.
- After including the address-action and Space-frame UI changes, all 17 SidebarAddressBarTest/SidebarDragTest cases passed again.
- Signed, non-debuggable Release build succeeded. Debug Lint: 0 errors, 34 warnings. These checks used the emulator; no physical device was connected.
- Installed the actual 0.1.0 APK distributed at takeruf.com/nagi (code 1, certificate SHA-256 a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc), seeded a named Space and Bookmark, and installed the signed Release with `adb install -r` without uninstalling. Android reported 0.1.1/code 2, and both records remained visible in the running Release UI.

- Live GitHub manifest/APK and takeruf.com/nagi download were re-fetched; both APKs matched SHA-256 `cda3dee9a7aaa83268f745b67a2599dc4774219de824c51735fc42bcc42959f9` and the published 0.1.0 signing certificate.
- A QA client using the updater code with versionCode 1 and the distribution signature detected the public release, downloaded and verified it, opened the unknown-source setting, and completed Android's Update confirmation. The installed app reported 0.1.1/code 2; the named Space and Bookmark remained visible. The normal Release then displayed “You have the latest version.” Initial emulator DNS failures were resolved by restarting the QA emulator with explicit DNS servers; no update transport was replaced or bypassed.

## English documentation and refreshed README screenshots — 2026-10-06

Repository Markdown, release notes, and update-manifest notes use English. CJK strings remain only as literal IME test examples. README screenshots were captured from the signed 0.1.1 Release on the API-36 emulator: light/dark home, Split with two locally hosted demonstration pages, and the live updater showing the latest version. Images were inspected, and relative document/image links were checked.

## 2026-10-06 — Google Play / GitHub distribution separation

- Added `github` and `play` distribution flavors, retaining `com.takeruf.nagi` and the existing database.
- GitHub: 98 unit tests passed. Play: 87 shared unit tests passed. The 11 updater tests remain GitHub-only because Play does not include APK update transport or installation code.
- Signed GitHub Release APK and Play Release AAB built. Release Lint: GitHub 0 errors / 35 warnings; Play 0 errors / 46 warnings.
- Inspected the final Play AAB's manifest and DEX: install permission, updater FileProvider, GitHub update-manifest URL, unknown-source settings action, and APK-install MIME marker were absent.
- API-36 tablet emulator: both ReleaseFeaturesUiTest cases passed for Play, covering Bookmarks, site-data clearing, Google Play update entry, missing APK-update entry/install permission, and the privacy-policy link.
- Both ReleaseFeaturesUiTest cases also passed for GitHub, confirming its APK-update entry remains available and the common policy link is visible.
- The emulator initially contained the distribution-signed build, so the default debug-signed install was rejected before tests ran. QA Debug and test APKs were signed with the existing distribution key through a temporary Gradle init script, preserving installed data; normal Debug signing configuration in the repository remains unchanged.
- Privacy policy source and Console/store drafts: `docs/PLAY_CONSOLE.md`. The policy website uses four separately authored locale bodies under takeruf.com/nagi/privacy. No Play Console submission or approval is claimed.
- Website PR https://github.com/TakeruF/me/pull/20 merged as `7253930`. Production checks returned HTTP 200 with the correct body and canonical URL for all four policy locales; the root locale redirect and product-page policy link also passed. Chrome confirmed the published Japanese page.

## 2026-10-06 — 0.1.2 release validation

- JDK 17: 112 GitHub and 101 Play unit tests passed. Updated the updater test fixture to always offer a newer candidate than the compiled version, retaining signature/checksum/downgrade checks.
- Signed Github Release APK and Play Release AAB built. Release Lint: GitHub 0 errors / 40 warnings; Play 0 errors / 51 warnings.
- APK: com.takeruf.nagi, 0.1.2/code 3, API 26+, non-debuggable; distribution certificate remains a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc. SHA-256: 61dbb8ad6cbe12c1450be33a896999ca7622f2f37a1257bb2d3effd370283e22.
- bundletool validated the Play AAB; manifest/DEX inspection confirmed version 0.1.2/code 3 and exclusion of the APK updater, installer permission, update provider and unknown-source action. Play AAB SHA-256: a1c14c534ad3029d0bf91d8d513fe7fb26a97bc9532830f93efe3d8d2e13092a.
- The existing Console Alpha track was confirmed Active, serving 0.1.1 to selected testers in 178 countries/regions, with 4 testers opted in. The 0.1.2 update uses this same track and its existing Google Group/opt-in links.
- API-36 / WebView 133 tablet emulator: 49 of 51 selected cases passed in the combined WebViewEngineTest, ReleaseFeaturesUiTest, SidebarDragTest and SplitNewTabUiTest run. The autofill case timed out after offering the system suggestion; the update-section case still asserted the old version text. Changed that assertion to BuildConfig.VERSION_NAME. Both cases passed in a separate two-case rerun (4.926 seconds), completing successful coverage of all 51 selected cases. No production code changed in response to these two failures.
- A separately booted Nagi_Features_QA emulator encountered a System UI ANR before the targeted rerun; that interrupted run is excluded from pass counts. The existing emulator retained its data and restored its original autofill service and IME through the test harness.
- GitHub APK was packaged again from clean committed release source e50190b and publicly re-downloaded with matching manifest, size, checksum and distribution signature. The uploaded Play AAB has identical executable code, resources and application manifest to the clean-source rebuild; only build-time Git provenance and the signed JAR metadata differ. The immutable uploaded artifact's checksum above remains the authoritative Play checksum.
- Website PR #21 merged and production checks verified all four product/privacy locales, SHA-256 text and the downloaded APK; old 0.1.0/0.1.1 downloads remain HTTP 200. Site/GitHub APK SHA-256: 61dbb8ad6cbe12c1450be33a896999ca7622f2f37a1257bb2d3effd370283e22.
- Play 0.1.2 was submitted and shows Changes in review / quick checks running. Afterwards Alpha remained Active, 0.1.1 remained Available to selected testers, and the tester group, feedback address, 178 countries/regions and opt-in links were verified unchanged.

## 0.1.3 release verification — 2026-10-06

- JDK 17: GitHub 112 and Play 101 unit tests passed; Release Lint has zero errors (40/51 warnings). Both signed artifacts built successfully.
- API-36 tablet emulator: all 8 BrowserUiTest/ReleaseFeaturesUiTest cases passed after correcting the lazy-list scroll in the search-settings test. The first run failed only because it tried to find an off-screen, uncomposed row.
- GitHub APK was rebuilt from clean committed source `59a8d66`: com.takeruf.nagi, 0.1.3/code 4, API 26+, non-debuggable, existing distribution certificate. SHA-256: d6ac27a0c91edd6f3eecc76dfdab0fa9857b6e97a4cc9771ddebfd281980c918.
- Installed signed 0.1.2 followed by 0.1.3 using `adb install -r`; launch succeeded and the existing Favorites and Spaces remained visible. Bookmark navigation is absent. This was emulator verification, not physical-device testing.
- Play accepted the signed 0.1.3/code 4 AAB with the existing upload key. bundletool validation and manifest inspection passed; the APK installer permission/provider remain excluded. Uploaded AAB SHA-256: edd15232849edeb9c57f91ce3e17a96bfac166bd231e3c4d39f79d3a20a5ce6a. This artifact was built from the same release application source before the release commit. A clean build succeeds, but byte-level comparison also differs in R8 output and resource metadata; it is not a byte-identical rebuild.
- Play update submitted in the same Alpha track. Afterwards Alpha remains Active, 0.1.3 is In review, and 0.1.2 remains Available to selected testers. Existing Google Group, feedback address, 178 regions and both opt-in links were verified unchanged. Dashboard count increased from 5 to 6 opted-in testers during this operation.
- Website PR #22 merged as `805f071`; CI and preview/main Vercel deployments passed. EdgeOne production was independently checked at takeruf.com in all four product, homepage and privacy locales. Downloaded site/GitHub APKs match SHA-256 d6ac27a0c91edd6f3eecc76dfdab0fa9857b6e97a4cc9771ddebfd281980c918; older APK URLs remain HTTP 200. Live rendered screenshot: [website 0.1.3](screenshots/website-013-live.png).
- Site publication exposed two build-time Google Fonts parsing errors. Existing font subsets, weights, Unicode ranges and fallback metrics were bundled with upstream OFL license texts; four webpack exports and 60-page validation passed. All 126 bundled font references per locale were checked. This repair is scoped to the website release worktree, preserving the original website checkout's unrelated work.
- Final Play refresh confirms automated checks finished and 0.1.3 remains Changes in review. This does not claim approval or 0.1.3 tester delivery.


## Video PiP and pop-out playback — 2026-10-10 (unreleased)

Settings → Video has two independent persistent switches, both enabled by default for new and existing installations: automatic PiP on Home, and in-browser pop-out video playback. English, Japanese, Simplified Chinese, and Korean labels are included.

Playing HTML5 videos have a pop-out assistant button. Fullscreen video also has a pop-out action. Both keep the existing WebView custom view, including videos in cross-origin frames, instead of extracting video URLs or creating a second player. The popup supports dragging, size changes, play/pause, fullscreen, returning to its tab, and closing. Size and position survive fullscreen/PiP round trips. Its source tab stays visible to the engine and protected from automatic suspension/archive across tabs, Spaces, and settings. Closing the source tab, resetting site data, or closing the popup releases the presentation; closing the popup pauses playback.

System PiP uses manual entry on Android 8–11 and automatic entry on Android 12+, with playback gating, aspect-ratio bounds, a cropped video transition rectangle, and a play/pause RemoteAction. Android 15+ hides non-video UI at transition start. Generic page fullscreen and paused videos do not automatically enter PiP. Configuration changes retain the Activity so the original player survives size/orientation changes. Implementation follows [Android's Compose PiP setup](https://developer.android.com/develop/ui/compose/system/pip-setup) and [PiP playback lifecycle guidance](https://developer.android.com/develop/ui/views/picture-in-picture).

JDK 17 / SDK 36: `:app:assembleGithubDebug`, `:app:assembleGithubDebugAndroidTest`, `:app:testGithubDebugUnitTest`, and `:app:lintGithubDebug --no-parallel` passed. Unit tests: **138 passed**, including independent settings persistence and controller eligibility/ownership/reentrant cleanup/aspect bounds at API 26 and 35. Lint: **0 errors, 44 warnings**; no new warnings in the video implementation.

On the dedicated API-36 `Nagi_Video_QA` emulator, **VideoUiTest: 5 passed** at the phone's native size/density, then **BrowserUiTest: 3 passed** at a 2560×1600 / 240-dpi tablet layout. The earlier phone run of BrowserUiTest failed two assertions that require the expanded tablet sidebar; both passed at the intended size. Video tests cover real playback-time advancement across Home/PiP/restore, the actual RemoteAction PendingIntent for play/pause, popup dragging/resizing, size retention after PiP/fullscreen, tab and Space changes, cross-origin iframe playback, source-tab closure, paused/non-video exclusion, independent off switches, and settings after Activity recreation.

Local evidence is under `app/build/reports/video-validation/`: `video-final-instrumentation.txt`, `tablet-regression-instrumentation.txt`, `apk-sha256.txt`, and three inspected screenshots (`video-fullscreen.png`, `video-system-pip.png`, `video-popup-other-tab.png`). The installed app APK's SHA-256 matched the final Debug build. These prove this emulator and local HTML5 fixtures, not production website compatibility.

The device-local fixture uses a generated six-second looping H.264 video; its cross-origin iframe uses localhost versus 127.0.0.1. Regenerate `app/src/androidTest/assets/video.mp4` with:

```sh
ffmpeg -f lavfi -i testsrc2=size=320x180:rate=15 -t 6 -an -c:v libx264 -profile:v baseline -pix_fmt yuv420p -crf 32 -movflags +faststart video.mp4
```

Physical devices, Android 8–15 system PiP transitions, production video-site policies, DRM, and embedded players inside closed shadow roots remain unverified. The assistant requires an HTML5 video and fullscreen permission; its failure message directs users to the site's fullscreen button. No release was published.
