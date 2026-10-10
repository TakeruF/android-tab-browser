# Nagi 0.3.0 publication verification

Verified 2026-10-10. Package `com.takeruf.nagi`, versionCode `7`, minSdk 26, targetSdk 36.

- Release source: `8e64092bc6a4f497fc30d826339b46cb6b5e171e`. Both signed artifacts built from a clean, isolated checkout of this commit; `v0.3.0` points to this source.
- Unit tests: GitHub 145 and Play 134, with zero failures, errors, or skipped tests. Release Lint: zero errors; 48 GitHub and 59 Play warnings.
- API-36 ARM64 `Nagi_Release021_QA`: 32 instrumentation cases passed in the final combined run (389.911 seconds). Coverage includes address actions at four widths and menu ordering, Reader/private storage isolation and cleanup, network/cosmetic blocking and exceptions, trackpad versus touchscreen input, Split targeting, WebView scrolling, PiP, video popup dragging/resizing, and cross-origin frame playback. Initial runs were interrupted by the emulator's System UI startup ANR and are excluded from successful results. Physical devices, older Android/WebView providers, and general production video compatibility remain untested.
- Signed Release upgrade: installed the publicly downloaded 0.2.1 APK, opened a uniquely identified Example Domain tab, upgraded in place to 0.3.0, and cold-launched the app. The saved tab and full URL remained. Installed non-debuggable package metadata and foreground Activity were verified; installed APK bytes matched the final release SHA-256.
- APK v2 signature matches the existing distribution certificate: `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`. ZIP alignment passed for 16 KB pages, and ARM64/x86_64 adblock ELF LOAD segments are aligned to 16 KB.
- APK: 16,645,964 bytes; SHA-256 `9d258ca8d3b9ae14c781c5bd6c258a90acc26fdbac0a2b293668a4ef53aca8a0`.
- Play AAB: 14,295,150 bytes; SHA-256 `83568f9a61f7175721374080508588802a3f5faa16246f2e81f75b4e374e17cd`. JAR signature and bundletool validation passed. Manifest has version 7/0.3.0 and excludes the APK installer permission and updater provider; Play R8 output excludes the GitHub updater implementation.
- [GitHub v0.3.0](https://github.com/TakeruF/android-tab-browser/releases/tag/v0.3.0) is public and latest, with the APK, `update.json`, and `SHA256SUMS`. Re-downloaded public latest metadata and APK; version, size, checksum, and bytes match the signed local artifact.

Local build/runtime/upgrade evidence is retained in ignored `artifacts/0.3.0/validation/`; public downloads are in `artifacts/0.3.0/public-verification/`.

## Google Play closed-test continuity

Uploaded the signed `7 (0.3.0)` AAB to the existing Alpha track with four-language notes and 100% rollout. Submitted the release and the four corrected full descriptions as five changes. Publishing overview confirms **Changes in review** and **5 changes sent for review**, with managed publishing still off. Alpha remains **Active**; 0.3.0 is **In review**, while 0.2.1 remains **Available to selected testers**. This verifies submission, not approval or 0.3.0 tester delivery.

The tester selection, `AndroidClosedJP@googlegroups.com`, feedback address `support@takeruf.com`, and Android/web opt-in links were compared before and after submission and match exactly. All 178 targeted country/region rows also match. The dashboard still reports 12 testers opted in for 3 days continuously. No track pause, new track, tester change, country change, signing-key change, managed-publishing change, or production promotion was performed. Console reports no loss of supported devices. Its only warning recommends optional native debug symbols.

Evidence: [review submission](play-store/console-030-review.png), [active Alpha and both releases](play-store/console-030-alpha-active.png).

## takeruf.com publication

Published through the EdgeOne project connected to `TakeruF/me` main. [PR 26](https://github.com/TakeruF/me/pull/26) merged as `33afe728d3e6cb13bf4ffa59bddcb0521047818f`; [PR 27](https://github.com/TakeruF/me/pull/27) aligns the lower story facts, date and release section, merged as `2a9da4ba19480d7fc48a63537182a5ff64e96514`. Both PRs passed GitHub web CI and Vercel preview checks. Local ESLint, four-language static builds and the 60-page export verification passed. Vercel checks are separate from the EdgeOne production serving takeruf.com.

The four localized product pages, privacy policies and homepages were checked on the public domain. Product pages show 0.3.0 / 16.6 MB in the hero and story, the October 10 update date, new feature descriptions and the correct APK checksum. Privacy policies cover isolated private storage, Reader/blocking, local processing, and list-provider connections. The publicly downloaded APK matches the GitHub artifact exactly. All six older APK URLs remain available. Public HTML and the downloaded APK are retained in ignored `artifacts/0.3.0/website-verification/`.

The original website worktree's 27 unrelated modified paths were preserved; site changes were built and committed in an isolated worktree. The dedicated release QA emulator was shut down after verification.
