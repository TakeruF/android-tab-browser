# Everyday browser integrations

## Camera uploads

`FileSelectionRequest` preserves WebView's capture preference. Single-image JPEG-compatible or unrestricted inputs offer Take photo / Choose files; image inputs requesting capture launch the camera directly. Other MIME types and multiple selection retain the document picker. Android camera permission is requested when the user selects photography.

The camera writes a full-size JPEG to `cache/camera-uploads/` through the separate, non-exported `UploadFileProvider`. Success returns its content URI; cancellation, denial, unavailable camera apps and empty results finish the file callback without a selection. Files remain available for upload until the Activity host is disposed. Document picker replies cannot expose Nagi's private providers. The GitHub APK update provider remains separate.

Video/audio recording and folder selection are outside this first implementation.

## Generated downloads

`PageDownloads` installs a WebView message listener and a small document-start script on capable WebViews, with a page-finished fallback. Main-frame Blob/data download links are fetched immediately so that subsequent object-URL revocation does not invalidate the export. Programmatic downloads triggered by a user interaction are supported. WebView download callbacks also request a transfer where the object URL is still available.

Native handling accepts only a main frame whose HTTP(S) origin matches the current page, validates metadata, permits one pending download per view, and caps files at 32 MB. The user confirms name, size and source origin before transfer. Bytes move in 48 KB chunks with a bounded reply and timeout, into a private temporary file. Navigation and destruction cancel pending transfers. The host copies the completed file on an IO dispatcher to MediaStore Downloads on API 29+, or app-specific Downloads on API 26–28, without overwriting an existing file. Partial output is removed on failure.

This channel exposes no app-file read method or arbitrary native calls. It is not a credential bridge. Cross-frame exports and files larger than 32 MB are not supported. This implementation received a focused source review; it is not a full security audit or a claim of sandboxed adversarial runtime validation.

## Link and image drag

The main manifest declares AndroidX WebKit's non-exported, URI-granting `DropDataContentProvider`. In native page-drag mode, Nagi supplies the destination URL explicitly for link drags, and leaves image/text drag handling to WebView so Android can transfer actual image data. Right-click retains Nagi's context actions. The persisted “Drag links and images” setting can restore the previous long-press menu.

Native Android drop receivers in pane address bars and blank pages accept HTTP(S) URL drops, highlight while entered, and navigate the receiving pane. They ignore file/content URIs and executable schemes. Website content retains WebView's own drop handling for fields and supported upload controls. Support for arbitrary web applications and external destination apps depends on their own drop handling.

## Sleeping tabs

`EnginePool` tracks access with a monotonic clock. Once per minute, when more than 6 pages are live, it checks background pages idle for at least 10 minutes, oldest first. Settings can request immediate background suspension. The selected and split tabs remain protected even while Settings is open or the Activity is stopped.

The WebView adapter rejects loading/restoring pages, pending file selection or permissions, active generated downloads, fullscreen content and pages granted media capture. It checks playing media, edited form fields, selected files, password values, contenteditable content and open dialogs. Input events are tracked conservatively on supported pages. Eligibility is checked again against engine identity, visibility and access time before destroying a page.

Sleeping tabs show a zZ indicator. Opening one recreates the WebView and restores its history and scroll snapshot where available, falling back to the last URL. At most 32 full snapshots are retained; older sleeping tabs keep only their page metadata. Closing a tab discards its snapshot. No live DOM or arbitrary JavaScript state is preserved, so applications with hidden state can still lose it on reload. Cookies remain managed by WebView.

## Validation

Camera contract tests cover full-size content-URI delivery and cancellation. Engine tests cover capture hints, Blob revocation, multi-chunk byte integrity, Unicode CSV, real MediaStore output, Android image drag with a readable image URI, edited-form protection, and history/scroll restoration. Pool tests cover age thresholds, protected/unsafe tabs, visibility races and closed snapshot disposal. UI cases exercise the service picker, manual suspension/wake and link drop to the right pane.

Physical camera hardware, third-party app drop destinations and OEM-specific behavior still need device validation.

On 2026-10-06, GitHub and Play Debug APK builds, Play Release Kotlin compilation, all 108 unit tests and 21 selected instrumentation tests passed. The instrumentation run used a dedicated API 36 tablet emulator with WebView 133.0.6943.137 and frozen APK copies, avoiding another test run using the shared emulator/build outputs. The cases include real link drop to the right pane, image content-URI transfer, Downloads bytes, history/scroll restoration and ordinary address-bar touch/copy behavior. Debug lint reported 0 errors and 40 warnings. These changes are local development work and have not been published as a release by this task.

Sources: [WebView file chooser and capture](https://developer.android.com/reference/android/webkit/WebChromeClient.FileChooserParams), [WebView image drag provider](https://developer.android.com/reference/androidx/webkit/DropDataContentProvider), [Android drop targets](https://developer.android.com/develop/ui/views/touch-and-input/drag-drop/view), [WebView message APIs](https://developer.android.com/reference/androidx/webkit/WebViewCompat).
