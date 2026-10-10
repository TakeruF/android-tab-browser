# Third-party licenses

Nagi source code and documentation use the [MIT License](LICENSE). Dependencies and tools retain their respective licenses.

## Libraries included in the APK

The 127 modules resolved from `releaseRuntimeClasspath` on 2026-10-04, including transitive dependencies, are listed in [THIRD_PARTY_NOTICES.txt](app/src/main/assets/licenses/THIRD_PARTY_NOTICES.txt). This inventory does not imply that every class remains after R8.

| Library / data | License / source |
| --- | --- |
| Mozilla Readability 0.6.0 | [Apache-2.0](app/src/main/assets/licenses/Readability-LICENSE.txt) |
| Brave adblock-rust 0.13.2 / Android wrapper 0.1.3 | [MPL-2.0](app/src/main/assets/licenses/Adblock-MPL-2.0.txt), [Rust dependency notices](app/src/main/assets/licenses/Adblock-Rust-NOTICES.txt), [source and provenance](third_party/adblock/README.md) |
| EasyList / EasyPrivacy, The EasyList authors | [CC-BY-SA-3.0](app/src/main/assets/licenses/EasyList-CC-BY-SA-3.0.txt), [source](https://easylist.to/) |
| AdGuard Japanese filter, AdGuard filter authors | [GPL-3.0](app/src/main/assets/licenses/AdGuard-Filters-GPL-3.0.txt), [source](https://github.com/AdguardTeam/AdguardFilters/tree/master/JapaneseFilter) — separately bundled, unmodified filter data; [marked WebView compatibility derivative](app/src/main/assets/adblock/adguard-webview-compat.txt) |
| AndroidX / Compose / Material icons / Room / DataStore | [Apache-2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| Kotlin / Kotlinx Coroutines / Serialization / JetBrains annotations | [Apache-2.0 and individual Kotlin notices](https://github.com/JetBrains/kotlin/blob/v2.2.21/license/README.md) |
| Coil 3.3.0 | [Apache-2.0](https://github.com/coil-kt/coil/blob/3.3.0/LICENSE.txt) |
| OkHttp 4.12.0 / Okio 3.15.0 | [Apache-2.0](https://github.com/square/okhttp/blob/parent-4.12.0/LICENSE.txt) / [Okio](https://github.com/square/okio/blob/parent-3.15.0/LICENSE.txt) |
| Accompanist / Guava listenablefuture / JSpecify | [Apache-2.0](https://github.com/google/accompanist/blob/v0.37.3/LICENSE) / [Guava](https://github.com/google/guava/blob/master/LICENSE) / [JSpecify](https://github.com/jspecify/jspecify/blob/v1.0.0/LICENSE) |
| Lucide static 1.52.0 (additional icons) | [ISC and Feather MIT](app/src/main/assets/licenses/Lucide-LICENSE.txt) |
| Lucide Android 2.2.1 / Lucide/Feather icons | [MIT (Android library)](app/src/main/assets/licenses/Compose-Icons-MIT.txt) / [ISC and Feather MIT](app/src/main/assets/licenses/Lucide-LICENSE.txt) |
| Google Play services / Google ID (transitive Credential Manager dependencies) | [Android SDK License](https://developer.android.com/studio/terms.html) |
| Protocol Buffers repackaged by DataStore | [BSD-3-Clause](app/src/main/assets/licenses/BSD-3-Clause-Protobuf.txt) |
| ThreeTen backport in Kotlin time | [BSD-3-Clause](app/src/main/assets/licenses/BSD-3-Clause-ThreeTen.txt) |
| Boost-derived code in Kotlin JVM math | [BSL-1.0](app/src/main/assets/licenses/Kotlin-boost_LICENSE.txt) |
| Public Suffix List in OkHttp | [MPL-2.0](app/src/main/assets/licenses/MPL-2.0.txt) — [original NOTICE](https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/resources/okhttp3/internal/publicsuffix/NOTICE) and [source data](https://publicsuffix.org/list/public_suffix_list.dat) |

Original license texts and copyright notices are stored in [assets/licenses](app/src/main/assets/licenses/) and bundled in Debug/Release APKs, including GWT/Guava texts inherited through Kotlin collections/unsigned JVM. WebView and the Android framework are provided by the device.

## Development and test tools

These tools are used for building/testing and are not bundled in the Release APK.

| Tool | License / source |
| --- | --- |
| Gradle 8.13 / included Wrapper | [Apache-2.0](https://github.com/gradle/gradle/blob/v8.13.0/LICENSE) — `META-INF/LICENSE` is retained in the Wrapper JAR |
| Android Gradle Plugin / Kotlin compiler / KSP / AndroidX Test | [Apache-2.0](https://github.com/google/ksp/blob/main/LICENSE) |
| JUnit 4.13.2 | [EPL-1.0](https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt) |
| Robolectric 4.16.1 | [MIT](https://github.com/robolectric/robolectric/blob/robolectric-4.16.1/LICENSE) |

## Updating dependencies

Run `./gradlew -I scripts/license-inventory.gradle :app:licenseInventory` to export resolved modules to `app/build/reports/licenses/release-runtime-modules.txt`. Inspect the dependency tree with `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`.

Check each release's POM, LICENSE, and NOTICE, and update the inventory and bundled originals. Include licenses for repackaged dependencies and data files. Changes to the project LICENSE must also be reflected in bundled `Nagi-MIT.txt`.

Repository screenshots are captures made while verifying Nagi. External product/service references are attributed in [ARC_REFINEMENT.md](docs/ARC_REFINEMENT.md).
