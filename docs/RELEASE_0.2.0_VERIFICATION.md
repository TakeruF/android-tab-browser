# Nagi 0.2.0 publication verification

Verified 2026-10-07. Package `com.takeruf.nagi`, versionCode `5`, minSdk 26, targetSdk 36.

- Release source: `3fb7ddae231d050c890fc14974348f05c269a1d7`. Built the GitHub APK and Play AAB from an isolated, clean checkout of this commit.
- Unit tests: GitHub 124, Play 113; zero failures/errors. Release Lint: zero errors, 41 GitHub warnings and 52 Play warnings.
- API-36 tablet emulator: all 22 SidebarDragTest and CrossFeatureUiTest cases passed, including the two-card pair drag preview and fading both source cards. This instrumentation run used Debug source equivalent to the release except version metadata; signed Release verification is recorded separately below.
- Signed Release: APK v2 signature verified; non-debuggable, version 0.2.0/code 5. Certificate SHA-256 matches the existing distribution certificate (`a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`).
- Signed Release upgrade smoke: installed public 0.1.3 on a fresh API-36 tablet, opened a local Left page tab, upgraded in place to 0.2.0, and verified that the tab and URL remained and the page reloaded. The existing developer-preview emulator was also updated to 0.2.0 Debug without clearing its saved pair.
- [GitHub v0.2.0](https://github.com/TakeruF/android-tab-browser/releases/tag/v0.2.0) is public and latest. Its three assets are `nagi-0.2.0.apk`, `update.json`, and `SHA256SUMS`. Re-downloaded public latest JSON and APK; size/version/checksum match the local artifact.
- APK: 5,228,171 bytes; SHA-256 `7712f1191f3b1eeb6f7b668c9a72a63d9d6e72ccd7688a07390ee36ee9c954e0`.
- Play AAB: JAR signature verified; SHA-256 `86777c9171027e91393fbac6535b9433dbbd384a2526b39ec5f7cad5f285d5d3`.

## Google Play

Uploaded `5 (0.2.0)` to the existing Alpha track and submitted the sole release change with four-language notes. Publishing overview confirms **Changes in review**, with quick checks running; Alpha remains **Active**, 0.2.0 is **In review**, and 0.1.3 remains **Available to selected testers**. This is submission evidence, not approval or tester delivery of 0.2.0. No tester, country, signing-key, or track settings were changed. Console reports no supported-device loss; the sole warning recommends optional native debug symbols.

Evidence: [review submission](play-store/console-020-review.png), [active Alpha and both releases](play-store/console-020-alpha-active.png).

## Website

[Website PR #23](https://github.com/TakeruF/me/pull/23) merged as `9720b7718a7a91112acc12c1f00fec63d73d9bde`. It updates all four localized product pages and homepage cards, same-channel upgrade guidance, feature descriptions, and the signed APK/checksum. Older download files remain available. Local four-locale static build, export verification, ESLint, diff check, PR CI, and Vercel preview passed.

Production main CI and Vercel deployment passed. Verified the canonical production URLs for all four Nagi product pages, homepages, and privacy pages after EdgeOne propagation. The downloaded production APK matches the GitHub checksum above. The official site now serves 0.2.0 at https://takeruf.com/nagi.
