# Google Play closed-test preparation

## Latest recorded submission — 2026-10-11

Nagi 1.0.0 (`com.takeruf.nagi`, versionCode 8, targetSdk 36) was submitted to the existing Alpha track with four-language release notes and store descriptions. The recorded Console state was **Changes in review**, with Alpha **Active** and 0.3.0 still available to selected testers. Tester selection, feedback address, join links, and all 178 country/region rows were unchanged. Submission does not establish approval or delivery to testers.

See [1.0.0 publication verification](RELEASE_1.0.0_VERIFICATION.md) for the dated evidence. The declaration guidance and listing copy below support future submissions; check them against the exact uploaded artifact and current Console forms.

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

Short description: Android phone and tablet browsing with Spaces, tabs, and split view.

Full description:

Nagi is a workspace browser for Android tablets and phones. Organize your browsing in a sidebar, separate tasks with Spaces, and keep two pages side by side with adjustable split browsing.

Keep frequently used pages in Favorites or pinned tabs. Find pages in your history, and use the Command Bar to open URLs, search, or switch tabs and Spaces. Choose your search engines, including a user-initiated Ask ChatGPT action that opens ChatGPT's website.

Nagi supports touch, mouse, trackpad, hardware-keyboard shortcuts, and English, Japanese, Korean, and Simplified Chinese interfaces. Choose light, dark, or system appearance and adjust the theme color. Upload selected files through Android's document picker, download files, find text on pages, and use desktop mode when needed.

Your workspace is stored on your device. You can clear browsing history and website cookies/storage in Settings. No Nagi account, subscription, or cloud synchronization is required. Google Play manages updates for this edition.

Block advertisements and trackers with a shield, persistent site exceptions, or pause/resume for a visit. Reader mode offers adjustable text size. Private browsing uses an isolated workspace and website storage on supported WebView versions. Closing the private workspace clears its website data; downloads and explicitly shared/copied information remain outside it.

Use automatic picture-in-picture or movable in-browser video playback, and two-finger trackpad gestures for Back/Forward. Narrow panes prioritize Share, then Copy link, then blocking, with hidden actions at the top of the menu.

Nagi is an early release. Normal Spaces share cookies and site logins. Cloud sync and an in-app AI chat are unavailable. Website compatibility, third-party logins, and AI-service behavior depend on those services and your Android WebView version.

Windows narrower than 600dp use full-width pages, bottom navigation, and a sheet for tabs, Favorites, and Spaces. Wider windows keep the sidebar. Resizing preserves loaded pages and unfinished input. Automatic mobile or desktop mode applies on the next navigation or reload; saved site choices take priority. Mobile mode requests a phone layout, including supported Client Hints. Empty containers around confirmed blocked advertisements are collapsed while protecting text, images, and controls.

Privacy policy: https://takeruf.com/en/nagi/privacy
Support: support@takeruf.com

### Japanese

アプリ名：Nagi Browser

簡単な説明：Spaceとタブで整理し、分割表示も使えるAndroidタブレット・スマートフォン向けブラウザ。

詳しい説明：

NagiはAndroidタブレット・スマートフォン向けのワークスペースブラウザです。サイドバーでタブを整理し、Spaceで作業を切り替え、サイズを調整できるSplit表示で2つのページを並べて閲覧できます。

よく使うページはお気に入りや固定タブに保存。履歴からページを開き、コマンドバーでURL入力、検索、タブやSpaceの切り替えを行えます。検索エンジンを選択でき、「ChatGPTに聞く」は入力した質問をChatGPTのウェブサイトで開きます。

タッチ、マウス、トラックパッド、外付けキーボードのショートカットに対応。日本語、英語、韓国語、簡体字中国語で利用できます。ライト・ダーク・システムテーマとテーマカラーを選択でき、ファイル選択によるアップロード、ダウンロード、ページ内検索、デスクトップ表示も利用できます。

ワークスペースは端末上に保存します。設定から履歴やCookie・サイトデータを削除できます。Nagiのアカウント登録やサブスクリプションは不要で、クラウド同期はありません。Google Play版の更新はGoogle Playから配信されます。

