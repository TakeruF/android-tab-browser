package com.takeruf.nagi.ui.settings

/**
 * Third-party components bundled in the APK and the license texts under `assets/licenses/`
 * that cover them. Mirrors THIRD_PARTY_NOTICES.md; names are proper nouns and stay untranslated.
 */
internal data class LicenseEntry(
    val name: String,
    val license: String,
    /** Files under `assets/licenses/`, shown one after another. */
    val files: List<String>,
    /** Where to get the source or the upstream license, when the text is not bundled. */
    val source: String? = null,
)

internal object LicenseCatalog {
    const val DIRECTORY = "licenses"

    val entries = listOf(
        LicenseEntry("Nagi", "MIT", listOf("Nagi-MIT.txt"), "https://github.com/TakeruF/android-tab-browser"),
        LicenseEntry("Mozilla Readability 0.6.0", "Apache-2.0", listOf("Readability-LICENSE.txt"),
            "https://github.com/mozilla/readability/tree/0.6.0"),
        LicenseEntry("Brave adblock-rust 0.13.2 / Android wrapper 0.1.3", "MPL-2.0",
            listOf("Adblock-SOURCES.txt", "Adblock-MPL-2.0.txt", "Adblock-Rust-NOTICES.txt")),
        LicenseEntry("EasyList / EasyPrivacy", "CC-BY-SA-3.0", listOf("EasyList-CC-BY-SA-3.0.txt"), "https://easylist.to/"),
        LicenseEntry("AdGuard Japanese filter", "GPL-3.0", listOf("AdGuard-Filters-GPL-3.0.txt"),
            "https://github.com/AdguardTeam/AdguardFilters/tree/master/JapaneseFilter"),
        LicenseEntry("AndroidX / Jetpack Compose / Room / DataStore", "Apache-2.0", listOf("APACHE-2.0.txt"),
            "https://github.com/androidx/androidx"),
        LicenseEntry("Kotlin / kotlinx.coroutines / kotlinx.serialization", "Apache-2.0",
            listOf("APACHE-2.0.txt", "Kotlin-boost_LICENSE.txt", "Kotlin-guava_license.txt", "Kotlin-gwt_license.txt"),
            "https://github.com/JetBrains/kotlin/blob/v2.2.21/license/README.md"),
        LicenseEntry("Coil 3.3.0", "Apache-2.0", listOf("APACHE-2.0.txt"), "https://github.com/coil-kt/coil"),
        LicenseEntry("OkHttp 4.12.0 / Okio 3.15.0", "Apache-2.0", listOf("APACHE-2.0.txt"), "https://github.com/square/okhttp"),
        LicenseEntry("Public Suffix List (OkHttp)", "MPL-2.0", listOf("MPL-2.0.txt"), "https://publicsuffix.org/list/public_suffix_list.dat"),
        LicenseEntry("Accompanist / Guava listenablefuture / JSpecify", "Apache-2.0", listOf("APACHE-2.0.txt")),
        LicenseEntry("Lucide icons", "ISC / MIT", listOf("Lucide-LICENSE.txt"), "https://lucide.dev/"),
        LicenseEntry("Lucide Android", "MIT", listOf("Compose-Icons-MIT.txt")),
        LicenseEntry("Protocol Buffers (DataStore)", "BSD-3-Clause", listOf("BSD-3-Clause-Protobuf.txt")),
        LicenseEntry("ThreeTen backport (Kotlin time)", "BSD-3-Clause", listOf("BSD-3-Clause-ThreeTen.txt")),
        LicenseEntry("Google Play services / Google ID", "Android SDK License", emptyList(),
            "https://developer.android.com/studio/terms.html"),
    )

    /** The complete inventory of resolved runtime modules. */
    const val INVENTORY = "THIRD_PARTY_NOTICES.txt"
}
