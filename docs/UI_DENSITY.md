# Input-aware UI density

Settings → Appearance → Compact interface uses a current-value button with a
chevron and a three-option anchored menu: Always off (OFF), With a physical
keyboard (AUTO), and Always on (ON). AUTO is the default for new, missing or
unknown stored values. Users can return to AUTO after an explicit override;
existing choices are retained. DataStore writes remain atomic.
Theme uses the same current-value menu for System, Light and Dark, with a check
beside the selected option. Use the new design remains a separate binary switch.
The app theme provides the effective policy through `LocalNagiUiDimensions` to
chrome, overflow menus and Command Bar suggestions. Shortcut hints continue to
reflect actual hardware availability independently of spacing. Window-width layout boundaries remain 600 dp
for browsing and 760 dp of available content for Settings.
Normal and private Activities handle keyboard, keyboard-visibility and navigation
configuration changes in place, so attaching input hardware does not require
recreating their live browsing sessions.

| Element | Compact off | Compact on |
| --- | --- | --- |
| Collapsed wide sidebar | 72 dp | 52 dp |
| Common icon-button area | 48 dp | 36 dp |
| Browser toolbar | 56 dp | 48 dp |
| Tab rows | at least 48 dp | at least 40 dp |
| Favorite tiles | 58 dp | 48 dp |
| Settings category rows | at least 56 dp | at least 40 dp |
| Wide Settings category column | 236 dp | 236 dp |
| Settings item rows | at least 64 dp | at least 48 dp |
| Settings horizontal row padding | 20 dp | 12 dp |
| Settings section spacing | 16 dp | 12 dp |

Settings, search-engine customization, licenses, history, the sidebar/tab sheet,
new-tab search/resume cards and browser controls share this policy. Controls and surrounding spacing shrink
together. Browser notices and snackbar clearance follow the current toolbar
height. [Menus](MENU_LAYOUT.md) and [Command Bar](COMMAND_BAR.md) retain their
own content-specific dimensions.

Text size, font scaling, corner radii, editor contents, saved choices and
website zoom are independent of chrome spacing. Rows with long or enlarged
text can grow beyond their minimum height. Keyboard delivery, focus and text
composition continue to use the existing controls. Mode or keyboard changes do not
recreate the navigation hierarchy or WebViews. An active sidebar drag is
cancelled when density changes because its cached pointer rectangles are no
longer valid; cancellation does not reorder tabs.

`HardwareKeyboardUiTest` delivers controlled Android keyboard configurations to
the real app view tree at phone/light and tablet/dark widths. It checks Settings
navigation, dimensions, toggle values, search-engine editor draft/focus, history
query text, live WebView identity and document/draft retention (including Split).
`UiDensityAccessibilityTest` checks growing CJK/supporting text and Enter
activation with enlarged fonts. These tests establish configuration-driven
behavior; physical-keyboard attachment and manufacturer reporting remain
separate device checks. See [validation](VALIDATION.md).

Local verification on 2026-11-23 passed both app-level keyboard cases and the
enlarged-text/keyboard-activation case, plus nine existing Settings navigation,
compact drag, browser-notice, menu-density and Command Bar cases on the dedicated
API-36 emulator. Debug/test APK assembly and Play Debug/Release lint passed
(zero errors, 96 warnings each). The initial focus harness was in touch input
mode; the corrected test explicitly selects keyboard input mode before Tab and
Enter. Screenshots were visually inspected and retained with logs and a final
artifact hash in ignored `artifacts/ui-density-20261123/`. This UI-only follow-up
did not rerun the JVM or full instrumentation suites, physical hardware or a
release.

`CompactUiSettingsTest` covers all three policies, automatic defaults for
existing/unknown preferences, concurrent unrelated edits and disk reopening.
`CompactUiSettingUiTest` uses both three-option menus at phone
and tablet widths, including selecting AUTO again after manual overrides, theme
changes and opposite keyboard availability,
selection after reopening Settings, fixed category width and live Split/draft
retention. The menu and Command Bar keyboard tests also exercise explicit
on/off overrides while their controls remain open. See the
[2026-11-24 validation](VALIDATION.md#compact-preference-and-background-editor-2026-11-24-local)
for executed checks and device limits.
