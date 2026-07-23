# MamoriDX（守りのDX 2.0）HANDOFF

## 現在バージョン: v1.0（Phase 1）

## コンセプト
シャドーIT対策の発想転換：「見つけて即削除」→「可視化 → 許可 → ガードレール設置」。
有益なSaaSは使い続けられるようにし、その代わり漏洩しない仕組みを置く。

## Phase 構成
- **Phase 1（本バージョン）**: 可視化。アプリ棚卸し（3分類）＋端末診断（6項目A/B/C判定）＋アプリ内説明書
- **Phase 2（未着手）**: VpnService によるDNSレベルの通信可視化。どのアプリがどのSaaSドメインと通信しているかを記録。ドメイン単位で「許可/記録のみ/ブロック」ポリシー
- **Phase 3（未着手）**: 共有インテントに割り込む関所（DLP）。マイナンバー（チェックデジット検証）、クレカ番号（Luhn）、「社外秘」等キーワード検査。SNS宛のみ警告強化

## ビルドスタック（変更禁止・Appathy標準）
- AGP 8.5.2 / Kotlin 1.9.24 / Gradle 8.9（GitHub Actionsで直接インストール、wrapperなし）
- 外部依存ゼロ / プログラマティックUIのみ（XMLレイアウトなし）
- debug.keystore をリポジトリにコミット（storepass/keypass: android, alias: androiddebugkey）
- minSdk 26 / targetSdk 34 / compileSdk 34

## ファイル構成
```
MamoriDX/
├── .github/workflows/build.yml   … Gradle 8.9 DL → assembleDebug → artifact
├── build.gradle.kts              … AGP/Kotlin バージョンピン
├── settings.gradle.kts
├── debug.keystore
├── app/build.gradle.kts          … 署名設定・依存ゼロ
└── app/src/main/
    ├── AndroidManifest.xml       … QUERY_ALL_PACKAGES のみ
    └── java/com/appathy/mamoridx/MainActivity.kt … 全実装（1ファイル）
```

## 実装メモ
- リスクスコア: 付与済み危険権限 1つ=1点、ストア外入手=+3点
- 分類: 0-2点=許可済み / 3-5点=要監視 / 6点以上またはストア外=要対策
- インストール元: API 30+ は getInstallSourceInfo、それ未満は getInstallerPackageName
- INTERNET権限なし＝外部送信ゼロ（説明書タブで信頼性アピールに使用）
- Play配布は不可の前提（QUERY_ALL_PACKAGESの審査が厳しい）。Appathyテスター向け直接配布

## ⚠️ 頻出の落とし穴
- **`git init` はホームディレクトリではなく必ず `~/MamoriDX` 内で実行すること。**
  ホームで実行するとトークン等がステージされ Push Protection (GH013) で弾かれる事故が過去に複数回発生。
- QUERY_ALL_PACKAGES を消すと棚卸しがほぼ空になる（Android 11+）

## 次にやる場合の候補
1. 棚卸し結果のCSVエクスポート（共有インテント）
2. システムアプリ表示トグル
3. Phase 2 着手時は新チャットで本ファイル＋Phase 2要件を貼って開始
