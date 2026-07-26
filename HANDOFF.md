# MamoriDX（守りのDX 2.0）HANDOFF

## 現在バージョン: v4.0（Phase 4「緊急対応」タブ追加）

## Phase 4 実装メモ（悪意あるリンク対策）
- タブ構成が5つに: 棚卸し / 端末診断 / 通信ログ / 緊急対応(index3) / 説明書(index4)。説明書内ページは6つ(概要/棚卸し/診断/通信/緊急/関所)
- `UrlChecker`(object): 完全オフラインのフィッシングURL判定。@偽装/Punycode/非ASCIIホスト/IP直打ち/短縮URL/危険TLD/多階層サブドメイン/長大ホスト/ブランド詐称/ハイフン乱用/認証誘導パス/過剰エンコード/APK直リンクの13項目。ブランド照合は誤検知回避のため「.-_」で分割したトークン単位（"online"に"line"が一致する事故を防止）。登録可能ドメインはjp二次レベル(co/ne/or等)を考慮して近似算出。ShareGateActivityからも呼ばれ、共有テキスト内のURLを自動検査
- `ThreatScanner`(object): 侵害チェック6分類。①firstInstallTimeで直近24h/7dの新規アプリ ②Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES ③"enabled_notification_listeners" ④DevicePolicyManager.activeAdmins ⑤REQUEST_INSTALL_PACKAGES付与済みアプリ ⑥既定SMSアプリ(Telephony.Sms.getDefaultSmsPackage)と既定ブラウザの乗っ取り。結果はseverity降順
- 対処動線: ACTION_DELETE(アンインストール) / ACTION_APPLICATION_DETAILS_SETTINGS / 各種Settings画面へのIntent。全てtry-catchで囲みopenSafely()経由（機種により存在しない画面があるため）
- 応急処置チェックリスト10手順（通信遮断→アプリ削除→ユーザー補助確認→提供元不明取消→パスワード変更→二段階認証→カード停止→Cookie削除→OS更新→会社報告）
- 説明書「緊急」ページに「できないこと（正直な限界）」を明記：盗まれた情報は取り戻せない、高度なマルウェアは検出不可、危険信号なし≠安全

## v3.1の内容
- アイコン: res/mipmap-*/ic_launcher.png（DFDX2.0デザイン、PNG5密度、PILで生成）
- 説明書タブ: ページ切替式。各ページに「これは何？→使い方手順（手順1から）→見方→FAQ」構成
- アイコン: res/mipmap-*/ic_launcher.png（DFDX2.0デザイン、PNG5密度、PILで生成）
- 説明書タブ: 概要/棚卸し/診断/通信/関所 の5ページ切替式。各ページに「これは何？→使い方手順（手順1から）→見方→FAQ」構成

## Phase 3 実装メモ
- `ShareGateActivity`: ACTION_SEND (text/plain, image/*) を受ける関所。exported=true、label「守りのDX関所」
- テキスト検査: 全角→半角正規化後、数字列（ハイフン/スペース区切り許容）を抽出し、12桁=マイナンバー（JISチェックデジット検証、テスト値123456789018で確認済）、14-16桁=クレカ（Luhn）。機密キーワード（社外秘/部外秘/極秘/マル秘/機密/取扱注意/confidential等）
- 画像検査: ExifInterface(InputStream)でTAG_GPS_LATITUDEの有無を確認
- 検出時アクション: マスクして転送（末尾4桁以外を*化）/ そのまま転送 / 中止。SNSアプリ（X/Instagram/Facebook/LINE/TikTok）導入端末では追加警告
- 転送はcreateChooser + EXTRA_EXCLUDE_COMPONENTSで自分自身を除外（無限ループ防止）
- 検査は全て端末内完結・外部送信なし

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
- **Phase 3（本バージョンで実装済）**: 共有インテントに割り込む関所（DLP）。マイナンバー（チェックデジット検証）、クレカ番号（Luhn）、「社外秘」等キーワード検査。SNS宛のみ警告強化

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
