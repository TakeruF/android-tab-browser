# Favicons and app themes

Status: included in the 1.4.0 release candidate; see
[release verification](RELEASE_1.4.0_VERIFICATION.md) for distribution status.

`FaviconStore` shares icons by normalized HTTP(S) origin across tabs, Favorites,
history and drag previews. Each origin has independent light and dark indexes,
plus an optional unclassified fallback. The effective app theme is provided by
`NagiTheme`, including Android System mode. Changing it selects an already loaded
path in the shared `Favicon` composable; it does not start recovery requests or
reload pages. Existing live pages retain their DOM, drafts and scroll. The native
provider may independently request its selected icon again when its theme
configuration changes; Nagi does not suppress the site's standard behavior.

## Acquisition and compatibility

`WebChromeClient.onReceivedIcon` is the preferred observation. It is tagged with
the engine's effective theme, and in-flight independent fetching cannot replace a
new native observation. A later native observation updates the corresponding
variant. Some providers replay the previous bitmap on a configuration change;
an unchanged observation from the previous theme is not learned as a new variant.
Navigation starts a new observation, so fixed icons can be learned in both themes.
The cached bitmap supplied to `onPageStarted` is not treated as a new observation.

If the provider supplies no icon, page-finished discovery uses the document's own
`matchMedia` results to choose matching `link` elements, prioritizing `rel=icon`
over touch icons. Native callbacks cancel this supplementary work. Navigation,
theme and icon generations prevent stale work from changing the current tab.
This does not rewrite site links, preferences or JavaScript.

Unopened saved pages retain the existing public-origin recovery path, without
cookies, saved private URL paths/queries or third-party icon services. That fetch
cannot know the document's theme, so its result remains unclassified. Similarly,
old `origin-<hash>.txt` indexes and imported saved paths remain unclassified;
they are never copied into both themed slots. Room's existing favicon paths and
schema remain compatible. An exact variant takes priority over this fallback.
When only the opposite variant is known, the normal globe placeholder is used.

## Storage and limits

- PNGs live in app-private `files/favicons`, surviving process restarts and Android
  cache cleanup. Light/dark indexes add `-light`/`-dark` to the legacy index name.
- Images are scaled to at most 64 pixels on their longest side. Content hashes
  share identical PNGs and give changed images new Coil cache keys.
- At most 256 origins and 8 MiB of PNG/index data are retained. On first access
  and subsequent writes, least recently used origins are evicted as a group;
  unreferenced images and incomplete temporary files are removed. Index writes
  are atomic and serialized with pruning. Memory indexes and failure backoff
  maps are bounded; file work runs on IO, not during Compose rendering.
- Missing files fall back safely and can be fetched again on a later visit.
  Settings' confirmed site-data clearing removes every variant and invalidates
  pending writes. Private browsing never writes to this cache.

## WebView limits

The native provider chooses which favicon formats and updates it reports.
`onReceivedIcon` provides a bitmap without its source URL or originating theme,
so delayed provider notifications cannot always be associated unambiguously.
Native `prefers-color-scheme`, SVG CSS and JavaScript icon selection are left to
WebView. Independent SVG decoding is skipped when the asset contains
`prefers-color-scheme`, because that decoder cannot reproduce WebView's media
environment. An unreported variant requires a later visit in that theme, or
remains a fallback/placeholder. No image inversion or background page reload is
used to manufacture an unavailable variant. Site-specific theme choices still
control what the site supplies.

