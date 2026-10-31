# Nagi 1.1.1 Google Play preparation

Prepared on 2026-10-31 at the user's request. Version **1.1.1 / code 10**, package `com.takeruf.nagi`, minSdk 26, targetSdk 36. **Saved in Play Console, not submitted for review or published.** Live production remains 1.1.0.

## Distribution and public surfaces

New official releases use Google Play exclusively. The app README, build/publication instructions and four-language 1.1.1 notes now describe Play distribution. The old APK preparation helper rejects versions newer than 1.1.0; this was checked against the actual signed 1.1.1 APK and created no publication output. The legacy source set remains available for historical regression checks. Existing APKs, updater manifests and old tags are retained as dated archives, with a distribution notice added to the public v1.1.0 release.

[takeruf.com PR 47](https://github.com/TakeruF/me/pull/47) merged as `1387db8476ab3b260660c288c8873ebf1991f860`. Its `web` CI, ESLint, four static locale builds and 68-page export checks passed. Both the homepage and Nagi product page now offer Play as the install action, with no Nagi APK download links, APK size, checksum or sideloading instructions. All four privacy bodies identify the former APK channel as legacy; data handling was not changed. Existing archived APK URLs were retained. EdgeOne's `takeruf` production project showed that exact commit running, and all 12 canonical homepage/product/privacy URLs returned HTTP 200 with the updated content.

[Profile PR 2](https://github.com/TakeruF/TakeruF/pull/2) merged as `04f66e68f16eff104c24e8d215b5a7dd28d4735e`. The public README source and rendered Nagi table cell confirm the Play-only install/update statement and removal of the Nagi Releases download action; the existing image loaded, and repository/product links and product ordering were preserved.

Existing APK and Play signing identities differ. An in-place switch is not guaranteed; uninstalling deletes app-private tabs, bookmarks, settings and site storage. Public copy explains that limitation without promising workspace migration.

## Review correction and emulator checks

The initial [1.1.1 preflight](RELEASE_1.1.1_VERIFICATION.md) remains a separate dated record. Its 313 unit passes, 221 complete-suite passes / seven skips, upgrade/storage/offline checks and earlier unsuccessful runs are not silently relabeled as results of a later source revision.

[App PR 3](https://github.com/TakeruF/android-tab-browser/pull/3) received an automated review of `30d1a2d`. One finding was confirmed: after a device-default page requested an attachment from a desktop-mode host, restoring the visible source-page state preserved the actual `desktopMode` but accidentally reused the source's `deviceDefaultMode`. Both could become true, misclassifying the current WebView identity on the next reload.

A real cross-host attachment regression failed before the fix with `expected false, was true` at the device-default flag assertion. Commit `cf1dbfc92891c6e8d7366c0f994dd8d146e17457` preserves both current flags together. The same test then passed in 3.421 seconds, covering device-default↔desktop and device-default↔mobile transitions, visible source preservation, actual download/WebView UA agreement, an additional source request on reload, and native UA/Client Hints restoration. The review thread was resolved. This fixes a confirmed browser-state defect; it does not prove the original live Cloudflare failure's cause.

The first post-review Play full run completed with 219 passes, nine skips and one video-test failure in 703.368 seconds. After requesting native fullscreen, the test immediately looked up `video-minimize`; the failure reported no matching merged node, while the unmerged tree contained one. The helper now waits for a laid-out control in the merged tree, asserts that it is displayed, then injects the click. Playback, pause, resize and PiP assertions remain intact. The precise timing cause was not isolated. Three focused repetitions passed before the final full run. The final run also enables `translationModelSmoke=true`; those two real-model tests were opt-in skips in the unsuccessful initial run. This change is test-only and does not alter the uploaded application binary.

| Check on reviewed Play source | Result |
| --- | --- |
| Play unit tests | 151 passed; no failures/errors/skips |
| Play Release lint | 0 errors, 68 warnings |
| Final complete Play instrumentation | 222 passed, 7 opt-in skips, 0 failures; 229 cases / 47 classes / 644.15 seconds; real-model translation enabled |
| Font diagnostic helper tests | 35 passed |
| Signed Play runtime | Five phases passed: public-UI synthetic storage seeding, navigation/Split/real-model translation/restoration, offline translation/restoration, and cold-start native-identity checks at tablet and phone widths |
| Runtime environment | Owned API-36 ARM64 AVD, WebView 156.0.8078.47, host GPU; tablet 2560×1600 / 320dpi and phone override 1080×2400 / 420dpi |

The standalone Release smoke helper also needed stronger prerequisites. Initial attempts reused a seed-time page/status and did not establish the keyboard-close target after dismissing a pane menu; a different fixture document remained, so the expected marker was absent. Giving the smoke document a unique URL alone did not settle this. The final helper waits for a document-specific DOM marker, checks the source URL before and after navigation/menu dismissal, and explicitly focuses the right document before Ctrl+W. The source URL and storage marker must then survive. Earlier failures and screenshots remain separate. This validates closing after explicit document focus; menu-only focus routing is not established by that blackbox run. No application code or uploaded binary was changed for these helper corrections.

The seven final skips are live ChatGPT, live Sazanka, public Google sign-in, consented Google identifier entry, font diagnostics, native-font diagnostics and explicit multi-process persistence. Both real-model translation cases passed with the opt-in flag enabled. The initial preflight records the four separate persistence phases.

The final full-suite source hash manifest is retained separately. Only this task-created emulator was reset when changing synthetic Debug/Release signing identities; no physical-device profile or personal login was used. Offline checks brought both guest interfaces down, confirmed an unreachable external route, and restored the original network state. Viewport settings were restored afterward. Final Debug and signed-Play crash buffers were empty; installed Play-variant APK bytes match the verified local artifact.

## Signed upload artifact

The reviewed source was built in an isolated clean checkout. Bundletool validation, bundle/APK signatures, 10/1.1.1 metadata, non-debuggable status, 16KB ZIP/ELF alignment, all ten ELF64 libraries, ML Kit notices and Play manifest boundaries passed. There is no APK self-update transport/installer, install permission or debug harness service in the Play artifact. The human-verification script in the AAB matches source.

| Local artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Play AAB uploaded to Console | 73,640,362 | `358f7653b2c09d0e95b8144d45250f691fd3c90060e3841aacec802bdc20ade8` |
| Play-variant APK, local runtime validation only | 84,240,096 | `9f006ef55965c432678a077134bf8d8ba69a63d2fbc25715cd9fab2583f6f94d` |

Local upload/distribution certificate SHA-256: `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`. Play acceptance verifies the configured upload identity; it is separate from the Play app-signing identity and does not establish APK-to-Play migration. The immutable [v1.1.1 source tag](https://github.com/TakeruF/android-tab-browser/tree/v1.1.1) records the release source. Application code matches the clean reviewed build above; later changes concern test readiness and documentation. A clean build from the final source is checked again against the accepted AAB hash. Source/tree/tag and content-equivalence evidence is retained locally. The new GitHub release note is a draft, without binary assets.

## Console stop point

The uploaded AAB was accepted as **10 (1.1.1)**. All four release-note languages were present, each below 500 characters. Preview showed **Ready to release**, no lost supported devices, and the existing full-rollout configuration: 100%, all existing 178 countries/regions. Supported devices: 11,505 phones; 6,486 tablets; five TVs; zero wearables; 27 cars; 77 Chromebooks; one Android XR. Console estimated 14 MB for new installs and 828 KB for updates.

Saving displayed confirmation that the change was saved and must be sent from Publishing overview. That overview shows **Changes not yet submitted for review**, with exactly one Production change, **1.1.1 — Start full rollout**. The remaining action is **Submit 1 change for review**. It was not pressed. Managed publishing remains off. Console quick checks completed without a blocking issue; the saved change remains unsubmitted.

The pending description “Start full rollout” is the configured future change, not evidence that rollout has started. No review submission or publishing action was executed. The saved release remains in the user's browser for review.

## Evidence and limits

Ignored local evidence is under `artifacts/1.1.1-play-release/`: source/artifact hashes, final instrumentation cases, red/green regression logs, unit/lint/build reports, signed runtime phases, network restoration, public-URL results, PR/review snapshots and Console saved-state evidence. AABs are uploaded only to Play; no new public APK, updater manifest or GitHub AAB asset is published.

The runtime page size is 4096 bytes; packaging alignment is not proof of actual 16KB execution. A locally signed Play-variant APK is not the Play-delivered, re-signed/protected binary. Logged-in ChatGPT, live Sazanka Turnstile success, completed Google login, OEM regressions, Google Docs editing and native-font fixes remain outside this release-preparation proof. See the separate authentication and Japanese-font investigations. Translation-provider changes remain deferred.
