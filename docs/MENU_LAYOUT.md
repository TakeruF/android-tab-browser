# Menu layout

Other app surfaces share the same compact-view preference for
[input-aware UI spacing](UI_DENSITY.md).

App-owned Compose menus share `NagiOverflowMenu` and `NagiOverflowMenuItem`.
This covers page actions, tabs, Spaces, Favorites, search-engine defaults,
blocking controls, translation options, translation language selection, Theme
and Compact interface. The last two show the selected value and a chevron on
the right of the settings row, with a check beside the current menu item.

Density follows the Compact interface three-choice setting in Appearance,
shared with the Command Bar and other chrome. Always off, With a physical
keyboard (the default), and Always on are persistent, reselectable policies. Shortcut hints still reflect real keyboard availability.
Window width and software keyboard visibility do not select density. Open
menus update in place when the effective spacing changes.

| Dimension | Compact off | Compact on |
| --- | --- | --- |
| Minimum / maximum width | 240 / 320 dp | 200 / 280 dp |
| Minimum row height | 48 dp | 36 dp |
| Horizontal / vertical row padding | 12 / 12 dp | 8 / 6 dp |
| Icon size / icon-text gap | 20 / 12 dp | 18 / 8 dp |
| Divider vertical padding | 4 dp | 2 dp |

Both densities retain the same text style and nested corners: 12 dp row radius,
8 dp card inset, and 20 dp card radius. Width is bounded rather than fixed;
long labels wrap and larger accessibility text grows rows. Page zoom and
blocking status use the same dimensions as the surrounding actions. Tab-menu
width follows the shared policy instead of the saved sidebar width.

Below the 600dp browser-window boundary, the page menu uses a Chrome-style
right-aligned popup overlapping the actual bottom toolbar by 12dp. Its width
is 72% of the window, capped at 280dp, with an 8dp right gap and 24dp corners.
The available height excludes system bars and the toolbar and is capped at 640dp. This width policy
is specific to the compact page menu; other popups retain the shared width
limits above.

Page tools and New tab/History/Settings use a scrolling icon/text list. The
five common Back/Forward/Copy link/Share/Reload buttons stay fixed at the popup
bottom and remain visible while the list scrolls. Copy and Share have no
duplicate text rows, and the fixed row has no Pin button. They stay square,
up to the shared menu row height (48dp normally, 36dp compact), shrinking equally
to fit five buttons in narrow popups without widening the popup. There is no
separate navigation footer, handle, dimmed sheet or extra Close button. Outside
taps, Android Back and the bottom menu trigger dismiss the popup. Text rows keep
the selected shared spacing policy. The tab sheet keeps its direct tab Close
buttons. Wide page menus retain their existing anchored popup.

Tab rows in both the wide sidebar and compact tab sheet omit the separate
three-dot action button. Their direct Close button, secondary-click menu,
accessibility actions and drag operations remain available. Wide sidebar
Close buttons retain their existing selection/hover visibility and sizing.

## Page text selection

Secondary mouse/trackpad clicks in a WebView use the same native finger-long-press
selection path as touch. This keeps the horizontal floating selection toolbar,
native Copy/Share/Select all and installed text-processing actions instead of
WebView's tablet mouse dropdown. The browser does not extract page text into a
custom selection menu. Link/image actions still use Nagi's existing context
actions; secondary clicks do not begin a link/image drag. Native selection and
toolbar contents remain dependent on the Android/WebView provider. The menu
appears after Android's configured long-press interval; this is native input
delegation rather than an app-rendered replacement toolbar.

`NativeSelectionMenuTest` compares touch and secondary-click floating ActionMode
and Copy, including an existing multiword selection, editable draft and a click
outside the selected text. These checks require actual WebView instrumentation;
physical mouse/trackpad and OEM toolbar validation are separate.

The Command Bar also routes secondary clicks to the native horizontal toolbar
used for touch selection, rather than the Compose mouse dropdown. This applies
to phone, wide/Split and centered search layouts. Compose retains ownership of
the selected range, text editing, clipboard and installed text-processing
actions; a right click does not replace the selection with a simulated long
press. Provider/OEM-specific actions and styling remain Android-owned.
`CommandBarSelectionMenuTest` checks actual mouse/touch input, floating ActionMode,
preserved full/partial selections, and native Copy/Cut/Paste without navigation.

