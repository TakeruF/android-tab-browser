# Keyboard shortcuts

Nagi uses Control or Command as the primary modifier when Android delivers that
key combination to the app. The [README shortcut table](../README.md#using-nagi)
lists the supported bindings. Existing tab, search, navigation and find bindings
remain available.

The additional bindings are limited to six actions:

| Keys | Action |
| --- | --- |
| Ctrl / Command + `+` | Zoom the focused page in. `=` and numpad `+` also work. |
| Ctrl / Command + `−` | Zoom the focused page out. Numpad `−` also works. |
| Ctrl / Command + `0` | Reset the focused page's zoom. Numpad `0` also works. |
| Ctrl / Command + `H` | Open browsing history. |
| Ctrl / Command + `,` | Open Settings. |
| Ctrl / Command + Shift + `N` | Open the existing private-browsing screen when the WebView provider supports isolation. |

Zoom applies only to the focused pane while its ordinary page is visible.
Settings, History, Reader, the Omnibox, Find, fullscreen video, the compact tab
sheet, translation options and external-app confirmation exclude page zoom.
Opening History or Settings dismisses the Omnibox and Find UI. Private browsing
uses the existing support check and does not open another private screen from a
private Activity.

Only the listed modifier combinations are recognized. Extra Alt, Symbol, or
Control+Command combinations, unused Shift combinations, key-up events and key
repeats pass through to Android or the focused content. Zoom-in accepts `+` or
`=` from the active keyboard layout, including Shift+`=` on US and the
corresponding JIS key; Control/Command is still required. This does not broaden
Shift combinations for zoom-out or reset. Ordinary text and unmodified symbols
are not browser shortcuts. CJK composition handling remains with the IME and
the existing Command Bar input policy.

The choices follow familiar [browser shortcut conventions](https://support.google.com/chrome/answer/157179?hl=en)
where Nagi has a corresponding feature. Android or a desktop host can reserve
some key combinations before Nagi receives them; synthetic key-event coverage
does not establish physical-keyboard/OEM behavior.

## Verification (2026-11-10)

All seven `KeyboardShortcutsTest` unit cases passed, including layout-provided
`+`/`=` symbols and modifier rejection. The final integrated working-tree build
passed all 193 unit tests, Debug/Release lint (zero errors, 81 warnings each),
and Debug/test APK assembly.

On dedicated `emulator-5584` (API 36, WebView 133.0.6943.137, 1000×800 dp),
`BrowserActionsShortcutsUiTest` exercised the foreground Android window with
`Instrumentation.sendKeySync`: History/Settings from the Omnibox Dialog, private
Activity reuse, and zoom/reset with a retained page draft and background-page
guards. Existing numbered-shortcut and Japanese/Chinese/Korean IME cases also
passed. The initial zoom-reset test sent Ctrl+0 before the dismissed Dialog had
returned focus; after explicitly waiting for the Activity window's focus, that
case passed on its focused rerun. The other six selected cases passed in the
initial run. The original Latin IME was confirmed restored afterward.

Logs and the frozen APK pair are under ignored
`artifacts/keyboard-shortcuts-20261110/`. These are local selected tests, not
full instrumentation, physical JIS-keyboard, OEM, or publication proof.

## Selective commit verification (2026-11-15)

An isolated checkout from `97e8852` validated the six additional keyboard
actions with the existing Settings and compact-browser layouts. The UI test
uses the existing Settings list and Back control, so it does not depend on the
separate category-navigation changes.

All 193 Play Debug unit tests passed with no failures, errors or skips.
Play Debug/Release lint each reported zero errors and 72 warnings.
The JDK-17 Gradle Wrapper passed
`:app:testPlayDebugUnitTest :app:lintPlayDebug :app:lintPlayRelease :app:assemblePlayDebug :app:assemblePlayDebugAndroidTest --no-parallel`.
After adapting the UI-test selectors, instrumentation APK assembly and Debug
lint passed again.

Dedicated `Nagi_Selected_20261115` (`emulator-5586`, API 36, WebView
133.0.6943.137) passed all ten selected instrumentation executions:

- At 1000 × 800 dp, three `BrowserActionsShortcutsUiTest` cases, the existing
  numbered-shortcut case, and Japanese/Chinese/Korean Command Bar IME cases
  passed (seven executions).
- At approximately 411 × 914 dp, all three `BrowserActionsShortcutsUiTest`
  cases passed again.

The cases verify History/Settings from the foreground Omnibox Dialog,
private-Activity reuse, native zoom/reset with retained drafts and exclusion
behind overlays, numbered navigation from Settings/Omnibox, and composition
commit/Enter/IME Go behavior. No case failed or skipped; the original IME was
confirmed restored afterward. Installed main/test APK hashes matched the
selected build.

Logs, exact selections and content hashes are retained under ignored
`artifacts/keyboard-commit-20261115/`. This is focused emulator coverage,
not a full instrumentation suite, physical-keyboard/JIS/OEM validation or
a store release.
