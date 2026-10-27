# Nagi 1.1.0 release verification

Verified on 2026-10-27. Package `com.takeruf.nagi`, version `1.1.0` / code `9`, minSdk 26, targetSdk 36.

The final GitHub release is public. Google Play production 1.1.0 has been submitted for review; store availability is not established by submission. Initial working-tree evidence is retained in ignored `artifacts/1.1.0-preflight/`; final clean-build and publication evidence is retained separately in `artifacts/1.1.0-release/`.

## Initial preflight baseline

The following counts and artifact hashes describe the candidate before the final, user-approved Google attribution adjustment. The publication candidate is rebuilt and checked from its clean release source; its final evidence is recorded separately below.

| Check | Result |
| --- | --- |
| GitHub Debug unit tests | 158 passed, no failures/errors/skips |
| Play Debug unit tests | 147 passed, no failures/errors/skips |
| Complete GitHub Debug instrumentation | 194 passed, 2 opt-in live identity tests skipped, no failures/errors; 196 cases across 41 classes, 509.242 seconds |
| Play Debug feature instrumentation | 14 passed, no failures/errors/skips; release features, translation controls and Google recovery UI, 27.436 seconds |
| Signed Release blackbox phases | Baseline seeding, upgrade verification, runtime smoke and cached-model offline translation passed |
| GitHub Release lint | 0 errors, 57 warnings |
| Play Release lint | 0 errors, 68 warnings |
| Signed GitHub Release APK | Signature v2 and existing distribution certificate verified; non-debuggable |
| Play Release AAB | JAR signature and bundletool validation passed; non-debuggable |
| Native packaging | 16KB APK ZIP alignment and all ten ELF64 libraries' LOAD alignment passed in both artifacts |
| Channel boundaries | Play excludes `REQUEST_INSTALL_PACKAGES` and the GitHub update provider; debug IME/autofill harnesses absent from Release manifests |
| License assets | ML Kit notices included in both artifacts |

The distribution/upload-certificate SHA-256 is `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`. This local AAB signature is separate from Google Play's app-signing identity.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| GitHub APK | 84,251,982 | `3796d6f786424802e8f8422532416d7d488db715999cb760cf6e1aa146df10e7` |
| Play AAB | 73,622,883 | `62d0c8011dd43a8894dbc300d26a77d792754c55af02d9ac95678577e53ae836` |

## Runtime validation

The dedicated `Nagi_110_Release_QA` tablet uses Android 16/API 36, ARM64, 2560×1600 at density 320, and Google Android System WebView 156.0.8078.47. The original tablet emulator received the 1.1.0 Debug update in place and cold-launched successfully, preserving its app data.

The suite covers tabs, Split, narrow/wide transitions, keyboard and CJK composition, Favorites gestures, four languages, history, Reader, private storage, content blocking, page translation, Google rejection recovery, video/fullscreen/PiP and native autofill. Live Google identity tests remain opt-in and are not enabled for this preflight.

The retained, signed 1.0.0 APK (code 8) was installed on the dedicated emulator, seeded with a local browser-storage fixture and a per-host mobile-display choice, then updated in place to the signed 1.1.0 APK. A separate test application exercised only public UI and package metadata, without depending on Nagi's Debug classes. Cookie, localStorage and IndexedDB markers remained readable. Before/after Room snapshots confirm the same three open tabs, URLs, positions, pin/split state, Space IDs, active selections and Favorites. The mobile-display choice also survived. Installed package metadata is 9/1.1.0 and non-debuggable; installed APK bytes match the verified candidate SHA-256.

The same R8 Release passed link/back navigation, Split creation and focused-pane closing, real Japanese-to-English translation, the compact single-row result bar, and original-text restoration. With cached models, both guest network interfaces were then forced down and an external route probe returned `Network is unreachable`; translation and restoration still passed in 5.676 seconds. Network settings and interface states were restored afterward. The inspected result screenshot is retained as `artifacts/1.1.0-preflight/release-translated.png`.

## Findings and corrections

