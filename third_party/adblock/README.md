# Brave adblock-rust Android integration

Nagi vendors `mlm-games/adblock-rs-kt-wrapper` **0.1.3**, source tag commit
`ef24e5a4f70bae15463aa9af99981e908e9da5ef`, under MPL-2.0.

- Source: https://github.com/mlm-games/adblock-rs-kt-wrapper/tree/0.1.3
- Wrapper sources: https://repo.maven.apache.org/maven2/io/github/mlm-games/adblock/0.1.3/adblock-0.1.3-sources.jar
- Native binaries: https://repo.maven.apache.org/maven2/io/github/mlm-games/adblock/0.1.3/adblock-0.1.3.aar
- Native source: https://github.com/mlm-games/adblock-rs-kt-wrapper/tree/0.1.3/adblock/src/main/rust
- Matching Rust transitive sources and checksums: `Cargo.lock`; each crate can be obtained at `https://static.crates.io/crates/NAME/NAME-VERSION.crate`.

`provenance.json` records original artifact and bundled-file SHA-256 digests.
Kotlin wrappers live in `app/src/main/java/org/mlm/adblock`; native libraries
are extracted unchanged into `app/src/main/jniLibs` for all four ABIs.
The only wrapper change makes `deserializeFrom` a block-body function so the
existing Kotlin 2.2 compiler accepts its early return. JNI keep rules are
copied from the published AAR. No filter parser or matching algorithm is
implemented by Nagi.

The AdGuard Japanese filter is separately bundled, unmodified filter data
under GPL-3.0, attributed to the AdGuard filter authors. Its source is
https://github.com/AdguardTeam/AdguardFilters/tree/master/JapaneseFilter;
the compiled Chromium subscription is fetched from
https://filters.adtidy.org/extension/chromium/filters/7.txt. The bundled
version is 2.0.77.34 (2026-10-10), with its hash in `provenance.json` and
the complete license in `assets/licenses/AdGuard-Filters-GPL-3.0.txt`.
This data supplement supplies Japanese site-specific ad-container selectors,
including Asahi's outer reserved-height banner. It is fed to Brave's existing
parser; unsupported AdGuard extended rules are not implemented by Nagi.
`assets/adblock/adguard-webview-compat.txt` is a separately marked GPL-3.0
derivative of one upstream Japanese rule: Livedoor's `:upward(1)` parent
selection is expressed with standard CSS `:has()` targeting the same parent.
It is always appended after cached list updates so the compatibility rule is
retained. Its original rule, attribution, and source URL are included in the
file; its exact modified source is bundled in the APK. Nagi does not implement
a procedural-selector interpreter.

The published AAR requires compileSdk 37 and Kotlin 2.4.10. Building its small
wrapper source with Nagi's existing SDK 36/Kotlin 2.2 avoids changing the app's
build toolchain. ARM64 and x86_64 ELF LOAD segments were checked for 16 KB alignment.
To rebuild native artifacts, use the upstream Rust crate/Cargo.lock and
cargo-ndk instructions; do not edit the binary files.

The AAR's bundled `resources.json` is **not** included. This integration uses
network matching and domain-specific CSS selectors; it does not enable
scriptlets, resource redirects, generic DOM-selector discovery, or URL
rewriting. It does not promise to block same-origin video advertisements.

EasyList and EasyPrivacy are separate, unmodified filter data, attributed to
The EasyList authors (https://easylist.to/), distributed under the offered
CC-BY-SA-3.0 license. The bundled snapshot is from 2026-10-10; a normal app
startup updates all cached lists from their official HTTPS endpoints when four days
old. Failed or oversized updates retain the previous/bundled lists. All
matching runs locally; no visited URLs or cookies are sent to list providers.
Original license texts and Rust dependency notices are bundled under
`app/src/main/assets/licenses`.
