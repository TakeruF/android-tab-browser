# 横断テストと機能欠落の調査 — 2026-10-06

対象: `989538a` のcheckoutと作業開始時点の未コミットUI変更。JDK 17.0.20.1、API 36 / Android 16の `Orbit_Tablet_QA` (`emulator-5554`)、WebView 133.0.6943.137。本調査では製品コードを変更せず、テストと検証記録を追加・更新した。

## 再現用テストで検査する不具合

### B1 / 高: Split右タブのキーボード選択と操作対象の不一致

再現: 2タブをSplit表示 → `Ctrl Tab` または右タブに相当する `Ctrl 1–9` → `Ctrl W`。

期待: 選んだ右タブを閉じ、左タブを保持する。実装では選択タブを右へ更新しながら `rightFocused = false` にするため、`focusedTab` が左になる。閉じる・検索・再読み込み・戻る・URL入力などが異なるページを対象にする可能性がある。Command Barの `SelectTab` も同じ経路。

箇所: `ui/NagiApp.kt` の `focusedTab` と `Shortcut.NEXT_TAB` / `PREVIOUS_TAB` / 数字ショートカット / `SuggestionAction.SelectTab`。

再現テスト: `CrossFeatureUiTest#nextTabSelectionMakesCloseShortcutCloseRightTab`、`#numberedRightTabSelectionMakesCloseShortcutCloseRightTab`。

### B2 / 中: 表示中のSplit右ページがArchiveで消える

再現: 右側タブをSplitに表示し、その最終アクセス日時がArchive期間より古い状態でArchiveを実行。

期待: 現在表示している両ページを保持する。`BrowserDao.archiveInactive` は `spaces.activeTabId` だけを保護し、Splitの右タブを認識しない。対象タブに `archivedAt` が設定されると `visibleTabs` から除外され、セッション保持対象からも外れるため、右ペインとlive WebViewが失われる。

最終アクセス日時はテストで古い値を設定して再現する。フォームやスクロール位置の消失まではこのテストでは検査していない。

箇所: `data/repository/TabRepository.kt#archiveNow`、`data/room/BrowserDao.kt#archiveInactive`、`ui/NagiApp.kt` の `retainTabIds`。

再現テスト: `CrossFeatureUiTest#archiveDoesNotHideEitherCurrentlyDisplayedSplitPage`。

### B3 / 中: 保存できる大文字HTTPS検索テンプレートを実行できない

再現: カスタム検索エンジンに `HTTPS://example.com/?q={query}` を登録し、検索する。

`InputResolver.validateEngine` はschemeを大文字小文字を区別せず受理するが、`search` はテンプレートのschemeを正規化せず、`WebViewBrowserEngine.loadUrl` は小文字の `https://` / `http://` だけを受理する。ロードせずに戻り、エラー表示もしない。

再現テスト: `WebViewEngineTest#validatedUppercaseSearchTemplateCanNavigate`。ネットワーク成功ではなく、検証で受理したURLがエンジンへ渡る契約を検査する。

### B4 / 中: ファイルアップロードの拡張子指定がpickerで失われる

再現: ファイル選択要求のacceptに `.pdf` を指定。

`NativeBrowserHost.chooseFiles` は `/` を含む値だけをMIMEとして残すため、`.pdf` が落ちてIntentのtypeが `*/*` になる。PDFで絞り込まず、すべてのファイルを表示する。複数の明示MIMEと複数選択の経路は別テストで検査する。

再現テスト: `FilePickerContractTest#pdfExtensionAcceptRestrictsPickerToPdfFiles`。Robolectricで実際に起動要求されたシステムpicker Intentを検査する。物理端末のpicker画面までは検査していない。

### 要追跡: ChatGPT handoff後の保存URLがabout:blankのまま

既存の `ChatGptBrowserHandoffUiTest` は初回一括・更新後一括・単独再実行で失敗した。新しい対象タブの表示を待ってから「ChatGPTに聞く」をクリックしても、10秒後のRoom URLが `about:blank` のままであることを単独実行の診断で確認した。候補の生成・選択・callbackのcomponentテストは成功している。

追加の単独診断では、実際のWebViewは `url=https://chatgpt.com/?q=Nagi+browser+test`、`originalUrl=about:blank`、`progress=10` だった。つまり質問付きURLはWebViewへ渡っているが、ロードが進まず、Roomへの反映も起きていない。引き渡しcallbackの失敗とは断定できない。接続・サイト・エンジンのどこでロードが止まるか、ページ開始前のURLを保存する必要性は要追跡。Cloudflare・ログイン・ネットワークが原因と断定しない。ChatGPTが質問を受理した証拠もない。

## コードと現行UI導線から確認した機能欠落

