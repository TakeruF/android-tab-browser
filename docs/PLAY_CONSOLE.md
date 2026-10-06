# Google Play closed-test preparation

Prepared 2026-10-06 for Nagi 0.1.1 (`com.takeruf.nagi`, versionCode 2, targetSdk 36). This file contains submission copy and source-backed declaration guidance; it does not claim that Console forms have been saved, a release has been submitted, or a test is active.

## 0.1.3 update — 2026-10-06

- Version `4 (0.1.3)` was uploaded and submitted to the existing **Alpha** closed-testing track with release notes in all four languages. Publishing overview confirms **Changes in review** with automated checks running.
- After submission Alpha remains **Active**, 0.1.3 is **In review**, and 0.1.2 remains **Available to selected testers**. No pause, promotion, tester-list change, signing-key change or region change was performed.
- `AndroidClosedJP@googlegroups.com`, `support@takeruf.com`, 178 countries/regions and both opt-in links remain unchanged. Dashboard count was 5 before the update and 6 afterwards. No 14-day completion is claimed.
- Console reports no loss of supported devices. The sole release warning is the optional native debug-symbol recommendation.
- Uploaded AAB SHA-256: `edd15232849edeb9c57f91ce3e17a96bfac166bd231e3c4d39f79d3a20a5ce6a`.
- Website PR [#22](https://github.com/TakeruF/me/pull/22) merged as `805f071`. Main CI and Vercel deployment passed; the separate EdgeOne production was verified live in all four product/privacy locales and homepages. Website APK matches the GitHub release checksum; all 0.1.0/0.1.1/0.1.2 downloads remain available.
- Evidence: [review submission](play-store/console-013-review.png), [active Alpha and both releases](play-store/console-013-alpha-active.png).

## 0.1.2 update — 2026-10-06

- Version `3 (0.1.2)` was uploaded to the existing **Alpha** closed-testing track and submitted with English, Japanese, Simplified Chinese and Korean release notes. Publishing overview shows **Changes in review**; quick checks are still running, so this is not approval or confirmation that 0.1.2 is already delivered.
- After submission, the track remains **Active**, the 0.1.2 release is **In review**, and the 0.1.1 release remains **Available to selected testers**. No track pause, tester removal, new track, production promotion, country change or signing-key change was performed.
- Testers remain `AndroidClosedJP@googlegroups.com`; feedback remains `support@takeruf.com`. The 178 countries/regions and existing Android/web opt-in links are unchanged. The pre-update dashboard showed 4 opted-in testers.
- Console reports zero devices lost compared with the previous release across all form factors. The only release warning is the optional native debug-symbol file recommendation.
- Uploaded AAB SHA-256: `a1c14c534ad3029d0bf91d8d513fe7fb26a97bc9532830f93efe3d8d2e13092a`. The APK updater remains excluded from Play. The existing Play app-signing certificate is retained.
- Website PR [#21](https://github.com/TakeruF/me/pull/21) merged as `2c2a2b7`. Production checks verified all four product/privacy locales and the 0.1.2 APK, matching the GitHub release APK checksum. Older website APK downloads remain available.
- Evidence: [review submission](play-store/console-012-review.png), [active Alpha and both releases](play-store/console-012-alpha-active.png).

## Live Console progress (2026-10-06)

- Distribution was expanded to all 178 Console options: 177 named countries/regions plus Rest of World. Store listing contact email was immediately published as `support@takeruf.com`; tester feedback email and support text in all four listing descriptions were saved and submitted in a seven-change update. **Changes in review** verifies acceptance of the updated submission (automated checks running again). Screenshot: `docs/play-store/console-countries-support-review.jpg`. IARC's administrative contact remains `me@takeruf.com`.

- Store listing title is **Nagi Browser** in all four languages; launcher/app UI name remains **Nagi**. The four title changes were submitted via **Restart review** and verified under **Changes in review**, together with the existing Alpha release and declarations. This restarted the review; automated checks are running again. Screenshot: `docs/play-store/console-name-review.jpg`.

- Play Console accepted the signed Play AAB as `com.takeruf.nagi`, version `2 (0.1.1)`, API 26+, target SDK 36. SHA-256: `fe0f4dd66a5f81f3da79b0098ee07a7174d82333e6874e297836672773cf2852`.
- Closed testing / Alpha release `0.1.1 – Closed test` was included in the review submission. All 178 available countries/regions (including Rest of World) are now targeted. Test availability has not yet been confirmed.
- Privacy policy, app access, ads, adult target audience, Data safety, government, financial and health declarations, Communication category and contact details were saved. English, Japanese, Simplified Chinese and Korean listing text and default visual assets were saved for review. The authored feature graphic carries the AI asset label; screenshots are actual Play release captures.
- After explicit user confirmation, IARC Terms of Use were accepted and the content rating questionnaire completed. It identifies Nagi as a web browser with Unrestricted Internet (IARC Generic 3+, ESRB Everyone, PEGI 3). The intended target audience remains adults (18+). Testers were saved as `AndroidClosedJP@googlegroups.com`, with feedback sent to `support@takeruf.com`.
- Play App Signing is already configured with a different certificate from the GitHub distribution certificate. Current Play SHA-256: `4E:89:50:A7:92:28:86:BA:49:50:48:ED:FD:C4:6F:00:91:34:F1:F4:C8:E6:52:64:EC:86:13:CA:51:E9:9E:49`. Upload certificate matches the existing Nagi distribution certificate. In-place switching between the current Play and GitHub APKs is therefore not supported.
- Advertising ID was saved as **No**. Missing advertising-ID/content-rating declarations were resolved; tester configuration was saved. The optional native debug symbols warning remains.
- All 17 changes were submitted using **Send changes for review**. The verified resulting Publishing overview state is **Changes in review**, with automated quick checks still running and the explanation that changes proceed to review when checks complete successfully. This proves the submission request was accepted, not approval or tester availability. Screenshot: `docs/play-store/console-review-submitted.jpg`.

## Upload and signing

Use only `app/build/outputs/bundle/playRelease/app-play-release.aab`, generated with `./gradlew :app:bundlePlayRelease`. Check that versionCode is higher than any previous Console upload. The Play artifact excludes GitHub APK self-updates, the APK installer, `REQUEST_INSTALL_PACKAGES`, and the update FileProvider.

Configure Play App Signing with the existing Nagi distribution key if in-place migration from the public APK is required. Confirm the actual Play app-signing certificate; signing the AAB with an upload key alone does not establish that compatibility. Expected existing distribution certificate SHA-256: `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`.

## Policy and support URLs

- Privacy policy: https://takeruf.com/nagi/privacy
- English: https://takeruf.com/en/nagi/privacy
- Japanese: https://takeruf.com/ja/nagi/privacy
- Simplified Chinese: https://takeruf.com/zh/nagi/privacy
- Korean: https://takeruf.com/ko/nagi/privacy
- Website: https://takeruf.com/nagi
- Support email: support@takeruf.com

Check that all policy pages are publicly available before submitting. Policies cover local storage, region lookups, website permissions, WebView, channel-specific updates, deletion, and developer contact.

## App content

| Field | Source-backed answer / action |
| --- | --- |
| App category | Communication; general-purpose web browser |
| Ads | No developer-provided advertising or advertising SDK. Websites may display their own advertisements. |
| App access | All Nagi features are available without an app account, subscription, invitation, or access code. Third-party sites may require their own accounts; no Nagi reviewer credentials are needed. |
| In-app purchases | None implemented. |
| Account creation/deletion | No Nagi account creation or developer-operated account service. Website accounts are managed by those websites. |
| Target audience | Recommend adults (18+) for initial release; confirm the intended audience before saving. No child-directed design or Families commitment. |
| Content rating | Complete the browser/open-web and user-interaction questions honestly. Arbitrary web content is available; do not declare the app to contain only curated child-safe content. The questionnaire determines the rating. |
| Install-packages declaration | Not applicable to Play artifact: permission removed. |
| Camera/microphone/location | For user-authorized website features. No background location. Explain this use wherever Console asks. |
| Feedback | support@takeruf.com; include Android/device/WebView version and reproduction steps, without passwords or private page content. |

## Data safety guidance

Do not simply answer “no data collected.” The default automatic-region mode contacts `api.country.is`, whose server sees the source IP and infers a country. Google's data-type guidance places IP-inferred location under **Approximate location**.

| Data / transfer | Proposed treatment and evidence |
| --- | --- |
| IP-derived country lookup | Declare Approximate location collected for App functionality, optional (automatic regional search can be disabled), encrypted in transit (HTTPS). Conservatively declare sharing with the external provider until its relationship and handling establish an applicable exemption. Do not claim ephemeral processing: provider server-log retention is not established. Country/time are cached locally. |
| Browsing history, tabs, bookmarks, settings | Stored locally, not sent to developer servers. Local-only processing is outside collection declarations. |
| Arbitrary websites and searches in WebView | Open-web browsing is excluded from WebView collection/sharing declarations under Google's guidance. Explain website handling in the privacy policy. This exclusion does not cover app-controlled region or update requests. |
| Ask ChatGPT | User-initiated query URL opens the third-party site. No automatic page-content/history upload, no OpenAI API, and no developer-operated AI server. |
| Camera, microphone, precise location, uploads | Access occurs only for a website after permission/selection. No developer server receives them. Consider the open-web rule rather than equating a manifest permission with developer collection. |
| Updates | Play delivery only; no GitHub update request in this variant. |
| Analytics, advertising IDs, automatic crash reports | No such SDK or developer transport in the current implementation. |
| Passkeys and Safe Browsing | Android WebView/credential-provider security services; no credential bridge or developer credential storage. Recheck provider documentation if the form asks about platform handling. |
| Deletion | Local history/site data can be cleared in Settings; Android app-data deletion removes app-private data. Public downloaded files are deleted separately. Do not promise deletion of external providers' server logs. |

Only claim “all collected data encrypted in transit” for app-controlled collection: region lookup uses HTTPS. Nagi supports HTTP websites, so do not claim all browsing is encrypted. Review the final form against provider handling and the exact uploaded version before submission.

Sources: [Google Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en), [permissions policy](https://support.google.com/googleplay/android-developer/answer/16558241), [closed-test setup](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en).

## Store listing copy

### English

Name: Nagi Browser

Short description: A workspace browser for Android tablets with Spaces, tabs, and split browsing.

Full description:

Nagi is a workspace browser built for Android tablets. Organize your browsing in a sidebar, separate tasks with Spaces, and keep two pages side by side with adjustable split browsing.

Keep frequently used pages in Favorites or pinned tabs. Find pages in your history, and use the Command Bar to open URLs, search, or switch tabs and Spaces. Choose your search engines, including a user-initiated Ask ChatGPT action that opens ChatGPT's website.

Nagi supports touch, mouse, trackpad, hardware-keyboard shortcuts, and English, Japanese, Korean, and Simplified Chinese interfaces. Choose light, dark, or system appearance and adjust the theme color. Upload selected files through Android's document picker, download files, find text on pages, and use desktop mode when needed.

Your workspace is stored on your device. You can clear browsing history and website cookies/storage in Settings. No Nagi account, subscription, or cloud synchronization is required. Google Play manages updates for this edition.

Nagi is an early release. Cookies and site logins are shared across Spaces. Private browsing, content blocking, cloud sync, and an in-app AI chat are not available. Website compatibility, third-party logins, and AI-service behavior depend on those services and your Android WebView version.

Privacy policy: https://takeruf.com/en/nagi/privacy
Support: support@takeruf.com

### Japanese

アプリ名：Nagi Browser

簡単な説明：Spaceとタブで整理し、2つのページを並べて使えるAndroidタブレット向けブラウザ。

詳しい説明：

NagiはAndroidタブレット向けのワークスペースブラウザです。サイドバーでタブを整理し、Spaceで作業を切り替え、サイズを調整できるSplit表示で2つのページを並べて閲覧できます。

よく使うページはお気に入りや固定タブに保存。履歴からページを開き、コマンドバーでURL入力、検索、タブやSpaceの切り替えを行えます。検索エンジンを選択でき、「ChatGPTに聞く」は入力した質問をChatGPTのウェブサイトで開きます。

タッチ、マウス、トラックパッド、外付けキーボードのショートカットに対応。日本語、英語、韓国語、簡体字中国語で利用できます。ライト・ダーク・システムテーマとテーマカラーを選択でき、ファイル選択によるアップロード、ダウンロード、ページ内検索、デスクトップ表示も利用できます。

ワークスペースは端末上に保存します。設定から履歴やCookie・サイトデータを削除できます。Nagiのアカウント登録やサブスクリプションは不要で、クラウド同期はありません。Google Play版の更新はGoogle Playから配信されます。

Nagiは初期リリースです。Cookieとサイトへのログイン状態はSpace間で共有されます。プライベートブラウジング、コンテンツブロック、クラウド同期、アプリ内AIチャットは未対応です。サイト表示、外部サービスへのログイン、AIサービスの動作は各サービスとAndroid WebViewのバージョンに依存します。

プライバシーポリシー：https://takeruf.com/ja/nagi/privacy
お問い合わせ：support@takeruf.com

### Simplified Chinese

应用名称：Nagi Browser

简短说明：为 Android 平板打造的工作空间浏览器，支持 Space、标签页整理与双窗格浏览。

完整说明：

Nagi 是为 Android 平板打造的工作空间浏览器。用侧边栏整理标签页，通过 Space 切换任务，使用可调整大小的双窗格并排浏览两个网页。

将常用页面保存为收藏或固定标签页，通过历史记录打开页面。使用命令栏输入网址、搜索、切换标签页与 Space。可选择搜索引擎；“询问 ChatGPT”会将您输入的问题在 ChatGPT 网站中打开。

支持触控、鼠标、触控板及外接键盘快捷键，提供英语、日语、韩语和简体中文界面。可选择浅色、深色或跟随系统的主题并调整主题色。通过 Android 文档选择器上传选定文件，下载文件，在网页中查找文字，或使用桌面模式。

工作空间数据保存在设备上。可在设置中清除浏览历史、Cookie 和网站数据。无需注册 Nagi 账号或订阅，不提供云同步。Google Play 版通过 Google Play 接收更新。

Nagi 处于早期发布阶段。不同 Space 共享 Cookie 和网站登录状态。目前不支持无痕浏览、内容拦截、云同步或应用内 AI 聊天。网站兼容性、第三方登录和 AI 服务行为取决于对应服务及 Android WebView 版本。

隐私政策：https://takeruf.com/zh/nagi/privacy
联系邮箱：support@takeruf.com

### Korean

앱 이름: Nagi Browser

간단한 설명: Space, 탭 정리와 분할 탐색을 지원하는 Android 태블릿용 워크스페이스 브라우저.

자세한 설명:

Nagi는 Android 태블릿용 워크스페이스 브라우저입니다. 사이드바에서 탭을 정리하고 Space로 작업을 전환하며, 크기를 조절할 수 있는 분할 화면에서 두 페이지를 나란히 볼 수 있습니다.

자주 사용하는 페이지를 즐겨찾기나 고정 탭으로 보관하세요. 방문 기록에서 페이지를 열고 명령 모음으로 URL 입력, 검색, 탭과 Space 전환을 할 수 있습니다. 검색 엔진을 선택할 수 있으며 “ChatGPT에 질문”은 입력한 질문을 ChatGPT 웹사이트에서 엽니다.

터치, 마우스, 트랙패드, 외장 키보드 단축키를 지원합니다. 영어, 일본어, 한국어, 중국어 간체 인터페이스를 제공하며 밝게, 어둡게, 시스템 테마와 테마 색상을 선택할 수 있습니다. Android 문서 선택기를 통해 선택한 파일을 업로드하고 파일 다운로드, 페이지 내 검색, 데스크톱 모드를 사용할 수 있습니다.

워크스페이스는 기기에 저장됩니다. 설정에서 방문 기록과 쿠키·사이트 데이터를 삭제할 수 있습니다. Nagi 계정 등록이나 구독이 필요하지 않으며 클라우드 동기화는 없습니다. Google Play 버전은 Google Play를 통해 업데이트됩니다.

Nagi는 초기 릴리스입니다. Space 간 쿠키와 사이트 로그인 상태는 공유됩니다. 비공개 탐색, 콘텐츠 차단, 클라우드 동기화와 앱 내 AI 채팅은 제공되지 않습니다. 웹사이트 호환성, 타사 로그인 및 AI 서비스의 동작은 해당 서비스와 Android WebView 버전에 따라 달라집니다.

개인정보 처리방침: https://takeruf.com/ko/nagi/privacy
지원: support@takeruf.com
