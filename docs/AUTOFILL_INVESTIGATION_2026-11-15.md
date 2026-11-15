# Androidパスワード自動入力の調査（2026-11-15）

## 結論と確認範囲

過去の実機でGoogleの候補が一切出なかった原因は、現時点では特定できていない。
当時のOS、WebView、IME、選択中サービス、APK署名、要求・応答の記録がないため、
「Googleの許可リストが原因」「Composeが原因」とは断定しない。
`PasswordAutofillAvailability.approved = false` を維持する。

この調査では、合成プロバイダーでアプリからOS、候補UI、HTML入力欄への配送を検証する。
Googleの実データ、Googleアカウントへのログイン、実機での入力完了は検証に含めていない。
Bitwardenは導入していない。要求・応答・UIを制御できる既存の合成プロバイダーで
先にアプリ側を切り分けられ、Bitwardenの成功だけではGoogleの拒否理由は分からないためである。

## 実装の確認

| 対象 | 確認した動作と注意点 |
| --- | --- |
| `WebViewBrowserEngine` | Activityを基にした`ContextThemeWrapper`でWebViewを作る。Application Contextだけで作る経路ではない。 |
| `NativeSurface` | Composeの`AndroidView`内のFrameLayoutは`IMPORTANT_FOR_AUTOFILL_NO`。子を除外する`NO_EXCLUDE_DESCENDANTS`ではない。 |
| WebView | 有効時は`YES`、リソースIDは`browser_web_view`。未承認時・プライベート時は子を含めて除外する。Readerと一時popupも除外する。 |
| 仮想HTML欄 | `super.onProvideAutofillVirtualStructure()`に処理を委譲する。Webドメイン、HTML情報、autocomplete、仮想IDをアプリで作り直していない。 |
| `FocusedAutofillStructure` | 実構造への転送を維持し、`setFocused`と`setAutofillId(parent, virtualId)`の順序に依存せずIDを観測する。値、テキスト、HTML属性を別途保持しない。追加のプラットフォームメソッドも転送する。 |
| `WebViewAutofillCallbacks` | ActivityのAutofillManagerの単一コールバック枠を共有し、WebViewインスタンスで振り分ける。新しいWebViewが登録する監視を再度置き換える。これはUIイベントの観測であり、要求到達や候補件数の通知ではない。 |
| 手動要求 | メニューのwindow終了を待ち、text editorと仮想IDを確認して`requestAutofill(webView, virtualId, bounds)`を呼ぶ。WebViewコンテナだけへの要求ではない。 |
| 座標 | 既存実装は最後のtouchを`getLocationOnScreen()`で画面座標に変換し、1pxの矩形を渡す。入力欄自体の矩形ではない。キーボード移動・スクロール・Splitでは追加確認が必要。 |
| IDの寿命 | navigationで破棄する。133では、UIコールバックなしで同一文書のusernameからpasswordへ移動する追加ケースも成功した。 |

