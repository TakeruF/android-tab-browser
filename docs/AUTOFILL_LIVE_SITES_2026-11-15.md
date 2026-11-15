# 実サイトでのAutofill検証（2026-11-15）

## 結果

朝日IDとGitHubの実際のHTTPSログイン画面を、Nagiの別package診断版で開いて検証した。
ローカルに複製したHTMLではなく、Nagi本体のタブ、Compose/native host、ページメニュー、
WebViewとAndroidのAutofillService経路を使用した。

**合成プロバイダーの候補表示・選択・実入力は成功した。Googleの保存済みパスワードが
提供されることや、実際のアカウントでのログイン成功は証明していない。**
本番の`PasswordAutofillAvailability.approved = false`は維持した。

| 実サイト・条件 | 確認結果 |
| --- | --- |
| 朝日ID / wide / 観測のみ | `sso.asahi.com`のID欄をネイティブ構造で受信。自動・手動の両要求がサービスへ到達。応答を意図的にnullにして、候補なしを再現した。 |
| 朝日ID / wide / 自動候補 | OSの合成候補を選び、実際のID欄へ架空IDが入力された。 |
| 朝日ID / phone / 手動候補・2段階 | 自動要求をdeclineしてからページメニューで再要求。ID入力後に「続ける」へ進み、実際のpassword欄でも候補を選択し、架空パスワードが入力された。最終「ログイン」は実行していない。 |
| GitHub / wide / 観測のみ | `github.com`のusername/password双方をネイティブ構造で受信。自動・手動の両要求がサービスへ到達。null応答を確認した。 |
| GitHub / wide / 自動候補 | OS候補の選択で、実際のusername/password両欄へ合成値が入った。 |
| GitHub / phone / 手動候補 | 自動要求をdeclineし、ページメニューで再要求後、候補を選び、両欄への入力を確認した。 |
| 朝日ID・GitHub / phone / Google対照 | 両方でnative構造要求・focus ID観測・手動要求API呼び出しを記録。候補UI表示と入力は確認できず。試験資格情報がないためGoogleの候補提供可否の合否には使用しない。 |

