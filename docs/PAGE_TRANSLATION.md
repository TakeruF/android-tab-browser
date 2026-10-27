# Web page translation

Open the upper-right **… → Translate this page**, choose the page language (automatic detection by default) and target language, then select **Translate**. The card contains just the two language selectors and Cancel/Translate buttons; its own **…** menu contains the Wi-Fi download option and **About translation**. The language options appear in the same top-right notice card as external-app notices, using shared positioning, corners, border, header, and button styles. It does not dim or block the rest of the page. A single-row toolbar shows preparation, translation, completion, or a recoverable error on the left and **Show original** (or **Cancel** during preparation) on the right. Translation settings remain available from the page menu. **Show original** restores text without reloading the page; the same action is available in the page menu. Translation options allow retrying or switching languages. The target language is saved immediately when selected, even if the notice is canceled; later pages and app launches use that last choice until it is changed again. Before the first explicit choice, the target follows the app language.

Translation is powered by [Google Translate](https://cloud.google.com/translate) through ML Kit's [on-device translation SDK](https://developers.google.com/ml-kit/language/translation). Language detection is bundled with the app. Translation models download on demand, approximately 30 MB each. Downloads use Wi-Fi by default; uncheck that option in the translation card's **…** menu to permit mobile data. A preparation timeout reports connection/storage trouble instead of waiting indefinitely. Models are cached by ML Kit for later offline use. Canceling translation invalidates the page session; a model download already submitted to the SDK may still complete.

Page text is processed locally, without a cloud translation API, API key, external translation page, or injected third-party script. ML Kit can send SDK diagnostics/usage information to Google; local inference does not mean the SDK makes no network requests. See [Google's SDK data disclosure](https://developers.google.com/ml-kit/android-data-disclosure). Before distributing a version containing these SDKs, reconcile the public privacy policy and Play Data safety declarations with that disclosure. This implementation does not publish a release or change store declarations.

## Automatic translation

The translation card's **… → Always translate this language** option stores a source-to-target rule. If the page language is set or already detected, toggling the option updates that rule immediately. With automatic source detection, enabling the option is saved when **Translate** identifies the source. Changing the target while an applicable rule is enabled updates the rule; disabling the option removes it.

Rules are retained in DataStore and apply to matching pages across tabs and direct URL navigation. A tab's manually chosen pair takes precedence during its own link continuation. Each destination is detected independently; an unrelated language is left alone unless it has its own rule. Automatic language detection failures are silent. Automatic model downloads use Wi-Fi by default, while a link continuation keeps the download preference used for that tab's translation. **Show original** leaves the current document in its original language; enabled global rules still apply to later matching documents. Private workspaces retain the existing session-only settings behavior.

## Behavior

- Each browser engine owns its translation state and original text. A successful translation records its source/target pair in that tab. Following a link in the same tab carries that pair across redirects; after loading, the destination is identified again and is translated only if its source language matches. A different or unknown source ends the manual continuation chain. Reload and Back/Forward also recheck the language before continuing. Typing/opening a URL directly clears the manual chain. New tabs start without their parent tab's manual translation state. **Show original** and Reader mode clear the manual chain; starting translation from Reader returns to the web page. Document jobs are always canceled on navigation, so late results cannot alter the destination.
- Only DOM text nodes change. Elements, links, event handlers, form values, scripts/styles, and editable text retain their original behavior. Code blocks, hidden content, and `translate="no"` / `.notranslate` regions are skipped.
- Added text nodes and website text updates are translated while the session remains active. Native results include a session token and expected original text, preventing old results from replacing a new document or newer website content. Restoration changes only nodes that still contain this session's translated text.
- The document language is updated after translated text is applied and restored with the original. Original text is held only in the live page, not in browser storage. An active translation keeps its WebView out of automatic tab suspension; polling pauses when its pane is hidden.
- Requests are bounded and repeated strings use a small per-session cache. A page is limited to 10,000 recorded text nodes / 2 million source characters, with a 32,000-character per-node limit. A visible notice identifies partial coverage on oversized pages. Long supported text nodes are translated in bounded chunks.

Images, PDFs, iframe documents, shadow DOM, attributes/tooltips, and text made visible only by a CSS/attribute change are outside this initial implementation. Inline markup can split sentence context between text nodes. ML Kit is intended for everyday translation; non-English language pairs pass through English and quality varies. A manual page-language choice is available when detection is incorrect or unsupported.

## Attribution

The compact result toolbar keeps status, the small unmodified official Google Translate badge, and the original/cancel action in one row. The translation action visibly says **Translate with Google** (localized in the app's four languages); provider information and notices remain available under **… → About translation**. See [ML Kit usage guidelines](https://developers.google.com/ml-kit/language/translation/translation-terms) and [attribution requirements](https://cloud.google.com/translate/attribution). The official greyscale/white badge follows the current theme without recoloring or cropping.

Google's translation disclaimer is available in the translation notice's information view:

> THIS SERVICE MAY CONTAIN TRANSLATIONS POWERED BY GOOGLE. GOOGLE DISCLAIMS ALL WARRANTIES RELATED TO THE TRANSLATIONS, EXPRESS OR IMPLIED, INCLUDING ANY WARRANTIES OF ACCURACY, RELIABILITY, AND ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.

## Verification

`TranslationNavigationTest` uses real link taps and local HTTP redirects to verify same-language continuation, stopping on language changes, direct-entry/new-tab isolation, always rules, cancellation, and per-site display-mode reloads. Its opt-in real-model case verifies a linked English page is automatically translated to Japanese. `TranslationNavigationStateTest` covers the per-tab policy, and `TranslationSettingsTest` verifies rules survive reopening the disk store.

`PageTranslationEngineTest` uses real WebViews with an injected deterministic text backend to verify DOM updates, original restoration, dynamic text, escaping, unchanged form/editable content, late results, cancellation/navigation, per-engine isolation, and error/retry behavior. `PageTranslationUiTest` verifies the page-menu entry, source/target choices, Wi-Fi preference, restoration, narrow panes, and disabled new-tab actions.

The real-model smoke test is opt-in because it downloads models from Google. Run it on a dedicated emulator after installing the Debug app and instrumentation APK:

```sh
adb -s <serial> shell am instrument -w \
  -e class com.takeruf.nagi.PageTranslationEngineTest#realModelTranslatesPageOnDevice \
  -e translationModelSmoke true \
  com.takeruf.nagi.test/androidx.test.runner.AndroidJUnitRunner
```

This checks English detection, model download, actual English-to-Japanese page translation, and original restoration. A deterministic backend pass alone does not prove model download availability or translation quality. Physical devices and mainland-China model-download connectivity require separate verification.
