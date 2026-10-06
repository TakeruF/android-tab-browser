# 検証記録

2026-10-06の横断テスト: [当初の不具合・機能欠落・再現条件](CROSS_FEATURE_AUDIT_2026-10-06.md)。そこで確認したSplitの選択／操作先、Archive、大文字scheme、upload accept拡張子の不具合を0.1.1で修正。Bookmarks、サイトデータ消去、リンク／画像メニュー、アプリ内更新を追加した。初回ナビゲーションは要求URLを直ちに保存し、30秒の読み込みタイムアウト後に再試行できる。ChatGPTの応答生成・ログイン成功は確認対象に含めない。

検証日: 2026-10-03。JDK 17 / Android SDK 36 / Gradle 8.13。端末テストはAPI 36のPixel Tabletエミュレーター `Orbit_Tablet_QA`、2560 × 1600 / 320 dpi / 横画面で実行。

## 最新の検証結果

2026-10-04のファビコン修正では、Debug / Test APKビルド・単体39件・対象端末4件（FaviconUi 1件、BrowserUi 3件）が成功。Lintもエラー0を確認しました。API 36のエミュレーターで、保存画像がないFavoritesと画像ファイルが消えたFavoritesの取得、取得失敗時の頭文字、サイト指定のアイコンへの更新、ピン留め・再読み込み・Activity再生成後の描画と永続保存を検査しています。Gmail・ChatGPT・GitHubの初期Favoritesのアイコンも実際の画面で確認しました。物理端末の確認は含みません。画面記録は`app/build/reports/favicon-validation/`に保存しています。

