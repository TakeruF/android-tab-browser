# 中国語自然さ監査の反映

ユーザー提示の監査レポートに基づく修正。番号ではなく実際のリソースキーで対応付けた。`ui_内部工具_saved` は `ui_download_saved`、`ui_generated_内部工具_failed` は `ui_generated_download_failed` として扱った。

10 キーを修正。元の全290件の対応表は修正前スナップショットとして保持する。

| キー | 修正前 | 修正後 |
|---|---|---|
| `ui_sleep_tabs_description` | 当打开的网页超过6个时，可休眠闲置10分钟的安全后台标签页。正在显示、编辑表单或使用媒体的页面保持运行。休眠页面打开时会重新加载，部分页面状态可能丢失。 | 同时运行的网页超过 6 个时，闲置至少 10 分钟且符合休眠条件的后台标签页会自动休眠。正在显示、表单内容已修改或正在播放媒体的页面不会休眠。休眠的标签页再次打开时会重新加载，部分页面状态可能丢失。 |
| `ui_common_search_engines_description` | 选择搜索时显示的引擎。“搜索默认”用于网页搜索，“AI 默认”用于向 AI 提问。两个默认引擎始终包含在内，出行时也会保留您选择的组合。 | 选择搜索时显示的引擎。“搜索默认”用于网页搜索，“AI 默认”用于向 AI 提问。两个默认引擎始终包含在内，出行时也会保留你选择的组合。 |
| `ui_camera_unavailable` | 无法打开相机应用。 | 没有可用的相机应用。 |
| `ui_download_saved` | 已保存到 Downloads | 已保存到“下载”文件夹 |
| `ui_search_anything` | 搜索内容 | 搜索任何内容 |
| `ui_site_controls` | 网站控制 | 网站设置 |
| `ui_generated_download_failed` | 无法保存生成的文件。大小上限为32 MB。 | 无法保存生成的文件。大小上限为 32 MB。 |
| `ui_private_browsing_and_content_blocking_are_planned_for_a_later_release` | 隐私浏览和内容拦截计划在后续版本提供。 | 隐私浏览和内容拦截将在后续版本中推出。 |
| `ui_update_install_description` | Android 会要求允许 Nagi 安装应用并确认更新。保留浏览数据。 | Android 会提示你允许来自 Nagi 的应用安装，并确认安装更新。你的浏览数据会保留。 |
| `ui_checked_on_launch_or_when_you_choose_archive_now_pinned_b64b52e0` | 启动时或选择“立即归档”时检查。固定标签页和当前标签页将保留。 | 启动时或手动选择“立即归档”时会自动检查。固定标签页和当前标签页不会被归档。 |

## 意味を保つための調整

- 休眠判定は `EnginePool.suspendBackground` の稼働ページ数 > 6、非表示期間 >= 10 分。開いている全タグ数とは異なるため「同时运行」「至少 10 分钟」とした。
- `WebViewBrowserEngine.canSuspend` はフォームの変更を保持して判定するため、「正在填写」だけに限定せず「表单内容已修改」とした。再読み込み・状態喪失の説明も残した。
- インストール説明は、Nagi をインストール元として許可する意味と、ブラウジングデータ保持の説明を残した。
- 「想去哪里？」、バッジの「AI 默认／搜索默认」、履歴の「历史记录／浏览记录」は許容範囲のため維持。

## 検証

- XML解析、キー照合、書式引数と `{query}` の照合は成功。
- `./gradlew :app:processGithubDebugResources --console=plain` 成功（Androidリソースのコンパイル・リンク）。
- `git diff --check` 成功。実機の表示・折り返しは未確認。

## Play ストアを含む追加フィードバック

- `docs/PLAY_CONSOLE.md` の簡体字中国語原稿を修正。Space はアプリ内の「空间」と対応付け、初出でタスク別の工作区として説明した。サイドバーは「侧边栏」、テーマは「跟随系统」、AI サービスは「可用情况」に統一した。「您」も既存 UI に合わせて「你」とした。
- ダウンロード説明は「网页生成的文件（Blob/data）」とし、32 MB の上限を併記した。休眠後の再読み込みと状態喪失の可能性も残した。
- アプリ内の `ui_sleep_tabs` を「标签页后台休眠」に修正。旧 Blob/data 未対応メッセージにも一般向けの補足を加えた。旧メッセージのリソースキーと呼び出し仕様は維持した。
- `docs/play-store/zh-CN-release-notes.md` に 0.1.2 と 0.1.3 の原稿を用意した。分屏・侧边栏は「优化」、0.1.2 の地区 AI 選択肢は実際の追加内容に合わせて「新增」とした。
- 290 件の翻訳キー、置換引数、`{query}` の照合成功。ストアの簡単な説明は 42/80 字、詳細説明は 746/4000 字、更新説明は 0.1.2 が 270/500 字、0.1.3 が 136/500 字。
- `./gradlew :app:processGithubDebugResources :app:processPlayDebugResources --console=plain` と `git diff --check` 成功。
- この追加修正はローカルの原稿・リソースのみ。Play Console への保存・審査提出、APK/AAB の公開、実機表示の確認は未実施。

## 続行依頼によるストア反映

- ユーザーの「アプリアップデートが不必要な部分についてはすぐ反映させて」に従い、2026-10-06 に zh-CN の簡単な説明・詳細説明を Play Console へ保存し、2 件の変更を審査へ提出した。「2 changes sent for review」「Changes in review」を確認。公開は審査完了待ち。
- 0.1.2 と 0.1.3 の zh-CN 更新説明も保存した。各操作で「Release details updated」を確認し、0.1.3 は保存後の言語別一覧で修正文を確認した。
- 新規ビルドのアップロード・配信設定の変更は行っていない。アプリ内リソースの変更は次回のアプリ更新で反映する。
- 証跡：`docs/play-store/console-zh-copy-review.jpg`。
