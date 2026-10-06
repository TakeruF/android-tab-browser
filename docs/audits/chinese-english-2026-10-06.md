# Nagi 中国語・英語対応表（監査用）

2026-10-06 のローカル作業ツリーから抽出。HEAD: `3e1f7b5`。未コミット変更を含むため、HEAD単体の内容とは異なる可能性があります。

中国語リソース 290 件（UI文言 289 件＋プライバシーポリシーURL 1 件）。英語は `values/strings.xml` のデフォルトリソース、中国語は `values-zh/strings.xml` の簡体字表現です。画面表示・読み上げ用文言・エラー文・未使用の可能性がある定義も含めたリソース全件表であり、全件が現在画面に出ることを意味しません。ウェブサイト本文やユーザー入力、OS提供の表示は対象外です。

XMLの外側の引用符とAndroidのエスケープを表示用に除去。`%1$s`、`%2$s` は実行時の差し込み、`{query}` は検索テンプレートです。先頭空白はHTMLの空白表記で明示しています。

## 機械的照合結果

- 翻訳対象キーの欠落: 0 件。
- 中国語側だけのキー: 0 件。
- 書式引数・`{query}` の不一致: 0 件。
- `app_name = Nagi` は `translatable="false"` のため中国語定義なし。翻訳漏れではありません。
- `AI` は英語と中国語で同一。プライバシーポリシーURLは `/en/` → `/zh/`。

## 表現監査の確認候補

以下は修正前の確認候補です。翻訳は変更していません。

| キー | 現在の中国語 | 確認したい点 |
|---|---|---|
| `ui_common_search_engines_description` | 出行时也会保留您选择的组合。 | 他の文言は「你」。敬称「您」との統一。 |
| `ui_download_saved` | 已保存到 Downloads | 保存先を「下载」または「下载文件夹」と表記するか。 |
| `ui_ai_default_badge` / `ui_search_default_badge` | AI 默认 / 搜索默认 | 「默认 AI 引擎」「默认搜索引擎」との用語・語順統一。バッジ幅も考慮。 |
| `ui_sleep_tabs_description` | 当打开的网页超过6个时…正在显示、编辑表单或使用媒体的页面保持运行。 | 英語の “live” は稼働中ページ、「打开的网页」は開いているページ。英語の “edited forms” は編集済みフォーム、中国語は編集中とも読める。条件の意味を確認。 |
| `ui_sleep_tabs_description` | 可休眠闲置10分钟的安全后台标签页 | 「安全」が何を指すか伝わりにくい。休眠可能条件を平易に表すか。 |
| `ui_site_controls` | 网站控制 | 文脈に応じて「网站设置」などの自然さを確認。 |
| `ui_history` / `ui_clear_browsing_history_a2d6ed` / `ui_your_browsing_history_will_appear_here` | 历史记录 / 清除浏览记录 / 浏览记录将在此处显示。 | 同じ履歴機能を「历史记录」「浏览记录」で呼び分ける方針を確認。 |
| `ui_camera` / `ui_camera_unavailable` | 摄像头 / 无法打开相机应用。 | ハードウェアと撮影アプリの区別は自然。ただし英語 “No camera app is available” に対する「无法打开」は障害にも読める。 |

## 全件対応表

