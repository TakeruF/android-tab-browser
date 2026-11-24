# Command Bar

Open the Command Bar with Ctrl / Command + L or the browser's search control.
Use the new design is enabled by default and adapts to the actual window width.

Below 600 dp, search takes over the safe viewport with an opaque theme surface.
The active field sits at the top with 8 dp side margins and 4 dp top spacing;
its height is 48 dp, or 40 dp with compact spacing. Results fill the area below
it and scroll above the software keyboard. This applies to both resting top and
bottom address bars; dismissal restores the chosen resting placement. The close
icon, Back or Escape dismisses search. Tapping empty space in this search screen
does not operate or dismiss the page underneath.

Touch opening translates the unscaled input from the tapped field to the top
while the search background and results fade in. The new-tab page supplies its
actual search-field bounds, so its center search field moves along that same
path. Close/Back/Escape returns toward the source while hiding the IME. Both
phases take 200 ms with the existing ease-out curve (`0.23, 1, 0.32, 1`). Keyboard
shortcut entry is immediate. Compose honors Android's animator duration scale,
including zero duration. Query edits do not restart motion; navigation executes
immediately. The input keeps its focus, selection and composition through live
layout, theme, compact-spacing and window-size changes.

At 600 dp or wider, search opens at the current page's actual address field,
retaining its 40 dp height, width, text style and surface color. Suggestions
unfold downward over 200 ms, without scaling text or moving the input. The
surrounding toolbar stays visible and the page keeps its normal brightness.
Tap outside, use the close icon or press Escape to dismiss. An inactive Split
pane's field edits that pane's URL; keyboard and sidebar entry use the focused
pane. Outside browsing the dropdown falls back to the safe top edge. The input
stays in place when results become empty. Dialog-window animation is disabled.

Suggestion density follows Settings → Appearance → Compact interface: Always
off, With a physical keyboard (AUTO, the default), or Always on. Compact rows
are at least 40 dp; standard phone rows are at least 48 dp and wide-window rows
at least 56 dp. Multiline entries can grow. AUTO follows actual physical-keyboard
availability, while explicit choices override it. Software keyboard visibility
alone does not select density. Other [app menus](MENU_LAYOUT.md) share this
policy, and shortcut hints still reflect actual hardware availability. Searching
does not reload, resize or recreate the background WebView.

Suggestions show their icon and title, with URLs below open tabs and history
entries. Open tabs use the same theme-aware site favicon as the sidebar; new tabs
use the home icon, and unavailable favicons use the shared globe fallback.
Private-mode suggestions keep the globe without consulting the normal favicon
store or fetching icons, including inside the dialog's wrapped context.
Category headings, engine keywords, trailing hints and the bottom
keyboard guide are omitted. The close control uses an accessible X icon on
both compact and wide windows; Escape and keyboard navigation still work.

Settings → Command Bar → Use the new design is an on/off switch: on uses the adaptive
phone search screen / wide address-bar dropdown; off uses the previous centered
design. Existing installations and missing/unknown values use the new design. An explicit choice is saved independently of
suggestion visibility and compact spacing. The anchored panel also has a
**Return to previous UI** action at its lower edge, outside the scrollable
results, including when no results are visible. It changes the saved layout
immediately while retaining text, selection, focus and the active suggestion.
**Dismiss tip** hides this migration footer without changing layouts or closing
the editor; dismissal survives restart. An explicit layout choice in Settings
or through the return action also dismisses the tip. The design switch in
Settings stays available after dismissal.
It can be activated by touch, pointer or keyboard focus plus Enter.

Centered mode ignores the address-field bounds and uses the center of the safe
area above the IME, at up to 720 dp wide (85% of a wide window or 96% of a narrow
one). It uses a dimmed background and an 80 dp input row, reduced to 64 dp with
compact spacing. Both top and bottom browser-bar placements use this centered
editor. Its entrance and live layout switch do not add a movement animation;
the new design uses the phone transition or wide dropdown described above. Both layouts keep the same
suggestions, icons, shortcut and CJK composition guards, dismissal and private
favicon behavior. Switching does not navigate a page or rebuild its WebView.

Settings → Command Bar controls which suggestions appear:

- Search and URLs, including the common search engines and AI search.
- Open tabs.
- Browsing history from the selected Space, including when the Spaces UI is hidden.
- Spaces.
- Commands, shown or hidden together with one switch.

Every category is initially enabled, preserving existing behavior. Preferences
use atomic DataStore updates; hiding suggestions does not delete tabs, Spaces or
browsing history.

