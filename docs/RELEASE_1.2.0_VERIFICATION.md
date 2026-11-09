# Nagi 1.2.0 verification and Play submission

Checked on 2026-11-09 (JST). VersionName **1.2.0**, versionCode **17**.

## Source and artifact

- [PR #14](https://github.com/TakeruF/android-tab-browser/pull/14) is merged. Immutable tag `v1.2.0` identifies clean source `21909b9c22f0d40e68d3244d5a939ffc0ae670b8`. The merged tree matches the tested release-branch tree.
- The release starts from the current production/main baseline, preserving 1.1.3–1.1.7 source history and notes. Camera calibration, video intent/PiP handling and private task lifetime match the shipped implementation; the new private-profile existence guard is retained.
- The signed Play AAB and local signed Release APK were built from the clean tag source. AAB SHA-256: `7c44c38faf138f51bf5d36950827bb14e1a9855f85a376d376cebfcb5e912364`. Google Play's accepted code-17 bundle reports the same digest.
- APK SHA-256: `41a8088c80a41987a77e670324aedaa5249a48dc5735f74207d610e801b19bad`; the installed local signed APK has the same hash.
- Upload-certificate SHA-256 remains `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`. This is distinct from Play's app-signing certificate, rechecked through code-16 generated-APK metadata and Console's App signing page (`4E:89:50:A7:92:28:86:BA:49:50:48:ED:FD:C4:6F:00:91:34:F1:F4:C8:E6:52:64:EC:86:13:CA:51:E9:9E:49`).
- GitHub holds a source release draft without binary assets. Google Play remains the sole official distribution channel.

## Validation

- All 180 Play Debug unit tests passed (zero failures, errors or skips).
- Play Debug/Release lint passed with zero errors and 72 warnings each. Debug and instrumentation APK builds passed.
- All 29 selected API-36 instrumentation cases passed on dedicated `Nagi_Spaces_QA_20261109` (`emulator-5580`): Spaces confirmation and wide/compact light/dark states, preserved live Split WebViews and unfinished inputs, camera registration/rotation, private lifetime/storage/keyboard metadata, Reader, and video pop-out/PiP. This is a focused suite, not a full instrumentation suite.
- The actual signed Release APK was installed and visibly exercised on fresh dedicated API-36 `Nagi_120_Release_QA` (`emulator-5582`). Phone Spaces-off confirmation, hidden Space controls, Pinned tabs, wide Spaces-on confirmation and restored Space controls passed. Stable signed phone/wide screenshots were inspected. Android's MAIN/APP_BROWSER query resolves `com.takeruf.nagi/.MainActivity`. No fatal Nagi crash was found in the fresh emulator's crash buffer.
- Bundletool validation, package/version/non-debuggable checks, AAB/APK signatures, APK 16KB ZIP alignment, bundle 16KB packaging configuration, 64-bit ELF load alignment and notice-asset checks passed. Play contains no install-packages permission or debug IME/autofill provider. Packaging checks do not prove execution on a 16KB-page device.
- Four equivalent Play note blocks are below 500 characters per language.

Physical devices, OEM keyboard behavior, production-provider login and process-death recovery are not established. Private tabs remain memory-only. The Debug confirmation screenshot caught a transient animation frame; stable signed Release screenshots separately establish the inspected layout. One signed UI dump returned a transient null root during resize; the phone/wide flow completed successfully afterward.

## Play state

The live baseline was code 16 (1.1.7), published at 100%. The highest code across uploaded bundles, APKs and tracks was 16 before upload. Code 17 was accepted, saved as a production draft with all four note languages, validated, then committed for a full production rollout. Country availability was compared unchanged before both draft and submission: 177 explicit entries plus rest-of-world availability, shown as 178 countries/regions in Console. SDK levels and required device features are preserved.

The lifecycle API initially returned NOT_SENT_FOR_REVIEW immediately after commit, then returned **IN_REVIEW** on re-fetch. The live Publishing overview confirms **Changes in review → Production → 17 (1.2.0) → Start full rollout**, with quick checks running and **Managed publishing off**. Checks proceed to review automatically; approval triggers publication. **Public availability of 1.2.0 is not yet confirmed.** Code 16 remains published meanwhile.

Compact evidence, signed artifacts, API responses and screenshots are under ignored `artifacts/1.2.0/`. See [developer notes](releases/1.2.0.md), [localized Play notes](play-store/release-notes-1.2.0.txt) and [Google Play](https://play.google.com/store/apps/details?id=com.takeruf.nagi).