| No. | リソースキー | 元の英語 | 現在の中国語 |
|---:|---|---|---|
| 1 | `ui_new_tab` | New tab | 新标签页 |
| 2 | `ui_search` | Search | 搜索 |
| 3 | `ui_history` | History | 历史记录 |
| 4 | `ui_settings` | Settings | 设置 |
| 5 | `ui_space` | Space | 空间 |
| 6 | `ui_spaces` | Spaces | 空间 |
| 7 | `ui_personal` | Personal | 个人 |
| 8 | `ui_work` | Work | 工作 |
| 9 | `ui_save` | Save | 保存 |
| 10 | `ui_cancel` | Cancel | 取消 |
| 11 | `ui_delete` | Delete | 删除 |
| 12 | `ui_back` | Back | 后退 |
| 13 | `ui_forward` | Forward | 前进 |
| 14 | `ui_reload` | Reload | 重新加载 |
| 15 | `ui_back_to_browser` | Back to browser | 返回浏览器 |
| 16 | `ui_expand_sidebar` | Expand sidebar | 展开侧边栏 |
| 17 | `ui_collapse_sidebar` | Collapse sidebar | 折叠侧边栏 |
| 18 | `ui_search_or_enter_url` | Search or enter URL | 搜索或输入网址 |
| 19 | `ui_new_tab_home` | New tab home | 新标签页主页 |
| 20 | `ui_search_anything` | Search anything | 搜索内容 |
| 21 | `ui_drop_to_favorite` | Drop to favorite | 拖放以添加到收藏 |
| 22 | `ui_drag_a_tab_here_to_favorite` | Drag a tab here to favorite | 将标签页拖到此处以收藏 |
| 23 | `ui_sidebar_tabs` | Sidebar tabs | 侧边栏标签页 |
| 24 | `ui_toggle_pinned_tabs` | Toggle pinned tabs | 切换固定标签页显示 |
| 25 | `ui_space_actions` | Space actions | 空间操作 |
| 26 | `ui_new_space` | New Space | 新建空间 |
| 27 | `ui_edit_space` | Edit Space | 编辑空间 |
| 28 | `ui_delete_space` | Delete Space | 删除空间 |
| 29 | `ui_drop_to_pin` | Drop to pin | 拖放以固定 |
| 30 | `ui_keep_your_everyday_tabs_here` | Keep your everyday tabs here | 将常用标签页固定在这里 |
| 31 | `ui_drop_as_an_unpinned_tab` | Drop as an unpinned tab | 拖放为普通标签页 |
| 32 | `ui_release_to_favorite` | Release to favorite | 松开以收藏 |
| 33 | `ui_release_to_pin` | Release to pin | 松开以固定 |
| 34 | `ui_release_to_open_in_space` | Release to open in Space | 松开以在空间中打开 |
| 35 | `ui_release_to_move_to_space` | Release to move to Space | 松开以移到空间 |
| 36 | `ui_release_to_place_tab` | Release to place tab | 松开以放置标签页 |
| 37 | `ui_drag_to_arrange_your_sidebar` | Drag to arrange your sidebar | 拖动以整理侧边栏 |
| 38 | `ui_space_name` | Space name | 空间名称 |
| 39 | `ui_emoji_or_custom_icon` | Emoji or custom icon | 表情符号或自定义图标 |
| 40 | `ui_icon` | Icon | 图标 |
| 41 | `ui_theme` | Theme | 主题 |
| 42 | `ui_its_tabs_will_be_removed_shared_favorites_stay_available` | Its tabs will be removed. Shared favorites stay available. | 此空间的标签页将被删除，共享收藏将保留。 |
| 43 | `ui_show_actions` | Show actions | 显示操作 |
| 44 | `ui_remove_favorite` | Remove favorite | 取消收藏 |
| 45 | `ui_unpin_tab` | Unpin tab | 取消固定标签页 |
| 46 | `ui_pin_tab` | Pin tab | 固定标签页 |
| 47 | `ui_add_to_favorites` | Add to favorites | 添加到收藏 |
| 48 | `ui_open_in_right_pane` | Open in right pane | 在右侧窗格打开 |
| 49 | `ui_close_tab` | Close tab | 关闭标签页 |
| 50 | `ui_make_it_yours` | Make it yours | 打造你的浏览器 |
| 51 | `ui_a_browser_that_fits_your_workspace` | A browser that fits your workspace. | 适合你工作空间的浏览器。 |
| 52 | `ui_general` | General | 通用 |
| 53 | `ui_default_search_engine` | Default search engine | 默认搜索引擎 |
| 54 | `ui_select` | Select | 选择 |
| 55 | `ui_automatic_search_by_region` | Automatic search by region | 按地区自动选择搜索引擎 |
| 56 | `ui_mainland_china_elsewhere_google_choosing_an_engine_turns_this_off` | Mainland China: 百度 + 千问 · elsewhere: Google + ChatGPT. Choosing an engine turns this off. | 中国大陆使用百度＋千问，其他地区使用 Google＋ChatGPT。手动选择搜索引擎后将关闭此功能。 |
| 57 | `ui_region_unavailable_current_engine_kept` | Region unavailable · current engine kept | 无法获取地区 · 保留当前搜索引擎 |
| 58 | `ui_detecting_region` | Detecting region… | 正在检测地区… |
| 59 | `ui_check_again` | Check again | 重新检测 |
| 60 | `ui_open_links_in_new_tab` | Open links in new tab | 在新标签页中打开链接 |
| 61 | `ui_page_links_you_tap_open_as_a_new_tab` | Page links you tap open as a new tab | 点击页面链接时在新标签页中打开 |
| 62 | `ui_restore_tabs_on_launch` | Restore tabs on launch | 启动时恢复标签页 |
| 63 | `ui_keep_your_spaces_and_open_tabs_between_sessions` | Keep your Spaces and open tabs between sessions | 下次启动时保留空间和打开的标签页 |
| 64 | `ui_desktop_site_by_default` | Desktop site by default | 默认使用桌面版网站 |
| 65 | `ui_use_a_desktop_user_agent_for_newly_created_sessions` | Use a desktop user agent for newly created sessions | 新会话使用桌面版用户代理 |
| 66 | `ui_search_engines` | Search engines | 搜索引擎 |
| 67 | `ui_default` | &#32;&#32;Default | &#32;&#32;默认 |
| 68 | `ui_add_search_engine` | Add search engine | 添加搜索引擎 |
| 69 | `ui_appearance` | Appearance | 外观 |
| 70 | `ui_theme_color` | Theme color | 主题色 |
| 71 | `ui_forest` | Forest | 森林绿 |
| 72 | `ui_blue` | Blue | 蓝色 |
| 73 | `ui_purple` | Purple | 紫色 |
| 74 | `ui_rose` | Rose | 玫瑰色 |
| 75 | `ui_orange` | Orange | 橙色 |
| 76 | `ui_slate` | Slate | 石板灰 |
| 77 | `ui_sidebar_width` | Sidebar width | 侧边栏宽度 |
| 78 | `ui_collapsed` | Collapsed | 已折叠 |
| 79 | `ui_at_the_minimum_width_the_sidebar_folds_into_a_rail` | At the minimum width, the sidebar folds into a rail. | 缩到最小宽度时，侧边栏将自动折叠。 |
| 80 | `ui_you_can_also_drag_the_sidebar_edge` | You can also drag the sidebar edge. | 也可以拖动侧边栏边界来调整宽度。 |
| 81 | `ui_tabs` | Tabs | 标签页 |
| 82 | `ui_restore_a_closed_tab` | Restore a closed tab | 恢复关闭的标签页 |
| 83 | `ui_restore` | Restore | 恢复 |
| 84 | `ui_archive_inactive_tabs` | Archive inactive tabs | 归档不活跃的标签页 |
| 85 | `ui_checked_on_launch_or_when_you_choose_archive_now_pinned_b64b52e0` | Checked on launch or when you choose Archive now. Pinned and selected tabs are kept. | 启动时或选择“立即归档”时检查。固定标签页和当前标签页将保留。 |
| 86 | `ui_never` | Never | 从不 |
| 87 | `ui_24_hours` | 24 hours | 24 小时 |
| 88 | `ui_7_days` | 7 days | 7 天 |
| 89 | `ui_30_days` | 30 days | 30 天 |
| 90 | `ui_archive_now` | Archive now | 立即归档 |
| 91 | `ui_privacy` | Privacy | 隐私 |
| 92 | `ui_automatic_search_uses_api_country_is_to_resolve_the_con_012b7a40` | Automatic search uses api.country.is to resolve the connection country. It sees your IP, but receives no search or browsing history. If unavailable, mobile network, SIM, then device region are used. A VPN can change the detected country. Turn off Automatic search by region to stop lookups. | 地区自动选择通过 api.country.is 获取连接所在国家。该服务会收到 IP 地址，但不会收到搜索或浏览记录。无法获取时，依次使用移动网络、SIM 卡和设备地区。VPN 可能影响结果。关闭“按地区自动选择搜索引擎”即可停止查询。 |
| 93 | `ui_your_workspace_stays_on_this_device` | Your workspace stays on this device. | 工作空间仅保存在此设备上。 |
| 94 | `ui_local_storage_privacy` | History, tabs and favorites are stored locally. Sites ask before accessing your camera, microphone or location. Third-party cookies are blocked. | 历史记录、标签页和收藏保存在本地。网站使用摄像头、麦克风或位置前会请求许可。第三方 Cookie 已屏蔽。 |
| 95 | `ui_clear_browsing_history_a2d6ed` | Clear browsing history | 清除浏览记录 |
| 96 | `ui_private_browsing_and_content_blocking_are_planned_for_a_later_release` | Private browsing and content blocking are planned for a later release. | 隐私浏览和内容拦截计划在后续版本提供。 |
| 97 | `ui_nagi_0_1_0_made_for_a_little_more_room` | Nagi 0.1.0  ·  Made for a little more room. | Nagi 0.1.0  ·  为你留出更多空间。 |
| 98 | `ui_clear_browsing_history_199567` | Clear browsing history? | 清除浏览记录？ |
| 99 | `ui_this_removes_saved_visits_from_this_device` | This removes saved visits from this device. | 这将删除保存在此设备上的浏览记录。 |
| 100 | `ui_clear_history` | Clear history | 清除记录 |
| 101 | `ui_edit_search_engine` | Edit search engine | 编辑搜索引擎 |
| 102 | `ui_name` | Name | 名称 |
| 103 | `ui_keyword` | Keyword | 关键词 |
| 104 | `ui_search_url_template` | Search URL Template | 搜索网址模板 |
| 105 | `ui_use_query_where_the_search_text_belongs` | Use {query} where the search text belongs | 在搜索词的位置使用 {query} |
| 106 | `ui_icon_url_optional` | Icon URL (optional) | 图标网址（可选） |
| 107 | `ui_that_keyword_is_already_in_use` | That keyword is already in use | 该关键词已被使用 |
| 108 | `ui_custom_theme_color` | Custom theme color | 自定义主题色 |
| 109 | `ui_hex_color` | HEX color | HEX 颜色 |
| 110 | `ui_six_hexadecimal_digits_for_example_3568c0` | Six hexadecimal digits, for example #3568C0 | 输入六位十六进制数字，例如 #3568C0 |
| 111 | `ui_resize_split_view` | Resize split view | 调整分屏宽度 |
| 112 | `ui_your_browsing_history_will_appear_here` | Your browsing history will appear here. | 浏览记录将在此处显示。 |
| 113 | `ui_search_history` | Search history | 搜索历史记录 |
| 114 | `ui_page_menu` | Page menu | 页面菜单 |
| 115 | `ui_unpin_from_sidebar` | Unpin from sidebar | 取消侧边栏固定 |
| 116 | `ui_pin_to_sidebar` | Pin to sidebar | 固定到侧边栏 |
| 117 | `ui_use_mobile_site` | Use mobile site | 使用移动版网站 |
| 118 | `ui_use_desktop_site` | Use desktop site | 使用桌面版网站 |
| 119 | `ui_find_in_page` | Find in page | 在页面中查找 |
| 120 | `ui_new_split_view` | New split view | 新建分屏 |
| 121 | `ui_swap_panes` | Swap panes | 交换窗格 |
| 122 | `ui_close_split_view` | Close split view | 关闭分屏 |
| 123 | `ui_choose_tab` | Choose tab | 选择标签页 |
| 124 | `ui_your_space` | your Space | 你的空间 |
| 125 | `ui_retry` | Retry | 重试 |
| 126 | `ui_previous_match` | Previous match | 上一个匹配项 |
| 127 | `ui_next_match` | Next match | 下一个匹配项 |
| 128 | `ui_close_find` | Close find | 关闭查找 |
| 129 | `ui_where_would_you_like_to_go` | Where would you like to go? | 想去哪里？ |
| 130 | `ui_could_not_open_workspace` | Could not open workspace | 无法打开工作空间 |
| 131 | `ui_action_could_not_be_completed` | Action could not be completed | 无法完成操作 |
| 132 | `ui_no_closed_tabs_to_restore` | No closed tabs to restore | 没有可恢复的标签页 |
| 133 | `ui_search_enter_a_url_or_type_for_commands` | Search, enter a URL, or type &gt; for commands | 输入搜索词或网址，输入 &gt; 使用命令 |
| 134 | `ui_ready` | Ready | 就绪 |
| 135 | `ui_composing` | Composing | 正在输入 |
| 136 | `ui_no_matches` | No matches | 没有匹配项 |
| 137 | `ui_navigate_open_g_ddg_yt_gh_scholar_keyword_search` | ↑ ↓  Navigate     ↵  Open     g · ddg · yt · gh · scholar  Keyword search | ↑ ↓  选择     ↵  打开     g · ddg · yt · gh · scholar  关键词搜索 |
| 138 | `ui_exit_fullscreen` | Exit fullscreen | 退出全屏 |
| 139 | `ui_open_tabs` | Open tabs | 打开的标签页 |
| 140 | `ui_commands` | Commands | 命令 |
| 141 | `ui_two_panes` | Two panes | 两个窗格 |
| 142 | `ui_one_pane` | One pane | 一个窗格 |
| 143 | `ui_restore_closed_tab` | Restore closed tab | 恢复关闭的标签页 |
| 144 | `ui_toggle_sidebar` | Toggle sidebar | 切换侧边栏 |
| 145 | `ui_more_room` | More room | 更多空间 |
| 146 | `ui_open_settings` | Open settings | 打开设置 |
| 147 | `ui_preferences` | Preferences | 偏好设置 |
| 148 | `ui_open_history` | Open history | 打开历史记录 |
| 149 | `ui_recent_visits` | Recent visits | 最近访问 |
| 150 | `ui_toggle_desktop_site` | Toggle desktop site | 切换桌面版网站 |
| 151 | `ui_user_agent` | User agent | 用户代理 |
| 152 | `ui_website` | Website | 网站 |
| 153 | `ui_system` | System | 跟随系统 |
| 154 | `ui_light` | Light | 浅色 |
| 155 | `ui_dark` | Dark | 深色 |
| 156 | `ui_mobile_network` | Mobile network | 移动网络 |
| 157 | `ui_device_region` | Device region | 设备地区 |
| 158 | `ui_unavailable` | Unavailable | 无法获取 |
| 159 | `ui_name_is_required` | Name is required | 请输入名称 |
| 160 | `ui_keyword_must_use_lowercase_letters_numbers_or` | Keyword must use lowercase letters, numbers, - or _ | 关键词只能使用小写字母、数字、- 或 _ |
| 161 | `ui_search_url_must_contain_query` | Search URL must contain {query} | 搜索网址必须包含 {query} |
| 162 | `ui_use_a_valid_https_search_url` | Use a valid https search URL | 请输入有效的 HTTPS 搜索网址 |
| 163 | `ui_icon_url_is_invalid` | Icon URL is invalid | 图标网址无效 |
| 164 | `ui_keep_at_least_one_search_engine` | Keep at least one search engine | 请至少保留一个搜索引擎 |
| 165 | `ui_space_name_is_required` | Space name is required | 请输入空间名称 |
| 166 | `ui_keep_at_least_one_space` | Keep at least one Space | 请至少保留一个空间 |
| 167 | `ui_space_no_longer_exists` | Space no longer exists | 空间已不存在 |
| 168 | `ui_enter_a_url_or_search_query` | Enter a URL or search query | 请输入网址或搜索词 |
| 169 | `ui_only_http_and_https_addresses_can_be_opened_here` | Only http and https addresses can be opened here | 此处仅支持 HTTP 和 HTTPS 网址 |
| 170 | `ui_add_a_search_engine_in_settings` | Add a search engine in Settings | 请在设置中添加搜索引擎 |
| 171 | `ui_no_file_picker_is_available` | No file picker is available | 无法打开文件选择器 |
| 172 | `ui_allow_site_access` | Allow site access? | 允许网站访问？ |
| 173 | `ui_allow` | Allow | 允许 |
| 174 | `ui_block` | Block | 阻止 |
| 175 | `ui_download_file` | Download file? | 下载文件？ |
| 176 | `ui_download` | Download | 下载 |
| 177 | `ui_download_started` | Download started | 下载已开始 |
| 178 | `ui_open_another_app` | Open another app? | 打开其他应用？ |
| 179 | `ui_open` | Open | 打开 |
| 180 | `ui_no_app_can_open_this_link` | No app can open this link | 没有可打开此链接的应用 |
| 181 | `ui_camera` | Camera | 摄像头 |
| 182 | `ui_microphone` | Microphone | 麦克风 |
| 183 | `ui_location` | Location | 位置 |
| 184 | `ui_downloaded_with_nagi` | Downloaded with Nagi | 通过 Nagi 下载 |
| 185 | `ui_this_address_type_is_not_supported` | This address type is not supported | 不支持此网址类型 |
| 186 | `ui_the_site_s_certificate_could_not_be_verified` | The site's certificate could not be verified. | 无法验证网站证书。 |
| 187 | `ui_blank_script_generated_popups_are_not_supported_in_this_mvp` | Blank script-generated popups are not supported in this MVP | 暂不支持脚本生成的空白弹出窗口 |
| 188 | `ui_blob_and_data_downloads_are_not_supported_in_this_mvp` | Blob and data downloads are not supported in this MVP | 暂不支持 Blob 和 data 下载 |
| 189 | `ui_this_popup_address_is_not_supported` | This popup address is not supported | 不支持此弹出窗口网址 |
| 190 | `ui_tab_1_s` | Tab %1$s | 标签页 %1$s |
| 191 | `ui_archived_1_s` | Archived · %1$s | 已归档 · %1$s |
| 192 | `ui_switch_to_1_s` | Switch to %1$s | 切换到 %1$s |
| 193 | `ui_space_icon_1_s` | Space icon %1$s | 空间图标 %1$s |
| 194 | `ui_space_theme_1_s` | Space theme %1$s | 空间主题 %1$s |
| 195 | `ui_delete_1_s_137cdc` | Delete %1$s? | 删除 %1$s？ |
| 196 | `ui_favorite_1_s` | Favorite %1$s | 收藏 %1$s |
| 197 | `ui_favorite_actions_for_1_s` | Favorite actions for %1$s | %1$s 的收藏操作 |
| 198 | `ui_drag_1_s` | Drag %1$s | 拖动 %1$s |
| 199 | `ui_actions_for_1_s` | Actions for %1$s | %1$s 的操作 |
| 200 | `ui_close_1_s` | Close %1$s | 关闭 %1$s |
| 201 | `ui_move_to_1_s` | Move to %1$s | 移到 %1$s |
| 202 | `ui_make_1_s_default` | Make %1$s default | 将 %1$s 设为默认 |
| 203 | `ui_edit_1_s` | Edit %1$s | 编辑 %1$s |
| 204 | `ui_delete_1_s_48c860` | Delete %1$s | 删除 %1$s |
| 205 | `ui_custom_color_1_s` | Custom color · #%1$s | 自定义颜色 · #%1$s |
| 206 | `ui_remove_1_s` | Remove %1$s | 删除 %1$s |
| 207 | `ui_workspace_could_not_be_opened_1_s` | Workspace could not be opened: %1$s | 无法打开工作空间：%1$s |
| 208 | `ui_open_1_s` | Open %1$s | 打开 %1$s |
| 209 | `ui_search_1_s_for_2_s` | Search %1$s for “%2$s” | 用 %1$s 搜索“%2$s” |
| 210 | `ui_search_1_s` | Search %1$s | 用 %1$s 搜索 |
| 211 | `ui_1_s_wants_to_use_your_2_s_access_lasts_for_this_request` | %1$s wants to use your %2$s. Access lasts for this request. | %1$s 请求使用%2$s。许可仅对此次请求有效。 |
| 212 | `ui_download_failed_1_s` | Download failed: %1$s | 下载失败：%1$s |
| 213 | `ui_emoji` | Emoji | 表情符号 |
| 214 | `ui_custom_emoji` | Custom emoji | 自定义表情符号 |
| 215 | `ui_customize_search_engines` | Customize search engines | 自定义搜索引擎 |
| 216 | `ui_manage_search_engines` | Add, edit or remove search engines and keywords | 添加、编辑或删除搜索引擎和关键词 |
| 217 | `ui_back_to_settings` | Back to settings | 返回设置 |
| 218 | `ui_ask_chatgpt_about_1_s` | Ask ChatGPT about “%1$s” | 向 ChatGPT 提问：“%1$s” |
| 219 | `ui_common_search_engines` | Common search engines | 常用搜索引擎 |
| 220 | `ui_common_search_engines_description` | Choose engines shown when you search. Search default is used for web searches; AI default is used to ask AI. Both defaults are always included. Your choices stay the same when you travel. | 选择搜索时显示的引擎。“搜索默认”用于网页搜索，“AI 默认”用于向 AI 提问。两个默认引擎始终包含在内，出行时也会保留您选择的组合。 |
| 221 | `ui_available_search_engines` | Available search engines | 可选搜索引擎 |
| 222 | `ui_chatgpt_web_handoff` | Open your query on the ChatGPT website | 在 ChatGPT 网站中打开输入的内容 |
| 223 | `ui_search_navigation_hint` | ↑ ↓ Navigate   ↵ Open   ·   Choose common engines in Settings | ↑ ↓ 选择   ↵ 打开   ·   在设置中选择常用引擎 |
| 224 | `ui_include_common_engine` | Include %1$s in common search engines | 将 %1$s 加入常用搜索引擎 |
| 225 | `ui_qwen` | Qwen | 千问 |
| 226 | `ui_qwen_china_mainland` | Qwen (China Mainland) | 千问（中国大陆） |
| 227 | `ui_ask_engine_about_query` | Ask %1$s about “%2$s” | 向 %1$s 提问：“%2$s” |
| 228 | `ui_copy_link` | Copy link | 复制链接 |
| 229 | `ui_share_link` | Share link | 分享链接 |
| 230 | `ui_link_copied` | Link copied | 已复制链接 |
| 231 | `ui_search_more_lucide` | Search for more on Lucide | 在 Lucide 上查找更多图标 |
| 232 | `ui_lucide_code_name` | Choose by Lucide code name | 按 Lucide 代码名称选择 |
| 233 | `ui_icon_code_name` | Icon code name | 图标代码名称 |
| 234 | `ui_lucide_code_not_found` | No icon matches this code name. | 未找到与此代码名称匹配的图标。 |
| 235 | `ui_lucide_exact_code_hint` | Enter the exact code name, for example user-round. Case and hyphens must match. | 输入完整代码名称，例如 user-round。大小写和连字符必须一致。 |
| 236 | `ui_icon_preview` | Icon preview | 图标预览 |
| 237 | `ui_use_icon` | Use icon | 使用此图标 |
| 238 | `ui_default_ai_engine` | Default AI engine | 默认 AI 引擎 |
| 239 | `ui_ai_default_badge` | AI default | AI 默认 |
| 240 | `ui_ai_engine_badge` | AI | AI |
| 241 | `ui_search_default_badge` | Search default | 搜索默认 |
| 242 | `ui_make_ai_default` | Set AI default | 设为 AI 默认 |
| 243 | `ui_make_search_default` | Set search default | 设为搜索默认 |
| 244 | `ui_make_ai_default_named` | Make %1$s the AI default | 将 %1$s 设为 AI 默认 |
| 245 | `ui_site_controls` | Site controls | 网站控制 |
| 246 | `ui_clear_site_data` | Clear cookies and site data | 清除 Cookie 和网站数据 |
| 247 | `ui_clear_site_data_description` | This signs you out of websites and clears cookies, site storage and cached pages in all Spaces. Tabs, favorites and history are kept. | 这会退出所有空间中的网站登录，并清除 Cookie、网站存储和页面缓存。保留标签页、收藏和历史记录。 |
| 248 | `ui_site_data_cleared` | Cookies and site data cleared | 已清除 Cookie 和网站数据 |
| 249 | `ui_loading_timeout` | The page is taking too long to load. Retry or check your connection. | 页面加载时间过长。请重试或检查网络连接。 |
| 250 | `ui_open_link_new_tab` | Open link in new tab | 在新标签页中打开链接 |
| 251 | `ui_open_image_new_tab` | Open image in new tab | 在新标签页中打开图片 |
| 252 | `ui_copy_image_link` | Copy image link | 复制图片链接 |
| 253 | `ui_save_image` | Save image | 保存图片 |
| 254 | `ui_app_updates` | App updates | 应用更新 |
| 255 | `ui_current_version` | Current version: %1$s | 当前版本：%1$s |
| 256 | `ui_update_idle` | Check for the latest version. | 检查最新版本。 |
| 257 | `ui_update_checking` | Checking for updates… | 正在检查更新… |
| 258 | `ui_update_current` | You have the latest version. | 已是最新版本。 |
| 259 | `ui_update_available` | An update is available. | 有可用更新。 |
| 260 | `ui_update_downloading` | Downloading update… | 正在下载更新… |
| 261 | `ui_update_ready` | The update is verified and ready to install. | 更新已通过验证，可以安装。 |
| 262 | `ui_update_failed` | Could not check, download or verify the update. Please retry. | 无法检查、下载或验证更新。请重试。 |
| 263 | `ui_update_unsupported` | This update requires a newer Android version. | 此更新需要更高版本的 Android。 |
| 264 | `ui_check_updates` | Check for updates | 检查更新 |
| 265 | `ui_download_update` | Download update | 下载更新 |
| 266 | `ui_install_update` | Install update | 安装更新 |
| 267 | `ui_update_install_description` | Android will ask you to allow installs from Nagi and confirm the update. Your browsing data is kept. | Android 会要求允许 Nagi 安装应用并确认更新。保留浏览数据。 |
| 268 | `ui_update_install_failed` | Could not open the verified update. Download it again and retry. | 无法打开已验证的更新。请重新下载后重试。 |
| 269 | `ui_update_banner` | Update to %1$s | 更新至 %1$s |
| 270 | `ui_clear_data` | Clear data | 清除数据 |
| 271 | `ui_privacy_policy` | Privacy policy | 隐私政策 |
| 272 | `ui_privacy_policy_url` | https://takeruf.com/en/nagi/privacy | https://takeruf.com/zh/nagi/privacy |
| 273 | `ui_play_updates` | Updates are delivered through Google Play. | 更新通过 Google Play 分发。 |
| 274 | `ui_open_google_play` | Open Google Play | 打开 Google Play |
| 275 | `ui_password_autofill` | Password autofill | 密码自动填充 |
| 276 | `ui_password_autofill_description` | Use Google Password Manager or another autofill service selected in Android settings. Tap a login field on a website to see available suggestions. Availability depends on your device, website and provider. | 使用在 Android 设置中选择的 Google 密码管理工具或其他自动填充服务。点按网站的登录输入框即可查看可用建议。可用性取决于设备、网站和服务提供方。 |
| 277 | `ui_open_autofill_settings` | Choose autofill service | 选择自动填充服务 |
| 278 | `ui_autofill_settings_unavailable` | Open Android settings to choose an autofill service. | 请在 Android 设置中选择自动填充服务。 |
| 279 | `ui_upload_file` | Upload file | 上传文件 |
| 280 | `ui_take_photo` | Take photo | 拍摄照片 |
| 281 | `ui_choose_files` | Choose files | 选择文件 |
| 282 | `ui_camera_unavailable` | No camera app is available. | 无法打开相机应用。 |
| 283 | `ui_download_saved` | Saved to Downloads | 已保存到 Downloads |
| 284 | `ui_generated_download_failed` | Could not save this generated file. The limit is 32 MB. | 无法保存生成的文件。大小上限为32 MB。 |
| 285 | `ui_page_drag` | Drag links and images | 拖放链接和图片 |
| 286 | `ui_page_drag_description` | Long-press a page link or image to drag it. Drop links on a pane address bar to open them. Right-click for actions; turn this off to use the long-press menu. | 长按网页链接或图片即可拖动。将链接放到窗格地址栏即可打开。右键可打开操作菜单，关闭此设置后也可通过长按打开菜单。 |
| 287 | `ui_sleep_tabs_description` | When more than 6 pages are live, safe background tabs idle for 10 minutes can sleep. Displayed pages, edited forms and active media stay live. Sleeping tabs reload when opened; some page state may be lost. | 当打开的网页超过6个时，可休眠闲置10分钟的安全后台标签页。正在显示、编辑表单或使用媒体的页面保持运行。休眠页面打开时会重新加载，部分页面状态可能丢失。 |
| 288 | `ui_sleep_tabs` | Suspend background tabs | 休眠后台标签页 |
| 289 | `ui_tab_suspended` | Suspended tab | 已休眠的标签页 |
| 290 | `ui_release_to_split_tabs` | Release to open tabs side by side | 松开以并排显示标签页 |

## リソース外の中国語固有名詞

`DefaultSearchEngines.kt` には以下が直接定義されています。元の英語文言との翻訳ペアはありません。Qwen → 千问 は上表のリソースで変換されます。

| エンジンID | 英語表示時も使われる名前 | 中国語表示 |
|---|---|---|
| baidu | 百度 | 百度 |
| sogou | 搜狗 | 搜狗 |
| douyin | 抖音 | 抖音 |
| shenma | 神马 | 神马 |

## 抽出元

- `app/src/main/res/values/strings.xml` — SHA-256: `c860d3201c0815d803ea79ed9b449476911608dea91792483e31c1883558c181`
- `app/src/main/res/values-zh/strings.xml` — SHA-256: `9c8b3cd702acbe407294e07a0c86bed9c9b4c6bcd349abf2da88b309a166bd1f`
- ローカライズの入口: `app/src/main/java/com/takeruf/nagi/ui/localization/NagiStrings.kt`。
- 検索エンジン表示: `app/src/main/java/com/takeruf/nagi/browser/search/SearchEngineNames.kt`。
