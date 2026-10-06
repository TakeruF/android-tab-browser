# 多言語表示と追加検索エンジン

## 表示言語

日本語・英語・韓国語・中国語（簡体字）に対応。ホーム、サイドバー、メニュー、設定、検索候補、ページ内検索、履歴・ブックマーク、権限確認、ダウンロード確認、アプリの検証エラー、読み上げ用の説明を翻訳しています。ページのコンテンツやユーザーが付けた名前は変更しません。標準のPersonal／Workと空タブは表示時に翻訳し、保存データを端末の言語で書き換えません。

端末の言語に応じて自動選択します。対応しない言語では英語を使用します。Android 13以降は端末のアプリ情報にある「言語」からNagiの言語を個別に選べます。Android 8–12では端末の言語設定に従います。仕組みは[Android公式のアプリ別言語設定](https://developer.android.com/guide/topics/resources/app-languages)に沿った`android:localeConfig`とAndroid標準リソースです。

文言は`app/src/main/res/values/strings.xml`に英語を定義し、`values-ja`・`values-ko`・`values-zh`に翻訳を配置しています。各言語219件。`%1$s`などの引数番号は翻訳でも維持してください。Composeでは現在のConfigurationに応じたリソースを使います。共有の入力検証・検索候補生成には表示時の翻訳を渡し、翻訳したコマンド名と英語名の両方で検索できます。

## 検索エンジン

| 名前 | キーワード | 検索URLテンプレート |
| --- | --- | --- |
| 搜狗 | `sg` | `https://www.sogou.com/web?query={query}` |
| 360 | `360` | `https://www.so.com/s?q={query}` |
| 抖音 | `dy` | `https://www.douyin.com/search/{query}` |
| 神马 | `sm` | `https://m.sm.cn/s?q={query}` |
| 千问（中国大陆） / Qwen (China Mainland) | `qwen` | `https://www.qianwen.com/?q={query}` |
| Perplexity | `pplx` | `https://www.perplexity.ai/search/?q={query}` |

既存の9件と合わせて15件になります。千问は設定画面で地域名を表示し、検索候補では「千问」／「Qwen」と表示します。常用検索エンジンに選ぶと、`qwen 東京 天気`や`pplx 東京 天気`でも検索できます。例えば`sg 東京 天気`、`dy 韩国 旅行`で検索できます。抖音の検索語はURLのパスに入るため、空白を`+`ではなく`%20`でエンコードします。日本語・韓国語・中国語、`+`・`/`・`?`・`&`も検索語としてエンコードします。

[Sogou公式ホーム](https://www.sogou.com/)と[360公式ホーム](https://www.so.com/)の検索フォーム、[神马のモバイルホーム](https://m.sm.cn/)の検索アクション、[抖音の公開検索URL](https://www.douyin.com/search/)を参照。各サービスの実検索結果・ログイン・地域ごとのアクセス可否は別の条件です。この検証ではURL生成とアプリ内での選択・表示を確認しています。

既存のインストールには一度だけ追加します。同じIDかキーワードが既にある場合は置き換えません。標準検索エンジン、既存の編集内容、カスタムエンジンは保持します。追加後に編集・削除したエンジンを、次の起動で元に戻しません。
