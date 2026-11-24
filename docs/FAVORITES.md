# Favorites

Favorites are shared shortcuts across Spaces. The tile's ellipsis menu provides
**Edit favorite** and **Remove favorite** in both the expanded sidebar and the
compact tab sheet. Editing opens a dialog with the existing name and URL.
Cancel dismisses the draft; Save updates the existing entry. Draft text survives
Activity recreation while the editor remains open.

Names must contain non-whitespace text. URLs use the browser's existing HTTP(S)
normalizer, including adding `https://` to a scheme-less address. Invalid URLs
and URLs already assigned to another Favorite disable Save. Validation also runs
inside the repository transaction. The update retains the entry's ID, order,
and other fields; changing its URL discards the previous favicon. A stale editor
cannot recreate a deleted Favorite or modify an ordinary Bookmark.

`FavoriteEditingTest` covers persistence, URL validation, duplicate prevention,
stale editors, and concurrent reordering. `FavoriteEditingUiTest` exercises the
menu, cancel/save, validation, draft restoration and opening the edited URL in a
real WebView against `FixtureServer`, at phone/dark and tablet/light widths.
Use a dedicated QA emulator following [VALIDATION.md](VALIDATION.md).

## Drop into a browser pane

In wide windows, dragging a Favorite into either existing Split pane opens its
URL in that pane's current tab and focuses that side. The other pane keeps its
live WebView, URL, history and unfinished page input. The shared Favorite's ID,
URL and ordering stay unchanged, including repeated drops into both panes.
Ordinary favicon discovery may update the shortcut's cached icon while its
identity and editable fields remain unchanged.

For one visible page, equal left and right halves create a new tab from the
shortcut on that side and pair it with the existing page, using the same
two-pane preview as ordinary tab drags. There is no third central drop region.
The original Favorite tile remains visible while the separate drag preview
moves, including when the pointer leaves the Favorites grid.
Creating, inserting beside the anchor, pairing and
selecting the new tab happen in one Room transaction. The existing page keeps
its engine and the surrounding sidebar tabs keep their relative order.
Dropping a stale/deleted shortcut or onto a closed, moved or newly paired anchor
does not create an orphan tab. Canceling leaves tabs and Favorites unchanged.
Sidebar row insertion keeps its existing conversion/reordering semantics.

These pane/Split targets appear only at the existing 600dp wide-window boundary;
compact browsing retains its separate tab sheet. Targets indicate the exact
destination pane, independently of which pane currently has keyboard focus.

`FavoritePaneDropTest` covers repeated current-pane navigation, retained shortcut
metadata, both Split edges and neighboring order, stale targets and concurrent
drops. `FavoritePaneDropUiTest` covers touch drops into both unfocused Split
panes, keyboard focus, retained partner WebViews/Japanese drafts, equal-half
mouse drops, source-tile visibility, cancellation and the 599dp boundary with
local HTML fixtures.

Local validation on 2026-11-19 passed all six `FavoriteEditingTest` cases,
both `FavoriteEditingUiTest` cases on the dedicated API-36 QA emulator, and the
two existing Favorite action-long-press/mouse-drag cases in `SidebarDragTest`.
Play Debug/test APK builds and Play Debug lint passed. Initial UI-test failures
were corrected in the harness (field labels, the collapsed Favorites section,
and matching the fixture's query string). Screenshots and logs are retained in
ignored `artifacts/favorite-edit-20261119/`. This was focused emulator coverage;
the full instrumentation suite, physical devices and Split were not rerun.

On 2026-11-24, all four Favorite pane repository tests and both pane UI cases
passed on the dedicated API-36 emulator, covering both unfocused Split panes,
mouse center/edge drops, cancellation, retained drafts and the 599dp boundary.
See the dated [validation record](VALIDATION.md) for staged source/artifact scope.
