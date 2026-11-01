# Password autofill

## Current availability: awaiting approval

Password autofill is unavailable in production until the submitted approval is granted. `PasswordAutofillAvailability.approved` remains false. WebView native autofill is excluded, manual requests are blocked, the page-menu action and the entire Settings section (including the service picker) are hidden in all locales. No pending-approval notice is shown in the normal UI. Existing Android provider preferences are not changed. The retained integration tests explicitly opt in to exercise future approved behavior with synthetic credentials; they do not indicate that the production feature is enabled. Approval requires a later source change and APK build; it does not enable itself automatically.

The integration details and earlier validation below describe the retained implementation.

Nagi uses Android's Autofill Framework for website usernames and passwords. The WebView has an explicit resource ID and is marked important for autofill. Its native Compose host excludes only itself, allowing WebView's virtual HTML form fields to be traversed. WebView supplies the website domain, HTML field metadata and autocomplete hints to the selected system provider; Nagi does not read a password vault or add a JavaScript credential bridge.

## Use

1. Open Nagi settings → Password autofill → Choose autofill service.
2. Select Google or another installed password manager in Android settings. If the device lacks the service picker, Nagi opens general Android settings.
3. Open a website's login form and tap the username or password field. Choose an available suggestion from the provider or keyboard.
4. If automatic suggestions do not appear, keep the login field selected and open the page menu → Fill saved password. This explicitly requests a new Android autofill context. If no service is enabled, Nagi directs you to the autofill settings; if no text field is selected, it asks you to select one first. This action is unavailable in private browsing and Reader.

The settings section reports whether Android autofill is enabled for Nagi, disabled, or unsupported, and refreshes when returning from Android settings. An enabled status does not mean that the provider has matching passwords or trusts Nagi. Manual retries use WebView's opaque virtual field ID from its native autofill structure or Android UI callback, not a request against the WebView container. A transparent ViewStructure delegate observes only the focused field ID and forwards the original structure unchanged; it does not retain text, values, or HTML attributes. This avoids depending on optional suggestion-UI callbacks to recognize a selected field. If Android reports that the provider cannot show suggestions after a manual request, the message identifies the provider result rather than incorrectly asking the user to tap the field again. Navigation invalidates the ID, and destroying the engine unregisters the callback.

Chrome's Touch to Fill password sheet is browser-specific UI, not a public WebView feature Nagi can enable. Android and the selected provider control whether suggestions appear as a popup, keyboard suggestions, or another system UI. Nagi does not imitate Google's credential sheet or claim access to Chrome's password store.

Suggestions depend on the website, Android System WebView version, keyboard, device settings and provider. Providers may restrict which browsers can receive website credentials, including by signing-certificate allowlists. Enabling Autofill does not establish Google Password Manager approval or guarantee that existing Google website passwords appear for Nagi. Do not add app-to-site associations for arbitrary sites or rewrite their origins to bypass these checks.

This is separate from [passkeys](PASSKEYS.md). Nagi adds no vault, password persistence, import/export or automatic credential-saving logic. Submission and save prompts remain under WebView and the provider.

## Validation

`WebViewEngineTest.passwordProviderFillsWebsiteFieldsThroughComposeSurface` uses a debug-only AutofillService and a device-local login fixture to exercise the real Compose/native host, domain and HTML field discovery, autocomplete hints, system suggestion selection and delivery to both fields. The test restores the previous device autofill service. The harness only offers synthetic credentials to the loopback fixture and is excluded from Release builds.

`WebViewEngineTest.manuallyRequestedPasswordFillsAfterAutomaticSuggestionsAreUnavailable` uses the real page menu and a provider that declines automatic requests. It verifies that the manual request offers a selectable system suggestion and fills both fields after the popup menu closes.

On 2026-11-01, 151 Play Debug unit tests, Play Debug lint, and four focused instrumentation tests passed on API 36 with WebView 133.0.6943.137. The instrumentation covers automatic filling, manual retry with another live browser engine in the same Activity, opening Android's service picker, and status refresh after returning from Android settings. These use synthetic credentials; real Google Password Manager behavior remains unverified. Autofill callbacks are shared across live engines because Android provides one callback slot per Activity and constructing another WebView replaces that slot.

The 2026-11-01 follow-up reproduced the recording's false "Tap a login field first" message by suppressing provider UI callbacks: the original implementation made zero manual requests. Regression coverage adds manual retry without those callbacks, an identifier-only first login step, and confirmation that a manual request reaches a provider that returns no matches. Android does not always send an unavailable-UI callback when a provider returns no matches; Nagi confirms that a manual request was sent and reports unavailable feedback when Android provides it. These fixtures use synthetic credentials and do not establish that Google Password Manager offers the user's saved Asahi credentials.

`ReleaseFeaturesUiTest.passwordAutofillSettingsOpenAndroidServicePicker` verifies the settings entry opens Android settings. Real Google Password Manager and third-party provider checks on a physical device remain required, including existing website credentials, cancellation, navigation, split panes and multi-step login forms.

On 2026-10-06, all 12 selected instrumentation cases (the complete WebView engine suite and the settings-picker case) passed on an isolated API 36 tablet emulator with WebView 133.0.6943.137. GitHub and Play Debug APK builds, Play Release Kotlin compilation and all 98 GitHub Debug unit tests passed. GitHub Debug lint reported 0 errors and 37 warnings. These checks do not prove access to a real Google password vault.

Sources: [Autofill setup and Activity context](https://developer.android.com/identity/autofill/autofill-optimize), [WebView virtual form structure](https://developer.android.com/reference/android/webkit/WebView), [provider web-domain verification and browser allowlists](https://developer.android.com/reference/android/service/autofill/AutofillService#web-security).

These integrations were introduced in Nagi 0.1.2. See [Validation](VALIDATION.md) for dated release and candidate checks.
