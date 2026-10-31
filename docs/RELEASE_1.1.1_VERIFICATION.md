# Nagi 1.1.1 pre-release verification

Verified on 2026-10-31. Package `com.takeruf.nagi`, version **1.1.1 / code 10**, minSdk 26, targetSdk 36. The user requested the patch-version increase from 1.1.0 / code 9.

This is a **local working-tree candidate**, based on `e56a48f26d87bc1428e88259bd0283b5267f89b5` plus the existing authentication and compact-UI changes. It has not been committed, published, or submitted to Google Play. Existing unrelated work was retained. Evidence is in ignored `artifacts/1.1.1-preflight/`; `source-start.json` fingerprints the source used by the final full suite. The checks below do not establish that a future clean publication build is identical.

## Completed checks

| Check | Result |
| --- | --- |
| GitHub Debug unit tests | 162 passed, no failures/errors/skips |
| Play Debug unit tests | 151 passed, no failures/errors/skips |
| Font diagnostic Python tests | 35 passed |
| Final complete GitHub instrumentation | **221 passed, 7 opt-in skips, 0 failures**; 228 cases, 47 classes, 610.576 seconds |
| Play feature instrumentation | **19 passed**, no failures; 35.589 seconds |
| Cross-process synthetic persistence | Seed, desktop verification, mobile verification, cleanup: all four phases passed in different processes |
| Signed Release upgrade | Public signed 1.1.0 → candidate 1.1.1 update, storage and workspace verification passed |
| Signed Release runtime smoke | Link/back, Split creation/closing, real Japanese→English translation and restoration passed; 20.253 seconds |
| Signed Release offline translation | Cached-model translation and restoration passed with both guest interfaces down; 3.992 seconds |
| Signed Release native-display identity | Tablet and phone configurations: selected mode retained across a cold restart; outgoing UA matched independently obtained WebView-default UA |
| GitHub Release lint | 0 errors, 57 warnings |
| Play Release lint | 0 errors, 68 warnings |
| GitHub / Play Debug lint | 0 errors; 58 / 68 warnings respectively |
| Signed GitHub APK | Existing certificate, signature, 10/1.1.1 metadata and non-debuggable flag verified |
| Signed Play AAB | JAR signature, bundletool validation, metadata and non-debuggable flag verified |
| Native packaging | 16KB ZIP alignment and all ten ELF64 libraries' LOAD alignment passed in both archives |
| Channel boundaries | Play excludes install permission and GitHub updater; both Release manifests exclude debug harness services |
| Release assets | ML Kit notices present; human-verification script bytes match source in both archives |
| Final source check | App/build source hashes still match the final-suite manifest; `git diff --check` passed |

The final suite covers the existing browser features, CJK composition, private-window flags, synthetic native autofill, video/PiP, narrow/wide UI, bottom search bar, per-host modes, redirects, popup handoff, synthetic first/third-party cookies, content-blocking controls, translation, and Google/Turnstile recovery UI. It exercises actual page behavior and retains the playback, filled-value and storage assertions.

The seven opt-in skips were live ChatGPT, live Sazanka, public Google sign-in, consented Google identifier entry, font diagnostics, native-font diagnostics, and explicit multi-process persistence. Persistence was then executed separately as the four passing phases above. No live-account login is implied by the synthetic engine/recovery tests.

## Environment and signed runtime evidence

All mutating runtime checks used the newly created **Nagi_111_PreRelease_QA** AVD (`emulator-5554`), Android 16/API 36 ARM64, WebView **156.0.8078.47**. The final full run used host GPU rendering: Android Emulator OpenGL ES Translator / Apple M5. Tablet configuration was 2560×1600 at density 320, smallest width 800dp. The additional phone configuration was 1080×2400 at density 420, smallest width 411dp. Original size/density were restored afterward.

The signed upgrade retained **two Spaces, five open tabs (six total including one closed tab), one pinned tab, one Split relationship and four Favorites**. Before/after Room snapshots compare original open IDs, URLs, positions, pin/Split/parent relationships, Space active selections and Favorite records. Browser Cookie, localStorage and IndexedDB markers remained readable through the public Release UI; the per-host mobile choice survived. The separate blackbox package has no dependency on Nagi's Debug classes.

The pin is explicitly a **synthetic retention fixture**: the helper's public pin drag did not establish a pin, so one synthetic tab's pin flag was seeded directly in the baseline Room database on this owned emulator while Nagi was stopped. Cookie/browser storage was untouched. `pin-fixture-provenance.json` records that distinction. This validates pin-state retention across the update; it is not evidence that the blackbox pin gesture succeeded. The full Debug sidebar gesture tests passed separately.

Cross-process persistence used four distinct PIDs. In both desktop and mobile verification processes, synthetic HttpOnly Cookie presence in the jar and transmission on the wire were true, JavaScript visibility remained false, and localStorage remained present. The seed waited six seconds before a deliberate process stop. This is a bounded persistence experiment, not a guarantee against arbitrary immediate process termination.

