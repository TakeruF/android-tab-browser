# Everyday browser integrations

Page loading failures use a native replacement screen; see [page errors](PAGE_ERRORS.md)
for transport/certificate handling, preserved site responses and retry/history behavior.

## Authentication windows and Android web search

User-initiated child windows are handed from Chromium to a complete
`WebViewBrowserEngine` and adopted by `EnginePool` under a Room-created tab ID.
The child is not replaced by a second `loadUrl` call. This preserves
`window.opener`, `postMessage`, native request bodies and the child's browsing
context. Script-written `about:blank` content renders as a website document;
explicit Home navigation still opens Nagi's new-tab screen. A child's
`window.close()` closes only its own tab through the repository's existing
opener/selection/Split policy.

The child inherits the opener's WebView profile, initial browser identity,
cookie policy and current privacy/display preferences. Destination-specific
display choices still apply during navigation. Pending requests and connected
opener/child engines are protected from suspension. Closing an opener cancels
unclaimed windows and releases its link to already adopted children. Popups
without user input are rejected; each opener permits at most two pending/live
children. Provider login and Google embedded-OAuth eligibility remain separate
from preserving this native window connection.

The exported browser Activity handles `ACTION_WEB_SEARCH` with
`SearchManager.QUERY`, so Nagi appears in Android's web-search app chooser.
Both cold launch and delivery to the existing Activity open a new tab using
the current default search engine. Selected text is a literal search query,
including URL-shaped text and Nagi engine keywords; the source page stays alive.
Empty or missing queries open no tab.

