# MamoriDX（守りのDX 2.0）HANDOFF

## 現在バージョン: v2.0（Phase 2 / A案・記録専用VPN）

## デプロイ規約（v1.1から・Appathy標準）
- 各ZIPのプロジェクト直下に `deploy.sh` を同梱する
- ユーザーの実行手順は毎回固定の4行のみ（cd ~ / cp / unzip -o / bash deploy.sh "メッセージ"）
- **対話入力（read等）を含むコマンドブロックは禁止**（ブロック一括貼り付けとreadは両立しないため）
- トークンは `git config --global github.token` に一度だけ登録し、deploy.shが読み出す
- deploy.shは冪等：リポジトリ作成済み(422)・init済み・remote設定済み・変更なし、いずれも安全に続行

## コンセプト
シャドーIT対策の発想転換：「見つけて即削除」→「可視化 → 許可 → ガードレール設置」。
有益なSaaSは使い続けられるようにし、その代わり漏洩しない仕組みを置く。

## Phase 構成
- **Phase 1（実装済）**: 可視化。アプリ棚卸し（3分類）＋端末診断（6項目A/B/C判定）＋アプリ内説明書
- **Phase 2（本バージョン・A案）**: VpnServiceによるDNS可視化。「通信ログ」タブを追加。記録専用モード（パケットは転送せずDNSクエリ名のみ抽出、VPN有効中は通信が流れない）。ドメイン単位で「許可/記録のみ/ブロック」ポリシーをSharedPreferencesに保存。ブロックは現状ポリシー記録のみ（実遮断なし）
- **Phase 2.5（未着手）**: ブロックの実効化＋パケット実転送（B案・常時ON対応）。TCP/UDP転送を自前実装
- **Phase 3（未着手）**: 共有インテントに割り込む関所（DLP）。マイナンバー（チェックデジット検証）、クレカ番号（Luhn）、「社外秘」等キーワード検査。SNS宛のみ警告強化

## Phase 2 実装メモ（A案）
- `DnsMonitorService`(VpnService): Builder で addRoute("0.0.0.0",0)+setBlocking(true)。establish後、入力FDをreadしIPv4/UDP/dport53のパケットのみDNSパース。QNAMEをラベル長方式で抽出しDnsLogStore.recordへ。**パケットは書き戻さない=記録専用（A案）**。自パッケージはaddDisallowedApplicationで除外
- `DnsLogStore`(object): domain->Entry(count) をLinkedHashMapで保持しSharedPreferences(JSON)に永続化。最大500件でFIFO。ポリシーは domain->Int(0許可/1記録のみ/2ブロック)
- MainActivityは VpnService.prepare→startActivityForResult(VPN_REQUEST=1001)→RESULT_OKでstartForegroundService。onActivityResultで開始
- Manifest追加: FOREGROUND_SERVICE / POST_NOTIFICATIONS / FOREGROUND_SERVICE_SPECIAL_USE、service(BIND_VPN_SERVICE, foregroundServiceType=specialUse, PROPERTY_SPECIAL_USE_FGS_SUBTYPE)
- タブは4つに（アプリ棚卸し/端末診断/通信ログ/説明書）。tabName index: 説明書は3、通信ログは2

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
    ├── AndroidManifest.xml       … QUERY_ALL_PACKAGES + VpnService登録 + FGS権限
    └── java/com/appathy/mamoridx/
        ├── MainActivity.kt       … UI全体（4タブ）＋VPN制御
        ├── DnsMonitorService.kt  … 記録専用VpnService（DNSパース）
        └── DnsLogStore.kt        … ログ/ポリシー永続化（object）
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
