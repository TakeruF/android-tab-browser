# Arc references and refinements

Research and implementation date: 2026-10-03. Scope: the Android tablet sidebar and regional search defaults. This is a historical record; current release validation is in [VALIDATION.md](VALIDATION.md).

## References

- [Arc: Favorites](https://resources.arc.net/hc/en-us/articles/19230755904151-Favorites-Top-Tabs-Across-Every-Space): top placement, shared across Spaces, up to 12 items. Drag upward to create a Favorite and downward to remove it.
- [Arc: Pinned Tabs](https://resources.arc.net/hc/en-us/articles/19231060187159-Pinned-Tabs-Tabs-you-want-to-stick-around): Space-specific pins, pin/unpin by crossing a divider, and a collapsible pinned section.
- [Arc: Spaces](https://resources.arc.net/hc/en-us/articles/19228064149143-Spaces-Distinct-Browsing-Areas): bottom icons and per-Space colors/icons.
- [Sidebar examples](https://dannyspina.com/blog/arc_browser): visual reference for search, icon tiles, restrained headings, dividers, subtle selection, and bottom Space placement. External images are not bundled in the app.
- [Android pointer input](https://developer.android.com/develop/ui/compose/touch-input/pointer-input): touch/mouse input and event forwarding.
- [Country API](https://github.com/lineofflight/country): IP country code through HTTPS GET without an API key.

## UI and interaction changes

| Before | After | Purpose |
| --- | --- | --- |
| Drag only from icons; Move up/down menus | Hold the whole row; immediate mouse/icon dragging | Grab the tab directly |
| Entire destination row colored | Insertion line, lifted preview, dimmed source | Show the destination |
| Cancel still moved the tab | Save only on drop, never on cancellation | Preserve order when interrupted |
| Reorder without pin changes | Cross pinned/ordinary sections, including empty sections | Organize with the divider |
| No off-screen destinations | Edge auto-scroll with sidebar-owned gestures | Continue after row recycling |
| Space-specific Favorites with titled cards | Shared icon tiles; drag into/out of Favorites | Distinguish Favorites from pins |
| Top Space cards and temporary cross-Space lists | Persistent bottom icons and drop-to-move | Use the same destination for switching and movement |
| Fixed initial colors/icons | Editable names, colors, and icons | Identify Spaces visually |
| Large introductory logo and duplicate Favorites | Quiet new-tab screen with subtle background/search | Prioritize browsing content and sidebar |
| Animated resizing lag | Direct finger tracking and smaller frame radius/padding | Reduce continuous-input delay |

Room transactions persist moves. The insertion index is calculated after removing the source, including downward moves. Favorite order persists, and deleting a Space retains shared Favorites.

## Regional search defaults

Fresh installs enable automatic detection; older saved defaults remain manual.

1. Immediately apply a fallback from an available device country code.
2. Request `https://api.country.is/` without blocking startup. Use only `country`; do not store or log the returned IP.
3. Prefer IP results, falling back to mobile network, SIM, then device region. Chinese language alone is insufficient.
4. `CN` selects Baidu; other countries select Google. HK/MO/TW are not treated as `CN`. If no country is available, retain the current engine.
5. Cache IP results for 24 hours and fallback/failures for one hour. Check on launch/foreground; “Check again” forces refresh.
6. Manual selection disables automatic mode, including when a lookup is in flight; late results cannot overwrite it.
7. Add Baidu to existing data once. Do not restore it after deletion; missing required engines leave the current choice intact.

No queries, browsing history, device IDs, or explicit IP parameters are submitted. The HTTPS host sees the source IP. Disabling automatic mode stops new lookups. A VPN can change the detected country. Mainland-China network reachability and roaming/SIM behavior remain unverified.

## Scope and remaining differences

Favorites are shared local shortcuts, not persistent app-like tabs that preserve the original WebView's form/history state. Folders, resetting to pinned URLs, Peek, multi-tab dragging, Space drag ordering, and Arc Sync are not implemented.

## Historical validation

API-36 Pixel Tablet emulator `Orbit_Tablet_QA`, 2560 × 1600 / 320 dpi / landscape.

| Check | Result |
| --- | --- |
| Debug / Release APK | Successful; Release was unsigned at this stage |
| Unit tests | 34 passed; no failures/skips |
| Android Lint | 0 errors |
| Instrumentation tests | 16 passed |
| Drag | Whole-row hold, immediate mouse drag, insertion, pin/unpin, cancellation, Favorite conversion/removal, Space movement, recreation, long-list auto-scroll |
| Regression coverage | Browsing, history, Bookmarks, tab restoration, Split, engine editing, CJK composition, WebView features |
| Region policy | Injected CN/HK/MO/TW/JP codes; IP priority, fallback, failures, caching/retry, concurrent manual choices, deleted-engine preservation |
| Rendering | Captured and inspected Compose screens, previews, insertion lines, and settings |

Injected country tests do not establish real mainland-China IP detection or network access. Physical touch/mouse validation is not included.

Screenshots: [sidebar](screenshots/arc-sidebar.png), [drag](screenshots/arc-sidebar-drag.png), [regional search](screenshots/regional-search-settings.png), [Space editor](screenshots/arc-space-editor.png).

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease \
  :app:testDebugUnitTest :app:lintDebug
adb -s emulator-5554 shell am instrument -w -r \
  -e class com.takeruf.nagi.SidebarDragTest,com.takeruf.nagi.BrowserUiTest,com.takeruf.nagi.CjkInputTest,com.takeruf.nagi.WebViewEngineTest \
  com.takeruf.nagi.test/androidx.test.runner.AndroidJUnitRunner
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Reports: `app/build/reports/tests/testDebugUnitTest/` and `app/build/reports/lint-results-debug.html`. Final instrumentation output was `OK (16 tests)`.
