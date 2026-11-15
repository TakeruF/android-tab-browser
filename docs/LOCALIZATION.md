# Localization and search engines

## Interface languages

Nagi supports English, Spanish, French, Japanese, Korean, and Simplified Chinese. Translations cover the home screen, sidebar, menus, settings, suggestions, find in page, history, permission/download prompts, validation errors, and accessibility descriptions. Website content and user-defined names are unchanged. Built-in Personal/Work names and empty tabs are translated at display time without rewriting stored data.

The device language determines the default; unsupported languages fall back to English. Android 13+ allows per-app language selection, while Android 8–12 follows the device setting. The implementation uses `android:localeConfig` and standard Android resources, following [Android's per-app language guide](https://developer.android.com/guide/topics/resources/app-languages).

English strings live in `app/src/main/res/values/strings.xml`, with translations in `values-es`, `values-fr`, `values-ja`, `values-ko`, and `values-zh`. Every translatable resource must have an equivalent in each locale. Keep the app name and the required Google translation disclaimer in their non-translatable base resources. Preserve numbered placeholders such as `%1$s`, literal `{query}` URL-template markers, and paragraph breaks. Compose resolves resources from the current Configuration. Shared input validation and suggestion generation receive display-time translations; commands can be found by both translated and English names.

Spanish and French use language-wide resources (`es`, `fr`), including fallback for regional settings such as `es-MX` and `fr-CA`. The public privacy policy is currently available in the original four languages, so Spanish/French Settings deliberately link to the existing English policy rather than nonexistent localized URLs.

The 1.3.0/code-19 replacement includes Spanish and French. Its six-language Play
notes were approved by the user on 2026-11-15; the original code-18 four-language
notes remain historical. The corresponding six-language store copy is recorded in
[the Play listing snapshot](play-store/listings-1.3.0-code19.json). Spanish and
French listings link to the existing English privacy policy. Submission and public
availability are tracked separately in the
[code-19 release verification](RELEASE_1.3.0_CODE19_VERIFICATION.md).

## Spanish and French validation — 2026-11-15

The language commit covers all **420 translatable strings per locale** in the committed app source. XML/key checks found no missing entries, duplicate keys, numbered-placeholder differences, `{query}` changes or paragraph-break differences. The two new Play-note draft blocks contain 394/441 characters.

The initial development-worktree checks covered 423 strings plus tab-count plural forms: the extra three labels and plural resource belong to the pending compact-UI redesign. Those translations and their plural assertions remain uncommitted with that UI, so this language commit has no dependency on it. Existing source files were preserved.

The focused `LocalizationTest` exercises en, ja, ko, zh-CN, es, es-MX, fr and fr-CA, including translated commands/AI suggestions, reordered search placeholders and display-time built-in names while retaining custom names and saved page titles. `LocalizationUiTest#spanish` and `#french` follow the appropriate phone/tab-sheet and tablet flows. Earlier phone runs exposed tablet-only home/Esc selectors; the test was corrected to use the compact Search label and native Back dismissal.

The original development-worktree checks passed the focused unit test, Play Debug/test APK assembly and Debug/Release lint with zero errors and the existing 81 warnings each. Both UI cases passed at 1000×800 dp and approximately 411×914 dp on the dedicated API-36 `Nagi_Localization_QA_20261115` emulator. Home/tab labels, custom names, Settings navigation, search-engine controls, light/dark appearance and localized Command Bar lookup were checked; retained screenshots were inspected. These initial results include the pending compact UI. Evidence is under ignored `artifacts/localization-es-fr-20261115/`.

The independent language-only candidate passed the focused eight-configuration unit test, Play Debug/test APK assembly, and Play Debug/Release lint with zero errors and 81 warnings each. Exact 420-key coverage, formatting and document links were checked separately. Evidence is under ignored `artifacts/localization-es-fr-commit-20261115/`. No additional device test ran for this commit: the dedicated QA emulator did not finish booting within the readiness check and was stopped; the earlier development-worktree UI results remain separate evidence. No full instrumentation suite, physical-device, Android 8–12 runtime, signed-release or Play-distribution claim is made for this addition.

## Additional search engines

| Name | Keyword | Search URL template |
| --- | --- | --- |
| Sogou | `sg` | `https://www.sogou.com/web?query={query}` |
| 360 | `360` | `https://www.so.com/s?q={query}` |
| Douyin | `dy` | `https://www.douyin.com/search/{query}` |
| Shenma | `sm` | `https://m.sm.cn/s?q={query}` |
| Qwen (China Mainland) | `qwen` | `https://www.qianwen.com/?q={query}` |
| Perplexity | `pplx` | `https://www.perplexity.ai/search/?q={query}` |

Together with the original nine engines, these provide 15 defaults. Qwen includes its region in Settings and uses the shorter name in suggestions. Select an engine as a common engine to use keywords such as `qwen Tokyo weather`, `pplx Tokyo weather`, `sg Tokyo weather`, or `dy Korea travel`. Douyin puts the query in the URL path, so spaces are encoded as `%20` rather than `+`. Japanese, Korean, Chinese, and `+`, `/`, `?`, and `&` are encoded as query content.

References: the forms on [Sogou](https://www.sogou.com/) and [360](https://www.so.com/), the search action on [Shenma's mobile home](https://m.sm.cn/), and [Douyin's public search URL](https://www.douyin.com/search/). Tests verify URL generation and in-app selection/display; actual results, login, and regional access are separate conditions.

Existing installations receive the additional engines once. Existing IDs/keywords are not overwritten. Defaults, edits, and custom engines are preserved; later edits or deletions are not reverted at startup.
