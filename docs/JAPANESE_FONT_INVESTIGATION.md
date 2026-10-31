# 日本語フォントの互換性調査

調査日: 2026-10-31。対象はNagiの通常ページで、日本語が明朝のように表示されるという報告。**OPPO実機の別ID診断版で、sans-serif指定でも日本語のかな8字がNoto Serif CJK SCへフォールバックする現象を再現した。ページ言語jaでは全13字がSansとなる。** エミュレーターでは未再現だったため、以下で環境と検証時期を区別する。通常Nagiの設定・データ、OSフォント、サイトCSSは変更していない。Google Docsのログイン済み編集面は未検証。

## OPPO実機での再現と切り分け

OPPO OPD2409 / Android 17 API 37 / zh-Hans-CN / font_scale=1.0 / Google WebView 156.0.8078.47。既存Nagiを保護するため、別applicationId `com.takeruf.nagi.compatprobe`と隔離Cookieプロファイルで実行した。通常NagiやChromeの既存プロファイルは測定していない。CSSだけをコピーした固定13字「日本語漢字ひらがなカタカナ」の描画であり、利用者の入力・本文・認証情報を読んだ結果ではない。

| 実機の条件 | 実フォント | 通信と判定 |
|---|---|---|
| localhost fixture、lang=ja、desktop/mobile | sans/system-ui/Arial/Roboto/monospace/missing+sansは全13字Noto Sans CJK JP。明示serifのみ全13字Noto Serif CJK JP | 正負対照を識別できる |
| ChatGPT匿名desktop/mobile/desktopブロック無効 | body/button/editorの各コピーで、lang=zh-CN、漢字5字OPPO Sans 4.0 SC＋かな8字Noto Serif CJK SC | Font通信0、loadingFailed0。desktop限定・当該font取得失敗・ブロック有効時限定という説明に合わない |
| Docs匿名入口から転送されたGoogleアカウント画面、desktop | lang=zh-CN、同じSans5字＋Serif8字 | WOFF2 1件HTTP200、loadingFailed0。Docs文書本文/Canvasの検証ではない |

localhostで**DOM言語だけ**を変えると、sans-serif・ui-sans-serif・system-ui・Arial・Roboto・存在しないfont+sans・公開ChatGPT CSSスタックのいずれも同じ変化となった。

| DOM言語 | 非serifスタックの13字 | 明示serif対照 |
|---|---|---|
| ja | 全13字Noto Sans CJK JP | 全13字Noto Serif CJK JP |
| zh-CN | 漢字5字OPPO Sans 4.0 SC＋かな8字Noto Serif CJK SC | 全13字Noto Serif CJK SC |
| en-US | 同上 | 全13字Noto Serif CJK SC |
| 不明（lang空文字） | 同上 | 全13字Noto Serif CJK SC |

漢字5字とかな8字を別々の合成ノードでも測り、この分裂を確認した。未知言語のノードは空langで、fixtureのja継承を防いでいる。56行＋元fixture7の初回行列に続き、フォント名対照を追加した72行＋元fixture7も採取した。`complete`は採取完了で、Sans期待値への適合を意味しない。

**フォントデータの欠落とフォールバックの選択は別。** CSSへ`"Noto Sans CJK JP/SC"`やPostScript名を直接指定しても、ja以外の混在は変わらなかった。`FontFace`の`local("Noto Sans CJK JP/SC")`はerror。一方、`local("NotoSansCJKJP-Regular")`・`local("NotoSansCJKsc-Regular")`はloadedとなり、4言語とも全13字が各Noto Sansの実glyphで描画された（CDPのisCustomFontはfalse）。したがって必要なSans字体データ自体は存在し、このローカル参照方法では利用できる。family名でのlocal失敗を「端末にフォントが無い」と解釈しない。

