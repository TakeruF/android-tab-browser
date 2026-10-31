# Nagiのデスクトップ表示と認証の互換性調査

調査日: 2026-10-31。初回の認証実験は`e56a48f`と既存の未コミットUI変更を含む作業ツリーで実施。その後の正負対照で一時ポップアップの設定非継承を修正し、実機で端末本来の識別情報の有効性を確認して、任意に選べる「端末標準表示」を追加した。Cookie既定値・フィルター・既存のPC/モバイル方針と選択・既存UI変更は保持。並行の[フォント調査](JAPANESE_FONT_INVESTIGATION.md)とは分けて読む。

## 判断

**ChatGPTで報告された「チェック完了後に戻るループ」の原因は未確定。** 確認できた候補は、認証途中の表示設定変更と、デスクトップ表示で提示するブラウザー情報に対するサイト側の判定。広告ブロックやCookieの一般的な故障と断定できる証拠はない。

追加したさざんかねっとの実機比較では、モバイル表示でTurnstileトークンが発行され、デスクトップ3条件では観測時間内に発行されない差を確認した。デスクトップでの手動操作の失敗画像は600010。ここでは表示モードに連動する識別情報・サイト側処理の差が有力だが、Cloudflare内部の拒否理由や特定フィールドまで原因を確定したわけではない。

**修正版の通常機能を経由する実機確認でも、PC表示でのトークン未発行から「端末標準表示」への変更後に発行・完了通知まで進んだ。** 同一の隔離プロファイル・同じviewport・同じCookie/ブロック設定を保ち、画面点灯とロック解除状態を継続して確認した。端末標準のUAにMobileはなくCHもmobile=falseなので、スマートフォン指定だけが必要なのではない。既存PC表示の全サイト置換を避け、サイト単位の明示選択として実装した。実際のアカウントログイン完了、ログイン済みChatGPT、Googleの実アカウントでの改善は未検証。

追加の予測検証では、親と一時ポップアップのUA/Hintsが異なる実装上の不整合を再現し、設定継承を修正した。これは一般的なウィンドウ間の互換性修正であり、Cloudflareループが解決した証拠ではない。

その後、影響端末のOPPO OPD2409でも匿名ChatGPTを4構成で比較した。新規の診断用アプリ・隔離プロファイルではチャレンジが発生せず、ループは再現していない。通常版Nagiの既存セッションを検査・変更した結果ではない。日本語フォントの明朝混入は同じ実機で再現できており、認証とは別に調査している。

追加で確認された利用者の報告条件は、**ChatGPTではログイン済み・パソコン表示**。前述の匿名トップの測定はこの条件を満たしておらず、報告条件の正常性を保証しない。また、杉並区スポーツ施設予約「さざんかねっと」でもCloudflareがあり、ログイン画面へ移った時点で問題が発生するとの報告があった。こちらは公開の入口→ログイン画面を、認証情報を入力しない別ケースとして比較する。両サイトが同じ原因だとはまだ判断しない。

