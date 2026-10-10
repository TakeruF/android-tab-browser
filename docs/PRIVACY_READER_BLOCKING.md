# Content blocking, private browsing, and Reader

## Open-source selection

- Blocking: Brave `adblock-rust` 0.13.2 via `mlm-games/adblock-rs-kt-wrapper` 0.1.3 (MPL-2.0), EasyList/EasyPrivacy (CC-BY-SA-3.0). See `third_party/adblock` for pinned source, artifact hashes, licenses and build adaptation. Adblock Plus Android was also inspected; it relies on older JNI/V8/NDK/JCenter integration. Edsuns/AdblockAndroid was inspected; it adds an older LGPL/native stack. The selected engine is independent of WebView subclasses and preserves existing browser integrations.
- Reader: Mozilla Readability 0.6.0 (Apache-2.0), the library used by Firefox Reader View. Vendored unchanged from https://github.com/mozilla/readability/blob/0.6.0/Readability.js. Existing jsoup 1.21.2 sanitizes extracted HTML; Nagi does not implement an article-extraction algorithm.
- Private storage isolation/deletion: existing AndroidX WebKit 1.14.0 (Apache-2.0), `MULTI_PROFILE` and `DELETE_BROWSING_DATA`. This is a platform capability rather than a complete incognito UI package. Nagi integrates its lifetime with an in-memory Room database and memory-only DataStore. AndroidX owns cookie/cache/site-storage separation and deletion.

## User flows

Blocking is enabled by default. Settings > Privacy offers a global switch;
the address-bar shield shows the current status and blocked-resource count.
Its controls pause blocking for this visit, resume it, or save/remove a site
exception. These actions automatically reload the current page. A visit pause
is shared by the site's tabs, including newly opened articles and subdomains,
and survives reloads and suspension/restoration. OkHttp's existing Public Suffix
List determines the registrable site (e.g. qq.com), including private suffixes.
The pause ends when all that site's tabs leave or close, on explicit resume,
or when the browser workspace ends. Normal/private workspaces stay separate.
It is never written to settings or restored after app restart.
Persistent exceptions remain in Settings. When the shield is hidden on a narrow
pane, the page menu offers the site control at the top. Address actions retain
share, then link copy, then the shield as width decreases; only hidden actions
appear in the page menu, in that same priority order.
Reader resources follow the same per-tab policy. Main documents are never
blocked. Third-party cookie blocking
remains enabled. Domain-specific CSS is applied on visible page commit and
after page load; it also hides subsequently inserted matching ad containers.
The unmodified AdGuard Japanese filter supplements EasyList/EasyPrivacy,
including rules that remove the outer Asahi banner's reserved height.

Page menu > Private browsing opens a separate secure Activity with its own
tabs and Spaces. The banner closes the whole private workspace. Normal tabs
stay alive behind it. Private tabs do not enter normal history, favorites,
closed-tab restoration, favicon files, site display preferences, or saved
Activity state. Private settings and bookmarks exist only for this workspace.
Autofill is disabled and Android screenshots/recents previews are blocked.
Downloaded files and explicitly shared/copied information remain outside the
private workspace; this mode is not a VPN or network anonymity service.

A fresh WebView profile is assigned **before** installing any scripts or
loading a page, including popup and Reader views. Cookies, localStorage,
IndexedDB, service-worker storage and cache are isolated from normal browsing.
On close, after all surfaces are destroyed, AndroidX deletes all browsing data
in that profile. AndroidX cannot delete an already-loaded profile directory in
the same process; empty directories and any profiles left by abrupt termination
are removed on the next cold startup. WebView can write temporary site data to
disk during an active private session: this is isolated, cleared session
storage, not a guarantee of memory-only engine storage. Private browsing is
unavailable unless both runtime WebView features are supported, and never
falls back to sharing normal cookies.