広告・トラッカーをブロックし、サイトごとの例外や訪問中の一時停止・再開を選べます。Readerモードでは文字サイズを調整できます。対応WebViewでは独立したワークスペースとサイトデータを使うプライベートブラウジングを利用できます。終了時にそのサイトデータを消去しますが、ダウンロードや明示的に共有・コピーした情報は外に残ります。

自動ピクチャーインピクチャーや移動できるアプリ内動画プレーヤー、トラックパッドの2本指で戻る・進む操作に対応。狭いペインでは共有、リンクコピー、広告ブロックの順にボタンを残し、隠れた操作だけメニュー上部へ移します。

Nagiは初期リリースです。通常のSpace間ではCookieとサイトへのログイン状態を共有します。クラウド同期とアプリ内AIチャットは未対応です。サイト表示、外部サービスへのログイン、AIサービスの動作は各サービスとAndroid WebViewのバージョンに依存します。

600dp未満のウィンドウでは全幅表示、下部ナビゲーション、タブ・お気に入り・Spaceのシートを使います。広いウィンドウではサイドバーを表示。サイズ変更時はページと入力途中の内容を保持し、自動のモバイル・PC表示切替は次のページ移動・再読込時に適用します。サイトごとの指定を優先し、モバイル表示では対応Client Hintsも含めスマートフォン向けの表示を要求します。ブロックが確認された広告の空の親枠を畳み、本文・画像・操作部品を保護します。

プライバシーポリシー：https://takeruf.com/ja/nagi/privacy
お問い合わせ：support@takeruf.com

### Simplified Chinese

应用名称：Nagi Browser

简短说明：适用于 Android 平板和手机，支持空间管理、标签页整理和双窗格浏览。

完整说明：

Nagi 是适用于 Android 平板和手机的工作空间浏览器。你可以在侧边栏中整理标签页，用空间（Space）分别管理不同任务的标签页，并通过可调整宽度的双窗格并排浏览两个网页。空间是 Nagi 用来组织不同任务的工作区，在应用内显示为“空间”。

将常用页面加入收藏或固定为标签页，也可以从历史记录中重新打开页面。通过命令栏输入网址、搜索内容，或切换标签页和空间。你可以选择搜索引擎；向 ChatGPT 提问时，会在 ChatGPT 网站中打开你输入的内容。

支持触控、鼠标、触控板和外接键盘快捷键，提供英语、日语、韩语和简体中文界面。你可以选择浅色或深色主题，也可以让主题跟随系统，并调整主题色。通过 Android 文件选择器上传文件，或拍摄照片后上传。还可以下载文件、在页面中查找文字，以及使用桌面版网站。

支持保存网页生成的文件（Blob/data），确认后即可下载，单个文件上限为 32 MB。可开启标签页后台休眠，让符合条件的闲置标签页自动休眠；再次打开时会重新加载，部分页面状态可能丢失。

工作空间数据保存在你的设备上。你可以在设置中清除历史记录、Cookie 和网站数据。无需注册 Nagi 账号或付费订阅。目前不提供云同步。Google Play 版通过 Google Play 接收更新。

支持广告和跟踪器拦截、网站例外及本次访问的暂停与恢复。阅读模式可调节文字大小。在受支持的 WebView 上，隐私浏览使用独立的工作区和网站存储；关闭隐私工作区时会清除其中的网站数据，但下载文件及明确分享或复制的信息仍保留在外部。

支持自动画中画、可移动的应用内视频播放器，以及触控板双指后退和前进手势。窄窗格按分享、复制链接、广告拦截的优先级保留按钮，只将隐藏的操作移至菜单顶部。

Nagi 目前处于早期发布阶段。普通空间共享 Cookie 和网站登录状态。目前不提供云同步或应用内 AI 聊天。网站兼容性、第三方登录以及 AI 服务的可用情况，取决于对应服务和 Android WebView 版本。