Activity Context、仮想構造の生成とイベント通知はAndroidの公開契約に従う。
[AndroidのAutofill最適化](https://developer.android.com/identity/autofill/autofill-optimize)、
[WebViewの仮想構造契約](https://developer.android.com/reference/android/webkit/WebView#onProvideAutofillVirtualStructure(android.view.ViewStructure,int))、
[手動要求のIDと画面座標](https://developer.android.com/reference/android/view/autofill/AutofillManager#requestAutofill(android.view.View,int,android.graphics.Rect))。

WebView 133のChromium実装では、現在のformとfocused fieldから構造を同期生成する。
`FormData.fillViewStructure()`は各欄に固有の仮想ID、focus、入力型、hint、矩形を設定する。
`AutofillManagerWrapper`は構築時にサービスの有効状態を調べ、独自のUIコールバックを登録する。
そのため、テストがサービスを切り替える前にWebViewを作ると、検証環境の初期化順序にも注意が要る。
[FormData.java（133）](https://chromium.googlesource.com/chromium/src/+/refs/tags/133.0.6943.137/components/android_autofill/browser/java/src/org/chromium/components/autofill/FormData.java)、
[AutofillManagerWrapper.java（133）](https://chromium.googlesource.com/chromium/src/+/refs/tags/133.0.6943.137/components/android_autofill/browser/java/src/org/chromium/components/autofill/AutofillManagerWrapper.java)。

### 確認できた別の不具合：古いWebViewでの手動再要求

Android 11 / WebView 91.0.4472.114では、自動入力、標準IMEでの入力、UI通知のある手動要求は成功した。
一方、UI通知を抑えた手動要求の4ケースは仮想IDが取得できず、要求を送る前に停止した。
91の`AutofillRequest.fillViewStructure()`は`setAutofillId()`、hint、矩形などを設定するが、
`setFocused()`を呼ばない。133では呼ぶ。このためNagiの構造観測は91でfocusを検出できず、
UIコールバックもなければIDを補完できない。
[WebView 91の構造生成](https://chromium.googlesource.com/chromium/src/+/refs/tags/91.0.4472.114/components/autofill/android/provider/java/src/org/chromium/components/autofill/AutofillProvider.java)。

これは「入力欄が選択されていない」という説明の誤りと、古いWebViewでの互換性制限である。
選択欄を識別できない場合の案内を6言語で修正し、IDを作らず停止する回帰確認を追加した。
WebView更新を案内するが、Googleの候補提供が保証されるとは説明しない。
**古いWebViewの手動入力そのものを修復したわけではない。元の実機のGoogle不表示原因も未特定のまま。**

同じAndroid 11端末で、署名検証済みの公式イメージ由来のWebViewとTrichromeを133へ更新すると、
同じ比較9件すべてが成功した。OSを変えずに結果が変わったため、この再現条件の主要因はWebViewの版差である。
どの中間版から対応したかは未調査であり、133を最小対応版と断定しない。

## Googleの署名・ドメイン検証について

AndroidのAutofillService契約は、Webドメインを正規化してDigital Asset Linksとアプリ署名を
確認する経路、および署名で真正性を確認した既知ブラウザーを許可する経路を説明している。
関連付けを検証できない場合の、ユーザー確認を伴うプロバイダー側の経路もある。
したがって、構造がOSへ届いていることと、プロバイダーがそのブラウザーへWeb資格情報を
提供することは別の条件になる。
[AutofillService: Web security](https://developer.android.com/reference/android/service/autofill/AutofillService#web-security)。

これをNagiに当てはめると「Google側のブラウザー信頼判定で候補が返らない」は仮説になる。
従来Autofillに対するGoogle内部の全許可条件・拒否理由は、この資料からは分からない。
一般のブラウザーを任意のサイトの所有アプリとして関連付けたり、ドメインを変更したりしない。
アプリ所有サイトとアプリの関連付けを扱う実装記事を、汎用ブラウザーへそのまま適用しない。
[GoogleのAutofill実装記事（2017、歴史的資料）](https://android-developers.googleblog.com/2017/11/getting-your-android-app-ready-for.html)。

Credential Managerの`setOrigin`に関するGoogleのprivileged-app承認は別に確認する。
今回取得した[公開リスト](https://www.gstatic.com/gpm-passkeys-privileged-apps/apps.json)に
`com.takeruf.nagi`はなかった（JSON SHA-256:
`0891ee2507894999ead3eb4f42e17936a2e8ed8f8fd95592535c9811ef319481`）。
この未掲載を、従来Autofillのパスワード候補が返らない直接の証拠として使わない。
[Credential Managerのprivileged apps](https://developer.android.com/identity/sign-in/privileged-apps)。

Chromeのパスワード候補は内蔵の経路を使う場合がある。Chromeでの成功は、同じAndroid
AutofillService経路がNagiでも成功した証明にはならない。比較対象としては有用である。
[Chromium Android Autofill構成](https://chromium.googlesource.com/chromium/src/+/refs/heads/main/components/android_autofill/README.md)、
[Chromeの第三者Autofill対応記事](https://android-developers.googleblog.com/2024/10/chrome-3p-autofill-services.html)。

## 要求・候補・UIを区別する診断

| 観測 | 言えること | 言えないこと |
| --- | --- | --- |
| `ENGINE_ENABLED`だけ | 診断ビルドのエンジンで機能が有効 | フィールドの検出、要求到達 |
| `STRUCTURE_REQUESTED` | フレームワーク側からWebView構造の取得が呼ばれた | プロバイダーが受信・解析したか |
| `FOCUSED_FIELD_OBSERVED` | ネイティブ構造にfocused仮想IDがあった | 現在も同じ欄か、候補の有無 |
| `MANUAL_REQUEST_CALLED` | アプリが手動要求APIを呼んだ | OS/Googleでの受理・候補数 |
| 合成サービスの`onFillRequest` | プロバイダーまで要求が到達した | Googleへも同様に届くか |
| 合成サービスのnull応答 | この検証では候補なしを意図的に再現した | Googleの候補なし理由 |
| 合成サービスのDataset応答 | 候補をOSへ返した | UIが表示されたか |
| `UI_SHOWN`または候補UIの目視 | 該当UIが表示された | 認証・選択・入力の完了 |
| `UI_UNAVAILABLE` | OSが利用不可のUIイベントを送った | 未一致、信頼拒否、timeout等の内訳 |
| `FILL_DELIVERED fieldCount=N` | N個の仮想欄への配送がWebViewへ来た | サイトへのログイン成功 |
| DOMの合成値一致 | fixtureで実際に入力された | 本物のアカウント認証成功 |

UIイベントが来ないだけでは「候補なし」「要求なし」を区別できない。
Android 11以降のinline表示はIMEとサービス双方の対応に依存する。
非対応時はpopupへfallbackする。今回の合成プロバイダーはpopupを返すため、Googleのinline表示は別検証になる。
[IMEとAutofill](https://developer.android.com/identity/autofill/ime-autofill)、
[UIコールバックの意味](https://developer.android.com/reference/android/view/autofill/AutofillManager.AutofillCallback)。

## 検証記録

開始時のHEADは`e331719329e98056fbbb772beae40740fd3e5155`。
既存の未コミットUI・翻訳作業を含むソースをignored領域へスナップショットし、
ビルド出力とQA端末を他の作業から分離した。元の変更は取り消していない。
証跡は`artifacts/autofill-investigation-20261115/`に保持する。

最初の単体テスト193件は失敗・error・skipとも0。
最初の既存instrumentation 8件は6成功・2失敗。手動ケース1件はページメニューのaccessibility探索でtimeoutし、
要求APIを呼ぶ前に停止した。Settings非表示確認1件は狭い画面で存在しないsidebar Settingsを探して停止した。
この2件をGoogle拒否や構造公開の不具合として数えない。メニュー探索はComposeのtestTagへ変更した。

最終の製品コードと6言語文字列を含むスナップショットで、次が成功した。

```sh
./gradlew :app:testPlayDebugUnitTest :app:lintPlayDebug :app:lintPlayRelease :app:assemblePlayDebug :app:assemblePlayDebugAndroidTest --no-parallel
```

単体193件は失敗・error・skipとも0。Debug/Release lintは各0エラー・82警告。
署名付きRelease/AABビルド、公開、Playへのuploadは行っていない。

| 環境と選択 | 結果 | 証跡 |
| --- | --- | --- |
| Android 16 / WebView 133、電話幅（約411dp）、8件 | 8成功、失敗・skip 0 | `api36-phone-system-touch.log`と`api36-phone-final-TEST-*.xml` |
| Android 16 / WebView 133、1600×1000dp、4件 | 4成功、失敗・skip 0 | `api36-wide-tests.log` |
| Android 11 / WebView 91、1600×1000dp、比較9件 | 5成功・4失敗。4件はUI通知なしでIDを識別できない既知の互換性制限 | `api30-tests.log` |
| Android 11 / WebView 91、修正案内の回帰1件 | 1成功。誤ったfocus案内を出さず、IDを推測せず、manual要求0を確認 | `api30-legacy-regression.log` |
| 同じAndroid 11をWebView 133へ更新、比較9件 | 9成功、失敗・skip 0 | `api30-webview133-tests.log` |
| 別packageの診断ビルド / Android 11 / WebView 133、4件 | 4成功、失敗・skip 0。テストIMEの実接続と元のGboard、自動・手動・null応答を確認 | `diagnostic-runtime-tests.log` |

電話幅の試験はシステムのtouchを使い、ネイティブfocusと祖先Viewの非除外設定も確認した。
wideではdarkの右Splitペインに候補が現れ、両WebViewのresource IDが同じでもAutofillIdが異なり、
対象欄への配送が成功した。`split-suggestion.png`を目視確認した。
これは2個のCompose/native surfaceでの対象確認であり、保存Splitペアのrepository動作を再検証したものではない。
ページメニューは安定したComposeのアクセシビリティclickで選択し、候補の選択はシステムUIへの実tapを使う。

途中の2件選択の試行は`focus-regression-before`、`stable-ui`、`ready-provider`、`native`で各2失敗、
`semantics`で2成功だった。失敗にはメニュー探索、テスト側のサービス初期化待ちのContext誤り、
アニメーション中の座標clickが含まれる。追加した即時focus assertionも、直接`dispatchTouchEvent`する
旧fixtureでは8件中6件が失敗したため、システムtouchとfocus完了待ちに改めた。
これらを製品の修正済み障害やGoogleの失敗として数えていない。
最初のlintでは追加harnessの`FillContext.getFocusedId()`に2件のNewApiエラーが出たため、
API 29 guardを追加して再実行し、0エラーを確認した。

通常Debug APK SHA-256:
`a9ac57f3a0b4adb5f6909d979b5f606e0a01cb4a350fcf3305e218e3abdf7195`。
診断APK SHA-256:
`e6d598b42201082b3dba85b2d2d84d6530cbb5495b881c8cc02a2d2d24ee1d75`。
成果物はそれぞれ`standard/`と`diagnostic/`に保持する。
診断APKのinstalled base.apkのSHA-256も保存APKと一致した。
`diagnostic-events.log`にはUI表示、手動要求、2欄への配送3回、利用不可通知を確認した。
全NagiAutofill行が固定enumと任意の件数に一致し、合成のusername/passwordやURLは含まれていない。
両QA端末のサービスはGoogle、IMEは元のGboardに復元された。
`-PnagiAutofillDiagnostics=true`で生成した場合にもReleaseの`DEBUG`と診断フラグはfalse、
packageは`com.takeruf.nagi`のまま、merged Release manifestにテストservice/IMEは含まれないことを確認した。

調査中に別の作業でHEADと未コミットUIが進んだため、上記は作業ツリー全体の最新HEADに対する
フルスイート結果ではない。Autofill関連11ファイルは`source-hashes.json`で最終作業ツリーとの一致を確認した。
新規案内以外の既存4言語文字列は開始時の内容が維持されていることも確認した。
API 26–29、物理端末、現行WebView stable、Googleのinline/実vault、実サイトでのログインは未検証。

追試では、[朝日ID・GitHubの実フォーム](AUTOFILL_LIVE_SITES_2026-11-15.md)で合成候補の
表示・選択・入力まで成功した。朝日IDはpasswordの2段階目も確認したが、最終ログインは送信していない。
Google選択時の構造要求と手動要求も観測した。Google用の保存済み試験資格情報がなく、
元の実機の不表示原因とGoogle vault提供の可否は引き続き未確定である。

## OS・WebView・キーボードの比較方針

API 26からFrameworkが利用できる。`setSaveFormData(true)`はAndroid 8以降では解決策にならない。
Android 8.0/8.1には既知のAutofillとdialogの問題があるため、後のAndroidの結果で代用しない。
API 36では`showAutofillDialog`や`notifyVirtualViewsReady`はno-opと記載されている。
Chrome固有のTouch to Fillや旧API呼び出しを追加して解決したとしない。
[Android 8のWeb form変更](https://developer.android.com/about/versions/oreo/android-8.0-changes#web-form-autofill)、
[既知のOS問題](https://developer.android.com/identity/autofill/autofill-optimize#address-known-issues)、
[API 36のdialog API](https://developer.android.com/reference/android/view/autofill/AutofillManager#showAutofillDialog(android.view.View,int))。

実機ではOS、WebView、Google Play開発者サービス、IMEのバージョンを記録し、1要因ずつ変えて比較する。
同じ試験でWebViewとIMEを同時に変えない。WebViewのdowngradeやpersonal端末のprovider変更は自動で行わない。

## デバッグ診断ビルドとADB

通常のDebug/Releaseは引き続き未承認で無効。明示的なフラグを付けたDebugだけで有効化できる。
フラグはReleaseへ適用されず、QAは別package `com.takeruf.nagi.autofillqa`になるため
Play版のインストール、workspace、Cookieを置き換えない。
**QAのpackageとdebug署名はPlay版と異なる。GoogleがQAへ候補を出さない結果で、
Play署名の信頼判定を結論付けない。**

```sh
./gradlew :app:assemblePlayDebug -PnagiAutofillDiagnostics=true --no-parallel
# SERIALを明示する。Play版をアンインストールしない。
adb -s SERIAL install -r app/build/outputs/apk/play/debug/app-play-debug.apk
adb -s SERIAL shell am start -n com.takeruf.nagi.autofillqa/com.takeruf.nagi.MainActivity
```

ログに渡せるのは固定enumイベントと入力配送件数だけ。URL、フィールドID、HTML、
ユーザー名、パスワード、Cookie、token、provider payloadをログAPIへ渡さない。
通常ビルドでは診断ログを出さない。
診断時はQA版の1タブで再現してから、必要に応じてSplitへ進む。

```sh
adb -s SERIAL shell getprop ro.build.version.release
adb -s SERIAL shell getprop ro.build.version.sdk
adb -s SERIAL shell dumpsys webviewupdate
adb -s SERIAL shell settings get secure autofill_service
adb -s SERIAL shell settings get secure default_input_method
adb -s SERIAL shell cmd autofill list sessions
adb -s SERIAL shell dumpsys package com.google.android.gms | rg 'versionName='
adb -s SERIAL shell dumpsys package com.google.android.inputmethod.latin | rg 'versionName='
adb -s SERIAL logcat -v brief -s NagiAutofill:I '*:S'
```

`cmd autofill list sessions`は短時間で消えるセッションもあるため、空でも「一度も要求がなかった」とは限らない。
アプリの診断だけではGoogleの応答内容は分からない。一般の`dumpsys autofill`全文、
verboseのAutofill/Chromiumログ、bugreport、実データ入りの画面・UI hierarchyを採取・共有しない。
必要なら架空の認証情報のみの専用環境で、出力内容を先に確認する。

Play版の署名確認は`pm path com.takeruf.nagi`で表示された**base.apk**をpullし、
SDKの`apksigner verify --print-certs`で証明書SHA-256を調べる。
upload keyではなく、そのインストール物のapp-signing証明書を比較する。
秘密鍵・keystore・署名propertiesの内容は採取しない。

## ユーザーが実機で行うGoogle検証

1. Android設定でGoogleを自動入力サービスとして選び、Google Password Managerの正しいアカウントに
   試験用サイトの架空のログインを1件だけ保存する。既存パスワードは変更・移行しない。
   Googleログイン、画面ロック、本人確認はユーザー自身が行う。
2. まずChromeで同じHTTPSログイン欄に候補が出ることを確認する。アカウント・保存先・URIの基準点にする。
   候補の表示、選択、欄への入力、サイトログインは別々に記録する。
   [Googleの保存と設定](https://support.google.com/accounts/answer/95606?co=GENIE.Platform%3DAndroid&hl=en)。
3. QA版で同じ欄をtapする。ユーザー名欄とpassword欄をそれぞれ試し、自動候補の有無を記録する。
   次に同じ欄を選び、ページメニューの「保存済みパスワードを入力」で手動要求する。
4. 上記の安全なmetadataと`NagiAutofill`イベントを記録する。
   popupかキーボードstripか、`UI_SHOWN`の有無、`FILL_DELIVERED`の有無を分ける。
5. すでにインストールされている別IMEがあれば、ユーザー操作で切り替え、同じ手順を繰り返す。
   keyboard学習やクリップボードに実パスワードを入れない。比較後に元のIMEへ戻す。
6. narrow/wide、Splitの左右、スクロール後、別欄への移動、identifier-only、複数段階、画面回転、
   別タブ往復、候補のキャンセルを確認する。プライベートとReaderでは機能が無効のままか確認する。
7. 同じ実機で合成provider経路も検証する場合は、専用QA環境でinstrumentationを使う。
   service/IMEの切替が発生し、テストが元へ戻す。個人端末へ無断で実行しない。

現在のPlay版はゲートが閉じているので、保存パスワードが出ないのが期待される状態。
承認後の本番package・本番署名での検証は、別の明示的なビルド・配布指示が必要である。

## Bitwardenによる任意の比較（未導入・未実行）

Googleだけで候補が出ず、OS経路と信頼判定の切り分けをさらに進めたい場合に限り行う。
無料個人プランでパスワード保存とAutofillを試せる。
この手順の記載はアカウント作成やサービス切替を代行したことを意味しない。
[無料プラン](https://bitwarden.com/pricing/)、
[Android Autofill設定](https://bitwarden.com/help/auto-fill-android/)。

1. ユーザーが公式アプリをインストールし、専用の無料試験アカウントを作成・メール確認し、vaultをunlockする。
   master password、メール認証、本人確認はユーザー操作。Googleからimportしない。
2. Google側の試験と同じHTTPS URIに、架空のusername/passwordを1件だけ手入力で登録する。
   NagiアプリURIへの追加関連付けや広いURIマッチ規則を使って、ブラウザー信頼判定を回避しない。
3. ユーザーがAndroidの自動入力サービスをBitwardenへ切り替える。
   最初はBitwardenの「Popup」で実施し、次に対応IMEで「Inline」を比較する。
   Accessibility経由のquick tileは別経路なので、このFramework比較では有効化しない。
4. Chrome/Braveを比較対象にする場合は公式のbrowser integrationを有効にする。
   HTTPSだけを使い、HTTPをsecure扱いするflagsは変更しない。
5. Nagi QAの同一fixture/HTTPSフォームで、自動・手動、候補表示、選択、配送を記録する。
   合成provider成功、Bitwarden成功、Google失敗ならGoogle固有条件の仮説が強まるが、
   Googleの具体的な拒否理由やPlay版での動作まで証明するものではない。
6. 比較後はユーザーが元のGoogleサービス・IMEへ戻す。

Bitwarden公式資料はmobileのsplit login workflowsを非対応と説明している。
identifier-only/複数段階での失敗を直ちにNagiの不具合と判定しない。
[制限を含むAndroid手順](https://bitwarden.com/help/auto-fill-android/)。

## 本番有効化前の残課題

- 当時失敗した実機・サイト・入力欄で要求、プロバイダー応答、UI、配送のどこが止まるか特定する。
- Googleの従来Autofillのブラウザー信頼条件を問い合わせで確認する場合は、パスキー承認と分けて記録する。
- 実際のPlay package・app-signing証明書で、Googleの候補選択とHTML欄への入力まで確認する。
- OS/WebView/IME、multi-step、iframe、Split、回転、navigation・タブ切替を実機で検証する。
- 修正した経路の回帰テスト、Releaseのゲート、debug harnessのRelease除外を確認する。
- 承認・本番有効化・リリースは別の判断。今回の診断ビルドや合成テストだけで完了扱いにしない。