Cloudflareは、セッション中のUA変更、Cookie/DOM storage不足、必要な通信の遮断をWebViewでの失敗要因に挙げる。一方、WebViewには対応範囲の制約があり、エミュレーターの結果を実機へ一般化できない。[Mobile implementation](https://developers.cloudflare.com/turnstile/get-started/mobile-implementation/)、[Supported browsers](https://developers.cloudflare.com/cloudflare-challenges/reference/supported-browsers/)

## コードから確認できた差

| 項目 | モバイル表示 | デスクトップ表示 |
|---|---|---|
| UA文字列 | システムWebView UA。`Mobile`がなければ補う。`wv`・`Version/4.0`は維持 | `X11; Linux x86_64`のChrome形式。Chromeバージョンはインストール済みWebViewから取得 |
| UA Client Hints | 既定メタデータに`mobile=true`を設定 | `mobile=false`、`platform=Linux`、`architecture=x86`、`bitness=64`、modelとplatformVersionを空にする |
| ブランド情報 | WebView既定値 | WebView既定値を維持。Chromeそのものにはならない |
| Cookie | 第一者許可、第三者不許可 | 同じ |
| JavaScript / DOM storage | 有効 | 同じ |
| viewport設定 | `useWideViewPort=true`、`loadWithOverviewMode=true` | 同じ。サイトの返すHTML/CSSが違えば実際のレイアウトは変わる |
| 広告ブロック | 設定・サイト例外に従う | 同じ。UAによって要求されるリソースが異なれば影響は異なり得る |
| Safe Browsing / TLS | Safe Browsing有効、証明書エラーは拒否、mixed content不許可 | 同じ |
| WebAuthn | 対応プロバイダーでブラウザーモード | 同じ |

参照: [SiteUserAgent.kt](../app/src/main/java/com/takeruf/nagi/browser/engine/SiteUserAgent.kt)、[WebViewBrowserEngine.kt](../app/src/main/java/com/takeruf/nagi/browser/engine/WebViewBrowserEngine.kt)。メタデータAPIは`USER_AGENT_METADATA`対応時のみ使用する。非対応プロバイダーで同等のHintsになることは未検証。[Android公式API](https://developer.android.com/reference/androidx/webkit/WebSettingsCompat#setUserAgentMetadata(android.webkit.WebSettings,androidx.webkit.UserAgentMetadata))

## 認証途中で変わり得る条件

- `loadUrl`、reload、戻る/進むは対象ホストの保存設定を適用する。保存キーは完全なホスト単位。`chatgpt.com`、`auth.openai.com`、`accounts.google.com`は独立する。
- メインフレームのGETリンク/リダイレクトで対象ホストのモードが現在と異なると、元の遷移を中止し、次のUI処理でUAとHintsを変更して対象URLを読み直す。リクエストヘッダーはRefererだけを引き継ぎ、CookieはWebViewのCookie jarに任せる。
- POSTは本文・メソッド・現在のUAを保つ。後続のGETで対象ホストの設定へ切り替わる場合がある。iframeやサブリソースのホスト別表示設定は適用しない。
- 同じホストでも、明示設定がない場合は`defaultDesktopMode()`が毎回参照される。ウィンドウ幅600dp未満ではアプリの既定がモバイルになるため、認証中にサイズ境界を跨いだ後の遷移でモードが変わり得る。
- 手動切り替えはstopLoading後にUA/Hintsを変更してreloadする。認証途中の継続には適さない場合がある。
- 履歴スナップショット復元は取得時のモードを保ち、次のreload/遷移で最新のサイト設定・既定値を適用する。
- 初回の一時ポップアップは、親のUA/Hints設定を継承していなかった。追加検証後はUA文字列・UAメタデータ全体・第三者Cookie許可設定を親からコピーする。対象URLを新しいタブへ渡した後は、従来どおり遷移先ホストの表示設定を適用する。今回の同一タブループとの関連は未確認。

**同じホスト・同じ保存設定・同じウィンドウ既定値なら、リダイレクトのたびに無条件でUAを変更するコードではない。** 実際のCloudflare認証でどの条件が発生したかは、実機のサニタイズ済み時系列で確認が必要。

## Cookieと広告ブロックの確認範囲

通常タブは共有CookieManagerを使う。通常ページのonPageFinishedで`flush()`する。表示モード切り替え時にCookieを消すコードはない。サイトデータ削除は明示的なユーザー操作の別処理。プライベートタブは隔離プロファイルを使い、終了時に削除するため永続セッションとは区別する。

広告ブロックはBraveのルールエンジンで、EasyList・EasyPrivacy・AdGuard JapaneseとWebView用のCSS互換ルールを使う。メインフレーム要求は遮断しない。サブリソースはルールに該当すると空の200応答に置き換える。リストは通常4日間隔で更新されるため、同じAPKでもリスト状態で結果が変わり得る。CSSによる要素非表示・広告枠除去も別に存在する。バンドルEasyListにはTurnstileスクリプトの許可ルールと、`challenges.cloudflare.com`・`chatgpt.com`のgenerichide例外がある。ChatGPT向けの広告・計測遮断ルールも存在するため、実際の要求単位の判定は引き続き必要。

診断テストで許可されたURLパターンがあっても、実際のチャレンジの全URL、POST、iframe、動的DOMが正しいことの証明にはしない。`cf_clearance`はサイト側の第一者Cookieになる場合があり、第三者Cookieを常に必要と断定しない。Cookie jar内の存在と、対象要求での送信・サーバー受理も別の証拠である。

## 今回の実験

新規の専用AVD `Nagi_Auth_API36_20261031`、Android 16/API 36、Pixel Tablet、2560×1600px・320dpi。既存実機・ユーザーのAVDのデータは使っていない。最初にAPI 36.1の専用AVDを試したがADB接続が進まず、API 36に切り替えた。

比較はデスクトップ＋ブロック有効＋第三者Cookie不許可を基準に、(1)モバイル、(2)ブロック無効、(3)第三者Cookie許可をそれぞれ独立に変更。各構成は新しい隔離プロファイルから開始する。UAとHintsは表示モードという1設定に連動して変わるため、サイトの内部判定についてUAとHintsを個別に原因特定した実験ではない。

WebView 133.0.6943.137でのローカル結果:

- 3段階の同一ホスト302遷移で、UA・低エントロピーHintsは各構成内で一定。HttpOnlyの合成Cookieが後続リクエストとreloadで送信された。
- 異なるホストでも両方デスクトップならモードは維持。遷移先だけモバイルに保存すると、遷移先でUAとHintsがモバイルへ切り替わることを再現。
- モバイル→デスクトップ→モバイルで、元のAndroid UA/Hintsに戻ることを確認。
- 第三者Cookieは不許可で合成Cookieが送られず、許可で送られた。これは`localhost`と`127.0.0.1`のloopback実験であり、公衆HTTPSサイトやCloudflareのCookieの動作証明ではない。
- チャレンジAPI・iframe・同一サイトのchallenge-platformを模した代表URLは、ルール照合で許可された。
- 固定viewportのfixtureでは両モードとも`screen=1280×800 CSS px`、`innerWidth=1280`、DPR=2。表示モードが物理画面サイズを変更することはない。
- デスクトップではUA/Hintsがx86、`navigator.platform`は`Linux aarch64`のまま。Hintsのブランドも`Android WebView`を維持する。**情報の組み合わせの差は観測事実だが、拒否原因だという判断は仮説。** JavaScriptのplatformを書き換える修正の根拠にはしない。

今回のテストは新しい[AuthenticationCompatibilityTest.kt](../app/src/androidTest/java/com/takeruf/nagi/AuthenticationCompatibilityTest.kt)と、既存の`SiteDisplayModeEngineTest`、`GoogleSignInRecoveryEngineTest`。133で15件成功・ライブ診断1件は明示的にスキップ。GitHub単体テスト159件成功。通常のテストは実サイトのログインを開始しない。

Google署名の既存ローカルAPKを専用AVDだけにインストールし、選択中プロバイダーが156.0.8078.47であることを確認した。156でもローカル15件成功。4構成の公開ChatGPT診断も実行完了したが、これは観測処理の完了であり認証成功を表すものではない。Cookie送信、モード切り替え、画面サイズのローカル結果は133と同じ傾向だった。

156の最終ライブ診断（各25秒、新規隔離プロファイル）の結果:

| 構成 | 入力要素とChatGPTのUI文言 | 観測した既知ホストの要求数 | UA/Hintsの途中変化 | Cloudflare要求 | アプリの遮断数 |
|---|---|---:|---|---:|---:|
| デスクトップ基準 | 検出 | 320 | 観測なし | 0 | 0 |
| モバイルだけ変更 | 検出 | 313 | 観測なし | 0 | 0 |
| デスクトップ・ブロック無効 | 検出 | 320 | 観測なし | 0 | 0 |
| デスクトップ・第三者Cookie許可 | 検出 | 323 | 観測なし | 0 | 0 |

4構成ともDOMにtextarea/contenteditableと英語UI文言を検出し、ページ読み込みエラー・拒否文言・チャレンジ要素を検出しなかった。旧`#prompt-textarea`セレクターだけでは現在のUIを検出できなかったため、最終診断は入力要素とUI文言も併用した。個々の通信記録は先頭300件までだが、ホスト分類別の件数とUA/Hintsの組み合わせは観測全件を集計している。リスト3種のSHA-256も診断前後で一致した。

**この環境の匿名トップではチャレンジが発生しておらず、報告されたループを再現できていない。** 公開サイトで認証情報は入力しておらず、チェックボックスも操作していない。4構成とも`cf_clearance`はjar内に未検出だったが、チャレンジが発生していないのでCookie保存不良の証拠ではない。認証開始→チェック完了→遷移→Cookie受理の全区間、ログイン済みのChatGPT、実機・影響回線での挙動は未検証。実サイトの成功を単体テストの成功と同一視しない。

## さざんかねっとで確認できた別の再現経路

利用者から、杉並区スポーツ施設予約「さざんかねっと」のログイン画面への遷移でもCloudflareに失敗し、モバイル表示では改善するとの追加報告があった。[杉並区の公式案内](https://www.city.suginami.tokyo.jp/s027/642.html)が示す現在のPC/スマートフォン共通入口 `https://www.shisetsuyoyaku.city.suginami.tokyo.jp/user/Home` を使用した。旧スポーツ予約ホストと混同しない。

専用診断アプリのデスクトップ表示で利用者が人間確認を操作し、確認完了後にも繰り返すと報告した。添付画像では、ログインフォーム内のTurnstileが認証失敗となり、**600010**を表示していることを確認した。画像・Ray IDは成果物へ複製しない。これはパスワード送信前のクライアント側の人間確認の失敗であり、アカウントのログイン拒否とは区別する。

読み取り専用の画面状態採取ではTurnstile APIとresponse入力欄は存在し、トークン存在フラグはfalseだった。値は採取・保存しない。Shadow DOM等にあるwidgetは通常のlight DOMのiframe検索では見えないことがあり、従来のchallenge=falseだけではTurnstileが無いとは判定できなかった。

[Cloudflare公式のエラー分類](https://developers.cloudflare.com/turnstile/troubleshooting/client-side-errors/error-codes/)では600*は一般的なチャレンジ失敗で、番号から特定のUAフィールド・Cookie・フィルターが原因だとは決められない。401もPAT取得の通常処理に含まれる場合がある。今回はURLを保存せず、PAT経路を固定の分類名として分離して記録する。また、Turnstileのトークン発行と、任意のpre-clearance Cookie発行を区別する。cf_clearanceが無いことだけで失敗とは判定しない。

診断の初回に除外した試行:

- モバイルの2試行は公開ログイン操作を検出できず、Homeに留まった。ログイン画面での成功・失敗の比較には使わない。実際の公開操作は、入力欄のないGETフォーム内のtype=buttonだった。その条件のボタンだけを許可し、認証フォームの送信は引き続き禁止した。
- 最初の手動desktop診断は終了通知後も完了せず、調査側で診断アプリだけを停止した。ログのProcess crashedはその停止によるもので、Nagiが自然にクラッシュした証拠にはしない。背景に移っていたことは確認したが、応答しなかった内部原因は未確定。以後の比較は同じ時刻に診断画面を前面へ戻す条件をそろえた。

サイトのCloudflare originから来たmessageを読み取る観測処理は、6桁のエラーコードと既知の状態名だけを保存する。token/response/Ray ID/任意のイベント文字列は保存しない。ブラウザーAPIやTurnstile APIを書き換えず、チェックを自動操作しない。この内部イベント形式は保証された公開APIではないため、未検出を成功扱いせず、フォームのtoken存在フラグと到達状態も併記する。合成の正負対照で、コード採取、不正origin除外、token除外、不正コード除外、未知イベント名の秘匿、スナップショットの分離の6条件を確認した。

統一した4条件の比較結果は `artifacts/device-compat-20261031/sazanka/controlled-final/` に記録する。ケースごとに新規の隔離プロファイルを使い、公開ログイン操作の後25秒まで観測する。利用者番号・パスワードは入力しない。トークン発行はクライアント側の人間確認完了の証拠で、実際のログイン完了やサーバー検証成功の証拠ではない。

| 構成 | ログイン画面 | Turnstile token存在 | 完了イベント | Cloudflare要求遮断 | アプリ全体の遮断 |
|---|---|---|---:|---:|---:|
| モバイル・ブロック有・第三者Cookie不許可 | 到達 | true | 1 | 0 | 1 |
| デスクトップ基準 | 到達 | false | 0 | 0 | 1 |
| デスクトップ・ブロック無効 | 到達 | false | 0 | 0 | 0 |
| デスクトップ・第三者Cookie許可 | 到達 | false | 0 | 0 | 1 |

4件ともinstrumentationによる観測処理は正常終了した。**デスクトップのfalseは25秒時点の未発行を示し、各構成で人間のチェック操作後に600010となったことを自動確認した意味ではない。** 手動の繰り返し失敗と600010は別に利用者の操作・画像で確認した。後続のモバイルでの読み取り専用確認でも、token=trueとcomplete=1を確認した。

3リストのSHA-256は全ケースの前後・ケース間で同じだった。ブロックされた要求は記録上other分類で、Cloudflare本体・そのサブドメイン・対象サイトの観測要求は遮断されていない。ブロック無効でアプリ全体の遮断0となってもトークン未発行は変わらなかった。成功したモバイルも、失敗が疑われるデスクトップも、PAT分類の401を1件ずつ観測した。この対照でも401だけでは失敗を判定できないことを確認できた。

全4件でscreen=1293×915、inner=1292×914 CSS px、DPR=2.625、navigator.platform=Linux aarch64は同じ。CHのブランドはAndroid WebViewを保持し、mobile/Androidとdesktop/Linuxが切り替わる。観測できた対象要求のMobile有無とナビゲーションの表示モードは各ケース内で一定だった。CHが最初の要求に無いことを、その後の交渉で提示される値の変更と混同しない。完全なUA文字列の全要求比較、Service Worker、成功レスポンスヘッダーまで網羅した観測ではない。

これにより、この経路の差を単なる画面幅・広告ブロック・第三者Cookie不足だけで説明する根拠は弱くなった。UA/CHの組み合わせ、WebViewの機能・環境、UAに応じたサイト側の処理分岐はまだ分離できていない。特定のJavaScriptプロパティがCloudflareに拒否されたという証拠は無い。

### Chromeとの比較と仮説の修正

利用者が同じ端末のChromeのシークレット＋PC表示で、人間確認に成功したと報告した（入力不要で依頼）。その後、既知サービスホストに一致するChromeのタブが1件であることを確認し、navigatorと画面サイズだけを読み取った。ページ本文、入力欄、Cookie、トークンは読まない。シークレットでの実施と人間確認の成功は利用者報告であり、こちらがChromeでログイン完了やトークンを採取して確認した意味ではない。

| 実測した項目 | Nagiデスクトップ | 成功したと報告されたChrome PC表示 |
|---|---|---|
| UAのChromeバージョン | 156.0.8078.47 | 156.0.0.0 |
| CHブランド | Android WebView | Google Chrome |
| CH platform / architecture / bitness | Linux / x86 / 64 | 同じ |
| navigator.platform | Linux aarch64 | Linux armv81 |
| 実エンジンのfullVersionList | 156.0.8078.47 | 156.0.8078.79 |
| innerWidth / innerHeight | 1292 / 914 | 1292 / 735 |

**ChromeもLinux/x86の提示とARM系のnavigator.platformを併用していた。** したがって「ARM端末なのにx86を提示することだけが失敗原因」という説明は、この成功対照に合わない。ブランド、版、API・エンジンの差、UAに応じたサイト処理等を残して考える。Chromeのツールバー等により高さも異なり、ブラウザーだけを完璧に1変数とした比較ではない。`chrome-user-outcome.json`と`chrome-desktop-identity.json`を区別して参照する。

[ChromiumのUA reduction資料](https://www.chromium.org/updates/ua-reduction/)と照合し、バージョン表記だけをmajor.0.0.0へ変えた診断を追加した。WebViewブランド・UA-CH・navigator等は維持し、開始前にUAを設定してセッション中に切り替えない。広告ブロック無効・第三者Cookie不許可を固定した、元表記→標準表記→元表記のA/B/Aで、3回とも25秒時点のトークンは未発行だった。観測要求のUAハッシュは各回の最終UAと一致し、CHの全採取値も3回で一致した。**バージョン表記の正規化だけで自動発行に戻る、という予測は支持されなかった。** 人間のチェック操作後の結果を全3条件で測定したわけではないため、どんな状況でも影響しないとまで断定しない。これを通常版の修正には採用していない。

この対照の証拠は`version-control/`。Cloudflare iframe内でも状態の真偽値だけを読み取れたが、エラー/成功ラベルやコードは未検出だった。実画像での失敗を覆す証拠にはしない。内部DOMの見え方に制約があるため、ラベル未検出を認証成功やエラー無しとは扱わない。

### 端末本来のタブレット識別と追加した選択肢

さらに、同じNagiエンジン・同じ幅のまま、UA/UA-CHだけをWebViewプロバイダーの既定値に戻した対照を実行した。設定値を読むためだけの未読み込みWebViewから既定値を取得し、対象の隔離WebViewへナビゲーション前に設定した。ブランド・JavaScript APIの偽装やチャレンジ操作は行わない。

この実機の既定UAにはMobileが無く、UA-CHもmobile=false・platform=Android・ブランドAndroid WebViewだった。その条件でもログイン画面へ到達し、**token=true・complete=1**を観測した。観測要求のUAハッシュも最後のUAとすべて一致した（`native-identity/`）。したがって「mobile=falseであることだけが失敗原因」という予測にも合わない。PC向けに合成した識別情報の組み合わせと、実体に沿う識別情報との差が原因群として有力になった。ただし、この旧比較は画面の点灯状態を全ケースで記録していなかったため、後述の修正版の確認では点灯状態もそろえる。

この結果を受け、**サイトごとに選べる「端末標準表示」**を追加した。既存のPC表示・モバイル表示・自動既定値は変更しない。ページメニューから3つの表示を明示的に選べ、現在の選択をチェックと選択状態で示す。新しい表示だけはプロバイダー本来のUAとUA-CHをそのまま使い、タブレットを一律にスマートフォンやPC Chromeとして扱わない。viewport・JavaScript・Cookie既定値・ブロック方針は従来のまま。

操作はページの**⋯ →「端末標準表示を使用」**。中国語UIでは「使用设备默认视图」。この項目を追加したのはソースと別IDの診断版であり、端末の通常版Nagiを更新したわけではない。

選択は通常タブでは完全なホスト単位で保存し、プライベートでは既存設計どおりメモリーだけに保存する。PC/モバイルに戻すと新しいフラグを解除する。既存のBoolean設定を別の型へ変換せず、旧版でもBooleanとして読み取れる値を残す。通常のリンク・reload・別エンジン・戻る/進むで設定を適用し、履歴スナップショットは取得時の表示を保ち、次の独立した遷移やreloadで最新の保存設定を使う。

**影響:** サイトによってタブレット向けのHTMLや機能が選ばれ、PC専用機能が使えなくなる場合がある。PC表示そのものの全面的な置き換えにはしない。別ホストへ認証が移る場合は、そのホストの保存設定が別に適用される。表示を明示的に変更するとreloadするため、入力済みフォームがある場合は通常の表示変更と同じ影響がある。認証後にPC表示へ自動で戻す処理や、Cookieの移送・消去は追加しない。

**初回の回帰:** 実機29件の回帰では18件成功・opt-in 3件スキップまで進んだが、端末がAsleepかつkeyguard表示となって停止したため、調査側で診断アプリだけ終了した。この実行を全件成功や製品の自然なクラッシュとして扱わない。テストActivityに限ってKEEP_SCREEN_ONを設定し、利用者が通常の方法でロック解除してから再実行した。端末全体のタイムアウト等は変更しない。

**最終の検証:**

- 実機/API37: 認証・エンジン等26件成功、opt-in 2件スキップ。25件のローカル回帰と、後述の公開サイト観測1件を含む。全22回の点灯状態採取でAwake・ロック解除を維持した。
- 公開のさざんかねっと: 広告ブロック有効・第三者Cookie不許可の同じプロファイルで、PC表示の25秒時点でtoken=falseを採取。その後、診断用UA上書きではなく製品側の`setDeviceDefaultMode()`を呼び、reload後25秒の観測でtoken=true・complete=1、保存モードDEVICE_DEFAULTを確認した。viewport/DPR/navigator.platformは前後で同じ。観測UAハッシュは、明示的な切り替えの前後それぞれの2種類だけだった。
- UI/API37: Espressoの`InputManager.getInstance`反射呼び出しで1件失敗し、UIテスト本体の選択操作には到達しなかった。これをメニュー機能の失敗と同一視しない。同じUIテストを専用API36エミュレーターで実行し、端末標準→モバイル→PCの3選択と選択状態が1件成功した。API37でEspressoのUI試験が未成功である制約は残す。
- 単体162件成功。GitHub Debugアプリ/テストAPKのビルドと、Play Debug Kotlinコンパイルも成功。Android Lintはエラー0・警告58。新しい保存処理について既存と同じSharedPreferences編集形式へのKTX推奨が1件増えた。JavaScript観測処理と公開ログイン操作の正負対照も成功した。
- 実機の通常版APK全6部品、OS言語、font_scale、アプリ言語、WebView版、画面タイムアウト・充電時点灯設定は開始前と一致。診断アプリだけ停止し、専用エミュレーターも終了した。通常版へのインストール・配布はしていない。

証拠は`sazanka/device-default-fix/`の`final-verification.json`、`live-device-default-recovery.json`、`awake-run-power-samples.json`、各instrumentation結果、`environment-final.json`。`turnstileTokenPresent`は真偽値であり、トークン値を含まない。Cloudflareの内部評価項目は依然不明で、特定ブランド/プロパティの除去を正当化する結果ではない。実際のログイン完了は利用者による別確認が必要。

安全な当面の対応は、既存のサイト別設定でモバイル表示を選び、認証の入口から開始すること。サイト全体のレイアウトが変わる影響がある。デスクトップ識別の一般設計を変更する場合は、実体のWebView情報を保つこと、標準のUA/CHとの整合、Google/Docs等のデスクトップ機能への影響を先に検証する。ブランド除去、navigatorの偽装、チャレンジ操作の自動化、Cookieの移送を修正案にはしない。今回の証拠だけで通常ページのUA方針やCookie既定値を変更する実装には進まない。

通常版APK全6部品のハッシュは再度不変を確認し、診断アプリだけ停止した。追加の診断・結果は`sazanka/`配下。初期の到達しなかったモバイル試行と、手動診断を調査側で停止したログも区別して残す。

## Turnstile失敗時の案内バー（追加対応）

PC表示でTurnstileの失敗通知を受けた場合、既存のGoogleサインイン案内と同じ位置・スタイルで「人間確認が進まない場合は、端末標準表示で再試行できます」と案内する。「端末標準で再読込」を押した場合だけ、既存の`setDeviceDefaultMode()`を呼び出し、そのホストの設定を保存して再読み込みする。表示しただけでUA・Client Hints・Cookie設定・広告ブロックを変更しない。Googleの拒否画面では既存のGoogle用案内を優先する。

`page-human-verification.js`はトップ文書の通常のmessageイベントを観測する。送信元は厳密に`https://challenges.cloudflare.com`、通知のsourceは`cloudflare-challenge`に限定し、合成イベントを除外する。読むのはsourceとeventの種類だけで、認証トークン、Cookie、入力欄、ページ本文は読まない。通知の中継や認証APIの置換も行わない。ネイティブ側へ返すのは失敗状態の真偽値だけで、WebViewへアプリの操作権限を持つJavaScriptブリッジを追加しない。

完了・再試行開始通知、通常の画面遷移、同じURLの再読み込み、SPAのURL変更で古い案内を解除する。「閉じる」は同じ文書・経路内で保持する。モバイル表示、端末標準表示、Reader、通信エラー、非対応エンジンでは表示しない。真偽値の確認は表示中のPC表示ペインだけで毎秒行い、非表示時・破棄時は停止する。document-start script非対応の古いWebViewは読み込み完了後の通知だけが対象になる。

**実サイトでの追加観測:** 隔離診断版・匿名のさざんかねっとログイン画面で、PC表示時に600010／fail通知、トークン未発行、案内フラグtrueを確認した。明示的な端末標準表示の操作後はcomplete通知、トークン発行、案内フラグfalseになった。利用者ID・パスワードは入力しておらず、施設予約サービスへのログイン完了を示すものではない。記録は`artifacts/device-compat-20261031/recovery-bar-live-sazanka.json`。

**範囲の限界:** CAPTCHAの存在だけで案内を出さない。今回確認したTurnstileの通知を対象とし、reCAPTCHA、通知のない停止・ループ、すべてのCloudflare Challenge Pagesを検出するものではない。通知形式は公開された汎用ブラウザAPIではないため、提供元の変更によって検出できなくなる可能性がある。失敗の理由を特定したり、確認を自動完了したりする機能ではない。

テストには、提供元と同じoriginを持つ子フレームからの通知を端末内インターセプターで返す合成ケースを追加した。公開CAPTCHAへ通信したり、解答したりするテストではない。偽の合成イベント、類似ホスト、異なるsource、未知の通知の無視と、失敗・完了・再試行通知、同じURLのreload、SPA経路変更、非表示ペイン、明示的なサイト設定の保存を比較する。SPAテストの初回は、テスト用WebViewClientが`doUpdateVisitedHistory`を本体へ中継しておらずタイムアウトした。これを修正して再検証し、初回の失敗記録も残す。UIはAPI37実機の既知のEspresso制約を避け、API36エミュレーターで検証する。

最終結果は、修正後の実機合成テスト6件、API36のUI回帰7件（新しい案内3件＋既存表示メニュー1件＋Google案内3件）、単体162件がすべて成功。Play DebugのKotlinコンパイル成功、Lintはエラー0・警告58件（既存分を含む）。さざんかねっとの再実行では25秒の観測時間内にfail通知が出ず、案内フラグもfalseのままだった。端末標準表示への操作後は再びトークン発行・completeを確認した。失敗通知が出た初回と、出なかった再実行を別記録にし、後者を案内の検出成功とは扱わない。

集約記録は`artifacts/device-compat-20261031/recovery-bar-verification.json`。診断用アプリを停止し、この作業用のエミュレーターも停止した。通常版Nagiの6つのAPK構成要素のハッシュが作業前と一致することを再確認した。ソースと隔離診断版までの対応であり、通常版の更新・公開、ログイン済みChatGPT、アカウントログイン完了は未実施。

## Googleの既存記録との関係

2026-10-27のローカル資料`artifacts/google-signin/assessment.md`・`test-results.md`を再確認した。実際のNagiエンジンで、識別子を入力した後にモバイルはパスワード画面、デスクトップは`/signin/rejected`へ進んだとの記録がある。133と158の公開Chromiumスナップショットで同傾向。158デスクトップでは第三者Cookie許可でも拒否された。158のスナップショットを正式配信Google WebViewと同一視しない。今回は識別子入力を再実行していない。

共通点は表示モードに連動するUA/Hintsと、WebViewのサイト互換性。相違点は、Googleは識別子送信後の明確な拒否画面、今回のChatGPT報告はCloudflareチェック完了後のループであること。Googleの過去結果だけでCloudflareの原因を断定できない。

既存の1.1.0修正（`6b5d0de`）はGoogleの拒否ページで利用者にモバイル再試行を提示し、Googleホストだけに選択を保存する。失敗したURLのreloadではなくServiceLoginからやり直す。通常のサイトに自動適用はしない。UA/Hintsのモード連動自体は1.0.0の`50d5776`に存在する。

Google Docs/Gmail等の通常サービスログインと、第三者サービスのGoogle OAuth認可は区別する。[Googleアカウントの案内](https://support.google.com/accounts/answer/7675428?hl=en)は組み込みブラウザーでログインできない場合を説明する。[OAuthポリシー](https://developers.google.com/identity/protocols/oauth2/policies#browsers)は開発者が制御する埋め込みユーザーエージェントへのOAuth認可要求を禁止しており、UA変更で解決すべき問題ではない。外部ブラウザー/Custom Tabsへ送る場合も、そのCookieがNagiのWebViewへ共有されるわけではない。[Custom Tabs公式](https://developer.chrome.com/docs/android/custom-tabs)

パスワード画面到達は認証完了・Docs/Gmail到達・再起動後のセッション保持の証明ではない。WebAuthnブラウザーモードの有効化もGoogleのOAuth制限を解除するものではない。

## 原因に応じた修正候補

### 追加した予測検証と確定した修正

公開サイトのチャレンジが出なかったことだけでは仮説を判定できないため、実際のWebView・端末内HTTPサーバー・隔離Cookieプロファイルで条件を制御した。以下は本番Cloudflare/Googleの判定を模倣したテストではない。

| 検証する予測 | 操作と対照 | 実測・判定 |
|---|---|---|
| UAとHintsを個別の変数として切り分けられる | UAモバイル/デスクトップ×Hintsモバイル/デスクトップの4条件。混在設定はloopback限定 | サーバー受信UA/HintsとJSの値が各指定と一致し、全条件で合成Cookieを送信。個別操作できることを確認したが、サイト側の受け入れ条件を証明するものではない |
| POSTリダイレクトで本文を失う、または同じ要求を重複送信する | 302/303/307/308×GET/POST×同/別ホスト×サイト設定同/異の32条件 | 302/303のPOSTはGETへ、307/308はPOSTと合成本文を保持。各入口/到達先は1回ずつ。ホストをまたいで元サイトのCookieが送られることもなかった |
| 保存済みモバイル設定ならPOSTでも直ちにモバイルUAになる | 遷移先ホストだけモバイルに保存した対照を含める | GETは保存設定に切り替わるが、307/308のPOSTは元のデスクトップUA/Hintsと本文を保持。後続GETで切り替わり得るという分析を支持する |
| 親とポップアップでブラウザー情報が一致する | 実際のタップ→window.openで一時WebViewの設定を採取 | 修正前はデスクトップ親とのUA一致=false、Hints platform一致=false。設定非継承を確認し、UA・メタデータ全体・Cookie許可設定を継承するよう修正。修正後はモバイル/デスクトップ×第三者Cookie許可/不許可の4条件で一致 |
| Cookie未送信なら保存不良である | 同じ保存済みの明示SameSite=Lax合成Cookieを、別ホストへのGET/POSTで比較 | GETでは送信され、307/308で保持された別ホストPOSTでは未送信。同じjar内の保存と特定要求の送信は別の判定である |

HTTPの比較は[RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html#name-redirection-3xx)のリダイレクト仕様と照合した。307/308でPOSTが保持されることは正常な挙動で、本文を失わないためにPOSTでUAを変更しない処理を維持している。

追加HTTPテストの初回は、WebViewのURL・タイトル・isLoadingだけで完了を判定したため、到達先のサーバー記録0で失敗した。テストはfixtureの到達記録も待つよう修正し、32条件を検証した。初回のこの失敗を、認証要求や本文を失う本体不具合と分類してはいない。UI状態・ネットワーク到達・認証受理を別の証拠として扱う。

ポップアップの修正範囲はURL捕捉用の一時WebViewの設定だけ。UAの新しい偽装方式、サイト設定の無視、Cookieの全面許可、TLS例外を導入していない。一時ウィンドウは引き続き対象URLを実際のタブへ渡す仕組みである。実サイトの認証通信が修正前の一時ウィンドウから異なるUAで送られたことまでは確認していない。

追加証拠は`artifacts/auth-prediction-20261031/`。修正前の失敗と修正後の成功を保存し、今回の条件別結果は`prediction-results/`と`verification-summary.json`を参照する。単体159件成功、認証関連instrumentationは18件成功・ライブ1件スキップ、メタデータ全体を追加確認したpopupテストも4条件で成功。Android Lintはエラー0・警告57。通常のライブ診断は今回再実行せず、正負対照と既存回帰を優先した。133での初回結果と、156で実施した今回の修正検証は区別する。

### 設定値から実通信・プロセス再起動までの追加検証

WebView 156の専用AVDで、さらに以下を確認した。単なる設定値一致やPageStateの更新だけを、通信・永続化の成功と取り違えないためのテストである。

| 条件 | 実測 | 判断の範囲 |
|---|---|---|
| ポップアップのURL捕捉→EngineEvent→新しい実エンジンへの引き渡し。親/遷移先のdesktop/mobileを4通りにする | 一時ポップアップから遷移先への要求は0、新しいタブからは1。保存済みの遷移先UA/Hintsでアクセスし、共有プロファイルの対象ホストCookieを送信。親の表示モードは維持 | 通常のGETポップアップをエンジン間で受け渡すローカル統合テスト。本番認証、POSTポップアップ、UI全体のテストではない。前回修正した初期設定の不一致が、このGET経路で別UAの認証通信を発生させた証拠は得られていない |
| HTTP302応答をサーバーで待たせ、その間に自動既定値だけを変更。サイトの明示設定有/無を対照にする | 明示設定なし＋既定変更だけがdesktop→mobileへ変化。明示desktop設定がある場合は変化せず、全4条件で合成Cookieは送信 | コールバックの戻り値を制御した実験で、実際のウィンドウ幅変更ではない。自動設定変更がUAを変え得る機構は確認したが、影響実機で発生していたとは断定しない |
| モード変更を伴うGET遷移をキューへ入れ、同じUI処理内で別URLへ移動、またはエンジンを破棄 | 旧URLへの要求0、別URL選択時は新URLのみ1回。破棄後も旧遷移を再開しない | 古い非同期遷移が後からUAやページを変更しないことを2条件で確認 |
| 合成Cookie/localStorageを専用の永続プロファイルへ保存し、別プロセスでdesktop/mobile検証→専用プロファイル削除 | 保存・desktop再検証・mobile再検証・削除の4段階が成功し、記録されたプロセスIDも4つとも異なる。両検証でCookieをサーバーへ送信し、localStorageも保持。HttpOnly CookieはJSから非公開 | 同じWebViewプロファイルAPIとflushの動作確認。通常利用者のCookie jarやCloudflare Cookieは採取しない。通常タブの全セッションや本番サーバー受理を保証するものではない |

永続化実験のプロファイル名はテスト専用の`nagi-auth-persistence-fixture`で、利用者の通常プロファイルと、終了時消去する`nagi-private-*`の両方から分離している。Cookieは明示的なMax-Age付きの合成HttpOnly Cookie。保存時は通常エンジンのonPageFinishedと同じCookieManager.flushを明示実行した。各段階はアプリをforce-stopしてからinstrumentationを開始し、最後にこのテスト専用プロファイルだけを削除した。プライベートブラウズが再起動後も残ることを求めたテストではない。HTTP loopbackの結果であり、本番TLS・Cookie属性・WebView更新前後・実機の動作は別に確認が必要。

完了待ちも強化した。fixtureのURL/isLoading/タイトルに加え、実際に読み込まれたDOMのパスマーカーとreadyStateを照合する。reload後のCookie送信は、前の要求を見て成功にしないよう、サーバー到達回数が増えたことを待って確認する。

証拠は`artifacts/auth-handoff-20261031/`。通常の認証関連回帰は21件成功・ライブ/複数プロセス診断2件は明示opt-in待ちでスキップ。複数プロセス診断は別途4段階を実行して成功。今回の追加は診断・回帰テストで、本体の追加変更はない。

永続化を再実行する場合は、専用環境で各段階の成功を確認してから次へ進める:

```sh
# PHASEを seed / verify-desktop / verify-mobile / cleanup の順で指定する。
# 段階ごとのforce-stopと、最後のcleanupを省略しない。
adb -s emulator-5554 shell am force-stop com.takeruf.nagi
adb -s emulator-5554 shell am instrument -w -r \
  -e class com.takeruf.nagi.AuthenticationCompatibilityTest#persistentSyntheticStorageAcrossSeparateProcesses \
  -e authPersistencePhase PHASE \
  com.takeruf.nagi.test/androidx.test.runner.AndroidJUnitRunner
```

通常のテストではauthPersistencePhaseを渡さず、冒頭でスキップする。保存用ポート・WebView版のメタデータ以外にCookie値やトークンをファイルへ渡さない。

### 本番の原因特定後に検討する変更

| 原因が実機で確認された場合 | 修正案 | 影響 | 必要な検証 |
|---|---|---|---|
| 認証のリダイレクト中にUA/Hintsが変わる | 1つのナビゲーション連鎖では表示モードを維持し、明示操作/次の独立ナビゲーションでサイト設定を適用する設計を検討 | 保存済みサイト別表示、戻る/進む、POST→GET、サイズ変更、ポップアップの意味が変わる | 302/303/307/308、フォーム送信、JS遷移、戻る/進む、キャンセル競合、実機認証を回帰確認 |
| 特定の必須リソースを遮断 | 実際に遮断されたルールとURL分類を確認して狭い例外を追加 | 例外の範囲だけブロック効果が低下 | 必須通信とDOMの復旧、無関係広告が引き続き遮断されることを確認 |
| 第三者Cookie許可だけで改善 | 利用者が選べるサイト限定許可を検討。既定値は維持 | プライバシー・設定UI・サイト判定・全タブへの適用範囲を設計する必要 | 同一UA/回線/リストで許可だけ変更し、認証完了・再起動・ログアウト・隔離を確認 |
| デスクトップ情報の組み合わせだけで拒否 | まずモバイル表示で認証の入口からやり直す。一般的なdesktop UA設計は比較調査後に見直す | ログイン画面のレイアウト。全体のUA変更は他サイトにも影響 | 同一実機Chromeとの比較、UA/Hintsと画面要因の分離、完了後のサービス遷移を確認 |
| WebViewで許可されないOAuth | 対応する外部ブラウザーで認可。NagiへのCookie移送はしない | サイトのログインが外部ブラウザー側で完結する場合がある | 正式なOAuthリダイレクト/対応ブラウザーで完了することを確認 |

既存PC/モバイルのUA方針・Cookie既定値・フィルターは変更していない。一時ポップアップの非継承を修正し、後続の実機検証に基づいて、実体の識別情報を保つ「端末標準表示」を任意に選べるよう追加した。UAブランド除去やJavaScript API偽装、TLS/Safe Browsingの緩和は行っていない。

## 影響実機での追加比較

OPPO OPD2409、Android 17/API 37、Google System WebView 156.0.8078.47、システム言語zh-Hans-CN、2400×3392px・420dpi。通常版NagiはPlay版1.1.0/code 9。利用者の許可を得て、別のapplicationId `com.takeruf.nagi.compatprobe`（表示名Nagi Compatibility Probe）に診断版とテスト部品をインストールした。通常版を置き換えず、既存Cookie・ログイン情報・サイト設定をコピーしていない。WebView、OS言語・フォント・Cookie設定も変更していない。

公開ChatGPTを各25秒、ケースごとの新規隔離プロファイルで読み込んだ。デスクトップ基準（ブロック有効・第三者Cookie不許可）から、一度に1設定だけ変更した。

| 構成 | textarea | 既知ホスト要求数 | UA/Hints途中変化 | Cloudflare要求/遮断数/HTTPエラー |
|---|---|---:|---|---|
| デスクトップ基準 | 検出 | 320 | 観測なし | 0 / 0 / 0 |
| モバイルのみ | 検出 | 313 | 観測なし | 0 / 0 / 0 |
| デスクトップ・ブロック無効 | 検出 | 320 | 観測なし | 0 / 0 / 0 |
| デスクトップ・第三者Cookie許可 | 検出 | 320 | 観測なし | 0 / 0 / 0 |

全構成で読み込み終了、エンジンエラーなし、チャレンジ/拒否のDOMフラグなし。英語だけを照合する`chatUiText`はfalseだが、中国語環境でこの値だけをUI欠落・認証失敗と判定しない。textareaと他の独立したフラグを併記する。全構成でclearance Cookieは未検出だが、チャレンジ自体が出ていないため保存失敗を意味しない。チェック操作、認証後のCookie送信・受理、ログイン完了は未検証である。

この横向き測定では4構成とも`screen=1293×915`、`inner=1292×914 CSS px`、DPR=2.625、`navigator.platform=Linux aarch64`だった。desktopのHintsはLinux/x86/64、mobileはAndroid/architecture空/bitness空/platformVersion 17.0.0。双方ともブランドにAndroid WebViewを保持する。表示モードによって実画面サイズは変わらず、情報の組み合わせの違いがこの匿名表示を拒否させた結果も得られていない。

同じ実機で通常の認証回帰も21件成功・ライブ/複数プロセスのopt-in 2件スキップ（`local-auth-regression-instrumentation.txt`）。ポップアップ継承、実タブへの引き渡し、32種類のHTTPリダイレクト、Cookieスコープ、第三者Cookie設定、表示モード変更とキャンセルを含む。公開ライブ診断は上表のとおり別途実行済みである。

### 実機のlocalStorageと終了タイミングの対照

複数プロセス診断を実機へ移した初回は、seed中のlocalStorage=true、再起動後=falseで失敗した。Cookieは再起動後もjar内にあり、fixtureへの送信もtrueだった。この失敗を隠さず、以下の終了タイミング対照を追加した。すべて同じ診断版・プロバイダー・専用の永続プロファイルで、各試行でseedし直し、最後にその専用プロファイルだけ削除した。

| seed時の条件 | 別プロセスdesktop/mobileでの結果 | 検証の範囲 |
|---|---|---|
| JS書き込み確認後、待たずにinstrumentationを終了 | 反復してlocalStorage=false、Cookie送信=true | 即時終了の診断はこの実機でLS永続化を保証しない。エミュレーターの成功を実機へ一般化できなかった |
| seedのinstrumentation終了後だけ5秒待つ | 両モードlocalStorage=false、Cookie送信=true | プロセス終了後に待つだけでは改善しない |
| JS書き込み後、seedのプロセスを6秒間生かしてから終了 | 2回の独立したseedで、両モードlocalStorage=true、Cookie送信=true。各4段階のPIDも異なる | 終了前に時間を与えると永続化することを確認。内部のcommit時刻や実サイトの保存状態は計測していない |

非同期保存が終了タイミングに左右されるという説明を支持するが、通常ブラウズ中のCloudflareループや、通常版のセッション喪失の原因が確定したわけではない。診断runner自身がプロセスを終了する点にも注意する。CookieManager.flushはCookieのためのAPIで、localStorageのflush成功を意味しない。本体へ待ち時間や非公開APIは追加していない。

テストの追加引数`authPersistenceSeedDwellMs`（0〜6000、既定0）はseed時だけ適用する診断用対照。公開ページや通常操作には使わない。実機で再実行する場合は4段階に`-e authPersistenceSeedDwellMs 6000`も渡し、値を含めて結果を記録する。終了直後の負対照と、6秒待機で成功した対照を混同しない。証拠は`persistence/`、`persistence-controls/`（いずれも`artifacts/device-compat-20261031/`配下）。

**同じ端末でも別アプリ・新規プロファイルの匿名トップの結果である。** 通常版の既存セッション、アプリ固有のOEM挙動、ログイン後や特定の認証入口に依存する問題は除外できない。通常版のアプリデータを消して比較する実験は行っていない。証拠は`artifacts/device-compat-20261031/auth/`と`live-auth-instrumentation.txt`。シリアル、認証URLのクエリー、Cookie値、トークン、画面ダンプは保存しない。

実機診断の終了時に、通常版のbase/split APK全6部品のSHA-256が開始前と一致することを確認した。通常版のアプリ言語は未指定のまま、診断版のアプリ言語も未指定へ復元済み。OS言語・font_scale・WebView版も開始前と一致し、診断版だけを停止した。証拠は`environment-before.json`・`environment-after.json`。通常版の停止・置換・データ削除は行っていない。

最終ソースの診断APK/テストAPKビルドとAndroid Lintは成功（エラー0・警告57）。フォント診断のPython35件・JS構文・git diff --checkも成功。実機と静的チェックの概要は`artifacts/device-compat-20261031/verification-summary.json`。これらを実サイトの認証完了や通常版への配布完了とは扱わない。

## 実機で残る確認手順

1. 影響端末でAndroid・WebViewのバージョン、回線/VPNの有無、ウィンドウサイズ、通常/プライベート、関連ホストの表示設定を記録。同じ回線・端末のChromeとも比較する。
2. 一定のウィンドウサイズと関連ホストの同じモードで、認証の入口から開始。途中で表示を切り替えず、利用者がチェック操作を行う。デスクトップ基準→モバイルだけ変更→デスクトップでブロックだけ無効→デスクトップで第三者Cookieだけ許可を、新規の隔離プロファイルで比較する。通常UIに第三者Cookie許可設定はないため、この比較は専用診断ビルドで行う。
3. 各ケースで「確認画面表示」「チェック操作」「遷移開始」「ChatGPT表示」「reload」「再起動」を別に判定。ログインが必要なら利用者が画面へ直接入力する。Google通常ログインとOAuthは別ケースにする。
4. 記録するのは相対時刻、既知ホスト分類、リダイレクト/メソッド、HTTP状態、UA/Hintsの変化の有無、ブロックの有無、対象Cookieの保存/送信の真偽値だけ。HttpOnly CookieはJavaScriptから見えなくても正常。`Cookie`/`Set-Cookie`本文、完全な認証URL、クエリー、フォーム値、本文、HAR、ログイン後の画面ダンプを保存しない。
5. Cookie保存だけでなく、認証後の要求で送られるか、サーバーに受理されて目的ページへ進むか、通常タブでアプリ再起動後も保持されるかを確認する。再起動確認にはプライベートプロファイルを使わない。

ローカル診断はCookieを受信したfixture側で合成Cookieの有無を比較する。ライブ診断の`shouldInterceptRequest`はすべてのリダイレクト後要求やService Worker通信を網羅せず、成功したレスポンスヘッダーも取得しない。その観測だけでCookie送信やCloudflareの受理成功は断定できない。`shouldOverrideUrlLoading`もPOSTでは呼ばれない。[Android公式のコールバック仕様](https://developer.android.com/reference/android/webkit/WebViewClient)

## 再実行と成果物

```sh
./gradlew :app:testGithubDebugUnitTest :app:assembleGithubDebugAndroidTest --no-parallel
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedGithubDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.takeruf.nagi.AuthenticationCompatibilityTest,com.takeruf.nagi.SiteDisplayModeEngineTest,com.takeruf.nagi.GoogleSignInRecoveryEngineTest \
  --no-parallel
```

公開ChatGPTの診断は`liveAuth=true`を渡した場合のみ動く。25秒ずつ4構成を読み込み、ログイン・チェック操作・送信は自動化しない。開始から認証完了までを保証するテストではない。通常のCookieプロファイルは使わない。

```sh
adb -s emulator-5554 shell am instrument -w -r \
  -e class com.takeruf.nagi.AuthenticationCompatibilityTest \
  -e liveAuth true com.takeruf.nagi.test/androidx.test.runner.AndroidJUnitRunner
```

上記serialは今回の専用AVDのもの。再実行時は対象を確認して指定する。JSONはアプリのexternal files内`auth-compatibility/`に出力。Gradleのconnectedテストは終了時にテストアプリをアンインストールするため、JSON保持が必要なら専用エミュレーターにAPK/テストAPKを直接インストールして`am instrument`で実行し、終了後にそのディレクトリだけをpullする。

この調査のローカル証拠はGit無視対象の`artifacts/desktop-auth-20261031/`に保存する。本書は認証情報やCookie値を含まない。
