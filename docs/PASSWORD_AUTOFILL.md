# Password autofill

Nagi uses Android's Autofill Framework for website usernames and passwords. The WebView has an explicit resource ID and is marked important for autofill. Its native Compose host excludes only itself, allowing WebView's virtual HTML form fields to be traversed. WebView supplies the website domain, HTML field metadata and autocomplete hints to the selected system provider; Nagi does not read a password vault or add a JavaScript credential bridge.

## Use

1. Open Nagi settings → Password autofill → Choose autofill service.
2. Select Google or another installed password manager in Android settings. If the device lacks the service picker, Nagi opens general Android settings.
3. Open a website's login form and tap the username or password field. Choose an available suggestion from the provider or keyboard.

Suggestions depend on the website, Android System WebView version, keyboard, device settings and provider. Providers may restrict which browsers can receive website credentials, including by signing-certificate allowlists. Enabling Autofill does not establish Google Password Manager approval or guarantee that existing Google website passwords appear for Nagi. Do not add app-to-site associations for arbitrary sites or rewrite their origins to bypass these checks.

This is separate from [passkeys](PASSKEYS.md). Nagi adds no vault, password persistence, import/export or automatic credential-saving logic. Submission and save prompts remain under WebView and the provider.

## Validation

`WebViewEngineTest.passwordProviderFillsWebsiteFieldsThroughComposeSurface` uses a debug-only AutofillService and a device-local login fixture to exercise the real Compose/native host, domain and HTML field discovery, autocomplete hints, system suggestion selection and delivery to both fields. The test restores the previous device autofill service. The harness only offers synthetic credentials to the loopback fixture and is excluded from Release builds.

`ReleaseFeaturesUiTest.passwordAutofillSettingsOpenAndroidServicePicker` verifies the settings entry opens Android settings. Real Google Password Manager and third-party provider checks on a physical device remain required, including existing website credentials, cancellation, navigation, split panes and multi-step login forms.

On 2026-10-06, all 12 selected instrumentation cases (the complete WebView engine suite and the settings-picker case) passed on an isolated API 36 tablet emulator with WebView 133.0.6943.137. GitHub and Play Debug APK builds, Play Release Kotlin compilation and all 98 GitHub Debug unit tests passed. GitHub Debug lint reported 0 errors and 37 warnings. These checks do not prove access to a real Google password vault.

Sources: [Autofill setup and Activity context](https://developer.android.com/identity/autofill/autofill-optimize), [WebView virtual form structure](https://developer.android.com/reference/android/webkit/WebView), [provider web-domain verification and browser allowlists](https://developer.android.com/reference/android/service/autofill/AutofillService#web-security).