Sources: [WebView child-window contract](https://developer.android.com/reference/android/webkit/WebChromeClient#onCreateWindow(android.webkit.WebView,%20boolean,%20boolean,%20android.os.Message)),
[Android web-search intents](https://developer.android.com/guide/components/intents-common#SearchWeb).

## Mouse input over native pages

`NativeSurface` keeps Compose as the pointer owner above a native WebView. Mouse
`BUTTON_PRESS` / `BUTTON_RELEASE` events can otherwise reach the focused native
child through generic dispatch even when a Compose notice owns the click.
The container forwards these events only for a mouse gesture whose `DOWN` it
received, matching its device ID and down time. This prevents a translation-card
button or inert card area from activating a page link underneath it.

A matching final button release is accepted before or after `UP`; a new press
cannot inherit ownership from an ended gesture. Cancellation, engine replacement,
child removal and detachment clear ownership. Clicking outside a non-modal notice
and clicking the page after dismissal still work. Touchscreen input, wheel
dispatch, keyboard actions and accessibility actions keep their existing paths.
Android's [cursor input mapper](https://android.googlesource.com/platform/frameworks/native/+/c497a09eb5d2eea5999915a7163e6e44d5185dfd/services/inputflinger/reader/mapper/CursorInputMapper.cpp)
documents the separate mouse button and pointer events.

`BrowserNoticeInputUiTest` sends raw window events over a real WebView link trap:
touch and mouse, card padding, disabled/enabled translation controls, dismissal,
both final-release orders and subsequent page clicks. The translation-action
case records the real notice's callback without downloading ML Kit models.
Semantics-only clicks do not establish this Compose/native routing behavior.

Page mouse/trackpad text selection now requests the native horizontal toolbar
after primary release; see [selection menus](MENU_LAYOUT.md#page-text-selection).
The `nagiSelection` bridge accepts only a short geometry/selection-status message
once after recent native pointer input. It returns no app data, exposes no
clipboard or file operation, and does not send selected text to Android.
The native WebView retains the selection and owns the eventual menu actions.

## Camera uploads

`FileSelectionRequest` preserves WebView's capture preference. Single-image JPEG-compatible or unrestricted inputs offer Take photo / Choose files; image inputs requesting capture launch the camera directly. Other MIME types and multiple selection retain the document picker. Android camera permission is requested when the user selects photography.

The camera writes a full-size JPEG to `cache/camera-uploads/` through the separate, non-exported `UploadFileProvider`. Success returns its content URI; cancellation, denial, unavailable camera apps and empty results finish the file callback without a selection. Files remain available for upload until the Activity host is disposed. Document picker replies cannot expose Nagi's private providers. The GitHub APK update provider remains separate.

Video/audio recording and folder selection are outside this first implementation.

## Website camera, microphone and location permissions

HTTPS pages request site access through Nagi's shared notice-card dialog, using
the active app theme and the same width, spacing and actions as download
confirmation. Camera, microphone and location requests identify the requesting
origin and resources. Blocking, Back or outside dismissal denies the request.
After site consent, Android asks for any missing runtime permissions through its
own system UI. Nagi does not persist a site-wide allow decision.

WebRTC microphone capture declares both `RECORD_AUDIO` and the normal
`MODIFY_AUDIO_SETTINGS` permission. Chromium's Android audio manager requires
both to enumerate/select communication input devices; without the latter,
granting recording alone can still leave capture without an available device.
Nagi leaves device selection, routing and audio-mode restoration to WebView.
See [Chromium's audio permission checks](https://chromium.googlesource.com/chromium/src/media/+/master/base/android/java/src/org/chromium/media/AudioManagerAndroid.java)
and [Android's WebView permission handling](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/wrap-webview-in-compose#handle-web-permissions).

Cancelled/destroyed WebView media requests withdraw only their matching consent
prompt. A late Android permission result cannot grant a different pending site
request. Engine suspension continues to protect pending and granted media
capture. `SitePermissionHostTest` covers consent, request identity, cancellation
and disposal. `MediaPermissionUiTest` exercises real `getUserMedia` audio tracks,
denial/retry and navigation cancellation using a local HTTPS-base document.
Acquiring a live track establishes WebView capture, not physical microphone
quality, manufacturer behavior or a completed third-party voice-service session.

### Permission and popup review, 2026-11-26

The source review covered the shared manifest, distribution overlays, runtime
launchers, WebView resource grants and the native host's app-owned dialogs.

| Capability | Required access and current handling |
| --- | --- |
| Camera capture | `CAMERA` is declared and requested at runtime. HTTPS WebRTC grants only requested camera resources after site consent. Photo uploads use the system camera and an app-owned output URI. Camera hardware remains optional. |
| Microphone | `RECORD_AUDIO` is declared/requested; `MODIFY_AUDIO_SETTINGS` now supplies the missing normal permission used by Chromium's communication audio input path. No foreground recording service is introduced. |
| Location | Coarse and fine permissions are declared and requested together. An approximate/coarse grant is accepted. HTTPS site consent is separate, and hiding the native request withdraws the corresponding prompt. Background location is not requested. |
| Notifications | `POST_NOTIFICATIONS` is declared. Nagi requests it after acceptance of a generated download or an HTTP download into a writable chosen folder, whose final result is posted by Nagi. Denial does not prevent saving. Ordinary HTTP downloads retain DownloadManager's notification path. |
| Photos, uploads and downloads | `ACTION_OPEN_DOCUMENT`, per-URI read grants and the selected folder's persisted SAF write grant provide scoped access. API 29+ generated downloads use app-owned MediaStore entries; older versions use app-specific external storage. Broad photo-library or external-storage permissions are not needed by these paths. |
| External apps | Existing browsable-intent queries and sanitized `ACTION_VIEW` handling remain. Launch confirmation is controlled by Nagi's existing external-app setting; no additional runtime permission is introduced. |
| Website credentials | The browser-origin credential permission is already declared. Provider/browser approval and the production password-autofill gate remain separate from Android permission presence. |
| Bluetooth and other web resources | Nagi does not scan, pair or directly connect Bluetooth devices, and adds no Nearby Devices request. Audio routing remains delegated to Android/WebView; physical wired, USB and Bluetooth headset routing remains unverified. WebView resources other than supported camera/audio requests continue to be denied. |

The native host's remaining Android `AlertDialog` uses—link/image actions and
the Take photo / Choose files choice—now use the same themed notice-card dialog
as site permissions and download confirmation. Their existing actions, picker
filters, cancellation and camera-denial callbacks remain. All displayed copy
uses the existing six-language strings.

Android's runtime permission, camera, document picker, share and installer UI
remain system-owned. WebView's default JavaScript alert/confirm/prompt UI is
provider-owned and was not replaced by this permission-focused change.
`BrowserActionsHostTest` covers single-use choices, stale prompts, file chooser
cancellation/disposal, camera denial, and the conditional notification request.
`BrowserActionsUiTest` exercises real page upload and link input through the
shared dialog. Runtime results and device limits are recorded separately.

Sources: [Android scoped storage and own-media access](https://developer.android.com/training/data-storage/shared/media),
[Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files),
[Bluetooth permission scope](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions),
[communication audio routing](https://developer.android.com/reference/android/media/AudioManager#setCommunicationDevice(android.media.AudioDeviceInfo)).

## Downloads

HTTP(S) and generated-file confirmations use the same rounded, bordered notice
card as page translation, following the active Space color and light/dark theme.
Two equal-width, at least 48dp-tall buttons provide an outlined Cancel action
and a filled Download action. Cancel is the only dismiss button; the header
has no duplicate close icon. The card remains modal while awaiting a decision.
The filename and source remain visible, with size shown for generated files.
Cancel, Back and outside dismissal do not start a transfer.

HTTP(S) attachments continue through Android DownloadManager with ongoing and
completion notifications. The request supplies a filename without a fixed
description, allowing Android to show its progress percentage and estimated
remaining time when the server provides a length and Android has a speed estimate.
Unknown-length transfers use Android's indeterminate progress indicator. The
system download notification channel must be enabled; its exact presentation
depends on the Android version and device. Android may initially minimize this
system notification; expand it to see its details.

### Download location

Settings › General › Download location keeps the system Downloads folder by
default, so existing users are unaffected. Choosing a folder opens Android's
folder picker; Nagi takes a persisted read/write grant and stores only the tree
URI in DataStore (`download_folder`). Replacing the folder or returning to the
default releases the previous grant only after saving succeeds. A failed save
keeps the old choice and its grant. Private-workspace changes do not release
the inherited grant used by normal browsing. The row shows the folder's name, or notes
when the folder has become unavailable.

`DownloadFolderSettingsTest` covers save-before-release ordering, failed writes,
same-folder selection and private/normal isolation. `DownloadLocationSettingsUiTest`
checks cancellation, resetting to Downloads and retained unrelated settings
after Activity recreation on phone/light and tablet/dark layouts. Its synthetic
folder URI checks Settings wiring; it does not establish a real document
provider's grant behavior.

Page-generated files are written straight into the chosen folder. DownloadManager
cannot write into a Storage Access Framework folder, so HTTP(S) downloads bound
for it are staged in app storage with an ongoing-only system notification;
`FolderDownloadReceiver` (protected by `SEND_DOWNLOAD_COMPLETED_INTENTS`) moves
the file on completion, removes the staged copy and posts Nagi's own completion
or failure notification. A pending move is recorded per download ID outside
DataStore and handed out once, so a repeated broadcast cannot copy twice. If the
grant was revoked or the folder removed, the file is saved to Downloads instead;
the staged copy is kept only when neither location accepts it.

A tab opened by a live page (including `target="_blank"`, supported popups,
the new-tab link setting and link context actions) is closed automatically after
the user cancels/dismisses an HTTP(S) download, or confirms it and DownloadManager accepts it, provided
that tab has never committed a document. The existing transactional close policy
returns selection to its opener and preserves its saved Split pair. Downloads
in existing documents, manually created home tabs, restored tabs
and enqueue failures do not trigger this cleanup. A tab/Space selected in the
meantime is not replaced by the opener. Redirects before the attachment do not
count as a committed document. This cleanup does not apply to page-generated
files, which require a live document to transfer their bytes.

### Generated downloads

`PageDownloads` installs a WebView message listener and a small document-start script on capable WebViews, with a page-finished fallback. Main-frame Blob/data download links are fetched immediately so that subsequent object-URL revocation does not invalidate the export. Programmatic downloads triggered by a user interaction are supported. WebView download callbacks also request a transfer where the object URL is still available.

Native handling accepts only a main frame whose HTTP(S) origin matches the current page, validates metadata, permits one pending download per view, and caps files at 32 MB. The user confirms name, size and source origin before transfer. Bytes move in 48 KB chunks with a bounded reply and timeout, into a private temporary file. Navigation and destruction cancel pending transfers. The host copies the completed file on an IO dispatcher to MediaStore Downloads on API 29+, or app-specific Downloads on API 26–28, without overwriting an existing file. Partial output is removed on failure.

Generated files have a separate low-importance notification with the filename,
percentage and transferred/total bytes, followed by a saving state and a final
success or failure state. Navigation/destruction removes cancelled progress;
success is reported only after the destination copy finishes. Android 13+ asks
for notification permission after the user accepts a generated download.
Denying it still permits saving. Generated transfers remain tied to the live
page and host lifetime; they are not process-resilient background downloads.

This channel exposes no app-file read method or arbitrary native calls. It is not a credential bridge. Cross-frame exports and files larger than 32 MB are not supported. This implementation received a focused source review; it is not a full security audit or a claim of sandboxed adversarial runtime validation.

## Link and image drag

The main manifest declares AndroidX WebKit's non-exported, URI-granting `DropDataContentProvider`. In native page-drag mode, Nagi supplies the destination URL explicitly for link drags, and leaves image/text drag handling to WebView so Android can transfer actual image data. Right-click retains Nagi's context actions. The persisted “Drag links and images” setting can restore the previous long-press menu.

Native Android drop receivers in pane address bars and blank pages accept HTTP(S) URL drops, highlight while entered, and navigate the receiving pane. They ignore file/content URIs and executable schemes. Website content retains WebView's own drop handling for fields and supported upload controls. Support for arbitrary web applications and external destination apps depends on their own drop handling.

Link drags show a rounded host-name card with the existing Lucide link icon,
a soft shadow, and the current Space's light/dark surface colors. The card sits
above the touch point. Address-bar drop feedback follows the bar's pill shape
with a thin outline and subtle tint; blank-page targets keep their rounded shape.
WebView-started drags use the same card while preserving their original payload.
Their preview fits the native drag surface, which can reduce its size.

## Reloading a pending page

Before an explicit URL navigation commits, WebView's current history entry can
still be the previous document or `about:blank`. Reload restarts that original
GET request, including its supplied headers, and lets WebView follow redirects
again. Headers are not attached directly to a redirect destination. A late
blank-page history callback cannot replace an active web page's URL or saved
metadata. Once a document commits, native reload remains responsible for normal
refreshes and POST resubmission handling.

## Sleeping tabs

`EnginePool` tracks access with a monotonic clock. Once per minute, when more than 6 pages are live, it checks background pages idle for at least 10 minutes, oldest first. Settings can request immediate background suspension. The selected and split tabs remain protected even while Settings is open or the Activity is stopped.

The WebView adapter rejects loading/restoring pages, pending file selection or permissions, active generated downloads, fullscreen content and pages granted media capture. It checks playing media, edited form fields, selected files, password values, contenteditable content and open dialogs. Input events are tracked conservatively on supported pages. Eligibility is checked again against engine identity, visibility and access time before destroying a page.

Sleeping tabs show a zZ indicator. Opening one recreates the WebView and restores its history and scroll snapshot where available, falling back to the last URL. At most 32 full snapshots are retained; older sleeping tabs keep only their page metadata. Closing a tab discards its snapshot. No live DOM or arbitrary JavaScript state is preserved, so applications with hidden state can still lose it on reload. Cookies remain managed by WebView.

## Validation

On 2026-11-15, the shared download confirmation passed 10 focused API-36 UI
checks across 411dp phone and 1000dp tablet widths in light and dark themes.
They cover HTTP cancellation, generated-file metadata and Back dismissal, and
tablet Split/opener draft restoration after acceptance. Separate focused cases
also passed generated-file progress/save and background-tab selection after
acceptance. All 193 Play Debug unit tests passed; Play Debug/Release lint each
reported 0 errors and 81 warnings, and Debug/test APK assembly passed.
Screenshots and exact test selections are in ignored
`artifacts/download-dialog-20261115/`. This confirmation UI check did not verify
ordinary HTTP DownloadManager scheduling, physical devices or live Moodle.

The subsequent notice-card restyle passed the same 10 focused UI checks again,
plus a separate outside-tap dismissal check using a local PDF attachment.
Debug/test APK assembly and Play Debug/Release lint passed with 0 errors and
81 warnings each. Updated screenshots and evidence are in ignored
`artifacts/download-dialog-card-20261115/`.

Camera contract tests cover full-size content-URI delivery and cancellation. Engine tests cover capture hints, Blob revocation, multi-chunk byte integrity, Unicode CSV, real MediaStore output, Android image drag with a readable image URI, edited-form protection, and history/scroll restoration. Pool tests cover age thresholds, protected/unsafe tabs, visibility races and closed snapshot disposal. UI cases exercise the service picker, manual suspension/wake and link drop to the right pane.

Physical camera hardware, third-party app drop destinations and OEM-specific behavior still need device validation.

On 2026-10-06, GitHub and Play Debug APK builds, Play Release Kotlin compilation, all 108 unit tests and 21 selected instrumentation tests passed. The instrumentation run used a dedicated API 36 tablet emulator with WebView 133.0.6943.137 and frozen APK copies, avoiding another test run using the shared emulator/build outputs. The cases include real link drop to the right pane, image content-URI transfer, Downloads bytes, history/scroll restoration and ordinary address-bar touch/copy behavior. Debug lint reported 0 errors and 40 warnings. These changes are local development work and have not been published as a release by this task.

Sources: [WebView file chooser and capture](https://developer.android.com/reference/android/webkit/WebChromeClient.FileChooserParams), [WebView image drag provider](https://developer.android.com/reference/androidx/webkit/DropDataContentProvider), [Android drop targets](https://developer.android.com/develop/ui/views/touch-and-input/drag-drop/view), [WebView message APIs](https://developer.android.com/reference/androidx/webkit/WebViewCompat).

These integrations are included in Nagi 0.1.2. See [0.1.2 release validation](VALIDATION.md#2026-10-06--012-release-validation) for the current release checks.

## Adaptive dark mode

Settings → Appearance → Adaptive dark mode is off by default, including upgrades.
When enabled, it allows native WebView algorithmic darkening only while the
**effective app theme is dark** (including System mode when the system is dark).
Light mode retains the site's normal appearance. Providers without
`WebViewFeature.ALGORITHMIC_DARKENING` show a disabled setting with an explanation.
Nagi uses [AndroidX WebKit's supported darkening API](https://developer.android.com/develop/ui/views/layout/webapps/dark-theme),
without injecting a color-inversion script. Authored dark themes and explicit
website opt-outs remain under the site's control; colors and contrast can vary.

While this setting and the dark app theme are both active, an HTTP(S) page's
menu includes “Turn off adaptive dark mode for this site”, or “Turn on…” for an
excluded site. Exceptions use the canonical hostname (case/IDN normalized,
trailing dot and port removed), shared across tabs and Spaces. They are saved
atomically in DataStore and retained when the global setting or app theme is
changed. Different subdomains remain separate sites. The site switch affects
Nagi's automatic adaptation; a site's own dark theme remains intact.

`EnginePool` distributes the setting and exclusions to live engines and applies
current values before loading/restoring new or suspended engines. Each engine
applies the destination hostname's policy for links, redirects, history and
restored documents. Theme, global and site switches update the native setting
in place, preserving the live document, drafts, scroll and Split pairing.
Reader mode uses its own app-themed presentation. Private browsing inherits the
initial normal-workspace choices, then keeps its settings and site exceptions
in memory for the private task lifetime.