[CDP Page.setFontFamilies](https://chromedevtools.github.io/devtools-protocol/tot/Page/#method-setFontFamilies)の実験的APIでsansSerifだけを`sans-serif`、`Noto Sans CJK JP`、`NotoSansCJKJP-Regular`へそれぞれ変更したlocalhost対照も、混在のままだった。明示serifやDOM言語は変更していない。**CDPの指定がAndroid WebViewでどこまで実効だったかは未確認で、ネイティブWebSettingsの効果と同一視しない。** この結果だけで本体の既定family設定を変更する根拠は得られなかった。

さらに、素WebView内でAndroidの`WebSettings.setSansSerifFontFamily`を使って同じ3候補を別々に設定した。各回のgetterが指定値と一致することを確認したが、3回とも79ノードすべてのPostScript名/glyphCountが素WebView既定値のbaselineと完全一致し、かなのSerif混入は改善しなかった。明示serif・4言語の対照も維持した。証拠は`native-sans/`、`native-jp-family/`、`native-jp-postscript/`と`native-settings-comparison.json`。この3候補では、CDPの実効性だけに依存せず**ネイティブ設定APIでも改善しない**ことを確認した。standard等の全設定を試したという意味ではない。

診断版だけのAndroid per-app languageを`[]`→`[ja]`→`[]`と変更し、DOM言語/familyは同じ79ノードで比較した。アプリ日本語時はDOMがen-US/不明の行だけ全13字Noto Sans CJK JPへ変わり、DOMがzh-CNの行は混在のまま。ja行は元からSans、明示serifは各言語のSerifを維持した。元の`[]`へ復元したことを確認済。これは**利用者がアプリ言語を日本語にする既存設定の限定的な緩和可能性**を示すが、公開実測のzh-CNページには単独で不十分。アプリのUI・Accept-Language等も変わり得るため、実装で自動強制しない。この実験でAccept-Languageの実リクエスト差までは採取していない。

Android標準描画との対照も追加した。[NativeFontFallbackTest](../app/src/androidTest/java/com/takeruf/nagi/NativeFontFallbackTest.kt)は[TextRunShaper](https://developer.android.com/reference/android/graphics/text/TextRunShaper)と[PositionedGlyphs.getFont](https://developer.android.com/reference/android/graphics/text/PositionedGlyphs#getFont(int))で、同じ固定文字の字体ファイルbasename・TTC index・glyph数だけを記録する。4言語×sans/serif/monospace/JPファイル直接指定×全文/漢字/かなの48条件が成功し、欠落glyphなし。native Paintではdefault/zh-CN/en-USのsans・serif・monoとも、漢字5字がSysSans-Hans-Regular.ttf、かな8字がNotoSansCJK-Regular.ttc index0（Sans JP）。jaは全13字がSans JP、直接JPファイル指定も4言語とも全13字Sans JPだった。nativeのserif指定もこの日本語サンプルではSansになるという観測をそのまま残し、CSS serifの期待値と混同しない。default Paintの実効LocaleListはzh-Hans-CN,ja-JP,en-USであり、明示localeと区別する。結果は`native-fallback.json`。

この結果で**「OS上の全描画でかなが明朝になる」という説明は反証され、WebView経路とnative Paintではフォールバック結果が違う**ことが分かった。さらに、Nagi engineを通さず、既定UA/font設定・JS有効の素WebViewでも同じlocalhost79ノードを測った。全79ノードの実フォント/glyphCountがengine版と一致し、zh/en/不明のかな8字がSerif SCになる現象を再現した。NagiのUA/Client Hints変更・広告ブロッカー・表示モード処理を必要条件とする説明はこの対照に合わない。証拠は`plain-webview/`。通常Chromeは未検証で、OEMフォントとWebView/Chromium間のどの選択経路に起因するかはまだ断定しない。

全サイトのlang=ja固定は中国語等の字形・読み上げ・改行へ、全font上書きは書式・Canvas・文字幅へ影響するため実装しない。local PostScriptによる全サイト注入もOEM名への依存と書式破壊のため行わない。

実機の証拠は`artifacts/device-compat-20261031/font/`。公開4件とfixture2件、`matrix/`（63ノード）、`matrix-local-names/`と`matrix-default-*`（各79ノード）を区別して保存。`matrix-app-ja/`も79ノードで、`app-locale-control.json`に設定の復元を記録した。各回はinstrumentation成功を確認し、nonce所有のlocalhost以外でgeneric font対照を拒否する。追加の純粋Pythonテスト5件を含む計33件が成功した。

## コードから確認したこと

- 通常の[WebViewBrowserEngine](../app/src/main/java/com/takeruf/nagi/browser/engine/WebViewBrowserEngine.kt)は、standard/sans-serif/serifのフォントファミリーを上書きしていない。デスクトップ表示で変えるのはUAとClient Hintsで、フォント設定は共通。
- [ReaderPane](../app/src/main/java/com/takeruf/nagi/ui/browser/ReaderPane.kt)は別のWebViewに抽出記事を表示し、その本文だけに`serif`を明示する。元ページのDOMへ明朝指定を注入する処理ではない。
- 広告ブロックはルールに合致するフォント要求を妨げる可能性はあるが、「すべてのWebフォントを無効にする」という設定はない。ブロックあり/なしで実際の要求と描画を比較する必要がある。
- 試験機で取得したWebSettingsは、standard=`sans-serif`、sansSerif=`sans-serif`、serif=`serif`、fixed=`monospace`。Chromiumの[AwSettingsの既定値](https://chromium.googlesource.com/chromium/src/+/refs/heads/main/android_webview/java/src/org/chromium/android_webview/AwSettings.java)とも一致した。

## エミュレーターでの初期検証と結果

新規の専用AVD `Nagi_Font_API36_20261031`、Android 16/API 36、Pixel Tablet、Google WebView 156.0.8078.47で実施。通常の利用者のCookieプロファイルや既存AVDは使用していない。

CSSの`font-family`文字列だけでは、実際に日本語の描画へ使われたフォントは分からない。そのため[CDPのCSS.getPlatformFontsForNode](https://chromedevtools.github.io/devtools-protocol/tot/CSS/#method-getPlatformFontsForNode)で、合成日本語テキスト「日本語漢字ひらがなカタカナ」の実フォントとglyphCountを取得した。実サイトではbody・入力欄・ボタンの**フォント指定をコピーした診断用ノードだけ**を測定し、ページ本文・入力値・文書内容は取得していない。

以下は初回の結果。初回helperはfamily/weightだけをコピーし、サンプルを`lang=ja`・32pxへ固定していた。従って**元要素と同じ言語・全フォント設定での結果ではない**。この制限を発見して修正し、後述の通り公開4ケースを再測定した。旧JPの結果を元ページの言語条件下で使われたフォントと同一視しない。

| ケース | 指定/対象 | 実際の日本語フォント |
|---|---|---|
| ローカル・デスクトップ | sans-serif、system-ui、Arial、Roboto、monospace、存在しないフォント+sans-serif | すべて`NotoSansCJKJP-Regular` |
| ローカル・モバイル | 同上 | すべて`NotoSansCJKJP-Regular` |
| ローカルの対照 | 明示的なserif | `NotoSerifCJKjp-Regular` |
| ChatGPT・デスクトップ・ブロック有効 | 公開ページのbody/入力要素/ボタンの指定をコピー | 3ノードとも`NotoSansCJKJP-Regular` |
| ChatGPT・モバイル・ブロック有効 | 同上 | 3ノードとも`NotoSansCJKJP-Regular` |
| ChatGPT・デスクトップ・ブロック無効 | 同上 | 3ノードとも`NotoSansCJKJP-Regular` |
| Google Docs入口・デスクトップ | 匿名状態で転送されたGoogleアカウント画面のbody/ボタンの指定をコピー | 2ノードとも`NotoSansCJKJP-Regular` |

ChatGPTの観測時のスタックは`ui-sans-serif`・`system-ui`等を含む。`document.fonts`の登録は0件、観測したFont要求も0件だった。このページ状態では日本語はOSフォールバックで描画されていた。フォント取得失敗が発生したとの証拠はないが、これだけで別のページ状態やスタイルシート取得の問題をすべて否定するものではない。

Googleアカウント画面のスタックは`"Google Sans", roboto, ... arial, sans-serif`。観測したWOFF2要求1件はHTTP 200、Font要求のloadingFailedは0件。必要なGoogle Sansのfaceはloadedで、日本語はOSのNoto Sans CJK JPへフォールバックした。`unloaded`のfaceも多数あったが、未使用のウェイト/文字範囲が未取得であることと、読み込みに失敗した`error`は区別する。[CSS Fontsの文字ごとのフォント選択](https://www.w3.org/TR/css-fonts-3/#font-matching-algorithm)

**Google Docsのログイン済み文書本文・Canvas描画を測定した結果ではない。** DOMの合成ノードを測るこの方法だけで、Canvas内で選ばれた実フォントは証明できない。

## 診断を正負対照で検証した結果

前節の非再現だけで診断方法が正しいとは保証できないため、同じAPI 36/WebView 156の隔離プロファイルで、意図的にフォント取得失敗・解析失敗・文字範囲の不一致を作った。新しい[FontControlServer](../app/src/androidTest/java/com/takeruf/nagi/FontControlServer.kt)は127.0.0.1だけで待ち受ける。Robotoは端末の`/system/fonts/Roboto-Regular.ttf`から読み、その場でHTTP配信する。フォントファイルを成果物へコピーせず、外部サイトや認証の挙動も模倣しない。

| 仮説/診断上の懸念 | 予測と検証操作 | 実測と判定 |
|---|---|---|
| Webフォントが失敗すると必ず日本語が明朝になる | 404フォント+sans-serif、不正データを200で返すフォント+sans-serifを表示 | 両方ともFontFaceはerrorだが、日本語はNoto Sans CJK JP。一般的な因果仮説は反証された |
| CDPが明朝への変化を見逃している | 同じ404フォントのfallbackだけserifにする。明示serifの対照も置く | 両方でNoto Serif CJK JPを検出。sans fallbackとの違いを検出できた |
| HTTP200/Network.loadingFailedなしならフォントは正常 | 不正なフォントデータをHTTP200で返す | HTTP200・当該要求のloadingFailed=0でもFontFaceはerror。HTTP/Networkだけの判定は不十分 |
| face=loaded、check(日本語)=trueなら、そのフォントで日本語を描画している | 有効なRobotoにunicode-range=U+0-7Fを指定し、欧文と日本語を別ノードで表示 | face=loadedかつcheck(日本語)=trueでも、日本語はnative Noto Sans、欧文だけcustom Roboto。文字ごとの実フォント確認が必要 |
| フォントのルール遮断を検出できない可能性 | 同一ページ・同一フォントで、広告ブロックだけ有効/無効を変更 | 有効時は対象要求がサーバーへ届かず、空の200応答とFontFace error。無効時はサーバーへ届き、face loaded・custom font使用へ変化。他の7ノードはCSS/実フォントとも一致 |

対照ページの8ノードを2構成で測定し、上記の予測を実行時assertで確認した。各構成のinstrumentationは成功、比較assertも成功。リスト3本のSHA-256は比較前後で一致した。ルール対象はバンドルEasyListにもある`/assets/ads/*$~image`に合致するループバックURL。これが実サイトで必要なフォントを遮断していたとの証拠ではない。

ブロック有効時のengineカウントは2、対象フォントのサーバー到達は0。無効時はカウント0、サーバー到達2。初回読み込みとCDP reloadを含む回数であり、1ナビゲーションの要求数ではない。不正200フォントは両構成でerrorになり、Networkの失敗分類は404要求だけだった。また、document.fonts全体のstatusは両構成ともloadedだった。**全体がloadedは「読み込み中のフォントがない」という状態で、全faceの成功保証ではない。**

[Font Loading仕様](https://www.w3.org/TR/css-font-loading/#dom-fontfaceset-check)も、unicode-range外の文字や存在しないfamilyに対してcheckがtrueになり得ると説明する。今回の対照はその誤判定の可能性を実ブラウザーで確認したもの。この段階の診断方法の検証は実機の原因証明ではなく、実機の追加結果は冒頭に分けて記録した。

## 診断実装の不具合修正と再測定

追加確認で、サイト以外に診断ツール自体の取り違えにつながる不具合を確認した。

| 不具合 | 修正・検証 |
|---|---|
| JS例外・Promise rejection・undefinedを値なしの成功と混同し得る | Runtime.evaluateのexceptionDetails/返値を検査し、機微情報を含み得る例外本文は保存せず固定の失敗分類だけを保存 |
| 欠落ノードをcontinueして空/部分結果を保存し得る | 要求した全ノード、重複、実描画glyph、sourceStyleとの一致を検査。不足はfailedとしcompleteにしない |
| font-readyのtimeoutを成功と区別しない | 明示的に拒否。CDPにもイベント受信で延長されない全体deadlineを設ける |
| 途中のreload/遷移後の別文書と混ざり得る | WebViewのnonceに加え文書ごとのcapture IDを検査し、世代が変われば失敗にする |
| family/weightだけ・固定サイズ/言語のコピー | computed font-*、spacing、writing-mode、実効lang/locale等を保持。公開サンプルは専用Shadow DOMでサイト側の要素セレクターの混入を避ける |
| 古い成功JSONや途中結果が残り得る | 開始時in_progress、失敗時failedへatomic更新。全対象とinstrumentation成功を確認してからcompleteを保存 |

コピー実装の正対照では、normal/italicとja/zh-Hansの**固定された合成参照ノード**を置いた。同じdocument rootとShadow rootの両方でCSS値・実フォント・合成文字列の幅/高さが一致することをassertした。variationを保持したRoboto参照とコピーの幅は共に83.359375px、variationだけ失わせた負対照は91.2109375px。同じRobotoというフォント名だけではvariation保持まで証明できないことも確認した。旧family/weightだけのコピーでは、参照のSourceSansPro-ItalicがRobotoへ変わる負対照を検出した。

また、診断用`all:initial!important`がこのWebViewの`-webkit-locale`をautoへ戻し、**lang=ja属性があってもJP→SCのSansへ変わる**ことを再現した。これはShadow内だけでなく同一document rootでも起きた。resetを外した対照と、resetありでも実効localeをコピーした対照はJPで参照と一致した。最終helperはprobeのresetを外し、computed localeも保持する。これは診断の不具合の修正であり、Nagi本体のフォント不具合を発見したという意味ではない。

修正後、公開4ケース（ChatGPT desktop/mobile/desktopブロック無効、Docs匿名入口）を1回ずつ有効な結果として採取した。**元ページの実効lang/localeはen-USで、日本語サンプルの実フォントは4ケースともNotoSansCJKSC-Regularだった。** 旧lang=ja固定で得たJPとの違いは、Sansの言語別選択であり、明朝化ではない。元要素のfont-style・stretch・variation等を保持した条件でも、今回の匿名表示で明朝化は未再現。ログイン済みDocs/Canvas、実機、別のページ状態を正常保証する結果ではない。

Python失敗系21件を検証し、実CDPでもthrow・Promise rejection・undefined・font待ちtimeout・応答timeout・missing node・文書世代変更の7条件を拒否できた。font待ちtimeoutの対照は未解決の合成ready Promiseを使い、実サイトの回線遅延そのものを再現したものではない。probe追加後は合成ノードだけでlayoutを確定してからfonts.readyを待ち、採取後にもstatusがloadedであることを検査する。`complete`は診断対象の採取完了を示し、全font face成功やサイトの正常性を意味しない。同端末でhelperを重複起動した無効な試行はfailedとして除外し、未完ケースだけ再試行した。同じ取り違えを防ぐため、端末serial単位の排他をready/done/結果へ触る前に獲得するようにした。端末へ触れない`--list-cases`は排他不要。

## 未確定の原因と対応候補

| 実機で確認すべきこと | 確認できた場合の対応 | 影響と検証 |
|---|---|---|
| ブロック有効時だけFont要求が失敗し、実フォントも変わる | 問題のルールと要求分類を特定して狭い例外を追加 | 無関係な広告遮断を維持し、再読み込み後の実フォントと通信成功を確認 |
| OS/WebView側でsans-serifの日本語も明朝になる | 同端末のChrome・素のWebView・OSフォント設定・プロバイダー版を比較し、プロバイダー更新等の原因別対応 | Nagiだけの問題かOS/OEM共通かを切り分ける。エミュレーターのフォント設定は実機の証拠にしない |
| サイトが明示的にserifを選択している | サイトの設定・文書のフォント指定を確認 | 強制上書きは意図した文書書式、改行、幅、高さを変更するため行わない |
| Webフォントはloadedだが日本語を含まない | その文字の実フォールバックとサイトの代替フォント指定を確認 | 欧文フォントの読み込み成功と日本語の対応範囲は別。`document.fonts.check()`だけで実フォントを判定しない |
| Google Docs編集面だけで発生する | 利用者が直接ログインし、合成テスト文書・Canvas側のフォント指定/描画を別に検証 | 実文書の内容や認証情報を採取しない。全サイトへのCSS注入ではCanvasの描画を直せない場合がある |

現時点では、全サイトに日本語フォントを強制する修正の根拠がないため、通常動作の変更は行っていない。実機では言語に応じたフォールバックの差まで確認したが、OS/OEMとWebViewの責任範囲は未確定。上流にはAndroidのフォントAPI/別メーカーのエイリアスを扱う[Skiaの修正記録](https://skia.googlesource.com/skia.git/+/ce19122e3982a94ba8c401dda0ec42928bc6d595)があるが、対象端末・症状・修正版を同一視しない。

## 再実行

[FontCompatibilityTest.kt](../app/src/androidTest/java/com/takeruf/nagi/FontCompatibilityTest.kt)は`fontProbe=true`の場合だけ実行する隔離診断。通常のテストでは冒頭でスキップする。再実行には専用の検証端末/AVDとDebug APKを使う。

```sh
ANDROID_SERIAL=emulator-5556 ./gradlew :app:assembleGithubDebug :app:assembleGithubDebugAndroidTest --no-parallel
adb -s emulator-5556 install -r app/build/outputs/apk/github/debug/app-github-debug.apk
adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/github/debug/app-github-debug-androidTest.apk
python3 -m venv /tmp/nagi-font-venv
/tmp/nagi-font-venv/bin/pip install websocket-client==1.9.0
/tmp/nagi-font-venv/bin/python scripts/inspect_font_compatibility.py \
  --serial emulator-5556 --output artifacts/font-compatibility
```

正負対照だけを実行する場合:

```sh
/tmp/nagi-font-venv/bin/python scripts/inspect_font_compatibility.py \
  --serial emulator-5556 --output artifacts/font-controls controls controls-no-blocking
```

コピーとAPI失敗系、Python単体テスト:

```sh
/tmp/nagi-font-venv/bin/python scripts/inspect_font_compatibility.py \
  --serial emulator-5556 --output artifacts/font-capture copy-controls api-controls
python3 -m unittest discover -s scripts/tests -p 'test_inspect_font_compatibility.py'
```

[補助スクリプト](../scripts/inspect_font_compatibility.py)は各ケースの起動、デバッグポート転送、計算済みスタックのコピー、実フォントの取得、診断用doneファイル作成、終了待ち、転送解除まで行う。DocumentStartで付けた診断専用の一時マーカーが一致するWebViewだけを採取し、既存の通常タブを同じURLという理由だけで選ばない。`--serial`は必須。ケース名を末尾に指定するとそのケースだけ実行でき、`--list-cases`では端末に接続せず選択ケースを表示する。省略時は6ケース。`--port`でローカル転送ポートを変更できる。Cookie値・トークン・ヘッダー・完全なサイトURL・本文はファイルに出力しない。直接`am instrument`だけで起動した場合は外部のdone作成が必要で、120秒でタイムアウトする。

既存アプリを保護する別applicationIdの診断版では、`--package com.takeruf.nagi.compatprobe`を指定する。PID検索、external filesのパス、instrumentation runnerはすべて指定packageへ切り替わり、runnerのテストIDは`PACKAGE.test`となる。テストクラスのコードnamespaceは元の`com.takeruf.nagi.FontCompatibilityTest`のまま。既定packageは`com.takeruf.nagi`。ADB自体が返すdevice IDもログへ書く前に置換する。package指定・ログ置換の純粋単体テストを追加し合計28件成功した。この追加の検証時点ではhelperから実機を操作していない。

結果は`artifacts/font-compatibility-20261031/final/`。JSONにはCSSファミリー、使用フォント名、glyphCount、Font通信のHTTP状態/件数、FontFaceの状態などを保存する。ビルド、補助スクリプトの構文検査、6構成の診断を検証した。マーカー照合追加後にもローカル1ケースを再実行して成功し、`fontProbe`省略時は仮定判定で1件スキップすることを確認した。このフォント調査による本体変更はない。

追加の正負対照は`artifacts/font-compatibility-20261031/controls-final/`に保存。Font通信は合成fixtureの要求分類、HTTP状態、失敗分類だけを記録し、URLやヘッダーは保存しない。実サイトの6ケースを再実行して非再現を重ねるのではなく、診断が誤った結論を出し得る条件を検証した。

layout待ちの最終防御を含むコピー正負対照・実CDP失敗系は`layout-final/`、公開4ケースの再測定は`capture-faithful/`（いずれも`artifacts/font-compatibility-20261031/`配下）。[font_probe.js](../scripts/font_probe.js)はfont属性と固定された合成ノードだけを扱う。[Python単体テスト](../scripts/tests/test_inspect_font_compatibility.py)は実際の通信やログインを開始しない。

実機相当の言語/family行列（`SERIAL`は作業時のみ指定し成果物へ保存しない）:

```sh
/tmp/nagi-font-venv/bin/python scripts/inspect_font_compatibility.py \
  --serial "$SERIAL" --package com.takeruf.nagi.compatprobe \
  --output artifacts/font-matrix --font-matrix \
  --matrix-stack artifacts/device-compat-20261031/font/chatgpt-desktop-fonts.json fixture-desktop
```

`--matrix-stack`は採取完了したChatGPTの合成bodyサンプルのfont-familyだけを使う。`--generic-sans-control sans-serif`等の対照は`--font-matrix`とlocalhost fixtureに限定され、通常ページでは拒否する。フォント名・DOM言語と合成テキスト以外を変更しない。通常の公開測定ではこれらの対照を実行しない。

素WebView対照は同じfixture/matrixコマンドへ`--plain-webview`を追加する。Android側の`fontPlain=true`もfixture2種以外を拒否する。別profileの素WebViewを作りJavaScriptだけ有効にし、UA/font設定は既定のまま。fixtureの絶対URL以外の要求とnavigationは診断専用clientが拒否する。Nagi広告ブロッカーを使う経路ではない。nonce・結果のplainWebViewフラグ・プロファイル破棄で対象の取り違えを防ぐ。素WebViewは既定UAなので、engineとの初回比較ではUA等の設定差も残る。native API対照の`--native-sans-control`はplain限定でsansSerifだけを設定し、getterの一致を確認する。CDPとの同時上書きは禁止。Python単体テストはこれらのガードを含む35件成功。