朝日IDの入口は[公式サイト](https://id.asahi.com/)、実際のフォームoriginは
`https://sso.asahi.com`だった。朝日の[公式ログイン案内](https://support.asahi.com/hc/ja/articles/28262728176663)も、
ID → 「続ける」 → passwordの2段階を説明している。
GitHubは[公開ログイン画面](https://github.com/login)を使用した。

## フィールドの公開状況

| 画面 | DOMでのinput type / autocomplete | サービスが受け取った情報 |
| --- | --- | --- |
| 朝日IDの1段階目 | `text` / `username` | `webDomain=sso.asahi.com`、username hint、focusedなAutofillId、正の幅・高さ |
| 朝日IDの2段階目 | `password` / `current-password` | 同じ実originのpassword仮想欄。navigation後の手動要求、Dataset返却、候補選択、入力一致まで成功 |
| GitHub | `text` / `username`、`password` / `current-password` | `webDomain=github.com`、両hint、両AutofillId、正の幅・高さ |

この条件では、朝日SSOの実フォームがNagi/ComposeによってOSから見えなくなる問題は再現しなかった。
ただし、元の実機のOS/WebView/IME、Googleのvaultと署名・ブラウザー信頼条件は別の検証対象である。
実サイトのフォームや検出条件は今後変わり得るため、これは当日の結果である。

## 試験条件と境界

- 専用AVD `Nagi_Autofill_API30_20261115`、`emulator-5592`、Android 11 / API 30、Google WebView `133.0.6943.137`。
- Google Play開発者サービス `20.18.17 (040400-311416286)`、Gboard `9.4.11.312687073-preload-arm64-v8a`。AVD由来の古い版であり、現行実機のGoogleサービスやIMEを再現した結果ではない。
- wideは1080×2400 physical px、density 160（1080dp幅）。phoneは同じphysical size、density 420（約411dp幅）。既存の他のQA・個人端末は使用していない。
- 既存のNagi Autofill診断スナップショットを再利用し、今回のdebug providerとinstrumentationだけを更新した。並行して進んでいるUI作業のビルド結果とは分離している。
- QA packageは`com.takeruf.nagi.autofillqa`。Google Play版のpackage、署名、データとは異なる。
- fake usernameは予約済みの`.invalid`ドメイン、passwordも固定の合成値。アカウント作成、メール認証、実パスワードの利用、最終ログインの送信は行っていない。
- inputへの代入をJavaScriptで行っていない。DatasetをOSへ返し、システム候補への実tapとWebView native Autofillで入力した。DOMの読み取り結果は値そのものではなく、合成値との一致booleanだけである。
- 朝日の次画面への遷移は、ID入力を検証した後、実ページの「続ける」ボタンをDOMでactivateした。これは架空IDの遷移のみであり、パスワード入力や最終認証を代行するbridgeではない。ボタンのtouch操作自体の合否はこの結果に含めない。
- ページメニューはComposeのアクセシビリティclickを使用。候補の選択とフィールドfocusはシステムtouchを使用した。
- Cookie、token、HTML全文、URL query、入力値、Google vaultはログやJSONへ出力しない。画面画像はWebView領域だけをcropし、OAuthパラメーターを表示し得るアドレスバーを除いている。

## 追加した診断・テスト

`LivePasswordAutofillTest`は`liveAutofill=true`に加えて、診断フラグと別packageを要求する。
通常のinstrumentation選択ではopt-inなしでskipする。自動workflowやrelease設定を追加していない。

既存の`AutofillHarnessService`は、デフォルトでは引き続きloopback fixture専用。
実サイト用の分岐は明示的な一時設定があるときだけ使い、さらに以下を確認する。

1. 診断フラグがtrueで、packageがQA用であること。
2. 要求元Activityのpackageが、このQAアプリ自身であること。
3. focused AutofillIdを含むformのnative webDomainが、このprobeで指定した実サイトdomainであること。

本物の資格情報を保持するproviderを変更したり、Googleの署名検証・許可リストを迂回したりしていない。
Digital Asset Links、Web origin、RP ID、UAのサイト別偽装も追加していない。

## 再実行

JDK 17 / Android SDKを設定し、専用QA端末を明示する。
live probeは一時的にシステムのAutofillServiceを合成サービスへ切り替え、`finally`で元へ戻す。
個人端末へ無断で実行するためのコマンドではない。

```sh
./gradlew :app:assemblePlayDebug :app:assemblePlayDebugAndroidTest -PnagiAutofillDiagnostics=true --no-parallel
adb -s SERIAL install -r app/build/outputs/apk/play/debug/app-play-debug.apk
adb -s SERIAL install -r app/build/outputs/apk/androidTest/play/debug/app-play-debug-androidTest.apk
adb -s SERIAL shell am instrument -w -r \
  -e class com.takeruf.nagi.LivePasswordAutofillTest \
  -e liveAutofill true -e site asahi -e mode manual -e advanceAsahi true \
  com.takeruf.nagi.autofillqa.test/androidx.test.runner.AndroidJUnitRunner
```

`site`は`asahi` / `github`、`mode`は`observe` / `automatic` / `manual` / `google`。
`advanceAsahi=true`は合成値を扱うモードでのみ実行され、架空IDの「続ける」だけに限定される。
`google`ではサービスを切り替えず、候補を選択せず、IDの次画面へ進まず、Googleの保存データを読まない。
Google用の試験資格情報を用意していない環境では、その結果で候補提供の可否を評価できない。

今回のGoogle対照では、自動要求の`UI_UNAVAILABLE`と、その後の
`MANUAL_REQUEST_CALLED` → `STRUCTURE_REQUESTED` → `FOCUSED_FIELD_OBSERVED`を両サイトで観測した。
12秒の観測中に`UI_SHOWN` / `FILL_DELIVERED`はなく、対象欄は空欄のままだった。
アプリ側のイベントからGoogle内部の応答数・信頼判定・vault一致結果は読めないため、
「署名拒否」「未承認」「該当資格情報なし」のいずれかに原因を確定することはできない。
Google対照のinstrumentation成功は、公開フォーム表示と要求操作が完了した意味に限定する。

安全な出力はアプリ内部の`files/autofill-live/<site>-<mode>/result.json`とcrop済みPNG。
QAアプリに限り、`adb exec-out run-as com.takeruf.nagi.autofillqa ...`でこのディレクトリだけを取得できる。
一般のAutofill dumpやWebViewのverbose logは収集していない。

## 証跡と未検証事項

証跡はignoredの`artifacts/autofill-live-20261115/`に保持する。

- 上表の合成サービス6条件は成功。最終の朝日2段階試験は、password画面への到達・候補選択・合成password一致を必須assertionにして再実行し、1件成功。
- Google対照2条件は公開フォームと要求操作のprobeとして完了。Google保存資格情報の提供成功とは数えない。
- 既存fixtureの`passwordProviderFillsWebsiteFieldsThroughComposeSurface`と`manualAutofillWithoutMatchesStillRequestsProviderAndReportsRequest`は2件成功（同じ更新済みdebug provider、既定のloopback動作）。
- `assemblePlayDebug` / `assemblePlayDebugAndroidTest`成功。最終`lintPlayDebug`は0 errors / 82 warnings。今回追加したスクリーンショット範囲チェックの警告を修正し、新規warningは残していない。
- `git diff --check`を実施。今回の変更はdebug provider、opt-in instrumentation、報告書であり、通常の本番有効化・リリースはしていない。

最終APKは同ディレクトリの`diagnostic/`、SHA-256は`apk-hashes.json`に保存。

| artifact | SHA-256 |
| --- | --- |
| `nagi-autofill-live-qa.apk` | `c9e852e6346feca1e94d21c841a774af2720144bcbf08aa9719cd1f05088fa27` |
| `nagi-autofill-live-qa-test.apk` | `b6e1c0d71e3b7e9f5a2a90dedf2ff4162deda722aaa78a453fa9a28ee7edad92` |

`source-hashes.json`で今回の2ソースが隔離ビルド側と作業ツリーで一致することを確認した。
並行作業を含む最新作業ツリー全体のビルド・フルスイート成功を意味しない。

初回のtest宣言はJUnitの戻り値がvoidでなく初期化エラーになったため修正した。
次の初回probeはテスト用Compose clockの描画待ち不足でWebViewを取得できず、描画待ちを追加した。
いずれも実サイトのAutofill失敗として数えていない。
朝日の「続ける」はtouchとaccessibility探索では遷移確認ができなかったため、
最終の2段階probeでは上記のDOM activationを用いた。途中のID欄だけの成功を、password入力成功として数えていない。

Googleの保存済みAsahi/GitHub資格情報がNagiへ提供されるか、元の実機の不表示理由、
Play署名での適格性、実アカウントのログイン成功、現行stable WebViewや他OSでの結果は未検証。
本番有効化は行わず、[元の調査報告](AUTOFILL_INVESTIGATION_2026-11-15.md)の残課題を維持する。
Bitwardenは、この実サイトでのOS経路検証には不要だったため導入していない。
