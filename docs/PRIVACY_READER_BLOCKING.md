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

Final release results are in [1.0.0 publication verification](RELEASE_1.0.0_VERIFICATION.md). Physical devices and older WebView providers remain untested. Service-worker and WebSocket traffic are outside this WebViewClient interception integration. Request interception can precede onPageStarted, so counters are initialized at navigation and kept per document.

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

The separately attributed GPL-3.0 compatibility data adapts Livedoor's upstream `:upward(1)` rule to equivalent standard `:has()` CSS, appended after cached list updates without modifying the original subscription. Local WebView regression cases cover Asahi and Livedoor ad-parent collapse, editorial preservation, and pause/resume. Request counts measure blocked requests, not ad units.

## Empty advertising containers (2026-10-11)

The temporary Asahi-specific mobile wrapper rule has been removed. Reserved
space is now handled by a common DOM controller without host names or site layout
class names. Brave's cosmetic selectors provide positive evidence of hidden ads.
Actual intercepted resource URLs also identify blocked images, iframes, objects,
and embeds; matching uses complete resolved URLs, not URL substrings. The current
document retains at most 256 blocked URLs, without writing them to disk.
Only requests with a Referer in the top document's origin contribute resource
URLs to its controller. Missing or cross-origin child-frame Referers contribute
no URLs; cosmetic evidence still applies. This prevents child-origin resource
URLs from being disclosed to the top page.

The controller walks outward from those elements and collapses empty containing
branches. It retains text, controls and editable fields, unblocked media, CSS
illustrations/generated text, unrelated empty spacers, and semantic article,
header, navigation, form, and main boundaries. Ordinary blank areas alone are not
evidence of an ad. Markers and a stylesheet hide the identified slots without
deleting DOM nodes or changing inline styles. Pause, saved exceptions, and global
disabling remove the markers and restore layout.

DOM changes and resource load/resize events are batched, without a polling loop.
Late slots are collapsed; a slot gaining editorial content or an allowed media
URL is revealed again. Request interception refreshes the resource evidence in
the page context; stale callbacks from prior documents do not update a new page.
This does not reload a page or alter the deferred mobile/desktop navigation policy.

WebView regressions cover unnamed nested containers on two unrelated example
hosts, network-blocked images and iframes, retained editorial/interactive/media
and CSS content, late insertion/removal, restoration when content appears,
and pause/resume. Broader privacy/Reader/private-profile cases and existing
resize/hidden-tab/Split draft preservation cases are included in validation.
Final common-logic/privacy regression XML results remain in ignored
`artifacts/1.0.0/validation/`; superseded prototype outputs and Debug APKs were removed.
Live-site coverage and its remaining limitations are recorded in
[`AD_SLOT_COLLAPSE_VALIDATION.md`](AD_SLOT_COLLAPSE_VALIDATION.md).
