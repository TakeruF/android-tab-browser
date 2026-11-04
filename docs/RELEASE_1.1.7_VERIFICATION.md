# Nagi 1.1.7 verification and Play submission

Checked on 2026-11-05 (JST). VersionName **1.1.7**, versionCode **16**.

## Source and distribution

- [PR #12](https://github.com/TakeruF/android-tab-browser/pull/12) is merged. Immutable source tag `v1.1.7` points to `b9b1e03d291138d0701e8d56c6b42b7b520a20f0`.
- Codex code review completed without findings on `6d670c48ada5d357be53310ff5def9c5915a4b80`; its source tree matches the merge/tag tree.
- The signed AAB and local Release APK were built from that clean merge revision. Google Play is the sole official channel. The GitHub release remains a draft without binary assets.
- AAB SHA-256: `2045c189eba14b4db472b164056e4e46385e12c58cb333350fc64f2d6b26a2fc`. Play's accepted code-16 bundle reports the same digest.
- The upload-certificate SHA-256 remains `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`. This is separate from the Play app-signing certificate, rechecked through generated-APK metadata for code 15.

## Verification

- 175 Play Debug unit tests passed, zero failures/errors/skips.
- `:app:lintPlayDebug` and `:app:lintPlayRelease` passed. Debug, instrumentation, signed Release APK and signed Play AAB builds passed.
- 22 selected instrumentation cases passed on API-36 `Nagi_111_PreRelease_QA`: private task lifetime, isolated storage deletion, Compose/WebView IME metadata, Reader and CJK composition. This is a focused suite, not all instrumentation tests.
- The lifetime case verifies Home/launcher return, Activity recreation/reentry, explicit close, and `finishAndRemoveTask()` with the background observer service already stopped.
- The actual signed Release APK was installed on fresh API-36 `Nagi_117_Private_Release_QA`. Its installed hash matches the verified local APK. Tablet/wide and phone/narrow input states were exercised; private tabs and cookies survived Home/launcher reentry, normal cookies stayed isolated, and explicit close removed private tabs and cookies. Android reported private address-editor `imeOptions=0x3000002`, including the no-personalized-learning request. No fatal app crash was recorded.
- Bundletool validation, package/version/non-debuggable checks, AAB/APK signatures, APK 16KB ZIP alignment, bundle 16KB packaging configuration and 64-bit ELF load alignment checks passed. Bundled notice assets remain present. Packaging checks do not prove execution on a 16KB-page device.
- Four-language Play notes are equivalent and below 500 characters per language.

Initial concurrent build output caused an incomplete test APK and missing test classes. Regenerating the test DEX/package outputs resolved this; the final 22-case result above has no failures. Release runtime helpers were adjusted for the narrow search dialog's Close Search control. Launcher swipe injection did not dismiss the Recents card, so an actual swipe gesture on the signed build is unverified; task removal is established separately by instrumentation. Physical devices, manufacturer keyboard privacy indicators and process-death restoration remain unverified. Private tabs remain memory-only.

## Play state

The Play API's existing production release was code 15 (1.1.6), confirmed published; the maximum uploaded code across tracks/bundles was 15 before upload. Code 16 was accepted with all four release-note languages, saved as a production draft, then committed as the replacement full-rollout release. An initial commit rejected the explicit `changesNotSentForReview` parameter; committing the same edit without that parameter succeeded, without reuploading the bundle.

The live Publishing overview subsequently showed **Changes in review → Production → 16 (1.1.7) → Start full rollout**, with **Managed publishing off**. Review submission is confirmed; public availability of 1.1.7 is not yet confirmed. The existing 177 explicit country entries plus rest-of-world availability were preserved. Package, SDK levels and required device features remain unchanged.

Compact evidence and raw local outputs are retained in ignored `artifacts/1.1.7/`. The [Play notes](play-store/release-notes-1.1.7.txt) and [developer release notes](releases/1.1.7.md) describe the release. [Google Play listing](https://play.google.com/store/apps/details?id=com.takeruf.nagi).