Page menu > Reader mode parses a clone of the live document locally. The
original WebView is retained. Extracted HTML is sanitized with jsoup and
rendered in a separate WebView with JavaScript, DOM storage, file/content
access, and autofill disabled; a restrictive CSP is added. Font controls and
the app theme style the article. Original page or Back closes Reader without
reloading the original DOM, form fields, scroll position or history. Article
links navigate the focused tab. A page that cannot yield an article shows a
message. Reader content is transient and is not persisted or sent to AI.

## Runtime verification

`PrivacyReaderEngineTest` verifies bundled network filtering, native exception
and resource-type handling, live form preservation during Reader, cookie and
localStorage isolation/cleanup without clearing normal data, and private
workspace/history/favicon separation. `PrivacyReaderUiTest` covers Reader
controls, secure private Activity entry/exit, and site exception controls.
Unit tests independently verify sanitization and memory-only settings.

Validation on 2026-10-10: JDK 17 / SDK 36, Android 16 API-36 ARM64 emulator
with WebView 133.0.6943.137. GitHub unit tests: 140 passed; Play unit tests:
129 passed. Debug Lint: 0 errors, 47 warnings. Debug APK/test APK, signed
GitHub Release APK and signed Play Release AAB built successfully.

Initial feature instrumentation included six engine tests and three UI tests.
The local HTTP fixture verifies a blocked script never reaches the server,
and reaches it when the site exception is enabled. Request interception can
precede onPageStarted; counters are therefore initialized at navigation and
kept per document instead of being reset in onPageStarted.

Reader rendering was visually checked at 1920 x 1200. Physical devices and
older WebView provider versions have not been tested. Service-worker and
WebSocket traffic are outside this WebViewClient interception integration.

Regression instrumentation covered 36 cases across the new feature suites,
CrossFeature, Split, site display modes, history, keyboard shortcuts, and video
PiP/pop-outs. 35 passed in the combined run. One existing collapsed-Split
title assertion raced WebView title updates; the fixture now waits for actual
loaded titles, and that case passed on rerun.


## Shield session follow-up (2026-10-10)

The shield popup adds pause/resume for a browsing visit, persistent site
exceptions, current status, and page request counts. qq.com article URLs and
subdomains share the same temporary exception, including new/background tabs.
A site visit remains alive while any retained tab still belongs to that site.
Sleeping tabs and not-yet-acquired background article tabs also keep it alive.
Closing the final site tab, leaving it in every tab, explicitly resuming, or
ending the workspace clears the exception. Private and normal pools stay separate.
OkHttp 4.12.0 is now an explicit dependency; its existing PSL data/notice was
already included through Coil. Tests cover qq.com lookalikes, co.jp/co.uk,
private suffixes such as github.io, local hosts, and unsupported URL schemes.

GitHub unit tests: 145 passed; Play unit tests: 134 passed. Debug lint:
0 errors, 49 warnings. Debug/test APKs and signed GitHub Release APK built;
APK signature verification passed. Device-local engine tests confirm a new
article tab permits the ad script while the shared visit is paused, an
independent workspace still blocks it, and blocking resumes after the last
site tab leaves. UI tests verify shield pause/resume and saved exception
changes with automatic reload. A UI settings wait now advances the Compose
clock instead of blocking its dispatcher while awaiting the write.

Related instrumentation covers 33 distinct cases (7 engine cases and 26 UI/
navigation cases). The combined regression run passed 25/26; one Split fixture
raced a blank page attaching before its DAO URL seed. Setup now navigates the
live pane explicitly before making the pair. All 11 Split cases passed on the
final rerun. All 15 non-Split cases, including the shield controls, passed in
the combined run. Popup and address-bar rendering were inspected at 1920 x
1200. Validation used a dedicated API-36 ARM64 Nagi_Shield_QA emulator with
WebView 133.0.6943.137; physical devices remain untested.

The signed Release APK was also installed in that isolated emulator and cold
launched via https://www.qq.com/. The Tencent homepage rendered successfully.
A real touch opened the shield popup, which initially showed 55 blocked requests.
Pausing caused the page to reload, changed the shield to its disabled state,
and showed zero blocked requests plus the Resume control. Resume reloaded
the page and restored the checked shield. Screenshots and
build/instrumentation logs are retained in ignored artifacts/shield-session/.

