# Nagi

Androidタブレット向けのサイドバー中心のワークスペースブラウザ。Kotlin / Jetpack Compose / Material 3 / WebView / Room / DataStoreで構成しています。

![Nagiのライトテーマ](docs/screenshots/ui-home.png)

## 主な機能

- Spaceごとのタブ管理、固定タブ、共通のFavorites、Bookmarks、履歴。
- URL・検索・タブ・Space・コマンドをまとめて探せるCommand Bar。
- Command Barで通常検索と「ChatGPTに聞く」を上下キー・Enterまたはクリック／タッチで選択。80文字以上や質問・説明／比較などの依頼ではChatGPTを先頭に表示します。URLと検索エンジンの明示キーワードは優先します。
- 2ペインのSplit、幅変更・折りたたみ可能なSidebar、ドラッグによる並び替え。
- ライト／ダーク／システムテーマ。選択状態と文字のコントラストを調整。
- 日本語・中国語・韓国語のIME入力、キーボード操作、マウス／トラックパッドのスクロール。
- ページ内検索、Desktop Mode、通常のHTTPダウンロード、システムファイル選択。

[0.1.1をダウンロード](https://github.com/TakeruF/android-tab-browser/releases/tag/v0.1.1)。以後は設定の「アプリの更新」から確認・ダウンロード・インストールできます。

バージョンは**0.1.1 / MVP**です。対応範囲と制限は下記に記載しています。

## 起動

Android Studioでこのディレクトリを開いてGradle Syncを実行し、`app`をAndroid 8.0以降のタブレットで起動してください。横画面を推奨します。端末の回転やAndroidのマルチウィンドウは制限していません。

- JDK 17
- Android SDK Platform 36（Build Tools等はGradle / Android Studioで取得）
- minSdk 26 / targetSdk 36
- Gradle Wrapper同梱

```sh
git clone https://github.com/TakeruF/android-tab-browser.git
cd android-tab-browser
./gradlew :app:assembleDebug
```

CLIでは`ANDROID_HOME`でAndroid SDKを指定するか、ローカルの`local.properties`に`sdk.dir`を設定してください。`local.properties`はGit管理対象外です。

ADB接続したタブレット／エミュレーターにインストール:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.takeruf.nagi/.MainActivity
```

複数端末を接続している場合は`adb -s <serial>`で対象を指定してください。

APK: `app/build/outputs/apk/debug/app-debug.apk`。署名付きReleaseは`./gradlew :app:assembleRelease`で生成できます。署名鍵の設定と公開手順は[配信・アプリ内更新](docs/RELEASING.md)を参照してください。

## 操作

- サイドバー下部のアイコンからSpaceを切り替え。Spaceのメニューから名前・色・アイコンを編集。
- `Ctrl L`でURL・検索・タブ・Space・コマンドを検索。`>`でコマンドだけを表示。
- 検索候補は設定で選んだ「常用検索エンジン」だけを表示。初期値はGoogle＋ChatGPT、中国大陸では百度＋千问。Google＋百度＋ChatGPTなども選択でき、選択後は地域が変わっても組み合わせを保持します。常用から外したエンジンはキーワード検索の対象にもなりません。登録一覧ではエンジンの追加・編集・削除ができます。
- タブ行を長押ししてドラッグ＆ドロップで並び替え。マウス、またはタブのアイコンからは直接ドラッグ。挿入線とプレビューを表示し、リスト端で自動スクロール。
- 区切り線の上下にドロップして固定・解除。最上部にドロップしてFavorites化、Favoritesを下に戻してタブ化。下部のSpaceアイコンへドロップして移動。タブの`…`から保存・右ペイン表示などの操作。
- ページの`…`からSplit、Desktop Mode、ページ内検索、Bookmarks／Favorites保存。サイドバーのBookmarksから保存ページを閲覧・削除。
- ページ内リンク／画像の長押し・右クリックで、新規タブ表示、URLコピー／共有、画像保存。
- Splitの中央の仕切りをドラッグして比率変更。右ペインの`Choose tab`で表示タブを選択。ページメニューから左右入れ替え・Split解除。
- サイドバー境界を右にドラッグして幅変更・展開、大きく左にスワイプして折りたたみ。

| ショートカット | 操作 |
| --- | --- |
| Ctrl / Command + L | Omnibox |
| Ctrl / Command + T | 新規タブ |
| Ctrl / Command + W | 選択ペインのタブを閉じる |
| Ctrl / Command + Shift + T | 最後に閉じたタブを復元 |
| Ctrl / Command + Tab / Shift + Tab | 次 / 前のタブ |
| Ctrl / Command + 1〜9 | サイドバーのN番目を開く（お気に入り → ピン留め → 通常タブ。9は9番目、存在しない番号は何もしない。テンキー対応） |
| Ctrl / Command + R | リロード |
| Ctrl / Command + F | ページ内検索 |
| Alt + Left / Right | 戻る / 進む |

## 設計

[アーキテクチャとRoom schema](docs/ARCHITECTURE.md)を参照してください。WebViewの設定・API操作は`WebViewBrowserEngine`に閉じ込め、ComposeはAndroid用描画アダプターからViewを接続します。セッションプールは、開いたタブのlive WebViewをActivity終了まで保持します。非表示タブはpauseしますが、現時点では自動回収の上限はありません。

## 対応範囲と制限

Archiveは起動時と手動実行です。

- Spacesはタブのグループです。Favoritesは全Space共通です。Cookie・Web Storage・サイトログインは共有します。
- アプリ再起動時はRoomのURL・タイトル・順序・選択タブを復元します。同じActivityでは開いたタブのWebViewを保持し、タブやSpaceの切り替えによる自動破棄・再読み込みを行いません。Activityやプロセス終了後のフォーム値・スクロール位置・ページ内履歴の復元は保証しません。
- Cookieは有効、サードパーティCookieは無効。設定の「Cookie・サイトデータを消去」で全Spaceのサイトログイン・ストレージ・ページキャッシュをリセットできます。タブ・Bookmarks・履歴は保持します。証明書エラーはキャンセルし、サイト権限は毎回確認します。
- `target="_blank"`とユーザー操作によるURL付き`window.open`に対応。`about:blank`へJavaScriptで文書を書き込むポップアップは未対応です。
- ダウンロードはDownloadManagerを使用。blob / data URLは未対応です。Android 8–9ではアプリ専用Downloads、Android 10以降では公開Downloadsに保存します。
- アップロードはシステムのドキュメントピッカーを使用。直接撮影するカメラピッカーとフォルダーアップロードは未実装です。
- Sync / Reader / アプリ内AIチャット / Userscripts / Content Blocking / Private Browsingは未実装です。
- ChatGPT連携は`https://chatgpt.com/?q=…`へのWeb引き渡しです。APIキーは不要で、ログイン・質問の入力反映・送信はChatGPT側の動作に依存します。ページ本文は送信しません。

検索テンプレートにはHTTPS URLと`{query}`を使用してください。`bd`は百度、`sg`は搜狗、`360`は360、`dy`は抖音、`sm`は神马、`qwen`は千问、`pplx`はPerplexityです。千问は設定画面では「千问（中国大陆）」／「Qwen (China Mainland)」、検索候補では「千问」／「Qwen」と表示します。標準15エンジンを用意しています。

表示言語は日本語・英語・韓国語・中国語（簡体字）に対応し、端末の言語に従います。Android 13以降では端末のアプリ情報にある「言語」から個別に選択できます。[多言語・検索エンジンの詳細](docs/LOCALIZATION.md)。

新規インストールでは「Automatic search by region」が有効です。既存の保存済みデフォルトは維持し、設定から自動判定を有効にできます。有効なら、IPの国コードが`CN`のとき百度、その他はGoogleを選びます。起動をブロックせず、取得不能時は携帯網 → SIM → 端末の地域設定を使用します。言語だけでは中国大陸と判定しません。IP判定は24時間、代替判定・失敗は1時間キャッシュし、起動・画面復帰時に期限を確認します。設定の「Check again」から再判定できます。手動で検索エンジンを選ぶと自動設定がオフになります。削除したエンジンは再作成せず、必要なエンジンがなければ現在の設定を保持します。

IP判定には[api.country.is](https://github.com/lineofflight/country)へHTTPS接続します。相手には接続元IPが見えますが、検索語・閲覧履歴・端末IDは送信しません。VPN利用時は出口IPの国が判定される場合があります。中国大陸の実回線での到達性は未検証です。

Arcの参照仕様と今回の検証は[ARC_REFINEMENT.md](docs/ARC_REFINEMENT.md)を参照してください。

日本語・中国語・韓国語の変換操作はIME compositionを保持し、未確定中のEnter・矢印をCommand Barから奪わない設計です。端末テストにはdebugビルドだけに含まれるInputConnectionテスト用IMEを使います。通常利用時は端末のキーボードを選択してください。

## テストと画面確認

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

`emulator-5554`は対象端末のserialに置き換えてください。

2026-10-03、JDK 17 / SDK 36 / API 36のPixel Tabletエミュレーターで確認しました。

| 検証 | 結果 |
| --- | --- |
| ユニットテスト | 34件成功 |
| Android端末テスト | 29件成功（UI・CJK入力・WebView・スクロール・ドラッグ） |
| Debug / Release / Test APK | ビルド成功。Releaseはunsigned |
| Android Lint | エラー0、警告23 |

追加のUIテスト9件では、合成済み画面の文字・アイコンのコントラスト、全6色のSpace、テーマ切替・Activity再生成、Command Barのキーボード操作、折りたたみ時のタブの読み上げ情報、縦画面の狭いSplitとページ内検索、システムテーマ追従、接続エラーの再試行を確認しています。

[詳細な検証記録](docs/VALIDATION.md)には修正内容、各テストの範囲、レポートの場所を記載しています。物理端末、Android 8–15、メーカーごとのIME、実ハードウェアのカメラ・マイク・位置情報・トラックパッドは未確認です。

| ダークテーマ | 縦画面のSplit / ページ内検索 |
| --- | --- |
| ![ダークテーマ](docs/screenshots/ui-home-dark.png) | ![縦画面のページ内検索](docs/screenshots/ui-portrait-find.png) |

[Command Bar](docs/screenshots/ui-light-commands.png)・[ドラッグ中](docs/screenshots/arc-sidebar-drag.png)・[地域検索の設定](docs/screenshots/regional-search-settings.png)も実行時の画面です。

## ライセンス

Nagi本体は[MIT License](LICENSE)です。依存ライブラリの出典・ライセンスは[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)にまとめ、原文と著作権表記をAPKの`assets/licenses/`にも同梱しています。