See Android's [favicon callback](https://developer.android.com/reference/android/webkit/WebChromeClient#onReceivedIcon(android.webkit.WebView,%20android.graphics.Bitmap))
and [WebView dark themes](https://developer.android.com/develop/ui/views/layout/webapps/dark-theme).

## Repeatable verification

`FaviconDiskCacheTest` covers persistence, legacy indexes, placeholders, fixed-icon
deduplication, origin/byte limits, garbage collection and missing/cleared files.
`FaviconThemeUiTest` uses local `FixtureServer` pages for actual WebView callbacks,
matching media, dynamic JavaScript updates, both cached variants, fixed icons,
redirects, System mode, live Split/drafts, Activity recreation and narrow/wide
screens. `FaviconRecoveryTest` and `FaviconUiTest` retain the previous recovery,
SVG/ICO, missing-file and shared-Favorite coverage.

`FaviconLiveSiteTest` is an opt-in, time-sensitive check for github.com and
chatgpt.com. On a dedicated QA emulator, pass `-e liveFavicon true` and select
`#githubAndChatgptActualWebViewFavicons` with `am instrument`. Then force-stop only
that QA app and run `#reopenLiveCacheWithoutCreatingAPage` separately to check a
fresh process without visiting either site. Results and actual PNGs are written
to the app's external-files `favicon-live` directory. A challenge/error page or
timeout is a failed live check, not evidence of website compatibility.

## Validation on 2026-11-17

The local working tree passed 218 Play Debug unit tests (no failures, errors or
skips), Play Debug/Release lint (zero errors, 95 warnings in each), and Debug/main
instrumentation APK assembly using JDK 17 and the Gradle Wrapper:

```sh
./gradlew :app:testPlayDebugUnitTest :app:lintPlayDebug :app:lintPlayRelease :app:assemblePlayDebug :app:assemblePlayDebugAndroidTest --no-parallel
```

Dedicated `Nagi_Favicon_QA_20261117` (`emulator-5588`, API 36, Android WebView
133.0.6943.137) passed all 11 selected instrumentation cases in a single final
run: `FaviconThemeUiTest`, `FaviconThemeCacheTest`, `FaviconRecoveryTest`,
`FaviconUiTest` and `PageDarkeningEngineTest`. Narrow 411dp and wide 1000dp
light/dark screenshots were inspected, including live Split and tab/Favorite
icons. Cache-only Compose switching made zero HTTP requests. The provider itself
re-requested its existing icon during live WebView theme configuration changes;
the document was not reloaded. The race regression confirms that a new native
observation wins an in-flight fetch and clearing prevents pending writes.

Two separate opt-in live checks also passed: the actual GitHub and ChatGPT pages
supplied distinct white/dark icons in dark/light app themes, cached switching
worked after leaving the sites, and a fresh process reopened both variants
without creating a page after explicitly stopping the QA app. The final settled
run used native callbacks for both sites and both themes. Opaque-pixel averages
of the captured PNGs were 255 versus approximately 36 for GitHub and 255 versus
0 for ChatGPT. These are signed-out mobile-page observations on this provider;
other providers, signed-in site themes and physical devices remain unverified.

Initial runs exposed and fixed stale native bitmap replay and stale asynchronous
cache writes. Test harness corrections expanded the compact sheet's collapsed
Favorites section and waited for live-site favicon hydration to settle. An
earlier attempt on the pre-existing QA AVD was rejected at installation because
its installed versionCode was newer; all final tests used the new dedicated AVD.

Compact logs, public-site icon PNGs/metadata, screenshots, source digests and APK
hashes are retained under ignored `artifacts/favicon-theme-20261117/`. Existing
unrelated working-tree changes were preserved. No version bump or release action
was performed.

### Source integration for the next update

The favicon changes were isolated onto current `origin/main` after 1.3.1 release
preparation. All 13 favicon source/test files match the implementation verified
above. This candidate passed all 218 unit tests, Debug/Release lint (zero errors,
76 warnings each), and Debug/main instrumentation APK assembly. The selected
11 emulator cases were rechecked on its APK: an initial run stalled in UI-idle
waiting and was stopped; the unchanged case passed after restarting only the QA
app. The legacy Favorite test initially ran at the narrow width left by that
interruption; it passed after restoring the dedicated QA tablet width. All 11
cases passed across these reruns without source changes. The existing version metadata
was retained; this feature is planned for the next update, with no release upload
or submission performed for it.
