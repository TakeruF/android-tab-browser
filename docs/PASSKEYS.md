# Passkeys in Nagi

Nagi enables the native WebAuthn implementation in supported Android System WebView versions. Pages can use `navigator.credentials.create()` and `navigator.credentials.get()` for passkeys. This is an engine integration; a completed registration and sign-in with a real credential provider has not yet been verified.

`WebViewBrowserEngine` checks `WebViewFeature.WEB_AUTHENTICATION` before setting `WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER`. This keeps the website origin and relying-party checks in WebView and delegates authentication to the device credential UI. Unsupported WebView versions keep their default behavior. No JavaScript credential bridge, origin rewriting, certificate bypass or private-key storage is added to Nagi.

Dependencies: AndroidX WebKit 1.14.0, Credential Manager 1.6.0 and its Google Play services adapter. The manifest declares `android.permission.CREDENTIAL_MANAGER_SET_ORIGIN`, required for browser calls on behalf of websites. Website credentials do not belong to Nagi's app identity; the browser mode is intentional, rather than the app mode that uses Digital Asset Links for app-owned sites.

## Provider approval and release requirements

Google Password Manager requires approval for a browser to access third-party website credentials. Adding the WebView setting and Android permission does not provide that approval. Other providers also control which browsers they trust. Availability therefore depends on the installed WebView, Android version, configured credential provider and its browser policy.

On 2026-11-01, the live [Google privileged-app list](https://www.gstatic.com/gpm-passkeys-privileged-apps/apps.json) contained no entry for `com.takeruf.nagi` (73 Android package entries; retrieved JSON SHA-256 `0891ee2507894999ead3eb4f42e17936a2e8ed8f8fd95592535c9811ef319481`). This is a provider-eligibility blocker for Google-managed website passkeys, not a diagnosis of a particular physical-device error. Do not describe Google passkeys as working on the strength of the engine tests or password-autofill support. Google provides an [approval request form](https://docs.google.com/forms/d/e/1FAIpQLScRuk9tTg0QPfSWl9SbpbIorX8xx2FCXlmoUYftCX2MxG4qyg/viewform).

The approval request was submitted through that official form on 2026-11-01. The browser displayed **“Your response has been recorded.”** The application includes the Play deployment certificate, the exported hybrid classical/PQC certificates, and the legacy release APK certificate, all marked `release`. The request explains that the legacy APK key is also the upload key and does not substitute it for the Play deployment identity. Google approval remains **pending**; form acceptance is not approval or proof of working passkey authentication. The form states that responses usually take fewer than ten business days, with possible follow-up questions. Submission/contact details are retained only in ignored local artifacts.

Before real-provider release validation, request browser approval using the final production signing certificate. The unsigned Release APK is not an approval identity, and the local Debug certificate is not a substitute for the production certificate.

Known request details:

- App: Nagi
- Android package: `com.takeruf.nagi`
- Purpose: a general Android tablet web browser handling passkey registration and authentication for the HTTPS website displayed in WebView
- Integration: AndroidX WebKit native browser mode and Android Credential Manager; the WebView validates the website origin and relying-party ID
- Legacy APK distribution / Play upload-certificate SHA-256: `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`; see [RELEASING.md](RELEASING.md). This is **not** the Play app-signing certificate. Approval for Play-delivered builds must include the app-signing SHA-256 verified in Play Console; approving an upload key alone does not approve the Play-delivered binary.
- Play deployment-certificate SHA-256, verified in Play Console's App signing page and its exported `deployment_cert.der` on 2026-11-01: `4E:89:50:A7:92:28:86:BA:49:50:48:ED:FD:C4:6F:00:91:34:F1:F4:C8:E6:52:64:EC:86:13:CA:51:E9:9E:49`. The same fingerprint appears in Console's generated Digital Asset Links snippet. The exported bundle also contains hybrid classical/PQC certificates; retain those fingerprints with the request if the approval reviewer needs all Play signing variants. This verifies Console configuration, not an installed Play-delivered APK or approval.

After approval, verify registration, authentication, cancellation, screen-lock/biometric handling and recreation on a physical device with the intended provider. Conditional/autofill mediation is not claimed; the Android WebView guide currently documents conditional mediation as unsupported.

For the Google path, first verify the approval reply and the package/certificate entry in the live privileged-app list. Then identify the installed Play-delivered build and its signing certificate before testing. Record native provider UI, completed registration and sign-in, cancellation, origin/RP rejection, recreation and split-pane targeting separately. The earlier engine tests remain necessary checks but cannot replace those real-provider results.

Password autofill's manual retry is separate from passkey authentication. It cannot initiate a site's WebAuthn login, supply its server challenge, or enable conditional passkey suggestions. A site's explicit passkey sign-in button still depends on native browser support and provider approval. Do not rewrite website origins or switch a general-purpose browser to app-owned-site mode to work around approval.

## Verified scope

`PasskeyEngineTest` passed on API 36 with Android System WebView 133.0.6943.137:

- Browser WebAuthn mode and the origin permission are active, and the page sees the create/get APIs.
- A request for an unrelated relying-party ID is rejected with `SecurityError`.
- An already canceled authentication request rejects with `AbortError`.

These tests do not create a passkey or authenticate an account. Google Password Manager approval, authentication with a Play-delivered binary, physical-device authentication and complete website registration/sign-in remain unverified.

On 2026-11-01, all three `PasskeyEngineTest` cases passed again on API 36 with WebView 133.0.6943.137. This remains engine/security-boundary coverage only; no passkey was created and no account was authenticated.

Sources: [Android WebView integration](https://developer.android.com/identity/sign-in/credential-manager-webview), [browser mode API](https://developer.android.com/reference/androidx/webkit/WebSettingsCompat#WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER), [privileged browser approval](https://developer.android.com/identity/sign-in/privileged-apps).

The historical initial-integration Debug/unsigned Release builds, 39 unit tests and 35 emulator tests passed; lint reported no errors and 24 warnings. Those historical results do not establish current production signing or provider approval.