For offline Release verification, both `eth0` and `wlan0` were brought down. An external-route probe returned code 2, `Network is unreachable`. Cached-model translation and original-text restoration still passed. Interface and network-setting states were restored in `finally`. The inspected `release-translated.png` shows translated text, attribution and the original-text action on one row.

The installed Release APK's SHA-256 matches the candidate below. The final crash-buffer capture was empty. Screen timeout and stay-awake settings were restored to their recorded original values; the owned fixture server and emulator were stopped, retaining their evidence and AVD. No physical device was modified during this pre-release run.

## Findings, diagnostic corrections and unsuccessful runs

Earlier failures are retained as evidence and are not counted as a passing full run:

- The first software-rendered full run finished with 218 passes, three failures and seven skips in 1339.871 seconds. Private-mode closing encountered a transient missing Compose hierarchy; cross-frame video did not start from the injected tap; the synthetic autofill suggestion did not fill the page. The first focused rerun passed all three, so the original video/autofill causes were not conclusively isolated.
- The private-window test now tolerates zero Compose roots during Activity transitions, then requires a resumed normal Activity without `FLAG_SECURE`; private mode still must have that flag. Video tests wait for playback readiness, a visual commit and a fresh frame-rectangle revision before injecting the native tap. The autofill fixture observes safe drawing insets and requires a visible, stable suggestion rectangle before tapping. Filled values and playback remain required. Three further focused runs passed all three cases (nine passes).
- A host-rendered full run was intentionally stopped after a Japanese composition timeout. Debug IME provenance now exposes an input-start sequence, and the test requires a fresh, coherent editor connection after opening the omnibox. **The stale-connection hypothesis was not proven**: five comparisons with the fresh-start guard disabled and five with it enabled all passed. Two complete CJK-class repetitions then passed all eight cases. The interrupted full run is not the final full-suite result.
- The final unsharded full run passed 221/228, with the same 228 case IDs as the initial run and only the seven explicit skips. The seven source changes made during this QA investigation are under `androidTest` or `debug`; Release production source stayed unchanged after the original candidate build. No Release application fix is inferred from the test-harness corrections.
- The first phone-width blackbox check classified any UA containing `Mobile` as forced-mobile mode. The public native-mode preference had persisted, but the WebView-default phone UA itself contains `Mobile`. The replacement check compares the outgoing header hash with `WebSettings.getDefaultUserAgent` obtained independently in the QA package, and checks the disabled/selected public menu option before and after cold restart. Both tablet and phone passed. The first replacement also queried the text child's enabled state instead of the clickable menu row; inspecting the accessibility tree identified and corrected that helper mistake. These were test-classification failures, not demonstrated application persistence defects.
- A second task-created AVD aborted during host-GPU startup before Nagi installation. No app-test results are attributed to it; the final suite ran completely on the primary AVD.

Raw earlier logs, the IME comparison, final case IDs, source hashes, signed-workspace snapshots and native-identity hashes remain separate. Diagnostic evidence records only synthetic data, booleans, counts, versions and identity hashes; real credentials, Cookie values, tokens and account identifiers were not collected.

## Candidate artifacts

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `nagi-1.1.1-candidate.apk` | 84,256,917 | `ec31e2d178263a9d03343f56e74876eec8e466ef9d304b6bae575d857f2e742b` |
| `nagi-1.1.1-candidate.aab` | 73,640,382 | `6912e584e567be2388fffd4aa08b35f4bd2f7084826737345a6a56f8eaf46422` |

Distribution/upload-certificate SHA-256: `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`. A local AAB signature is separate from Google Play's app-signing identity. Artifacts are retained under `artifacts/1.1.1-preflight/`.

## Boundaries and remaining checks

The runtime kernel page size was **4096 bytes**. ZIP/ELF alignment success does not establish actual 16KB-page runtime compatibility. Android 8–15, OEM keyboards/autofill vaults, foldable hardware, physical-device regressions, mainland-China networking and general DRM/production-video behavior were not verified here. Phone sizing uses the same emulator and is not a separate physical phone result.

Logged-in ChatGPT, live Sazanka Turnstile success and completed Google authentication were not rerun in this emulator pre-release pass. The existing [authentication investigation](DESKTOP_AUTH_INVESTIGATION.md) distinguishes normal Google service login from embedded OAuth restrictions and records the physical-device observations. Recovery hints and native mode are compatibility assistance, not proof that Cloudflare or Google accepts this browser in every session.

The [Japanese font investigation](JAPANESE_FONT_INVESTIGATION.md) separately records the OPPO/font-fallback findings. No global font/CSS override was added to this candidate. The Python helper tests validate the diagnostic tool, not logged-in Docs rendering. Translation-provider changes remain deferred as requested.

Before publication, build from the chosen clean source revision and separately verify its artifact hashes/signatures and delivery. This report establishes the local candidate and emulator results only.