宽度小于600dp的窗口采用全宽网页、底部导航及标签页、收藏和空间面板；宽窗口保留侧边栏。调整窗口大小时保留网页和未完成的输入，自动手机版或电脑版切换在下次导航或刷新时生效，并优先使用各网站的设置。手机版发送手机标识及受支持的Client Hints。折叠已确认拦截的广告周围留下的空容器，并保护正文、图片和操作控件。

隐私政策：https://takeruf.com/zh/nagi/privacy
联系邮箱：support@takeruf.com

### Korean

앱 이름: Nagi Browser

간단한 설명: Space와 분할 탐색을 지원하는 Android 태블릿·휴대전화용 브라우저.

자세한 설명:

Nagi는 Android 태블릿과 휴대전화를 위한 워크스페이스 브라우저입니다. 사이드바에서 탭을 정리하고 Space로 작업을 전환하며, 크기를 조절할 수 있는 분할 화면에서 두 페이지를 나란히 볼 수 있습니다.

자주 사용하는 페이지를 즐겨찾기나 고정 탭으로 보관하세요. 방문 기록에서 페이지를 열고 명령 모음으로 URL 입력, 검색, 탭과 Space 전환을 할 수 있습니다. 검색 엔진을 선택할 수 있으며 “ChatGPT에 질문”은 입력한 질문을 ChatGPT 웹사이트에서 엽니다.

터치, 마우스, 트랙패드, 외장 키보드 단축키를 지원합니다. 영어, 일본어, 한국어, 중국어 간체 인터페이스를 제공하며 밝게, 어둡게, 시스템 테마와 테마 색상을 선택할 수 있습니다. Android 문서 선택기를 통해 선택한 파일을 업로드하고 파일 다운로드, 페이지 내 검색, 데스크톱 모드를 사용할 수 있습니다.

워크스페이스는 기기에 저장됩니다. 설정에서 방문 기록과 쿠키·사이트 데이터를 삭제할 수 있습니다. Nagi 계정 등록이나 구독이 필요하지 않으며 클라우드 동기화는 없습니다. Google Play 버전은 Google Play를 통해 업데이트됩니다.

광고와 추적기를 차단하고 사이트 예외를 저장하거나 이번 방문의 차단을 일시 중지하고 재개할 수 있습니다. 읽기 모드에서는 글자 크기를 조절할 수 있습니다. 지원되는 WebView에서 비공개 탐색은 분리된 작업 공간과 사이트 저장소를 사용합니다. 비공개 작업 공간을 닫으면 해당 사이트 데이터가 삭제되지만 다운로드와 명시적으로 공유하거나 복사한 정보는 외부에 남습니다.

자동 화면 속 화면, 이동 가능한 앱 내 동영상 플레이어, 트랙패드 두 손가락 뒤로/앞으로 제스처를 지원합니다. 좁은 창에서는 공유, 링크 복사, 광고 차단 순으로 버튼을 유지하고 숨겨진 작업만 메뉴 상단으로 이동합니다.

Nagi는 초기 릴리스입니다. 일반 Space는 쿠키와 사이트 로그인 상태를 공유합니다. 클라우드 동기화와 앱 내 AI 채팅은 제공되지 않습니다. 웹사이트 호환성, 타사 로그인 및 AI 서비스의 동작은 해당 서비스와 Android WebView 버전에 따라 달라집니다.

600dp보다 좁은 창은 전체 너비 페이지, 하단 탐색 모음과 탭·즐겨찾기·Space 시트를 사용하고 넓은 창은 사이드바를 유지합니다. 창 크기 변경 시 페이지와 입력 중인 내용을 유지합니다. 자동 모바일·데스크톱 모드는 다음 탐색이나 새로고침에 적용되며 사이트별 설정이 우선합니다. 모바일 모드는 지원되는 Client Hints를 포함해 휴대전화 레이아웃을 요청합니다. 차단이 확인된 광고 주변의 빈 컨테이너를 접고 본문, 이미지와 조작 요소를 보호합니다.

개인정보 처리방침: https://takeruf.com/ko/nagi/privacy
지원: support@takeruf.com
