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

## Bare HTTPS host recovery

If a trusted, otherwise valid certificate only proves `www.<host>`, the engine
cancels the failed bare-host TLS request and navigates to that www host through
normal certificate validation. It does not retry hosts already starting with
www, IP literals, unrelated certificate names, or certificates with additional
trust/date faults. Path, query, explicit port and fragment are retained.

Current-main-document matching accepts WebView's canonical empty-root slash,
host/scheme case and default ports, and ignores fragments. It still requires the
same origin, raw path and query, so another image/frame URL cannot trigger the
recovery. Previously `https://afpbb.com` did not match WebView's
`https://afpbb.com/` SSL callback; the failure was cancelled but ignored, and the
30-second watchdog eventually showed a timeout.

On 2026-11-24, a real Nagi WebView reproduced that timeout while the direct www
control rendered AFPBB News. After the URL comparison fix, both mobile and
desktop checks reached `https://www.afpbb.com/` with no page error and an AFPBB
page title. At that time, HTTP bare-host access returned 301 to www, while bare
HTTPS failed hostname validation before any HTTP redirect. These are live,
time-sensitive emulator results; physical-device/provider behavior is separate.
`AfpbbLiveNavigationTest` requires explicit `nagiLiveAfpbb=true` opt-in and is not
a deterministic fixture. `CertificateHostRecoveryTest` covers root equivalence,
origin/path/query exclusions and the existing target-host restrictions. Evidence
is in ignored `artifacts/command-layout-20261124/`.
