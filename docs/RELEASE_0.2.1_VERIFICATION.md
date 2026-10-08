# Nagi 0.2.1 publication verification

Verified 2026-10-08. Package `com.takeruf.nagi`, versionCode `6`, minSdk 26, targetSdk 36.

- Release source: `849b265e59b01b8f7bc76cb1f3109c67b1eaa5a7`. Built both signed artifacts from a clean, isolated checkout.
- Unit tests: GitHub 133, Play 122; zero failures/errors. Release Lint: zero errors, 43 GitHub warnings and 54 Play warnings.
- API-36 emulator: 24 related Debug instrumentation cases passed across targeted runs. The tests cover repeated completion callbacks, rotation, display toggles, reload, actual link taps and Back revisits, per-host modes, redirects, snapshot restoration, and existing WebView features.
- Signed Release upgrade: installed the public 0.2.0 APK on a fresh emulator, opened a local fixture tab, upgraded in place to 0.2.1, and verified the saved tab and URL remained. Selected mobile display and verified the server received the mobile UA again after a full app-process restart. Physical-device behavior was not tested.
- GitHub APK v2 signature verified, non-debuggable. Certificate SHA-256 matches the existing distribution key: `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`.
- APK: 5,228,171 bytes; SHA-256 `c66d65e1cb0fe250859b5a17ad3862c9ad94b5a3862a17fe7157f6d7d71555e5`.
- Play AAB: JAR signature verified; SHA-256 `59859a80925a3deefb10c3a99a555a56b8713a3b89dd1829404fe3591dda266b`.
- [GitHub v0.2.1](https://github.com/TakeruF/android-tab-browser/releases/tag/v0.2.1) is public and latest, with APK, `update.json`, and `SHA256SUMS`. Re-downloaded public latest metadata and APK; version, size, checksum, signature, and bytes match the local artifact.

## Google Play closed-test continuity

Uploaded `6 (0.2.1)` to the existing Alpha track and submitted the sole release change with four-language notes. Publishing overview confirms **Changes in review**, with quick checks running. Alpha remains **Active**; 0.2.1 is **In review**, while 0.2.0 remains **Available to selected testers**. This verifies submission, not approval or tester delivery of 0.2.1.

The existing tester-group selection and feedback address were compared before and after submission and are unchanged. The same track continues targeting 178 country/region options. The dashboard still reports 12 testers opted in for 2 days continuously. No track pause, new track, tester change, country change, signing-key change, or production promotion was performed. Console reports no supported-device loss; its only warning recommends optional native debug symbols.

Evidence: [review submission](play-store/console-021-review.png), [active Alpha and both releases](play-store/console-021-alpha-active.png).

## Website

[Website PR #25](https://github.com/TakeruF/me/pull/25) merged as `6c0a2b065417cda26af435774c5fc970c76d65dd`. The four localized product pages and download metadata point to 0.2.1; older APK downloads are retained. Local ESLint, four-language static builds, export verification (60 localized pages), and diff checks passed. PR and production CI and Vercel checks passed. Verified the canonical production product pages in Japanese, English, Chinese, and Korean show the 0.2.1 APK link and exact SHA-256; all four homepages and privacy pages return HTTP 200. Re-downloaded the canonical website APK and confirmed its bytes and checksum match the signed release. The rendered English product page shows 0.2.1 and the updated download link.