- Sidebar page settling could force a remeasure from inside an enclosing measure pass after rotation/resizing. Defer settling to the next frame; retain the existing rotation/history regression assertions.
- Update the passkey fixture to load an actual loopback document instead of a synthetic HTTPS base URL. Strict wrong-RP `SecurityError` and cancellation `AbortError` assertions remain intact.
- Await asynchronous IME edits and Dialog window focus before asserting editor state. Scroll lazy lists to their requested item before selecting offscreen controls.
- Correct mouse horizontal-axis test signs using [Android's axis definition](https://developer.android.com/reference/android/view/MotionEvent#AXIS_HSCROLL) and exercise both axes in separate settled bursts. Commit resized WebView content and end its wheel latch before changing scrollers in the touchpad fixture.
- Require stable rendered pixels after a Favorite landing animation. Scroll video controls into view and wait for native rendering while allowing the Compose test clock to advance before injecting taps.
- Wait for link layout to be committed after translation/restoration before injecting native taps. Give the real-model navigation smoke test the model-download budget instead of its former 20-second cached-model budget; deterministic fixtures keep their shorter timeout.
- A subsequent model-download failure was traced to the QA emulator's DNS failure. Restarting only that emulator with explicit DNS restored name resolution and network validation; both real-model translation and same-tab translation continuation passed in the focused rerun.

Earlier failing runs and focused reruns are retained separately; they are not counted as a passing full suite.

## Publication preparation

The user approved a small official Google Translate mark in the single-row bar on 2026-10-27. The result bar now uses the unmodified official greyscale/white asset and the translation button visibly uses localized **Translate with Google** wording. See [ML Kit guidelines](https://developers.google.com/ml-kit/language/translation/translation-terms) and [attribution requirements](https://docs.cloud.google.com/translate/attribution).

The privacy-policy update explains local text processing separately from ML Kit diagnostics, installation identifiers and language metadata, following [Google's SDK disclosure](https://developers.google.com/ml-kit/android-data-disclosure). Four-language policy changes passed website lint, static builds and export verification in [website PR 35](https://github.com/TakeruF/me/pull/35), merged as `960eb383c404ae7339c0c0831695ac336f366977`. Live deployment and Play Data safety changes are verified before store submission. The initial preflight's publication findings are addressed by this release-preparation work rather than waived.

After the attribution change, both unit suites passed again (158 GitHub + 147 Play = 305), both Release lint tasks and signed packaging passed, complete GitHub instrumentation passed 195/197 with the two opt-in identity diagnostics skipped, and Play feature instrumentation passed all 15 cases. The real-model smoke tests were enabled. The new 280dp-wide result-bar test verifies the official attribution and Show original action remain in one row. All four deployed privacy pages returned HTTP 200 with the updated ML Kit disclosure and 27 October date.

Final source, artifact and publication evidence follows.

## Final clean build and publication — 2026-10-27

[App PR 1](https://github.com/TakeruF/android-tab-browser/pull/1) merged as `6b5d0de9fec1d14cbded57609a6613604e16c944`. The APK and AAB were rebuilt in an isolated checkout of that exact commit, verified clean before and after building. The repository had no required remote checks or pending reviews; local test results above cover the same app source. Both Release lint checks had zero errors (56 GitHub / 67 Play warnings).

| Final artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| GitHub APK | 84,252,470 | `befa7d79d107dbedae664ad6ef54332c3ffea67972bac40b4cbc5e9d427ed253` |
| Play AAB | 73,624,560 | `8935879f251ad1ae2acab7aaa165070b8a8cf4ea22b50ab48473cf36600c06d7` |

APK signature, existing distribution certificate, non-debuggable 9/1.1.0 metadata, bundle signature/validation, channel-specific manifest boundaries, license assets, ZIP alignment and all ten ELF64 libraries' alignment passed again. These are packaging checks, not proof of execution on a 16KB-page device. Google Play accepted the uploaded AAB as 9 (1.1.0), confirming its upload identity is accepted by the existing app.

The separate blackbox test package passed all four final phases: 1.0.0 storage seeding (5.915s), in-place 1.1.0 upgrade verification (2.184s), signed runtime navigation/Split/real-model translation/restoration (15.547s), and cached-model translation/restoration offline (4.314s). Cookie, localStorage, IndexedDB, three original open tabs, two Spaces, Favorites, IDs, ordering, active selections, pin/split state and per-host mobile choice survived. The installed APK hash matches the final artifact. The offline route probe failed with `Network is unreachable` while both guest interfaces were down, and original network settings were restored. The final screenshot shows the small official attribution and Show original action on one row. The original user tablet emulator received the current branded Debug build in place and Nagi remained in the foreground.

[GitHub v1.1.0](https://github.com/TakeruF/android-tab-browser/releases/tag/v1.1.0) publishes the APK, `update.json`, and `SHA256SUMS`, tagged at the clean source commit above. The downloaded APK signature and digest match. An unauthenticated download also returned exactly 84,252,470 bytes with the expected hash, and the public latest-update URL returns 1.1.0 / code 9 with that digest.

Google Play's Publishing overview showed **Changes in review** after sending six changes: Production **1.1.0, Start full rollout**, all four full descriptions, and Data safety. Managed publishing is **off**. Rollout is 100% in the existing 178 countries/regions. The upload retained the same supported-device counts (11,470 phones; 6,480 tablets; no lost devices). The only release warning was increased APK download size; Console estimated 14 MB for new installs and 8.12 MB for updates. SDK initialization and translation feature diagnostics/identifiers are declared separately from on-device page text; the existing approximate-location declaration remains. Automated quick checks were still running after submission. This records submission for review, not approval or actual delivery of 1.1.0.

Four updated privacy-policy URLs returned HTTP 200 with the current Google ML Kit disclosure and date. Website [PR 36](https://github.com/TakeruF/me/pull/36) updates the version, checksum, size, four-language feature copy and both product/homepage download buttons. The new universal APK exceeds [EdgeOne Makers' 25 MB per-file limit](https://pages.edgeone.ai/document/limits-and-quotas), so current download buttons use the verified GitHub asset while previous website APK URLs are retained. PR 36 passed its `web` CI at the corrected head `042a09f8b8d33acc13dbc1f4dace3a6bcaed6c92`, including the homepage-link review fix, and merged as `2c04e69a61b67d00173e6d8ee9062824b720fb18`. Local lint, all four locale builds and 68-page export validation passed. Deployed English, Japanese, Simplified Chinese and Korean home/product pages all returned HTTP 200 and pointed at the verified 1.1.0 asset, with no old download link. Product pages displayed 84.3 MB and the final checksum; EdgeOne Last-Modified values were 09:45:43–09:45:48 UTC on 27 October. The previous 1.0.0 APK remains available at its historical URL.

## Boundaries

Native autofill uses a synthetic, debug-only provider. Google Password Manager vault access, real third-party managers, completed Google sign-in and passkey-provider approval are not established. No personal account identifiers are included in the preflight text evidence.

Physical tablets/folds, manufacturer keyboards/trackpads, Android 8–15 runtimes, actual 16KB-page execution, mainland-China networking, DRM and general production embedded-video compatibility remain unverified. Translation covers page text, excluding image and PDF contents.
