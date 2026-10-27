# Nagi 1.1.0 local candidate verification

Verified on 2026-10-27. Package `com.takeruf.nagi`, version `1.1.0` / code `9`, minSdk 26, targetSdk 36.

This is a local candidate from the modified working tree. Source hashes, build outputs and test evidence are retained in ignored `artifacts/1.1.0-preflight/`. No clean release commit, CI/review, store submission, or public distribution is established by these checks.

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

Final artifact, source, public-download and Console states are appended after the release operations complete.

## Boundaries

Native autofill uses a synthetic, debug-only provider. Google Password Manager vault access, real third-party managers, completed Google sign-in and passkey-provider approval are not established. No personal account identifiers are included in the preflight text evidence.

Physical tablets/folds, manufacturer keyboards/trackpads, Android 8–15 runtimes, actual 16KB-page execution, mainland-China networking, DRM and general production embedded-video compatibility remain unverified. Translation covers page text, excluding image and PDF contents.