2026-10-03の全体検証結果は**単体34件・端末29件が全件成功、Debug / Release / Test APKのビルド成功、Lintエラー0・警告23**です。Releaseはunsignedです。[詳細UIテストと表示崩れの修正](#詳細uiテストと表示崩れの修正)に実行の範囲を記載しています。

以下は開発順の検証記録です。途中の失敗・未完了の記述は、その時点の結果を示します。

## 初期実装のビルドとテスト

```sh
./gradlew :app:assembleDebug :app:assembleRelease \
  :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest
```

| 検証 | 結果 |
| --- | --- |
| Debug APK | 成功 |
| Release APK / R8 | 成功。署名設定は未設定のためunsigned |
| Android Lint | エラー0。更新可能な依存バージョン等の警告あり |
| 単体テスト | 26件成功、失敗・スキップ0 |
| Android端末テスト | 13件成功、失敗・スキップ0 |

単体テスト: URL/IDN/IPv4/IPv6/ポート/危険なscheme判定・keyword/UTF-8検索11件、25タブ切り替え時のliveセッション保持/表示切り替え/閉じたタブの破棄/全セッション終了4件、実際のRoomによるSpace/Tab/Bookmark/Engine/Archiveの整合性8件、ダウンロードファイル名3件。

端末テスト: ブラウズ/履歴/Favorites/Bookmarks/閉じたタブ復元、Split/比率変更/交換/解除、検索エンジン追加とActivity再生成の3件、CJK入力4件、実際のWebViewによるJavaScript/Cookie/LocalStorage/IndexedDB/戻る進む/ページ内検索/ページ履歴snapshot/Desktop UA/ダウンロード/HTML入力/target blank/ファイル選択・全画面コールバックの6件。

生成レポート:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`

## 日本語・中国語・韓国語入力

Composeのテキストを置き換えるだけではなく、debug専用InputMethodServiceから、Androidが接続した実際のInputConnectionを使用。テスト終了後に元のIMEを復元します。このサービスはRelease APKに含まれません。

| 対象 | 操作と確認 |
| --- | --- |
| 日本語Command Bar | `に` → `にほん` → `にほんごのけんさく` → `日本語の検索`。未確定中のDown/Enter/IME Goで文字とフォーカスを保持。確定後に再変換してEnterで検索 |
| 中国語Command Bar | `zh` → `zhongwen` → `zhongwensousuo` → `中文搜索`。未確定中の誤実行を防ぎ、確定後IME Goで検索 |
| 韓国語Command Bar | `ㅎ` → `하` → `한` → `한구` → `한국어 검색`。compositionの更新と確定後Enterを確認 |
| 検索エンジンName | 日本語・中国語・韓国語のpreeditとcommitを同じフィールドで確認 |
| HTMLフォーム | WebViewの実際の入力欄へCJK compositionとcommitを送信。JavaScriptから最終値を読み、文字を保持していることを確認 |

修正した回帰: 変換中のDownがComposeの別候補へフォーカスを移し、その後の入力を失うケース。Command BarをTextFieldStateで管理し、composition中にアプリへ届いたEnter/Up/Down/EscapeとIME Goがタブ移動・検索実行を起こさないようにしました。

Command Barの初期フォーカスはダイアログのウィンドウが入力を受け取れる状態になるまで待ちます。親ウィンドウ側で先にフォーカスを要求してIMEが未接続になる競合も修正しました。テスト用IMEの切り替え・解除の同期は別に行い、アプリのフォーカスと区別しています。

ソフトキーボード表示時は安全領域とIMEのinsetsをCommand Barへ適用し、候補リストをスクロールできる高さに縮めます。実装は[AndroidのWindowInsetsガイド](https://developer.android.com/develop/ui/compose/system/insets-ui)に沿っています。

これはアプリとAndroid入力接続の検証です。Gboard・Samsung Keyboard等の実際の候補辞書、各端末のハードウェアキーボードとIME固有動作の確認は残っています。

## トラックパッドとマウスのスクロール

2026-10-03に`BrowserScrollTest`を追加。API 36の`Orbit_Tablet_QA`で4件成功。別作業とのAPKインストール競合を避けるため専用の`Orbit_Scroll_QA`でも4件再確認し成功。WebViewへ直接送るのではなく、Activityのウィンドウ入口から入力し、ComposeとネイティブViewの転送経路を含めて検証しています。

- マウスの`ACTION_SCROLL`でページを縦横に動かし、逆方向にも戻る。
- `SOURCE_TOUCHPAD`の縦横入力がHTML内のスクロール領域へ届く。ページ全体は動かず、DOMの`wheel`が1入力につき1回届く。
- タッチスワイプの後も、マウス・トラックパッドの小数のスクロール入力が届く。
- Splitで先にクリックしなくてもポインター下のペインだけが動く。スクロールしたペインへフォーカスが切り替わり、Ctrl Wがそのペインを閉じる。

トラックパッドの`SOURCE_TOUCHPAD`はフォーカス先へ転送されるため、`ACTION_SCROLL`だけをウィンドウ入口でポインター入力に変換。座標・縦横軸・小数の値を維持して標準のWebView処理へ渡します。ペインのフォーカス通知はネイティブコンテナーでタッチ開始とスクロールを観測します。イベント形式は[Android MotionEvent](https://developer.android.com/reference/android/view/MotionEvent)、転送先の規則は[View.dispatchGenericMotionEvent](https://developer.android.com/reference/android/view/View#dispatchGenericMotionEvent(android.view.MotionEvent))を参照。

これはエミュレーターへのAndroid入力イベント注入による検証です。物理トラックパッド固有のドライバー・加速度・ジェスチャーの確認は含みません。

修正後のDebugビルド、単体テスト34件、Lintエラー0を確認。専用エミュレーターで上記4件と既存のBrowserUiTest 3件・CjkInputTest 4件・WebViewEngineTest 6件、計17件が成功しています。端末テストはAPKをインストールし`adb -s emulator-5556 shell am instrument -w -r`で実行。ログは`app/build/reports/scroll-validation/`に保存しました。

この時点では全体テストは完了扱いにしていません。並行して追加された`SidebarDragTest`のFavoritesへのドラッグで1秒の待機時間超過があり、自動スクロールのテストも待機が終了せず中断しました。その後、未実行だったWebViewEngineTest 6件を分けて実行し成功。サイドバーのドラッグ処理は今回のスクロール修正の対象外です。

## 手動確認

- 実際にAPKをインストールして起動。ホーム、Sidebar、Command Bar、設定、Splitの描画を確認。
- 通常のGboardを表示し、入力後の先頭候補とキーボード上の操作案内が見えることを確認。これは英語キーボードでの表示確認で、CJK候補辞書の検証とは別です。
- 外部HTTPSページを取得し、ページタイトルとWebコンテンツの表示を確認。
- ローカルHTTPページの`input type=file`からAndroidの標準ドキュメントピッカーを開き、`orbit-upload.txt`を選択。HTMLのFile APIで選択したファイル名が表示されることを確認。
- ローカルHTTPの添付ファイルをDownloadManagerで公開Downloadsへ保存。`orbit.txt`の内容が`Orbit download verification`であることと完了状態SUCCESSを確認。
- 添付ファイルのURLが元のページURLを上書きし、再生成時に新規タブ画面になるケースを修正。ダウンロード後は元の文書URLとタイトルを維持する回帰検証を追加。

画面記録は[screenshots](screenshots/)に保存。Splitとファイル処理のWeb領域は検証用のローカルHTML、`web-https.png`は外部HTTPSページです。いずれも実際のWebViewで描画しています。

## 残る検証

物理端末のカメラ/マイク/位置情報の取得、実際の動画コーデックと全画面再生、Android 8–9の保存先、複数メーカーのIMEと大規模な実サイト互換性は未確認。サイト権限の要求処理と全画面ホストは実装済みですが、コールバックテストは実ハードウェア動作の証明にはなりません。

MVPの機能範囲・復元の保証・共有Cookie・未実装のPhase 3は[README](../README.md)を参照してください。

## Arc Sidebarと地域検索の更新

追加変更の検証結果・範囲・最新画面は[ARC_REFINEMENT.md](ARC_REFINEMENT.md)に記録しています。単体34件・対象端末テスト16件が成功し、Debug/ReleaseビルドとLintも成功しています。

## UIの配色と視認性

2026-10-03にライト／ダーク表示を調整。Space色を混ぜたルート背景の`contentColor`が未指定だったため、ダーク表示でもSidebarの文字・操作アイコン・Favoritesの頭文字が黒で描かれていた問題を修正しました。

- 新規タブの見出しと補助文字のコントラストを上げ、検索欄に輪郭を追加。
- 選択中のタブ・検索候補・Spaceを塗りと輪郭で表示し、選択状態をsemanticsにも公開。
- Material 3のsurface containerと文字色をテーマごとに指定。設定カードと検索欄の半透明色を不透明なテーマ色に変更。
- Spaceの保存色を保ち、表示するアイコン色のみ背景とのコントラストが4.5:1以上になるよう補正。雲・ハートはtext presentationでテーマの文字色を適用。
- アプリとダイアログのステータス／ナビゲーションバーの文字色を、システム設定とは独立したアプリのテーマに同期。
- Splitのフォーカス枠とリサイズハンドル、エラーの再試行ボタンも見やすい色に調整。

sRGB相対輝度から主要な色の組み合わせを計算。通常文字／surfaceはライト12.67:1・ダーク12.06:1、補助文字／surfaceContainerHighは5.60:1・6.84:1、選択文字／primaryContainerは8.71:1・7.36:1。検索欄の輪郭／surfaceContainerは3.77:1・5.10:1です。無効状態・Webページ・IMEの配色はこの数値の対象外です。

DebugビルドとLintを再実行し成功（エラー0、警告23）。更新したAPKで既存のBrowserUiTest 3件を実行し成功。API 36の`Orbit_Tablet_QA`でホーム、設定、Command Bar、Space編集を実際に表示して確認しました。物理端末での表示確認は含みません。

画面: [ライト](screenshots/colors-light-home.png)・[ダーク](screenshots/colors-dark-home.png)・[ダーク設定](screenshots/colors-dark-settings.png)・[検索候補](screenshots/colors-dark-commandbar.png)・[Space編集](screenshots/colors-dark-editor.png)。

## 詳細UIテストと表示崩れの修正

2026-10-03、配色変更後の全テストを再実行。以下が今回の最終結果です。

| 検証 | 結果 |
| --- | --- |
| Debug APK / Android Test APK | 成功 |
| Release APK / R8 | 成功、unsigned |
| 単体テスト | 34件成功、失敗・スキップ0 |
| Android端末テスト | 29件成功、失敗・スキップ0 |
| Lint | エラー0、警告23 |

端末テストはAPI 36の`Orbit_Tablet_QA`で`connectedDebugAndroidTest`を実行。既存20件（BrowserUi 3、CJK入力4、WebView 6、スクロール4、ドラッグ3）に、`AppearanceUiTest` 9件を追加しました。全体の実行は2分2秒で完了しています。

追加した確認:

- ライト／ダークのSidebar、Favoritesの頭文字、見出し、操作アイコン、新規タブの文字を実際の合成済み描画画像で検査。文字4.5:1、アイコン3:1を確認。
- 実際のテーマ選択チップを操作し、Activity再生成後も設定画面と選択状態が保持されることを確認。
- Command Barのフォーカス、Downによる選択移動、Escapeによる終了、両テーマでのダイアログのステータスバー文字色を確認。
- 全6色のSpaceを両テーマで保存し、雲のアイコンの実描画コントラストを確認。色補正は4.5:1を目標とし、この細い字形の描画検査はアンチエイリアスを考慮して4.4:1を下限にしています。
- Sidebarを折りたたんでも、タブ名・選択状態・クリック操作をsemanticsから取得できることを確認。
- サイドバー幅380 dpの縦画面でSplitを作成し、仕切りを左へ動かしてもページ内検索の入力幅が100 dp以上、高さが64 dp以下に収まることを確認。検索を閉じる操作とSplit解除も確認。
- Systemテーマのまま端末のナイトモードをオン／オフし、描画色とステータスバーが追従し、同じタブが残ることを確認。端末設定はテスト後に元へ戻します。
- ローカルの接続失敗を実際のWebViewで発生させ、両テーマでエラー文字とRetryのコントラスト・再試行操作を確認。

目視確認で、縦画面の狭いSplitペインでは検索文字が細く折り返され、ページ内検索欄もほぼ幅0に潰れていました。狭いペインではReloadをページメニューに移し、新規タブの検索文言・余白を短くし、ページ内検索を入力欄と移動ボタンの2行に変更。Split比率も表示幅に応じて制限し、可能な幅がある場合は各ペイン180 dp以上を確保します。折りたたみ時のタブ名の読み上げ情報も補いました。

最初の全体実行では、以前のテストが追加したFavoritesでリストの表示領域が縮み、ドラッグ3件が準備段階で失敗しました。ドラッグテストはFavoritesを一定のfixtureにして、終了時に元のエントリーを復元するよう変更。最終の全29件は一括実行で成功しています。

画面: [縦画面Split](screenshots/ui-portrait-split.png)・[縦画面のページ内検索](screenshots/ui-portrait-find.png)・[ライトの検索候補](screenshots/ui-light-commands.png)・[ダークのエラー](screenshots/ui-dark-error.png)・[システムのダーク追従](screenshots/ui-system-dark.png)。

レポート: `app/build/reports/androidTests/connected/debug/index.html`、`app/build/reports/tests/testDebugUnitTest/index.html`。実行ログは`app/build/reports/ui-validation/`。この検証はAPI 36のエミュレーターでの結果で、物理端末、別メーカーのIME、Android 8–15は含みません。

## READMEとライセンスの整備

MITのLICENSE、依存ライブラリの出典一覧、原文・著作権表記を追加。ライセンス一覧はRelease runtimeの解決済み108モジュールと一致することを確認しました。ライセンス原文・NOTICE・本体MITの計9ファイルはDebug / Release APK内の`assets/licenses/`とバイト単位で一致しています。同梱後に両ビルドとLintも成功しています。

READMEのローカルリンクとライセンス文書の外部リンク21件も確認。画面は最終のライト／ダークのホームを撮り直しました。


## 画像注記に沿ったサイドバー操作の更新（2026-10-04）

- Personal／Workの標準アイコンを🏠／💼で表示。Space編集に絵文字の候補と自由入力を追加し、複合絵文字も保存・Activity再生成後の表示を確認。既存の単色アイコンも選択可能です。
- `about:blank`のタブを家アイコンで表示。展開・折りたたみの両表示に適用。
- マウスではタブの「…」「×」とFavoritesの「…」をホバー時に表示。タッチでは選択中タブの操作とFavoritesのメニューを表示し、読み上げ用にも「Show actions」を提供。
- タブ／Favoritesの右クリックでメニューを表示。右クリックがタブ選択やドラッグを開始しないことを確認。
- 既存のドラッグによる並べ替え、ピン留め、Favoritesへの移動、別Spaceへの移動、キャンセルを検証。
- アイコン編集画面はスクロール可能にし、入力キーボードや狭い画面でも項目へ到達できるようにしました。

Debugビルド・単体テスト39件・Lintが成功（エラー0、既存警告23）。端末テストはSidebarDragTest 5件が一括成功。AppearanceUiTest 9件とBrowserUiTest 3件も成功しました（表示の12件とサイドバーの5件は別実行）。API 36のOrbit_Tablet_QAで検証。物理端末・実際のトラックパッドは未検証です。

画面: [絵文字と家アイコン](screenshots/sidebar-emoji-home.png)・[ホバー時の操作](screenshots/sidebar-hover-actions.png)・[右クリックメニュー](screenshots/sidebar-context-menu.png)。

長いリストの自動スクロールは、固定時間のタイマーから描画フレームに合わせた進行に変更。リスト端ではスクロールを止めます。テストもドラッグ入力後の描画を待ってから到達を判定するようにしました。


## パスキー連携とLucide（2026-10-04）

パスキーのWebAuthnを、対応するWebViewでブラウザー用モードに設定。originを保持して端末のCredential Managerへ渡す権限と依存を追加しました。ネイティブAPIの公開、異なるRP IDの拒否、キャンセルの3件がAPI 36・WebView 133.0.6943.137で成功しました。実サービスでの登録／ログイン成功は未検証です。Google Password Managerで第三者サイトのパスキーを扱うにはブラウザーの承認が必要で、設定だけでは完了しません。[実装・承認条件・検証範囲](PASSKEYS.md)。

一般操作のMaterial IconsをLucideに置換。サイドバー、アドレスバー、検索候補、履歴、ブックマーク、設定、ページ／タブメニューに73箇所・34種類を使用。設定の見出しとメニューにもアイコンを追加し、読み上げ用の説明、クリック領域、テーマ色を維持しています。戻る／進むはRTLの方向にも対応。ファビコンとSpaceの絵文字は維持しました。Lucide・FeatherとAndroidパッケージのライセンスをAPKへ同梱し、推移的依存を含む127モジュールの一覧を更新しました。

画面: [ホーム](screenshots/lucide-home.png)・[設定](screenshots/lucide-settings.png)・[右クリックメニュー](screenshots/lucide-context-menu.png)。

最終検証: Debug／Releaseビルド（Releaseはunsigned）、単体39件、端末35件が成功。失敗・スキップ0。端末テストはAPI 36のOrbit_Tablet_QAで実行し、既存の表示・CJK入力・スクロール・WebView・ファビコン・サイドバーと、新規パスキーテスト3件を含みます。Lintはエラー0・警告24（追加分はWebKitの更新通知）。同梱ライセンス11ファイルはDebug／Release APK内とバイト単位で一致しました。物理端末のパスキー登録／ログイン成功は未検証です。

## サイドバー境界のリサイズとアクセントカラー（2026-10-04）

境界にマウスを合わせると水平リサイズカーソルとガイドを表示し、ドラッグ中に幅を反映します。展開時の最小幅220dpで72dpのレールへ自動的に折りたたみ、境界を右へドラッグすると再展開します。折りたたむ前の幅は保持し、通常の展開ボタンでも復帰できます。設定の幅スライダーも最小値で折りたたみます。最大幅は380dpです。右クリックとキャンセルしたドラッグは設定を変更しません。幅計算は画面上の座標を基準にし、ドラッグ中の境界移動を補正します。

Appearanceにアクセントカラー6色とカスタムHEX入力を追加。ボタン、スイッチ、選択チップ、選択タブなどのテーマ色を更新し、DataStoreへ保存します。Spaceの色・絵文字は別の設定です。任意の色の表示はライト／ダークの背景に対して文字コントラスト4.5以上になるよう調整し、入力した元の色を保存します。

画面: [最小幅での折りたたみ](screenshots/sidebar-minimum-collapsed.png)・[カスタム色のライト表示](screenshots/custom-accent-light.png)・[カスタム色のダーク表示](screenshots/custom-accent-dark.png)。

検証: 単体41件と端末38件が全件成功。選択チップの最終配色と、描画を挟んだ複数移動のリサイズ操作を含むAppearanceUiTest 12件も追加実行して全件成功しました。Debug／Releaseビルド（Releaseはunsigned）とLintが成功。API 36のOrbit_Tablet_QAでマウス・タッチ入力を検証し、実物のマウス／トラックパッドは未検証です。

## 日英韓中の表示と検索エンジン4件の追加（2026-10-04）

搜狗（`sg`）・360（`360`）・抖音（`dy`）・神马（`sm`）を追加。標準は13件です。初回起動と既存データへの一度限りの追加に対応し、同じID／キーワードの既存エンジンを保持します。削除・編集を次回起動で巻き戻しません。抖音のパス検索は空白を`%20`に変換します。CJK検索語と`+`・`/`・`?`・`&`のURL生成を検証しました。

日・英・韓・中（簡体字）各219文言を標準Androidリソースに追加。ホーム、設定、サイドバー、メニュー、検索候補、権限／ダウンロード確認、読み上げ用説明を対象にしています。ユーザーの名前やページタイトルは保持します。`localeConfig`に4言語を宣言し、Android 13以降のアプリ別言語設定に対応。翻訳したコマンド名と英語名の両方で検索できます。

4言語のホーム・ダーク設定画面・コマンド操作をAPI 36のエミュレーターで検証。各言語で4エンジンが表示されることを確認しました。言語変更中にActivityを管理するComposeテストが待ち続けたため、端末のアプリ別言語を設定してからActivityを起動する方式にし、4件すべて成功しました。言語はテスト後に復元します。

カスタムアクセントの描画における文字コントラストの余裕も改善しました。文字のアンチエイリアスを含む描画で基準をわずかに下回ったため、カスタム色のprimary／container文字色の調整目標を5.0に上げています。

画面: [日本語ホーム](screenshots/locale-ja-home.png)・[韓国語設定](screenshots/locale-ko-settings.png)・[中国語コマンド](screenshots/locale-zh-CN-commands.png)・[英語設定](screenshots/locale-en-settings.png)。[言語設定・検索URL・実装範囲](LOCALIZATION.md)。

最終検証: 単体44件・端末42件が全件成功（失敗・スキップ0）。Debug／Releaseビルド成功（Releaseはunsigned）、Lintはエラー0・警告25（追加分はlocaleConfigがAPI 33以降で使われることの通知）。API 36のOrbit_Tablet_QAで実行。物理端末・Android 8–15・各検索サービスでの実検索結果は未検証です。

## 2026-10-04 — Space icons and theme color

Space編集に絵文字／Lucide切り替え、各24種の折り返しグリッド、選択プレビュー、任意の絵文字入力を追加。Lucideは`lucide:<name>`を既存のSpace icon列へ保存し、旧絵文字・記号も表示できます。

アクセントカラーをテーマカラーへ改名し、ブラウザーの背景・パネル・選択色にも反映。DataStoreの既存`accent_color`キーを維持して保存済みの色を引き継ぎます。ライト／ダークと極端な色の文字コントラストを検証しました。

検証: Debug APK・AndroidTest APKビルド成功、単体テスト44件成功、Lintエラー0。API 36のOrbit_Tablet_QAで`AppearanceUiTest#customThemeColorPersistsAndRemainsReadableInBothThemes`、`AppearanceUiTest#lucideAndEmojiPersistAcrossRecreationAndBothThemes`成功。Lucide・任意の絵文字の保存、Activity再生成後のLucide復元、両テーマの選択画面とカスタム色表示を確認。実機では未検証です。


## 2026-10-05 — テーマカラーでベース配色を変更

固定の緑色パレットへ選択色を重ねる方式を修正。各surfaceの明るさを保ちながら元の色味を除き、選択したテーマカラーで背景・パネル・枠線を生成します。画面全体とサイドバーへ重ねていたSpace色も外し、サイドバーのグラデーションをテーマカラーへ統一。Space色はSpaceの識別表示で維持します。既存のテーマカラー設定と保存キーは変更していません。

検証: Debug／AndroidTest APKビルド成功、単体45件・AppearanceUiTest 13件成功（失敗・スキップ0）、Lint成功、git diff --check成功。API 36のOrbit_Tablet_QAで、Spaceが緑のままでも赤・青のテーマカラーがサイドバーの実描画へ反映されること、ライト／ダークでの文字コントラスト、Activity再生成後の色の保持を確認。単体テストでは赤・青・グレーのsurfaceに固定の緑色が残らないことも検査しました。ライト赤／ダーク青の画面全体を目視確認。画面記録は`app/build/reports/theme-base-validation/`。実機では未検証です。

## 2026-10-05 — 柔らかい角丸のoverflow menu

ページ・タブ・Favorites・Spaceの3ドットメニューを`NagiOverflowMenu`／`NagiOverflowMenuItem`へ統一。外側24dp、内側は外側から8dpのinsetを引いた16dp、上下12dp、項目52dp以上、左右はカード8dp＋項目12dp。surfaceContainerHighと6dpの影を使い、hover／focus／pressのMaterial indicationを内側の角丸でclipします。アイコンとラベルを中央揃えにし、削除／Favorite除去だけerror色に設定。タブとFavoritesは3ドットのBoxをアンカーに変更しました。設定の検索エンジン選択と右ペインのタブ選択は変更していません。

[標準DropdownMenu](https://developer.android.com/reference/kotlin/androidx/compose/material3/DropdownMenu.composable)のPopup位置調整、スクロール、focus、fade／scaleアニメーションを保持。行は固定高さにせず拡大文字で伸びるようにしています。

API 36のOrbit_Tablet_QAでOverflowMenuUiTest 3件、BrowserUiTest 3件、SidebarDragTest 5件、計11件が一括成功（失敗・スキップ0）。無効項目、クリック後の閉じる動作、戻るキー、ライト／ダークのhoverで角の背景を保持すること、2倍の文字サイズでの行の伸長と長いラベルの折り返し・項目へのスクロール、既存の右クリックとドラッグ操作を検証。SidebarDragTestのSpace編集用selectorは、既存のアイコンpickerの現在のラベル`Custom emoji`へ修正しました。

実際のページメニューとライト／ダークの共通カード・hover・拡大文字を目視確認。画面記録は`app/build/reports/overflow-validation/`。拡大文字テストはPopup内のLocalDensityを2倍にしたcomponent検証で、OS設定変更の検証ではありません。物理端末・ColorOS上では未検証です。

最終チェック: Debug／Release（unsigned）／AndroidTest APKビルド成功、Lint成功（エラー0、既存警告25）、`git diff --check`成功。ドメイン／保存処理の変更はありません。

## Shared corner geometry (2026-10-05)

- Rectangular controls, search fields, tab/new-tab rows, favorites, Space/icon pickers, settings cards, command results, dialogs and overflow menus use `NagiShapes.Rounded` (12dp). The Material shape scale uses the same geometry; rectangular text buttons explicitly use it.
- The sidebar clips its gradient and children at its top-end/bottom-end corners, including its collapsed form. WebView content uses matching bottom corners.
- Clickable library rows, settings rows and command results clip press/hover feedback to the same shape as their visible backgrounds and borders.
- Circular icon buttons, switches and color dots retain their circular/capsule geometry. Small favicon tiles (6dp) and narrow drag/resize indicators (2–4dp) retain geometry appropriate to their size.
- Debug app/test builds and `git diff --check` passed. API-36 emulator validation covered 26 cases across AppearanceUiTest, OverflowMenuUiTest, LocalizationUiTest and SidebarDragTest: 25 passed in the combined run; the new corner/press case passed on rerun after waiting for the deferred scroll-container press indication. Its pixel assertions cover both sidebar end corners and clipped new-tab press feedback in light/dark modes.
- Inspected emulator screenshots for light/dark home, Chinese settings and dark overflow hover. Physical-device visual verification remains pending.

## Sidebar Space swipe (2026-10-05)

- Quick horizontal touch swipes across the sidebar select the adjacent Space in its saved order: left advances, right goes back, and endpoints do not wrap. Works with the sidebar expanded or collapsed.
- Direction locks after touch slop; vertical motion yields to scrolling. Long-press tab drags, immediate favicon drag handles, and mouse drags retain their existing behavior. A threshold distance and normal release are required; canceled/multitouch gestures do not select a Space.
- Debug app/test builds, APK signature verification and `git diff --check` passed. All 8 SidebarDragTest cases passed on the API-36 emulator, including switching from the new-tab button without creating a tab, quick tab-row swipes, canceled/vertical gestures, endpoint behavior, recreation persistence, collapsed sidebar, and existing mouse/touch drag operations. Inspected the resulting sidebar screenshot; physical-device verification remains pending.

## Responsive sidebar favorites (2026-10-05)

- Favorite tiles fill each row using equal widths. Column capacity comes from the available sidebar content width with a 72dp minimum tile width and 6dp gap; the column count is capped by the actual favorite count. At sidebar widths of 220/264/380dp this gives 2/3/4 columns. Incomplete final rows retain the same tile width as preceding rows.
- Debug app/test builds, APK signature verification and `git diff --check` passed. All 9 SidebarDragTest cases passed on the API-36 emulator, including measured two-item full-width filling, reflow at 2/3/4 columns, existing drag/drop and Space swipes. Inspected two-item and four-column screenshots.

## ChatGPT command-bar handoff (2026-10-05)

- Non-URL search input always offers the configured search engine and ChatGPT as the first two actions. ChatGPT comes first for 80+ Unicode code points, trailing question marks, or explanation/comparison/summary request markers in Japanese, English, Simplified/Traditional Chinese and Korean. Explicit search-engine keywords retain priority. URLs, unsupported schemes, empty input and `>` commands do not offer ChatGPT.
- Reuses the command bar's Up/Down and Enter handling, selected-row border/background, click/touch execution and IME composition guard. ChatGPT uses a lightning icon. Labels are localized in Japanese, English, Korean and Simplified Chinese.
- The action navigates to `https://chatgpt.com/?q=…`, encoding the question as one query parameter. No API key or page-content extraction is used. ChatGPT login, prompt prefill and submission remain provider-controlled; actual remote prompt acceptance/answer generation and physical-device behavior were not verified.
- Debug app/test builds and all 54 unit tests passed, including 5 new policy/encoding cases. API-36 emulator: 2 new component tests passed (arrows, Enter, touch, query-change selection reset), 3 existing omnibox CJK composition tests passed on the targeted rerun, and 1 real MainActivity handoff test passed (active tab URL updated, command bar dismissed). Inspected light/dark screenshots under `app/build/reports/chatgpt-validation/chatgpt-validation/`.
- The initial combined run had a Japanese test-IME binding failure (passed on targeted rerun) and the unrelated existing settings-engine-name test failed because its `Add search engine` selector assumes the previous settings layout. That test was left unchanged; its settings-editor coverage is not claimed here.
- Debug lint passed with 0 errors and 25 warnings; Release (unsigned) build and `git diff --check` passed.

## 常用検索エンジン（2026-10-05）

登録一覧と常用候補を分離。未設定時は中国大陸で百度＋千问、それ以外でGoogle＋ChatGPT。設定のチェックボックスとChatGPTスイッチで組み合わせを保存し、手動変更時は地域自動設定を無効にします。常用外のキーワードは通常検索の文字列として扱います。

単体61件、API 36エミュレーター対象7件（CommonSearchEnginesUiTest 1件、ChatGptCommandBarUiTest 2件、LocalizationUiTest 4件）が全件成功。Debug / Releaseビルド成功、Lintエラー0・警告25。常用選択の保存・Activity再生成・候補追加と除外・未選択360キーワードの通常検索・4言語設定を確認。物理端末・中国大陸の実ネットワークは未検証。画面記録は `app/build/reports/common-search-validation/`。

## 2026-10-06 — drag tabs into split view and filled theme presets

- A sidebar tab can be dropped into the left or right half of the browser area. The drop target highlights and the tab preview follows the pointer outside the sidebar. Existing split panes can be replaced or swapped; canceled gestures leave the layout unchanged.
- The settings header uses the localized Settings title. Theme preset buttons use their own color across the button, with contrasting labels and a check on the selected preset.
- Verified with an isolated clean build and an API 36 tablet emulator: debug app/test APKs, unit tests, and lint passed. Two split-drop UI tests covered touch, mouse, cancellation, left/right placement, replacement, and swapping. Two existing sidebar drag tests passed. The preset UI test checked all six colors, selection, and screen-pixel text contrast in light and dark themes.

## 2026-10-06 — Sidebar split pair

- When two panes are visible, the expanded sidebar groups their tabs in a single rounded card, with the left page on the left and the right page on the right. Equal-width tiles show each favicon and a single-line ellipsized title. The group occupies the left tab's section; the right tab is not duplicated elsewhere.
- Clicking a grouped title focuses that pane without replacing either page. Clicking its icon opens the existing tab actions; favicon-handle and whole-tile dragging remain available. Closing one page restores the ordinary sidebar row.
- Debug app/test builds, lint, and diff whitespace checks passed. Three targeted SidebarDragTest cases passed on an isolated API-36 tablet emulator, covering grouped geometry/focus, mixed pinned state, right-page closure, drag cancellation, left/right placement, replacement, and swapping. The group test was rerun after adding a wait for actual dark-mode/narrow-width rendering and passed. Inspected light and dark 220dp-sidebar screenshots in `app/build/reports/split-sidebar-validation/`. Physical-device validation is not included.

## 2026-10-06 — Split pane title alignment

- Both pane title rows reserve 48dp and center their contents vertically, matching the right pane's tab picker. The progress/divider slot reserves 2dp so independent loading states do not shift the page content.
- Debug app/test builds and `git diff --check` passed. API-36 tablet emulator: existing split creation/resize/swap/close and sidebar split grouping/focus tests passed (2 cases). Inspected light/dark screenshots in `app/build/reports/split-title-validation/`, including a loading right page beside an idle left pane. Physical-device validation is not included.


## 2026-10-06 — Back from newly opened tabs

- System Back and the Back keyboard shortcut use the focused page history first. At the start of a normal tab's history, if another visible tab exists in the space, Back closes the focused tab and selects its opener. A missing/closed/archived/moved opener falls back to a remaining tab in the same space. The final visible tab and pinned tabs retain their existing exit behavior when no page history exists; fullscreen Back still exits fullscreen first.
- Selected new tabs remember the previously selected tab; page-created tabs explicitly remember their source engine's tab, including split panes. Parent IDs are persisted with a Room version 1-to-2 auto-migration that preserves existing data. Closing an active tab also prefers its live opener.
- Debug app/test builds and lint passed. All 78 unit tests passed, including nested openers, unrelated neighboring tabs, an unselected split child, unavailable parents, and a populated version-1 database migration. API-36 tablet emulator: all three TabBackUiTest cases passed using device-local pages, covering a real target=_blank link, open-links-in-new-tab with Activity recreation, and in-tab history before child closure. Physical-device gesture validation is not included.
## 2026-10-06 — Address action spacing and Space selection frame

- Both address bars use 32dp-wide copy/share buttons instead of 48dp, with unchanged 16dp icons. Space selection frames use 36dp squares inside the existing 48dp click/drop targets.
- Debug app/test builds and `git diff --check` passed. API-36 tablet emulator: SidebarAddressBarTest and the existing Space tap/drag-to-Space cases passed (3 tests). Inspected the rendered address bars and selection frame in `app/build/reports/address-spacing-validation/sidebar-address-always-visible.png`. Physical-device verification is pending.

## 2026-10-06 — 0.1.1 release validation

- Unit tests: 98 passed, including update manifest validation, APK download/checksum/signature/version checks on API 26 and 35, and file-picker extension contracts.
- API-36 tablet emulator: all 91 cross-feature instrumentation tests passed. Includes right-pane keyboard focus, visible-page archive protection, bookmark flows, confirmed/canceled site-data clearing, real WebView link/image context menus, cookie/localStorage/IndexedDB removal, and stalled-navigation timeout/recovery.
- After including the address-action and Space-frame UI changes, all 17 SidebarAddressBarTest/SidebarDragTest cases passed again.
- Signed, non-debuggable Release build succeeded. Debug Lint: 0 errors, 34 warnings. These checks used the emulator; no physical device was connected.
- Installed the Nagi 0.1.0 baseline (code 1), seeded a named Space and Bookmark, and installed the signed Release with `adb install -r` without uninstalling. Android reported 0.1.1/code 2, and both records remained visible in the running Release UI.