- **通常Bookmarksの保存・閲覧導線**: `LibraryScreen(history = false)` と永続保存処理は残るが、NavHostには `browser` / `settings` / `history` しかなく、SidebarやCommand BarにBookmarksの入口がない。ページメニューの保存操作もなく、既存の通常BookmarkをUIで開けない。Favoritesは利用可能。READMEの「Bookmarks」「ページメニューから保存」と現行UIが不一致。復活させるか、廃止するなら既存データの利用方法と説明の更新が必要。
- **Cookie・サイトデータの消去**: 設定の消去操作は閲覧履歴だけ。サイトログイン・Cookie・localStorageをユーザーがリセットする操作、サイト別の消去もない。ログインループの調査やサイトからのログアウト操作に不足する。履歴消去がサイトデータも消すとは表記していないため、履歴消去そのもののバグではない。
- **リンク／画像の長押し・右クリック操作**: WebViewのhit-testを使うコンテキストメニューがなく、リンク単位の新規タブ表示・URLコピー・画像保存の専用操作がない。`target=_blank` と全リンクを新規タブにする設定は別の機能。

既にREADMEで未対応と明示されているPrivate Browsing、Sync、Reader、Content Blocking、blob/dataダウンロード、カメラ・フォルダーpickerは今回の新規回帰とは扱わない。

ドキュメント上の別の不一致: READMEはセッションプールを最大3と説明するが、現行EnginePoolは開いたliveセッションを全て保持し、既存テストも25タブ保持を検査する。多数タブ時のメモリ上限・回収方針は明示する必要がある。今回OOMを再現したわけではない。

## 既存テストの修復

初回全件実行は端末82件中70成功・12失敗。失敗はアプリの不具合と一律には扱わない。

- カスタムエンジン編集・CJKエンジン名入力: 新しい「Customize search engines」画面を経由。カスタムkeywordのテストは常用エンジン選択も実施。
- 4言語: Settingsから移動したTheme colorを探す処理を、現行のDark選択の検査に変更。
- ファビコン: 削除されたページメニューのPin項目ではなく、タブのPinアクセシビリティ操作を実行。
- 新規タブのhomeアイコン: 新規タブの到着とOmniboxを待ち、閉じてから対象タブのアイコンを検査。非表示の別タブの同名アイコンを誤選択しない。
- Space切替アニメーション: 他テストが残したQA Spacesでボタンが横スクロール範囲の外に出るため、対象Spaceを表示してからアニメーション時計を停止。修正後の単独再実行は成功。
- ChatGPT handoff: 旧Spaceにも存在するSettingsボタンではなく、新しい対象タブが表示されてから操作。
- CJK検索: 実InputConnectionのcomposition検査を維持し、device-local HTTP fixtureをテスト用検索先に使用。公開検索サイトのリダイレクトやCAPTCHAが保存URLの検査に混入しないようにした。HTTPS登録の検証は別の既存テストで維持。

## 実行結果・レポート

初回の既存単体85件は全成功。追加したfile picker契約テストを含む単体87件は86成功・1失敗（B4）。Debug / Release（unsigned）/ AndroidTest APKビルド成功。Lintはエラー0、警告33。`git diff --check` 成功。

追加再現4件を含む端末86件の一括実行は **80成功・6失敗・スキップ0**。内訳はB1の2件、B2の1件、B3の1件、ChatGPT handoff、Space切替アニメーション。Space切替の導線修正後、当該テストとChatGPT handoffの2件を個別再実行し、Space切替は成功、ChatGPTは失敗した。更新済み既存テストの全件成功を主張しない。

保存先: `artifacts/cross-feature-audit-2026-10-06/`。初回の全件結果を `baseline-results` / `baseline-report`、追加端末全件結果を `expanded-results` / `expanded-report`、個別再実行を `targeted-results` / `targeted-report`、単体の追加検査結果を `unit-results` / `unit-report` に保存。

ChatGPTの追加診断は `handoff-results` / `handoff-report` に保存する。

失敗時の[実画面](../artifacts/cross-feature-audit-2026-10-06/handoff-audit.png)も確認。アドレスバーには質問付きURLが表示され、本文は白紙・進捗表示のまま、タブ名はNew tabである。

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest
ANDROID_SERIAL=emulator-5554 JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:connectedDebugAndroidTest
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:lintDebug
```

再現テストは修正前の不具合を検出するため失敗する。全件成功とは主張しない。

物理端末、API 26–35、実メーカーIME、実トラックパッド、実カメラ／マイク／位置情報、中国大陸の実ネットワーク、ChatGPTの質問受理・回答生成は今回の確認対象外。API 26のエミュレーターイメージはこの環境に未導入。
