# Localization and search engines

## Interface languages

Nagi supports English, Japanese, Korean, and Simplified Chinese. Translations cover the home screen, sidebar, menus, settings, suggestions, find in page, history, permission/download prompts, validation errors, and accessibility descriptions. Website content and user-defined names are unchanged. Built-in Personal/Work names and empty tabs are translated at display time without rewriting stored data.

The device language determines the default; unsupported languages fall back to English. Android 13+ allows per-app language selection, while Android 8–12 follows the device setting. The implementation uses `android:localeConfig` and standard Android resources, following [Android's per-app language guide](https://developer.android.com/guide/topics/resources/app-languages).

English strings live in `app/src/main/res/values/strings.xml`, with translations in `values-ja`, `values-ko`, and `values-zh`. As of 0.1.1 there are 278 English string resources, including one non-translatable app-name resource, and 277 in each translated file. Preserve numbered placeholders such as `%1$s`. Compose resolves resources from the current Configuration. Shared input validation and suggestion generation receive display-time translations; commands can be found by both translated and English names.

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
