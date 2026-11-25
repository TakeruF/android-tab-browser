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
