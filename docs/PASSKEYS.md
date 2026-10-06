# Passkeys in Nagi

Nagi enables the native WebAuthn implementation in supported Android System WebView versions. Pages can use `navigator.credentials.create()` and `navigator.credentials.get()` for passkeys. This is an engine integration; a completed registration and sign-in with a real credential provider has not yet been verified.

`WebViewBrowserEngine` checks `WebViewFeature.WEB_AUTHENTICATION` before setting `WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER`. This keeps the website origin and relying-party checks in WebView and delegates authentication to the device credential UI. Unsupported WebView versions keep their default behavior. No JavaScript credential bridge, origin rewriting, certificate bypass or private-key storage is added to Nagi.

Dependencies: AndroidX WebKit 1.14.0, Credential Manager 1.6.0 and its Google Play services adapter. The manifest declares `android.permission.CREDENTIAL_MANAGER_SET_ORIGIN`, required for browser calls on behalf of websites. Website credentials do not belong to Nagi's app identity; the browser mode is intentional, rather than the app mode that uses Digital Asset Links for app-owned sites.

## Provider approval and release requirements

Google Password Manager requires approval for a browser to access third-party website credentials. Adding the WebView setting and Android permission does not provide that approval. Other providers also control which browsers they trust. Availability therefore depends on the installed WebView, Android version, configured credential provider and its browser policy.

Before real-provider release validation, request browser approval using the final production signing certificate. The unsigned Release APK is not an approval identity, and the local Debug certificate is not a substitute for the production certificate.

Known request details:

- App: Nagi
- Android package: `com.takeruf.nagi`
- Purpose: a general Android tablet web browser handling passkey registration and authentication for the HTTPS website displayed in WebView
- Integration: AndroidX WebKit native browser mode and Android Credential Manager; the WebView validates the website origin and relying-party ID
- Production signing-certificate SHA-256: `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`; signed GitHub/website APK distribution is documented in [RELEASING.md](RELEASING.md). Provider browser approval is still required.

After approval, verify registration, authentication, cancellation, screen-lock/biometric handling and recreation on a physical device with the intended provider. Conditional/autofill mediation is not claimed; the Android WebView guide currently documents conditional mediation as unsupported.

## Verified scope

`PasskeyEngineTest` passed on API 36 with Android System WebView 133.0.6943.137:

- Browser WebAuthn mode and the origin permission are active, and the page sees the create/get APIs.
- A request for an unrelated relying-party ID is rejected with `SecurityError`.
- An already canceled authentication request rejects with `AbortError`.

These tests do not create a passkey or authenticate an account. Google Password Manager approval, production signing, physical-device authentication and complete website registration/sign-in remain unverified.

Sources: [Android WebView integration](https://developer.android.com/identity/sign-in/credential-manager-webview), [browser mode API](https://developer.android.com/reference/androidx/webkit/WebSettingsCompat#WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER), [privileged browser approval](https://developer.android.com/identity/sign-in/privileged-apps).

The final Debug/unsigned Release builds, all 39 unit tests and all 35 emulator tests passed. Lint reported no errors and 24 warnings. These build results do not establish production signing or provider approval.
