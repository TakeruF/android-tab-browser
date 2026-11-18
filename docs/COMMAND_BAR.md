# Command Bar

Open the Command Bar with Ctrl / Command + L or the browser's search control.
Suggestions show their icon and title, with URLs below open tabs and history
entries. Category headings, engine keywords, trailing hints and the bottom
keyboard guide are omitted. The close control uses an accessible X icon on
both compact and wide windows; Escape and keyboard navigation still work.

Settings → Command Bar controls which suggestions appear:

- Search and URLs, including the common search engines and AI search.
- Open tabs.
- Browsing history.
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
is nothing to suggest, the bar shows only its input, without a "No matches" line. IME composition keeps the existing
input guards described in [architecture](ARCHITECTURE.md#ime-input-policy).

Other feature restrictions still apply: disabling Spaces excludes Space switches
and other Spaces' tabs, while narrow windows exclude both Split commands.
The visibility switches do not change saved Split pairs or live browser sessions.

## Repeatable checks

`CommandBarSuggestionsTest` covers category independence, command mode, and
Space/window restrictions. `CommandBarSettingsTest`
covers upgrade defaults, concurrent updates, reopening and retained choices.
`KeyboardShortcutsTest` checks the displayed bindings against key dispatch.

`CommandBarSettingsUiTest` exercises Settings at phone/light and tablet/dark
sizes, preference recovery after Activity recreation, live page/draft and Split
retention across Settings, empty suggestions, keyboard access to Settings and
local URL navigation. `CommandBarInputUiTest` checks committed search fallback
and command-only input without depending on a live search provider.

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
