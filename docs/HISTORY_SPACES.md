# Browsing history by Space

The history screen and Command Bar history suggestions show only the selected
Space's saved visits. The history heading identifies that Space when Spaces are
enabled. Switching Space updates the open history screen and resets its search
query. Turning off the Spaces UI keeps the current Space's history scoped; it
does not combine hidden Spaces. Favorites and Bookmarks remain shared.

Recording resolves the originating tab's Space inside a Room transaction,
independently of the currently selected Space. Identical URLs visited in two
Spaces have separate rows. Repeated completion/reload notifications update the
same visit's metadata without changing its timestamp. Moving a tab keeps its
earlier visits in the old Space; a subsequent completion in the new Space
records a new visit there. Deleting a Space cascades its visits, and a late
callback from a deleted/closed tab cannot recreate them. Private browsing
continues to suppress visit recording and uses its separate in-memory workspace.

Same-document navigation (`pushState`, `replaceState`, fragments and native
Back/Forward traversal) also records the current URL without waiting for a new
document's `onPageFinished`. Delayed page/video title notifications update that
visit's metadata through the same tab/Space deduplication path. A title update or
repeated notification does not create another visit or change its timestamp;
an actual navigation away and back remains a separate visit.

The store retains up to 5,000 visits per Space and observes the latest 2,000
for the current history screen and suggestions. Another Space's activity cannot
consume this limit. Individual history-screen removals check the current Space.
Settings → Privacy → Clear history remains a device-wide privacy action; its
six-language confirmation explicitly says that all Spaces are affected.

## Upgrade preservation

Room schema v4 retains `orbit.db` and adds `history.spaceId`, an indexed foreign
key to `spaces` with cascading deletion. The non-destructive v3 → v4 migration
preserves row ids, URLs, titles, favicon paths and visit timestamps. Versions
before v4 did not record the originating Space, so ownership cannot be inferred
reliably. Those earlier shared visits are kept once in existing Personal, or
the first existing Space by saved position if Personal no longer exists. They
are not copied into every Space. If a legacy database has no Spaces, rows stay
unowned until initialization creates Personal and assigns them there.

## Validation

- `HistorySpaceMigrationTest` opens the exported v3 schema with legacy rows and
  validates the current schema, ownership fallback, preserved metadata/ids,
  cascading deletion and initialization of a database without Spaces.
- `WorkspaceRepositoryTest#versionOneDatabaseMigratesWithoutLosingTabs` checks
  the full v1 → current migration chain including earlier visits.
- `LibraryHistoryTest` covers reload deduplication, concurrent callbacks,
  background ownership, duplicate URLs across Spaces, tab movement, scoped and
  global removal, and ignored callbacks after Space/tab deletion.
- `CommandBarSuggestionsTest` checks history filtering before URL deduplication
  with Spaces enabled and hidden. Open-tab search retains its separate behavior.
- `SpaceHistoryUiTest` uses real WebView visits in two Spaces at phone/light and
  tablet/dark widths, switches the open history route, deletes one visit,
  recreates the Activity and hides Spaces without combining their histories.

Executed results and device limits belong in [validation](VALIDATION.md).