A temporary settings-file read failure retries without treating the store as
empty or enabling every category. Existing observers resume when storage is
readable again. Corrupt or otherwise invalid settings surface an error instead
of displaying an editable set of defaults; settings files are not reset.

Turning off **Use Spaces** also turns the Spaces suggestion switch off and
disables it. Its saved choice is retained, so turning Spaces back on restores
the previous suggestion setting.

These settings change only what the Command Bar lists. Hiding Commands does not
disable the commands themselves: their keyboard bindings and the other places
that run them keep working. The bindings are documented in
[keyboard shortcuts](KEYBOARD_SHORTCUTS.md).

Earlier development builds had a switch for each command. Those per-command
choices are no longer read, so every command appears whenever Commands is on.

While Commands are listed, input beginning with `>` shows only commands, and
Enter does nothing if none matches. When Commands are hidden, `>` is ordinary
text and the placeholder no longer mentions it. With other input and no visible
suggestions, Enter still resolves the committed URL or search query. When there
is nothing to suggest, the centered bar shows only its input, without a "No matches" line. The new layout keeps its migration actions below the input until they are dismissed. IME composition keeps the existing
input guards described in [architecture](ARCHITECTURE.md#ime-input-policy).

Other feature restrictions still apply: disabling Spaces excludes Space switches
and other Spaces' tabs, while narrow windows exclude both Split commands.
With empty input, open-tab suggestions come from the current Space, matching the
sidebar. Typing a query searches open tabs across Spaces while Spaces are enabled;
other Spaces' matches show the Space name before the URL in their subtitle.
Tabs with different IDs remain independently selectable even when their URLs
match. The plus-icon **New tab** command creates a tab, while a home-icon
**New tab** result selects an existing blank tab.
The visibility switches do not change saved Split pairs or live browser sessions.

## Repeatable checks

`CommandBarSuggestionsTest` covers category independence, command mode, and
Space/window restrictions. `SpacesSuggestionsTest` checks current-Space defaults,
cross-Space query results and labels, disabled Spaces, and distinct IDs sharing a
URL. `CommandBarSettingsTest`
covers upgrade defaults, concurrent updates, reopening and retained choices.
`KeyboardShortcutsTest` checks the displayed bindings against key dispatch.

`CommandBarSettingsUiTest` exercises Settings at phone/light and tablet/dark
sizes, preference recovery after Activity recreation, live page/draft and Split
retention across Settings, empty suggestions, keyboard access to Settings and
local URL navigation. `CommandBarInputUiTest` checks committed search fallback
and command-only input without depending on a live search provider.
`CommandBarDropdownUiTest` checks phone/top and phone/bottom placement, software
keyboard clearance, both Split panes in light/dark themes, stable input position
with empty results, shortcut entry, text retention while resizing and live
WebView/draft retention across opening and dismissing the dropdown.

## Local verification (2026-11-15)

The JDK 17 Gradle Wrapper passed `:app:testPlayDebugUnitTest`,
`:app:lintPlayDebug`, `:app:lintPlayRelease`, `:app:assemblePlayDebug` and
`:app:assemblePlayDebugAndroidTest` with `--no-parallel`. All 201 unit tests
passed without failures, errors or skips. Debug/Release lint reported zero
errors and 83/82 warnings respectively.

The three selected UI cases passed on dedicated `Nagi_Selected_20261115`
(`emulator-5586`, API 36, WebView 133.0.6943.137), through the connected Gradle
task and direct Android instrumentation. The final two-case screenshot rerun
also passed at 411 × 820 dp/light and 1000 × 820 dp/dark. Settings, shortcut
labels, filtered command lists and empty results were visually inspected.
The six added strings have matching keys/placeholders in all six locales;
pre-existing resource content was checked against the pre-edit snapshot.

An added UI assertion initially failed test compilation because its Compose key
API required experimental opt-in. It now sends ordinary Android key events;
the final test APK assembly, Debug lint and all selected tests passed. Screenshot
capture waits for the rendered frame after Compose semantics become idle.

Logs, 12 screenshots and final APK hashes are retained under ignored
`artifacts/commandbar-settings-20261115/`. This verifies the current development
working tree, including retained unrelated work. It is selected emulator coverage,
not a full instrumentation suite or physical-keyboard/device result. No version
bump or release action was performed.

## Label simplification verification (2026-11-15)

After removing the category headings, trailing hints and footer, the JDK 17
Wrapper passed `:app:assemblePlayDebug :app:assemblePlayDebugAndroidTest
:app:lintPlayDebug --no-parallel`. Debug lint reported zero errors and 84 warnings.
The existing UI tests now locate the accessible close icon by `command-close`
instead of the removed Esc text. Shortcut descriptions remain in Settings.

Five selected UI cases passed without failures or skips on the same dedicated
API-36 emulator: both `CommandBarSettingsUiTest` cases, `CommandBarInputUiTest`,
`CommonSearchEnginesUiTest#chooseOverseasCombinationRecreateAndRemoveAnEngine`,
and `AppearanceUiTest#commandSelectionKeyboardNavigationAndDialogSystemBarsFollowBothThemes`.
Phone/light and tablet/light/dark screenshots were visually inspected. They
cover the reduced suggestion list, search-engine selection, touch dismissal,
arrow-key selection, Escape, saved visibility settings and live Split retention.

Compact evidence is retained under ignored `artifacts/commandbar-labels-20261115/`.
Unit tests, Release lint and the full instrumentation suite were not rerun for
this display-only follow-up. No version bump or release action was performed.

## Address-toolbar dropdown verification (2026-11-19)

The JDK 17 Wrapper passed `:app:assemblePlayDebug`,
`:app:assemblePlayDebugAndroidTest` and `:app:lintPlayDebug` with `--no-parallel`.
Debug lint reported zero errors and 96 warnings. `git diff --check` passed.

On dedicated `Nagi_CompactPanel_QA_20261118` (`emulator-5588`, API 36,
WebView 133.0.6943.137), all four `CommandBarDropdownUiTest` cases passed,
including a final direct-instrumentation rerun. Screenshots were inspected at
411 × 820 dp in phone/light and phone/bottom/dark with the software keyboard,
and at 1000 × 820 dp in wide/light and Split/light/dark. Checks cover toolbar
position, changing result counts, inactive-pane URL selection, keyboard clearance,
retained input while resizing, and the same live WebViews and page drafts after
opening/dismissal.

Seven existing cases also passed: `CommandBarInputUiTest`, both
`ChatGptCommandBarUiTest` cases and all four `CjkInputTest` cases. The initial
11-case connected run passed ten cases; search-engine-name composition failed
because that test requires the wide sidebar Settings button while the emulator
was at phone width. That case passed when rerun at 1000 dp. No case was skipped.
The test IME and display-size override were restored afterward.

Compact logs, nine final screenshots and development APK hashes are under
ignored `artifacts/commandbar-dropdown-20261119/`. Unit tests, Release lint,
the full instrumentation suite and physical devices were not checked for this
prototype. No version bump or release action was performed.

## Original field size refinement (2026-11-19)

The dropdown now measures the visible address field rather than the whole
toolbar. Its input retains the original width, 40/44 dp height, typography and
surface color. Its outer radius remains half the original input height rather
than changing with the results. `TextFieldState` and the existing composition
and key guards are retained in a compact `BasicTextField`.

The JDK 17 Wrapper passed `:app:assemblePlayDebug`,
`:app:assemblePlayDebugAndroidTest` and `:app:lintPlayDebug --no-parallel`;
lint reported zero errors and 96 warnings. Final direct instrumentation passed
all ten selected cases: four `CommandBarDropdownUiTest`, `CommandBarInputUiTest`,
both `ChatGptCommandBarUiTest`, and the Japanese, Chinese and Korean Command Bar
cases in `CjkInputTest`. No case was skipped. The same API-36 QA emulator was
used; phone/light, bottom/dark and wide/Split light/dark screenshots were checked.

The initial run caught four size/position assertion failures: measuring the
Surface included its 48 dp touch target. Measuring the visible inner field fixed
the discrepancy; final assertions compare both edges and the input height to
the idle field, including after resizing and with empty results.

Evidence and APK hashes are under ignored
`artifacts/commandbar-field-size-20261119/`. Unit tests, Release lint, the full
instrumentation suite and physical devices were not rerun. No version bump or
release action was performed.

## Compact suggestion rows (2026-11-19)

Single-line rows shrink from 60 to 40 dp, and the list's top/bottom padding
shrinks from 12 to 4 dp. The input dimensions and outer corner radius are retained.
Debug APK/test APK assembly and Debug lint passed (zero errors, 96 warnings).
All four `CommandBarDropdownUiTest` cases and both `ChatGptCommandBarUiTest`
cases passed on the same API-36 QA emulator, with no failures or skips.
Phone/light, bottom/dark and Split/light/dark screenshots were inspected.
Evidence is under ignored `artifacts/commandbar-compact-rows-20261119/`.
Unit tests, Release lint, the full UI suite and physical devices were not rerun.

## Hardware-keyboard density (2026-11-19)

The compact layout now applies only while Android reports an available hardware
keyboard. Other configurations use 56 dp minimum rows and 8 dp list padding.
Debug APK/test APK assembly and Debug lint passed (zero errors, 96 warnings).
An initial test compilation needed a missing import; the corrected build passed.

All seven selected API-36 emulator cases passed: `CommandBarKeyboardLayoutUiTest`,
four `CommandBarDropdownUiTest` and two `ChatGptCommandBarUiTest`. No case failed
or was skipped. The configuration test drives absent, available and hidden
hardware-keyboard states through `LocalConfiguration`, checking different row
heights plus retained text, focus, input bounds and selected suggestion. Its
final screenshot rerun also passed. Paired configuration screenshots and the
phone/bottom/Split light/dark screenshots were inspected.

Evidence is under ignored `artifacts/commandbar-keyboard-density-20261119/`.
Actual physical keyboard attachment/removal, unit tests, Release lint and the
full UI suite were not checked. No version bump or release action was performed.

## Anchored opening animation (2026-11-19)

The Command Bar disables its platform window animation and uses Compose
`AnimatedVisibility` to reveal only the results, downward from a top input or
upward from a bottom input. The 200 ms ease-out transition changes the revealed
height without scaling the text, and retains the original input and corner radius.

Debug APK/test APK assembly and Debug lint passed with zero errors and 96
warnings; `git diff --check` passed. All 14 selected cases passed without failures
or skips on dedicated API-36 `emulator-5588`: two `CommandBarMotionUiTest`,
`CommandBarReducedMotionUiTest`, four `CommandBarDropdownUiTest`,
`CommandBarKeyboardLayoutUiTest`, two `ChatGptCommandBarUiTest`,
`CommandBarInputUiTest`, and the Japanese/Chinese/Korean Command Bar cases in
`CjkInputTest`.

Controlled animation-clock checks verify progressive expansion with a stationary
top input, expansion attached to the bottom input, no overshoot, zero native
dialog animations, and immediate results with a zero `MotionDurationScale`.
The preview Pixel Tablet (`emulator-5584`, 1280 × 800 dp) was updated separately;
before/after recordings and extracted intermediate frames were inspected, and
its native dialog attributes confirmed the window animation was removed.
Phone/light, bottom/dark and Split/light/dark regression screenshots were checked.

Evidence, recordings and APK hashes are under ignored
`artifacts/commandbar-expand-20261119/`. Unit tests, Release lint, the full UI
suite and physical devices were not checked. No version bump or release action
was performed.

## Open-tab icon verification, 2026-11-23

Open-tab suggestions now render the sidebar's shared favicon instead of a
generic tab glyph. Their existing icon slot and row layout are retained. A new
tab uses the home glyph. `Favicon` resolves wrapped Activity contexts so a
private Command Bar dialog retains the existing private-mode globe policy.

Nine selected emulator cases passed across the final runs: two
`CommandBarFaviconUiTest`, `CommandBarKeyboardLayoutUiTest`, two
`FaviconThemeCacheTest` and four `CommandBarDropdownUiTest`. The added dialog
check uses distinct cached fixture icons, verifies light/411 dp and dark/1000 dp
keyboard layouts and tab selection, and makes no requests to either fixture.
The real private-Activity dialog makes zero requests to an uncached fixture
origin and leaves the normal icon store without that origin. The initial empty
editor assertion included the placeholder; the harness was corrected before
the final normal-dialog pass. Screenshots were inspected.

Debug/test APK builds, Debug/Release lint (zero errors, 97 warnings each), and
`git diff --check` passed. JVM/full instrumentation suites, physical devices and
live websites were not rerun for this UI-only change. A Debug-signed parallel
test APK uses `com.takeruf.nagi.preview` / `Nagi Test`. Evidence and APK metadata
are retained in ignored `artifacts/command-tab-icons-20261123/`; no version bump,
upload or publication is included.

## Layout choice and migration verification (2026-11-24)

`CommandBarLayoutSettingsTest` covers new-design defaults, unknown values,
explicit legacy choice, persistent tip dismissal and concurrent unrelated
settings edits. `CommandBarLayoutUiTest` checks the real footer, keyboard access,
Settings selector, restart persistence, both compact densities, empty results,
editor/candidate selection and live Split/page drafts at phone/light and
tablet/dark sizes. A stable `SearchPanelLayout` moves the input's existing node
above/below the results so switching from a bottom editor cannot dispose its
focus or collapse its text selection. Both layouts passed native Japanese,
Chinese and Korean InputConnection checks. See the dated
[validation record](VALIDATION.md#search-layout-choice-migration-tip-and-afpbb-recovery-2026-11-24-local)
for the executed checks, initial failures, debug artifact and limits.
