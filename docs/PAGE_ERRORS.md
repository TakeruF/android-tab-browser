# Page loading errors

Main-frame WebView failures use a native, themed page with a centered Lucide icon,
a localized title and explanation, and Retry. Connection/DNS/I/O failures use
Wi-Fi off, timeouts use a clock, certificate failures use a shield alert, and
other provider failures use a file warning. The same screen adapts to phone,
tablet and Split panes; long text scrolls within a constrained pane.

`PageState.error` retains the engine diagnostic, while `errorKind` selects the
display-time strings in all six UI languages. Raw provider diagnostics are not
shown in the page. The old red notification strip is removed. The failed native
surface is detached from Compose, including its accessibility tree, while its
engine and history remain owned by the session controller. Retry reuses that
engine; the replacement stays visible with a progress indicator until retry
finishes, so the provider-generated Android error document cannot flash through.

Only main-frame transport failures replace the page. HTTP error responses such
as server-authored 404/500 pages remain visible. Failed images/iframes do not
replace a healthy document. All certificate errors are cancelled; only a failure
for the current main URL shows the certificate screen. Existing certificate-host
recovery still performs a separately validated navigation; this adds no bypass.
The engine's existing 30-second loading watchdog uses the timeout screen.

`PageErrorUiTest` uses a closed loopback port with recovery on the same port,
a runtime-generated untrusted local TLS endpoint, a stalled response and a
server-authored 404. A direct native load of a denied/missing local file covers
generic provider failures without enabling file access. Assertions cover detached error documents, live-engine
reuse, native history, both address placements, narrow/wide windows and themes.
The TLS fixture keeps its ephemeral key in the QA app's Android Keystore and
removes only its own alias afterwards. No public site or network setting change
is required.

Reference: Android's [WebViewClient callbacks](https://developer.android.com/reference/android/webkit/WebViewClient).