## Advertising whitespace and shield appearance (2026-10-10)

The separately bundled, unmodified AdGuard Japanese filter 2.0.77.34 (GPL-3.0)
adds site-specific container hiding to Brave's existing cosmetic parser.
Asahi's `#HometopAdOuter` includes a 110px minimum-height inner banner;
the upstream outer-container rule removes that reserved space. Matching
containers inserted later are hidden by the same stylesheet. Ordinary blank
editorial spacing is retained. Pausing, a saved site exception, or disabling
blocking removes the injected stylesheet; resume reinstalls it. Cosmetics
are applied on visible commit as well as page finish, and injection checks
the document URL to avoid applying a stale page's rules during navigation.
The shield inherits its adjacent address actions' color. Its glyph is now
18dp for optical balance beside the 16dp link/share glyphs; the action button's
position and touch target remain unchanged.
Checked/off shapes and popup status communicate its state.

GitHub unit tests: 145 passed; Debug lint: 0 errors, 49 warnings. The eight
engine and five UI cases were exercised on the dedicated API-36 ARM64
emulator. The first run passed 12/13; the added local cosmetic fixture needed
to supply its base URL through WebView's history callback because
`loadDataWithBaseURL` reports about:blank navigation metadata. The corrected
case passed on rerun, verifying 110px/200px ad container collapse, retained
120px editorial spacing, dynamic insertion, and pause/resume/global/saved
exception behavior. No external site is required by that regression test.

The signed GitHub Release APK built and passed signature verification. It
was installed and cold-launched with the actual Asahi homepage; the large
top ad gap disappeared while the masthead, navigation, and articles remained
visible. The shield matches the adjacent copy/share icons. Evidence is in
ignored `artifacts/cosmetic-blocking/`. Physical devices remain untested.

Additional website QA used the signed Release APK on that same emulator:

| Page | Result |
| --- | --- |
| Yahoo! News homepage and article | Headline, images, article text, and navigation remained visible; pause/resume switched request counts from 19/23 to 0 and back. |
| QQ homepage | Homepage content and navigation rendered; pause showed 0 and resume restored filtering (counts vary as dynamic resources load). |
| BBC News homepage | The large top advertising gap collapsed when blocking was active; pause restored the gap and resume removed it again. |
| NHK News Web | Header/content and the initial usage notice rendered; pause/resume worked. The usage agreement was not accepted, so article interaction beyond that notice was not tested. |
| Livedoor News homepage | Request filtering and pause/resume worked, but the first right-hand ad parent's reserved height remained. The upstream `:upward(1)` rule was adapted to equivalent standard `:has()` CSS in a separately attributed GPL-3.0 compatibility data file. |

The compatibility data is appended after cached list updates, preserving the
fix without modifying the original AdGuard subscription. Two local WebView
regression cases passed on the final code, verifying both Asahi and Livedoor
ad-parent collapse, retained editorial sections, and pause/resume behavior.
Final Debug lint still reports 0 errors and 49 warnings. Screenshots, popup
states, request counts, and the small native UI QA driver are retained in
`artifacts/cosmetic-blocking/`; counts measure blocked requests, not ad units.
The final signed Release APK was installed and Livedoor was rechecked through
the real shield controls: its right-hand news column now starts directly below
the header instead of after the empty ad frame. Pausing restores that frame;
resume collapses it again while preserving news, navigation, and images.

The shield's optical size follow-up increases only the address-bar glyph from
16dp to 18dp to balance its narrower silhouette against the 16dp link/share
icons. The inherited color and fixed action-button bounds remain unchanged.
The signed Release APK was built, signature-verified, installed, and visually
checked in the isolated API-36 emulator at density 240 in both checked and
paused states. The popup opened normally and pause/resume controls worked.
Evidence is retained in ignored `artifacts/shield-optical/`.