In the normal page WebView and Command Bar, completing a mouse/trackpad text
selection also opens the horizontal toolbar, without a secondary click.
Collapsed selections and ordinary clicks do not request it. Page selection
still uses WebView's native selection/actions and configured long-press timing;
the Command Bar requests its existing Compose toolbar after primary release.
Pointer-type touchpad events are normalized for WebView selection, while
trackpad scroll events keep their existing history/scroll handling.

The page observer reports only the selected region's geometry from the frame
that received a trusted pointer release. It measures editable selections locally
without sending text to Android, excludes password fields and active composition,
and supports child frames through document-start injection when the provider
supports it. Android accepts at most one bounded geometry message after a native
primary release. Navigation, detachment, destruction and a new gesture invalidate
pending requests. Native Copy/Share/installed processing actions remain provider
owned. No custom page-selection toolbar is injected.

### Selection audit (2026-11-27)

Outside the Command Bar, 12 Compose input fields and one selectable text region
still use Compose's default mouse dropdown. This audit identified the following
remaining sites; it did not change their production code:

| Surface | Fields/regions | Evidence |
| --- | --- | --- |
| [Find in page](../app/src/main/java/com/takeruf/nagi/ui/browser/BrowserPane.kt) | 1 | Actual component mouse input and screenshot |
| [History search](../app/src/main/java/com/takeruf/nagi/ui/library/LibraryScreen.kt) | 1 | Source; String-based OutlinedTextField |
| [Favorite editor](../app/src/main/java/com/takeruf/nagi/ui/sidebar/FavoriteEditor.kt) | 2: name, URL | Name reproduced; both use the same state-based field path |
| [Search-engine editor](../app/src/main/java/com/takeruf/nagi/ui/settings/SettingsScreen.kt) | 4: name, keyword, URL template, icon URL | Source; String-based OutlinedTextField |
| [Space editor](../app/src/main/java/com/takeruf/nagi/ui/sidebar/Sidebar.kt) / [custom emoji](../app/src/main/java/com/takeruf/nagi/ui/components/SpaceIcons.kt) | 2 | Source; String-based OutlinedTextField |
| [Custom color](../app/src/main/java/com/takeruf/nagi/ui/components/ThemeColorEditor.kt) | 1 | Actual component mouse input and screenshot |
| [Lucide code](../app/src/main/java/com/takeruf/nagi/ui/components/LucideIconEditor.kt) | 1 | Actual component mouse input and screenshot |
| [License text](../app/src/main/java/com/takeruf/nagi/ui/settings/LicensesScreen.kt) | 1 | Actual SelectionContainer mouse input and screenshot |

The separate [Reader WebView](../app/src/main/java/com/takeruf/nagi/ui/browser/ReaderPane.kt)
also lacks the normal page's secondary-click/automatic-selection adapter. This is
a source-confirmed coverage gap, with its runtime mouse menu still unconfirmed:
the audit harness's initial Reader input attempt timed out. Its JavaScript-disabled
rendering is outside the normal page observer above.

Five actual production components reproduced the dropdown in an ignored,
investigation-only instrumentation source set. Screenshots/logs are under
`artifacts/selection-menu-audit-20261127/`. This is separate from whole-app
navigation, physical-input and OEM-provider verification.

`OverflowMenuUiTest` checks live density changes, restoration of touch targets,
disabled actions, dismissal, hover corners, and reachable large-font actions.
Controlled `LocalConfiguration` values establish configuration-driven behavior;
actual physical-keyboard attachment and OEM configuration reporting require
separate device verification. See [validation](VALIDATION.md).

Local validation on 2026-11-19 passed all four `OverflowMenuUiTest` cases at
both phone and tablet widths (eight executions), plus five selected translation,
native page-zoom and Favorite-editor UI cases on a dedicated API-36 emulator.
Debug/test APK builds and Play Debug lint passed. The initial density-test
harness mixed up the software- and hardware-keyboard visibility fields; its
corrected final runs passed. Evidence is retained under ignored
`artifacts/menu-density-20261119/`. The full instrumentation suite, Release lint,
Split-specific menu scenarios and physical-device attachment were not rerun.
