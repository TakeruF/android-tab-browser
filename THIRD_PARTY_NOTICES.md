# Third-party licenses

Nagi本体のソースコード・ドキュメントには[MIT License](LICENSE)を適用します。依存ライブラリ・ツールには、それぞれのライセンスが適用されます。

## APKに含まれるライブラリ

2026-10-04の`releaseRuntimeClasspath`で解決された127モジュールの一覧と出典は、[THIRD_PARTY_NOTICES.txt](app/src/main/assets/licenses/THIRD_PARTY_NOTICES.txt)に記録しています。推移的依存も含む一覧であり、R8後の全クラスの残存を意味しません。

| ライブラリ / データ | ライセンス / 出典 |
| --- | --- |
| AndroidX / Compose / Material icons / Room / DataStore | [Apache-2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| Kotlin / Kotlinx Coroutines / Serialization / JetBrains annotations | [Apache-2.0およびKotlin内の個別表記](https://github.com/JetBrains/kotlin/blob/v2.2.21/license/README.md) |
| Coil 3.3.0 | [Apache-2.0](https://github.com/coil-kt/coil/blob/3.3.0/LICENSE.txt) |
| OkHttp 4.12.0 / Okio 3.15.0 | [Apache-2.0](https://github.com/square/okhttp/blob/parent-4.12.0/LICENSE.txt) / [Okio](https://github.com/square/okio/blob/parent-3.15.0/LICENSE.txt) |
| Accompanist / Guava listenablefuture / JSpecify | [Apache-2.0](https://github.com/google/accompanist/blob/v0.37.3/LICENSE) / [Guava](https://github.com/google/guava/blob/master/LICENSE) / [JSpecify](https://github.com/jspecify/jspecify/blob/v1.0.0/LICENSE) |
| Lucide static 1.52.0 (追加アイコン) | [ISCおよびFeather MIT](app/src/main/assets/licenses/Lucide-LICENSE.txt) |
| Lucide Android 2.2.1 / Lucide・Featherのアイコン | [MIT（Androidライブラリ）](app/src/main/assets/licenses/Compose-Icons-MIT.txt) / [ISCおよびFeather MIT](app/src/main/assets/licenses/Lucide-LICENSE.txt) |
| Google Play services / Google ID（Credential Managerの推移的依存） | [Android SDK License](https://developer.android.com/studio/terms.html) |
| DataStoreが再パッケージしたProtocol Buffers | [BSD-3-Clause](app/src/main/assets/licenses/BSD-3-Clause-Protobuf.txt) |
| Kotlin time内のThreeTen backport | [BSD-3-Clause](app/src/main/assets/licenses/BSD-3-Clause-ThreeTen.txt) |
| Kotlin JVM math内のBoost由来コード | [BSL-1.0](app/src/main/assets/licenses/Kotlin-boost_LICENSE.txt) |
| OkHttp内のPublic Suffix List | [MPL-2.0](app/src/main/assets/licenses/MPL-2.0.txt)。[元のNOTICE](https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/resources/okhttp3/internal/publicsuffix/NOTICE)と[ソースデータ](https://publicsuffix.org/list/public_suffix_list.dat) |

ライセンス原文と個別の著作権表記は[assets/licenses](app/src/main/assets/licenses/)に保存し、Debug / Release APKへ同梱します。Kotlin collections / unsigned JVMに由来するGWT・Guavaの原文も保存しています。WebViewとAndroidフレームワークは端末側で提供されます。

## 開発・テスト用ツール

以下はビルド／テストに使用し、Release APKには含めません。

| ツール | ライセンス / 出典 |
| --- | --- |
| Gradle 8.13 / 同梱Wrapper | [Apache-2.0](https://github.com/gradle/gradle/blob/v8.13.0/LICENSE)。Wrapper JAR内の`META-INF/LICENSE`も保持 |
| Android Gradle Plugin / Kotlin compiler / KSP / AndroidX Test | [Apache-2.0](https://github.com/google/ksp/blob/main/LICENSE) |
| JUnit 4.13.2 | [EPL-1.0](https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt) |
| Robolectric 4.16.1 | [MIT](https://github.com/robolectric/robolectric/blob/robolectric-4.16.1/LICENSE) |

## 依存更新時

`./gradlew -I scripts/license-inventory.gradle :app:licenseInventory`で解決済みモジュール一覧を`app/build/reports/licenses/release-runtime-modules.txt`へ出力できます。依存関係ツリーは`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`で確認できます。

各リリースのPOM・LICENSE・NOTICEを照合して一覧と同梱原文を更新してください。再パッケージされた依存やデータファイルのライセンスも確認します。本体のLICENSEを変更した場合は同梱の`Nagi-MIT.txt`にも反映します。

本リポジトリの画面画像はNagiの検証時のキャプチャです。参照した外部製品・サービスの資料は[ARC_REFINEMENT.md](docs/ARC_REFINEMENT.md)に出典を記載しています。
