# Arcの参照仕様と今回の変更

調査・実装日: 2026-10-03。対象はAndroidタブレットのSidebarと地域別の検索初期設定です。

## 参照した仕様

- [Arc公式: Favorites](https://resources.arc.net/hc/en-us/articles/19230755904151-Favorites-Top-Tabs-Across-Every-Space): 最上部、全Space共通、最大12件。上部へのドラッグでFavorite化、下部へのドラッグで解除。
- [Arc公式: Pinned Tabs](https://resources.arc.net/hc/en-us/articles/19231060187159-Pinned-Tabs-Tabs-you-want-to-stick-around): Space別の固定タブ。水平線の上下へドラッグして固定・解除。Spaceの見出しから固定領域を折りたたみ。
- [Arc公式: Spaces](https://resources.arc.net/hc/en-us/articles/19228064149143-Spaces-Distinct-Browsing-Areas): 下部のアイコンでSpace切り替え。Spaceごとの色・アイコン。
- [Arc Sidebarの画面例](https://dannyspina.com/blog/arc_browser): 検索欄、アイコンタイル、控えめな見出し、区切り線、淡い選択行、下部のSpace配置を目視参照。画像はアプリに同梱していません。
- [Android公式: Pointer input](https://developer.android.com/develop/ui/compose/touch-input/pointer-input): タッチとマウス、入力イベントの受け渡し。
- [Country APIの公式仕様](https://github.com/lineofflight/country): 接続元IPの国コードをHTTPS GETで取得。追加のAPIキーは不要。

## UI・操作の変更

| Before | After | Why |
| --- | --- | --- |
| アイコンからだけドラッグ、メニューのMove up/down | 行全体の長押しドラッグ、マウスとアイコンは直接ドラッグ | タブそのものをつかんで並べる操作にする |
| 行全体をドロップ先として着色 | 上下の挿入線、持ち上げたタブのプレビュー、元の行を淡く表示 | どこへ移動するかを示す |
| ドラッグキャンセルでも移動処理 | キャンセル時は保存せず、指を離したときだけ保存 | 中断した操作で並び順を変えない |
| 並び替えだけでPin状態は変わらない | 固定領域と通常領域をまたいで固定・解除、空領域にもドロップ | Arcの区切り線による整理に合わせる |
| 長いリストの画面外へ移動できない | 上下端で自動スクロール、サイドバー全体でジェスチャーを保持 | 元の行が再利用・破棄されてもドラッグを継続する |
| Space別Favorites、タイトル付きカード | 全Space共通のアイコンタイル。上部へのドラッグでFavorite化し、下へ戻してタブ化 | FavoritesとSpace別Pinnedを区別する |
| 上部のSpaceカード、ドラッグ中だけ別Spaceのリスト | 常設の下部Spaceアイコン、そこへのドロップで移動 | 普段のSpace切り替えと同じ場所を使う |
| 色・アイコンは初期値のみ | Space編集で名前・色・アイコンを変更 | Spaceを視覚的に見分ける |
| 大きなロゴ・紹介文と重複するFavorites | 控えめな新規タブ画面、薄い背景と検索欄 | Webコンテンツとサイドバーを主役にする |
| 幅変更もアニメーションで追従 | リサイズは指に直接追従、フレームの角丸と余白を縮小 | 連続操作の遅れを減らす |

保存はRoomのtransactionで行います。タブを取り除いた後に挿入位置を算出するため、上から下への移動でもずれません。Favoritesの並び順も永続化します。Spaceを削除しても共有Favoritesは残ります。

## 検索の地域最適化

新規インストールは自動判定を有効にします。旧版の保存済みデフォルトは手動設定として維持します。

1. 初回は端末で取得可能な国コードから代替設定をすぐ適用。
2. `https://api.country.is/`へ起動をブロックしないHTTPS接続。`country`だけを使用し、返されたIPは保存・ログ出力しません。
3. IPが取得できれば優先。取得不能時は携帯網 → SIM → 端末の地域を使用。中国語という言語設定だけでは判定しません。
4. 国コード`CN`なら百度、それ以外ならGoogle。HK・MO・TWは`CN`として扱いません。どの国も取得できなければ現在のエンジンを維持。
5. IPは24時間、代替判定・失敗は1時間キャッシュ。起動と画面復帰時に期限を確認。設定のCheck againで強制更新。
6. 手動でエンジンを選ぶと自動判定をオフにする。通信中に手動変更しても、後から取得した国コードで上書きしない。
7. 百度を既存データに一度だけ追加。後で削除した場合は復活させず、必要なエンジンがなければ現在の設定を維持。

検索語・閲覧履歴・端末ID・明示的なIPパラメーターを地域サービスへ送信しません。ただしサービスにはHTTPS接続元IPが見えます。自動判定をオフにすると新たな地域確認を行いません。VPNは出口IPを変えるため、実際の滞在地と判定国が違う場合があります。中国大陸の実回線とローミング端末での到達性・SIM判定は未検証です。

## 範囲と残る差

Favoritesはローカルの共有ショートカットで、移動前のWebViewのページ内履歴・フォーム状態を保持する「常駐アプリ型タブ」ではありません。フォルダー、Pinned URLへのリセット、Peek、複数タブの一括ドラッグ、Space自体のドラッグ並び替え、Arc Syncは未実装です。

## 検証結果

API 36のPixel Tabletエミュレーター `Orbit_Tablet_QA`、2560 × 1600 / 320 dpi / 横画面で確認。

| 確認 | 結果 |
| --- | --- |
| Debug / Release APK | ビルド成功。Releaseはunsigned |
| 単体テスト | 34件成功、失敗・スキップ0 |
| Android Lint | エラー0 |
| Android端末テスト | 16件成功 |
| 新しいドラッグ操作 | 行全体の長押し、マウス即時ドラッグ、上下への挿入、Pin/Unpin、キャンセル、Favoritesへの移動と取り出し、別Spaceへの移動、Activity再生成、長いリストの自動スクロール |
| 既存機能の回帰 | ブラウズ、履歴、Bookmarks、タブ復元、Split、エンジン編集、日本語・中国語・韓国語のIME composition、WebView機能 |
| 地域判定 | CN/HK/MO/TW/JP、IP優先、代替判定、通信失敗、キャッシュ・再試行、通信中の手動設定優先、削除したエンジンを再生成しないことを単体テストで確認 |
| 表示 | 実際のCompose画面・プレビュー・挿入線・設定をキャプチャして目視確認 |

地域判定のテストは注入した国コードを用いています。中国大陸での実IP判定・実回線の到達性と、物理端末でのタッチ・マウス動作は未確認です。

画面: [Sidebar](screenshots/arc-sidebar.png)、[ドラッグ中](screenshots/arc-sidebar-drag.png)、[地域検索設定](screenshots/regional-search-settings.png)、[Space編集](screenshots/arc-space-editor.png)。

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease \
  :app:testDebugUnitTest :app:lintDebug
adb -s emulator-5554 shell am instrument -w -r \
  -e class com.takeruf.nagi.SidebarDragTest,com.takeruf.nagi.BrowserUiTest,com.takeruf.nagi.CjkInputTest,com.takeruf.nagi.WebViewEngineTest \
  com.takeruf.nagi.test/androidx.test.runner.AndroidJUnitRunner
```

APK: `app/build/outputs/apk/debug/app-debug.apk`。ログ: `app/build/reports/tests/testDebugUnitTest/`、`app/build/reports/lint-results-debug.html`。端末の最終テスト出力は`OK (16 tests)`を確認しました。
