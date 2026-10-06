# 配信とアプリ内アップデート

配信先は [GitHub Releases](https://github.com/TakeruF/android-tab-browser/releases)。packageは `com.takeruf.nagi`。0.1.1は `versionCode=2`、minSdk 26、署名付き・R8最適化済み・debuggable=falseのAPKを配布する。

## 署名

Releaseの署名設定は環境変数 `NAGI_SIGNING_PROPERTIES` が指すPropertiesファイルから読み込む。未設定時は `~/.config/nagi/release-signing.properties`。必要なキーは `storeFile` / `storePassword` / `keyAlias` / `keyPassword`。署名設定がない場合はReleaseビルドを成功させず、unsigned APKを配布しない。

0.1.1は takeruf.com/nagi で配布済みの0.1.0 APKと同じ配布鍵を使用する。元の公開APKからpackage・version・署名fingerprintを検査して、Release keystoreを設定した。同じpackageと署名を維持し、アンインストールせずに更新する。ローカルDebug版は別署名のため、この配布版の上書き更新対象ではない。今後もこの配布鍵を継続使用する。

設定ファイルとkeystoreはGit管理対象外の `~/.config/nagi/` にあり、ファイル権限600。秘密鍵とパスワードを安全な場所へ別途バックアップする。公開するのは証明書fingerprintとAPKのchecksumだけ。

署名証明書SHA-256: `a36f6aa66c975c3fc2d2b2d4c424dbb911cbef08b4e16532eba82acd6d7468cc`。

旧package `com.orbit.browser` と別packageを使うインストールは、Androidの同一アプリ更新の対象ではない。

## 更新の流れ

起動時に一度、および設定の「アプリの更新」から、HTTPSで `releases/latest/download/update.json` を取得する。確認失敗でブラウジングを妨げない。新しいversionCodeを検出すると通知ボタンを表示し、ユーザー操作でダウンロードを開始する。

APKはアプリprivate cacheへ保存し、サイズとSHA-256、package、versionName、versionCode、minSdk、インストール済みAPKとの署名一致を検査する。別署名・別package・ダウングレード・破損ファイルはインストールへ渡さない。インストール直前にも再検査する。未完成ファイルは成功・失敗・キャンセル後に削除する。

Android 8以降の「この提供元からのインストール」設定へ必要に応じて案内し、許可後にAndroid標準のインストール確認画面を開く。バックグラウンドで勝手にインストールしない。0.1.0にはこの機能がないため、0.1.1への初回更新は配布APKを開いて行う。0.1.1以降の更新をアプリ内で確認できる。

Google Play版を将来作る場合は、GitHub用の `REQUEST_INSTALL_PACKAGES` とAPK配信ではなく、Playの更新方式を使う別flavorに分ける。

## 公開手順

1. versionNameとversionCodeを上げ、Release notesを作成する。
2. 単体・端末テスト、Lint、署名付きReleaseを検証する。
3. 関連変更のみcommitし、同じcommitのclean checkoutからRelease APKを生成する。
4. `scripts/prepare_release.py APK --notes RELEASE_NOTES --out OUTPUT_DIR` でAPKと `update.json` / `SHA256SUMS` を生成する。
5. commitをpushし、タグ `v<versionName>` のGitHub ReleaseへAPK、JSON、checksumを同時にアップロードする。
6. 公開JSONとAPKを再取得し、checksum・署名・package・versionを検査。アプリからの更新確認も行う。

`update.json` のスキーマ:

```json
{
  "applicationId": "com.takeruf.nagi",
  "versionCode": 2,
  "versionName": "0.1.1",
  "minSdk": 26,
  "apkUrl": "https://github.com/TakeruF/android-tab-browser/releases/download/v0.1.1/nagi-0.1.1.apk",
  "sha256": "64 lowercase hexadecimal characters",
  "size": 123,
  "notes": "Release notes"
}
```

更新チェックは公開GitHubのHTTPS配信を使用し、API keyやGitHub tokenをアプリへ埋め込まない。接続先には接続元IPとNagiのバージョン付きUser-Agentが見えるが、閲覧履歴や検索語を送らない。利用ネットワークからGitHubへ到達できない場合はエラー表示後に再試行する。
